package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.engine.Storage;
import dev.orvaen.mmo.rom.Rom;
import dev.orvaen.mmo.world.GameMap;

/**
 * Les scripts attachés à une carte (en-tête de carte, champ « scripts »).
 * <p>
 * Dans la ROM, une carte a une petite liste d'entrées {type (1 octet), pointeur (4 octets)} terminée par 0 :
 * <ul>
 *   <li>{@link #ON_LOAD}, {@link #ON_TRANSITION}, {@link #ON_RESUME} : un script lancé à l'arrivée ;</li>
 *   <li>{@link #ON_FRAME}, {@link #ON_WARP_INTO} : une <b>table</b> d'entrées {variable, valeur, script} —
 *       le script part quand la variable a cette valeur (ex. : « le Professeur t'arrête dans les hautes herbes »).</li>
 * </ul>
 */
final class MapScripts {

    static final int ON_LOAD = 1, ON_FRAME = 2, ON_TRANSITION = 3, ON_WARP_INTO = 4, ON_RESUME = 5;

    /** Drapeaux 0x00-0x1F et variables 0x4000-0x400F : temporaires, remis à zéro à chaque carte. */
    private static final int TEMP_FLAGS_END = 0x20, TEMP_VARS = 0x4000, TEMP_VARS_END = 0x4010;

    private MapScripts() {}

    /** Script de ce type pour la carte, ou -1. */
    static int find(Rom rom, GameMap map, int type) {
        int o = map.scripts;
        if (o < 0) return -1;
        for (int i = 0; i < 16 && rom.inRange(o, 5); i++, o += 5) {
            int t = rom.u8(o);
            if (t == 0) break;
            if (t == type) return rom.pointer(o + 1);
        }
        return -1;
    }

    /** Dans une table {variable, valeur, script} : le premier script dont la condition est vraie, ou -1. */
    static int fromTable(Rom rom, Storage.Save save, GameMap map, int type) {
        int table = find(rom, map, type);
        if (table < 0) return -1;
        for (int e = table; rom.inRange(e, 8); e += 8) {
            int var = rom.u16(e);
            if (var == 0) break;
            if (varValue(save, var) == varValue(save, rom.u16(e + 2))) return rom.pointer(e + 4);
        }
        return -1;
    }

    /** Valeur d'une variable de script (ou le nombre lui-même si ce n'est pas un n° de variable). */
    static int varValue(Storage.Save save, int id) {
        if (id >= 0x4000 && id < 0x8000) return save.vars.getOrDefault(id, 0);
        return id;
    }

    /** Efface les drapeaux et variables temporaires (à chaque arrivée sur une carte, comme le jeu). */
    static void clearTemporary(Storage.Save save) {
        save.flags.clear(0, TEMP_FLAGS_END);
        for (int v = TEMP_VARS; v < TEMP_VARS_END; v++) save.vars.remove(v);
    }
}
