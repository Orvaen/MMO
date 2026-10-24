package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.HiUi;
import dev.orvaen.mmo.engine.UiIcons;
import dev.orvaen.mmo.engine.UiIcons.Icon;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Barre de raccourcis en bas à droite de l'écran (comme PokeMMO) : une icône par menu, avec sa touche
 * dans le coin. Un clic fait la même chose que la touche.
 */
final class ShortcutBar {

    /** Un raccourci : icône, nom, touche, fenêtre qu'il ouvre (pour l'allumer quand elle est ouverte). */
    record Shortcut(Icon icon, String name, String key, String windowId) {}

    static final Shortcut[] SHORTCUTS = {
            new Shortcut(Icon.BALL, "Équipe", "E", SummaryWindow.ID),
            new Shortcut(Icon.DEX, "Pokédex", "P", "pokedex"),
            new Shortcut(Icon.MAP, "Carte", "M", RegionMapWindow.ID),
            new Shortcut(Icon.CARD, "Carte de dresseur", "C", "carte-dresseur"),
            new Shortcut(Icon.GEAR, "Réglages", "O", OptionsWindow.ID),
            new Shortcut(Icon.MENU, "Menu", "X", null),
    };

    private final Game game;

    ShortcutBar(Game game) {
        this.game = game;
    }

    private static double scale(int h) {
        return Math.max(0.8, Math.min(1.15, h / 900.0));
    }

    /** Bouton n° i dans une fenêtre de jeu w × h. */
    static Rectangle2D button(int i, int w, int h) {
        double s = scale(h), size = 40 * s, gap = 6 * s, margin = 10 * s;
        int n = SHORTCUTS.length;
        double x0 = w - margin - n * size - (n - 1) * gap;
        return new Rectangle2D.Double(x0 + i * (size + gap), h - margin - size, size, size);
    }

    int buttonAt(int mx, int my, int w, int h) {
        for (int i = 0; i < SHORTCUTS.length; i++) if (button(i, w, h).contains(mx, my)) return i;
        return -1;
    }

    void draw(Graphics2D g0, int w, int h) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double s = scale(h);
        int mx = game.input().rawMouseX(), my = game.input().rawMouseY();
        int hover = -1;
        for (int i = 0; i < SHORTCUTS.length; i++) {
            Shortcut sc = SHORTCUTS[i];
            Rectangle2D r = button(i, w, h);
            boolean over = r.contains(mx, my), open = sc.windowId() != null && game.windowOpen(sc.windowId());
            if (over) hover = i;
            HiUi.fill(g, open ? new Color(0x2E3150) : over ? HiUi.SELECTOR_HOVER : new Color(0x1C1D2B, false), r.getX(), r.getY(), r.getWidth(), r.getHeight(), 9 * s);
            HiUi.stroke(g, open ? HiUi.ACCENT : over ? HiUi.SELECTOR_FOCUS : HiUi.PANEL_BORDER, r.getX(), r.getY(), r.getWidth(), r.getHeight(), 9 * s, 1f);
            double is = 18 * s;
            Color ic = sc.icon() == Icon.BALL ? new Color(0xEB5757) : open ? HiUi.ACCENT : over ? Color.WHITE : HiUi.TEXT;
            UiIcons.draw(g, sc.icon(), r.getCenterX() - is / 2, r.getCenterY() - is / 2 - 2 * s, is, ic);
            // la touche, en petit dans le coin
            g.setFont(HiUi.font((float) (9 * s), true));
            double kw = g.getFontMetrics().stringWidth(sc.key()) + 6 * s, kh = 12 * s;
            double kx = r.getMaxX() - kw - 2 * s, ky = r.getMaxY() - kh - 2 * s;
            g.setColor(new Color(0x101119));
            g.fill(new RoundRectangle2D.Double(kx, ky, kw, kh, 4 * s, 4 * s));
            g.setColor(HiUi.MUTED);
            HiUi.centered(g, sc.key(), kx, ky, kw, kh);
        }
        if (hover >= 0) {
            Rectangle2D r = button(hover, w, h);
            Shortcut sc = SHORTCUTS[hover];
            g.setFont(HiUi.font((float) (11.5 * s), true));
            String text = sc.name() + "  (" + sc.key() + ")";
            double tw = g.getFontMetrics().stringWidth(text) + 16 * s;
            double cx = Math.min(w - 6 * s - tw / 2, r.getCenterX());
            UiIcons.tooltip(g, text, cx, r.getY() - 28 * s, s);
        }
        g.dispose();
    }
}
