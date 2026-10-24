package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.engine.HiUi;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.Input.Button;

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
 * Boîte de texte du jeu, large en bas de l'écran comme sur PokeMMO : le texte s'écrit lettre par lettre,
 * A (ou un clic) l'affiche d'un coup puis passe à la page suivante. Quand quelqu'un parle, une pointe
 * part vers lui et une flèche rebondit au-dessus de sa tête. Peut aussi poser une question oui / non.
 * Utilisée par les dialogues simples et par les scripts de l'histoire.
 */
public final class MessageBox {

    private static final double CHARS_PER_FRAME = 1.5;

    private List<String> pages = List.of();
    private Supplier<DialogueScene.Speaker> speaker;
    private boolean open;
    private int page;
    private double shown;
    private int frame;
    private boolean button;
    private boolean choosing;
    private int choice;        // 0 oui, 1 non (curseur)
    private int result = -1;   // 1 oui, 0 non

    public void show(List<String> text, Supplier<DialogueScene.Speaker> speaker) {
        this.pages = text.isEmpty() ? List.of("…") : List.copyOf(text);
        this.speaker = speaker;
        this.open = true;
        this.page = 0;
        this.shown = 0;
        this.frame = 0;
        this.button = false;
        this.choosing = false;
    }

    public void close() {
        open = false;
        choosing = false;
    }

    public boolean isOpen() {
        return open;
    }

    public int page() {
        return page;
    }

    public boolean pageComplete() {
        return open && shown >= pages.get(page).length();
    }

    /** Tout le texte est affiché (dernière page écrite jusqu'au bout). */
    public boolean printed() {
        return !open || page == pages.size() - 1 && pageComplete();
    }

    /** A pressé une fois le texte fini (consommé par l'appel). */
    public boolean takeButton() {
        if (!button) return false;
        button = false;
        return true;
    }

    public void askYesNo() {
        choosing = true;
        choice = 0;
        result = -1;
        if (!open) show(List.of(""), null);
    }

    /** -1 tant que le joueur n'a pas répondu, puis 1 (oui) ou 0 (non). */
    public int takeResult() {
        int r = result;
        if (r >= 0) result = -1;
        return r;
    }

    public void update(Input in) {
        if (!open) return;
        frame++;
        String text = pages.get(page);
        if (shown < text.length()) shown = Math.min(text.length(), shown + CHARS_PER_FRAME);
        if (choosing && printed()) {
            if (in.repeat(Button.UP) || in.repeat(Button.DOWN)) choice = 1 - choice;
            if (in.pressed(Button.A)) {
                result = choice == 0 ? 1 : 0;
                choosing = false;
            } else if (in.pressed(Button.B)) {
                result = 0;
                choosing = false;
            }
            return;
        }
        boolean next = in.pressed(Button.A) || in.pressed(Button.B) || in.mousePressed();
        if (!next) return;
        if (shown < text.length()) {
            shown = text.length();
        } else if (page + 1 < pages.size()) {
            page++;
            shown = 0;
        } else {
            button = true;
        }
    }

    private void drawChoice(Graphics2D g, double s, double right, double top) {
        double cw = 120 * s, ch = 84 * s, cx = right - cw, cy = top - ch - 10 * s;
        HiUi.fill(g, new Color(0, 0, 0, 60), cx + 2, cy + 4, cw, ch, 10 * s);
        HiUi.fill(g, new Color(0x262838), cx, cy, cw, ch, 10 * s);
        HiUi.stroke(g, HiUi.PANEL_BORDER, cx, cy, cw, ch, 10 * s, 1.2f);
        String[] opts = {"Oui", "Non"};
        g.setFont(HiUi.font((float) (16 * s), true));
        for (int i = 0; i < 2; i++) {
            double oy = cy + 10 * s + i * 32 * s;
            if (i == choice) HiUi.fill(g, HiUi.SELECTOR_FOCUS, cx + 8 * s, oy, cw - 16 * s, 28 * s, 6 * s);
            g.setColor(i == choice ? Color.WHITE : HiUi.TEXT);
            HiUi.centered(g, opts[i], cx + 8 * s, oy, cw - 16 * s, 28 * s);
        }
    }

    /** Dessine la boîte (et le choix oui / non) si elle est ouverte. */
    public void render(Graphics2D g, int w, int h) {
        if (!open) return;
        double s = Math.max(0.8, Math.min(1.6, h / 720.0));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(HiUi.font((float) (17 * s), false));
        FontMetrics fm = g.getFontMetrics();
        DialogueScene.Speaker who = speaker == null ? null : speaker.get();

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
        if (choosing) drawChoice(g, s, x + bw, y);
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
