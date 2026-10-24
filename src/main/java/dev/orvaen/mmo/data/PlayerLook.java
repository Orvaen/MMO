package dev.orvaen.mmo.data;

import java.util.random.RandomGenerator;

/**
 * Apparence personnalisée du joueur : un gabarit (garçon / fille) et une couleur par pièce.
 * Chaque couleur est un RVB libre (0xRRGGBB, choisi au menu ou à la roue de couleur),
 * ou {@link #ORIGIN} pour garder celle du sprite. Tient dans une courte chaîne
 * (sauvegarde, et plus tard envoi aux autres joueurs).
 */
public record PlayerLook(int body, int skin, int hair, int hat, int top, int bottom, int shoes, int bag) {

    /** Couleur d'origine du sprite. */
    public static final int ORIGIN = -1;

    /** Pièces recolorables, dans l'ordre des champs après {@code body}. */
    public enum Part {
        SKIN("Teint de peau"), HAIR("Cheveux"), HAT("Chapeau"), TOP("Haut"), BOTTOM("Jambes"),
        SHOES("Chaussures"), BAG("Dos");

        public final String label;

        Part(String label) {
            this.label = label;
        }
    }

    public static final String[] BODIES = {"Homme", "Femme"};

    /** Teintes de peau proposées (index 0 = d'origine). */
    public static final int[] SKIN_TONES = {
            ORIGIN, 0xFCE3CC, 0xF4C9A3, 0xE0AC80, 0xC08A5E, 0x9A6A44, 0x6E4A2E, 0x4A3020
    };
    public static final String[] SKIN_NAMES = {
            "Teint d'origine", "Teint de peau #1", "Teint de peau #2", "Teint de peau #3", "Teint de peau #4",
            "Teint de peau #5", "Teint de peau #6", "Teint de peau #7"
    };

    /** Teintures proposées pour cheveux et vêtements (index 0 = d'origine). */
    public static final int[] DYES = {
            ORIGIN,
            0xE63946, 0xB5172E, 0xFF7A3D, 0xF4A261, 0xFFD23F, 0xC9B458,
            0x8AC926, 0x2D9D4A, 0x1B5E3B, 0x2A9D8F, 0x00B4D8, 0x4CC9F0,
            0x3A86FF, 0x1D3557, 0x5E60CE, 0x9B5DE5, 0x6A2C91, 0xF15BB5,
            0xFF8FAB, 0xB56576, 0x8D5B3A, 0x5A3825, 0xF1FAEE, 0xC0C4CC,
            0x6C757D, 0x2B2D42, 0x111111
    };
    public static final String[] DYE_NAMES = {
            "D'origine",
            "Rouge", "Bordeaux", "Orange", "Pêche", "Jaune", "Moutarde",
            "Vert pomme", "Vert", "Sapin", "Turquoise", "Cyan", "Ciel",
            "Bleu", "Marine", "Indigo", "Violet", "Prune", "Fuchsia",
            "Rose", "Vieux rose", "Caramel", "Chocolat", "Blanc", "Argent",
            "Gris", "Anthracite", "Noir"
    };

    private static final String V3 = "v3.";
    private static final String V2 = "v2.";

    public PlayerLook {
        body = Math.floorMod(body, BODIES.length);
        skin = clean(skin);
        hair = clean(hair);
        hat = clean(hat);
        top = clean(top);
        bottom = clean(bottom);
        shoes = clean(shoes);
        bag = clean(bag);
    }

    private static int clean(int rgb) {
        return rgb < 0 ? ORIGIN : rgb & 0xFFFFFF;
    }

    public static PlayerLook defaults() {
        return new PlayerLook(0, ORIGIN, ORIGIN, ORIGIN, ORIGIN, ORIGIN, ORIGIN, ORIGIN);
    }

    /** Tenue aléatoire parmi les teintes proposées. */
    public static PlayerLook random(RandomGenerator rng) {
        return new PlayerLook(rng.nextInt(BODIES.length), SKIN_TONES[rng.nextInt(SKIN_TONES.length)],
                pick(rng), pick(rng), pick(rng), pick(rng), pick(rng), pick(rng));
    }

    private static int pick(RandomGenerator rng) {
        return DYES[rng.nextInt(DYES.length)];
    }

    /** Couleur RVB d'une pièce, ou {@link #ORIGIN}. */
    public int rgb(Part p) {
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

    public PlayerLook with(Part p, int rgb) {
        return switch (p) {
            case SKIN -> new PlayerLook(body, rgb, hair, hat, top, bottom, shoes, bag);
            case HAIR -> new PlayerLook(body, skin, rgb, hat, top, bottom, shoes, bag);
            case HAT -> new PlayerLook(body, skin, hair, rgb, top, bottom, shoes, bag);
            case TOP -> new PlayerLook(body, skin, hair, hat, rgb, bottom, shoes, bag);
            case BOTTOM -> new PlayerLook(body, skin, hair, hat, top, rgb, shoes, bag);
            case SHOES -> new PlayerLook(body, skin, hair, hat, top, bottom, rgb, bag);
            case BAG -> new PlayerLook(body, skin, hair, hat, top, bottom, shoes, rgb);
        };
    }

    public PlayerLook withBody(int b) {
        return new PlayerLook(b, skin, hair, hat, top, bottom, shoes, bag);
    }

    // ------------------------------------------------------------------ teintes proposées

    public static int[] presets(Part p) {
        return p == Part.SKIN ? SKIN_TONES : DYES;
    }

    public static String[] presetNames(Part p) {
        return p == Part.SKIN ? SKIN_NAMES : DYE_NAMES;
    }

    /** Index de la teinte proposée qui correspond à la couleur actuelle, ou -1 (couleur personnalisée). */
    public int presetIndex(Part p) {
        int[] presets = presets(p);
        for (int i = 0; i < presets.length; i++) if (presets[i] == rgb(p)) return i;
        return -1;
    }

    /** Nom affiché de la couleur actuelle (« Rouge », « Teint de peau #3 », « Personnalisée »). */
    public String colorName(Part p) {
        int i = presetIndex(p);
        return i >= 0 ? presetNames(p)[i] : "Personnalisée";
    }

    /** Teinte proposée suivante / précédente (une couleur personnalisée repart du début). */
    public PlayerLook cyclePreset(Part p, int delta) {
        int[] presets = presets(p);
        int i = presetIndex(p);
        int next = i < 0 ? (delta > 0 ? 0 : presets.length - 1) : Math.floorMod(i + delta, presets.length);
        return with(p, presets[next]);
    }

    // ------------------------------------------------------------------ chaîne compacte

    /** « v3.1.o.ff8800.o.o.2a9d8f.o.o » : gabarit, puis chaque pièce en hexadécimal (o = d'origine). */
    public String encode() {
        StringBuilder sb = new StringBuilder(V3).append(body);
        for (Part p : Part.values()) {
            int c = rgb(p);
            sb.append('.').append(c == ORIGIN ? "o" : String.format("%06x", c));
        }
        return sb.toString();
    }

    /**
     * Relit une apparence ; renvoie {@code null} si la chaîne est absente ou abîmée.
     * Les anciens formats (v2 : index de teintes ; v1 : avatar dessiné) sont convertis.
     */
    public static PlayerLook decode(String s) {
        if (s == null || s.isBlank()) return null;
        s = s.trim();
        try {
            if (s.startsWith(V3)) {
                String[] f = s.substring(V3.length()).split("\\.");
                if (f.length != 1 + Part.values().length) return null;
                int[] c = new int[f.length - 1];
                for (int i = 0; i < c.length; i++) c[i] = f[i + 1].equals("o") ? ORIGIN : Integer.parseInt(f[i + 1], 16);
                return new PlayerLook(Integer.parseInt(f[0]), c[0], c[1], c[2], c[3], c[4], c[5], c[6]);
            }
            if (s.startsWith(V2)) {
                String[] f = s.substring(V2.length()).split("\\.");
                if (f.length != 8) return null;
                int[] v = new int[8];
                for (int i = 0; i < 8; i++) v[i] = Integer.parseInt(f[i]);
                return new PlayerLook(v[0], at(SKIN_TONES, v[1]), at(DYES, v[2]), at(DYES, v[3]), at(DYES, v[4]),
                        at(DYES, v[5]), at(DYES, v[6]), at(DYES, v[7]));
            }
            String[] f = s.split("\\.");
            if (f.length == 9) {
                // v1 : peau, coiffure, cheveux, chapeau, couleur chapeau, haut, couleur haut, bas, chaussures
                int style = Integer.parseInt(f[1]);
                return defaults().withBody(style == 1 || style == 2 || style == 3 ? 1 : 0);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    private static int at(int[] presets, int i) {
        return presets[Math.floorMod(i, presets.length)];
    }
}
