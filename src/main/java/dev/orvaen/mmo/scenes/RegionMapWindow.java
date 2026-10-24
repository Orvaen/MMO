package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.data.PokemonTexts;
import dev.orvaen.mmo.data.RegionMap;
import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.HiUi;
import dev.orvaen.mmo.engine.HiWindow;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.UiWindow;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.IntSupplier;

/**
 * La Carte de la Région en fenêtre (touche M), comme dans PokeMMO : le dessin de la carte du jeu (lu dans la
 * ROM) agrandi, des onglets Kanto / Îles Sevii, le nom du lieu sous la souris (ses cases s'éclairent),
 * et la position du joueur qui pulse.
 */
public final class RegionMapWindow implements UiWindow {

    public static final String ID = "carte-region";

    private final Game game;
    private final HiWindow win;
    private final RegionMap map;
    private final IntSupplier currentSection;
    private int tab;
    private int frame;

    public RegionMapWindow(Game game, IntSupplier currentSection) {
        this.game = game;
        this.win = new HiWindow(game, "carte-region", 640, 500, 380, 320);
        this.map = game.data.regionMap();
        this.currentSection = currentSection;
        int m = map.mapOf(currentSection.getAsInt());
        this.tab = Math.max(0, m);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public HiWindow frame() {
        return win;
    }

    public int tab() {
        return tab;
    }

    /** Point écran d'un élément (tests) : « close », « tab0 »…, « cell:x:y » (case de la grille). */
    public Point anchor(String id) {
        Rectangle2D r;
        if (id.startsWith("tab")) {
            r = tabRect(id.charAt(3) - '0');
        } else if (id.equals("switch")) {
            r = switchButton();
        } else if (id.startsWith("cell:")) {
            String[] p = id.split(":");
            r = cellRect(Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        } else {
            r = win.closeButton();
        }
        return new Point((int) r.getCenterX(), (int) r.getCenterY());
    }

    // ------------------------------------------------------------------ disposition

    private Rectangle2D tabRect(int i) {
        Rectangle2D h = win.header();
        double s = win.scale(), w = 82 * s, gap = 4 * s;
        return new Rectangle2D.Double(h.getX() + 10 * s + i * (w + gap), h.getY() + 7 * s, w, h.getHeight() - 14 * s);
    }

    /** Agrandissement de la carte : elle remplit la fenêtre (proportions gardées). */
    private double zoom() {
        Rectangle2D c = win.content();
        double s = win.scale();
        return Math.max(1, Math.min((c.getWidth() - 24 * s) / RegionMap.VIEW_W, (c.getHeight() - 20 * s) / RegionMap.VIEW_H));
    }

    /** Carte agrandie ×4 en pixels nets, puis réduite en douceur à la taille de la fenêtre : nette à toutes les tailles. */
    private final java.util.Map<Integer, BufferedImage> big = new java.util.HashMap<>();

    private BufferedImage big(int m) {
        BufferedImage src = map.image(m);
        if (src == null) return null;
        return big.computeIfAbsent(m, k -> {
            int f = 4;
            BufferedImage out = new BufferedImage(RegionMap.VIEW_W * f, RegionMap.VIEW_H * f, BufferedImage.TYPE_INT_RGB);
            Graphics2D b = out.createGraphics();
            b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            b.drawImage(src, 0, 0, out.getWidth(), out.getHeight(), RegionMap.VIEW_X, RegionMap.VIEW_Y,
                    RegionMap.VIEW_X + RegionMap.VIEW_W, RegionMap.VIEW_Y + RegionMap.VIEW_H, null);
            b.dispose();
            return out;
        });
    }

    /** Où la carte est dessinée dans la fenêtre. */
    private Rectangle2D mapRect() {
        Rectangle2D c = win.content();
        double k = zoom();
        double w = Math.round(RegionMap.VIEW_W * k), h = Math.round(RegionMap.VIEW_H * k);
        return new Rectangle2D.Double(Math.round(c.getCenterX() - w / 2), Math.round(c.getY() + (c.getHeight() - h) / 2), w, h);
    }

    /** Le bouton « changer de carte » dessiné par le jeu dans le coin en bas à droite. */
    private Rectangle2D switchButton() {
        Rectangle2D m = mapRect();
        double k = m.getWidth() / RegionMap.VIEW_W;
        return new Rectangle2D.Double(m.getX() + (196 - RegionMap.VIEW_X) * k, m.getY() + (130 - RegionMap.VIEW_Y) * k, 20 * k, 20 * k);
    }

    /** Rectangle d'une case de la grille des lieux. */
    private Rectangle2D cellRect(int x, int y) {
        Rectangle2D m = mapRect();
        double k = m.getWidth() / RegionMap.VIEW_W;
        return new Rectangle2D.Double(m.getX() + (RegionMap.GRID_X - RegionMap.VIEW_X + x * 8) * k,
                m.getY() + (RegionMap.GRID_Y - RegionMap.VIEW_Y + y * 8) * k, 8 * k, 8 * k);
    }

    /** Case sous la souris, ou null. */
    private int[] cellAt(int mx, int my) {
        Rectangle2D m = mapRect();
        if (!m.contains(mx, my)) return null;
        double k = m.getWidth() / RegionMap.VIEW_W;
        int x = (int) Math.floor(((mx - m.getX()) / k - (RegionMap.GRID_X - RegionMap.VIEW_X)) / 8);
        int y = (int) Math.floor(((my - m.getY()) / k - (RegionMap.GRID_Y - RegionMap.VIEW_Y)) / 8);
        return x < 0 || y < 0 || x >= RegionMap.GRID_W || y >= RegionMap.GRID_H ? null : new int[]{x, y};
    }

    /** Lieu sous la souris (grotte ou forêt d'abord, sinon la ville / route), ou -1. */
    public int hoveredSection(int mx, int my) {
        int[] c = cellAt(mx, my);
        if (c == null) return -1;
        int d = map.dungeonAt(tab, c[0], c[1]);
        return d >= 0 ? d : map.sectionAt(tab, c[0], c[1]);
    }

    // ------------------------------------------------------------------ logique

    @Override
    public boolean update(Input in, boolean focused) {
        frame++;
        if (!focused) return true;
        int mx = in.mouseX(), my = in.mouseY();
        if (in.mousePressed()) {
            for (int i = 0; i < RegionMap.MAPS; i++) {
                if (tabRect(i).contains(mx, my)) {
                    tab = i;
                    return true;
                }
            }
        }
        if (win.update(in)) return true;
        if (in.mousePressed() && switchButton().contains(mx, my)) tab = (tab + 1) % RegionMap.MAPS;
        return !(in.mousePressed() && win.closeButton().contains(mx, my));
    }

    // ------------------------------------------------------------------ dessin

    @Override
    public void render(Graphics2D g, int w, int h, boolean focused) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        win.paintFrame(g, "Carte", true);
        double s = win.scale();
        int mx = game.input().rawMouseX(), my = game.input().rawMouseY();

        // onglets : Kanto, puis les îles
        for (int i = 0; i < RegionMap.MAPS; i++) {
            Rectangle2D r = tabRect(i);
            boolean on = i == tab, over = r.contains(mx, my);
            if (on || over) HiUi.fill(g, on ? HiUi.SELECTOR_FOCUS : HiUi.SELECTOR_HOVER, r.getX(), r.getY(), r.getWidth(), r.getHeight(), 6 * s);
            g.setFont(HiUi.font((float) (12 * s), on));
            g.setColor(on ? Color.WHITE : over ? HiUi.TEXT : HiUi.MUTED);
            HiUi.centered(g, RegionMap.NAMES[i], r.getX(), r.getY(), r.getWidth(), r.getHeight());
        }

        BufferedImage img = big(tab);
        Rectangle2D m = mapRect();
        if (img == null) {
            g.setFont(HiUi.font((float) (13 * s), false));
            g.setColor(HiUi.MUTED);
            HiUi.centered(g, "La carte de la région n'a pas été trouvée dans cette ROM.", m.getX(), m.getY() - 12 * s, m.getWidth(), m.getHeight());
            g.setFont(HiUi.font((float) (11 * s), false));
            HiUi.centered(g, map.diagnosis(), m.getX(), m.getY() + 14 * s, m.getWidth(), m.getHeight());
            return;
        }
        double k = m.getWidth() / RegionMap.VIEW_W;
        Graphics2D q = (Graphics2D) g.create();
        q.clip(new RoundRectangle2D.Double(m.getX(), m.getY(), m.getWidth(), m.getHeight(), 8 * s, 8 * s));
        q.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        q.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        q.drawImage(img, (int) m.getX(), (int) m.getY(), (int) m.getWidth(), (int) m.getHeight(), null);

        // lieu survolé : toutes ses cases s'éclairent
        int hover = hoveredSection(mx, my);
        if (switchButton().contains(mx, my)) {                       // bouton du jeu : on peut cliquer dessus
            Rectangle2D b = switchButton();
            q.setColor(new Color(255, 255, 255, 70));
            q.fill(new RoundRectangle2D.Double(b.getX(), b.getY(), b.getWidth(), b.getHeight(), 4 * s, 4 * s));
        }
        if (hover >= 0) {
            q.setColor(new Color(255, 255, 255, 90));
            for (int y = 0; y < RegionMap.GRID_H; y++) {
                for (int x = 0; x < RegionMap.GRID_W; x++) {
                    if (map.sectionAt(tab, x, y) == hover || map.dungeonAt(tab, x, y) == hover) q.fill(cellRect(x, y));
                }
            }
        }

        // le joueur : un point rouge qui pulse sur son lieu
        int here = currentSection.getAsInt();
        int[] spot = spot(here);
        if (spot != null) {
            Rectangle2D c = cellRect(spot[0], spot[1]);
            double cx = c.getCenterX(), cy = c.getCenterY();
            double pulse = (frame % 60) / 60.0, r = 4.5 * k;
            q.setColor(new Color(235, 87, 87, (int) (150 * (1 - pulse))));
            q.setStroke(new BasicStroke((float) (1.5 * k)));
            double pr = r + pulse * 5 * k;
            q.draw(new Ellipse2D.Double(cx - pr, cy - pr, 2 * pr, 2 * pr));
            q.setColor(Color.WHITE);
            q.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
            q.setColor(new Color(0xE84855));
            q.fill(new Ellipse2D.Double(cx - r + 1.5 * k, cy - r + 1.5 * k, 2 * r - 3 * k, 2 * r - 3 * k));
        }
        q.dispose();
        HiUi.stroke(g, HiUi.PANEL_BORDER, m.getX(), m.getY(), m.getWidth(), m.getHeight(), 8 * s, 1f);

        // nom du lieu, en haut à gauche de la carte : celui survolé (ville ou route, et la grotte / forêt qui
        // s'y trouve en dessous), sinon celui où l'on est
        String title = null, sub = null;
        int[] cell = cellAt(mx, my);
        if (cell != null && hover >= 0) {
            int town = map.sectionAt(tab, cell[0], cell[1]), dungeon = map.dungeonAt(tab, cell[0], cell[1]);
            title = name(town >= 0 ? town : dungeon);
            if (town >= 0 && dungeon >= 0 && dungeon != town) sub = name(dungeon);
        } else if (spot != null) {
            title = name(here);
        }
        if (title != null) {
            g.setFont(HiUi.font((float) (13.5 * s), true));
            double tw = g.getFontMetrics().stringWidth(title);
            g.setFont(HiUi.font((float) (11.5 * s), false));
            if (sub != null) tw = Math.max(tw, g.getFontMetrics().stringWidth(sub));
            boolean here2 = hover < 0;
            double pad = here2 ? 22 * s : 12 * s;
            double bw = tw + pad + 12 * s, bh = sub == null ? 26 * s : 42 * s;
            double tx = m.getX() + 10 * s, ty = m.getY() + 10 * s;
            HiUi.fill(g, new Color(16, 17, 25, 215), tx, ty, bw, bh, 7 * s);
            if (here2) {                                            // « vous êtes ici »
                g.setColor(new Color(0xE84855));
                g.fill(new Ellipse2D.Double(tx + 9 * s, ty + 13 * s - 3 * s, 6 * s, 6 * s));
            }
            g.setFont(HiUi.font((float) (13.5 * s), true));
            g.setColor(Color.WHITE);
            g.drawString(title, (float) (tx + pad), (float) (ty + 18 * s));
            if (sub != null) {
                g.setFont(HiUi.font((float) (11.5 * s), false));
                g.setColor(new Color(0xB8BAD0));
                g.drawString(sub, (float) (tx + pad), (float) (ty + 34 * s));
            }
        }
    }

    private String name(int section) {
        String n = section < 0 ? null : game.data.maps.sectionName(section);
        return n == null ? null : PokemonTexts.pretty(n);
    }

    /** Case où dessiner le joueur : celle de son lieu la plus proche du centre du lieu (sur cet onglet). */
    private int[] spot(int section) {
        if (section < 0) return null;
        double sx = 0, sy = 0;
        int n = 0;
        for (int y = 0; y < RegionMap.GRID_H; y++) {
            for (int x = 0; x < RegionMap.GRID_W; x++) {
                if (map.sectionAt(tab, x, y) == section || map.dungeonAt(tab, x, y) == section) {
                    sx += x;
                    sy += y;
                    n++;
                }
            }
        }
        if (n == 0) return null;
        double cx = sx / n, cy = sy / n, best = Double.MAX_VALUE;
        int[] out = null;
        for (int y = 0; y < RegionMap.GRID_H; y++) {
            for (int x = 0; x < RegionMap.GRID_W; x++) {
                if (map.sectionAt(tab, x, y) != section && map.dungeonAt(tab, x, y) != section) continue;
                double d = (x - cx) * (x - cx) + (y - cy) * (y - cy);
                if (d < best) {
                    best = d;
                    out = new int[]{x, y};
                }
            }
        }
        return out;
    }
}
