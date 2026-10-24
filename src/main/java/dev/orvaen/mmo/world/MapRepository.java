package dev.orvaen.mmo.world;

import dev.orvaen.mmo.rom.GameProfile;
import dev.orvaen.mmo.rom.Rom;
import dev.orvaen.mmo.rom.RomException;
import dev.orvaen.mmo.rom.TextCodec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Découvre et charge les cartes de la ROM.
 * <pre>
 * gMapGroups : ptr[] → (ptr[] → MapHeader)
 * MapHeader  : 0x00 layout · 0x04 events · 0x0C connexions · 0x14 section (nom) · 0x17 type
 * MapLayout  : 0x00 w · 0x04 h · 0x08 bordure · 0x0C grille · 0x10/0x14 tilesets · 0x18/0x19 taille bordure (FR/LG)
 * </pre>
 */
public final class MapRepository {

    private static final int MAX_DIMENSION = 512;

    private final Rom rom;
    private final GameProfile profile;
    private final int groupsTable;
    private final int[] groupOffsets;
    private final int[] groupSizes;
    private final int labelsTable;
    private final Map<Integer, Tileset> tilesets = new HashMap<>();
    private final Map<String, GameMap> cache = new HashMap<>();

    public MapRepository(Rom rom) {
        this.rom = rom;
        this.profile = rom.profile();
        int sig = rom.find(GameProfile.SIG_MAP_GROUPS);
        if (sig < 0) {
            throw new RomException("Table des cartes introuvable : ROM modifiée ou non prise en charge.");
        }
        groupsTable = rom.requirePointer(sig + GameProfile.SIG_POINTER_DISTANCE, "la table des cartes");

        List<Integer> groups = new ArrayList<>();
        for (int o = groupsTable; ; o += 4) {
            if (crossesGroupStart(o, groupsTable, groups)) break;
            int p = rom.pointer(o);
            if (p < 0) break;
            groups.add(p);
        }
        groupOffsets = groups.stream().mapToInt(Integer::intValue).toArray();
        groupSizes = new int[groupOffsets.length];
        for (int g = 0; g < groupOffsets.length; g++) {
            int start = groupOffsets[g];
            int count = 0;
            for (int o = start; ; o += 4) {
                if (crossesGroupStart(o, start, groups)) break;
                if (start < groupsTable && o >= groupsTable) break;
                int header = rom.pointer(o);
                if (header < 0 || rom.pointer(header) < 0) break;
                count++;
            }
            groupSizes[g] = count;
        }

        int labelsSig = rom.find(profile.mapLabelsSignature);
        labelsTable = labelsSig < 0 ? -1 : rom.pointer(labelsSig + GameProfile.SIG_POINTER_DISTANCE);
    }

    private static boolean crossesGroupStart(int offset, int base, List<Integer> starts) {
        for (int s : starts) {
            if (base < s && offset >= s) return true;
        }
        return false;
    }

    public int groupCount() {
        return groupOffsets.length;
    }

    public int mapCount(int group) {
        return group >= 0 && group < groupSizes.length ? groupSizes[group] : 0;
    }

    public boolean exists(int group, int num) {
        return num >= 0 && num < mapCount(group);
    }

    public synchronized GameMap load(int group, int num) {
        if (!exists(group, num)) {
            throw new RomException("Carte inexistante : " + group + "." + num);
        }
        return cache.computeIfAbsent(group + "." + num, k -> read(group, num));
    }

    private GameMap read(int group, int num) {
        int header = rom.requirePointer(groupOffsets[group] + num * 4, "l'en-tête de carte " + group + "." + num);
        MapLayout layout = readLayout(rom.requirePointer(header, "le layout de " + group + "." + num));
        String name = readName(rom.u8(header + 0x14), group, num);
        int mapType = rom.u8(header + 0x17);
        return new GameMap(group, num, name, mapType, layout,
                readWarps(rom.pointer(header + 0x04)),
                readConnections(rom.pointer(header + 0x0C)),
                readObjects(rom.pointer(header + 0x04)),
                readSigns(rom.pointer(header + 0x04)),
                profile);
    }

    private MapLayout readLayout(int o) {
        int w = rom.s32(o), h = rom.s32(o + 4);
        if (w <= 0 || h <= 0 || w > MAX_DIMENSION || h > MAX_DIMENSION) {
            throw new RomException(String.format("Dimensions de carte invalides (%dx%d) à 0x%06X.", w, h, o));
        }
        int borderPtr = rom.pointer(o + 0x08);
        int gridPtr = rom.requirePointer(o + 0x0C, "la grille de la carte");
        Tileset primary = tileset(rom.pointer(o + 0x10));
        Tileset secondary = tileset(rom.pointer(o + 0x14));
        if (primary == null) throw new RomException(String.format("Tileset primaire manquant à 0x%06X.", o));

        int bw = 2, bh = 2;
        if (profile.layoutHasBorderSize) {
            bw = rom.u8(o + 0x18);
            bh = rom.u8(o + 0x19);
            if (bw == 0 || bh == 0 || bw > 16 || bh > 16) { bw = 2; bh = 2; }
        }
        int[] grid = readBlocks(gridPtr, w * h);
        int[] border = borderPtr < 0 ? new int[0] : readBlocks(borderPtr, bw * bh);
        return new MapLayout(w, h, grid, bw, bh, border, primary, secondary);
    }

    private int[] readBlocks(int offset, int count) {
        if (!rom.inRange(offset, count * 2)) {
            throw new RomException(String.format("Grille de carte hors ROM à 0x%06X.", offset));
        }
        int[] out = new int[count];
        for (int i = 0; i < count; i++) out[i] = rom.u16(offset + i * 2);
        return out;
    }

    private Tileset tileset(int offset) {
        if (offset < 0) return null;
        return tilesets.computeIfAbsent(offset, o -> new Tileset(rom, o));
    }

    private List<Warp> readWarps(int events) {
        List<Warp> out = new ArrayList<>();
        if (events < 0 || !rom.inRange(events, 0x14)) return out;
        int count = rom.u8(events + 1);
        int ptr = rom.pointer(events + 0x08);
        if (ptr < 0 || !rom.inRange(ptr, count * 8)) return out;
        for (int i = 0; i < count; i++) {
            int w = ptr + i * 8;
            out.add(new Warp(rom.s16(w), rom.s16(w + 2), rom.u8(w + 4), rom.u8(w + 5), rom.u8(w + 7), rom.u8(w + 6)));
        }
        return out;
    }

    /** Personnages de la carte : événements, compteur à +0, tableau à +4. */
    private List<ObjectEvent> readObjects(int events) {
        List<ObjectEvent> out = new ArrayList<>();
        if (events < 0 || !rom.inRange(events, 0x14)) return out;
        int count = rom.u8(events);
        int ptr = rom.pointer(events + 0x04);
        if (count == 0 || ptr < 0 || !rom.inRange(ptr, count * ObjectEvent.BYTES)) return out;
        for (int i = 0; i < count; i++) {
            int o = ptr + i * ObjectEvent.BYTES;
            int range = rom.u8(o + 0x0A);
            out.add(new ObjectEvent(rom.u8(o), rom.u8(o + 1), rom.s16(o + 4), rom.s16(o + 6), rom.u8(o + 8),
                    rom.u8(o + 9), range & 0xF, range >> 4, rom.u16(o + 0x0C), rom.pointer(o + 0x10),
                    rom.u16(o + 0x14)));
        }
        return out;
    }

    /** Panneaux : compteur à +3, tableau à +0x10. */
    private List<SignEvent> readSigns(int events) {
        List<SignEvent> out = new ArrayList<>();
        if (events < 0 || !rom.inRange(events, 0x14)) return out;
        int count = rom.u8(events + 3);
        int ptr = rom.pointer(events + 0x10);
        if (count == 0 || ptr < 0 || !rom.inRange(ptr, count * SignEvent.BYTES)) return out;
        for (int i = 0; i < count; i++) {
            int o = ptr + i * SignEvent.BYTES;
            out.add(new SignEvent(rom.u16(o), rom.u16(o + 2), rom.u8(o + 5), rom.pointer(o + 8)));
        }
        return out;
    }

    private List<Connection> readConnections(int header) {
        List<Connection> out = new ArrayList<>();
        if (header < 0 || !rom.inRange(header, 8)) return out;
        int count = rom.s32(header);
        int ptr = rom.pointer(header + 4);
        if (count <= 0 || count > 8 || ptr < 0 || !rom.inRange(ptr, count * 12)) return out;
        for (int i = 0; i < count; i++) {
            int c = ptr + i * 12;
            Connection.Direction dir = Connection.Direction.fromCode(rom.u8(c));
            if (dir == null) continue;
            int group = rom.u8(c + 8), num = rom.u8(c + 9);
            if (exists(group, num)) out.add(new Connection(dir, rom.s32(c + 4), group, num));
        }
        return out;
    }

    private String readName(int section, int group, int num) {
        String fallback = "Carte " + group + "." + num;
        if (labelsTable < 0) return fallback;
        int entry = profile == GameProfile.FIRERED_LEAFGREEN
                ? labelsTable + (section - profile.mapLabelsStart) * 4
                : labelsTable + section * 8 + 4;
        if (section < profile.mapLabelsStart) return fallback;
        int ptr = rom.pointer(entry);
        if (ptr < 0) return fallback;
        String name = TextCodec.read(rom, ptr, 32);
        return name.isEmpty() ? fallback : name;
    }
}
