package dev.orvaen.mmo.script;

import dev.orvaen.mmo.rom.Rom;

/**
 * Données fixes du langage de script : longueur des arguments de chaque commande (pour sauter
 * proprement celles que le moteur n'exécute pas), et script « début de partie » de la ROM.
 * Longueurs tirées des macros de la décompilation de Rouge Feu (pret/pokefirered).
 */
public final class ScriptTables {

    private static final int[] ARGS = {
            0, 0, 0, 0, 4, 4, 5, 5, 1, 1, 2, 2, 0, 0, 1, 5, 2, 5, 5, 5, 2, 8, 4, 4, 4, 4, 4, 2, 2, 5, 5, 5,
            8, 4, 4, 4, 4, 2, 4, 0, 2, 2, 2, 2, 4, 0, 0, 2, 0, 2, 0, 3, 2, 0, 2, 1, 1, 7, 7, 7, 2, 7, 7, 7,
            7, 2, 4, 0, 4, 4, 4, 4, 2, 4, 4, 2, 2, 2, 2, 6, 8, 2, 4, 2, 4, 2, 4, 6, 4, 4, 0, 3, -1, 0, 0, 0,
            2, 2, 2, 6, 2, 3, 0, 4, 0, 0, 0, 0, 0, 0, 2, 4, 5, 5, 0, 4, 4, 4, 0, 1, 4, 14, 2, 4, 2, 3, 1, 3,
            3, 3, 3, 3, 3, 5, 4, 4, 4, 2, 3, 0, 0, 0, 0, 2, 5, 5, 5, 3, 2, 3, 2, 1, 2, 2, 1, 4, 2, 3, 2, 2,
            0, 4, 8, 0, 2, 0, 1, 2, 5, 4, 8, 2, 4, 4, 0, 4, 4, 7, 0, 2, 2, 2, 5, 0, 4, 4, 4, 5, 5, 4, 4, 5,
            2, 2, 2, 1, 0, 0, 3, 1, 4, 0, 0, 0, 5, 2, 2, 0, 2, 0, 3, 4, 5
    };

    private static final int SETFLAG = 0x29, END = 0x02;
    private static final int MIN_RESET = 30;

    private final int resetScript;

    public ScriptTables(Rom rom) {
        resetScript = findReset(rom);
    }

    /** Octets d'arguments de la commande {@code op}, ou -1 si inconnue (le script s'arrête). */
    public int argLength(int op) {
        return op >= 0 && op < ARGS.length ? ARGS[op] : -1;
    }

    /**
     * Script lancé en début de partie : une longue suite de {@code setflag} qui cache les personnages
     * d'histoire pas encore apparus (le Professeur dans son labo, le rival à Azuria…), puis {@code end}.
     */
    public int resetScript() {
        return resetScript;
    }

    private static int findReset(Rom rom) {
        int run = 0, start = -1;
        for (int o = 0; o + 3 <= rom.size(); ) {
            if (rom.u8(o) == SETFLAG && rom.u16(o + 1) >= 0x20 && rom.u16(o + 1) < 0x1000) {
                if (run == 0) start = o;
                run++;
                o += 3;
                continue;
            }
            if (run >= MIN_RESET && (rom.u8(o) == END || rom.u8(o) == 0x16)) return start;
            if (run > 0) {
                o = start + 1;
                run = 0;
            } else {
                o++;
            }
        }
        return -1;
    }
}
