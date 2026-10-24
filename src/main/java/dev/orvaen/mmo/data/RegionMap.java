package dev.orvaen.mmo.data;

import dev.orvaen.mmo.gfx.GbaGfx;
import dev.orvaen.mmo.rom.Lz77;
import dev.orvaen.mmo.rom.Rom;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * La Carte de la Région de Rouge Feu / Vert Feuille (l'objet « Carte »), lue dans la ROM du joueur :
 * Kanto et les Îles Sevii (1-2-3, 4-5, 6-7).
 * <p>
 * Ce qu'on retrouve, chaque morceau par sa forme :
 * <ul>
 *   <li><b>le dessin</b> : les tuiles (LZ, 0x2800 octets) et 4 plans d'écran de 30 × 20 cases (LZ, 1200 octets
 *       chacun, rangés à la suite : Kanto, puis les îles) ;</li>
 *   <li><b>la palette</b> : 80 couleurs, juste avant une petite palette inutilisée du jeu
 *       (bleu, bleu clair, blanc, blanc) qui sert de repère ;</li>
 *   <li><b>les lieux</b> : pour chaque carte, une grille de 22 × 15 cases donnant le lieu (« section ») de
 *       chaque case, en 2 couches (la carte, puis les donjons) ; les 4 grilles se suivent.</li>
 * </ul>
 * Sur l'écran du jeu, la case (x, y) de la grille est dessinée au pixel (32 + 8x, 32 + 8y).
 */
public final class RegionMap {

    public static final int MAPS = 4;
    public static final String[] NAMES = {"Kanto", "Îles 1·2·3", "Îles 4·5", "Îles 6·7"};
    public static final int GRID_W = 22, GRID_H = 15;
    /** Pixel de l'écran où commence la case (0, 0). */
    public static final int GRID_X = 32, GRID_Y = 32;
    /** Partie de l'écran (240 × 160) qui contient la carte, sans la barre du haut. */
    public static final int VIEW_X = 24, VIEW_Y = 16, VIEW_W = 192, VIEW_H = 144;

    private static final int SCREEN_W = 30, SCREEN_H = 20, TILEMAP_BYTES = SCREEN_W * SCREEN_H * 2;
    private static final int TILES_BYTES = 0x2800, PAL_COLORS = 80;
    private static final int LAYERS = 2, GRID_BYTES = GRID_W * GRID_H * LAYERS;
    private static final byte[] PAL_MARK = {0x00, 0x7C, (byte) 0x80, 0x7D, (byte) 0xFF, 0x7F, (byte) 0xFF, 0x7F};

    private final Rom rom;
    private final int[] tilemaps = new int[MAPS];
    private final int tiles, palette, grids;
    private final int none;                      // valeur « pas de lieu » des grilles
    private final Map<Integer, BufferedImage> images = new HashMap<>();
    private byte[] tileData;

    public RegionMap(Rom rom) {
        this.rom = rom;
        int first = findTilemaps();
        for (int i = 0; i < MAPS; i++) tilemaps[i] = -1;
        int t = -1;
        if (first >= 0) {
            int o = first;
            for (int i = 0; i < MAPS; i++) {
                tilemaps[i] = o;
                o = (Lz77.end(rom, o) + 3) & ~3;
            }
            t = findTiles(first);
        }
        this.tiles = t;
        this.palette = findPalette();
        int g = findGrids();
        this.grids = g;
        this.none = g < 0 ? -1 : mostFrequent(g);
    }

    /** Tout a été retrouvé : la carte peut s'afficher. */
    public boolean available() {
        return tiles >= 0 && palette >= 0 && grids >= 0 && tilemaps[0] >= 0;
    }

    /** Lieu (« section ») de la case (x, y) de la carte n° {@code map}, ou -1. */
    public int sectionAt(int map, int x, int y) {
        if (grids < 0 || map < 0 || map >= MAPS || x < 0 || y < 0 || x >= GRID_W || y >= GRID_H) return -1;
        int v = rom.u8(grids + map * GRID_BYTES + y * GRID_W + x);
        return v == none ? -1 : v;
    }

    /** Carte (0 Kanto, 1-3 îles) qui contient ce lieu, ou -1. */
    public int mapOf(int section) {
        if (section < 0) return -1;
        for (int m = 0; m < MAPS; m++) {
            for (int y = 0; y < GRID_H; y++) for (int x = 0; x < GRID_W; x++) if (sectionAt(m, x, y) == section) return m;
        }
        // un donjon (2e couche) : on regarde aussi cette couche
        if (grids < 0) return -1;
        for (int m = 0; m < MAPS; m++) {
            for (int i = 0; i < GRID_W * GRID_H; i++) {
                if (rom.u8(grids + m * GRID_BYTES + GRID_W * GRID_H + i) == section) return m;
            }
        }
        return -1;
    }

    /** Lieu de la case, couche des donjons comprise (grotte, forêt… posées sur la carte), ou -1. */
    public int dungeonAt(int map, int x, int y) {
        if (grids < 0 || map < 0 || map >= MAPS || x < 0 || y < 0 || x >= GRID_W || y >= GRID_H) return -1;
        int v = rom.u8(grids + map * GRID_BYTES + GRID_W * GRID_H + y * GRID_W + x);
        return v == none ? -1 : v;
    }

    /** Écran complet de la carte n° {@code map} (240 × 160), ou null. */
    public synchronized BufferedImage image(int map) {
        if (!available() || map < 0 || map >= MAPS || tilemaps[map] < 0) return null;
        return images.computeIfAbsent(map, this::render);
    }

    private BufferedImage render(int map) {
        try {
            if (tileData == null) tileData = Lz77.decompress(rom, tiles);
            byte[] tm = Lz77.decompress(rom, tilemaps[map]);
            int[] pals = GbaGfx.readPalettes(rom, palette, PAL_COLORS / 16);
            int w = SCREEN_W * 8, h = SCREEN_H * 8;
            int[] px = new int[w * h];
            java.util.Arrays.fill(px, pals[0]);                       // couleur de fond (couleur 0)
            for (int ty = 0; ty < SCREEN_H; ty++) {
                for (int tx = 0; tx < SCREEN_W; tx++) {
                    int i = (ty * SCREEN_W + tx) * 2;
                    int e = (tm[i] & 0xFF) | (tm[i + 1] & 0xFF) << 8;
                    GbaGfx.drawTile(px, w, tx * 8, ty * 8, tileData, e & 0x3FF, pals, e >> 12,
                            (e & 0x400) != 0, (e & 0x800) != 0);
                }
            }
            return GbaGfx.toImage(px, w, h);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ recherche dans la ROM

    /** Taille annoncée d'un bloc LZ, ou -1. */
    private int lzSize(int o) {
        if (o < 0 || !rom.inRange(o, 4) || rom.u8(o) != 0x10) return -1;
        return rom.u8(o + 1) | rom.u8(o + 2) << 8 | rom.u8(o + 3) << 16;
    }

    /** 4 plans d'écran de 1200 octets à la suite : le premier est Kanto. */
    private int findTilemaps() {
        for (int o = 0; o + 16 <= rom.size(); o += 4) {
            if (lzSize(o) != TILEMAP_BYTES) continue;
            int p = o, n = 0;
            while (n < MAPS && lzSize(p) == TILEMAP_BYTES) {
                int end = Lz77.end(rom, p);
                if (end < 0) break;
                n++;
                p = (end + 3) & ~3;
            }
            if (n == MAPS) return o;
        }
        return -1;
    }

    /** Les tuiles : le bloc LZ de 0x2800 octets le plus proche avant les plans (3 blocs avant, dans le jeu). */
    private int findTiles(int before) {
        for (int o = (before - 4) & ~3; o >= Math.max(0, before - 0x10000); o -= 4) {
            if (lzSize(o) == TILES_BYTES && Lz77.end(rom, o) > 0 && Lz77.end(rom, o) <= before) return o;
        }
        return -1;
    }

    private int findPalette() {
        int from = 0;
        while (true) {
            int o = rom.find(PAL_MARK, from);
            if (o < 0) return -1;
            if (o >= PAL_COLORS * 2 && (o & 1) == 0) return o - PAL_COLORS * 2;
            from = o + 1;
        }
    }

    /**
     * Les 4 grilles de lieux (2 couches de 22 × 15 octets chacune) : une longue suite d'octets presque tous
     * égaux à « pas de lieu », avec quelques n° de lieux de Kanto (0x58 et plus).
     */
    private int findGrids() {
        int size = rom.size();
        int need = GRID_BYTES * MAPS;
        int run = 0;
        for (int o = 0; o <= size; o++) {
            int v = o < size ? rom.u8(o) : 0;
            if (v >= 0x58 && v <= 0xC5) {
                run++;
                continue;
            }
            // fin d'une suite : les grilles finissent juste avant (suivies des destinations du vol, petits n°)
            if (run >= need) {
                int start = o - need;
                int none = mostFrequent(start);
                int count = 0;
                for (int i = 0; i < need; i++) if (rom.u8(start + i) == none) count++;
                if (count > need / 2) return start;
            }
            run = 0;
        }
        return -1;
    }

    private int mostFrequent(int start) {
        int[] counts = new int[256];
        for (int i = 0; i < GRID_BYTES * MAPS; i++) counts[rom.u8(start + i)]++;
        int best = 0;
        for (int v = 1; v < 256; v++) if (counts[v] > counts[best]) best = v;
        return best;
    }
}
