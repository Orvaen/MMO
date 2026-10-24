package dev.orvaen.mmo.player;

import dev.orvaen.mmo.data.PlayerLook;
import dev.orvaen.mmo.data.PlayerLook.Part;
import dev.orvaen.mmo.player.CosmeticMasks.Rule;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Recolore un sprite 4bpp pièce par pièce. Chaque nuance d'origine (clair, moyen, ombre)
 * garde son écart de luminosité, transposé sur la teinte choisie : les ombrages sont conservés.
 */
public final class Recolor {

    private Recolor() {}

    /** Sprite brut lu dans la ROM : tuiles 4bpp rangées ligne par ligne + palette ARGB. */
    public record Source(byte[] tiles, int widthTiles, int heightTiles, int[] palette) {
        public int width() { return widthTiles * 8; }
        public int height() { return heightTiles * 8; }

        /** Index de couleur (0-15) du pixel (x, y). */
        public int index(int x, int y) {
            int tile = (y >> 3) * widthTiles + (x >> 3);
            int o = tile * 32 + (y & 7) * 4 + ((x & 7) >> 1);
            if (o < 0 || o >= tiles.length) return 0;
            int b = tiles[o] & 0xFF;
            return (x & 1) == 0 ? b & 0x0F : b >> 4;
        }
    }

    /**
     * @param facing  orientation (règles des sprites de marche), {@link CosmeticMasks#ALL} sinon
     * @param yShift  décalage vertical des frames de marche par rapport aux règles
     */
    public static BufferedImage render(Source src, List<Rule> rules, PlayerLook look,
                                       int facing, int yShift, boolean hflip) {
        Map<Part, int[]> recolored = palettes(src.palette(), rules, look);
        int w = src.width(), h = src.height();
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int ci = src.index(x, y);
                if (ci == 0) continue;
                Part p = CosmeticMasks.partAt(rules, ci, x, y - yShift, facing);
                int[] pal = p == null ? null : recolored.get(p);
                int argb = pal != null ? pal[ci] : src.palette()[ci];
                out[y * w + (hflip ? w - 1 - x : x)] = argb;
            }
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, out, 0, w);
        return img;
    }

    /** Palette recolorée pour chaque pièce dont la couleur n'est pas « d'origine ». */
    static Map<Part, int[]> palettes(int[] base, List<Rule> rules, PlayerLook look) {
        Map<Part, int[]> out = new EnumMap<>(Part.class);
        for (Part p : Part.values()) {
            int target = look.rgb(p);
            if (target < 0) continue;
            int mask = CosmeticMasks.indexMask(rules, p) & ~1;
            if (mask == 0) continue;
            out.put(p, shade(base, mask, target));
        }
        return out;
    }

    /** Transpose les nuances des index de {@code mask} sur la couleur {@code targetRgb}. */
    static int[] shade(int[] base, int mask, int targetRgb) {
        double sum = 0;
        int n = 0;
        for (int i = 1; i < 16; i++) {
            if ((mask >> i & 1) != 0) {
                sum += hsl(base[i])[2];
                n++;
            }
        }
        double ref = n == 0 ? 0.5 : sum / n;
        double[] t = hsl(targetRgb);
        int[] pal = base.clone();
        for (int i = 1; i < 16; i++) {
            if ((mask >> i & 1) == 0) continue;
            double l = clamp(t[2] + (hsl(base[i])[2] - ref) * contrast(t[2]));
            pal[i] = 0xFF00_0000 | rgb(t[0], t[1], l);
        }
        return pal;
    }

    /** Réduit l'écart d'ombrage pour les couleurs très claires ou très sombres (évite le blanc/noir pur). */
    private static double contrast(double targetLight) {
        double edge = Math.min(targetLight, 1 - targetLight);
        return 0.55 + Math.min(0.45, edge);
    }

    private static double clamp(double v) {
        return Math.max(0.04, Math.min(0.97, v));
    }

    /** RGB → {teinte 0-1, saturation 0-1, luminosité 0-1}. */
    static double[] hsl(int rgb) {
        double r = (rgb >> 16 & 0xFF) / 255.0, g = (rgb >> 8 & 0xFF) / 255.0, b = (rgb & 0xFF) / 255.0;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        double l = (max + min) / 2, h = 0, s = 0;
        if (max != min) {
            double d = max - min;
            s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
            if (max == r) h = (g - b) / d + (g < b ? 6 : 0);
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h /= 6;
        }
        return new double[]{h, s, l};
    }

    static int rgb(double h, double s, double l) {
        double r, g, b;
        if (s == 0) {
            r = g = b = l;
        } else {
            double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
            double p = 2 * l - q;
            r = hue(p, q, h + 1.0 / 3);
            g = hue(p, q, h);
            b = hue(p, q, h - 1.0 / 3);
        }
        return (int) Math.round(r * 255) << 16 | (int) Math.round(g * 255) << 8 | (int) Math.round(b * 255);
    }

    private static double hue(double p, double q, double t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1.0 / 6) return p + (q - p) * 6 * t;
        if (t < 1.0 / 2) return q;
        if (t < 2.0 / 3) return p + (q - p) * (2.0 / 3 - t) * 6;
        return p;
    }
}
