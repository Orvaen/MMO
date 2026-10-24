package dev.orvaen.mmo.player;

import dev.orvaen.mmo.data.PlayerLook.Part;

import java.util.List;

/**
 * Quelle partie du sprite (couvre-chef, cheveux, haut…) correspond à chaque pixel.
 * <p>
 * Un même index de palette sert parfois à plusieurs pièces (le rouge de la casquette est aussi
 * celui du haut), d'où des règles « index + zone ». Elles ont été relevées sur les sprites des
 * héros de Rouge Feu / Vert Feuille ; la première règle qui correspond l'emporte.
 * Ce fichier ne contient que des numéros d'index et des coordonnées, aucun graphisme.
 */
public final class CosmeticMasks {

    /** Orientation dans les frames de marche (bitmask). */
    public static final int SOUTH = 1, NORTH = 2, WEST = 4, ALL = 7;

    public record Rule(Part part, int indexMask, int x0, int y0, int x1, int y1, int facings) {
        boolean matches(int index, int x, int y, int facing) {
            return (indexMask >> index & 1) != 0 && x >= x0 && x <= x1 && y >= y0 && y <= y1
                    && (facings & facing) != 0;
        }
    }

    private CosmeticMasks() {}

    /** Partie du pixel, ou {@code null} s'il garde sa couleur d'origine. */
    public static Part partAt(List<Rule> rules, int index, int x, int y, int facing) {
        if (index == 0) return null;
        for (Rule r : rules) {
            if (r.matches(index, x, y, facing)) return r.part;
        }
        return null;
    }

    /** Index de palette concernés par une partie (pour calculer l'éclairage de référence). */
    public static int indexMask(List<Rule> rules, Part part) {
        int m = 0;
        for (Rule r : rules) if (r.part == part) m |= r.indexMask;
        return m;
    }

    private static Rule r(Part p, String hexIndices, int x0, int y0, int x1, int y1) {
        return new Rule(p, mask(hexIndices), x0, y0, x1, y1, ALL);
    }

    private static Rule r(Part p, String hexIndices, int x0, int y0, int x1, int y1, int facings) {
        return new Rule(p, mask(hexIndices), x0, y0, x1, y1, facings);
    }

    static int mask(String hexIndices) {
        int m = 0;
        for (char c : hexIndices.toCharArray()) m |= 1 << Character.digit(c, 16);
        return m;
    }

    // ---------------------------------------------------------------- sprites de marche (16x32)

    /** Héros, sur le monde. Coordonnées des frames immobiles (les frames de marche sont décalées de 1 px). */
    public static final List<Rule> OVERWORLD_BOY = List.of(
            r(Part.HAT, "8bc", 0, 12, 15, 20),
            r(Part.HAIR, "1", 0, 0, 15, 31),
            r(Part.HAIR, "4", 0, 21, 15, 22, NORTH),
            r(Part.TOP, "8bc", 0, 21, 15, 27),
            r(Part.BOTTOM, "567", 0, 27, 15, 31, SOUTH | NORTH),
            r(Part.BOTTOM, "567", 0, 25, 15, 31, WEST),
            r(Part.SHOES, "8bc", 0, 28, 15, 31),
            r(Part.BAG, "de", 0, 0, 15, 31),
            r(Part.SKIN, "234", 0, 17, 15, 31)
    );

    /** Héroïne, sur le monde. Le ruban rouge du chapeau reste d'origine. */
    public static final List<Rule> OVERWORLD_GIRL = List.of(
            r(Part.HAT, "69a", 0, 12, 15, 20),
            r(Part.HAIR, "14", 0, 18, 3, 26, SOUTH),
            r(Part.HAIR, "14", 12, 18, 15, 26, SOUTH),
            r(Part.HAIR, "14", 0, 18, 15, 27, NORTH),
            r(Part.HAIR, "148", 6, 18, 15, 28, WEST),
            r(Part.TOP, "567", 0, 21, 15, 27),
            r(Part.BOTTOM, "8bc", 0, 21, 15, 29),
            r(Part.SHOES, "59a", 0, 28, 15, 31),
            r(Part.BAG, "de", 0, 0, 15, 31),
            r(Part.SKIN, "234", 0, 17, 15, 31)
    );

    // ---------------------------------------------------------------- portraits de face (64x64)

    public static final List<Rule> FRONT_BOY = List.of(
            r(Part.HAT, "cd", 20, 8, 44, 16),
            r(Part.TOP, "cd", 20, 17, 41, 40),
            r(Part.BOTTOM, "5678", 18, 40, 46, 57),
            r(Part.SHOES, "cd789", 18, 58, 46, 63),
            r(Part.BAG, "ab", 0, 0, 63, 63),
            r(Part.SKIN, "123", 0, 0, 63, 63),
            r(Part.SKIN, "4", 20, 17, 40, 24),
            r(Part.SKIN, "4", 0, 17, 24, 40),
            r(Part.SKIN, "4", 40, 28, 52, 40)
    );

    public static final List<Rule> FRONT_GIRL = List.of(
            r(Part.HAT, "9e", 20, 8, 42, 17),
            r(Part.HAIR, "348", 35, 16, 50, 34),
            r(Part.HAIR, "48", 22, 16, 36, 19),
            r(Part.TOP, "5678", 24, 23, 40, 36),
            r(Part.BOTTOM, "cd", 23, 36, 37, 41),
            r(Part.SHOES, "5679de", 18, 52, 42, 63),
            r(Part.BAG, "ab", 0, 0, 63, 63),
            r(Part.SKIN, "123", 0, 0, 63, 63),
            r(Part.SKIN, "4", 22, 41, 42, 63)
    );

    // ---------------------------------------------------------------- vue de dos en combat (64x64)

    public static final List<Rule> BACK_BOY = List.of(
            r(Part.HAT, "bc", 28, 16, 52, 28),
            r(Part.HAIR, "48", 20, 28, 44, 40),
            r(Part.TOP, "bc", 25, 41, 45, 47),
            r(Part.BAG, "de", 0, 0, 63, 63),
            r(Part.SKIN, "23", 0, 0, 63, 63),
            r(Part.SKIN, "4", 45, 29, 52, 40)
    );

    public static final List<Rule> BACK_GIRL = List.of(
            r(Part.HAT, "69a", 12, 15, 48, 30),
            r(Part.HAIR, "148", 10, 30, 34, 56),
            r(Part.TOP, "567", 23, 49, 42, 63),
            r(Part.BAG, "de", 0, 0, 63, 63),
            r(Part.SKIN, "23", 0, 0, 63, 63),
            r(Part.SKIN, "4", 0, 56, 26, 63),
            r(Part.SKIN, "4", 35, 31, 44, 42)
    );
}
