package dev.orvaen.mmo.engine;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Petites icônes de l'interface, dessinées en vectoriel (nettes à toutes les tailles, aucune image du jeu).
 * Chaque icône tient dans un carré de côté {@code size} dont le coin haut-gauche est ({@code x}, {@code y}).
 */
public final class UiIcons {

    public enum Icon { CARD, BARS, HEXAGON, BOLT, BALL, HASH, TAG, LEAF, STAR, PERSON, SPARK, ARROW_UP, BAG, HEART,
        SPEAKER, MUTE, NOTE, GEAR, SCREEN, DEX, MENU }

    private UiIcons() {
    }

    public static void draw(Graphics2D g0, Icon icon, double x, double y, double size, Color c) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.translate(x, y);
        g.scale(size / 16.0, size / 16.0);          // on dessine sur une grille de 16 × 16
        g.setColor(c);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (icon) {
            case CARD -> {                            // carte d'identité
                g.draw(new RoundRectangle2D.Double(1.5, 3, 13, 10, 3, 3));
                g.fill(new Ellipse2D.Double(3.8, 5.6, 3.4, 3.4));
                g.draw(new Line2D.Double(9, 6.5, 12.5, 6.5));
                g.draw(new Line2D.Double(9, 9.5, 11.5, 9.5));
                g.draw(new Line2D.Double(3.8, 11, 7.2, 11));
            }
            case BARS -> {                            // statistiques
                g.fill(new RoundRectangle2D.Double(2, 8, 3, 6, 1.2, 1.2));
                g.fill(new RoundRectangle2D.Double(6.5, 3, 3, 11, 1.2, 1.2));
                g.fill(new RoundRectangle2D.Double(11, 6, 3, 8, 1.2, 1.2));
            }
            case HEXAGON -> {                         // IV / EV (radar)
                g.draw(hexagon(8, 8, 6.5));
                g.fill(hexagon(8, 8.6, 3.2));
            }
            case BOLT -> {                            // attaques
                Path2D p = new Path2D.Double();
                p.moveTo(9.5, 1);
                p.lineTo(3, 9.2);
                p.lineTo(7.6, 9.2);
                p.lineTo(6.5, 15);
                p.lineTo(13, 6.8);
                p.lineTo(8.4, 6.8);
                p.closePath();
                g.fill(p);
            }
            case BALL -> {                            // Poké Ball stylisée
                g.draw(new Ellipse2D.Double(1.5, 1.5, 13, 13));
                g.fill(new Arc2D.Double(1.5, 1.5, 13, 13, 0, 180, Arc2D.CHORD));
                g.setColor(Color.WHITE);
                g.fill(new Ellipse2D.Double(5.6, 5.6, 4.8, 4.8));
                g.setColor(c);
                g.draw(new Ellipse2D.Double(5.6, 5.6, 4.8, 4.8));
            }
            case HASH -> {                            // n° du Pokédex
                g.draw(new Line2D.Double(6, 2, 4.5, 14));
                g.draw(new Line2D.Double(11.5, 2, 10, 14));
                g.draw(new Line2D.Double(2.5, 6, 14, 6));
                g.draw(new Line2D.Double(2, 10.5, 13.5, 10.5));
            }
            case TAG -> {                             // nom
                Path2D p = new Path2D.Double();
                p.moveTo(2, 3);
                p.lineTo(8.5, 3);
                p.lineTo(14, 8.5);
                p.lineTo(8.5, 14);
                p.lineTo(2, 7.5);
                p.closePath();
                g.draw(p);
                g.fill(new Ellipse2D.Double(4.2, 4.6, 2.4, 2.4));
            }
            case LEAF -> {                            // nature
                Path2D p = new Path2D.Double();
                p.moveTo(2.5, 13.5);
                p.curveTo(2, 5, 7, 2, 14, 2);
                p.curveTo(14, 9, 10, 14, 2.5, 13.5);
                p.closePath();
                g.draw(p);
                g.draw(new Line2D.Double(2.5, 13.5, 10, 6));
            }
            case STAR -> g.fill(star(8, 8.5, 7, 3));     // talent
            case PERSON -> {                          // dresseur d'origine
                g.fill(new Ellipse2D.Double(5, 1.5, 6, 6));
                g.fill(new Arc2D.Double(2, 9, 12, 11, 0, 180, Arc2D.CHORD));
            }
            case SPARK -> {                           // expérience
                g.fill(star(8, 8, 7, 1.6));
                g.fill(star(13, 3, 2.6, 0.7));
            }
            case ARROW_UP -> {                        // prochain niveau
                g.draw(new Line2D.Double(8, 14, 8, 3));
                g.draw(new Line2D.Double(3.5, 7.5, 8, 3));
                g.draw(new Line2D.Double(12.5, 7.5, 8, 3));
            }
            case BAG -> {                             // objet tenu
                g.draw(new RoundRectangle2D.Double(2, 5.5, 12, 9, 3, 3));
                g.draw(new Arc2D.Double(5, 2, 6, 7, 0, 180, Arc2D.OPEN));
                g.draw(new Line2D.Double(2, 9.5, 14, 9.5));
            }
            case SPEAKER, MUTE -> {                   // volume / son coupé
                Path2D p = new Path2D.Double();
                p.moveTo(1.5, 6);
                p.lineTo(4.5, 6);
                p.lineTo(8.5, 2.5);
                p.lineTo(8.5, 13.5);
                p.lineTo(4.5, 10);
                p.lineTo(1.5, 10);
                p.closePath();
                g.fill(p);
                if (icon == Icon.SPEAKER) {
                    g.draw(new Arc2D.Double(6, 5, 5, 6, -55, 110, Arc2D.OPEN));
                    g.draw(new Arc2D.Double(5.5, 2.5, 9, 11, -55, 110, Arc2D.OPEN));
                } else {
                    g.draw(new Line2D.Double(10.5, 5.5, 14.5, 10.5));
                    g.draw(new Line2D.Double(14.5, 5.5, 10.5, 10.5));
                }
            }
            case NOTE -> {                            // musique
                g.fill(new Ellipse2D.Double(1.5, 10.5, 5, 4));
                g.fill(new Ellipse2D.Double(9, 8.5, 5, 4));
                g.draw(new Line2D.Double(6.2, 12.3, 6.2, 3));
                g.draw(new Line2D.Double(13.7, 10.3, 13.7, 1.5));
                g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(6.2, 3, 13.7, 1.5));
            }
            case GEAR -> {                            // réglages
                g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45);
                    g.draw(new Line2D.Double(8 + 5 * Math.cos(a), 8 + 5 * Math.sin(a), 8 + 6.6 * Math.cos(a), 8 + 6.6 * Math.sin(a)));
                }
                g.setStroke(new BasicStroke(1.8f));
                g.draw(new Ellipse2D.Double(3.4, 3.4, 9.2, 9.2));
                g.fill(new Ellipse2D.Double(6.2, 6.2, 3.6, 3.6));
            }
            case SCREEN -> {                          // affichage
                g.draw(new RoundRectangle2D.Double(1.5, 2.5, 13, 9, 2.5, 2.5));
                g.draw(new Line2D.Double(8, 11.5, 8, 14));
                g.draw(new Line2D.Double(5, 14, 11, 14));
            }
            case DEX -> {                             // Pokédex : boîtier avec un gros voyant
                g.draw(new RoundRectangle2D.Double(2.5, 1.5, 11, 13, 3, 3));
                g.fill(new Ellipse2D.Double(4.5, 3.5, 5, 5));
                g.fill(new Ellipse2D.Double(10.3, 3.8, 1.8, 1.8));
                g.draw(new Line2D.Double(5, 11.5, 11, 11.5));
            }
            case MENU -> {                            // menu
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(2.5, 4, 13.5, 4));
                g.draw(new Line2D.Double(2.5, 8, 13.5, 8));
                g.draw(new Line2D.Double(2.5, 12, 13.5, 12));
            }
            case HEART -> {                           // PV
                Path2D p = new Path2D.Double();
                p.moveTo(8, 14);
                p.curveTo(1, 9, 0.5, 4, 4.5, 2.5);
                p.curveTo(6.5, 2, 7.6, 3, 8, 4.4);
                p.curveTo(8.4, 3, 9.5, 2, 11.5, 2.5);
                p.curveTo(15.5, 4, 15, 9, 8, 14);
                p.closePath();
                g.fill(p);
            }
        }
        g.dispose();
    }

    /** Hexagone régulier (pointe en haut). */
    public static Path2D hexagon(double cx, double cy, double r) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(-90 + i * 60);
            if (i == 0) p.moveTo(cx + r * Math.cos(a), cy + r * Math.sin(a));
            else p.lineTo(cx + r * Math.cos(a), cy + r * Math.sin(a));
        }
        p.closePath();
        return p;
    }

    private static Path2D star(double cx, double cy, double outer, double inner) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double r = i % 2 == 0 ? outer : inner, a = Math.toRadians(-90 + i * 36);
            if (i == 0) p.moveTo(cx + r * Math.cos(a), cy + r * Math.sin(a));
            else p.lineTo(cx + r * Math.cos(a), cy + r * Math.sin(a));
        }
        p.closePath();
        return p;
    }

    /** Petite bulle d'aide (au survol d'une icône). */
    public static void tooltip(Graphics2D g, String text, double cx, double top, double scale) {
        g.setFont(HiUi.font((float) (11.5 * scale), true));
        double w = g.getFontMetrics().stringWidth(text) + 14 * scale, h = 22 * scale;
        double x = cx - w / 2;
        HiUi.fill(g, new Color(0x101119), x, top, w, h, 5 * scale);
        g.setColor(HiUi.TEXT);
        HiUi.centered(g, text, x, top, w, h);
    }
}
