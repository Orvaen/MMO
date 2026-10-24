package dev.orvaen.mmo.data;

import dev.orvaen.mmo.rom.GameProfile;
import dev.orvaen.mmo.rom.Rom;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Tables de rencontres sauvages (herbe / grotte) lues dans la ROM.
 * <pre>
 * entrée (20 o) : u8 groupe · u8 n° · 2 o vides · ptr herbe · ptr eau · ptr éclate-roc · ptr pêche
 * table herbe   : u8 taux · 3 o vides · ptr → 12 x { u8 niv. min · u8 niv. max · u16 espèce }
 * </pre>
 */
public final class WildEncounters {

    /** Probabilités (en %) des 12 emplacements d'herbe, identiques dans tous les jeux Gen 3. */
    private static final int[] LAND_SLOT_RATES = {20, 20, 10, 10, 10, 10, 5, 5, 4, 4, 1, 1};

    public record Slot(int minLevel, int maxLevel, int species) {}

    public record Table(int rate, List<Slot> slots) {
        /** Tire une rencontre selon les probabilités officielles des emplacements. */
        public Slot roll(RandomGenerator rng) {
            int r = rng.nextInt(100);
            for (int i = 0; i < slots.size(); i++) {
                r -= LAND_SLOT_RATES[i];
                if (r < 0) return slots.get(i);
            }
            return slots.get(slots.size() - 1);
        }
    }

    private final Map<String, Table> land = new HashMap<>();

    public WildEncounters(Rom rom, int speciesCount) {
        int sig = rom.find(GameProfile.SIG_WILD_ENCOUNTERS);
        if (sig < 0) return; // pas de rencontres : le jeu reste jouable
        int table = rom.pointer(sig + GameProfile.SIG_POINTER_DISTANCE);
        if (table < 0) return;

        for (int o = table; rom.inRange(o, 20); o += 20) {
            int group = rom.u8(o), num = rom.u8(o + 1);
            if (group == 0xFF && num == 0xFF) break;
            int landPtr = rom.pointer(o + 4);
            if (landPtr < 0 || !rom.inRange(landPtr, 8)) continue;
            int rate = rom.u8(landPtr);
            int slotsPtr = rom.pointer(landPtr + 4);
            if (rate == 0 || slotsPtr < 0 || !rom.inRange(slotsPtr, LAND_SLOT_RATES.length * 4)) continue;
            Slot[] slots = new Slot[LAND_SLOT_RATES.length];
            boolean valid = true;
            for (int i = 0; i < slots.length; i++) {
                int s = slotsPtr + i * 4;
                int species = rom.u16(s + 2);
                if (species <= 0 || species >= speciesCount) { valid = false; break; }
                slots[i] = new Slot(rom.u8(s), rom.u8(s + 1), species);
            }
            if (valid) land.putIfAbsent(group + "." + num, new Table(rate, List.of(slots)));
        }
    }

    public Optional<Table> land(int group, int num) {
        return Optional.ofNullable(land.get(group + "." + num));
    }

    public int mapsWithLandEncounters() {
        return land.size();
    }
}
