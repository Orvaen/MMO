package dev.orvaen.mmo.battle;

import dev.orvaen.mmo.gfx.GbaGfx;
import dev.orvaen.mmo.rom.GameProfile;
import dev.orvaen.mmo.rom.Lz77;
import dev.orvaen.mmo.rom.Rom;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Les images de l'écran de combat, lues dans la ROM :
 * <ul>
 *   <li><b>fonds</b> (herbe, grotte, bâtiment…) : une table de 5 pointeurs par terrain
 *       {tuiles, plan, tuiles d'entrée, plan d'entrée, palettes} ;</li>
 *   <li><b>boîte de texte</b> : tuiles, palettes et plan rangés à la suite. Le plan contient trois
 *       présentations : message seul, message + menu d'action, choix des attaques ;</li>
 *   <li><b>Poké Ball</b> : l'en-tête de la ROM pointe vers la table des balls.</li>
 * </ul>
 * Tout ce qui manque reste {@code null} et la scène dessine un remplaçant.
 */
public final class BattleGraphics {

    /** Terrains du jeu (constantes BATTLE_TERRAIN_* de la décompilation). */
    public static final int GRASS = 0, LONG_GRASS = 1, SAND = 2, WATER = 4, POND = 5, MOUNTAIN = 6, CAVE = 7,
            BUILDING = 8, PLAIN = 9;

    public static final int WIDTH = 240, HEIGHT = 160;
    /** Hauteur de la zone de combat au-dessus de la boîte de texte. */
    public static final int FIELD_HEIGHT = 112;

    private static final int TERRAIN_ENTRY = 20, MIN_TERRAINS = 10, TERRAIN_PAL_BYTES = 0x60;
    private static final int TEXTBOX_TILES = 0x2000, TEXTBOX_PAL = 0x40, TEXTBOX_MAP = 0x1000;

    /** Les trois présentations de la boîte de texte (lignes de son plan). */
    public enum Box {
        MESSAGE(14), ACTIONS(34), MOVES(54);
        final int firstRow;

        Box(int firstRow) {
            this.firstRow = firstRow;
        }
    }

    private final Rom rom;
    private final int terrainTable;
    private int terrainCount;
    private final Map<Integer, BufferedImage> terrains = new HashMap<>();
    private final Map<Box, BufferedImage> boxes = new HashMap<>();
    /** Poké Ball : fermée, entrouverte, ouverte (16x16). */
    public final BufferedImage[] ball;

    public BattleGraphics(Rom rom) {
        this.rom = rom;
        this.terrainTable = findTerrainTable();
        BufferedImage[] b = null;
        try {
            b = readBall();
        } catch (RuntimeException ignored) {
            // pas de ball : dessinée par le moteur
        }
        this.ball = b;
        try {
            readTextbox();
        } catch (RuntimeException ignored) {
            // pas de boîte de texte : dessinée par le moteur
        }
    }

    // ------------------------------------------------------------------ fonds

    /** Fond du terrain (240x112, la partie au-dessus de la boîte de texte), ou null. */
    public BufferedImage terrain(int id) {
        if (terrainTable < 0) return null;
        int t = id >= 0 && id < terrainCount ? id : PLAIN;
        return terrains.computeIfAbsent(t, this::readTerrain);
    }

    private BufferedImage readTerrain(int id) {
        try {
            int e = terrainTable + id * TERRAIN_ENTRY;
            byte[] tiles = Lz77.decompress(rom, rom.pointer(e));
            byte[] map = Lz77.decompress(rom, rom.pointer(e + 4));
            int[] pals = new int[16 * 16];
            int[] loaded = GbaGfx.palettesFromBytes(Lz77.decompress(rom, rom.pointer(e + 16)));
            System.arraycopy(loaded, 0, pals, 2 * 16, Math.min(loaded.length, 3 * 16));   // palettes 2 à 4
            return layer(tiles, map, pals, 0, FIELD_HEIGHT / 8, true);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private int findTerrainTable() {
        for (int o = 0; o + TERRAIN_ENTRY * MIN_TERRAINS <= rom.size(); o += 4) {
            int n = 0;
            while (terrainEntry(o + n * TERRAIN_ENTRY)) n++;
            if (n >= MIN_TERRAINS) {
                terrainCount = n;
                return o;
            }
            if (n > 0) o += n * TERRAIN_ENTRY - 4;
        }
        return -1;
    }

    private boolean terrainEntry(int e) {
        if (!rom.inRange(e, TERRAIN_ENTRY)) return false;
        for (int i = 0; i < 5; i++) if (!Lz77.looksCompressed(rom, rom.pointer(e + i * 4))) return false;
        int pal = rom.pointer(e + 16);
        return lzSize(pal) == TERRAIN_PAL_BYTES && lzSize(rom.pointer(e + 4)) >= 0x500;
    }

    // ------------------------------------------------------------------ boîte de texte

    /** Boîte du bas (240x48) dans la présentation demandée, ou null. */
    public BufferedImage box(Box b) {
        return boxes.get(b);
    }

    private void readTextbox() {
        for (int o = 0; o + 8 <= rom.size(); o += 4) {
            if (lzSize(o) != TEXTBOX_TILES || rom.u8(o) != 0x10) continue;
            int pal = next(Lz77.end(rom, o));
            if (pal < 0 || lzSize(pal) != TEXTBOX_PAL) continue;
            int map = next(Lz77.end(rom, pal));
            if (map < 0 || lzSize(map) != TEXTBOX_MAP) continue;
            byte[] tiles = Lz77.decompress(rom, o);
            byte[] tm = Lz77.decompress(rom, map);
            int[] pals = new int[16 * 16];
            int[] loaded = GbaGfx.palettesFromBytes(Lz77.decompress(rom, pal));
            System.arraycopy(loaded, 0, pals, 0, Math.min(loaded.length, 32));
            for (Box b : Box.values()) boxes.put(b, layer(tiles, tm, pals, b.firstRow, 6, false));
            return;
        }
    }

    /** Prochain bloc compressé juste après {@code from} (quelques octets de remplissage possibles). */
    private int next(int from) {
        if (from < 0) return -1;
        for (int o = from; o < from + 8; o++) if (rom.inRange(o, 4) && rom.u8(o) == 0x10 && Lz77.end(rom, o) > 0) return o;
        return -1;
    }

    // ------------------------------------------------------------------ Poké Ball

    private BufferedImage[] readBall() {
        int sheets = rom.pointer(GameProfile.PTR_BALL_SPRITES), pals = rom.pointer(GameProfile.PTR_BALL_PALETTES);
        if (sheets < 0 || pals < 0) return null;
        byte[] tiles = Lz77.decompress(rom, rom.pointer(sheets));
        int[] pal = GbaGfx.palettesFromBytes(Lz77.decompress(rom, rom.pointer(pals)));
        int frames = Math.max(1, tiles.length / 128);
        BufferedImage[] out = new BufferedImage[Math.min(3, frames)];
        for (int f = 0; f < out.length; f++) {
            out[f] = GbaGfx.sprite(Arrays.copyOfRange(tiles, f * 128, f * 128 + 128), 2, 2, pal);
        }
        return out;
    }

    // ------------------------------------------------------------------ outils

    private int lzSize(int o) {
        if (o < 0 || !rom.inRange(o, 4) || rom.u8(o) != 0x10) return -1;
        return rom.u8(o + 1) | rom.u8(o + 2) << 8 | rom.u8(o + 3) << 16;
    }

    /** Dessine {@code rows} lignes d'un plan 32 cases de large, à partir de la ligne {@code firstRow}. */
    private static BufferedImage layer(byte[] tiles, byte[] map, int[] pals, int firstRow, int rows, boolean opaque) {
        int[] out = new int[WIDTH * rows * 8];
        int maxTile = tiles.length / 32;
        for (int ty = 0; ty < rows; ty++) {
            for (int tx = 0; tx < 30; tx++) {
                int i = (firstRow + ty) * 32 + tx;
                if (i * 2 + 1 >= map.length) continue;
                int e = (map[i * 2] & 0xFF) | (map[i * 2 + 1] & 0xFF) << 8;
                if ((e & 0x3FF) >= maxTile) continue;
                GbaGfx.drawTile(out, WIDTH, tx * 8, ty * 8, tiles, e & 0x3FF, pals, (e >> 12) & 0xF,
                        (e & 0x400) != 0, (e & 0x800) != 0);
            }
        }
        if (opaque) {
            // couleur 0 de la 1re palette du terrain : le fond derrière les tuiles
            for (int i = 0; i < out.length; i++) if ((out[i] >>> 24) == 0) out[i] = pals[2 * 16] | 0xFF000000;
        }
        return GbaGfx.toImage(out, WIDTH, rows * 8);
    }
}
