package dev.orvaen.mmo.engine;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/** Petits outils de dessin d'interface, dans un style « boîte de dialogue GBA » maison. */
public final class Ui {

    public static final Font FONT = new Font(Font.DIALOG, Font.BOLD, 10);
    public static final Font SMALL = new Font(Font.DIALOG, Font.PLAIN, 8);
    public static final Color INK = new Color(0x303038);
    public static final Color SHADOW = new Color(0xD0D0C8);
    public static final Color PAPER = new Color(0xF8F8F0);
    public static final Color FRAME = new Color(0x4A6CB8);
    public static final Color ACCENT = new Color(0xE05A3A);

    private Ui() {}

    public static void box(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(FRAME);
        g.fillRoundRect(x, y, w, h, 8, 8);
        g.setColor(PAPER);
        g.fillRoundRect(x + 2, y + 2, w - 4, h - 4, 6, 6);
        g.setColor(SHADOW);
        g.setStroke(new BasicStroke(1));
        g.drawRoundRect(x + 3, y + 3, w - 7, h - 7, 5, 5);
    }

    /** Texte avec ombre portée, ancré sur la ligne de base. */
    public static void text(Graphics2D g, String s, int x, int y) {
        text(g, s, x, y, INK);
    }

    public static void text(Graphics2D g, String s, int x, int y, Color color) {
        g.setColor(SHADOW);
        g.drawString(s, x + 1, y + 1);
        g.setColor(color);
        g.drawString(s, x, y);
    }

    public static void textRight(Graphics2D g, String s, int right, int y) {
        text(g, s, right - g.getFontMetrics().stringWidth(s), y);
    }

    /** Petite flèche de sélection ▶. */
    public static void cursor(Graphics2D g, int x, int y) {
        g.setColor(INK);
        g.fillPolygon(new int[]{x, x + 5, x}, new int[]{y - 7, y - 4, y - 1}, 3);
    }

    /** Découpe un texte pour qu'il tienne dans une largeur donnée. */
    public static List<String> wrap(Graphics2D g, String text, int width) {
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(candidate) > width && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    public static void bar(Graphics2D g, int x, int y, int w, int h, double ratio, Color fill) {
        g.setColor(INK);
        g.fillRect(x, y, w, h);
        g.setColor(new Color(0x585860));
        g.fillRect(x + 1, y + 1, w - 2, h - 2);
        g.setColor(fill);
        g.fillRect(x + 1, y + 1, (int) Math.round((w - 2) * Math.max(0, Math.min(1, ratio))), h - 2);
    }
}
