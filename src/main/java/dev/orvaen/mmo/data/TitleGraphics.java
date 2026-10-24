package dev.orvaen.mmo.data;

import dev.orvaen.mmo.gfx.GbaGfx;
import dev.orvaen.mmo.rom.Lz77;
import dev.orvaen.mmo.rom.Rom;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Images de l'écran titre de Rouge Feu / Vert Feuille, lues dans la ROM du joueur.
 * <p>
 * L'écran titre du jeu est fait de 4 calques (du plus proche au plus lointain) :
 * <ol>
 *   <li>le logo « POKéMON ROUGE FEU » (image 8 bits, 256 couleurs) ;</li>
 *   <li>le Pokémon de la boîte (Dracaufeu / Florizarre) ;</li>
 *   <li>le texte du bas « PRESS START » et le copyright ;</li>
 *   <li>le cadre de fond.</li>
 * </ol>
 * Par-dessus le cadre volent des flammes (ou des feuilles), et un reflet balaie le logo de temps en temps.
 *
 * <h2>Comment on les retrouve</h2>
 * Aucune adresse n'est écrite en dur : on cherche des données « rangées à la suite » qui ont la bonne forme.
 * <ul>
 *   <li><b>Logo, Pokémon, texte</b> : palette 256 couleurs, tuiles, plan (32x20 cases = 0x500 octets),
 *       puis palette 16 couleurs, tuiles, plan… trois fois de suite.</li>
 *   <li><b>Cadre, reflet, flammes</b> : tuiles du cadre (4 tuiles = 0x80 octets), plan du cadre (0x500),
 *       forme du reflet (0x800), palette des flammes, images des flammes.</li>
 * </ul>
 * Ce qui n'est pas trouvé reste {@code null} : l'écran titre utilise alors son fond de secours.
 */
public final class TitleGraphics {

    /** Taille de l'écran de la GBA. */
    public static final int WIDTH = 240, HEIGHT = 160;

    private static final int MAP_BYTES = 0x500;       // plan 32 x 20 cases, 2 octets par case
    private static final int BORDER_TILES = 0x80;     // 4 tuiles 4 bits
    private static final int SLASH_BYTES = 0x800;     // sprite 64x64 4 bits

    /** Couleur du fond de l'écran (couleur 0 de la palette). */
    public int backdrop = 0xFF000000;
    /** Calques 240x160 avec transparence (null si absents). */
    public BufferedImage logo, monster, pressStart, copyrightOnly, border;
    /** Flammes : images 16x16 de l'animation. */
    public BufferedImage[] flames;
    /** Forme du reflet (64x64) : true là où le logo s'éclaire. */
    public boolean[] slash;

    public TitleGraphics(Rom rom) {
        try {
            readMainLayers(rom);
        } catch (RuntimeException ignored) {
            // ROM modifiée : pas de logo
        }
        try {
            readBorderAndSprites(rom);
        } catch (RuntimeException ignored) {
            // pas de cadre ni de flammes
        }
    }

    /** Vrai si l'écran titre de la ROM peut être dessiné. */
    public boolean available() {
        return logo != null && monster != null;
    }

    // ------------------------------------------------------------------ logo, Pokémon, texte

    private void readMainLayers(Rom rom) {
        for (int pal = 0; pal + 512 + 8 <= rom.size(); pal += 2) {
            int tiles = pal + 512;
            if (!lzHeader(rom, tiles) || lzSize(rom, tiles) % 64 != 0 || !palette(rom, pal, 256)) continue;
            int logoMap = nextLz(rom, Lz77.end(rom, tiles), MAP_BYTES);
            if (logoMap < 0) continue;
            int monPal = paletteAfter(rom, logoMap);
            if (monPal < 0) continue;
            int monTiles = monPal + 32;
            int monMap = nextLz(rom, Lz77.end(rom, monTiles), MAP_BYTES);
            if (monMap < 0) continue;
            int textPal = paletteAfter(rom, monMap);
            if (textPal < 0) continue;
            int textTiles = textPal + 32;
            int textMap = nextLz(rom, Lz77.end(rom, textTiles), MAP_BYTES);
            if (textMap < 0) continue;

            // Palette de l'écran comme dans le jeu : 0-12 logo, 13 Pokémon, 14 et 15 fond/texte.
            int[] screen = new int[256];
            int[] logoColors = readPalette(rom, pal, 256);
            System.arraycopy(logoColors, 0, screen, 0, 13 * 16);
            System.arraycopy(readPalette(rom, monPal, 16), 0, screen, 13 * 16, 16);
            int[] text = readPalette(rom, textPal, 16);
            System.arraycopy(text, 0, screen, 14 * 16, 16);
            System.arraycopy(text, 0, screen, 15 * 16, 16);
            backdrop = screen[0] | 0xFF000000;
            backgroundPalette = text;

            logo = layer8bpp(Lz77.decompress(rom, tiles), Lz77.decompress(rom, logoMap), screen);
            monster = layer4bpp(Lz77.decompress(rom, monTiles), Lz77.decompress(rom, monMap), screen);
            byte[] tt = Lz77.decompress(rom, textTiles), tm = Lz77.decompress(rom, textMap);
            pressStart = layer4bpp(tt, tm, screen);
            // « PRESS START » clignote : le jeu remplace les couleurs 1 à 5 du texte par la couleur 6.
            int[] blink = screen.clone();
            for (int i = 1; i <= 5; i++) blink[15 * 16 + i] = screen[15 * 16 + 6];
            copyrightOnly = layer4bpp(tt, tm, blink);
            return;
        }
    }

    /** Palette des calques du fond (utilisée aussi par le cadre). */
    private int[] backgroundPalette;

    // ------------------------------------------------------------------ cadre, reflet, flammes

    private void readBorderAndSprites(Rom rom) {
        for (int o = 0; o + 8 <= rom.size(); o++) {
            if (!lzHeader(rom, o) || lzSize(rom, o) != BORDER_TILES) continue;
            int map = nextLz(rom, Lz77.end(rom, o), MAP_BYTES);
            if (map < 0) continue;
            int slashAt = nextLz(rom, Lz77.end(rom, map), SLASH_BYTES);
            if (slashAt < 0) continue;
            int flamePal = paletteAfter(rom, slashAt);
            if (flamePal < 0) continue;
            int flameTiles = flamePal + 32;

            if (backgroundPalette != null) {
                int[] screen = new int[256];
                System.arraycopy(backgroundPalette, 0, screen, 14 * 16, 16);
                System.arraycopy(backgroundPalette, 0, screen, 15 * 16, 16);
                border = layer4bpp(Lz77.decompress(rom, o), Lz77.decompress(rom, map), screen);
            }

            byte[] s = Lz77.decompress(rom, slashAt);
            slash = new boolean[64 * 64];
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int tile = (y / 8) * 8 + x / 8;
                    slash[y * 64 + x] = GbaGfx.pixel4bpp(s, tile, x % 8, y % 8) != 0;
                }
            }

            int[] pal = readPalette(rom, flamePal, 16);
            byte[] f = Lz77.decompress(rom, flameTiles);
            int count = f.length / 128;                     // une image 16x16 = 4 tuiles = 128 octets
            flames = new BufferedImage[count];
            for (int i = 0; i < count; i++) {
                flames[i] = GbaGfx.sprite(Arrays.copyOfRange(f, i * 128, i * 128 + 128), 2, 2, pal);
            }
            return;
        }
    }

    // ------------------------------------------------------------------ dessin des calques

    /** Calque 4 bits : chaque case du plan donne la tuile, la palette et le retournement. */
    private static BufferedImage layer4bpp(byte[] tiles, byte[] map, int[] palettes) {
        int[] out = new int[WIDTH * HEIGHT];
        int maxTile = tiles.length / 32;
        for (int ty = 0; ty < 20; ty++) {
            for (int tx = 0; tx < 30; tx++) {
                int e = entry(map, ty * 32 + tx);
                if ((e & 0x3FF) >= maxTile) continue;
                GbaGfx.drawTile(out, WIDTH, tx * 8, ty * 8, tiles, e & 0x3FF, palettes, (e >> 12) & 0xF,
                        (e & 0x400) != 0, (e & 0x800) != 0);
            }
        }
        return GbaGfx.toImage(out, WIDTH, HEIGHT);
    }

    /** Calque 8 bits : un octet par pixel, qui désigne directement une des 256 couleurs. */
    private static BufferedImage layer8bpp(byte[] tiles, byte[] map, int[] palette) {
        int[] out = new int[WIDTH * HEIGHT];
        int maxTile = tiles.length / 64;
        for (int ty = 0; ty < 20; ty++) {
            for (int tx = 0; tx < 30; tx++) {
                int e = entry(map, ty * 32 + tx);
                int tile = e & 0x3FF;
                if (tile >= maxTile) continue;
                boolean hflip = (e & 0x400) != 0, vflip = (e & 0x800) != 0;
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        int sx = hflip ? 7 - x : x, sy = vflip ? 7 - y : y;
                        int ci = tiles[tile * 64 + sy * 8 + sx] & 0xFF;
                        if (ci != 0) out[(ty * 8 + y) * WIDTH + tx * 8 + x] = palette[ci] | 0xFF000000;
                    }
                }
            }
        }
        return GbaGfx.toImage(out, WIDTH, HEIGHT);
    }

    private static int entry(byte[] map, int i) {
        return i * 2 + 1 < map.length ? (map[i * 2] & 0xFF) | (map[i * 2 + 1] & 0xFF) << 8 : 0;
    }

    // ------------------------------------------------------------------ outils de recherche

    private static boolean lzHeader(Rom rom, int o) {
        return o >= 0 && rom.inRange(o, 4) && rom.u8(o) == 0x10 && lzSize(rom, o) > 0;
    }

    private static int lzSize(Rom rom, int o) {
        return rom.u8(o + 1) | rom.u8(o + 2) << 8 | rom.u8(o + 3) << 16;
    }

    /**
     * Prochain bloc compressé juste après {@code from} (les données peuvent être séparées de
     * quelques octets de remplissage). {@code size} : taille attendue une fois décompressé, ou -1.
     */
    private static int nextLz(Rom rom, int from, int size) {
        if (from < 0) return -1;
        for (int o = from; o < from + 8; o++) {
            if (lzHeader(rom, o) && (size < 0 || lzSize(rom, o) == size) && Lz77.end(rom, o) > 0) return o;
        }
        return -1;
    }

    /**
     * Palette de 16 couleurs rangée juste après un bloc compressé, et elle-même suivie d'un autre
     * bloc compressé (les tuiles qu'elle colore). Renvoie sa position, ou -1.
     */
    private static int paletteAfter(Rom rom, int lz) {
        int end = Lz77.end(rom, lz);
        if (end < 0) return -1;
        for (int o = end; o < end + 8; o++) {
            if (palette(rom, o, 16) && lzHeader(rom, o + 32) && Lz77.end(rom, o + 32) > 0) return o;
        }
        return -1;
    }

    /** Palette plausible : couleurs BGR555, bit 15 à zéro. */
    private static boolean palette(Rom rom, int o, int colors) {
        if (o < 0 || !rom.inRange(o, colors * 2)) return false;
        for (int i = 0; i < colors; i++) if ((rom.u16(o + i * 2) & 0x8000) != 0) return false;
        return true;
    }

    private static int[] readPalette(Rom rom, int o, int colors) {
        int[] p = new int[colors];
        for (int i = 0; i < colors; i++) p[i] = GbaGfx.bgr555ToArgb(rom.u16(o + i * 2));
        return p;
    }
}
