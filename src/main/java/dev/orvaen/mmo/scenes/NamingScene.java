package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.HiUi;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.Scene;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * Écran « donner un nom » : surnom d'un Pokémon reçu, nom du rival… Clavier (Entrée valide,
 * Échap garde le nom proposé) ou souris (boutons).
 */
public final class NamingScene implements Scene {

    public static final int MAX = 10;

    private final Game game;
    private final String title;
    private final String fallback;
    private final BufferedImage picture;
    private final Consumer<String> done;
    private String name = "";
    private int frame;
    private boolean finished;

    public NamingScene(Game game, String title, String fallback, BufferedImage picture, Consumer<String> done) {
        this.game = game;
        this.title = title;
        this.fallback = fallback;
        this.picture = picture;
        this.done = done;
    }

    @Override
    public boolean isOverlay() {
        return true;
    }

    private double scale() {
        return Math.max(0.8, Math.min(1.3, game.viewHeight() / 760.0));
    }

    private Rectangle2D panel() {
        double s = scale(), w = 440 * s, h = 300 * s;
        return new Rectangle2D.Double((game.viewWidth() - w) / 2, (game.viewHeight() - h) / 2, w, h);
    }

    private Rectangle2D field() {
        Rectangle2D p = panel();
        double s = scale();
        return new Rectangle2D.Double(p.getX() + 150 * s, p.getY() + 110 * s, p.getWidth() - 170 * s, 40 * s);
    }

    private Rectangle2D okButton() {
        Rectangle2D p = panel();
        double s = scale();
        return new Rectangle2D.Double(p.getMaxX() - 150 * s, p.getMaxY() - 56 * s, 130 * s, 38 * s);
    }

    private Rectangle2D keepButton() {
        Rectangle2D p = panel();
        double s = scale();
        return new Rectangle2D.Double(p.getMaxX() - 290 * s, p.getMaxY() - 56 * s, 130 * s, 38 * s);
    }

    /** Point écran d'un bouton (tests) : « ok », « keep ». */
    public Point anchor(String id) {
        Rectangle2D r = id.equals("ok") ? okButton() : keepButton();
        return new Point((int) r.getCenterX(), (int) r.getCenterY());
    }

    @Override
    public void update(Input in) {
        frame++;
        if (finished) return;
        for (char c : in.typed().toCharArray()) {
            if (c == '\n' || c == '\r') {
                finish(name.isBlank() ? fallback : name.trim());
                return;
            } else if (c == 27) {
                finish(fallback);
                return;
            } else if (c == '\b' || c == 127) {
                if (!name.isEmpty()) name = name.substring(0, name.length() - 1);
            } else if (!Character.isISOControl(c) && name.length() < MAX && (Character.isLetterOrDigit(c) || " -'.♂♀".indexOf(c) >= 0)) {
                if (c != ' ' || !name.isEmpty()) name += c;
            }
        }
        if (in.mousePressed()) {
            if (okButton().contains(in.mouseX(), in.mouseY())) finish(name.isBlank() ? fallback : name.trim());
            else if (keepButton().contains(in.mouseX(), in.mouseY())) finish(fallback);
        }
    }

    private void finish(String result) {
        finished = true;
        game.pop();
        done.accept(result);
    }

    @Override
    public void render(Graphics2D g) {
        // haute résolution uniquement
    }

    @Override
    public void renderHiRes(Graphics2D g, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRect(0, 0, w, h);
        double s = scale();
        Rectangle2D p = panel();
        for (int i = 5; i >= 1; i--) HiUi.fill(g, new Color(0, 0, 0, 24), p.getX() - i * 2, p.getY() - i * 2 + 4, p.getWidth() + i * 4, p.getHeight() + i * 4, 16);
        HiUi.fill(g, HiUi.PANEL, p.getX(), p.getY(), p.getWidth(), p.getHeight(), 10 * s);
        HiUi.fill(g, HiUi.HEADER, p.getX(), p.getY(), p.getWidth(), 42 * s, 10 * s);
        g.setColor(HiUi.HEADER);
        g.fill(new Rectangle2D.Double(p.getX(), p.getY() + 32 * s, p.getWidth(), 10 * s));
        HiUi.stroke(g, HiUi.PANEL_BORDER, p.getX(), p.getY(), p.getWidth(), p.getHeight(), 10 * s, 1f);
        g.setFont(HiUi.font((float) (15 * s), true));
        g.setColor(Color.WHITE);
        g.drawString(title, (float) (p.getX() + 16 * s), (float) (p.getY() + 27 * s));

        // Portrait
        double box = 120 * s, bx = p.getX() + 16 * s, by = p.getY() + 62 * s;
        g.setPaint(new GradientPaint((float) bx, (float) by, new Color(0x3A3E5C), (float) bx, (float) (by + box), new Color(0x262838)));
        g.fill(new java.awt.geom.RoundRectangle2D.Double(bx, by, box, box, 12 * s, 12 * s));
        if (picture != null) {
            double k = Math.max(1, Math.floor(box / picture.getWidth() * 2) / 2);
            double size = picture.getWidth() * Math.min(k, box / picture.getWidth());
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(picture, (int) (bx + (box - size) / 2), (int) (by + (box - size) / 2), (int) size, (int) size, null);
        }

        // Champ de saisie
        g.setFont(HiUi.font((float) (12 * s), false));
        g.setColor(HiUi.MUTED);
        Rectangle2D f = field();
        g.drawString("Nom (" + MAX + " lettres max.)", (float) f.getX(), (float) (f.getY() - 10 * s));
        HiUi.fill(g, HiUi.CELL, f.getX(), f.getY(), f.getWidth(), f.getHeight(), 6 * s);
        HiUi.stroke(g, HiUi.ACCENT, f.getX(), f.getY(), f.getWidth(), f.getHeight(), 6 * s, 1.6f);
        g.setFont(HiUi.font((float) (18 * s), true));
        boolean empty = name.isEmpty();
        g.setColor(empty ? HiUi.MUTED : Color.WHITE);
        String shown = empty ? fallback : name;
        float ty = (float) (f.getCenterY() + g.getFontMetrics().getAscent() / 2.0 - 3 * s);
        g.drawString(shown, (float) (f.getX() + 12 * s), ty);
        if (frame / 30 % 2 == 0) {
            double cx = f.getX() + 12 * s + (empty ? 0 : g.getFontMetrics().stringWidth(name)) + 2;
            g.setColor(HiUi.ACCENT);
            g.fill(new Rectangle2D.Double(cx, f.getY() + 9 * s, 2 * s, f.getHeight() - 18 * s));
        }
        g.setFont(HiUi.font((float) (11 * s), false));
        g.setColor(HiUi.MUTED);
        g.drawString("Entrée : valider · Échap : garder « " + fallback + " »", (float) f.getX(), (float) (f.getMaxY() + 20 * s));

        button(g, keepButton(), "Garder", false, s);
        button(g, okButton(), "Valider", true, s);
    }

    private void button(Graphics2D g, Rectangle2D r, String label, boolean primary, double s) {
        boolean over = r.contains(game.input().mouseX(), game.input().mouseY());
        Color base = primary ? new Color(0x5B62C9) : HiUi.SELECTOR;
        HiUi.fill(g, over ? base.brighter() : base, r.getX(), r.getY(), r.getWidth(), r.getHeight(), 6 * s);
        g.setFont(HiUi.font((float) (13 * s), true));
        g.setColor(Color.WHITE);
        HiUi.centered(g, label, r.getX(), r.getY(), r.getWidth(), r.getHeight());
    }
}
