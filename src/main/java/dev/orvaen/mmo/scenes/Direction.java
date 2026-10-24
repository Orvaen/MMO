package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.engine.Input;

/** Les quatre directions de déplacement, dans l'ordre des sprites GBA. */
public enum Direction {
    DOWN(0, 1), UP(0, -1), LEFT(-1, 0), RIGHT(1, 0);

    public final int dx;
    public final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public static Direction fromIndex(int i) {
        Direction[] all = values();
        return i >= 0 && i < all.length ? all[i] : DOWN;
    }

    /** La direction inverse (pour qu'un PNJ se tourne vers le joueur). */
    public Direction opposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
        };
    }

    /** Direction actuellement maintenue (la plus récente gagne en cas de diagonale). */
    public static Direction held(Input in) {
        Direction best = null;
        int bestHeld = Integer.MAX_VALUE;
        for (Direction d : values()) {
            int h = in.heldFrames(d.button());
            if (h > 0 && h < bestHeld) {
                best = d;
                bestHeld = h;
            }
        }
        return best;
    }

    public Input.Button button() {
        return switch (this) {
            case DOWN -> Input.Button.DOWN;
            case UP -> Input.Button.UP;
            case LEFT -> Input.Button.LEFT;
            case RIGHT -> Input.Button.RIGHT;
        };
    }

    /** Comportements « tapis flèche » (sortie de bâtiment) et « corniche » associés. */
    public int arrowWarpBehavior() {
        return switch (this) {
            case RIGHT -> 0x62;
            case LEFT -> 0x63;
            case UP -> 0x64;
            case DOWN -> 0x65;
        };
    }

    public int ledgeBehavior() {
        return switch (this) {
            case RIGHT -> 0x38;
            case LEFT -> 0x39;
            case UP -> 0x3A;
            case DOWN -> 0x3B;
        };
    }
}
