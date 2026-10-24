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
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Boîte de dialogue (texte d'un PNJ ou d'un panneau), nette et en bas de l'écran comme sur PokeMMO.
 * Le texte s'écrit lettre par lettre ; A, B ou un clic l'affiche en entier, puis passe à la page suivante.
 */
public final class DialogueScene implements Scene {

    private static final double CHARS_PER_FRAME = 1.5;

    private final Game game;
    private final List<String> pages;
    private final Runnable onClose;
    private int page;
    private double shown;
    private int frame;

    public DialogueScene(Game game, List<String> pages, Runnable onClose) {
        this.game = game;
        this.pages = pages.isEmpty() ? List.of("…") : List.copyOf(pages);
        this.onClose = onClose;
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
        double bw = Math.min(w - 32, 760 * s);
        g.setFont(HiUi.font((float) (17 * s), false));
        FontMetrics fm = g.getFontMetrics();
        int fullLines = Math.max(2, wrap(pages.get(page), fm, bw - 56 * s).size());
        double bh = fullLines * fm.getHeight() * 1.15 + 40 * s;
        double x = (w - bw) / 2, y = h - bh - 22 * s;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (int i = 4; i >= 1; i--) {
            HiUi.fill(g, new Color(0, 0, 0, 30), x - i * 2, y - i * 2 + 3, bw + i * 4, bh + i * 4, 12);
        }
        HiUi.fill(g, new Color(0x262838), x, y, bw, bh, 8 * s);
        HiUi.stroke(g, HiUi.PANEL_BORDER, x, y, bw, bh, 8 * s, 1.2f);
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
