package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.world.MapRenderer;
import dev.orvaen.mmo.world.ObjectEvent;
import dev.orvaen.mmo.world.ObjectGraphics;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiPredicate;

/**
 * Un personnage non joueur : position, orientation et comportement tirés de la ROM
 * (regarder autour de lui, errer dans sa zone, rester tourné vers une direction…).
 * Il marche à la vitesse GBA (16 frames par case) avec les mêmes pas que le joueur.
 */
public final class Npc {

    private static final int T = MapRenderer.TILE;
    private static final Direction[] ALL = Direction.values();

    public final ObjectEvent event;
    private final ObjectGraphics.Sprite sprite;
    private final Random rng;
    private final Direction[] directions;   // orientations possibles
    private final boolean wanders;
    private final Map<Integer, BufferedImage> flipped = new HashMap<>();

    private int x, y;
    private Direction facing;
    private boolean moving;
    private int progress;
    private boolean leftFoot;
    private int wait;
    private int rotation;
    private List<String> dialogue;

    public Npc(ObjectEvent event, ObjectGraphics.Sprite sprite, long seed) {
        this.event = event;
        this.sprite = sprite;
        this.rng = new Random(seed);
        this.x = event.x();
        this.y = event.y();
        this.directions = directionsFor(event.movementType());
        this.wanders = event.movementType() >= ObjectEvent.MOVE_WANDER
                && event.movementType() <= ObjectEvent.MOVE_WANDER_RL && sprite != null && sprite.walks();
        this.facing = initialFacing(event.movementType(), directions);
        this.wait = 30 + rng.nextInt(90);
    }

    private static Direction[] directionsFor(int type) {
        return switch (type) {
            case ObjectEvent.MOVE_WANDER_UD, ObjectEvent.MOVE_WANDER_DU, 0x0D -> new Direction[]{Direction.DOWN, Direction.UP};
            case ObjectEvent.MOVE_WANDER_LR, ObjectEvent.MOVE_WANDER_RL, 0x0E -> new Direction[]{Direction.LEFT, Direction.RIGHT};
            case ObjectEvent.MOVE_FACE_UP -> new Direction[]{Direction.UP};
            case ObjectEvent.MOVE_FACE_DOWN -> new Direction[]{Direction.DOWN};
            case ObjectEvent.MOVE_FACE_LEFT -> new Direction[]{Direction.LEFT};
            case ObjectEvent.MOVE_FACE_RIGHT -> new Direction[]{Direction.RIGHT};
            case 0x0F -> new Direction[]{Direction.UP, Direction.LEFT};
            case 0x10 -> new Direction[]{Direction.UP, Direction.RIGHT};
            case 0x11 -> new Direction[]{Direction.DOWN, Direction.LEFT};
            case 0x12 -> new Direction[]{Direction.DOWN, Direction.RIGHT};
            case 0x13 -> new Direction[]{Direction.DOWN, Direction.UP, Direction.LEFT};
            case 0x14 -> new Direction[]{Direction.DOWN, Direction.UP, Direction.RIGHT};
            case 0x15 -> new Direction[]{Direction.UP, Direction.LEFT, Direction.RIGHT};
            case 0x16 -> new Direction[]{Direction.DOWN, Direction.LEFT, Direction.RIGHT};
            case ObjectEvent.MOVE_LOOK_AROUND, ObjectEvent.MOVE_WANDER, 0x17, 0x18 -> ALL;
            default -> new Direction[]{Direction.DOWN};
        };
    }

    private static Direction initialFacing(int type, Direction[] dirs) {
        if (type == ObjectEvent.MOVE_WANDER_DU) return Direction.DOWN;
        if (type == ObjectEvent.MOVE_WANDER_RL) return Direction.RIGHT;
        if (type == ObjectEvent.MOVE_WANDER_UD) return Direction.UP;
        if (type == ObjectEvent.MOVE_WANDER_LR) return Direction.LEFT;
        return dirs[0];
    }

    public int x() { return x; }
    public int y() { return y; }
    public Direction facing() { return facing; }
    public boolean isMoving() { return moving; }

    /** Case occupée (les deux pendant un pas, comme sur GBA). */
    public boolean occupies(int tx, int ty) {
        if (tx == x && ty == y) return true;
        return moving && tx == x + facing.dx && ty == y + facing.dy;
    }

    /**
     * Une frame de logique. {@code free} dit si une case est libre (carte, joueur, autres PNJ) ;
     * {@code frozen} : le joueur lui parle, il ne bouge plus.
     */
    public void update(BiPredicate<Integer, Integer> free, boolean frozen) {
        if (moving) {
            progress++;
            if (progress >= T) {
                moving = false;
                x += facing.dx;
                y += facing.dy;
            }
            return;
        }
        if (frozen || directions.length == 1 && !wanders) return;
        if (--wait > 0) return;
        wait = 32 * (1 + rng.nextInt(4)); // comme sur GBA : 32, 64, 96 ou 128 frames
        int type = event.movementType();
        if (type == 0x17 || type == 0x18) { // rotation
            rotation = (rotation + (type == 0x18 ? 1 : 3)) % 4;
            facing = new Direction[]{Direction.DOWN, Direction.LEFT, Direction.UP, Direction.RIGHT}[rotation];
            return;
        }
        Direction d = directions[rng.nextInt(directions.length)];
        facing = d;
        if (!wanders) return;
        int tx = x + d.dx, ty = y + d.dy;
        int rx = event.rangeX(), ry = event.rangeY();
        boolean inRange = (rx == 0 || Math.abs(tx - event.x()) <= rx) && (ry == 0 || Math.abs(ty - event.y()) <= ry);
        if (inRange && free.test(tx, ty)) {
            moving = true;
            progress = 0;
            leftFoot = !leftFoot;
        }
    }

    /** Se tourne vers le joueur (on lui parle). */
    public void face(Direction d) {
        if (!moving) facing = d;
    }

    /** Position exacte en pixels du monde (fraction de frame comprise). */
    public double exactX(double interpolation) {
        return x * T + facing.dx * exactProgress(interpolation);
    }

    public double exactY(double interpolation) {
        return y * T + facing.dy * exactProgress(interpolation);
    }

    private double exactProgress(double interpolation) {
        return moving ? Math.min(T, progress + interpolation) : 0;
    }

    public boolean visible() {
        return sprite != null;
    }

    public int width() { return sprite.width(); }
    public int height() { return sprite.height(); }

    /** Frame à dessiner (pas pendant la 1re moitié de la case, comme le joueur). */
    public BufferedImage frame() {
        BufferedImage[] f = sprite.frames();
        if (f.length < 9) return f[0];
        int base = switch (facing) {
            case DOWN -> 0;
            case UP -> 1;
            case LEFT, RIGHT -> 2;
        };
        int index = moving && progress < T / 2 ? 3 + base * 2 + (leftFoot ? 0 : 1) : base;
        if (facing != Direction.RIGHT) return f[index];
        return flipped.computeIfAbsent(index, i -> {
            BufferedImage src = f[i];
            AffineTransform t = AffineTransform.getScaleInstance(-1, 1);
            t.translate(-src.getWidth(), 0);
            return new AffineTransformOp(t, AffineTransformOp.TYPE_NEAREST_NEIGHBOR).filter(src, null);
        });
    }

    public List<String> dialogue() {
        return dialogue;
    }

    public void setDialogue(List<String> pages) {
        this.dialogue = pages;
    }
}
