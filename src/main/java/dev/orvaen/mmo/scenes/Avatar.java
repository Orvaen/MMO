package dev.orvaen.mmo.scenes;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Avatar du joueur, dessiné entièrement par le moteur (aucun sprite de la ROM).
 * Les autres joueurs du futur mode en ligne utiliseront le même dessin avec leurs couleurs.
 */
public final class Avatar {

    private static final Color OUTLINE = new Color(0x282830);
    private static final Color SKIN = new Color(0xF0C8A0);
    private static final Color PANTS = new Color(0x34405C);
    private static final Color SHOES = new Color(0x4A3A30);

    private final Color cap;
    private final Color shirt;

    public Avatar(Color cap, Color shirt) {
        this.cap = cap;
        this.shirt = shirt;
    }

    public static Avatar defaultPlayer() {
        return new Avatar(new Color(0x2A9D8F), new Color(0xE76F51));
    }

    /**
     * Dessine l'avatar (16x20) avec les pieds en bas de la case (x, y) de 16x16.
     * @param step 0 = immobile, 1 / 2 = jambe gauche / droite en avant
     */
    public void draw(Graphics2D g, int x, int y, Direction facing, int step, int hop) {
        int top = y - 4 - hop;
        // ombre au sol
        g.setColor(new Color(0, 0, 0, 70));
        g.fillOval(x + 3, y + 12, 10, 4);

        // jambes
        int left = step == 1 ? -1 : 0, right = step == 2 ? -1 : 0;
        g.setColor(PANTS);
        g.fillRect(x + 5, top + 14 + left, 3, 4);
        g.fillRect(x + 9, top + 14 + right, 3, 4);
        g.setColor(SHOES);
        g.fillRect(x + 5, top + 18 + left, 3, 2);
        g.fillRect(x + 9, top + 18 + right, 3, 2);

        // buste
        g.setColor(OUTLINE);
        g.fillRoundRect(x + 3, top + 8, 11, 8, 3, 3);
        g.setColor(shirt);
        g.fillRoundRect(x + 4, top + 9, 9, 6, 2, 2);
        // bras
        g.setColor(SKIN);
        if (facing == Direction.LEFT || facing == Direction.RIGHT) {
            int ax = facing == Direction.LEFT ? x + 6 + (step == 0 ? 0 : step == 1 ? -1 : 1) : x + 8 - (step == 0 ? 0 : step == 1 ? -1 : 1);
            g.fillRect(ax, top + 11, 3, 4);
        } else {
            g.fillRect(x + 2, top + 10 + (step == 1 ? 1 : 0), 2, 4);
            g.fillRect(x + 13, top + 10 + (step == 2 ? 1 : 0), 2, 4);
        }

        // tête
        g.setColor(OUTLINE);
        g.fillOval(x + 3, top, 11, 10);
        g.setColor(SKIN);
        g.fillOval(x + 4, top + 1, 9, 8);

        // casquette
        g.setColor(cap);
        g.fillArc(x + 3, top - 1, 11, 9, 0, 180);
        g.setColor(cap.darker());
        switch (facing) {
            case DOWN -> g.fillRect(x + 4, top + 3, 9, 2);
            case UP -> g.fillArc(x + 3, top - 1, 11, 11, 0, 360);
            case LEFT -> g.fillRect(x + 1, top + 3, 6, 2);
            case RIGHT -> g.fillRect(x + 10, top + 3, 6, 2);
        }

        // yeux
        g.setColor(OUTLINE);
        switch (facing) {
            case DOWN -> {
                g.fillRect(x + 6, top + 6, 1, 2);
                g.fillRect(x + 10, top + 6, 1, 2);
            }
            case LEFT -> g.fillRect(x + 5, top + 6, 1, 2);
            case RIGHT -> g.fillRect(x + 11, top + 6, 1, 2);
            case UP -> { }
        }
    }
}
