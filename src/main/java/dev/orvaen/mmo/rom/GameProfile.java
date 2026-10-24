package dev.orvaen.mmo.rom;

/**
 * Ce qui diffère entre les familles de jeux Gen 3 que le moteur sait lire.
 * Les valeurs viennent de la décompilation pret (pokefirered / pokeemerald)
 * et des signatures utilisées par le Universal Pokemon Randomizer.
 */
public enum GameProfile {

    FIRERED_LEAFGREEN(
            "Rouge Feu / Vert Feuille",
            640, 640, 7,      // tuiles, métatiles, palettes du tileset primaire
            4, 0x14,          // attributs de métatile : u32, à l'offset 0x14 du tileset
            true,             // le layout contient la taille de la bordure
            3, 0,             // carte de départ : Bourg Palette
            "AC470000AE470000B0470000", 0x58),

    EMERALD(
            "Émeraude",
            512, 512, 6,
            2, 0x10,          // attributs de métatile : u16, à l'offset 0x10 du tileset
            false,            // bordure fixe 2x2
            0, 9,             // carte de départ : Bourg-en-Vol
            "C078288030BC01BC00470000", 0);

    // Pointeurs stockés près de l'en-tête (identiques sur E/FR/LG, toutes langues)
    public static final int PTR_SPECIES_NAMES = 0x144;
    public static final int PTR_SPECIES_INFO = 0x1BC;
    public static final int PTR_FRONT_SPRITES = 0x128;
    public static final int PTR_SPECIES_PALETTES = 0x130;
    public static final int PTR_BACK_SPRITES = 0x12C;     // sprites de dos (combat)
    public static final int PTR_MOVE_NAMES = 0x148;       // noms des attaques (13 octets chacun)
    public static final int PTR_MOVES = 0x1CC;            // caractéristiques des attaques (12 octets chacune)
    public static final int PTR_BALL_SPRITES = 0x1D0;     // images des Poké Balls
    public static final int PTR_BALL_PALETTES = 0x1D4;

    // Signatures de code précédant les pointeurs vers les tables de cartes et de rencontres
    public static final String SIG_MAP_GROUPS = "80180068890B091808687047";
    public static final String SIG_WILD_ENCOUNTERS = "0348048009E00000FFFF0000";
    public static final int SIG_POINTER_DISTANCE = 12;

    public static final int SPECIES_COUNT = 412;          // 0 = vide, 1..411
    public static final int SPECIES_NAME_LENGTH = 11;
    public static final int SPECIES_INFO_SIZE = 28;
    public static final int PALS_TOTAL = 13;

    private final String name;
    public final int tilesInPrimary;
    public final int metatilesInPrimary;
    public final int palsInPrimary;
    public final int metatileAttrSize;
    public final int metatileAttrOffset;
    public final boolean layoutHasBorderSize;
    public final int startGroup;
    public final int startNum;
    public final String mapLabelsSignature;
    public final int mapLabelsStart;

    GameProfile(String name, int tilesInPrimary, int metatilesInPrimary, int palsInPrimary,
                int metatileAttrSize, int metatileAttrOffset, boolean layoutHasBorderSize, int startGroup, int startNum,
                String mapLabelsSignature, int mapLabelsStart) {
        this.name = name;
        this.tilesInPrimary = tilesInPrimary;
        this.metatilesInPrimary = metatilesInPrimary;
        this.palsInPrimary = palsInPrimary;
        this.metatileAttrSize = metatileAttrSize;
        this.metatileAttrOffset = metatileAttrOffset;
        this.layoutHasBorderSize = layoutHasBorderSize;
        this.startGroup = startGroup;
        this.startNum = startNum;
        this.mapLabelsSignature = mapLabelsSignature;
        this.mapLabelsStart = mapLabelsStart;
    }

    public static GameProfile detect(String gameCode) {
        if (gameCode == null || gameCode.length() != 4) return null;
        String base = gameCode.substring(0, 3);
        return switch (base) {
            case "BPR", "BPG" -> FIRERED_LEAFGREEN;
            case "BPE" -> EMERALD;
            default -> null;
        };
    }

    public String displayName(String gameCode) {
        String game = switch (gameCode.substring(0, 3)) {
            case "BPR" -> "Rouge Feu";
            case "BPG" -> "Vert Feuille";
            case "BPE" -> "Émeraude";
            default -> name;
        };
        String lang = switch (gameCode.charAt(3)) {
            case 'E' -> "anglais";
            case 'F' -> "français";
            case 'D' -> "allemand";
            case 'S' -> "espagnol";
            case 'I' -> "italien";
            case 'J' -> "japonais";
            default -> "langue " + gameCode.charAt(3);
        };
        return game + " (" + lang + ")";
    }

    /** Comportement du métatile (herbe haute, eau…). */
    public int behavior(long attributes) {
        return this == FIRERED_LEAFGREEN ? (int) (attributes & 0x1FF) : (int) (attributes & 0xFF);
    }

    /** Type de calque : 0 = normal (2e couche au-dessus du joueur), 1 = couvert, 2 = séparé. */
    public int layerType(long attributes) {
        return this == FIRERED_LEAFGREEN ? (int) ((attributes >> 29) & 0x3) : (int) ((attributes >> 12) & 0xF);
    }

    /** Vrai si le métatile déclenche des rencontres terrestres (herbe, grotte). */
    public boolean isLandEncounter(long attributes) {
        int b = behavior(attributes);
        if (b == 0x02 || b == 0x03) return true; // herbe haute / longue herbe
        return this == FIRERED_LEAFGREEN && ((attributes >> 24) & 0x7) == 1;
    }

    public boolean isWater(long attributes) {
        int b = behavior(attributes);
        return b >= 0x10 && b <= 0x17;
    }
}
