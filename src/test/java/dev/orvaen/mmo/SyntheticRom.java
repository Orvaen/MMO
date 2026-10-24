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

    public static final int SIZE = 0x40000;

    // Couleurs (BGR555) choisies pour être faciles à reconnaître au rendu
    public static final int GREEN = 0x03E0;   // tuile 1 · palette 0 · couleur 1
    public static final int BLUE = 0x7C00;    // tuile 2 · palette 0 · couleur 2
    public static final int RED = 0x001F;     // sprite · couleur 1
    public static final int YELLOW = 0x03FF;  // tileset secondaire · palette 7 · couleur 3

    private final byte[] rom = new byte[SIZE];
    private int cursor = 0x400;

    public static byte[] build() {
        return new SyntheticRom().make();
    }

    private byte[] make() {
        ascii(0xA0, "POKEMON FIRE");
        ascii(0xAC, "BPRE");
        rom[0xBC] = 0;

        buildSpecies();
        int primary = buildPrimaryTileset();
        int secondary = buildSecondaryTileset();
        buildMapsAndWild(primary, secondary);
        return rom;
    }

    // ------------------------------------------------------------------ espèces

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
        for (int i = 0; i < n; i++) {
            ptr(frontTable + i * 8, spriteData);
            ptr(palTable + i * 8, palData);
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
        gridA[5] = 0x3000 | 0x0400 | 2;
        gridA[4 * 6 + 3] = 0x3000 | 640;
        int layoutA = layout(6, 5, gridA, primary, secondary);

        int[] gridB = new int[4 * 3];
        for (int i = 0; i < gridB.length; i++) gridB[i] = 0x3000;
        int layoutB = layout(4, 3, gridB, primary, secondary);

        int warpsA = alloc(8);
        warp(warpsA, 1, 1, 0, 0, 1);
        int eventsA = events(1, warpsA);
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
