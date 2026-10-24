package dev.orvaen.mmo.engine;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/**
 * Fenêtre de l'interface façon PokeMMO, par-dessus le jeu : on la déplace en tirant sa barre de titre,
 * on la redimensionne par ses bords ou le coin en bas à droite, et sa place est mémorisée
 * d'une partie à l'autre. Coordonnées en pixels de la fenêtre du jeu.
 */
public final class HiWindow {

    public static final double HEADER = 38;
    private static final double EDGE = 7;

    private enum Drag { NONE, MOVE, RIGHT, BOTTOM, CORNER, LEFT, BOTTOM_LEFT }

    private final Game game;
    private final String key;
    private final double defW, defH, minW, minH;   // en unités (multipliées par l'échelle)
    private double x, y, w, h;
    private boolean placed;
    private int cascade;

    /** Décale la première ouverture (sans place mémorisée) pour ne pas recouvrir les autres fenêtres. */
    public void cascade(int n) {
        cascade = n;
    }
    private Drag drag = Drag.NONE;
    private double grabX, grabY, startX, startY, startW, startH;
    private int frame;

    public HiWindow(Game game, String key, double defW, double defH, double minW, double minH) {
        this.game = game;
        this.key = key;
        this.defW = defW;
        this.defH = defH;
        this.minW = minW;
        this.minH = minH;
    }

    /** Échelle de l'interface (polices, marges) selon la hauteur de la fenêtre du jeu. */
    public double scale() {
        // Interface compacte comme PokeMMO : elle grossit peu avec la taille de la fenêtre
        return Math.max(0.8, Math.min(1.15, game.viewHeight() / 900.0));
    }

    private void place() {
        int vw = game.viewWidth(), vh = game.viewHeight();
        if (vw <= 0 || vh <= 0) return;
        if (!placed) {
            double s = scale();
            double[] saved = Storage.uiRect(key);
            if (saved != null) {
                x = saved[0];
                y = saved[1];
                w = saved[2];
                h = saved[3];
            } else {
                w = Math.min(vw - 40, defW * s);
                h = Math.min(vh - 40, defH * s);
                x = (vw - w) / 2 + cascade * 36 * s;
                y = (vh - h) / 2 + cascade * 30 * s;
            }
            placed = true;
        }
        clamp(vw, vh);
    }

    /** Toujours visible et pas plus petite que le minimum, même après un changement de taille du jeu. */
    private void clamp(int vw, int vh) {
        double s = scale();
        w = Math.max(Math.min(minW * s, vw - 8), Math.min(w, vw - 8));
        h = Math.max(Math.min(minH * s, vh - 8), Math.min(h, vh - 8));
        x = Math.max(4, Math.min(vw - w - 4, x));
        y = Math.max(4, Math.min(vh - h - 4, y));
        // pixels entiers : nets à l'écran, et identiques une fois relus depuis la configuration
        x = Math.rint(x);
        y = Math.rint(y);
        w = Math.rint(w);
        h = Math.rint(h);
    }

    public Rectangle2D bounds() {
        place();
        return new Rectangle2D.Double(x, y, w, h);
    }

    public Rectangle2D header() {
        place();
        return new Rectangle2D.Double(x, y, w, HEADER * scale());
    }

    public Rectangle2D closeButton() {
        place();
        double s = scale();
        return new Rectangle2D.Double(x + w - 34 * s, y + 8 * s, 24 * s, 22 * s);
    }

    /** Zone de contenu, sous la barre de titre. */
    public Rectangle2D content() {
        place();
        double s = scale();
        return new Rectangle2D.Double(x, y + HEADER * s, w, h - HEADER * s);
    }

    public boolean dragging() {
        return drag != Drag.NONE;
    }

    /** La souris est sur la fenêtre (ou sur un bord qu'on peut tirer). */
    public boolean grabs(double mx, double my) {
        place();
        double e = EDGE * scale();
        return mx >= x - e && mx <= x + w + e && my >= y - e && my <= y + h + e;
    }

    private Drag hit(double mx, double my) {
        double s = scale(), e = EDGE * s;
        boolean inX = mx >= x - e && mx <= x + w + e, inY = my >= y - e && my <= y + h + e;
        if (!inX || !inY) return Drag.NONE;
        boolean right = mx >= x + w - e, bottom = my >= y + h - e, left = mx <= x + e;
        if (right && bottom || mx >= x + w - 16 * s && my >= y + h - 16 * s && mx <= x + w && my <= y + h) {
            return Drag.CORNER;
        }
        if (left && bottom) return Drag.BOTTOM_LEFT;
        if (right) return Drag.RIGHT;
        if (left) return Drag.LEFT;
        if (bottom) return Drag.BOTTOM;
        if (header().contains(mx, my) && !closeButton().contains(mx, my)) return Drag.MOVE;
        return Drag.NONE;
    }

    /**
     * À appeler en premier dans {@code update} : gère le déplacement et le redimensionnement.
     * Renvoie vrai si la souris est prise par la fenêtre (le contenu ne doit pas réagir).
     */
    public boolean update(Input in) {
        place();
        frame++;
        int mx = in.mouseX(), my = in.mouseY();
        if (drag == Drag.NONE) {
            Drag over = hit(mx, my);
            game.setCursor(switch (over) {
                case MOVE -> Cursor.MOVE_CURSOR;
                case RIGHT -> Cursor.E_RESIZE_CURSOR;
                case LEFT -> Cursor.W_RESIZE_CURSOR;
                case BOTTOM -> Cursor.S_RESIZE_CURSOR;
                case CORNER -> Cursor.SE_RESIZE_CURSOR;
                case BOTTOM_LEFT -> Cursor.SW_RESIZE_CURSOR;
                default -> Cursor.DEFAULT_CURSOR;
            });
            if (in.mousePressed() && over != Drag.NONE) {
                drag = over;
                grabX = mx;
                grabY = my;
                startX = x;
                startY = y;
                startW = w;
                startH = h;
                return true;
            }
            return false;
        }
        if (!in.mouseDown()) {
            drag = Drag.NONE;
            game.setCursor(Cursor.DEFAULT_CURSOR);
            Storage.setUiRect(key, x, y, w, h);
            return true;
        }
        double dx = mx - grabX, dy = my - grabY, s = scale();
        switch (drag) {
            case MOVE -> {
                x = startX + dx;
                y = startY + dy;
            }
            case RIGHT -> w = Math.max(minW * s, startW + dx);
            case BOTTOM -> h = Math.max(minH * s, startH + dy);
            case CORNER -> {
                w = Math.max(minW * s, startW + dx);
                h = Math.max(minH * s, startH + dy);
            }
            case LEFT, BOTTOM_LEFT -> {
                double nw = Math.max(minW * s, startW - dx);
                x = startX + startW - nw;
                w = nw;
                if (drag == Drag.BOTTOM_LEFT) h = Math.max(minH * s, startH + dy);
            }
            default -> { }
        }
        clamp(game.viewWidth(), game.viewHeight());
        return true;
    }

    /** Remet le curseur normal (à la fermeture). */
    public void close() {
        game.setCursor(Cursor.DEFAULT_CURSOR);
        if (placed) Storage.setUiRect(key, x, y, w, h);
    }

    /** Panneau, barre de titre, bouton de fermeture et poignée de redimensionnement (sans ombre). */
    public void paintFrame(Graphics2D g, String title) {
        paintFrame(g, title, false);
    }

    /**
     * Idem ; avec {@code titleRight}, le titre est calé à droite (près du ×) pour laisser la gauche de la
     * barre aux onglets en icônes, comme dans PokeMMO.
     */
    public void paintFrame(Graphics2D g, String title, boolean titleRight) {
        place();
        double s = scale();
        HiUi.fill(g, HiUi.PANEL, x, y, w, h, 6 * s);
        HiUi.fill(g, dragging() && drag == Drag.MOVE ? new Color(0x23253A) : HiUi.HEADER, x, y, w, HEADER * s, 6 * s);
        g.setColor(dragging() && drag == Drag.MOVE ? new Color(0x23253A) : HiUi.HEADER);
        g.fill(new Rectangle2D.Double(x, y + (HEADER - 8) * s, w, 8 * s));
        HiUi.stroke(g, dragging() ? HiUi.ACCENT : HiUi.PANEL_BORDER, x, y, w, h, 6 * s, 1f);

        g.setFont(HiUi.font((float) (15 * s), true));
        g.setColor(Color.WHITE);
        Rectangle2D c = closeButton();
        double tx = titleRight ? c.getX() - 10 * s - g.getFontMetrics().stringWidth(title) : x + 14 * s;
        g.drawString(title, (float) tx, (float) (y + 25 * s));
        if (c.contains(game.input().mouseX(), game.input().mouseY())) {
            HiUi.fill(g, new Color(0x3A2333), c.getX(), c.getY(), c.getWidth(), c.getHeight(), 4 * s);
        }
        g.setColor(HiUi.CLOSE);
        g.setFont(HiUi.font((float) (17 * s), true));
        HiUi.centered(g, "×", c.getX(), c.getY() - s, c.getWidth(), c.getHeight());

        // poignée : trois petites diagonales dans le coin
        g.setColor(HiUi.MUTED);
        g.setStroke(new java.awt.BasicStroke((float) (1.2 * s)));
        for (int i = 1; i <= 3; i++) {
            double d = i * 4 * s;
            g.draw(new java.awt.geom.Line2D.Double(x + w - 4 * s - d, y + h - 4 * s, x + w - 4 * s, y + h - 4 * s - d));
        }
    }
}
