package dev.orvaen.mmo.audio;

import dev.orvaen.mmo.rom.GameProfile;

/**
 * Numéros des musiques et bruitages du combat (include/constants/songs.h de la décompilation).
 * 0 : pas de son.
 */
public record BattleSounds(
        int victoryWild, int victoryTrainer, int levelUp, int caught,
        int hitNormal, int hitWeak, int hitSuper, int faint, int flee,
        int ballThrow, int ballOpen, int ballShake, int ballBounce, int ballClick, int exp, int select,
        int statUp, int statDown) {

    public static final BattleSounds FIRERED = new BattleSounds(
            311, 310, 257, 322,
            13, 12, 14, 16, 17,
            54, 15, 23, 49, 247, 27, 5,
            232, 238);

    public static final BattleSounds EMERALD = new BattleSounds(
            353, 412, 367, 352,
            13, 12, 14, 16, 17,
            61, 15, 23, 56, 0, 33, 5,
            239, 245);

    public static BattleSounds of(GameProfile p) {
        return p == GameProfile.EMERALD ? EMERALD : FIRERED;
    }
}
