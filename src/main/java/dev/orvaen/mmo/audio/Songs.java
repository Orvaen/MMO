package dev.orvaen.mmo.audio;

import dev.orvaen.mmo.rom.GameProfile;

/**
 * Numéros des morceaux utilisés directement par le moteur (les musiques des cartes et des scripts,
 * elles, sont indiquées par la ROM). Valeurs de la décompilation (include/constants/songs.h).
 */
public record Songs(int title, int intro, int introExit, int vsWild, int vsTrainer, int victoryWild, int caught,
                    int select, int door, int exit) {

    public static final Songs FIRERED = new Songs(278, 292, 325, 298, 297, 311, 322, 5, 241, 9);
    public static final Songs EMERALD = new Songs(413, 0, 0, 474, 476, 353, 352, 5, 8, 9);

    public static Songs of(GameProfile p) {
        return p == GameProfile.EMERALD ? EMERALD : FIRERED;
    }
}
