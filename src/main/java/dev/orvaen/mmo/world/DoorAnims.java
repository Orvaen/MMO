package dev.orvaen.mmo.world;

import dev.orvaen.mmo.gfx.GbaGfx;
import dev.orvaen.mmo.rom.Rom;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Animations d'ouverture des portes, lues dans la ROM du joueur.
 * <p>
 * Le jeu garde une table d'entrées de 12 octets, terminée par une entrée nulle :
 * <pre>
 * 0x00 u16 métatile de la porte   0x02 u8 son   0x03 u8 taille (0 = 1x1, 1 = 1x2)
 * 0x04 ptr tuiles (3 étapes de 4 ou 8 tuiles 4bpp, non compressées)
 * 0x08 ptr n° de palette de chaque tuile (8 octets)
 * </pre>
 * Elle est retrouvée par sa forme, sans adresse codée en dur. Chaque étape dure 4 frames :
 * fermée, puis les 3 étapes ; la fermeture rejoue l'inverse.
 */
public final class DoorAnims {

    /** Étapes d'animation d'une porte (fermée exclue). */
    public static final int STAGES = 3;
    /** Durée d'une étape, en frames (comme sur GBA). */
    public static final int STAGE_FRAMES = 4;

    private static final int ENTRY = 12;
    private static final int MIN_ENTRIES = 6;

    public record Door(int metatile, boolean big, int tiles, int paletteNums) {}

    private final Rom rom;
    private final Map<Integer, Door> doors = new HashMap<>();
    private final Map<String, BufferedImage[]> cache = new HashMap<>();

    public DoorAnims(Rom rom) {
        this.rom = rom;
        int table = find(rom);
        if (table >= 0) {
            for (int e = table; valid(rom, e); e += ENTRY) {
                Door d = new Door(rom.u16(e), rom.u8(e + 3) == 1, rom.pointer(e + 4), rom.pointer(e + 8));
                doors.putIfAbsent(d.metatile, d);
            }
        }
    }

    public int count() {
        return doors.size();
    }

    /** Porte animée posée sur ce métatile, ou {@code null}. */
    public Door at(int metatileId) {
        return doors.get(metatileId);
    }

    /** Hauteur en cases de l'image d'une porte (1 ou 2 : la case du dessus fait partie de la porte). */
    public static int heightInTiles(Door d) {
        return d.big ? 2 : 1;
    }

    /** Les 3 étapes d'ouverture, rendues avec les palettes de la carte. */
    public BufferedImage[] frames(Door d, MapLayout layout, MapRenderer renderer) {
        String key = d.metatile + "@" + layout.primary.offset + "/" + (layout.secondary == null ? -1 : layout.secondary.offset);
        return cache.computeIfAbsent(key, k -> build(d, renderer.palettes(layout)));
    }

    private BufferedImage[] build(Door d, int[] palettes) {
        int perStage = d.big ? 8 : 4;
        int h = heightInTiles(d) * MapRenderer.TILE;
        byte[] tiles = rom.slice(d.tiles, STAGES * perStage * GbaGfx.TILE_BYTES_4BPP);
        int[] pal = new int[8];
        for (int i = 0; i < 8; i++) pal[i] = rom.u8(d.paletteNums + i) & 0xF;
        BufferedImage[] out = new BufferedImage[STAGES];
        for (int s = 0; s < STAGES; s++) {
            int[] px = new int[MapRenderer.TILE * h];
            Arrays.fill(px, palettes[0] | 0xFF00_0000); // sous la porte : le fond, comme sur GBA
            for (int i = 0; i < perStage; i++) {
                int quadrant = i & 3;
                int tx = (quadrant & 1) * 8;
                int ty = (i >> 2) * MapRenderer.TILE + (quadrant >> 1) * 8;
                GbaGfx.drawTile(px, MapRenderer.TILE, tx, ty, tiles, s * perStage + i, palettes, pal[i], false, false);
            }
            out[s] = GbaGfx.toImage(px, MapRenderer.TILE, h);
        }
        return out;
    }

    // ------------------------------------------------------------------ recherche de la table

    static int find(Rom rom) {
        for (int o = 0; o + ENTRY * (MIN_ENTRIES + 1) <= rom.size(); o += 4) {
            int n = 0;
            while (n <= MIN_ENTRIES && valid(rom, o + n * ENTRY)) n++;
            if (n < MIN_ENTRIES) continue;
            int end = o;
            while (valid(rom, end)) end += ENTRY;
            if (isZero(rom, end)) return o;
            o = end - 4; // pas une table : on reprend après
        }
        return -1;
    }

    private static boolean valid(Rom rom, int e) {
        if (!rom.inRange(e, ENTRY)) return false;
        int metatile = rom.u16(e);
        if (metatile == 0 || metatile > MapLayout.METATILE_MASK) return false;
        if (rom.u8(e + 2) > 3 || rom.u8(e + 3) > 1) return false;
        int tiles = rom.pointer(e + 4), pals = rom.pointer(e + 8);
        int perStage = rom.u8(e + 3) == 1 ? 8 : 4;
        if (tiles < 0 || !rom.inRange(tiles, STAGES * perStage * GbaGfx.TILE_BYTES_4BPP)) return false;
        if (pals < 0 || !rom.inRange(pals, 8)) return false;
        for (int i = 0; i < 8; i++) if (rom.u8(pals + i) > 15) return false;
        return true;
    }

    private static boolean isZero(Rom rom, int e) {
        if (!rom.inRange(e, ENTRY)) return false;
        for (int i = 0; i < ENTRY; i++) if (rom.u8(e + i) != 0) return false;
        return true;
    }
}
