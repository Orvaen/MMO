package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.audio.AudioEngine;
import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.HiUi;
import dev.orvaen.mmo.engine.HiWindow;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.Storage;
import dev.orvaen.mmo.engine.UiIcons;
import dev.orvaen.mmo.engine.UiIcons.Icon;
import dev.orvaen.mmo.engine.UiWindow;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Fenêtre « Réglages » (touche O, ou menu Start) : volume général, musique, effets sonores, son coupé,
 * et zoom du monde. Tout est mémorisé ; les touches F7 / F8 / F9 et + / − restent en raccourcis.
 * <p>
 * Clavier : ↑ ↓ choisissent la ligne, ← → changent la valeur (5 % par appui), A coupe / remet le son,
 * B ou Échap ferme.
 */
public final class OptionsWindow implements UiWindow {

    public static final String ID = "reglages";
    /** Curseurs : volume général, musique, effets sonores. */
    public static final int MASTER = 0, MUSIC = 1, EFFECTS = 2;
    private static final String[] SLIDERS = {"Volume général", "Musique", "Effets sonores"};
    private static final Icon[] SLIDER_ICONS = {Icon.SPEAKER, Icon.NOTE, Icon.BOLT};
    /** Choix du zoom : 0 = automatique, puis ×2 à ×6. */
    private static final int[] ZOOMS = {0, 2, 3, 4, 5, 6};

    private final Game game;
    private final HiWindow win;
    private int dragging = -1;
    /** Ligne choisie au clavier : 0-2 curseurs, 3 son coupé, 4 zoom. */
    private int row;
    private static final int ROW_MUTE = 3, ROW_ZOOM = 4, ROWS = 5;

    public OptionsWindow(Game game) {
        this.game = game;
        this.win = new HiWindow(game, "reglages", 440, 350, 380, 330);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public HiWindow frame() {
        return win;
    }

    /** Point écran d'un élément (tests) : « close », « mute », « slider0 » à « slider2 » (au milieu), « zoom0 »… */
    public Point anchor(String id) {
        Rectangle2D r;
        if (id.startsWith("slider")) r = track(id.charAt(6) - '0');
        else if (id.startsWith("zoom")) r = zoomRect(id.charAt(4) - '0');
        else if (id.equals("mute")) r = toggle();
        else r = win.closeButton();
        return new Point((int) r.getCenterX(), (int) r.getCenterY());
    }

    // ------------------------------------------------------------------ disposition

    private double s() {
        return win.scale();
    }

    private Rectangle2D inner() {
        Rectangle2D c = win.content();
        double s = s();
        return new Rectangle2D.Double(c.getX() + 18 * s, c.getY() + 14 * s, c.getWidth() - 36 * s, c.getHeight() - 28 * s);
    }

    /** Ligne n° i (0-2 : curseurs, 3 : son coupé) de la partie « Son ». */
    private double rowY(int i) {
        return inner().getY() + 26 * s() + i * 40 * s();
    }

    private Rectangle2D track(int i) {
        Rectangle2D in = inner();
        double s = s(), x = in.getX() + 150 * s, w = in.getMaxX() - 48 * s - x;
        return new Rectangle2D.Double(x, rowY(i) + 10 * s, w, 6 * s);
    }

    private Rectangle2D toggle() {
        Rectangle2D in = inner();
        double s = s();
        return new Rectangle2D.Double(in.getMaxX() - 42 * s, rowY(3) + 3 * s, 42 * s, 22 * s);
    }

    private double zoomTop() {
        return rowY(3) + 62 * s();
    }

    private Rectangle2D zoomRect(int i) {
        Rectangle2D in = inner();
        double s = s(), gap = 4 * s, w = (in.getWidth() - 150 * s - gap * (ZOOMS.length - 1)) / ZOOMS.length;
        return new Rectangle2D.Double(in.getX() + 150 * s + i * (w + gap), zoomTop() + 24 * s, w, 26 * s);
    }

    // ------------------------------------------------------------------ valeurs

    private AudioEngine audio() {
        return game.data.audio;
    }

    /** Valeur du curseur, de 0 à 100. */
    public int value(int slider) {
        double v = switch (slider) {
            case MUSIC -> audio().musicVolume();
            case EFFECTS -> audio().effectsVolume();
            default -> audio().masterVolume();
        };
        return (int) Math.round(v * 100);
    }

    private void set(int slider, int percent) {
        int p = Math.max(0, Math.min(100, percent));
        switch (slider) {
            case MUSIC -> audio().setMusicVolume(p / 100.0);
            case EFFECTS -> audio().setEffectsVolume(p / 100.0);
            default -> {
                audio().setMasterVolume(p / 100.0);
                if (p > 0 && audio().muted()) setMuted(false);     // monter le son le remet
            }
        }
    }

    private void save(int slider) {
        switch (slider) {
            case MUSIC -> Storage.setMusicVolume(value(MUSIC));
            case EFFECTS -> Storage.setEffectsVolume(value(EFFECTS));
            default -> Storage.setVolume(value(MASTER));
        }
    }

    private void setMuted(boolean m) {
        audio().setMuted(m);
        Storage.setMuted(m);
    }

    // ------------------------------------------------------------------ logique

    @Override
    public Input.Button[] usesKeys() {
        return new Input.Button[]{Input.Button.LEFT, Input.Button.RIGHT, Input.Button.UP, Input.Button.DOWN,
                Input.Button.A, Input.Button.B};
    }

    public int row() {
        return row;
    }

    /** Clavier : ↑ ↓ ligne, ← → valeur, A interrupteur. Renvoie faux pour fermer. */
    private boolean keys(Input in) {
        if (in.pressed(Input.Button.B)) return false;
        if (in.repeat(Input.Button.DOWN)) row = (row + 1) % ROWS;
        if (in.repeat(Input.Button.UP)) row = (row + ROWS - 1) % ROWS;
        int dir = in.repeat(Input.Button.RIGHT) ? 1 : in.repeat(Input.Button.LEFT) ? -1 : 0;
        if (row < ROW_MUTE && dir != 0) {
            set(row, value(row) + dir * 5);
            save(row);
        } else if (row == ROW_MUTE && (dir != 0 || in.pressed(Input.Button.A))) {
            setMuted(dir > 0 || (dir == 0 && !audio().muted()));
        } else if (row == ROW_ZOOM && dir != 0) {
            int cur = 0;
            for (int i = 0; i < ZOOMS.length; i++) if (ZOOMS[i] == game.zoomSetting()) cur = i;
            game.setZoomSetting(ZOOMS[Math.max(0, Math.min(ZOOMS.length - 1, cur + dir))]);
        }
        return true;
    }

    @Override
    public boolean update(Input in, boolean focused) {
        int mx = in.mouseX(), my = in.mouseY();
        if (dragging >= 0) {
            Rectangle2D t = track(dragging);
            set(dragging, (int) Math.round((mx - t.getX()) / t.getWidth() * 100));
            if (!in.mouseDown()) {
                save(dragging);
                // petit son pour entendre le nouveau volume des effets
                if (dragging != MUSIC) audio().playSe(game.data.songs.select());
                dragging = -1;
            }
            return true;
        }
        if (!focused) return true;
        if (!keys(in)) return false;
        if (win.update(in)) return true;
        if (in.mousePressed()) {
            if (win.closeButton().contains(mx, my)) return false;
            for (int i = 0; i < SLIDERS.length; i++) {
                Rectangle2D t = track(i);
                // zone de clic plus haute que la piste : facile à attraper
                if (new Rectangle2D.Double(t.getX() - 8 * s(), t.getY() - 12 * s(), t.getWidth() + 16 * s(), t.getHeight() + 24 * s()).contains(mx, my)) {
                    dragging = i;
                    set(i, (int) Math.round((mx - t.getX()) / t.getWidth() * 100));
                    return true;
                }
            }
            if (toggle().contains(mx, my)) setMuted(!audio().muted());
            for (int i = 0; i < ZOOMS.length; i++) if (zoomRect(i).contains(mx, my)) game.setZoomSetting(ZOOMS[i]);
        }
        // molette sur un curseur : ±5 %
        if (in.wheel() != 0) {
            for (int i = 0; i < SLIDERS.length; i++) {
                Rectangle2D t = track(i);
                if (my >= t.getY() - 14 * s() && my <= t.getMaxY() + 14 * s() && mx >= inner().getX() && mx <= inner().getMaxX()) {
                    set(i, value(i) - Integer.signum(in.wheel()) * 5);
                    save(i);
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ dessin

    @Override
    public void render(Graphics2D g, int w, int h, boolean focused) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        win.paintFrame(g, "Réglages", true);
        double s = s();
        Rectangle2D hd = win.header();
        UiIcons.draw(g, Icon.GEAR, hd.getX() + 14 * s, hd.getCenterY() - 8 * s, 16 * s, HiUi.ACCENT);
        Rectangle2D in = inner();
        int mx = game.input().rawMouseX(), my = game.input().rawMouseY();
        boolean muted = audio().muted();

        // ligne choisie au clavier : un léger fond
        double selY = row == ROW_ZOOM ? zoomRect(0).getY() - 3 * s : rowY(row) - 4 * s;
        double selH = row == ROW_ZOOM ? zoomRect(0).getHeight() + 6 * s : 34 * s;
        HiUi.fill(g, new Color(0x2E3150), in.getX() - 8 * s, selY, in.getWidth() + 16 * s, selH, 8 * s);
        section(g, Icon.SPEAKER, "SON", in.getX(), in.getY() + 8 * s, s);
        for (int i = 0; i < SLIDERS.length; i++) {
            double y = rowY(i);
            boolean off = muted || (i != MASTER && value(MASTER) == 0);
            Icon icon = i == MASTER && (muted || value(MASTER) == 0) ? Icon.MUTE : SLIDER_ICONS[i];
            UiIcons.draw(g, icon, in.getX(), y + 5 * s, 15 * s, off ? HiUi.MUTED : HiUi.TEXT);
            g.setFont(HiUi.font((float) (12.5 * s), i == MASTER));
            g.setColor(off ? HiUi.MUTED : HiUi.TEXT);
            g.drawString(SLIDERS[i], (float) (in.getX() + 24 * s), (float) (y + 17 * s));
            Rectangle2D t = track(i);
            boolean hover = dragging == i || new Rectangle2D.Double(t.getX() - 8 * s, t.getY() - 12 * s, t.getWidth() + 16 * s, t.getHeight() + 24 * s).contains(mx, my);
            slider(g, t, value(i) / 100.0, hover, dragging == i, off, s);
            g.setFont(HiUi.font((float) (12.5 * s), true));
            g.setColor(off ? HiUi.MUTED : HiUi.TEXT);
            String pct = value(i) + " %";
            g.drawString(pct, (float) (in.getMaxX() - g.getFontMetrics().stringWidth(pct)), (float) (y + 17 * s));
        }
        // son coupé : interrupteur
        double y3 = rowY(3);
        UiIcons.draw(g, Icon.MUTE, in.getX(), y3 + 7 * s, 15 * s, muted ? UP : HiUi.TEXT);
        g.setFont(HiUi.font((float) (12.5 * s), false));
        g.setColor(HiUi.TEXT);
        g.drawString("Couper le son", (float) (in.getX() + 24 * s), (float) (y3 + 19 * s));
        g.setFont(HiUi.font((float) (10.5 * s), false));
        g.setColor(HiUi.MUTED);
        g.drawString("F9", (float) (in.getX() + 120 * s), (float) (y3 + 19 * s));
        switchControl(g, toggle(), muted, toggle().contains(mx, my), s);

        // affichage : zoom
        double zt = zoomTop();
        g.setColor(HiUi.PANEL_BORDER);
        g.fill(new Rectangle2D.Double(in.getX(), zt - 14 * s, in.getWidth(), 1));
        section(g, Icon.SCREEN, "AFFICHAGE", in.getX(), zt + 4 * s, s);
        g.setFont(HiUi.font((float) (12.5 * s), false));
        g.setColor(HiUi.TEXT);
        g.drawString("Zoom du monde", (float) (in.getX() + 24 * s), (float) (zt + 42 * s));
        int chosen = game.zoomSetting();
        for (int i = 0; i < ZOOMS.length; i++) {
            Rectangle2D r = zoomRect(i);
            boolean on = ZOOMS[i] == chosen, hover = r.contains(mx, my);
            HiUi.fill(g, on ? HiUi.SELECTOR_FOCUS : hover ? HiUi.SELECTOR_HOVER : HiUi.CELL, r.getX(), r.getY(), r.getWidth(), r.getHeight(), 6 * s);
            g.setFont(HiUi.font((float) (11.5 * s), on));
            g.setColor(on ? Color.WHITE : HiUi.TEXT);
            HiUi.centered(g, ZOOMS[i] == 0 ? "Auto" : "×" + ZOOMS[i], r.getX(), r.getY(), r.getWidth(), r.getHeight());
        }

        // aide
        g.setFont(HiUi.font((float) (10.5 * s), false));
        g.setColor(HiUi.MUTED);
        g.drawString("Raccourcis : F7 / F8 volume · F9 couper · + / − zoom", (float) in.getX(), (float) (in.getMaxY() - 2 * s));
    }

    private static final Color UP = new Color(0xF2788F);

    private static void section(Graphics2D g, Icon icon, String title, double x, double y, double s) {
        UiIcons.draw(g, icon, x, y - 10 * s, 12 * s, HiUi.ACCENT);
        g.setFont(HiUi.tracked((float) (10.5 * s), true, 0.12f));
        g.setColor(HiUi.ACCENT);
        g.drawString(title, (float) (x + 18 * s), (float) y);
    }

    /** Curseur : piste arrondie, partie remplie en dégradé, bouton rond (plus gros au survol). */
    private static void slider(Graphics2D g, Rectangle2D t, double v, boolean hover, boolean drag, boolean off, double s) {
        double h = t.getHeight();
        HiUi.fill(g, HiUi.CELL, t.getX(), t.getY(), t.getWidth(), h, h);
        double fill = t.getWidth() * v;
        if (fill > 0) {
            Color a = off ? new Color(0x4A4E6E) : new Color(0x5FC8E8), b = off ? new Color(0x5A5E7E) : HiUi.ACCENT;
            g.setPaint(new GradientPaint((float) t.getX(), 0, a, (float) t.getMaxX(), 0, b));
            g.fill(new RoundRectangle2D.Double(t.getX(), t.getY(), Math.max(h, fill), h, h, h));
        }
        double r = (drag ? 9 : hover ? 8 : 7) * s, cx = t.getX() + fill, cy = t.getCenterY();
        if (hover || drag) {
            g.setColor(new Color(140, 147, 230, 60));
            g.fill(new Ellipse2D.Double(cx - r - 4 * s, cy - r - 4 * s, 2 * r + 8 * s, 2 * r + 8 * s));
        }
        g.setColor(off ? new Color(0xB4B6CC) : Color.WHITE);
        g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
    }

    /** Interrupteur on / off. */
    private static void switchControl(Graphics2D g, Rectangle2D r, boolean on, boolean hover, double s) {
        Color bg = on ? new Color(0xC9506A) : hover ? HiUi.SELECTOR_HOVER : HiUi.SELECTOR;
        HiUi.fill(g, bg, r.getX(), r.getY(), r.getWidth(), r.getHeight(), r.getHeight());
        double d = r.getHeight() - 6 * s, x = on ? r.getMaxX() - 3 * s - d : r.getX() + 3 * s;
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(x, r.getY() + 3 * s, d, d));
    }
}
