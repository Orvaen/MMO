package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.HiUi;
import dev.orvaen.mmo.engine.HiWindow;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.UiWindow;
import dev.orvaen.mmo.rom.GameProfile;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Carte de dresseur en fenêtre (non bloquante) : une vraie carte aux couleurs de la tenue du joueur,
 * avec son portrait, son n° ID, son argent, son Pokédex, son temps de jeu et ses badges.
 */
public final class TrainerCardWindow implements UiWindow {

    private static final int BADGES = 8;

    private final Game game;
    private final HiWindow win;
    private final BufferedImage portrait;
    private final Color accent;

    public TrainerCardWindow(Game game) {
        this.game = game;
        this.win = new HiWindow(game, "carte-dresseur", 540, 330, 460, 290);
        PlayerSprite sprite = new PlayerSprite(game.data.player, game.save.look);
        this.portrait = sprite.front();
        int top = sprite.look().top();
        this.accent = top >= 0 ? new Color(top) : new Color(0xC0384F);
        if (game.save.trainerId < 0 || game.save.started.isEmpty()) game.save.store(); // n° ID et date attribués
    }

    @Override
    public String id() {
        return "carte-dresseur";
    }

    @Override
    public HiWindow frame() {
        return win;
    }

    /** Point écran d'un élément (tests) : « close ». */
    public Point anchor(String id) {
        Rectangle2D r = win.closeButton();
        return new Point((int) r.getCenterX(), (int) r.getCenterY());
    }

    @Override
    public boolean update(Input in, boolean focused) {
        if (!focused) return true;
        if (win.update(in)) return true;
        return !(in.mousePressed() && win.closeButton().contains(in.mouseX(), in.mouseY()));
    }

    @Override
    public void render(Graphics2D g, int w, int h, boolean focused) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        win.paintFrame(g, "Carte de dresseur");
        double s = win.scale();
        Rectangle2D c = win.content();
        double x = c.getX() + 12 * s, y = c.getY() + 10 * s, cw = c.getWidth() - 24 * s, ch = c.getHeight() - 22 * s;

        // La carte : dégradé aux couleurs du haut porté, motif discret
        RoundRectangle2D card = new RoundRectangle2D.Double(x, y, cw, ch, 16 * s, 16 * s);
        Color light = mix(accent, Color.WHITE, 0.12), dark = mix(accent, new Color(0x141522), 0.62);
        g.setPaint(new GradientPaint((float) x, (float) y, light, (float) (x + cw * 0.8), (float) (y + ch), dark));
        g.fill(card);
        Shape clip = g.getClip();
        g.clip(card);
        g.setColor(new Color(255, 255, 255, 14));
        g.setStroke(new java.awt.BasicStroke((float) (10 * s)));
        for (double d = -ch; d < cw; d += 34 * s) g.draw(new Line2D.Double(x + d, y + ch, x + d + ch, y));
        g.setColor(new Color(255, 255, 255, 22));
        g.fill(new Ellipse2D.Double(x + cw - 120 * s, y - 90 * s, 260 * s, 200 * s));

        // Bandeau des badges, en bas
        double bandH = 46 * s, bandY = y + ch - bandH;
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new Rectangle2D.Double(x, bandY, cw, bandH));
        g.setClip(clip);
        g.setColor(new Color(255, 255, 255, 60));
        g.setStroke(new java.awt.BasicStroke(1f));
        g.draw(card);

        // En-tête
        g.setFont(HiUi.tracked((float) (11 * s), true, 0.16f));
        g.setColor(new Color(255, 255, 255, 190));
        g.drawString("CARTE DE DRESSEUR", (float) (x + 16 * s), (float) (y + 22 * s));
        String id = String.format("N° ID %05d", Math.max(0, game.save.trainerId));
        g.setFont(HiUi.font((float) (12 * s), true));
        g.setColor(Color.WHITE);
        g.drawString(id, (float) (x + cw - 16 * s - g.getFontMetrics().stringWidth(id)), (float) (y + 22 * s));

        // Portrait dans un cadre clair
        double ph = ch - bandH - 46 * s, pw = Math.min(ph, 150 * s);
        double px = x + 16 * s, py = y + 34 * s;
        HiUi.fill(g, new Color(0, 0, 0, 50), px + 2, py + 3, pw, ph, 12 * s);
        HiUi.fill(g, new Color(0xF3F4FA), px, py, pw, ph, 12 * s);
        HiUi.fill(g, mix(accent, Color.WHITE, 0.82), px + 6 * s, py + ph * 0.55, pw - 12 * s, ph * 0.45 - 6 * s, 8 * s);
        if (portrait != null) {
            double k = Math.max(1, Math.floor(Math.min(pw, ph) / portrait.getWidth() * 2) / 2);
            double size = portrait.getWidth() * k;
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(portrait, (int) Math.round(px + (pw - size) / 2), (int) Math.round(py + ph - size),
                    (int) Math.round(size), (int) Math.round(size), null);
        }

        // Nom et informations
        double ix = px + pw + 18 * s, iw = x + cw - 16 * s - ix;
        String name = game.save.name == null || game.save.name.isBlank() ? "Dresseur" : game.save.name;
        g.setFont(HiUi.font((float) (20 * s), true));
        g.setColor(Color.WHITE);
        g.drawString(HiUi.ellipsis(g, name, iw), (float) ix, (float) (py + 20 * s));
        int caught = game.save.caught.size(), seen = caught;
        for (int sp : game.save.seen) if (!game.save.caught.contains(sp)) seen++;
        String[][] rows = {
                {"Argent", String.format(Locale.FRANCE, "%,d $", game.save.money).replace(' ', ' ').replace(' ', ' ')},
                {"Pokédex", seen + " vu" + (seen > 1 ? "s" : "") + " · " + caught + " capturé" + (caught > 1 ? "s" : "")},
                {"Temps de jeu", playTime(game.save.playFrames)},
                {"Aventure", startDate(game.save.started) + " · " + (game.data.rom.profile() == GameProfile.EMERALD ? "Hoenn" : "Kanto")},
        };
        double ry = py + 36 * s, rowH = Math.min(26 * s, (ph - 36 * s) / rows.length);
        for (String[] r : rows) {
            g.setFont(HiUi.font((float) (11 * s), false));
            g.setColor(new Color(255, 255, 255, 170));
            g.drawString(r[0], (float) ix, (float) (ry + 13 * s));
            g.setFont(HiUi.font((float) (13 * s), true));
            g.setColor(Color.WHITE);
            double vx = ix + Math.min(96 * s, iw * 0.42);
            g.drawString(HiUi.ellipsis(g, r[1], x + cw - 16 * s - vx), (float) vx, (float) (ry + 13 * s));
            g.setColor(new Color(255, 255, 255, 30));
            g.fill(new Rectangle2D.Double(ix, ry + rowH - 3 * s, iw, 1));
            ry += rowH;
        }

        // Badges
        g.setFont(HiUi.tracked((float) (10 * s), true, 0.14f));
        g.setColor(new Color(255, 255, 255, 190));
        g.drawString("BADGES", (float) (x + 16 * s), (float) (bandY + bandH / 2 + 4 * s));
        double r = 10 * s, gap = Math.min(30 * s, (cw - 150 * s) / BADGES);
        for (int i = 0; i < BADGES; i++) {
            double cx = x + 90 * s + i * gap, cy = bandY + bandH / 2;
            g.setColor(new Color(0, 0, 0, 80));
            g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
            g.setColor(new Color(255, 255, 255, 70));
            g.setStroke(new java.awt.BasicStroke((float) (1.2 * s)));
            g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        }
        g.setFont(HiUi.font((float) (12 * s), true));
        g.setColor(Color.WHITE);
        String count = "0 / " + BADGES;
        g.drawString(count, (float) (x + cw - 16 * s - g.getFontMetrics().stringWidth(count)), (float) (bandY + bandH / 2 + 4 * s));
    }

    static String playTime(long frames) {
        long minutes = frames / 60 / 60;
        return String.format("%d h %02d", minutes / 60, minutes % 60);
    }

    private static String startDate(String iso) {
        try {
            return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (RuntimeException e) {
            return LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }
    }

    private static Color mix(Color a, Color b, double t) {
        return new Color((int) (a.getRed() * (1 - t) + b.getRed() * t), (int) (a.getGreen() * (1 - t) + b.getGreen() * t),
                (int) (a.getBlue() * (1 - t) + b.getBlue() * t));
    }
}
