package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.data.WildEncounters;
import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.Input.Button;
import dev.orvaen.mmo.engine.Scene;
import dev.orvaen.mmo.engine.Ui;
import dev.orvaen.mmo.rom.RomException;
import dev.orvaen.mmo.world.Connection;
import dev.orvaen.mmo.world.GameMap;
import dev.orvaen.mmo.world.MapLayout;
import dev.orvaen.mmo.world.MapRenderer;
import dev.orvaen.mmo.world.Warp;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/** Le monde : cartes extraites de la ROM, déplacements, warps, liaisons et rencontres. */
public final class OverworldScene implements Scene {

    private static final int T = MapRenderer.TILE;
    private static final int BEHAVIOR_DOOR = 0x69;
    private static final int TURN_DELAY = 5;
    private static final int POPUP_FRAMES = 150;

    private final Game game;
    private PlayerSprite avatar;
    private final Random rng = new Random();
    private final Map<String, MapRenderer.Rendered> renders = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, MapRenderer.Rendered> eldest) {
            return size() > 12;
        }
    };

    private GameMap map;

    // Joueur
    private int px, py;                 // case actuelle
    private Direction facing = Direction.DOWN;
    private int elevation = 3;
    private boolean moving;
    private int progress;               // pixels parcourus vers la case suivante
    private int distance;               // 16 (pas) ou 32 (saut de corniche)
    private int turnTimer;
    private int animCounter;
    private boolean stepLeftFoot;

    // États divers
    private Direction autoStep;         // sortie automatique d'une porte
    private GameMap returnMap;          // pour les warps « dynamiques »
    private int returnX, returnY;
    private int stepsSinceBattle;
    private int popupTimer;
    private String message;
    private int messageTimer;

    public OverworldScene(Game game) {
        this.game = game;
        refreshAvatar();
        var save = game.save;
        var maps = game.data.maps;
        var profile = game.data.rom.profile();
        if (save.hasPosition() && maps.exists(save.group, save.num)) {
            enterMap(maps.load(save.group, save.num), save.x, save.y);
            facing = Direction.fromIndex(save.facing);
        } else {
            GameMap start = maps.exists(profile.startGroup, profile.startNum)
                    ? maps.load(profile.startGroup, profile.startNum)
                    : maps.load(0, 0); // ROM modifiée : première carte disponible
            int[] spawn = defaultSpawn(start);
            enterMap(start, spawn[0], spawn[1]);
        }
    }

    /** Reprend l'apparence de la sauvegarde (après création ou modification du personnage). */
    private void refreshAvatar() {
        avatar = new PlayerSprite(game.data.player, game.save.look);
    }

    @Override
    public void onEnter() {
        refreshAvatar();
    }

    // ------------------------------------------------------------------ logique

    /** Le monde remplit toute la fenêtre : on voit plus de carte au lieu d'agrandir davantage. */
    @Override
    public boolean fillsWindow() {
        return true;
    }

    @Override
    public void update(Input in) {
        animCounter++;
        if (popupTimer > 0) popupTimer--;
        if (messageTimer > 0) messageTimer--;

        if (moving) {
            advanceMove(in);
            return;
        }
        if (in.pressed(Button.START)) {
            game.push(new StartMenuScene(game, this));
            return;
        }
        if (in.pressed(Button.SELECT)) {
            game.push(new MapListScene(game, this));
            return;
        }
        if (autoStep != null) {
            Direction d = autoStep;
            autoStep = null;
            facing = d;
            tryMove(d, false);
            return;
        }

        Direction dir = Direction.held(in);
        if (dir == null) {
            turnTimer = 0;
            return;
        }
        if (dir != facing) {
            facing = dir;
            turnTimer = TURN_DELAY;
            return;
        }
        if (turnTimer > 0 && in.heldFrames(dir.button()) < TURN_DELAY + 2) {
            turnTimer--;
            return;
        }
        tryMove(dir, in.down(Button.B));
    }

    private void tryMove(Direction dir, boolean running) {
        // Tapis de sortie : on « pousse » vers la flèche pour changer de carte.
        Optional<Warp> here = map.warpAt(px, py);
        if (here.isPresent() && map.behaviorAt(px, py) == dir.arrowWarpBehavior()) {
            doWarp(here.get());
            return;
        }

        int tx = px + dir.dx, ty = py + dir.dy;

        // Changement de carte sans transition (ville ↔ route)
        if (!map.layout.inBounds(tx, ty)) {
            Located next = locate(tx, ty);
            if (next == null || !canEnter(next.map, next.x, next.y)) {
                bump();
                return;
            }
            int ox = next.x - tx, oy = next.y - ty; // décalage de repère entre les deux cartes
            switchMap(next.map, px + ox, py + oy);
            startMove(dir, running, T);
            return;
        }

        // Corniche : on saute par-dessus
        if (map.behaviorAt(tx, ty) == dir.ledgeBehavior()) {
            int lx = tx + dir.dx, ly = ty + dir.dy;
            if (map.layout.inBounds(lx, ly) && MapLayout.collision(map.layout.blockAt(lx, ly)) == 0) {
                startMove(dir, false, T * 2);
                return;
            }
        }

        if (canEnter(map, tx, ty)) {
            startMove(dir, running, T);
            return;
        }

        // Porte ou escalier « bloquant » : on entre directement
        Optional<Warp> front = map.warpAt(tx, ty);
        if (front.isPresent()) {
            doWarp(front.get());
            return;
        }
        bump();
    }

    private boolean canEnter(GameMap m, int x, int y) {
        int block = m.layout.blockAt(x, y);
        if (MapLayout.collision(block) != 0) return false;
        int z = MapLayout.elevation(block);
        return elevation == 0 || z == 0 || z == 15 || z == elevation;
    }

    private void startMove(Direction dir, boolean running, int dist) {
        facing = dir;
        moving = true;
        progress = 0;
        distance = dist;
        stepLeftFoot = !stepLeftFoot;
        speed = dist > T ? 2 : running ? 2 : 1;
    }

    private int speed = 1;

    private void advanceMove(Input in) {
        progress += speed;
        if (progress < distance) return;
        moving = false;
        int tiles = distance / T;
        px += facing.dx * tiles;
        py += facing.dy * tiles;
        onStep();
        // Direction toujours maintenue : on enchaîne le pas suivant dans la même frame,
        // sans image d'arrêt entre deux cases (c'est ce qui donnait l'effet saccadé).
        if (!moving && !game.isFading() && game.top() == this && autoStep == null) {
            Direction dir = Direction.held(in);
            if (dir == facing) tryMove(dir, in.down(Button.B));
        }
    }

    private void onStep() {
        int z = MapLayout.elevation(map.layout.blockAt(px, py));
        if (z != 15) elevation = z;
        stepsSinceBattle++;

        Optional<Warp> w = map.warpAt(px, py);
        if (w.isPresent() && !isArrowWarp(map.behaviorAt(px, py))) {
            doWarp(w.get());
            return;
        }
        if (map.isLandEncounterAt(px, py) && stepsSinceBattle > 3) {
            Optional<WildEncounters.Table> table = game.data.wild.land(map.group, map.num);
            if (table.isPresent() && rng.nextInt(2880) < table.get().rate() * 16) {
                WildEncounters.Slot slot = table.get().roll(rng);
                int level = slot.minLevel() + rng.nextInt(Math.max(1, slot.maxLevel() - slot.minLevel() + 1));
                stepsSinceBattle = 0;
                game.save.seen.add(slot.species());
                game.fadeThen(() -> game.push(new BattleScene(game, slot.species(), level)));
            }
        }
    }

    private static boolean isArrowWarp(int behavior) {
        return behavior >= 0x62 && behavior <= 0x65;
    }

    private void bump() {
        // Petit retour visuel : rien à faire de plus pour le prototype.
    }

    // ------------------------------------------------------------------ warps & cartes

    private void doWarp(Warp w) {
        if (game.isFading()) return;
        GameMap from = map;
        int fromX = px, fromY = py;
        game.fadeThen(() -> {
            try {
                if (w.isDynamic() || !game.data.maps.exists(w.targetGroup(), w.targetNum())) {
                    if (returnMap != null) {
                        enterMap(returnMap, returnX, returnY);
                        autoStep = Direction.DOWN;
                    }
                    return;
                }
                GameMap target = game.data.maps.load(w.targetGroup(), w.targetNum());
                if (from.mapType != GameMap.TYPE_INDOOR) {
                    returnMap = from;
                    returnX = fromX;
                    returnY = fromY;
                }
                int ax, ay;
                if (w.targetWarpId() < target.warps.size()) {
                    Warp arrival = target.warps.get(w.targetWarpId());
                    ax = arrival.x();
                    ay = arrival.y();
                } else {
                    int[] s = defaultSpawn(target);
                    ax = s[0];
                    ay = s[1];
                }
                enterMap(target, ax, ay);
                if (target.behaviorAt(ax, ay) == BEHAVIOR_DOOR || MapLayout.collision(target.layout.blockAt(ax, ay)) != 0) {
                    autoStep = Direction.DOWN;
                }
            } catch (RomException e) {
                showMessage("Warp impossible : " + e.getMessage());
            }
        });
    }

    /** Arrivée « avec écran noir » sur une carte (warp, téléportation, chargement). */
    public void enterMap(GameMap target, int x, int y) {
        boolean newPlace = map == null || !map.name.equals(target.name);
        switchMap(target, x, y);
        elevation = MapLayout.elevation(target.layout.blockAt(x, y));
        if (elevation == 15) elevation = 3;
        moving = false;
        facing = Direction.DOWN;
        if (newPlace) popupTimer = POPUP_FRAMES;
        rememberPosition();
        game.save.store(); // sauvegarde auto à chaque entrée de carte
    }

    /** Bascule de carte sans écran noir (liaisons extérieures). */
    private void switchMap(GameMap target, int x, int y) {
        boolean newPlace = map != null && !map.name.equals(target.name);
        map = target;
        px = x;
        py = y;
        if (newPlace) popupTimer = POPUP_FRAMES;
        render(target); // précharge
        for (Connection c : target.connections) {
            if (game.data.maps.exists(c.targetGroup(), c.targetNum())) {
                safeRender(c.targetGroup(), c.targetNum());
            }
        }
    }

    public void teleport(GameMap target) {
        int[] s = defaultSpawn(target);
        returnMap = null;
        enterMap(target, s[0], s[1]);
    }

    /** Point d'apparition par défaut : sous le premier warp (souvent une porte), sinon au centre. */
    private int[] defaultSpawn(GameMap m) {
        for (Warp w : m.warps) {
            int x = w.x(), y = w.y() + 1;
            if (m.layout.inBounds(x, y) && MapLayout.collision(m.layout.blockAt(x, y)) == 0) return new int[]{x, y};
        }
        int cx = m.width() / 2, cy = m.height() / 2;
        for (int r = 0; r < Math.max(m.width(), m.height()); r++) {
            for (int y = cy - r; y <= cy + r; y++) {
                for (int x = cx - r; x <= cx + r; x++) {
                    if (m.layout.inBounds(x, y) && MapLayout.collision(m.layout.blockAt(x, y)) == 0) {
                        return new int[]{x, y};
                    }
                }
            }
        }
        return new int[]{cx, cy};
    }

    public void rememberPosition() {
        var save = game.save;
        save.group = map.group;
        save.num = map.num;
        save.x = px;
        save.y = py;
        save.facing = facing.ordinal();
    }

    public void showMessage(String text) {
        message = text;
        messageTimer = 180;
    }

    public GameMap currentMap() {
        return map;
    }

    public boolean isMoving() { return moving; }
    public int playerX() { return px; }
    public int playerY() { return py; }
    public Direction facing() { return facing; }

    /** Rend les rencontres reproductibles (tests, replays). */
    public void setRandomSeed(long seed) {
        rng.setSeed(seed);
    }

    // ------------------------------------------------------------------ géométrie des liaisons

    private record Located(GameMap map, int x, int y) {}

    /** Origine (en cases) d'une carte liée, dans le repère de la carte courante. */
    private int[] originOf(Connection c, GameMap target) {
        return switch (c.direction()) {
            case SOUTH -> new int[]{c.offset(), map.height()};
            case NORTH -> new int[]{c.offset(), -target.height()};
            case WEST -> new int[]{-target.width(), c.offset()};
            case EAST -> new int[]{map.width(), c.offset()};
        };
    }

    private Located locate(int x, int y) {
        for (Connection c : map.connections) {
            GameMap t = loadOrNull(c.targetGroup(), c.targetNum());
            if (t == null) continue;
            int[] o = originOf(c, t);
            int lx = x - o[0], ly = y - o[1];
            if (t.layout.inBounds(lx, ly)) return new Located(t, lx, ly);
        }
        return null;
    }

    private GameMap loadOrNull(int group, int num) {
        try {
            return game.data.maps.load(group, num);
        } catch (RomException e) {
            return null;
        }
    }

    private MapRenderer.Rendered render(GameMap m) {
        return renders.computeIfAbsent(m.key(), k -> game.data.renderer.render(m));
    }

    private void safeRender(int group, int num) {
        GameMap m = loadOrNull(group, num);
        if (m != null) render(m);
    }

    // ------------------------------------------------------------------ rendu

    /** Position exacte du joueur en pixels du monde (interpolée entre deux pas de logique). */
    private double exactX() {
        return px * T + facing.dx * exactProgress();
    }

    private double exactY() {
        return py * T + facing.dy * exactProgress();
    }

    private double exactProgress() {
        if (!moving) return 0;
        return Math.min(distance, progress + speed * game.interpolation());
    }

    /** Coin haut-gauche de la caméra (pixels du monde, non arrondi). */
    private double camExactX() {
        return exactX() - (game.screenWidth() / 2 - T / 2);
    }

    private double camExactY() {
        return exactY() - (game.screenHeight() / 2 - T / 2);
    }

    @Override
    public double subpixelX() {
        double c = camExactX();
        return c - Math.floor(c);
    }

    @Override
    public double subpixelY() {
        double c = camExactY();
        return c - Math.floor(c);
    }

    /** Écran virtuel : bordures et sols (le joueur et les toits sont dessinés en haute résolution). */
    @Override
    public void render(Graphics2D g) {
        int sw = game.screenWidth(), sh = game.screenHeight();
        int camX = (int) Math.floor(camExactX());
        int camY = (int) Math.floor(camExactY());

        MapRenderer.Rendered current = render(map);

        // 1. Bordure répétée là où il n'y a ni la carte, ni une carte liée
        int firstTx = Math.floorDiv(camX, T), firstTy = Math.floorDiv(camY, T);
        int lastTx = Math.floorDiv(camX + sw, T), lastTy = Math.floorDiv(camY + sh, T);
        MapLayout layout = map.layout;
        for (int ty = firstTy; ty <= lastTy; ty++) {
            for (int tx = firstTx; tx <= lastTx; tx++) {
                // En intérieur, le vide autour de la pièce reste noir (comme PokeMMO) : pas de bordure répétée
                if (map.mapType == GameMap.TYPE_INDOOR) break;
                if (layout.inBounds(tx, ty) || locate(tx, ty) != null || current.borderBelow().length == 0) continue;
                int i = Math.floorMod(ty, layout.borderHeight) * layout.borderWidth + Math.floorMod(tx, layout.borderWidth);
                if (i < current.borderBelow().length) {
                    g.drawImage(current.borderBelow()[i], tx * T - camX, ty * T - camY, null);
                    g.drawImage(current.borderAbove()[i], tx * T - camX, ty * T - camY, null);
                }
            }
        }

        // 2. Cartes liées puis carte courante (calque sol)
        for (Connection c : map.connections) {
            GameMap t = loadOrNull(c.targetGroup(), c.targetNum());
            if (t == null) continue;
            int[] o = originOf(c, t);
            g.drawImage(render(t).below(), o[0] * T - camX, o[1] * T - camY, null);
        }
        g.drawImage(current.below(), -camX, -camY, null);
    }

    /** Fenêtre réelle : joueur, herbe devant les jambes, toits et cimes, puis l'interface. */
    @Override
    public void renderHiRes(Graphics2D g, int w, int h) {
        int zoom = game.blitScale();
        double camX = camExactX(), camY = camExactY();
        // Origine du monde dans la fenêtre, alignée exactement sur l'image du sol
        double originX = game.blitX() - Math.floor(camX) * zoom;
        double originY = game.blitY() - Math.floor(camY) * zoom;

        Graphics2D world = (Graphics2D) g.create();
        world.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        world.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_OFF);
        world.translate(originX, originY);
        world.scale(zoom, zoom);

        // Joueur, à sa position exacte (fraction de pixel virtuel comprise)
        MapRenderer.Rendered current = render(map);
        double ex = exactX(), ey = exactY();
        int hop = 0;
        if (moving && distance > T) hop = (int) Math.round(Math.sin(Math.PI * exactProgress() / distance) * 10);
        // Comme sur GBA : pied en avant pendant la 1re moitié du pas, position de repos pendant la 2de
        int step = moving && distance == T && progress < distance / 2 ? (stepLeftFoot ? 1 : 2) : 0;
        // Joueur calé sur un pixel entier de l'écran : chaque pixel du sprite fait exactement « zoom » pixels
        // (sinon, en nearest-neighbor, certains pixels font 2 ou 4 de large et le sprite tremble en marchant)
        Graphics2D p = (Graphics2D) g.create();
        p.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        p.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_OFF);
        p.translate(Math.round(originX + ex * zoom), Math.round(originY + ey * zoom));
        p.scale(zoom, zoom);
        avatar.drawOverworld(p, 0, 0, facing, step, hop);
        if (!moving && map.isLandEncounterAt(px, py) && map.behaviorAt(px, py) == 0x02) {
            BufferedImage below = current.below();
            int gx = px * T, gy = py * T + 9;
            if (gx >= 0 && gy >= 0 && gx + T <= below.getWidth() && gy + 7 <= below.getHeight()) {
                p.drawImage(below.getSubimage(gx, gy, T, 7), 0, 9, null);
            }
        }
        p.dispose();

        // Calque supérieur (cimes, toits) des cartes liées puis de la carte courante
        for (Connection c : map.connections) {
            GameMap t = loadOrNull(c.targetGroup(), c.targetNum());
            if (t == null) continue;
            int[] o = originOf(c, t);
            world.drawImage(render(t).above(), o[0] * T, o[1] * T, null);
        }
        world.drawImage(current.above(), 0, 0, null);
        world.dispose();

        drawHud(g, w, h);
    }

    /** Nom de la zone (en haut à gauche) et messages, en panneaux nets façon PokeMMO. */
    private void drawHud(Graphics2D g, int w, int h) {
        double s = Math.max(0.8, Math.min(1.6, h / 720.0));
        if (popupTimer > 0) {
            double t = Math.min(POPUP_FRAMES - popupTimer, popupTimer) / 12.0;
            double slide = (Math.min(1, t) - 1) * 60 * s;
            g.setFont(dev.orvaen.mmo.engine.HiUi.font((float) (15 * s), true));
            double tw = g.getFontMetrics().stringWidth(map.name);
            double bw = tw + 44 * s, bh = 34 * s, x = 14 * s, y = 14 * s + slide;
            dev.orvaen.mmo.engine.HiUi.fill(g, new java.awt.Color(20, 22, 34, 225), x, y, bw, bh, 6 * s);
            dev.orvaen.mmo.engine.HiUi.fill(g, dev.orvaen.mmo.engine.HiUi.ACCENT, x, y, 4 * s, bh, 2 * s);
            g.setColor(java.awt.Color.WHITE);
            dev.orvaen.mmo.engine.HiUi.centered(g, map.name, x + 8 * s, y, bw - 8 * s, bh);
        }
        if (messageTimer > 0 && message != null) {
            g.setFont(dev.orvaen.mmo.engine.HiUi.font((float) (15 * s), false));
            double bw = Math.min(w - 40, 620 * s), bh = 56 * s, x = (w - bw) / 2, y = h - bh - 24 * s;
            dev.orvaen.mmo.engine.HiUi.fill(g, new java.awt.Color(20, 22, 34, 235), x, y, bw, bh, 6 * s);
            dev.orvaen.mmo.engine.HiUi.stroke(g, dev.orvaen.mmo.engine.HiUi.PANEL_BORDER, x, y, bw, bh, 6 * s, 1f);
            g.setColor(dev.orvaen.mmo.engine.HiUi.TEXT);
            dev.orvaen.mmo.engine.HiUi.centered(g, message, x + 12 * s, y, bw - 24 * s, bh);
        }
    }
}
