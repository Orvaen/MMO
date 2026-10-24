package dev.orvaen.mmo;

import dev.orvaen.mmo.rom.GameProfile;
import dev.orvaen.mmo.rom.TextCodec;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Fabrique une petite ROM factice au format Rouge Feu (BPRE) pour tester le moteur
 * sans jamais utiliser de données Nintendo. Tout le contenu est inventé.
 */
public final class SyntheticRom {

    public static final int SIZE = 0x80000;

    // Couleurs (BGR555) choisies pour être faciles à reconnaître au rendu
    public static final int GREEN = 0x03E0;   // tuile 1 · palette 0 · couleur 1
    public static final int BLUE = 0x7C00;    // tuile 2 · palette 0 · couleur 2
    public static final int RED = 0x001F;     // sprite · couleur 1
    public static final int YELLOW = 0x03FF;  // tileset secondaire · palette 7 · couleur 3

    private final byte[] rom = new byte[SIZE];
    private int cursor = 0x400;

    public static byte[] build() {
        return new SyntheticRom().make(true);
    }

    /** Variante sans écran titre (pour essayer de vraies images à la place). */
    public static byte[] buildWithoutTitleScreen() {
        return new SyntheticRom().make(false);
    }

    /** Variante sans aucune image d'interface (écran titre, combat), pour essayer les vraies à la place. */
    public static byte[] buildWithoutInterfaceGraphics() {
        SyntheticRom r = new SyntheticRom();
        r.battleGraphics = false;
        return r.make(false);
    }

    private boolean battleGraphics = true;

    private byte[] make(boolean titleScreen) {
        ascii(0xA0, "POKEMON FIRE");
        ascii(0xAC, "BPRE");
        rom[0xBC] = 0;
        fixHeader(rom);

        buildSpecies();
        buildPokemonTexts();
        int primary = buildPrimaryTileset();
        int secondary = buildSecondaryTileset();
        buildMapsAndWild(primary, secondary);
        buildPlayerGraphics();
        buildDoorAnims();
        buildTileAnims();
        buildResetScript();
        buildSongs();
        if (titleScreen) buildTitleScreen();
        buildBattle();
        return rom;
    }

    /** Octet fixe 0xB2 et somme de contrôle 0xBD : un en-tête GBA valide, comme sur une vraie cartouche. */
    public static void fixHeader(byte[] rom) {
        rom[0xB2] = (byte) 0x96;
        int sum = 0;
        for (int i = 0xA0; i <= 0xBC; i++) sum += rom[i] & 0xFF;
        rom[0xBD] = (byte) (-(sum + 0x19));
    }

    /** Écrit la ROM dans un fichier de 1 Mo (taille minimale d'une vraie ROM GBA). */
    public static java.nio.file.Path writeFile(java.nio.file.Path file, byte[] rom) throws java.io.IOException {
        byte[] padded = java.util.Arrays.copyOf(rom, Math.max(rom.length, 0x10_0000));
        return java.nio.file.Files.write(file, padded);
    }

    // ------------------------------------------------------------------ sprites du joueur (inventés)

    /** Couleurs de la palette de test du joueur (BGR555). */
    public static final int PLAYER_RED = 0x001F, PLAYER_RED_DARK = 0x0010, PLAYER_SKIN = 0x4E7F, PLAYER_BLUE = 0x7C00;

    /**
     * Pixels repères, en coordonnées de sprite :
     * marche (16x32) : (5,14) idx c « couvre-chef », (5,24) idx c « haut », (5,15) idx 8 ombre de couvre-chef,
     *                  (5,18) idx 2 « peau », (5,28) idx 6 « bas » ; (12,14) idx c uniquement en frame 0 (asymétrie)
     * portrait (64x64) : (30,12) idx c couvre-chef, (30,30) idx c haut, (30,45) idx 5 bas, (46,31) idx c balle tenue
     */
    private void buildPlayerGraphics() {
        int[] pal = new int[16];
        pal[2] = PLAYER_SKIN;
        pal[5] = PLAYER_BLUE;
        pal[6] = PLAYER_BLUE;
        pal[8] = PLAYER_RED_DARK;
        pal[0xc] = PLAYER_RED;
        pal[0xf] = 0;
        int owPal = alloc(32);
        for (int i = 0; i < 16; i++) w16(owPal + i * 2, pal[i]);
        int palTable = alloc(32);
        ptr(palTable, owPal);
        w16(palTable + 4, 0x1100);
        ptr(palTable + 8, owPal);
        w16(palTable + 12, 0x1101);
        int npcPal = alloc(32);
        w16(npcPal + 2, NPC_COLOR);
        ptr(palTable + 16, npcPal);
        w16(palTable + 20, 0x1103);

        byte[] frame0 = new byte[256], frame = new byte[256];
        for (byte[] f : new byte[][]{frame0, frame}) {
            setPixel(f, 2, 5, 14, 0xc);
            setPixel(f, 2, 5, 15, 0x8);
            setPixel(f, 2, 5, 18, 0x2);
            setPixel(f, 2, 5, 24, 0xc);
            setPixel(f, 2, 5, 28, 0x6);
        }
        setPixel(frame0, 2, 12, 14, 0xc);
        int f0 = alloc(256);
        System.arraycopy(frame0, 0, rom, f0, 256);
        int f1 = alloc(256);
        System.arraycopy(frame, 0, rom, f1, 256);
        byte[] runFrame = frame.clone();
        setPixel(runFrame, 2, 1, 30, 0x5); // marque propre aux frames de course
        int f2 = alloc(256);
        System.arraycopy(runFrame, 0, rom, f2, 256);

        int boyInfo = -1;
        for (int tag : new int[]{0x1100, 0x1110}) {
            int images = alloc(18 * 8);
            for (int i = 0; i < 18; i++) {
                ptr(images + i * 8, i == 0 ? f0 : i < 9 ? f1 : f2);
                w16(images + i * 8 + 4, 0x100);
            }
            int info = alloc(0x24);
            w16(info, 0xFFFF);
            w16(info + 2, tag);
            w16(info + 4, 0x1102);
            w16(info + 6, 0x200);
            w16(info + 8, 16);
            w16(info + 10, 32);
            ptr(info + 0x1C, images);
            if (boyInfo < 0) boyInfo = info;
        }

        // PNJ : 9 frames 16x32 de couleur 1 (NPC_COLOR), et la table des graphismes d'objets
        byte[] npcFrame = new byte[256];
        for (int y = 8; y < 32; y++) for (int x = 4; x < 12; x++) setPixel(npcFrame, 2, x, y, 1);
        int npcData = alloc(256);
        System.arraycopy(npcFrame, 0, rom, npcData, 256);
        int npcImages = alloc(9 * 8);
        for (int i = 0; i < 9; i++) {
            ptr(npcImages + i * 8, npcData);
            w16(npcImages + i * 8 + 4, 0x100);
        }
        int npcInfo = alloc(0x24);
        w16(npcInfo, 0xFFFF);
        w16(npcInfo + 2, 0x1103);
        w16(npcInfo + 8, 16);
        w16(npcInfo + 10, 32);
        ptr(npcInfo + 0x1C, npcImages);
        int gfxTable = alloc(41 * 4);
        ptr(gfxTable, boyInfo);
        for (int i = 1; i < 41; i++) ptr(gfxTable + i * 4, npcInfo);

        byte[] pic = new byte[0x800];
        setPixel(pic, 8, 30, 12, 0xc);
        setPixel(pic, 8, 30, 30, 0xc);
        setPixel(pic, 8, 30, 45, 0x5);
        setPixel(pic, 8, 46, 31, 0xc);
        int picData = alloc(0x800 + 0x100 + 16);
        writeLz(picData, pic);
        byte[] picPal = new byte[32];
        for (int i = 0; i < 16; i++) put16(picPal, i * 2, pal[i]);
        int picPalData = alloc(64);
        writeLz(picPalData, picPal);

        int ids = 137;
        int sheets = alloc(ids * 8 + 8);
        int pals = alloc(ids * 8 + 8);
        for (int k = 0; k < ids; k++) {
            ptr(sheets + k * 8, picData);
            w16(sheets + k * 8 + 4, 0x800);
            w16(sheets + k * 8 + 6, k);
            ptr(pals + k * 8, picPalData);
            w16(pals + k * 8 + 4, k);
        }

        int backData = alloc(0x800);
        System.arraycopy(pic, 0, rom, backData, 0x800); // vue de dos stockée non compressée
        int backSheets = alloc(3 * 8 + 8);
        for (int k = 0; k < 3; k++) {
            ptr(backSheets + k * 8, backData);
            w16(backSheets + k * 8 + 4, k < 2 ? 0x2800 : 0x2000);
            w16(backSheets + k * 8 + 6, k);
        }
        int backPals = alloc(6 * 8 + 8);
        for (int k = 0; k < 6; k++) {
            ptr(backPals + k * 8, picPalData);
            w16(backPals + k * 8 + 4, k);
        }
    }

    /** Écrit l'index de couleur d'un pixel dans des tuiles 4bpp rangées ligne par ligne. */
    private static void setPixel(byte[] tiles, int widthTiles, int x, int y, int index) {
        int o = ((y >> 3) * widthTiles + (x >> 3)) * 32 + (y & 7) * 4 + ((x & 7) >> 1);
        tiles[o] = (byte) ((x & 1) == 0 ? (tiles[o] & 0xF0) | index : (tiles[o] & 0x0F) | index << 4);
    }

    // ------------------------------------------------------------------ espèces

    /** Talents de TESTMON et EV qu'il donne (Attaque +2, Vitesse +1). */
    public static final int ABILITY_1 = 1, ABILITY_2 = 2, EV_YIELD = 2 << 2 | 1 << 6;
    public static final String[] NATURES = {"HARDI", "SOLO", "BRAVE", "RIGIDE", "MAUVAIS", "ASSURÉ", "DOCILE",
            "RELAX", "MALIN", "LACHE", "TIMIDE", "PRESSÉ", "SÉRIEUX", "JOVIAL", "NAIF", "MODESTE", "DOUX",
            "DISCRET", "PUDIQUE", "FOUFOU", "CALME", "GENTIL", "MALPOLI", "PRUDENT", "BIZARRE"};

    /**
     * Comme dans le jeu : descriptions des talents, table de 78 pointeurs, puis les 78 noms de 13 octets ;
     * et les 25 noms de natures à la suite, suivis de leur table de pointeurs.
     */
    private void buildPokemonTexts() {
        int descs = alloc(78 * 16);
        int table = alloc(78 * 4 + 78 * 13 + 32);
        int names = table + 78 * 4;
        for (int i = 0; i < 78; i++) {
            text(descs + i * 16, i == 0 ? "AUCUN" : "EFFET " + (char) ('A' + i % 26));
            ptr(table + i * 4, descs + i * 16);
            text(names + i * 13, switch (i) {
                case 0 -> "-------";
                case ABILITY_1 -> "ENGRAIS";
                case ABILITY_2 -> "CHLOROPHYLE";
                default -> "TALENT " + (char) ('A' + i % 26);
            });
        }
        int len = 0;
        for (String n : NATURES) len += n.length() + 1;
        int strings = alloc(len + 4 + 25 * 4);
        int at = strings;
        int[] ptrs = new int[25];
        for (int i = 0; i < 25; i++) {
            ptrs[i] = at;
            text(at, NATURES[i]);
            at += NATURES[i].length() + 1;
        }
        int natureTable = (at + 3) & ~3;
        for (int i = 0; i < 25; i++) ptr(natureTable + i * 4, ptrs[i]);
    }

    private void buildSpecies() {
        int n = GameProfile.SPECIES_COUNT;
        int names = alloc(n * GameProfile.SPECIES_NAME_LENGTH);
        for (int i = 0; i < n * GameProfile.SPECIES_NAME_LENGTH; i++) rom[names + i] = (byte) 0xFF;
        text(names + GameProfile.SPECIES_NAME_LENGTH, "TESTMON");
        text(names + 2 * GameProfile.SPECIES_NAME_LENGTH, "Bébé-Éclair");

        int stats = alloc(n * GameProfile.SPECIES_INFO_SIZE);
        int s1 = stats + GameProfile.SPECIES_INFO_SIZE;
        byte[] base = {45, 49, 50, 51, 52, 53, 10, 12, (byte) 190, 64};
        System.arraycopy(base, 0, rom, s1, base.length);
        w16(s1 + 10, EV_YIELD);                      // EV donnés : 2 en Attaque, 1 en Vitesse
        rom[s1 + 22] = (byte) ABILITY_1;
        rom[s1 + 23] = (byte) ABILITY_2;
        int s2 = stats + 2 * GameProfile.SPECIES_INFO_SIZE;
        rom[s2] = 100;
        rom[s2 + 6] = 13;
        rom[s2 + 7] = 13;

        // Sprite 64x64 : pixel (0,0) couleur 1, pixel (63,63) couleur 2, reste transparent
        byte[] sprite = new byte[2048];
        sprite[0] = 0x01;
        sprite[63 * 32 + 31] = 0x20; // dernière tuile, dernière ligne, pixel de droite
        int spriteData = alloc(sprite.length + 400);
        writeLz(spriteData, sprite);

        byte[] pal = new byte[32];
        put16(pal, 2, RED);
        put16(pal, 4, BLUE);
        int palData = alloc(64);
        writeLz(palData, pal);

        int frontTable = alloc(n * 8);
        int palTable = alloc(n * 8);
        // Même forme que dans les vraies ROM : {image, 0x800, n° d'espèce} et {palette, n°}.
        // Elle ressemble à s'y méprendre à la table des portraits de dresseurs (piège voulu).
        for (int i = 0; i < n; i++) {
            ptr(frontTable + i * 8, spriteData);
            w16(frontTable + i * 8 + 4, 0x800);
            w16(frontTable + i * 8 + 6, i);
            ptr(palTable + i * 8, palData);
            w16(palTable + i * 8 + 4, i);
        }
        ptr(GameProfile.PTR_SPECIES_NAMES, names);
        ptr(GameProfile.PTR_SPECIES_INFO, stats);
        ptr(GameProfile.PTR_FRONT_SPRITES, frontTable);
        ptr(GameProfile.PTR_SPECIES_PALETTES, palTable);
    }

    // ------------------------------------------------------------------ tilesets

    private int buildPrimaryTileset() {
        int tiles = alloc(640 * 32);
        fillTile(tiles, 1, 1);
        fillTile(tiles, 2, 2);

        int pals = alloc(16 * 32);
        w16(pals + 2, GREEN);
        w16(pals + 4, BLUE);

        int metatiles = alloc(640 * 16);
        metatile(metatiles, 0, 1, 0, 0, 0);       // sol vert
        metatile(metatiles, 1, 2, 0, 0, 0);       // herbe haute (bleue)
        metatile(metatiles, 2, 1, 0, 2, 0);       // arbre : sol vert, cime bleue au-dessus du joueur
        metatile(metatiles, FLOWER_METATILE, 508, 0, 0, 0); // fleurs animées (tuile 508)
        for (int t = 508; t < 512; t++) fillTile(tiles, t, 1); // en VRAM : 1re frame des fleurs

        int attrs = alloc(640 * 4);
        w32(attrs + 4, 0x02 | (1 << 24));         // herbe haute + rencontres terrestres
        w32(attrs + 8, 0);                        // calque normal

        int ts = alloc(0x18);
        rom[ts] = 0;      // non compressé
        rom[ts + 1] = 0;  // primaire
        ptr(ts + 4, tiles);
        ptr(ts + 8, pals);
        ptr(ts + 0x0C, metatiles);
        ptr(ts + 0x14, attrs);
        return ts;
    }

    public static final int SONG_COUNT = 120;

    /**
     * Table de 120 morceaux identiques : une piste, un instrument échantillonné (onde carrée en boucle),
     * tempo 120, un Do de 24 ticks (une noire), une attente, puis fin.
     */
    private void buildSongs() {
        int sample = alloc(16 + 64);
        w16(sample, 0);
        w16(sample + 2, 0x4000);                          // en boucle
        w32(sample + 4, 13379 * 1024);
        w32(sample + 8, 0);
        w32(sample + 12, 64);
        for (int i = 0; i < 64; i++) rom[sample + 16 + i] = (byte) (i < 32 ? 100 : -100);
        int voices = alloc(12 * 2);
        rom[voices] = 0;                                  // DirectSound
        rom[voices + 1] = 60;
        ptr(voices + 4, sample);
        rom[voices + 8] = (byte) 255;                     // attaque immédiate
        rom[voices + 9] = 0;
        rom[voices + 10] = (byte) 255;                    // maintien plein
        rom[voices + 11] = (byte) 200;                    // relâche
        int track = alloc(16);
        int t = track;
        rom[t++] = (byte) 0xBD; rom[t++] = 0;             // VOICE 0
        rom[t++] = (byte) 0xBE; rom[t++] = 100;           // VOL
        rom[t++] = (byte) 0xBB; rom[t++] = 60;            // TEMPO 120
        rom[t++] = (byte) 0xE7; rom[t++] = 60; rom[t++] = 127; // N24 Do4 v127
        rom[t++] = (byte) 0xB0;                           // W96
        rom[t] = (byte) 0xB1;                             // FINE
        int header = alloc(12);
        rom[header] = 1;
        ptr(header + 4, voices);
        ptr(header + 8, track);
        int table = alloc(SONG_COUNT * 8);
        for (int i = 0; i < SONG_COUNT; i++) ptr(table + i * 8, header);
    }

    // ------------------------------------------------------------------ combat

    /** Attaques inventées : 1 CHARGE, 2 GRIFFE, 3 RUGISSEMENT (Attaque −1), 4 FLAMMECHE (Feu), 5 VIVE-ATTAQUE (priorité). */
    public static final int MOVE_TACKLE = 1, MOVE_SCRATCH = 2, MOVE_GROWL = 3, MOVE_EMBER = 4, MOVE_QUICK = 5;
    /** Couleurs repères du combat (BGR555). */
    public static final int TERRAIN_COLOR = 0x0360, TEXTBOX_COLOR = 0x5000, MENU_COLOR = 0x7FFF,
            GRASS_COLOR = 0x0280;

    private void buildBattle() {
        // Attaques (12 octets) et noms (13 octets), pointés par l'en-tête de la ROM
        int count = 355;
        int moves = alloc(count * 12), names = alloc(count * 13);
        for (int i = 0; i < count * 13; i++) rom[names + i] = (byte) 0xFF;
        Object[][] list = {
                {MOVE_TACKLE, "CHARGE", 0, 35, 0, 100, 35, 0, 0},
                {MOVE_SCRATCH, "GRIFFE", 0, 40, 0, 100, 35, 0, 0},
                {MOVE_GROWL, "RUGISSEMENT", 18, 0, 0, 100, 40, 0, 0},
                {MOVE_EMBER, "FLAMMECHE", 4, 40, 10, 100, 25, 10, 0},
                {MOVE_QUICK, "VIVE-ATTAQUE", 103, 40, 0, 100, 30, 0, 1},
        };
        for (Object[] m : list) {
            int id = (int) m[0], o = moves + id * 12;
            text(names + id * 13, (String) m[1]);
            for (int k = 0; k < 6; k++) rom[o + k] = (byte) (int) (Integer) m[2 + k];
            rom[o + 7] = (byte) (int) (Integer) m[8];
        }
        ptr(GameProfile.PTR_MOVES, moves);
        ptr(GameProfile.PTR_MOVE_NAMES, names);

        // Apprentissages : TESTMON (1) apprend CHARGE et RUGISSEMENT au N.1, FLAMMECHE au N.5,
        // VIVE-ATTAQUE au N.9 ; les autres espèces : GRIFFE au N.1
        int ls1 = alloc(10), lsOther = alloc(4);
        w16(ls1, 1 << 9 | MOVE_TACKLE);
        w16(ls1 + 2, 1 << 9 | MOVE_GROWL);
        w16(ls1 + 4, 5 << 9 | MOVE_EMBER);
        w16(ls1 + 6, 9 << 9 | MOVE_QUICK);
        w16(ls1 + 8, 0xFFFF);
        w16(lsOther, 1 << 9 | MOVE_SCRATCH);
        w16(lsOther + 2, 0xFFFF);
        int n = GameProfile.SPECIES_COUNT, learn = alloc(n * 4);
        for (int i = 0; i < n; i++) ptr(learn + i * 4, i == 1 ? ls1 : lsOther);

        // Table des types : commence par Normal → Roche ×0,5 ; Feu → Plante ×2 ; Eau → Feu ×2
        byte[] types = {0, 5, 5, 0, 8, 5, 10, 12, 20, 11, 10, 20, (byte) 0xFE, (byte) 0xFE, 0, 0, 7, 0, (byte) 0xFF};
        int t = alloc(types.length);
        System.arraycopy(types, 0, rom, t, types.length);

        // Courbes d'expérience (6 x 101 niveaux)
        int exp = alloc(6 * 101 * 4);
        for (int g = 0; g < 6; g++) {
            for (int l = 0; l <= 100; l++) w32(exp + (g * 101 + l) * 4, (int) expFormula(g, l));
        }

        // Sprites de dos : pixel (0,0) couleur 2 (BLUE dans la palette de l'espèce)
        byte[] back = new byte[2048];
        back[0] = 0x02;
        int backData = alloc(back.length + 400);
        writeLz(backData, back);
        int backTable = alloc(n * 8);
        for (int i = 0; i < n; i++) {
            ptr(backTable + i * 8, backData);
            w16(backTable + i * 8 + 4, 0x800);
            w16(backTable + i * 8 + 6, i);
        }
        ptr(GameProfile.PTR_BACK_SPRITES, backTable);

        buildIcons();
        if (!battleGraphics) return;
        buildMoveAnimations();
        // Fonds de combat : 10 terrains identiques, lignes 0 à 13 = tuile 1 de couleur 1 (palette 2)
        java.io.ByteArrayOutputStream tb = new java.io.ByteArrayOutputStream();
        lzBlock(tb, solidTiles(2, 1));
        int tilesAt = 0;
        int mapAt = tb.size();
        byte[] tmap = new byte[0x1000];
        for (int y = 0; y < 14; y++) for (int x = 0; x < 32; x++) put16(tmap, (y * 32 + x) * 2, 1 | 2 << 12);
        lzBlock(tb, tmap);
        int palAt = tb.size();
        byte[] tpal = new byte[0x60];
        put16(tpal, 2, TERRAIN_COLOR);
        lzBlock(tb, tpal);
        byte[] terrainData = tb.toByteArray();
        int terrains = alloc(terrainData.length);
        System.arraycopy(terrainData, 0, rom, terrains, terrainData.length);
        int table = alloc(10 * 20);
        for (int i = 0; i < 10; i++) {
            ptr(table + i * 20, terrains + tilesAt);
            ptr(table + i * 20 + 4, terrains + mapAt);
            ptr(table + i * 20 + 8, terrains + tilesAt);
            ptr(table + i * 20 + 12, terrains + mapAt);
            ptr(table + i * 20 + 16, terrains + palAt);
        }

        // Boîte de texte : tuiles (0x2000), palettes (0x40), plan (0x1000), à la suite
        java.io.ByteArrayOutputStream box = new java.io.ByteArrayOutputStream();
        lzBlock(box, solidTiles(256, 1));
        byte[] bpal = new byte[0x40];
        put16(bpal, 2, TEXTBOX_COLOR);
        put16(bpal, 32 + 2, MENU_COLOR);
        lzBlock(box, bpal);
        byte[] bmap = new byte[0x1000];
        for (int row : new int[]{14, 15, 16, 17, 18, 19, 34, 35, 36, 37, 38, 39}) {
            for (int x = 0; x < 32; x++) put16(bmap, (row * 32 + x) * 2, 1 | (row >= 34 && x >= 15 ? 1 << 12 : 0));
        }
        for (int row = 54; row < 60; row++) for (int x = 0; x < 32; x++) put16(bmap, (row * 32 + x) * 2, 1 | 1 << 12);
        lzBlock(box, bmap);
        put(box.toByteArray());

        // Poké Ball : 3 images 16x16 (couleur 1), et sa palette
        int ballData = alloc(600);
        writeLz(ballData, solidTiles(12, 1));
        byte[] ballPal = new byte[32];
        put16(ballPal, 2, RED);
        int ballPalData = alloc(64);
        writeLz(ballPalData, ballPal);
        int ballSheets = alloc(8), ballPals = alloc(8);
        ptr(ballSheets, ballData);
        w16(ballSheets + 4, 384);
        w16(ballSheets + 6, 55000);
        ptr(ballPals, ballPalData);
        w16(ballPals + 4, 55000);
        ptr(GameProfile.PTR_BALL_SPRITES, ballSheets);
        ptr(GameProfile.PTR_BALL_PALETTES, ballPals);

        // Herbe haute : 5 images 16x16 (image n remplie de la couleur n + 1), animation, modèle, palette 0x1005
        int grassPics = alloc(5 * 128);
        for (int f = 0; f < 5; f++) for (int i = 0; i < 128; i++) rom[grassPics + f * 128 + i] = (byte) ((f + 1) | (f + 1) << 4);
        int images = alloc(5 * 8);
        for (int f = 0; f < 5; f++) {
            ptr(images + f * 8, grassPics + f * 128);
            w16(images + f * 8 + 4, 0x80);
        }
        int anim = alloc(6 * 4);
        int[] frames = {1, 2, 3, 4, 0};
        for (int i = 0; i < 5; i++) w32(anim + i * 4, frames[i] | 10 << 16);
        w32(anim + 20, 0xFFFFFFFD);
        int animTable = alloc(4);
        ptr(animTable, anim);
        int template = alloc(24);
        w16(template, 0xFFFF);
        w16(template + 2, 0x1005);
        ptr(template + 4, anim);
        ptr(template + 8, animTable);
        ptr(template + 12, images);
        ptr(template + 16, anim);
        ptr(template + 20, anim);
        int grassPal = alloc(32);
        w16(grassPal + 2, GRASS_COLOR);
        int palStruct = alloc(8);
        ptr(palStruct, grassPal);
        w16(palStruct + 4, 0x1005);
    }

    public static final int ANIM_COLOR = 0x001F, ANIM_SE = 13;

    /**
     * Animations d'attaque, au format du jeu : 70 planches d'images (n° 10000…) et leurs palettes,
     * deux modèles de sprite (un impact 32x32 animé, un sprite invisible qui fait s'élancer l'attaquant),
     * et les scripts de CHARGE (élan, impact, tremblement, son) et FLAMMECHE (projectile).
     */
    private void buildMoveAnimations() {
        int pics = 70;
        int sheet = alloc(600);
        writeLz(sheet, solidTiles(48, 1));                         // 3 images 32x32
        byte[] pal = new byte[32];
        put16(pal, 2, ANIM_COLOR);
        int palData = alloc(64);
        writeLz(palData, pal);
        int picTable = alloc(pics * 8), palTable = alloc(pics * 8);
        for (int i = 0; i < pics; i++) {
            ptr(picTable + i * 8, sheet);
            w16(picTable + i * 8 + 4, 0x600);
            w16(picTable + i * 8 + 6, 10000 + i);
            ptr(palTable + i * 8, palData);
            w16(palTable + i * 8 + 4, 10000 + i);
        }
        int oam = alloc(8);
        w32(oam, 0x80000000);                                      // carré, taille 2 : 32x32
        int anim = alloc(16);
        w32(anim, 0 | 4 << 16);                                    // image 0 (tuile 0), 4 frames
        w32(anim + 4, 16 | 4 << 16);                               // image 1 (tuile 16), 4 frames
        w32(anim + 8, 0xFFFF);                                     // fin
        int animTable = alloc(4);
        ptr(animTable, anim);
        int impact = alloc(24), lunge = alloc(24);
        w16(impact, 10005);
        w16(impact + 2, 10005);
        ptr(impact + 4, oam);
        ptr(impact + 8, animTable);
        ptr(impact + 16, anim);
        ptr(impact + 20, anim);
        ptr(lunge + 4, oam);
        ptr(lunge + 8, animTable);
        ptr(lunge + 16, anim);
        ptr(lunge + 20, anim);
        int task = alloc(4);                                       // « fonction » d'une tâche (adresse impaire)

        java.io.ByteArrayOutputStream tackle = new java.io.ByteArrayOutputStream();
        tackle.write(0x00); le16(tackle, 10005);                   // loadspritegfx
        sprite(tackle, lunge, false, 4, 4);                        // élan de l'attaquant
        tackle.write(0x04); tackle.write(6);                       // delay 6
        sprite(tackle, impact, false, 0, 0, 1, 2);                 // impact sur la cible
        tackle.write(0x03); le32(tackle, 0x0800_0000 + task + 1); tackle.write(2); tackle.write(5);
        le16(tackle, 1); le16(tackle, 3); le16(tackle, 0); le16(tackle, 6); le16(tackle, 1);   // secouer la cible
        tackle.write(0x19); le16(tackle, ANIM_SE); tackle.write(0x3F);                        // playsewithpan
        tackle.write(0x05);                                        // waitforvisualfinish
        tackle.write(0x08);                                        // end
        java.io.ByteArrayOutputStream ember = new java.io.ByteArrayOutputStream();
        ember.write(0x00); le16(ember, 10005);
        sprite(ember, impact, true, 20, 0, -16, 24, 20, 1);         // projectile vers la cible
        ember.write(0x05);
        ember.write(0x08);
        int tackleAt = alloc(tackle.size()), emberAt = alloc(ember.size()), none = alloc(1);
        System.arraycopy(tackle.toByteArray(), 0, rom, tackleAt, tackle.size());
        System.arraycopy(ember.toByteArray(), 0, rom, emberAt, ember.size());
        rom[none] = 0x08;
        int scripts = alloc(355 * 4);
        for (int i = 0; i < 355; i++) ptr(scripts + i * 4, i == MOVE_TACKLE ? tackleAt : i == MOVE_EMBER ? emberAt : none);
    }

    public static final int ICON_COLOR = 0x7C1F;

    /** Icônes des Pokémon (2 images 32x32 pleines de la couleur 1), n° de palette, et les 3 palettes. */
    private void buildIcons() {
        int n = GameProfile.SPECIES_COUNT;
        int icon = alloc(1024);
        for (int i = 0; i < 1024; i++) rom[icon + i] = 0x11;
        int table = alloc(n * 4), ids = alloc(n);
        for (int i = 0; i < n; i++) ptr(table + i * 4, icon);
        int pal = alloc(32);
        w16(pal + 2, ICON_COLOR);
        int pals = alloc(3 * 8);
        for (int i = 0; i < 3; i++) {
            ptr(pals + i * 8, pal);
            w16(pals + i * 8 + 4, 56000 + i);
        }
        ptr(GameProfile.PTR_ICONS, table);
        ptr(GameProfile.PTR_ICON_PALETTE_IDS, ids);
        ptr(GameProfile.PTR_ICON_PALETTES, pals);
    }

    private void sprite(java.io.ByteArrayOutputStream out, int template, boolean onTarget, int... args) {
        out.write(0x02);
        le32(out, 0x0800_0000 + template);
        out.write((onTarget ? 0x80 : 0) | 2);
        out.write(args.length);
        for (int a : args) le16(out, a);
    }

    private static void le16(java.io.ByteArrayOutputStream out, int v) {
        out.write(v);
        out.write(v >> 8);
    }

    private static void le32(java.io.ByteArrayOutputStream out, int v) {
        le16(out, v);
        le16(out, v >> 16);
    }

    static long expFormula(int g, int n) {
        long n3 = (long) n * n * n;
        return switch (g) {
            case 3 -> n <= 1 ? 0 : 6 * n3 / 5 - 15L * n * n + 100L * n - 140;
            case 4 -> 4 * n3 / 5;
            case 5 -> 5 * n3 / 4;
            default -> n3;
        };
    }

    /** Couleurs repères de l'écran titre (BGR555). */
    public static final int TITLE_LOGO = 0x7FE0, TITLE_MON = 0x001F, TITLE_TEXT = 0x7FFF, TITLE_BORDER = 0x2108,
            TITLE_FLAME = 0x01DF;

    /**
     * Écran titre inventé, rangé comme dans Rouge Feu :
     * logo 8 bits (case (5..24, 2) couleur 5), Pokémon (cases (10..19, 8..14)), texte (ligne 17),
     * puis à part : cadre (partout), reflet, flammes (10 images 16x16).
     */
    private void buildTitleScreen() {
        java.io.ByteArrayOutputStream main = new java.io.ByteArrayOutputStream();
        int[] logoPal = new int[256];
        logoPal[5] = TITLE_LOGO;
        palette(main, logoPal);
        byte[] logoTiles = new byte[128];
        java.util.Arrays.fill(logoTiles, 64, 128, (byte) 5);
        lzBlock(main, logoTiles);
        lzBlock(main, titleMap(5, 24, 2, 2, 0));
        int[] monPal = new int[16];
        monPal[3] = TITLE_MON;
        palette(main, monPal);
        lzBlock(main, solidTiles(2, 3));
        lzBlock(main, titleMap(10, 19, 8, 14, 13));
        int[] bgPal = new int[16];
        bgPal[1] = TITLE_TEXT;
        bgPal[2] = TITLE_BORDER;
        bgPal[6] = TITLE_BORDER;                          // « PRESS START » éteint : couleur du fond
        palette(main, bgPal);
        lzBlock(main, solidTiles(2, 1));
        lzBlock(main, titleMap(8, 21, 17, 17, 15));
        put(main.toByteArray());

        java.io.ByteArrayOutputStream sprites = new java.io.ByteArrayOutputStream();
        lzBlock(sprites, solidTiles(4, 2));
        lzBlock(sprites, titleMap(0, 29, 0, 19, 14));
        lzBlock(sprites, solidTiles(64, 1));
        int[] flamePal = new int[16];
        flamePal[1] = TITLE_FLAME;
        palette(sprites, flamePal);
        lzBlock(sprites, solidTiles(40, 1));
        put(sprites.toByteArray());
    }

    /** Tuiles 4 bits : la tuile 0 est vide, les suivantes sont pleines de la couleur donnée. */
    private static byte[] solidTiles(int count, int color) {
        byte[] t = new byte[count * 32];
        java.util.Arrays.fill(t, count > 2 ? 0 : 32, t.length, (byte) (color | color << 4));
        return t;
    }

    /** Plan 32x20 : la tuile 1 (palette donnée) dans le rectangle, rien ailleurs. */
    private static byte[] titleMap(int x0, int x1, int y0, int y1, int palette) {
        byte[] map = new byte[0x500];
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int e = 1 | palette << 12;
                map[(y * 32 + x) * 2] = (byte) e;
                map[(y * 32 + x) * 2 + 1] = (byte) (e >> 8);
            }
        }
        return map;
    }

    private static void palette(java.io.ByteArrayOutputStream out, int[] colors) {
        for (int c : colors) {
            out.write(c);
            out.write(c >> 8);
        }
    }

    /** Bloc compressé, complété par des zéros jusqu'à un multiple de 4 octets (comme les fichiers .lz du jeu). */
    private static void lzBlock(java.io.ByteArrayOutputStream out, byte[] data) {
        byte[] lz = Lz77Encoder.encode(data);
        out.write(lz, 0, lz.length);
        while (out.size() % 4 != 0) out.write(0);
    }

    private void put(byte[] data) {
        int o = alloc(data.length);
        System.arraycopy(data, 0, rom, o, data.length);
    }

    /** Script « début de partie » : 40 setflag (dont celui du PNJ d'histoire), puis end. */
    private void buildResetScript() {
        int o = alloc(40 * 3 + 4);
        for (int i = 0; i < 40; i++) {
            rom[o + i * 3] = 0x29;
            w16(o + i * 3 + 1, i == 7 ? STORY_FLAG : 0x200 + i);
        }
        rom[o + 40 * 3] = 0x02;
    }

    public static final int FLOWER_METATILE = 9;

    /** Animation des fleurs (Rouge Feu : 4 tuiles en 508, 5 frames) : frame n de couleur 1 + n % 2. */
    private void buildTileAnims() {
        int frames = alloc(5 * 128);
        for (int f = 0; f < 5; f++) for (int t = 0; t < 4; t++) fillTile(frames, f * 4 + t, 1 + f % 2);
        int table = alloc(5 * 4);
        for (int f = 0; f < 5; f++) ptr(table + f * 4, frames + f * 128);
    }

    private int buildSecondaryTileset() {
        byte[] tiles = new byte[384 * 32];
        for (int i = 0; i < 32; i++) tiles[i] = 0x33; // tuile locale 0 : couleur 3 partout
        int tileData = alloc(tiles.length + tiles.length / 8 + 16);
        writeLz(tileData, tiles);

        int pals = alloc(16 * 32);
        w16(pals + 7 * 32 + 6, YELLOW);

        int metatiles = alloc(384 * 16);
        for (int slot = 0; slot < 4; slot++) w16(metatiles + slot * 2, 640 | (7 << 12));

        int attrs = alloc(384 * 4);
        int ts = alloc(0x18);
        rom[ts] = 1;      // compressé
        rom[ts + 1] = 1;  // secondaire
        ptr(ts + 4, tileData);
        ptr(ts + 8, pals);
        ptr(ts + 0x0C, metatiles);
        ptr(ts + 0x14, attrs);
        return ts;
    }

    private void metatile(int base, int id, int bottomTile, int bottomPal, int topTile, int topPal) {
        int o = base + id * 16;
        for (int slot = 0; slot < 4; slot++) w16(o + slot * 2, bottomTile | bottomPal << 12);
        for (int slot = 4; slot < 8; slot++) w16(o + slot * 2, topTile | topPal << 12);
    }

    private void fillTile(int tilesBase, int index, int color) {
        for (int i = 0; i < 32; i++) rom[tilesBase + index * 32 + i] = (byte) (color | color << 4);
    }

    // ------------------------------------------------------------------ cartes

    /**
     * Carte A « BOURG TEST » 6x5 : herbe en ligne 2, arbre bloquant en (5,0), métatile secondaire en (3,4),
     * warp en (1,1) vers B, liaison sud vers B (décalage 1).
     * Carte B « ROUTE TEST » 4x3 : warp en (2,1) vers A.
     */
    private void buildMapsAndWild(int primary, int secondary) {
        int[] gridA = new int[6 * 5];
        for (int i = 0; i < gridA.length; i++) gridA[i] = 0x3000;
        for (int x = 0; x < 6; x++) gridA[2 * 6 + x] = 0x3000 | 1;
        gridA[6 + 1] = 0x3000 | DOOR_METATILE; // porte animée sur le warp vers B
        gridA[0] = 0x3000 | FLOWER_METATILE;   // fleurs en (0,0)
        gridA[5] = 0x3000 | 0x0400 | 2;
        gridA[4 * 6 + 3] = 0x3000 | 640;
        int layoutA = layout(6, 5, gridA, primary, secondary);

        int[] gridB = new int[4 * 3];
        for (int i = 0; i < gridB.length; i++) gridB[i] = 0x3000;
        int layoutB = layout(4, 3, gridB, primary, secondary);

        int warpsA = alloc(8);
        warp(warpsA, 1, 1, 0, 0, 1);
        int eventsA = events(1, warpsA);
        // PNJ en (2,4) tourné vers le haut, avec un texte ; un 2e, à drapeau (histoire), absent en début de partie
        int hello = alloc(64);
        text(hello, "BONJOUR @!\nBELLE JOURNEE.|A PLUS TARD.");
        int script = alloc(16);
        rom[script] = 0x6A;                       // lock
        rom[script + 1] = 0x5A;                   // faceplayer
        rom[script + 2] = 0x0F;                   // loadword 0, texte
        rom[script + 3] = 0;
        ptr(script + 4, hello);
        rom[script + 8] = 0x09;                   // callstd MSGBOX_NPC
        rom[script + 9] = 2;
        rom[script + 10] = 0x6C;                  // release
        rom[script + 11] = 0x02;                  // end
        int objects = alloc(2 * 24);
        object(objects, 1, NPC_GRAPHICS, 2, 4, 7, script, 0);
        object(objects + 24, 2, NPC_GRAPHICS, 0, 4, 8, script, 0x0123);
        rom[eventsA] = 2;
        ptr(eventsA + 4, objects);
        // Panneau en (3,0), lisible de face
        int signText = alloc(32);
        text(signText, "BOURG TEST");
        int signScript = alloc(12);
        rom[signScript] = 0x0F;
        rom[signScript + 1] = 0;
        ptr(signScript + 2, signText);
        rom[signScript + 6] = 0x09;               // callstd MSGBOX_SIGN
        rom[signScript + 7] = 3;
        rom[signScript + 8] = 0x02;
        int signs = alloc(12);
        w16(signs, 3);
        w16(signs + 2, 0);
        rom[signs + 5] = 1;                       // lisible en regardant vers le haut
        ptr(signs + 8, signScript);
        rom[eventsA + 3] = 1;
        ptr(eventsA + 0x10, signs);
        // Case déclencheur en (4,3) tant que TRIGGER_VAR = 0 : le joueur recule d'un pas à gauche, un message
        int moves = alloc(4);
        rom[moves] = 0x12;                        // walk_left
        rom[moves + 1] = (byte) 0xFE;
        int trapText = alloc(16);
        text(trapText, "ATTENTION !");
        int trap = alloc(32);
        int t = trap;
        rom[t++] = 0x4F; w16(t, 0xFF); t += 2; ptr(t, moves); t += 4;   // applymovement joueur
        rom[t++] = 0x51; w16(t, 0); t += 2;                             // waitmovement 0
        rom[t++] = 0x16; w16(t, TRIGGER_VAR); w16(t + 2, 1); t += 4;    // setvar
        rom[t++] = 0x0F; rom[t++] = 0; ptr(t, trapText); t += 4;        // loadword 0
        rom[t++] = 0x09; rom[t++] = 6;                                  // callstd MSGBOX_AUTOCLOSE
        rom[t] = 0x02;
        int coords = alloc(16);
        w16(coords, 4);
        w16(coords + 2, 3);
        w16(coords + 6, TRIGGER_VAR);
        w16(coords + 8, 0);
        ptr(coords + 12, trap);
        rom[eventsA + 2] = 1;
        ptr(eventsA + 0x0C, coords);
        int warpsB = alloc(8);
        warp(warpsB, 2, 1, 0, 0, 0);
        int eventsB = events(1, warpsB);

        int conn = alloc(12);
        rom[conn] = 1;          // sud
        w32(conn + 4, 1);       // décalage
        rom[conn + 8] = 0;      // groupe
        rom[conn + 9] = 1;      // numéro
        int connHeader = alloc(8);
        w32(connHeader, 1);
        ptr(connHeader + 4, conn);

        int headerA = header(layoutA, eventsA, connHeader, 0x58, 1);
        int headerB = header(layoutB, eventsB, -1, 0x59, 3);

        int group0 = alloc(12);   // 2 cartes puis un pointeur nul
        ptr(group0, headerA);
        ptr(group0 + 4, headerB);
        int groups = alloc(8);
        ptr(groups, group0);
        signature(GameProfile.SIG_MAP_GROUPS, groups);

        int nameA = alloc(16);
        text(nameA, "BOURG TEST");
        int nameB = alloc(16);
        text(nameB, "ROUTE TEST");
        int labels = alloc(8);
        ptr(labels, nameA);
        ptr(labels + 4, nameB);
        signature(GameProfile.FIRERED_LEAFGREEN.mapLabelsSignature, labels);

        int slots = alloc(12 * 4);
        for (int i = 0; i < 12; i++) {
            rom[slots + i * 4] = 2;
            rom[slots + i * 4 + 1] = 4;
            w16(slots + i * 4 + 2, i < 6 ? 1 : 2);
        }
        int grass = alloc(8);
        rom[grass] = 21;
        ptr(grass + 4, slots);
        int wild = alloc(40);
        rom[wild] = 0;
        rom[wild + 1] = 0;
        ptr(wild + 4, grass);
        rom[wild + 20] = (byte) 0xFF;
        rom[wild + 21] = (byte) 0xFF;
        signature(GameProfile.SIG_WILD_ENCOUNTERS, wild);
    }

    /** Métatile de porte animée posé sur le warp de la carte A. */
    public static final int DOOR_METATILE = 3;

    /** Table des portes : 6 entrées (métatiles 3 à 8), la première sur le warp de A. */
    private void buildDoorAnims() {
        int table = alloc(7 * 12);
        for (int k = 0; k < 6; k++) {
            boolean big = k == 5;
            int tiles = alloc(3 * (big ? 8 : 4) * 32);
            for (int s = 0; s < 3; s++) {
                for (int i = 0; i < (big ? 8 : 4); i++) {
                    fillTile(tiles, s * (big ? 8 : 4) + i, s + 1); // étape s : couleur s+1
                }
            }
            int pals = alloc(8);
            int e = table + k * 12;
            w16(e, DOOR_METATILE + k);
            rom[e + 2] = 0;
            rom[e + 3] = (byte) (big ? 1 : 0);
            ptr(e + 4, tiles);
            ptr(e + 8, pals);
        }
        // entrée nulle déjà à zéro : fin de table
    }

    private int layout(int w, int h, int[] grid, int primary, int secondary) {
        int gridData = alloc(grid.length * 2);
        for (int i = 0; i < grid.length; i++) w16(gridData + i * 2, grid[i]);
        int border = alloc(8);
        for (int i = 0; i < 4; i++) w16(border + i * 2, 2);
        int o = alloc(0x1C);
        w32(o, w);
        w32(o + 4, h);
        ptr(o + 8, border);
        ptr(o + 0x0C, gridData);
        ptr(o + 0x10, primary);
        ptr(o + 0x14, secondary);
        rom[o + 0x18] = 2;
        rom[o + 0x19] = 2;
        return o;
    }

    private void warp(int o, int x, int y, int warpId, int group, int num) {
        w16(o, x);
        w16(o + 2, y);
        rom[o + 4] = 0;
        rom[o + 5] = (byte) warpId;
        rom[o + 6] = (byte) num;
        rom[o + 7] = (byte) group;
    }

    public static final int NPC_GRAPHICS = 1;
    public static final int TRIGGER_VAR = 0x4050;
    public static final int STORY_FLAG = 0x0123;
    public static final int NPC_COLOR = 0x7FE0; // cyan

    private void object(int o, int localId, int gfx, int x, int y, int movement, int script, int flag) {
        rom[o] = (byte) localId;
        rom[o + 1] = (byte) gfx;
        w16(o + 4, x);
        w16(o + 6, y);
        rom[o + 8] = 3;
        rom[o + 9] = (byte) movement;
        ptr(o + 0x10, script);
        w16(o + 0x14, flag);
    }

    private int events(int warpCount, int warps) {
        int o = alloc(0x14);
        rom[o + 1] = (byte) warpCount;
        ptr(o + 8, warps);
        return o;
    }

    private int header(int layout, int events, int connections, int section, int mapType) {
        int o = alloc(0x1C);
        ptr(o, layout);
        ptr(o + 4, events);
        if (connections >= 0) ptr(o + 0x0C, connections);
        rom[o + 0x14] = (byte) section;
        rom[o + 0x17] = (byte) mapType;
        return o;
    }

    private void signature(String hex, int target) {
        byte[] sig = HexFormat.of().parseHex(hex);
        int o = alloc(sig.length + 4);
        System.arraycopy(sig, 0, rom, o, sig.length);
        ptr(o + sig.length, target);
    }

    // ------------------------------------------------------------------ utilitaires

    private int alloc(int bytes) {
        int o = cursor;
        cursor = (cursor + bytes + 3 + 4) & ~3; // +4 : marge de zéros entre les blocs
        if (cursor > SIZE) throw new IllegalStateException("ROM synthétique trop petite");
        return o;
    }

    private void ptr(int at, int target) {
        w32(at, 0x0800_0000 + target);
    }

    private void w16(int at, int v) {
        rom[at] = (byte) v;
        rom[at + 1] = (byte) (v >> 8);
    }

    private void w32(int at, int v) {
        w16(at, v);
        w16(at + 2, v >> 16);
    }

    private static void put16(byte[] a, int at, int v) {
        a[at] = (byte) v;
        a[at + 1] = (byte) (v >> 8);
    }

    private void ascii(int at, String s) {
        byte[] b = s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(b, 0, rom, at, b.length);
    }

    /** Encode une chaîne dans le jeu de caractères Gen 3, terminée par 0xFF. */
    private void text(int at, String s) {
        int i = at;
        for (int c = 0; c < s.length(); c++) {
            char special = s.charAt(c);
            if (special == '\n' || special == '|' || special == '@') { // retour à la ligne, page, {PLAYER}
                if (special == '@') {
                    rom[i++] = (byte) 0xFD;
                    rom[i++] = 0x01;
                } else {
                    rom[i++] = (byte) (special == '|' ? 0xFB : 0xFE);
                }
                continue;
            }
            String ch = String.valueOf(s.charAt(c));
            int code = -1;
            for (int k = 0; k < 0xF7; k++) {
                if (ch.equals(TextCodec.decodeChar(k))) {
                    code = k;
                    break;
                }
            }
            if (code < 0) throw new IllegalArgumentException("Caractère non encodable : " + ch);
            rom[i++] = (byte) code;
        }
        rom[i] = (byte) 0xFF;
    }

    /** Écrit un bloc LZ77 valide (avec répétitions) — sert aussi à tester le décompresseur. */
    private void writeLz(int at, byte[] data) {
        byte[] lz = Lz77Encoder.encode(data);
        System.arraycopy(lz, 0, rom, at, lz.length);
    }
}
