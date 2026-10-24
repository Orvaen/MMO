package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.data.GameData;
import dev.orvaen.mmo.world.DoorAnims;
import dev.orvaen.mmo.world.GameMap;
import dev.orvaen.mmo.world.MapRenderer;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Une porte qui s'ouvre ou se ferme : quelle porte, où, et quelle image afficher.
 * <p>
 * Une porte a 3 images (entrouverte, ouverte, grande ouverte) ; -1 veut dire « fermée » (on voit alors
 * la carte normale). Chaque image reste {@link DoorAnims#STAGE_FRAMES} frames, comme sur GBA.
 */
final class DoorAnimator {

    private static final int T = MapRenderer.TILE;
    private static final int[] OPENING = {-1, 0, 1, 2};
    private static final int[] CLOSING = {2, 1, 0, -1};

    private DoorAnims.Door door;        // null : aucune porte animée
    private int x, y;                   // case de la porte
    private int[] sequence = {};
    private int tick;
    private int stage = -1;             // image affichée

    /** Lance l'ouverture ou la fermeture de la porte {@code d} située en (x, y). */
    void play(DoorAnims.Door d, int x, int y, boolean open) {
        this.door = d;
        this.x = x;
        this.y = y;
        sequence = open ? OPENING : CLOSING;
        tick = 0;
        stage = sequence[0];
    }

    /** Recommence l'animation de la même porte dans l'autre sens. */
    void play(boolean open) {
        play(door, x, y, open);
    }

    /** Porte déjà grande ouverte (arrivée par une porte : on en sort). */
    void showOpen(DoorAnims.Door d, int x, int y) {
        play(d, x, y, true);
        tick = sequence.length * DoorAnims.STAGE_FRAMES;
        stage = 2;
    }

    /** Une frame d'animation. */
    void update() {
        if (done()) return;
        stage = sequence[Math.min(sequence.length - 1, tick / DoorAnims.STAGE_FRAMES)];
        tick++;
    }

    /** Vrai quand l'animation est finie (la dernière image reste affichée). */
    boolean done() {
        return tick >= sequence.length * DoorAnims.STAGE_FRAMES;
    }

    /** Vrai si une porte est en train de bouger ou reste ouverte. */
    boolean active() {
        return door != null;
    }

    /** Plus de porte animée : la carte normale se voit de nouveau. */
    void clear() {
        door = null;
        stage = -1;
    }

    /** Image affichée (-1 : aucune). */
    int stage() {
        return door == null ? -1 : stage;
    }

    /** Dessine la porte par-dessus le sol (et le métatile du dessus si elle fait deux cases de haut). */
    void draw(Graphics2D g, GameData data, GameMap map, int camX, int camY) {
        if (door == null || stage < 0) return;
        BufferedImage[] frames = data.doors.frames(door, map.layout, data.renderer);
        int up = DoorAnims.heightInTiles(door) - 1;
        g.drawImage(frames[stage], x * T - camX, (y - up) * T - camY, null);
    }
}
