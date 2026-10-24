package dev.orvaen.mmo.data;

import java.util.random.RandomGenerator;

/**
 * Apparence personnalisée du joueur. Uniquement des index dans des palettes fixes,
 * pour qu'elle tienne en une courte chaîne (sauvegarde, et plus tard envoi au serveur).
 */
public record PlayerLook(int skin, int hairStyle, int hairColor, int hat, int hatColor,
                         int top, int topColor, int pants, int shoes) {

    public static final int[] SKIN_COLORS = {0xF8DCC0, 0xF0C8A0, 0xD9A27A, 0xB07850, 0x7A4E34, 0x5A3624};

    public static final int[] HAIR_COLORS = {
            0x2B2B30, 0x5A3825, 0x8B5A2B, 0xC08040, 0xE8C870, 0xE8E4DC,
            0xC0392B, 0xF78FB3, 0x9B5DE5, 0x2E86DE, 0x2A9D8F, 0x27AE60
    };

    public static final int[] CLOTH_COLORS = {
            0xE76F51, 0xD62828, 0xF4A261, 0xE9C46A, 0x8AC926, 0x2A9D8F,
            0x00B4D8, 0x457B9D, 0x1D3557, 0x9B5DE5, 0xF15BB5, 0xF1FAEE,
            0x8D99AE, 0x2B2D42, 0x6D4C41
    };

    public static final String[] HAIR_STYLES = {"Courts", "Mi-longs", "Longs", "Queue", "Pics", "Rasés"};
    public static final String[] HATS = {"Casquette", "Bonnet", "Bandana", "Chapeau", "Aucun"};
    public static final String[] TOPS = {"T-shirt", "Veste", "Sweat", "Débardeur"};

    public static final int FIELDS = 9;

    public PlayerLook {
        skin = clamp(skin, SKIN_COLORS.length);
        hairStyle = clamp(hairStyle, HAIR_STYLES.length);
        hairColor = clamp(hairColor, HAIR_COLORS.length);
        hat = clamp(hat, HATS.length);
        hatColor = clamp(hatColor, CLOTH_COLORS.length);
        top = clamp(top, TOPS.length);
        topColor = clamp(topColor, CLOTH_COLORS.length);
        pants = clamp(pants, CLOTH_COLORS.length);
        shoes = clamp(shoes, CLOTH_COLORS.length);
    }

    /** Tenue proposée par défaut. */
    public static PlayerLook defaults() {
        return new PlayerLook(1, 0, 1, 0, 5, 0, 0, 8, 14);
    }

    public static PlayerLook random(RandomGenerator rng) {
        return new PlayerLook(
                rng.nextInt(SKIN_COLORS.length), rng.nextInt(HAIR_STYLES.length), rng.nextInt(HAIR_COLORS.length),
                rng.nextInt(HATS.length), rng.nextInt(CLOTH_COLORS.length), rng.nextInt(TOPS.length),
                rng.nextInt(CLOTH_COLORS.length), rng.nextInt(CLOTH_COLORS.length), rng.nextInt(CLOTH_COLORS.length));
    }

    /** Nombre de choix possibles pour le champ n° {@code field}. */
    public static int choices(int field) {
        return switch (field) {
            case 0 -> SKIN_COLORS.length;
            case 1 -> HAIR_STYLES.length;
            case 2 -> HAIR_COLORS.length;
            case 3 -> HATS.length;
            case 4, 6, 7, 8 -> CLOTH_COLORS.length;
            case 5 -> TOPS.length;
            default -> throw new IllegalArgumentException("Champ inconnu : " + field);
        };
    }

    public int get(int field) {
        return toArray()[field];
    }

    /** Copie avec le champ {@code field} décalé de {@code delta} (on boucle aux extrémités). */
    public PlayerLook cycle(int field, int delta) {
        int[] v = toArray();
        v[field] = Math.floorMod(v[field] + delta, choices(field));
        return fromArray(v);
    }

    public String encode() {
        StringBuilder sb = new StringBuilder();
        for (int v : toArray()) {
            if (!sb.isEmpty()) sb.append('.');
            sb.append(v);
        }
        return sb.toString();
    }

    /** Relit une apparence ; renvoie {@code null} si la chaîne est absente ou abîmée. */
    public static PlayerLook decode(String s) {
        if (s == null || s.isBlank()) return null;
        String[] parts = s.trim().split("\\.");
        if (parts.length != FIELDS) return null;
        int[] v = new int[FIELDS];
        try {
            for (int i = 0; i < FIELDS; i++) v[i] = Integer.parseInt(parts[i]);
        } catch (NumberFormatException e) {
            return null;
        }
        return fromArray(v);
    }

    public int skinRgb() { return SKIN_COLORS[skin]; }
    public int hairRgb() { return HAIR_COLORS[hairColor]; }
    public int hatRgb() { return CLOTH_COLORS[hatColor]; }
    public int topRgb() { return CLOTH_COLORS[topColor]; }
    public int pantsRgb() { return CLOTH_COLORS[pants]; }
    public int shoesRgb() { return CLOTH_COLORS[shoes]; }

    private int[] toArray() {
        return new int[]{skin, hairStyle, hairColor, hat, hatColor, top, topColor, pants, shoes};
    }

    private static PlayerLook fromArray(int[] v) {
        return new PlayerLook(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8]);
    }

    private static int clamp(int v, int n) {
        return Math.floorMod(v, n);
    }
}
