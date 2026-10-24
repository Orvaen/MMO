package dev.orvaen.mmo.battle;

/**
 * Numéros des effets d'attaque utilisés par la ROM (include/constants/battle_move_effects.h de la
 * décompilation). Seuls ceux que le moteur sait jouer sont listés ; les autres font des dégâts normaux
 * (ou rien, pour une attaque de statut) et le combat affiche « Mais rien ne se passe ! ».
 */
final class Effects {

    private Effects() {}

    static final int HIT = 0, SLEEP = 1, POISON_HIT = 2, ABSORB = 3, BURN_HIT = 4, FREEZE_HIT = 5,
            PARALYZE_HIT = 6, EXPLOSION = 7, DREAM_EATER = 8;
    static final int ATTACK_UP = 10, DEFENSE_UP = 11, SPEED_UP = 12, SP_ATTACK_UP = 13, SP_DEFENSE_UP = 14,
            ACCURACY_UP = 15, EVASION_UP = 16, ALWAYS_HIT = 17;
    static final int ATTACK_DOWN = 18, DEFENSE_DOWN = 19, SPEED_DOWN = 20, SP_ATTACK_DOWN = 21,
            SP_DEFENSE_DOWN = 22, ACCURACY_DOWN = 23, EVASION_DOWN = 24, HAZE = 25;
    static final int MULTI_HIT = 29, FLINCH_HIT = 31, RESTORE_HP = 32, TOXIC = 33, PAY_DAY = 34, TRI_ATTACK = 36,
            REST = 37, SUPER_FANG = 40, DRAGON_RAGE = 41, HIGH_CRITICAL = 43, DOUBLE_HIT = 44, RECOIL = 48,
            CONFUSE = 49;
    static final int ATTACK_UP_2 = 50, DEFENSE_UP_2 = 51, SPEED_UP_2 = 52, SP_ATTACK_UP_2 = 53, SP_DEFENSE_UP_2 = 54,
            ACCURACY_UP_2 = 55, EVASION_UP_2 = 56;
    static final int ATTACK_DOWN_2 = 58, DEFENSE_DOWN_2 = 59, SPEED_DOWN_2 = 60, SP_ATTACK_DOWN_2 = 61,
            SP_DEFENSE_DOWN_2 = 62, ACCURACY_DOWN_2 = 63, EVASION_DOWN_2 = 64;
    static final int POISON = 66, PARALYZE = 67;
    static final int ATTACK_DOWN_HIT = 68, DEFENSE_DOWN_HIT = 69, SPEED_DOWN_HIT = 70, SP_ATTACK_DOWN_HIT = 71,
            SP_DEFENSE_DOWN_HIT = 72, ACCURACY_DOWN_HIT = 73, EVASION_DOWN_HIT = 74;
    static final int CONFUSE_HIT = 76, TWINEEDLE = 77, VITAL_THROW = 78, LEECH_SEED = 84, SPLASH = 85,
            LEVEL_DAMAGE = 87, PSYWAVE = 88;
    static final int QUICK_ATTACK = 103, TRIPLE_KICK = 104, FALSE_SWIPE = 101, THAW_HIT = 125, SONICBOOM = 130;
    static final int MORNING_SUN = 132, SYNTHESIS = 133, MOONLIGHT = 134;
    static final int DEFENSE_UP_HIT = 138, ATTACK_UP_HIT = 139, DEFENSE_CURL = 156, SOFTBOILED = 157,
            WILL_O_WISP = 167, BLAZE_KICK = 200, POISON_FANG = 202, POISON_TAIL = 209, DOUBLE_EDGE = 198,
            TELEPORT = 153, FLINCH_MINIMIZE_HIT = 150, TWISTER = 146, GUST = 149, THUNDER = 152;
    static final int COSMIC_POWER = 206, BULK_UP = 208, CALM_MIND = 211, DRAGON_DANCE = 212;
}
