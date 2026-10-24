package dev.orvaen.mmo.data;

/** Données d'une espèce lues dans la ROM du joueur. */
public record Species(
        int id,
        String name,
        int hp, int attack, int defense, int speed, int spAttack, int spDefense,
        int type1, int type2,
        int catchRate,
        int expYield,
        int evYield,
        int growthRate,
        int ability1, int ability2) {

    /**
     * EV donnés quand on bat cette espèce, pour la stat {@code stat} (0 PV, 1 Att, 2 Déf, 3 Vit, 4 Att.Spé,
     * 5 Déf.Spé) : 2 bits par stat dans la ROM.
     */
    public int evYield(int stat) {
        return evYield >> (stat * 2) & 3;
    }

    /** Talent n° {@code slot} (0 ou 1) ; le 2e talent vide revient au 1er. */
    public int ability(int slot) {
        return slot == 1 && ability2 != 0 ? ability2 : ability1;
    }

    private static final String[] TYPE_NAMES = {
            "Normal", "Combat", "Vol", "Poison", "Sol", "Roche", "Insecte", "Spectre", "Acier", "???",
            "Feu", "Eau", "Plante", "Électrik", "Psy", "Glace", "Dragon", "Ténèbres"
    };

    private static final int[] TYPE_COLORS = {
            0xA8A878, 0xC03028, 0xA890F0, 0xA040A0, 0xE0C068, 0xB8A038, 0xA8B820, 0x705898, 0xB8B8D0, 0x68A090,
            0xF08030, 0x6890F0, 0x78C850, 0xF8D030, 0xF85888, 0x98D8D8, 0x7038F8, 0x705848
    };

    /** Les emplacements 252 à 276 des jeux Gen 3 sont des espèces vides (« ? »). */
    public boolean isPlaceholder() {
        return id == 0 || name.isBlank() || name.equals("?");
    }

    public int baseStatTotal() {
        return hp + attack + defense + speed + spAttack + spDefense;
    }

    public boolean hasSecondType() {
        return type2 != type1;
    }

    public static String typeName(int type) {
        return type >= 0 && type < TYPE_NAMES.length ? TYPE_NAMES[type] : "?";
    }

    public static int typeColor(int type) {
        return type >= 0 && type < TYPE_COLORS.length ? TYPE_COLORS[type] : 0x888888;
    }

    /** PV au niveau donné (IV moyens, sans EV) — formule Gen 3. */
    public int maxHpAt(int level) {
        return (2 * hp + 15) * level / 100 + level + 10;
    }

    /** Autre statistique au niveau donné (IV moyens, sans EV, nature neutre). */
    public static int statAt(int base, int level) {
        return (2 * base + 15) * level / 100 + 5;
    }
}
