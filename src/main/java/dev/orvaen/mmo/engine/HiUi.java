package dev.orvaen.mmo.engine;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.font.TextAttribute;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Map;

/**
 * Interface haute résolution : style plat et sobre. Une seule surface sombre par fenêtre, des fonds
 * seulement au survol et sur la sélection, une couleur d'accent (pervenche), texte lissé. Utilisée par
 * les écrans dessinés en {@link Scene#renderHiRes(Graphics2D, int, int)}.
 */
public final class HiUi {

    /** Fond des fenêtres (et de leur barre de titre : pas de bandeau séparé). */
    public static final Color PANEL = new Color(0x1D1E29);
    /** Bordure fine et traits de séparation. */
    public static final Color PANEL_BORDER = new Color(0x2E3042);
    public static final Color HEADER = PANEL;
    /** Zones légèrement éclairées (pistes, champs, cartes). */
    public static final Color CELL = new Color(0x252736);
    public static final Color SELECTOR = new Color(0x2A2C3D);
    public static final Color SELECTOR_HOVER = new Color(0x313449);
    public static final Color SELECTOR_FOCUS = new Color(0x464C85);
    public static final Color SELECTOR_BORDER = new Color(0x3A3D54);
    public static final Color TEXT = new Color(0xECEDF3);
    public static final Color MUTED = new Color(0x8A8CA3);
    public static final Color ACCENT = new Color(0x8C93E6);
    public static final Color CLOSE = new Color(0xF0507A);
    public static final Color ERROR = new Color(0xFF6B6B);

    private static final String FAMILY = pickFamily();

    private HiUi() {}

    private static String pickFamily() {
        try {
            String[] names = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            for (String wanted : new String[]{"Segoe UI", "Source Sans Pro", "Noto Sans", "DejaVu Sans"}) {
                if (Arrays.asList(names).contains(wanted)) return wanted;
            }
        } catch (RuntimeException ignored) {
            // environnement sans polices : police logique
        }
        return Font.SANS_SERIF;
    }

    public static Font font(float size, boolean bold) {
        return new Font(FAMILY, bold ? Font.BOLD : Font.PLAIN, 12).deriveFont(size);
    }

    /** Police avec espacement des lettres (titres en capitales). */
    public static Font tracked(float size, boolean bold, float tracking) {
        return font(size, bold).deriveFont(Map.of(TextAttribute.TRACKING, tracking));
    }

    public static void fill(Graphics2D g, Color c, double x, double y, double w, double h, double arc) {
        g.setColor(c);
        g.fill(new java.awt.geom.RoundRectangle2D.Double(x, y, w, h, arc, arc));
    }

    public static void stroke(Graphics2D g, Color c, double x, double y, double w, double h, double arc, float width) {
        g.setColor(c);
        g.setStroke(new BasicStroke(width));
        g.draw(new java.awt.geom.RoundRectangle2D.Double(x + width / 2, y + width / 2, w - width, h - width, arc, arc));
    }

    /** Texte centré (horizontalement et verticalement) dans un rectangle ; gère les retours à la ligne « \n ». */
    public static void centered(Graphics2D g, String text, double x, double y, double w, double h) {
        FontMetrics fm = g.getFontMetrics();
        String[] lines = text.split("\n");
        double lineH = fm.getAscent() + fm.getDescent();
        double top = y + (h - lineH * lines.length) / 2 + fm.getAscent();
        for (int i = 0; i < lines.length; i++) {
            g.drawString(lines[i], (float) (x + (w - fm.stringWidth(lines[i])) / 2), (float) (top + i * lineH));
        }
    }

    /** Texte coupé avec « … » s'il dépasse {@code maxWidth}. */
    public static String ellipsis(Graphics2D g, String text, double maxWidth) {
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(text) <= maxWidth) return text;
        String s = text;
        while (!s.isEmpty() && fm.stringWidth(s + "…") > maxWidth) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    /** Petites flèches haut/bas des sélecteurs (⇕). */
    public static void updown(Graphics2D g, Color c, double cx, double cy) {
        g.setColor(c);
        g.fill(new Polygon(new int[]{(int) cx - 3, (int) cx + 4, (int) cx}, new int[]{(int) cy - 2, (int) cy - 2, (int) cy - 6}, 3));
        g.fill(new Polygon(new int[]{(int) cx - 3, (int) cx + 4, (int) cx}, new int[]{(int) cy + 2, (int) cy + 2, (int) cy + 6}, 3));
    }

    /** Pastille arc-en-ciel de la roue de couleur. */
    public static void colorWheel(Graphics2D g, double cx, double cy, double r, boolean hover) {
        int n = 36;
        for (int i = 0; i < n; i++) {
            g.setColor(Color.getHSBColor(i / (float) n, 0.85f, 1f));
            g.fill(new java.awt.geom.Arc2D.Double(cx - r, cy - r, r * 2, r * 2, i * 360.0 / n, 360.0 / n + 1, java.awt.geom.Arc2D.PIE));
        }
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), (float) r, new float[]{0f, 1f},
                new Color[]{new Color(255, 255, 255, 230), new Color(255, 255, 255, 0)}));
        g.fill(new java.awt.geom.Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g.setColor(hover ? Color.WHITE : new Color(0x1C1D2A));
        g.setStroke(new BasicStroke(hover ? 1.6f : 1.2f));
        g.draw(new java.awt.geom.Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
    }

    /** Grosse flèche blanche « pixel » à contour sombre (rotation de l'aperçu). */
    public static void pixelArrow(Graphics2D g, double x, double y, double size, boolean right, boolean hover) {
        double u = size / 8;
        // flèche dessinée sur une grille 8x8
        int[][] cells = {
                {3, 0}, {3, 1}, {4, 1}, {0, 2}, {1, 2}, {2, 2}, {3, 2}, {4, 2}, {5, 2},
                {0, 3}, {1, 3}, {2, 3}, {3, 3}, {4, 3}, {5, 3}, {6, 3},
                {0, 4}, {1, 4}, {2, 4}, {3, 4}, {4, 4}, {5, 4}, {6, 4},
                {0, 5}, {1, 5}, {2, 5}, {3, 5}, {4, 5}, {5, 5}, {3, 6}, {4, 6}, {3, 7}
        };
        Color fill = hover ? Color.WHITE : new Color(0xF2F2F6);
        for (int pass = 0; pass < 2; pass++) {
            for (int[] c : cells) {
                double cx = right ? c[0] : 7 - c[0];
                double px = x + cx * u, py = y + c[1] * u;
                if (pass == 0) {
                    g.setColor(new Color(0, 0, 0, 160));
                    g.fill(new java.awt.geom.Rectangle2D.Double(px - u * 0.35, py - u * 0.35, u * 1.7, u * 1.7));
                } else {
                    g.setColor(fill);
                    g.fill(new java.awt.geom.Rectangle2D.Double(px, py, u + 0.5, u + 0.5));
                }
            }
        }
    }

    /**
     * Fond en damier vu en perspective (sol et plafond qui fuient vers le centre),
     * comme derrière la création de personnage de PokeMMO.
     */
    public static void checkerTunnel(Graphics2D g, int w, int h, double scroll) {
        g.setPaint(new GradientPaint(0, 0, new Color(0x0B0B10), 0, h, new Color(0x15151C)));
        g.fillRect(0, 0, w, h);
        double cx = w / 2.0, horizonTop = h * 0.40, horizonBottom = h * 0.60;
        double focal = h * 0.9;
        for (int side = 0; side < 2; side++) {
            for (int gz = 40; gz >= 1; gz--) {
                double z0 = gz - (scroll % 1), z1 = z0 + 1;
                if (z0 < 0.35) continue;
                for (int gx = -14; gx < 14; gx++) {
                    boolean dark = ((gx + gz) & 1) == 0;
                    double fogT = Math.min(1, z0 / 26.0);
                    int base = dark ? 0x2E : 0x62;
                    int v = (int) (base * (1 - fogT) + 0x16 * fogT);
                    g.setColor(new Color(v, v, v + 4));
                    double[] xs = new double[4], ys = new double[4];
                    double[][] pts = {{gx, z0}, {gx + 1, z0}, {gx + 1, z1}, {gx, z1}};
                    for (int i = 0; i < 4; i++) {
                        double z = pts[i][1];
                        xs[i] = cx + pts[i][0] * focal / z;
                        double dy = (h * 0.5) / z * 1.6;
                        ys[i] = side == 0 ? horizonBottom + dy : horizonTop - dy;
                    }
                    java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
                    p.moveTo(xs[0], ys[0]);
                    for (int i = 1; i < 4; i++) p.lineTo(xs[i], ys[i]);
                    p.closePath();
                    g.fill(p);
                }
            }
        }
        // Lueur sombre au centre et vignette
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, h / 2.0), (float) (Math.max(w, h) * 0.75),
                new float[]{0f, 0.35f, 1f},
                new Color[]{new Color(0, 0, 0, 200), new Color(0, 0, 0, 70), new Color(0, 0, 0, 150)}));
        g.fillRect(0, 0, w, h);
    }
}
