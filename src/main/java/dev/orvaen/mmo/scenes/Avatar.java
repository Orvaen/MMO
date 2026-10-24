package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.data.PlayerLook;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Avatar du joueur, dessiné entièrement par le moteur (aucun sprite de la ROM)
 * à partir de son {@link PlayerLook}. Les joueurs du futur mode en ligne utiliseront le même dessin.
 */
public final class Avatar {

    private static final Color OUTLINE = new Color(0x282830);
    private static final Color SHADOW = new Color(0, 0, 0, 70);

    private final Color skin;
    private final Color hair;
    private final Color hat;
    private final Color top;
    private final Color pants;
    private final Color shoes;
    private final int hairStyle;
    private final int hatStyle;
    private final int topStyle;

    public Avatar(PlayerLook look) {
        this.skin = new Color(look.skinRgb());
        this.hair = new Color(look.hairRgb());
        this.hat = new Color(look.hatRgb());
        this.top = new Color(look.topRgb());
        this.pants = new Color(look.pantsRgb());
        this.shoes = new Color(look.shoesRgb());
        this.hairStyle = look.hairStyle();
        this.hatStyle = look.hat();
        this.topStyle = look.top();
    }

    public static Avatar defaultPlayer() {
        return new Avatar(PlayerLook.defaults());
    }

    private boolean longHair() {
        return hairStyle == 1 || hairStyle == 2;
    }

    /**
     * Dessine l'avatar (16x20) avec les pieds en bas de la case (x, y) de 16x16.
     * @param step 0 = immobile, 1 / 2 = jambe gauche / droite en avant
     * @param hop  hauteur de saut en pixels (corniches)
     */
    public void draw(Graphics2D g, int x, int y, Direction facing, int step, int hop) {
        int t = y - 4 - hop;

        g.setColor(SHADOW);
        g.fillOval(x + 3, y + 12, 10, 4);

        // Cheveux longs derrière la tête (vue de face ou de profil)
        if (longHair() && facing != Direction.UP) {
            g.setColor(hair.darker());
            int len = hairStyle == 2 ? 14 : 10;
            g.fillRoundRect(x + 3, t + 3, 11, len - 2, 4, 4);
        }

        drawLegs(g, x, t, step);
        drawTorso(g, x, t, facing, step);

        if (longHair() && facing == Direction.UP) {
            g.setColor(hair);
            int len = hairStyle == 2 ? 14 : 10;
            g.fillRoundRect(x + 3, t + 4, 11, len - 2, 4, 4);
        }

        // Tête
        g.setColor(OUTLINE);
        g.fillOval(x + 3, t, 11, 10);
        g.setColor(skin);
        g.fillOval(x + 4, t + 1, 9, 8);

        drawHair(g, x, t, facing);
        drawHat(g, x, t, facing);
        drawEyes(g, x, t, facing);
    }

    private void drawLegs(Graphics2D g, int x, int t, int step) {
        int left = step == 1 ? -1 : 0, right = step == 2 ? -1 : 0;
        g.setColor(pants);
        g.fillRect(x + 5, t + 14 + left, 3, 4);
        g.fillRect(x + 9, t + 14 + right, 3, 4);
        g.setColor(shoes);
        g.fillRect(x + 5, t + 18 + left, 3, 2);
        g.fillRect(x + 9, t + 18 + right, 3, 2);
        g.setColor(OUTLINE);
        g.fillRect(x + 5, t + 19 + left, 3, 1);
        g.fillRect(x + 9, t + 19 + right, 3, 1);
    }

    private void drawTorso(Graphics2D g, int x, int t, Direction facing, int step) {
        boolean tank = topStyle == 3;
        g.setColor(OUTLINE);
        g.fillRoundRect(x + 3, t + 8, 11, 8, 3, 3);
        g.setColor(top);
        g.fillRoundRect(x + 4, t + 9, 9, 6, 2, 2);
        if (tank) {
            g.setColor(skin);
            g.fillRect(x + 4, t + 9, 2, 2);
            g.fillRect(x + 11, t + 9, 2, 2);
        }
        if (topStyle == 1 && facing == Direction.DOWN) { // veste ouverte
            g.setColor(new Color(0xF1FAEE));
            g.fillRect(x + 7, t + 9, 3, 6);
        }
        if (topStyle == 2) { // sweat : col et poche
            g.setColor(top.darker());
            g.fillRect(x + 5, t + 9, 7, 1);
            if (facing == Direction.DOWN) g.fillRect(x + 6, t + 12, 5, 2);
        }

        // Bras : manches de la couleur du haut, sauf t-shirt court et débardeur
        Color sleeve = (topStyle == 1 || topStyle == 2) ? top : skin;
        Color hand = skin;
        if (facing == Direction.LEFT || facing == Direction.RIGHT) {
            int swing = step == 0 ? 0 : step == 1 ? -1 : 1;
            int ax = facing == Direction.LEFT ? x + 6 + swing : x + 8 - swing;
            g.setColor(sleeve);
            g.fillRect(ax, t + 10, 3, 3);
            g.setColor(hand);
            g.fillRect(ax, t + 13, 3, 2);
        } else {
            int l = step == 1 ? 1 : 0, r = step == 2 ? 1 : 0;
            g.setColor(sleeve);
            g.fillRect(x + 2, t + 9 + l, 2, 3);
            g.fillRect(x + 13, t + 9 + r, 2, 3);
            g.setColor(hand);
            g.fillRect(x + 2, t + 12 + l, 2, 2);
            g.fillRect(x + 13, t + 12 + r, 2, 2);
        }
    }

    private void drawHair(Graphics2D g, int x, int t, Direction facing) {
        if (hairStyle == 5) { // rasés : simple ombre sur le crâne
            g.setColor(blend(hair, skin));
            g.fillArc(x + 4, t + 1, 9, 6, 0, 180);
            return;
        }
        g.setColor(hair);
        if (facing == Direction.UP) {
            g.fillOval(x + 3, t, 11, 9);
        } else {
            g.fillArc(x + 3, t - 1, 11, 9, 0, 180);
            switch (facing) {
                case DOWN -> {
                    g.fillRect(x + 4, t + 3, 9, 1);              // frange
                    g.fillRect(x + 3, t + 3, 2, longHair() ? 6 : 3); // mèches latérales
                    g.fillRect(x + 12, t + 3, 2, longHair() ? 6 : 3);
                }
                case LEFT -> g.fillRect(x + 8, t + 2, 6, longHair() ? 7 : 4);
                case RIGHT -> g.fillRect(x + 3, t + 2, 6, longHair() ? 7 : 4);
                default -> { }
            }
        }
        if (hairStyle == 4) { // pics
            int[] xs = {x + 3, x + 5, x + 7, x + 9, x + 11, x + 14};
            for (int i = 0; i < 4; i++) {
                g.fillPolygon(new int[]{xs[i] + 1, xs[i] + 3, xs[i] + 5}, new int[]{t + 2, t - 3, t + 2}, 3);
            }
        }
        if (hairStyle == 3) { // queue de cheval
            switch (facing) {
                case LEFT -> g.fillRoundRect(x + 13, t + 2, 3, 8, 2, 2);
                case RIGHT -> g.fillRoundRect(x, t + 2, 3, 8, 2, 2);
                case UP -> g.fillRoundRect(x + 7, t + 6, 3, 8, 2, 2);
                case DOWN -> g.fillRect(x + 13, t + 2, 2, 3);
            }
        }
    }

    private void drawHat(Graphics2D g, int x, int t, Direction facing) {
        Color dark = hat.darker();
        switch (hatStyle) {
            case 0 -> { // casquette
                g.setColor(hat);
                g.fillArc(x + 3, t - 1, 11, 9, 0, 180);
                g.setColor(dark);
                switch (facing) {
                    case DOWN -> g.fillRect(x + 4, t + 3, 9, 2);
                    case UP -> g.fillArc(x + 3, t - 1, 11, 11, 0, 360);
                    case LEFT -> g.fillRect(x + 1, t + 3, 6, 2);
                    case RIGHT -> g.fillRect(x + 10, t + 3, 6, 2);
                }
                if (facing == Direction.UP) {
                    g.setColor(hat);
                    g.fillArc(x + 4, t, 9, 7, 0, 180);
                }
            }
            case 1 -> { // bonnet à pompon
                g.setColor(hat);
                g.fillArc(x + 3, t - 2, 11, 11, 0, 180);
                g.fillRect(x + 3, t + 2, 11, 2);
                g.setColor(dark);
                g.fillRect(x + 3, t + 3, 11, 2);
                g.setColor(new Color(0xF1FAEE));
                g.fillOval(x + 7, t - 4, 3, 3);
            }
            case 2 -> { // bandana noué derrière
                g.setColor(hat);
                g.fillRect(x + 3, t + 2, 11, 2);
                g.setColor(dark);
                switch (facing) {
                    case LEFT -> g.fillRect(x + 13, t + 2, 3, 3);
                    case RIGHT -> g.fillRect(x, t + 2, 3, 3);
                    case UP -> g.fillRect(x + 7, t + 3, 3, 3);
                    case DOWN -> { }
                }
            }
            case 3 -> { // chapeau à larges bords
                g.setColor(hat);
                g.fillRoundRect(x + 4, t - 3, 9, 6, 3, 3);
                g.setColor(dark);
                g.fillRect(x + 4, t + 1, 9, 1);
                g.setColor(hat);
                g.fillRoundRect(x, t + 2, 17, 2, 2, 2);
            }
            default -> { } // tête nue
        }
    }

    private void drawEyes(Graphics2D g, int x, int t, Direction facing) {
        g.setColor(OUTLINE);
        switch (facing) {
            case DOWN -> {
                g.fillRect(x + 6, t + 6, 1, 2);
                g.fillRect(x + 10, t + 6, 1, 2);
            }
            case LEFT -> g.fillRect(x + 5, t + 6, 1, 2);
            case RIGHT -> g.fillRect(x + 11, t + 6, 1, 2);
            case UP -> { }
        }
    }

    private static Color blend(Color a, Color b) {
        return new Color((a.getRed() + b.getRed()) / 2, (a.getGreen() + b.getGreen()) / 2, (a.getBlue() + b.getBlue()) / 2);
    }
}
