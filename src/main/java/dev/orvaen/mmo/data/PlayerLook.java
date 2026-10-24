package dev.orvaen.mmo.data;

import java.util.random.RandomGenerator;

/**
 * Apparence personnalisée du joueur : un gabarit (garçon / fille) et une couleur par pièce.
 * Les couleurs sont des index dans des palettes fixes ; l'index 0 = couleur d'origine du sprite.
 * Tient dans une courte chaîne (sauvegarde, et plus tard envoi aux autres joueurs).
 */
public record PlayerLook(int body, int skin, int hair, int hat, int top, int bottom, int shoes, int bag) {

    /** Pièces recolorables, dans l'ordre des champs après {@code body}. */
    public enum Part {
        SKIN("Peau"), HAIR("Cheveux"), HAT("Couvre-chef"), TOP("Haut"), BOTTOM("Bas"), SHOES("Chaussures"), BAG("Sac");

        public final String label;

        Part(String label) {
            this.label = label;
        }
    }

    public static final String[] BODIES = {"Garçon", "Fille"};

    /** Teintes de peau (index 0 = d'origine). */
    public static final int[] SKIN_TONES = {
            -1, 0xFCE3CC, 0xF4C9A3, 0xE0AC80, 0xC08A5E, 0x9A6A44, 0x6E4A2E, 0x4A3020
    };
    public static final String[] SKIN_NAMES = {
            "Origine", "Très claire", "Claire", "Mate", "Hâlée", "Brune", "Foncée", "Ébène"
    };

    /** Teintures pour cheveux et vêtements (index 0 = d'origine). */
    public static final int[] DYES = {
            -1,
            0xE63946, 0xB5172E, 0xFF7A3D, 0xF4A261, 0xFFD23F, 0xC9B458,
            0x8AC926, 0x2D9D4A, 0x1B5E3B, 0x2A9D8F, 0x00B4D8, 0x4CC9F0,
            0x3A86FF, 0x1D3557, 0x5E60CE, 0x9B5DE5, 0x6A2C91, 0xF15BB5,
            0xFF8FAB, 0xB56576, 0x8D5B3A, 0x5A3825, 0xF1FAEE, 0xC0C4CC,
            0x6C757D, 0x2B2D42, 0x111111
    };
    public static final String[] DYE_NAMES = {
            "Origine",
            "Rouge", "Bordeaux", "Orange", "Pêche", "Jaune", "Moutarde",
            "Vert pomme", "Vert", "Sapin", "Turquoise", "Cyan", "Ciel",
            "Bleu", "Marine", "Indigo", "Violet", "Prune", "Fuchsia",
            "Rose", "Vieux rose", "Caramel", "Chocolat", "Blanc", "Argent",
            "Gris", "Anthracite", "Noir"
    };

    private static final String PREFIX = "v2.";

    public PlayerLook {
        body = Math.floorMod(body, BODIES.length);
        skin = Math.floorMod(skin, SKIN_TONES.length);
        hair = Math.floorMod(hair, DYES.length);
        hat = Math.floorMod(hat, DYES.length);
        top = Math.floorMod(top, DYES.length);
        bottom = Math.floorMod(bottom, DYES.length);
        shoes = Math.floorMod(shoes, DYES.length);
        bag = Math.floorMod(bag, DYES.length);
    }

    public static PlayerLook defaults() {
        return new PlayerLook(0, 0, 0, 0, 0, 0, 0, 0);
    }

    public static PlayerLook random(RandomGenerator rng) {
        return new PlayerLook(rng.nextInt(BODIES.length), rng.nextInt(SKIN_TONES.length),
                rng.nextInt(DYES.length), rng.nextInt(DYES.length), rng.nextInt(DYES.length),
                rng.nextInt(DYES.length), rng.nextInt(DYES.length), rng.nextInt(DYES.length));
    }

    /** Index de couleur choisi pour une pièce. */
    public int colorIndex(Part p) {
        return switch (p) {
            case SKIN -> skin;
            case HAIR -> hair;
            case HAT -> hat;
            case TOP -> top;
            case BOTTOM -> bottom;
            case SHOES -> shoes;
            case BAG -> bag;
        };
    }

    /** Couleur RGB choisie pour une pièce, ou -1 pour garder celle du sprite. */
    public int rgb(Part p) {
        int i = colorIndex(p);
        return p == Part.SKIN ? SKIN_TONES[i] : DYES[i];
    }

    public static int choices(Part p) {
        return p == Part.SKIN ? SKIN_TONES.length : DYES.length;
    }

    public static String colorName(Part p, int index) {
        return p == Part.SKIN ? SKIN_NAMES[index] : DYE_NAMES[index];
    }

    public PlayerLook withBody(int b) {
        return new PlayerLook(b, skin, hair, hat, top, bottom, shoes, bag);
    }

    /** Copie avec la couleur de la pièce décalée de {@code delta} (on boucle aux extrémités). */
    public PlayerLook cycle(Part p, int delta) {
        int v = Math.floorMod(colorIndex(p) + delta, choices(p));
        return switch (p) {
            case SKIN -> new PlayerLook(body, v, hair, hat, top, bottom, shoes, bag);
            case HAIR -> new PlayerLook(body, skin, v, hat, top, bottom, shoes, bag);
            case HAT -> new PlayerLook(body, skin, hair, v, top, bottom, shoes, bag);
            case TOP -> new PlayerLook(body, skin, hair, hat, v, bottom, shoes, bag);
            case BOTTOM -> new PlayerLook(body, skin, hair, hat, top, v, shoes, bag);
            case SHOES -> new PlayerLook(body, skin, hair, hat, top, bottom, v, bag);
            case BAG -> new PlayerLook(body, skin, hair, hat, top, bottom, shoes, v);
        };
    }

    public String encode() {
        return PREFIX + body + "." + skin + "." + hair + "." + hat + "." + top + "." + bottom + "." + shoes + "." + bag;
    }

    /**
     * Relit une apparence ; renvoie {@code null} si la chaîne est absente ou abîmée.
     * Les apparences de l'ancien format (9 champs, avatar dessiné) sont converties.
     */
    public static PlayerLook decode(String s) {
        if (s == null || s.isBlank()) return null;
        s = s.trim();
        boolean v2 = s.startsWith(PREFIX);
        String[] parts = (v2 ? s.substring(PREFIX.length()) : s).split("\\.");
        int[] v = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) v[i] = Integer.parseInt(parts[i]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (v2 && v.length == 8) return new PlayerLook(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7]);
        if (!v2 && v.length == 9) {
            // ancien format : peau, coiffure, cheveux, chapeau, couleur chapeau, haut, couleur haut, bas, chaussures
            int body = v[1] == 1 || v[1] == 2 || v[1] == 3 ? 1 : 0;
            return new PlayerLook(body, 0, 0, 0, 0, 0, 0, 0);
        }
        return null;
    }
}
