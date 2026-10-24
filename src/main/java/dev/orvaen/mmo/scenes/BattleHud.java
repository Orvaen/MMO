package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.battle.Pokemon;
import dev.orvaen.mmo.engine.HiUi;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Dessins de l'interface de combat, façon Rouge Feu : cadres de PV, barres, texte de la boîte du bas.
 * <p>
 * Deux sortes de méthodes :
 * <ul>
 *   <li>« pixel » : dessinent sur l'écran GBA (240x160), agrandi ensuite sans flou ;</li>
 *   <li>« texte » : écrivent en haute résolution, à partir de coordonnées GBA converties par {@link Mapping}.</li>
 * </ul>
 */
final class BattleHud {

    // Couleurs du cadre de PV (palette de l'interface de combat du jeu)
    static final Color BOX_FILL = new Color(255, 255, 222);
    static final Color BOX_SHADE = new Color(222, 213, 180);
    static final Color BOX_EDGE = new Color(57, 82, 65);
    static final Color BOX_TAIL = new Color(82, 106, 98);
    static final Color INK = new Color(65, 65, 65);
    static final Color HP_LABEL = new Color(255, 230, 0);
    static final Color BAR_BG = new Color(57, 82, 65);
    static final Color EXP_BAR = new Color(65, 205, 255);
    static final Color MESSAGE_INK = Color.WHITE;
    static final Color MESSAGE_SHADOW = new Color(106, 90, 115);
    static final Color MENU_INK = new Color(72, 72, 72);
    static final Color MENU_SHADOW = new Color(208, 208, 200);

    /** Cadres de PV : position et taille sur l'écran GBA. */
    static final Rectangle2D FOE_BOX = new Rectangle2D.Double(10, 12, 102, 30);
    static final Rectangle2D PLAYER_BOX = new Rectangle2D.Double(126, 70, 106, 40);

    private BattleHud() {}

    /** Conversion des coordonnées GBA vers la fenêtre (échelle et décalage de l'image agrandie). */
    record Mapping(double x0, double y0, double scale) {
        double x(double gx) {
            return x0 + gx * scale;
        }

        double y(double gy) {
            return y0 + gy * scale;
        }

        double size(double g) {
            return g * scale;
        }
    }

    // ================================================================== pixel (écran GBA)

    /** Cadre de PV : fond crème, bord foncé, petite « queue » sur le côté comme dans le jeu. */
    static void drawBoxFrame(Graphics2D g, Rectangle2D r, boolean player) {
        int x = (int) r.getX(), y = (int) r.getY(), w = (int) r.getWidth(), h = (int) r.getHeight();
        // queue (en bas à gauche pour l'adversaire, en bas à droite pour le joueur)
        Path2D tail = new Path2D.Double();
        if (player) {
            tail.moveTo(x - 8, y + h);
            tail.lineTo(x + 6, y + h - 7);
            tail.lineTo(x + 6, y + h);
        } else {
            tail.moveTo(x + w + 8, y + h);
            tail.lineTo(x + w - 6, y + h - 7);
            tail.lineTo(x + w - 6, y + h);
        }
        tail.closePath();
        g.setColor(BOX_TAIL);
        g.fill(tail);
        g.setColor(BOX_EDGE);
        g.fill(new RoundRectangle2D.Double(x, y, w, h - 2, 8, 8));
        g.setColor(BOX_FILL);
        g.fill(new RoundRectangle2D.Double(x + 2, y + 2, w - 4, h - 6, 5, 5));
        g.setColor(BOX_SHADE);
        g.fillRect(x + 4, y + h - 6, w - 8, 1);
    }

    /** Barre de PV : étiquette « PV », fond sombre, remplissage vert / jaune / rouge. */
    static void drawHpBar(Graphics2D g, int x, int y, double fraction) {
        int barW = 48;
        g.setColor(BAR_BG);
        g.fillRoundRect(x, y, barW + 17, 5, 3, 3);
        g.setColor(HP_LABEL);
        g.fillRect(x + 2, y + 1, 11, 3);                         // étiquette « PV » (lettres en haute résolution)
        g.setColor(Color.WHITE);
        g.fillRect(x + 15, y + 1, barW, 3);
        double f = Math.max(0, Math.min(1, fraction));
        int fill = (int) Math.ceil(f * barW);
        Color[] c = hpColors(f);
        g.setColor(c[1]);
        g.fillRect(x + 15, y + 1, fill, 3);
        g.setColor(c[0]);
        g.fillRect(x + 15, y + 1, fill, 2);
    }

    /** Couleurs clair / foncé de la barre selon les PV restants (vert > 50 %, jaune > 20 %, rouge). */
    static Color[] hpColors(double f) {
        if (f > 0.5) return new Color[]{new Color(115, 255, 172), new Color(90, 213, 131)};
        if (f > 0.2) return new Color[]{new Color(255, 230, 57), new Color(205, 172, 8)};
        return new Color[]{new Color(255, 90, 57), new Color(172, 65, 74)};
    }

    /** Barre d'expérience du joueur (sous ses PV). */
    static void drawExpBar(Graphics2D g, int x, int y, int w, double fraction) {
        g.setColor(BAR_BG);
        g.fillRoundRect(x, y - 1, w, 5, 3, 3);                  // l'étiquette « EXP » est écrite à gauche
        g.setColor(new Color(255, 255, 255, 60));
        g.fillRect(x + 16, y, w - 18, 3);
        g.setColor(EXP_BAR);
        g.fillRect(x + 16, y, (int) Math.round((w - 18) * Math.max(0, Math.min(1, fraction))), 3);
    }

    /** Boîte du bas de secours (si la ROM n'en fournit pas) : cadre bleu-vert du jeu. */
    static void drawFallbackBox(Graphics2D g, boolean menu, boolean moves) {
        g.setColor(new Color(41, 82, 106));
        g.fillRect(0, 112, 240, 48);
        g.setColor(new Color(205, 172, 74));
        g.drawRoundRect(2, 114, 235, 43, 6, 6);
        if (menu || moves) {
            int x = moves ? 160 : 120;
            g.setColor(Color.WHITE);
            g.fillRoundRect(moves ? 0 : x, 112, moves ? 160 : 120, 48, 6, 6);
            if (moves) {
                g.fillRoundRect(160, 112, 80, 48, 6, 6);
            }
            g.setColor(new Color(82, 98, 156));
            if (moves) {
                g.drawRoundRect(1, 113, 157, 45, 6, 6);
                g.drawRoundRect(161, 113, 77, 45, 6, 6);
            } else {
                g.drawRoundRect(x + 1, 113, 117, 45, 6, 6);
            }
        }
    }

    // ================================================================== texte (haute résolution)

    /** Police un peu resserrée, plus proche de celle de la GBA (taille en pixels GBA). */
    static Font font(Mapping m, double gbaSize, boolean bold) {
        return HiUi.font((float) m.size(gbaSize), bold).deriveFont(java.awt.geom.AffineTransform.getScaleInstance(0.88, 1));
    }

    /** Texte avec ombre décalée d'un pixel GBA (bas-droite), comme la police du jeu. */
    static void text(Graphics2D g, Mapping m, String s, double gx, double gy, Color ink, Color shadow) {
        float x = (float) m.x(gx), y = (float) m.y(gy);
        float d = (float) Math.max(1, m.scale() * 0.6);
        g.setColor(shadow);
        g.drawString(s, x + d, y + d);
        g.setColor(ink);
        g.drawString(s, x, y);
    }

    /** Découpe un message en lignes qui tiennent dans {@code width} pixels GBA. */
    static List<String> wrap(Graphics2D g, Mapping m, String text, double width) {
        List<String> lines = new ArrayList<>();
        FontMetrics fm = g.getFontMetrics();
        double max = m.size(width);
        for (String para : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (fm.stringWidth(next) > max && !line.isEmpty()) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(next);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    /** Nom, niveau et (joueur) PV chiffrés dans un cadre de PV. */
    static void drawBoxTexts(Graphics2D g, Mapping m, Rectangle2D r, String name, int level, int status,
                             int hp, int maxHp, boolean player) {
        g.setFont(font(m, 9, true));
        double x = r.getX() + 7, y = r.getY() + 11;
        text(g, m, name, x, y, INK, BOX_SHADE);
        String lv = "N." + level;
        g.setFont(font(m, 8, true));
        double lvW = g.getFontMetrics().stringWidth(lv) / m.scale();
        text(g, m, lv, r.getX() + r.getWidth() - 7 - lvW, y, INK, BOX_SHADE);
        // « PV » dans l'étiquette jaune de la barre
        g.setFont(font(m, 4.6, true));
        int barX = (int) (r.getX() + r.getWidth() - 7 - 65), barY = (int) (r.getY() + 15);
        g.setColor(new Color(57, 82, 65));
        g.drawString("PV", (float) m.x(barX + 2.6), (float) m.y(barY + 4.1));
        if (status != Pokemon.OK) {
            String[] names = {"", "SOM", "PSN", "BRL", "GEL", "PAR"};
            Color[] colors = {null, new Color(140, 136, 140), new Color(176, 88, 168), new Color(232, 96, 64),
                    new Color(112, 184, 232), new Color(200, 168, 40)};
            double sx = r.getX() + 7, sy = r.getY() + 15;
            g.setColor(colors[status]);
            g.fill(new RoundRectangle2D.Double(m.x(sx), m.y(sy), m.size(16), m.size(6), m.size(2), m.size(2)));
            g.setColor(Color.WHITE);
            g.setFont(font(m, 5, true));
            g.drawString(names[status], (float) m.x(sx + 2), (float) m.y(sy + 5));
        }
        if (player) {
            g.setFont(font(m, 4.8, true));
            g.setColor(HP_LABEL);
            g.drawString("EXP", (float) m.x(r.getX() + 14), (float) m.y(r.getY() + r.getHeight() - 4.4));
            g.setFont(font(m, 8, true));
            String hpText = hp + "/ " + maxHp;
            double w = g.getFontMetrics().stringWidth(hpText) / m.scale();
            text(g, m, hpText, r.getX() + r.getWidth() - 8 - w, r.getY() + 30, INK, BOX_SHADE);
        }
    }

    /** Petite flèche ▶ du curseur des menus. */
    static void cursor(Graphics2D g, Mapping m, double gx, double gy) {
        Path2D p = new Path2D.Double();
        p.moveTo(m.x(gx), m.y(gy - 4));
        p.lineTo(m.x(gx + 5), m.y(gy - 0.5));
        p.lineTo(m.x(gx), m.y(gy + 3));
        p.closePath();
        g.setColor(MENU_SHADOW);
        g.translate(m.size(0.6), m.size(0.6));
        g.fill(p);
        g.translate(-m.size(0.6), -m.size(0.6));
        g.setColor(MENU_INK);
        g.fill(p);
    }

    /** Cadre blanc arrondi façon menus du jeu, en haute résolution. */
    static void whitePanel(Graphics2D g, double x, double y, double w, double h, double s) {
        g.setColor(new Color(82, 98, 156));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 10 * s, 10 * s));
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Double(x + 3 * s, y + 3 * s, w - 6 * s, h - 6 * s, 7 * s, 7 * s));
        g.setStroke(new BasicStroke((float) (1.2 * s)));
        g.setColor(new Color(208, 208, 216));
        g.draw(new RoundRectangle2D.Double(x + 5 * s, y + 5 * s, w - 10 * s, h - 10 * s, 5 * s, 5 * s));
        g.setStroke(new BasicStroke(1f));
    }
}
