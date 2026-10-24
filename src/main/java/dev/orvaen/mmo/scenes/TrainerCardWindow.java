package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.HiUi;
import dev.orvaen.mmo.engine.HiWindow;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.UiWindow;
import dev.orvaen.mmo.rom.GameProfile;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Carte de dresseur en fenêtre (non bloquante), sobre comme les fenêtres de PokeMMO : portrait,
 * n° ID, argent, Pokédex, temps de jeu et badges.
 */
public final class TrainerCardWindow implements UiWindow {

    private static final int BADGES = 8;

    private final Game game;
    private final HiWindow win;
    private final BufferedImage portrait;
    private final Color accent;

    public TrainerCardWindow(Game game) {
        this.game = game;
        this.win = new HiWindow(game, "carte-dresseur-v2", 430, 230, 360, 200);
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
        double x = c.getX() + 14 * s, y = c.getY() + 12 * s, cw = c.getWidth() - 28 * s;

        // En-tête : nom (avec un trait à la couleur de la tenue) et n° ID
        String name = game.save.name == null || game.save.name.isBlank() ? "Dresseur" : game.save.name;
        g.setColor(accent);
        g.fill(new RoundRectangle2D.Double(x, y + 2 * s, 3 * s, 18 * s, 2 * s, 2 * s));
        g.setFont(HiUi.font((float) (16 * s), true));
        g.setColor(HiUi.TEXT);
        String id = String.format("N° ID %05d", Math.max(0, game.save.trainerId));
        g.drawString(HiUi.ellipsis(g, name, cw * 0.6), (float) (x + 10 * s), (float) (y + 16 * s));
        g.setFont(HiUi.font((float) (12 * s), false));
        g.setColor(HiUi.MUTED);
        g.drawString(id, (float) (x + cw - g.getFontMetrics().stringWidth(id)), (float) (y + 16 * s));

        // Portrait : taille d'origine (x1, ou x2 si la fenêtre est grande), sans ombre
        double top = y + 32 * s;
        double k = c.getHeight() > 260 * s ? 2 : 1;
        double size = 64 * k * Math.min(1.25, s);
        HiUi.fill(g, HiUi.CELL, x, top, size + 8 * s, size + 8 * s, 8 * s);
        if (portrait != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(portrait, (int) Math.round(x + 4 * s), (int) Math.round(top + 4 * s),
                    (int) Math.round(size), (int) Math.round(size), null);
        }

        // Informations
        int caught = game.save.caught.size(), seen = caught;
        for (int sp : game.save.seen) if (!game.save.caught.contains(sp)) seen++;
        String[][] rows = {
                {"Argent", String.format(Locale.FRANCE, "%,d ₽", game.save.money).replace('\u202F', ' ').replace('\u00A0', ' ')},
                {"Pokédex", seen + " vu" + (seen > 1 ? "s" : "") + " · " + caught + " capturé" + (caught > 1 ? "s" : "")},
                {"Temps de jeu", playTime(game.save.playFrames)},
                {"Début", startDate(game.save.started) + " · " + (game.data.rom.profile() == GameProfile.EMERALD ? "Hoenn" : "Kanto")},
        };
        double ix = x + size + 22 * s, iw = x + cw - ix, ry = top;
        double rowH = 24 * s;
        for (String[] r : rows) {
            g.setFont(HiUi.font((float) (11.5f * s), false));
            g.setColor(HiUi.MUTED);
            g.drawString(r[0], (float) ix, (float) (ry + 15 * s));
            g.setFont(HiUi.font((float) (12.5f * s), true));
            g.setColor(HiUi.TEXT);
            double vx = ix + Math.min(92 * s, iw * 0.4);
            g.drawString(HiUi.ellipsis(g, r[1], x + cw - vx), (float) vx, (float) (ry + 15 * s));
            g.setColor(HiUi.PANEL_BORDER);
            g.fill(new Rectangle2D.Double(ix, ry + rowH - 1, iw, 1));
            ry += rowH;
        }

        // Badges : 8 petits ronds (vides tant qu'ils ne sont pas gagnés)
        double by = Math.max(ry, top + size + 8 * s) + 14 * s;
        g.setFont(HiUi.font((float) (11.5f * s), false));
        g.setColor(HiUi.MUTED);
        g.drawString("Badges", (float) x, (float) (by + 4 * s));
        double r = 5 * s, gap = 16 * s, bx = x + 70 * s;
        g.setStroke(new java.awt.BasicStroke((float) Math.max(1, 1.2 * s)));
        for (int i = 0; i < BADGES; i++) {
            g.setColor(HiUi.SELECTOR_BORDER);
            g.draw(new Ellipse2D.Double(bx + i * gap - r, by - r, 2 * r, 2 * r));
        }
        g.setStroke(new java.awt.BasicStroke(1f));
        g.setFont(HiUi.font((float) (12 * s), true));
        g.setColor(HiUi.TEXT);
        String count = "0 / " + BADGES;
        g.drawString(count, (float) (x + cw - g.getFontMetrics().stringWidth(count)), (float) (by + 4 * s));
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

}
