package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.HiUi;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.Input.Button;
import dev.orvaen.mmo.engine.Scene;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Boîte de dialogue large en bas de l'écran (comme PokeMMO). Quand un PNJ ou un panneau parle, une pointe
 * part du haut de la boîte vers lui et une flèche rebondit au-dessus de sa tête.
 * Le texte s'écrit lettre par lettre ; A, B ou un clic l'affiche en entier, puis passe à la page suivante.
 */
public final class DialogueScene implements Scene {

    private static final double CHARS_PER_FRAME = 1.5;

    /**
     * Qui parle : rectangle du sprite dans la fenêtre ; {@code below} place la bulle sous lui
     * (quand le joueur est au-dessus, pour ne pas le cacher).
     */
    public record Speaker(Rectangle2D bounds, boolean below) {}

    private final Game game;
    private final List<String> pages;
    private final Runnable onClose;
    private final Supplier<Speaker> speaker;
    private int page;
    private double shown;
    private int frame;

    public DialogueScene(Game game, List<String> pages, Runnable onClose) {
        this(game, pages, onClose, null);
    }

    public DialogueScene(Game game, List<String> pages, Runnable onClose, Supplier<Speaker> speaker) {
        this.game = game;
        this.pages = pages.isEmpty() ? List.of("…") : List.copyOf(pages);
        this.onClose = onClose;
        this.speaker = speaker;
    }

    @Override
    public boolean isOverlay() {
        return true;
    }

    public int page() {
        return page;
    }

    public boolean pageComplete() {
        return shown >= pages.get(page).length();
    }

    @Override
    public void update(Input in) {
        frame++;
        String text = pages.get(page);
        if (shown < text.length()) shown = Math.min(text.length(), shown + CHARS_PER_FRAME);
        boolean next = in.pressed(Button.A) || in.pressed(Button.B) || in.mousePressed();
        if (!next) return;
        if (shown < text.length()) {
            shown = text.length();
            return;
        }
        if (page + 1 < pages.size()) {
            page++;
            shown = 0;
        } else {
            game.pop();
            if (onClose != null) onClose.run();
        }
    }

    @Override
    public void render(Graphics2D g) {
        // haute résolution uniquement
    }

    @Override
    public void renderHiRes(Graphics2D g, int w, int h) {
        double s = Math.max(0.8, Math.min(1.6, h / 720.0));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(HiUi.font((float) (17 * s), false));
        FontMetrics fm = g.getFontMetrics();
        Speaker who = speaker == null ? null : speaker.get();

        // Taille : bulle ajustée au texte (page entière, pour qu'elle ne grandisse pas en cours d'écriture)
        // Boîte large en bas de l'écran (comme PokeMMO) ; si quelqu'un parle, une pointe sur le bord
        // supérieur part vers lui et une flèche rebondit au-dessus de sa tête.
        double bw = Math.min(w - 32, 760 * s);
        List<String> full = wrap(pages.get(page), fm, bw - 56 * s);
        double bh = Math.max(2, full.size()) * fm.getHeight() * 1.15 + 40 * s;
        double x = (w - bw) / 2, y = h - bh - 22 * s;
        Rectangle2D r = who == null ? null : who.bounds();

        if (r != null) drawMarker(g, s, r);

        // Apparition : la boîte monte légèrement en fondu
        double pop = Math.min(1, frame / 7.0);
        java.awt.geom.AffineTransform saved = g.getTransform();
        g.translate(0, (1 - (1 - Math.pow(1 - pop, 3))) * 14 * s);
        java.awt.Composite savedComposite = g.getComposite();
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) Math.min(1, pop * 1.4)));

        Area shape = new Area(new RoundRectangle2D.Double(x, y, bw, bh, 14 * s, 14 * s));
        if (r != null) {
            double target = r.getCenterX();
            double tx = Math.max(x + 34 * s, Math.min(x + bw - 34 * s, target));
            double lean = Math.max(-14 * s, Math.min(14 * s, (target - tx) * 0.35));
            Path2D t = new Path2D.Double();
            t.moveTo(tx - 13 * s, y + 1);
            t.lineTo(tx + 13 * s, y + 1);
            t.lineTo(tx + lean, y - 17 * s);
            t.closePath();
            shape.add(new Area(t));
        }
        for (int i = 4; i >= 1; i--) {
            g.setColor(new Color(0, 0, 0, 26));
            g.fill(java.awt.geom.AffineTransform.getTranslateInstance(0, i * 1.2).createTransformedShape(shape));
        }
        g.setColor(new Color(0x262838));
        g.fill(shape);
        g.setColor(HiUi.PANEL_BORDER);
        g.setStroke(new java.awt.BasicStroke((float) (1.4 * s)));
        g.draw(shape);
        HiUi.fill(g, HiUi.ACCENT, x, y + 14 * s, 4 * s, bh - 28 * s, 2 * s);

        String visible = pages.get(page).substring(0, (int) Math.min(pages.get(page).length(), Math.floor(shown)));
        List<String> lines = wrap(visible, fm, bw - 56 * s);
        g.setColor(HiUi.TEXT);
        double ly = y + 22 * s + fm.getAscent();
        for (String line : lines) {
            g.drawString(line, (float) (x + 26 * s), (float) ly);
            ly += fm.getHeight() * 1.15;
        }

        if (pageComplete() && frame / 20 % 2 == 0) {
            double ax = x + bw - 30 * s, ay = y + bh - 24 * s, a = 7 * s;
            Path2D arrow = new Path2D.Double();
            arrow.moveTo(ax - a, ay - a * 0.6);
            arrow.lineTo(ax + a, ay - a * 0.6);
            arrow.lineTo(ax, ay + a * 0.6);
            arrow.closePath();
            g.setColor(HiUi.ACCENT);
            g.fill(arrow);
        }
        g.setComposite(savedComposite);
        g.setTransform(saved);
    }

    /** Flèche qui rebondit au-dessus de celui qui parle. */
    private void drawMarker(Graphics2D g, double s, Rectangle2D r) {
        double bounce = Math.abs(Math.sin(frame * 0.12)) * 5 * s;
        double cx = r.getCenterX(), tip = r.getMinY() - 4 * s - bounce, a = 12 * s;
        Path2D m = new Path2D.Double();
        m.moveTo(cx - a, tip - a * 1.2);
        m.lineTo(cx + a, tip - a * 1.2);
        m.lineTo(cx, tip);
        m.closePath();
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(java.awt.geom.AffineTransform.getTranslateInstance(0, 2 * s).createTransformedShape(m));
        g.setColor(HiUi.ACCENT);
        g.fill(m);
        g.setColor(Color.WHITE);
        g.setStroke(new java.awt.BasicStroke((float) (1.5 * s), java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g.draw(m);
    }

    /** Lignes du jeu (retours à la ligne d'origine), recoupées si elles dépassent la boîte. */
    private static List<String> wrap(String text, FontMetrics fm, double max) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String tryLine = line.isEmpty() ? word : line + " " + word;
                if (fm.stringWidth(tryLine) > max && !line.isEmpty()) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(tryLine);
                }
            }
            out.add(line.toString());
        }
        return out;
    }
}
