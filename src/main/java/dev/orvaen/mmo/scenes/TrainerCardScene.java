package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.HiUi;
import dev.orvaen.mmo.engine.HiWindow;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.Input.Button;
import dev.orvaen.mmo.engine.Scene;
import dev.orvaen.mmo.rom.GameProfile;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Carte de dresseur, en fenêtre déplaçable et redimensionnable : portrait du joueur (avec sa tenue),
 * nom, n° ID, argent, Pokédex, temps de jeu, début de l'aventure et badges.
 */
public final class TrainerCardScene implements Scene {

    private static final int BADGES = 8;

    private final Game game;
    private final HiWindow win;
    private final BufferedImage portrait;
    private final Color accent;

    public TrainerCardScene(Game game) {
        this.game = game;
        this.win = new HiWindow(game, "carte-dresseur", 660, 420, 540, 360);
        PlayerSprite sprite = new PlayerSprite(game.data.player, game.save.look);
        this.portrait = sprite.front();
        int top = sprite.look().top();
        Color c = top >= 0 ? new Color(top) : HiUi.ACCENT;
        this.accent = c;
        if (game.save.trainerId < 0 || game.save.started.isEmpty()) game.save.store(); // attribue n° ID et date
    }

    @Override
    public boolean isOverlay() {
        return true;
    }

    /** Point écran d'un élément (tests) : « close ». */
    public Point anchor(String id) {
        Rectangle2D r = win.closeButton();
        return new Point((int) r.getCenterX(), (int) r.getCenterY());
    }

    @Override
    public void update(Input in) {
        if (win.update(in)) return;
        boolean close = in.pressed(Button.B) || in.pressed(Button.START) || in.pressed(Button.A)
                || in.mousePressed() && win.closeButton().contains(in.mouseX(), in.mouseY());
        if (close) {
            win.close();
            game.pop();
        }
    }

    @Override
    public void render(Graphics2D g) {
        // haute résolution uniquement
    }

    @Override
    public void renderHiRes(Graphics2D g, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0, 0, 0, 70));
        g.fillRect(0, 0, w, h);
        win.paintFrame(g, "Carte de dresseur");
        double s = win.scale();
        Rectangle2D c = win.content();
        double x = c.getX() + 16 * s, y = c.getY() + 14 * s, cw = c.getWidth() - 32 * s, ch = c.getHeight() - 30 * s;

        // La carte elle-même : bandeau à la couleur du haut porté par le joueur
        RoundRectangle2D card = new RoundRectangle2D.Double(x, y, cw, ch, 14 * s, 14 * s);
        g.setPaint(new GradientPaint((float) x, (float) y, new Color(0x2C2F45), (float) x, (float) (y + ch), new Color(0x1F2132)));
        g.fill(card);
        java.awt.Shape clip = g.getClip();
        g.clip(card);
        Color dark = mix(accent, new Color(0x1C1D2B), 0.55);
        g.setPaint(new GradientPaint((float) x, (float) y, mix(accent, new Color(0x1C1D2B), 0.25), (float) (x + cw), (float) y, dark));
        g.fill(new Rectangle2D.Double(x, y, cw, 50 * s));
        g.clip(new Rectangle2D.Double(x, y, cw, 50 * s));
        g.setColor(new Color(255, 255, 255, 18));
        for (int i = 0; i < 6; i++) {
            double d = 40 * s + i * 22 * s;
            g.fill(new Ellipse2D.Double(x + cw - d * 1.2, y - d * 0.6, d * 1.6, d * 1.6));
        }
        g.setClip(clip);
        g.setColor(new Color(0x3B3E57));
        g.setStroke(new java.awt.BasicStroke((float) (1.2 * s)));
        g.draw(card);

        g.setFont(HiUi.tracked((float) (13 * s), true, 0.14f));
        g.setColor(Color.WHITE);
        g.drawString("CARTE DE DRESSEUR", (float) (x + 18 * s), (float) (y + 31 * s));
        String id = String.format("N° ID  %05d", Math.max(0, game.save.trainerId));
        g.setFont(HiUi.font((float) (14 * s), true));
        g.drawString(id, (float) (x + cw - 18 * s - g.getFontMetrics().stringWidth(id)), (float) (y + 31 * s));

        // Portrait à droite
        double box = Math.min(150 * s, ch - 140 * s);
        double bx = x + cw - 18 * s - box, by = y + 66 * s;
        g.setPaint(new RadialGradientPaint(new Point2D.Double(bx + box / 2, by + box * 0.55), (float) (box * 0.7),
                new float[]{0f, 1f}, new Color[]{mix(accent, new Color(0x2E3147), 0.6), new Color(0x23253A)}));
        g.fill(new RoundRectangle2D.Double(bx, by, box, box, 12 * s, 12 * s));
        HiUi.stroke(g, new Color(0x45496A), bx, by, box, box, 12 * s, 1f);
        if (portrait != null) {
            double ps = Math.floor(box / portrait.getWidth() * 2) / 2; // échelle « propre » (×1, ×1,5, ×2…)
            double pw = portrait.getWidth() * ps;
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(portrait, (int) Math.round(bx + (box - pw) / 2), (int) Math.round(by + box - pw),
                    (int) Math.round(pw), (int) Math.round(pw), null);
        }

        // Informations à gauche
        String name = game.save.name == null || game.save.name.isBlank() ? "Dresseur" : game.save.name;
        int seen = 0, caught = game.save.caught.size();
        for (int sp : game.save.seen) if (!game.save.caught.contains(sp)) seen++;
        seen += caught;
        String[][] rows = {
                {"Nom", name},
                {"Argent", String.format(Locale.FRANCE, "$%,d", game.save.money).replace(' ', ' ').replace(' ', ' ')},
                {"Pokédex", seen + (seen > 1 ? " vus · " : " vu · ") + caught + (caught > 1 ? " capturés" : " capturé")},
                {"Temps de jeu", playTime(game.save.playFrames)},
                {"Aventure", "depuis le " + startDate(game.save.started)},
                {"Région", game.data.rom.profile() == GameProfile.EMERALD ? "Hoenn" : "Kanto"},
        };
        double rx = x + 22 * s, rw = bx - 20 * s - rx, ry = y + 70 * s, rowH = Math.min(30 * s, (ch - 150 * s) / rows.length);
        for (String[] r : rows) {
            g.setFont(HiUi.font((float) (13 * s), false));
            g.setColor(HiUi.MUTED);
            g.drawString(r[0], (float) rx, (float) (ry + 16 * s));
            g.setFont(HiUi.font((float) (15 * s), true));
            g.setColor(Color.WHITE);
            String v = HiUi.ellipsis(g, r[1], rw - 120 * s);
            g.drawString(v, (float) (rx + 120 * s), (float) (ry + 16 * s));
            g.setColor(new Color(255, 255, 255, 16));
            g.fill(new Rectangle2D.Double(rx, ry + rowH - 4 * s, rw, 1));
            ry += rowH;
        }

        // Badges
        double badgeY = y + ch - 52 * s;
        g.setFont(HiUi.font((float) (13 * s), false));
        g.setColor(HiUi.MUTED);
        g.drawString("Badges", (float) rx, (float) (badgeY + 23 * s));
        double r = 13 * s, gap = Math.min(40 * s, (cw - 160 * s) / BADGES);
        for (int i = 0; i < BADGES; i++) {
            double cx = rx + 110 * s + i * gap;
            g.setColor(new Color(0x1A1B28));
            g.fill(new Ellipse2D.Double(cx - r, badgeY + 18 * s - r, 2 * r, 2 * r));
            g.setColor(new Color(0x45496A));
            g.setStroke(new java.awt.BasicStroke((float) (1.4 * s)));
            g.draw(new Ellipse2D.Double(cx - r, badgeY + 18 * s - r, 2 * r, 2 * r));
        }
        g.setColor(Color.WHITE);
        g.setFont(HiUi.font((float) (13 * s), true));
        g.drawString("0 / " + BADGES, (float) (rx + 110 * s + BADGES * gap), (float) (badgeY + 23 * s));
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
