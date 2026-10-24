package dev.orvaen.mmo.gfx;

import dev.orvaen.mmo.rom.Rom;

import java.awt.image.BufferedImage;

/** Conversion des formats graphiques GBA (palettes BGR555, tuiles 4bpp) vers des images Java. */
public final class GbaGfx {

    public static final int TILE_BYTES_4BPP = 32;
    public static final int TRANSPARENT = 0x0000_0000;

    private GbaGfx() {}

    /** Couleur GBA 15 bits (xBBBBBGGGGGRRRRR) vers ARGB opaque. */
    public static int bgr555ToArgb(int c) {
        int r = c & 0x1F, g = (c >> 5) & 0x1F, b = (c >> 10) & 0x1F;
        r = (r << 3) | (r >> 2);
        g = (g << 3) | (g >> 2);
        b = (b << 3) | (b >> 2);
        return 0xFF00_0000 | r << 16 | g << 8 | b;
    }

    /** Lit {@code count} palettes de 16 couleurs non compressées. */
    public static int[] readPalettes(Rom rom, int offset, int count) {
        int[] out = new int[16 * count];
        for (int i = 0; i < out.length; i++) {
            out[i] = bgr555ToArgb(rom.u16(offset + i * 2));
        }
        return out;
    }

    /** Palette(s) depuis un tableau d'octets (ex. après décompression LZ77). */
    public static int[] palettesFromBytes(byte[] data) {
        int[] out = new int[data.length / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = bgr555ToArgb((data[i * 2] & 0xFF) | (data[i * 2 + 1] & 0xFF) << 8);
        }
        return out;
    }

    /** Index de couleur (0-15) du pixel (x, y) d'une tuile 4bpp. */
    public static int pixel4bpp(byte[] tiles, int tileIndex, int x, int y) {
        int offset = tileIndex * TILE_BYTES_4BPP + y * 4 + (x >> 1);
        if (offset < 0 || offset >= tiles.length) return 0;
        int b = tiles[offset] & 0xFF;
        return (x & 1) == 0 ? b & 0x0F : b >> 4;
    }

    /**
     * Dessine une tuile 8x8 dans un tampon ARGB.
     * L'index 0 est transparent (on ne touche pas au pixel de destination).
     */
    public static void drawTile(int[] dest, int destWidth, int dx, int dy,
                                byte[] tiles, int tileIndex, int[] palettes, int paletteIndex,
                                boolean hflip, boolean vflip) {
        int destHeight = dest.length / destWidth;
        for (int y = 0; y < 8; y++) {
            int py = dy + y;
            if (py < 0 || py >= destHeight) continue;
            int sy = vflip ? 7 - y : y;
            for (int x = 0; x < 8; x++) {
                int px = dx + x;
                if (px < 0 || px >= destWidth) continue;
                int sx = hflip ? 7 - x : x;
                int ci = pixel4bpp(tiles, tileIndex, sx, sy);
                if (ci == 0) continue;
                int pi = paletteIndex * 16 + ci;
                if (pi < palettes.length) dest[py * destWidth + px] = palettes[pi];
            }
        }
    }

    /** Assemble un sprite rangé tuile par tuile (ordre GBA « 1D ») en image ARGB, couleur 0 transparente. */
    public static BufferedImage sprite(byte[] tiles, int widthTiles, int heightTiles, int[] palette) {
        int w = widthTiles * 8, h = heightTiles * 8;
        int[] px = new int[w * h];
        for (int ty = 0; ty < heightTiles; ty++) {
            for (int tx = 0; tx < widthTiles; tx++) {
                drawTile(px, w, tx * 8, ty * 8, tiles, ty * widthTiles + tx, palette, 0, false, false);
            }
        }
        return toImage(px, w, h);
    }

    public static BufferedImage toImage(int[] argb, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, argb, 0, w);
        return img;
    }
}
