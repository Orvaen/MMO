package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.world.FieldEffects;
import dev.orvaen.mmo.world.GameMap;
import dev.orvaen.mmo.world.MapRenderer;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * L'herbe haute qui s'agite quand quelqu'un y entre (joueur ou PNJ), avec les images de la ROM.
 * <p>
 * Comme sur GBA : au début du pas vers une case d'herbe haute, un petit sprite apparaît sur cette case
 * et joue son animation (images 1 → 4, 10 frames chacune), puis reste au repos (image 0) tant que
 * quelqu'un se tient dans l'herbe. Il est dessiné par-dessus le personnage : l'herbe cache ses pieds.
 */
final class GrassEffects {

    private static final int T = MapRenderer.TILE;
    private static final int TALL_GRASS = 0x02;
    private static final int ANIM_FRAMES = 50;

    private static final class Rustle {
        final int x, y;
        int age;

        Rustle(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private final BufferedImage[] frames;
    private final List<Rustle> active = new ArrayList<>();

    GrassEffects(FieldEffects effects) {
        this.frames = effects.tallGrass;
    }

    /** Oublie tout (changement de carte). */
    void clear() {
        active.clear();
    }

    /**
     * Une frame : nouveaux pas dans l'herbe (joueur et PNJ), animation, et disparition une fois
     * la case libérée.
     */
    void update(GameMap map, OverworldPlayer player, List<Npc> npcs) {
        if (frames == null) return;
        if (player.moving && player.distance == T) {
            enter(map, player.x + player.facing.dx, player.y + player.facing.dy);
        }
        for (Npc n : npcs) {
            if (n.isMoving()) enter(map, n.x() + n.direction().dx, n.y() + n.direction().dy);
        }
        for (Iterator<Rustle> it = active.iterator(); it.hasNext(); ) {
            Rustle r = it.next();
            r.age++;
            if (r.age >= ANIM_FRAMES && !occupied(r.x, r.y, player, npcs)) it.remove();
        }
    }

    private void enter(GameMap map, int x, int y) {
        if (!map.layout.inBounds(x, y) || map.behaviorAt(x, y) != TALL_GRASS) return;
        for (Rustle r : active) if (r.x == x && r.y == y) return;
        active.add(new Rustle(x, y));
    }

    private static boolean occupied(int x, int y, OverworldPlayer p, List<Npc> npcs) {
        if (p.x == x && p.y == y) return true;
        if (p.moving && p.x + p.facing.dx == x && p.y + p.facing.dy == y) return true;
        for (Npc n : npcs) if (n.occupies(x, y)) return true;
        return false;
    }

    /** Dessine l'herbe animée ({@code originX/Y} : case (0,0) de la carte dans la fenêtre). */
    void draw(Graphics2D g, double originX, double originY, int zoom) {
        if (frames == null || active.isEmpty()) return;
        Graphics2D q = (Graphics2D) g.create();
        q.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (Rustle r : active) {
            BufferedImage img = frames[FieldEffects.tallGrassFrame(r.age)];
            int x = (int) Math.round(originX + r.x * T * zoom), y = (int) Math.round(originY + r.y * T * zoom);
            q.drawImage(img, x, y, T * zoom, T * zoom, null);
        }
        q.dispose();
    }

    /** Pour les tests : nombre d'herbes animées. */
    int count() {
        return active.size();
    }
}
