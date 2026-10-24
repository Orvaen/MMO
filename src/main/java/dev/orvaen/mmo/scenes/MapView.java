package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.data.GameData;
import dev.orvaen.mmo.rom.RomException;
import dev.orvaen.mmo.world.Connection;
import dev.orvaen.mmo.world.GameMap;
import dev.orvaen.mmo.world.MapLayout;
import dev.orvaen.mmo.world.MapRenderer;
import dev.orvaen.mmo.world.TileAnims;
import dev.orvaen.mmo.world.Tileset;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ce qu'on voit du monde : la carte courante, les cartes voisines (liaisons ville ↔ route) et la bordure.
 * <p>
 * Chaque carte est dessinée une fois en deux images (le sol, et le dessus : toits, cimes) puis gardée
 * en mémoire ; les tuiles animées (eau, fleurs) ne redessinent que les cases qui les utilisent.
 * <p>
 * Les cartes voisines sont placées dans le repère de la carte courante : la case (0, 0) de la carte
 * courante est l'origine, une carte au nord a donc des coordonnées y négatives.
 */
final class MapView {

    private static final int T = MapRenderer.TILE;
    private static final int CACHE_SIZE = 12;

    /** Une case d'une carte voisine. */
    record Located(GameMap map, int x, int y) {}

    private final GameData data;
    /** Images des cartes déjà dessinées (les plus anciennes sont oubliées au-delà de 12). */
    private final Map<String, MapRenderer.Rendered> renders = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, MapRenderer.Rendered> eldest) {
            return size() > CACHE_SIZE;
        }
    };
    /** Cases qui utilisent chaque tuile animée (calculées une fois par carte). */
    private final Map<String, int[]> animatedCells = new HashMap<>();

    MapView(GameData data) {
        this.data = data;
    }

    /** Images de la carte (dessinées au premier appel). */
    MapRenderer.Rendered render(GameMap m) {
        return renders.computeIfAbsent(m.key(), k -> data.renderer.render(m));
    }

    /** Oublie l'image d'une carte (ses métatiles ont changé). */
    void forget(GameMap m) {
        renders.remove(m.key());
    }

    /** Dessine à l'avance la carte et ses voisines. */
    void preload(GameMap m) {
        render(m);
        for (Connection c : m.connections) {
            GameMap t = loadOrNull(c.targetGroup(), c.targetNum());
            if (t != null) render(t);
        }
        animatedCells.clear();
    }

    /** Redessine quelques cases de la carte (métatile changé par un script). */
    void redraw(GameMap m, int[] cells) {
        data.renderer.redraw(m.layout, render(m), cells);
    }

    // ------------------------------------------------------------------ cartes voisines

    GameMap loadOrNull(int group, int num) {
        try {
            if (!data.maps.exists(group, num)) return null;
            return data.maps.load(group, num);
        } catch (RomException e) {
            return null;
        }
    }

    /** Origine (en cases) d'une carte voisine, dans le repère de la carte courante. */
    private static int[] originOf(GameMap current, Connection c, GameMap target) {
        return switch (c.direction()) {
            case SOUTH -> new int[]{c.offset(), current.height()};
            case NORTH -> new int[]{c.offset(), -target.height()};
            case WEST -> new int[]{-target.width(), c.offset()};
            case EAST -> new int[]{current.width(), c.offset()};
        };
    }

    /** La case (x, y), hors de la carte courante, appartient-elle à une carte voisine ? Sinon null. */
    Located locate(GameMap current, int x, int y) {
        for (Connection c : current.connections) {
            GameMap t = loadOrNull(c.targetGroup(), c.targetNum());
            if (t == null) continue;
            int[] o = originOf(current, c, t);
            int lx = x - o[0], ly = y - o[1];
            if (t.layout.inBounds(lx, ly)) return new Located(t, lx, ly);
        }
        return null;
    }

    // ------------------------------------------------------------------ tuiles animées

    /** Eau, fleurs… : recopie les images dues et redessine les cases concernées des cartes affichées. */
    void animateTiles(GameMap current) {
        List<TileAnims.Step> steps = data.tileAnims.tick();
        if (steps.isEmpty()) return;
        List<GameMap> shown = new ArrayList<>();
        shown.add(current);
        for (Connection c : current.connections) {
            GameMap t = loadOrNull(c.targetGroup(), c.targetNum());
            if (t != null) shown.add(t);
        }
        for (TileAnims.Step step : steps) {
            TileAnims.Anim a = step.anim();
            Set<Tileset> done = Collections.newSetFromMap(new IdentityHashMap<>());
            for (GameMap m : shown) {
                Tileset ts = m.layout.primary;
                if (!data.tileAnims.appliesTo(ts, a)) continue;
                if (done.add(ts)) data.tileAnims.apply(ts, step);
                int[] cells = animatedCells.computeIfAbsent(m.key() + "#" + a.dest(),
                        k -> data.renderer.cellsUsing(m.layout, a.dest(), a.dest() + a.tiles()));
                if (cells.length > 0) data.renderer.redraw(m.layout, render(m), cells);
            }
        }
    }

    // ------------------------------------------------------------------ dessin

    /**
     * Le sol : bordure répétée autour de la carte, cartes voisines, puis carte courante.
     * {@code camX}/{@code camY} : coin haut-gauche de l'écran, en pixels du monde.
     */
    void drawGround(Graphics2D g, GameMap map, int camX, int camY, int screenW, int screenH) {
        MapRenderer.Rendered current = render(map);

        // 1. Bordure répétée là où il n'y a ni la carte, ni une carte voisine.
        //    En intérieur, le vide autour de la pièce reste noir (comme PokeMMO).
        if (map.mapType != GameMap.TYPE_INDOOR && current.borderBelow().length > 0) {
            MapLayout layout = map.layout;
            int firstTx = Math.floorDiv(camX, T), firstTy = Math.floorDiv(camY, T);
            int lastTx = Math.floorDiv(camX + screenW, T), lastTy = Math.floorDiv(camY + screenH, T);
            for (int ty = firstTy; ty <= lastTy; ty++) {
                for (int tx = firstTx; tx <= lastTx; tx++) {
                    if (layout.inBounds(tx, ty) || locate(map, tx, ty) != null) continue;
                    int i = Math.floorMod(ty, layout.borderHeight) * layout.borderWidth
                            + Math.floorMod(tx, layout.borderWidth);
                    if (i < current.borderBelow().length) {
                        g.drawImage(current.borderBelow()[i], tx * T - camX, ty * T - camY, null);
                        g.drawImage(current.borderAbove()[i], tx * T - camX, ty * T - camY, null);
                    }
                }
            }
        }

        // 2. Cartes voisines puis carte courante
        for (Connection c : map.connections) {
            GameMap t = loadOrNull(c.targetGroup(), c.targetNum());
            if (t == null) continue;
            int[] o = originOf(map, c, t);
            g.drawImage(render(t).below(), o[0] * T - camX, o[1] * T - camY, null);
        }
        g.drawImage(current.below(), -camX, -camY, null);
    }

    /** Le dessus (toits, cimes des arbres) : dessiné après les personnages. {@code g} est déjà dans le repère du monde. */
    void drawAbove(Graphics2D g, GameMap map) {
        for (Connection c : map.connections) {
            GameMap t = loadOrNull(c.targetGroup(), c.targetNum());
            if (t == null) continue;
            int[] o = originOf(map, c, t);
            g.drawImage(render(t).above(), o[0] * T, o[1] * T, null);
        }
        g.drawImage(render(map).above(), 0, 0, null);
    }
}
