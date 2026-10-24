package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.data.WildEncounters;
import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.Input.Button;
import dev.orvaen.mmo.engine.Scene;
import dev.orvaen.mmo.engine.Storage;
import dev.orvaen.mmo.rom.GameProfile;
import dev.orvaen.mmo.rom.RomException;
import dev.orvaen.mmo.script.ScriptVM;
import dev.orvaen.mmo.world.CoordEvent;
import dev.orvaen.mmo.world.DoorAnims;
import dev.orvaen.mmo.world.GameMap;
import dev.orvaen.mmo.world.MapLayout;
import dev.orvaen.mmo.world.MapRenderer;
import dev.orvaen.mmo.world.SignEvent;
import dev.orvaen.mmo.world.Warp;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;

/**
 * Le monde : on s'y promène, on parle aux gens, on passe les portes, et l'histoire s'y déroule.
 * <p>
 * Cette classe <b>coordonne</b> ; le travail est réparti dans des classes plus petites :
 * <ul>
 *   <li>{@link OverworldPlayer} — le joueur : position, pas en cours, dessin ;</li>
 *   <li>{@link NpcManager} — les personnages de la carte ;</li>
 *   <li>{@link DoorAnimator} — la porte qui s'ouvre / se ferme ;</li>
 *   <li>{@link MapView} — les images des cartes, les cartes voisines, les tuiles animées ;</li>
 *   <li>{@link MapScripts} et {@link OverworldScriptHost} — les scripts de l'histoire lus dans la ROM ;</li>
 *   <li>{@link OverworldHud} — les petits dessins d'interface (bulles, nom de la zone…).</li>
 * </ul>
 *
 * <h2>Une frame du monde ({@link #update})</h2>
 * <ol>
 *   <li>animations (eau, fleurs), PNJ, porte ;</li>
 *   <li>si un script tourne : on le laisse avancer, le joueur ne contrôle rien ;</li>
 *   <li>sinon : menus (Start, P, C), parler (A), puis marcher / courir avec les flèches.</li>
 * </ol>
 */
public final class OverworldScene implements Scene {

    private static final int T = MapRenderer.TILE;    // une case = 16 pixels
    private static final int BEHAVIOR_DOOR = 0x69;     // case « porte » (sans animation)
    private static final int BEHAVIOR_COUNTER = 0x80;  // comptoir : on parle au vendeur par-dessus
    private static final int BEHAVIOR_TALL_GRASS = 0x02;
    private static final int TURN_DELAY = 5;           // frames pour se tourner sans avancer

    final Game game;
    final OverworldPlayer player = new OverworldPlayer();
    final NpcManager npcs;
    final DoorAnimator doors = new DoorAnimator();
    final MapView view;
    final MessageBox box = new MessageBox();
    private final GrassEffects grass;
    private final PartyBar party;
    private final FollowerPokemon follower;
    private final OverworldScriptHost host;
    private PlayerSprite avatar;
    private final Random rng = new Random();
    private final int runFrames = Storage.runFramesPerTile();

    private GameMap map;

    // Entrée / sortie par une porte animée : ouverture → on entre → fermeture → fondu (et l'inverse en sortant)
    private enum DoorPhase { NONE, OPEN_TO_ENTER, WALK_IN, CLOSE_THEN_WARP, WAIT_EXIT, WALK_OUT, CLOSE_AFTER_EXIT }
    private DoorPhase doorPhase = DoorPhase.NONE;
    private Warp doorWarp;

    // Histoire : script en cours et ce qu'il a mis en place (lu et modifié par OverworldScriptHost)
    ScriptVM vm;
    Npc speaker;                        // PNJ qui parle (pour la flèche de la boîte de dialogue)
    int monPic = -1;                    // Pokémon montré à l'écran (-1 : aucun)
    boolean battling;                   // un combat lancé par le script n'est pas encore fini
    boolean naming;                     // écran « surnom » ouvert par le script
    boolean warpedIn;                   // vient d'arriver par un warp (scripts ON_WARP_INTO)
    int savedBgm;                       // musique imposée par l'histoire (0 : celle de la carte)

    // Divers
    private int turnTimer;
    private Direction autoStep;         // pas automatique en sortant d'un bâtiment
    private GameMap returnMap;          // pour les warps « retour à l'extérieur »
    private int returnX, returnY;
    private int stepsSinceBattle;
    private int areaPopupTimer;
    private String notice;
    private int noticeTimer;

    public OverworldScene(Game game) {
        this.game = game;
        this.npcs = new NpcManager(game.data, game.save);
        this.view = new MapView(game.data);
        this.host = new OverworldScriptHost(this);
        this.grass = new GrassEffects(game.data.fieldEffects());
        this.party = new PartyBar(game);
        this.follower = new FollowerPokemon(game.data.icons(), game.data.followerSprites(), game.data.species::nationalNumber);
        refreshAvatar();
        startNewGameIfNeeded();
        placePlayerAtStart();
    }

    /** Nouvelle partie : la ROM cache les personnages d'histoire pas encore apparus (script « reset »). */
    private void startNewGameIfNeeded() {
        if (game.save.eventsReady) return;
        int reset = game.data.scripts.resetScript();
        if (reset >= 0) newScript(reset).runInstant(2000);
        game.save.eventsReady = true;
    }

    /** Première carte : la sauvegarde, sinon la chambre du héros (Rouge Feu), sinon le départ du jeu. */
    private void placePlayerAtStart() {
        var save = game.save;
        var maps = game.data.maps;
        var profile = game.data.rom.profile();
        boolean fireRed = profile == GameProfile.FIRERED_LEAFGREEN;
        if (!save.hasPosition() && fireRed && maps.exists(4, 1) && game.data.scripts.resetScript() >= 0) {
            enterMap(maps.load(4, 1), 6, 6);            // Rouge Feu / Vert Feuille : dans sa chambre
            warpedIn = true;
        } else if (save.hasPosition() && maps.exists(save.group, save.num)) {
            enterMap(maps.load(save.group, save.num), save.x, save.y);
            player.facing = Direction.fromIndex(save.facing);
        } else {
            GameMap start = maps.exists(profile.startGroup, profile.startNum)
                    ? maps.load(profile.startGroup, profile.startNum)
                    : maps.load(0, 0);                    // ROM modifiée : première carte disponible
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
        battling = false;
        game.data.audio.playBgm(defaultMusic());     // retour d'un combat : la musique de la carte reprend
    }

    /** Musique « normale » : celle imposée par l'histoire, sinon celle de la carte. */
    int defaultMusic() {
        return savedBgm != 0 ? savedBgm : map == null ? 0 : map.music;
    }

    /** Le monde remplit toute la fenêtre : on voit plus de carte au lieu d'agrandir davantage. */
    @Override
    public boolean fillsWindow() {
        return true;
    }

    @Override
    public boolean countsPlayTime() {
        return true;
    }

    // ================================================================== une frame de logique

    @Override
    public void update(Input in) {
        if (areaPopupTimer > 0) areaPopupTimer--;
        if (noticeTimer > 0) noticeTimer--;
        view.animateTiles(map);
        npcs.respawn(map);
        npcs.update(map, player, vm != null);
        grass.update(map, player, npcs.all());
        follower.setSpecies(leadSpecies());
        follower.update(player);
        releaseFollower();
        player.tick();
        if (doorPhase == DoorPhase.NONE && doors.active()) {
            doors.update();                               // porte ouverte / fermée par un script
            if (doors.done() && doors.stage() < 0) doors.clear();
        }
        box.update(in);
        // clic sur un Pokémon de la barre d'équipe : son résumé
        if (in.mousePressed()) {
            int slot = party.slotAt(in.mouseX(), in.mouseY(), game.viewWidth(), game.viewHeight());
            if (slot >= 0) openSummary(slot);
        }

        if (vm != null) {
            updateScript(in);
            return;
        }
        if (doorPhase != DoorPhase.NONE) {
            updateDoorPhase();
            if (player.moving) advanceMove(in);
            return;
        }
        if (player.moving) {
            advanceMove(in);
            return;
        }
        if (game.isFading()) return;
        if (runFrameScript()) return;
        if (handleMenus(in)) return;
        if (in.pressed(Button.A) && interact()) return;
        if (autoStep != null) {
            Direction d = autoStep;
            autoStep = null;
            player.facing = d;
            tryMove(d, false);
            return;
        }
        handleWalking(in);
    }

    /** Un script tourne : il avance, le joueur ne contrôle rien. À la fin, on sauvegarde. */
    private void updateScript(Input in) {
        if (player.moving) advanceMove(in);
        vm.update();
        if (!vm.running() && !player.moving) endScript();
    }

    private void endScript() {
        vm = null;
        speaker = null;
        monPic = -1;
        box.close();
        game.save.store();
    }

    /** Résumé du Pokémon n° {@code slot} de l'équipe (la fenêtre déjà ouverte change de Pokémon). */
    void openSummary(int slot) {
        for (var w : game.windows()) {
            if (w instanceof SummaryWindow sw) {
                sw.show(slot);
                game.openWindow(sw);                      // repasse devant
                return;
            }
        }
        game.openWindow(new SummaryWindow(game, slot));
    }

    /** Start : menu. P / C / E / O : Pokédex / carte de dresseur / équipe / réglages. Select : liste des cartes. */
    private boolean handleMenus(Input in) {
        if (in.pressed(Button.START)) {
            game.push(new StartMenuScene(game, this));
            return true;
        }
        for (char c : in.typed().toLowerCase(Locale.ROOT).toCharArray()) {
            if (c == 'p') game.toggleWindow(new DexWindow(game));
            if (c == 'c') game.toggleWindow(new TrainerCardWindow(game));
            if (c == 'e' && !game.save.party.isEmpty()) game.toggleWindow(new SummaryWindow(game, 0));
            if (c == 'o') game.toggleWindow(new OptionsWindow(game));
        }
        if (in.pressed(Button.SELECT)) {
            game.push(new MapListScene(game, this));
            return true;
        }
        return false;
    }

    /** Flèches : on se tourne d'abord (un appui bref), puis on marche ; B maintenu pour courir. */
    private void handleWalking(Input in) {
        Direction dir = Direction.held(in);
        if (dir == null) {
            turnTimer = 0;
            return;
        }
        if (dir != player.facing) {
            player.facing = dir;
            turnTimer = TURN_DELAY;
            return;
        }
        if (turnTimer > 0 && in.heldFrames(dir.button()) < TURN_DELAY + 2) {
            turnTimer--;
            return;
        }
        tryMove(dir, in.down(Button.B));
    }

    // ================================================================== marcher

    /** Essaie de faire un pas : carte voisine, corniche, porte, obstacle… */
    private void tryMove(Direction dir, boolean running) {
        int px = player.x, py = player.y;

        // Tapis de sortie : on « pousse » vers la flèche pour changer de carte
        Optional<Warp> here = map.warpAt(px, py);
        if (here.isPresent() && map.behaviorAt(px, py) == dir.arrowWarpBehavior()) {
            doWarp(here.get());
            return;
        }

        int tx = px + dir.dx, ty = py + dir.dy;

        // Bord de la carte : on passe sur la carte voisine sans écran noir (ville ↔ route)
        if (!map.layout.inBounds(tx, ty)) {
            MapView.Located next = view.locate(map, tx, ty);
            if (next == null || !canEnter(next.map(), next.x(), next.y())) return;
            int ox = next.x() - tx, oy = next.y() - ty;   // décalage entre les repères des deux cartes
            follower.shift(ox, oy);
            switchMap(next.map(), px + ox, py + oy);
            startStep(dir, running, T);
            return;
        }

        // Corniche : on saute par-dessus
        if (map.behaviorAt(tx, ty) == dir.ledgeBehavior()) {
            int lx = tx + dir.dx, ly = ty + dir.dy;
            if (map.layout.inBounds(lx, ly) && MapLayout.collision(map.layout.blockAt(lx, ly)) == 0) {
                startStep(dir, false, T * 2);
                return;
            }
        }

        // Porte animée : elle s'ouvre, on entre, elle se referme, puis fondu (comme sur GBA)
        Optional<Warp> front = map.warpAt(tx, ty);
        if (dir == Direction.UP && front.isPresent() && !isArrowWarp(map.behaviorAt(tx, ty)) && !game.isFading()) {
            DoorAnims.Door d = game.data.doors.at(MapLayout.metatileId(map.layout.blockAt(tx, ty)));
            if (d != null) {
                player.facing = dir;
                doors.play(d, tx, ty, true);
                doorWarp = front.get();
                doorPhase = DoorPhase.OPEN_TO_ENTER;
                game.data.audio.playSe(game.data.songs.door());
                return;
            }
        }

        if (canEnter(map, tx, ty)) {
            startStep(dir, running, T);
            return;
        }

        // Escalier ou porte sans animation (cases « bloquantes ») : on entre directement
        front.ifPresent(this::doWarp);
    }

    /** Le joueur peut-il aller sur cette case ? (pas d'obstacle, pas de PNJ, même niveau) */
    private boolean canEnter(GameMap m, int x, int y) {
        if (m == map && npcs.at(x, y) != null) return false;
        int block = m.layout.blockAt(x, y);
        if (MapLayout.collision(block) != 0) return false;
        int z = MapLayout.elevation(block);
        int elevation = player.elevation;
        return elevation == 0 || z == 0 || z == 15 || z == elevation;
    }

    private void startStep(Direction dir, boolean running, int distance) {
        player.startStep(dir, running, distance, runFrames);
        follower.follow(player);                          // le Pokémon part dans la même frame que le joueur
    }

    /** Une frame du pas en cours ; à l'arrivée : porte, warp, déclencheurs, rencontres, pas suivant. */
    private void advanceMove(Input in) {
        if (!player.advance()) return;
        if (player.endScriptedStep()) return;
        if (doorPhase == DoorPhase.WALK_IN) {
            // dans l'encadrement : le joueur disparaît et la porte se referme derrière lui
            player.hidden = true;
            doorPhase = DoorPhase.CLOSE_THEN_WARP;
            doors.play(false);
            return;
        }
        if (doorPhase == DoorPhase.WALK_OUT) {
            doorPhase = DoorPhase.CLOSE_AFTER_EXIT;
            doors.play(false);
            return;
        }
        onStep();
        // Direction toujours maintenue : on enchaîne le pas suivant dans la même frame,
        // sans image d'arrêt entre deux cases (sinon le déplacement paraît saccadé).
        if (!player.moving && vm == null && !game.isFading() && game.top() == this && autoStep == null) {
            Direction dir = Direction.held(in);
            if (dir == player.facing) tryMove(dir, in.down(Button.B));
        }
    }

    /** Arrivée sur une nouvelle case. */
    private void onStep() {
        int px = player.x, py = player.y;
        int z = MapLayout.elevation(map.layout.blockAt(px, py));
        if (z != 15) player.elevation = z;
        stepsSinceBattle++;

        // Warp sous les pieds (escalier, trou…)
        Optional<Warp> w = map.warpAt(px, py);
        if (w.isPresent() && !isArrowWarp(map.behaviorAt(px, py))) {
            doWarp(w.get());
            return;
        }
        // Case piège d'un script (« Attends ! Ne va pas dans les hautes herbes ! »)
        for (CoordEvent t : map.triggers) {
            if (t.x() != px || t.y() != py || t.script() < 0) continue;
            if (t.var() == 0 || MapScripts.varValue(game.save, t.var()) == t.value()) {
                startScript(t.script(), -1);
                return;
            }
        }
        // Rencontre sauvage dans les hautes herbes (il faut au moins un Pokémon dans l'équipe)
        if (map.isLandEncounterAt(px, py) && stepsSinceBattle > 3 && !game.save.party.isEmpty()) {
            Optional<WildEncounters.Table> table = game.data.wild.land(map.group, map.num);
            if (table.isPresent() && rng.nextInt(2880) < table.get().rate() * 16) {
                WildEncounters.Slot slot = table.get().roll(rng);
                int level = slot.minLevel() + rng.nextInt(Math.max(1, slot.maxLevel() - slot.minLevel() + 1));
                stepsSinceBattle = 0;
                game.save.seen.add(slot.species());
                game.push(new BattleScene(game, slot.species(), level, battleTerrain()));
            }
        }
    }

    /** Fond du combat selon la case du joueur : herbe, hautes herbes, eau, grotte, intérieur… */
    int battleTerrain() {
        int b = map.behaviorAt(player.x, player.y);
        if (b == 0x03) return dev.orvaen.mmo.battle.BattleGraphics.LONG_GRASS;
        if (b == BEHAVIOR_TALL_GRASS) return dev.orvaen.mmo.battle.BattleGraphics.GRASS;
        if (b >= 0x10 && b <= 0x17) return dev.orvaen.mmo.battle.BattleGraphics.WATER;
        if (map.mapType == GameMap.TYPE_UNDERGROUND) return dev.orvaen.mmo.battle.BattleGraphics.CAVE;
        if (map.mapType == GameMap.TYPE_INDOOR) return dev.orvaen.mmo.battle.BattleGraphics.BUILDING;
        return dev.orvaen.mmo.battle.BattleGraphics.PLAIN;
    }

    private static boolean isArrowWarp(int behavior) {
        return behavior >= 0x62 && behavior <= 0x65;
    }

    // ================================================================== portes

    /** Avance l'entrée / la sortie par une porte animée. */
    private void updateDoorPhase() {
        doors.update();
        switch (doorPhase) {
            case OPEN_TO_ENTER -> {
                if (doors.done()) {
                    doorPhase = DoorPhase.WALK_IN;
                    startStep(player.facing, false, T);
                }
            }
            case CLOSE_THEN_WARP -> {
                if (doors.done()) {
                    doorPhase = DoorPhase.NONE;
                    doors.clear();
                    doWarp(doorWarp);
                }
            }
            case WAIT_EXIT -> {
                // le fondu d'arrivée est fini (la scène ne tourne pas pendant le fondu) : on sort
                player.hidden = false;
                doorPhase = DoorPhase.WALK_OUT;
                startStep(Direction.DOWN, false, T);
            }
            case CLOSE_AFTER_EXIT -> {
                if (doors.done()) {
                    doorPhase = DoorPhase.NONE;
                    doors.clear();
                }
            }
            default -> { }
        }
    }

    /**
     * Arrivée sur une porte animée : elle est déjà ouverte, le joueur est derrière (invisible)
     * et sortira vers le bas une fois le fondu terminé, puis elle se refermera.
     */
    private boolean exitThroughDoor(GameMap target, int x, int y) {
        DoorAnims.Door exit = game.data.doors.at(MapLayout.metatileId(target.layout.blockAt(x, y)));
        if (exit == null) return false;
        doors.showOpen(exit, x, y);
        player.hidden = true;
        doorPhase = DoorPhase.WAIT_EXIT;
        return true;
    }

    // ================================================================== changer de carte

    /** Passe par un warp (porte, escalier, tapis) : fondu au noir, puis arrivée sur l'autre carte. */
    private void doWarp(Warp w) {
        if (game.isFading()) return;
        if (!doors.active()) game.data.audio.playSe(game.data.songs.exit());   // escaliers, tapis de sortie
        GameMap from = map;
        int fromX = player.x, fromY = player.y;
        game.fadeThen(() -> {
            player.hidden = false;
            try {
                if (w.isDynamic() || !game.data.maps.exists(w.targetGroup(), w.targetNum())) {
                    // warp « retour » : on ressort là où on était entré
                    if (returnMap != null) {
                        enterMap(returnMap, returnX, returnY);
                        if (!exitThroughDoor(returnMap, returnX, returnY)) autoStep = Direction.DOWN;
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
                warpedIn = true;
                if (!exitThroughDoor(target, ax, ay)
                        && (target.behaviorAt(ax, ay) == BEHAVIOR_DOOR
                        || MapLayout.collision(target.layout.blockAt(ax, ay)) != 0)) {
                    autoStep = Direction.DOWN;            // on fait un pas pour sortir de l'encadrement
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
        int z = MapLayout.elevation(target.layout.blockAt(x, y));
        player.elevation = z == 15 ? 3 : z;
        player.placeAt(x, y);
        player.facing = Direction.DOWN;
        player.hidden = false;
        follower.placeAt(x, y, player);
        doorPhase = DoorPhase.NONE;
        doors.clear();
        if (newPlace) areaPopupTimer = OverworldHud.AREA_POPUP_FRAMES;
        rememberPosition();
        game.save.store();                               // sauvegarde auto à chaque entrée de carte
    }

    /** Bascule de carte sans écran noir (liaisons extérieures). */
    private void switchMap(GameMap target, int x, int y) {
        boolean newPlace = map != null && !map.name.equals(target.name);
        boolean changed = map != target;
        map = target;
        player.x = x;
        player.y = y;
        if (changed) mapLoaded();
        if (newPlace) areaPopupTimer = OverworldHud.AREA_POPUP_FRAMES;
        view.preload(target);
    }

    /** Arrivée sur une carte : remise à zéro du temporaire, scripts d'arrivée, personnages, musique. */
    private void mapLoaded() {
        MapScripts.clearTemporary(game.save);
        npcs.forgetScriptChanges();
        grass.clear();
        if (map.layout.reset()) view.forget(map);        // métatiles changés par un script : on redessine
        runMapScript(MapScripts.ON_LOAD);
        runMapScript(MapScripts.ON_TRANSITION);
        npcs.load(map);
        runMapScript(MapScripts.ON_RESUME);
        game.data.audio.playBgm(defaultMusic());
    }

    public void teleport(GameMap target) {
        int[] s = defaultSpawn(target);
        returnMap = null;
        enterMap(target, s[0], s[1]);
    }

    /** Point d'apparition par défaut : sous le premier warp (souvent une porte), sinon au centre. */
    static int[] defaultSpawn(GameMap m) {
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
        save.x = player.x;
        save.y = player.y;
        save.facing = player.facing.ordinal();
    }

    // ================================================================== parler, scripts

    /** A face à un PNJ ou un panneau : il se tourne vers le joueur et parle. */
    private boolean interact() {
        Direction f = player.facing;
        int tx = player.x + f.dx, ty = player.y + f.dy;
        Npc n = npcs.at(tx, ty);
        if (n == null && map.layout.inBounds(tx, ty) && map.behaviorAt(tx, ty) == BEHAVIOR_COUNTER) {
            n = npcs.at(tx + f.dx, ty + f.dy);
        }
        if (n != null) {
            if (n.event.script() >= 0) {
                speaker = n;
                startScript(n.event.script(), n.event.localId());
            } else {
                n.face(f.opposite().ordinal());
            }
            return true;
        }
        for (SignEvent s : map.signs) {
            if (s.x() != tx || s.y() != ty || !s.readable()) continue;
            boolean facingOk = switch (s.kind()) {          // certains panneaux ne se lisent que de face
                case 1 -> f == Direction.UP;
                case 2 -> f == Direction.DOWN;
                case 3 -> f == Direction.RIGHT;
                case 4 -> f == Direction.LEFT;
                default -> true;
            };
            if (!facingOk) continue;
            startScript(s.script(), -1);
            return true;
        }
        return false;
    }

    private ScriptVM newScript(int script) {
        return new ScriptVM(game.data.rom, host, game.save, game.data.scripts, script);
    }

    /** Lance un script (bloquant : le joueur ne bouge plus jusqu'à la fin). */
    public void startScript(int script, int lastTalked) {
        if (script < 0) return;
        vm = newScript(script);
        vm.setSpecial(ScriptVM.VAR_FACING, player.facing.ordinal() + 1);
        if (lastTalked >= 0) vm.setSpecial(ScriptVM.VAR_LAST_TALKED, lastTalked);
        vm.update();
        if (!vm.running()) {
            vm = null;
            speaker = null;
            box.close();
        }
    }

    /** Scripts « immédiats » de la carte (exécutés d'un coup, sans attendre). */
    private void runMapScript(int type) {
        int ptr = MapScripts.find(game.data.rom, map, type);
        if (ptr >= 0) newScript(ptr).runInstant(500);
    }

    /** Scènes qui se lancent seules (le Professeur qui t'interpelle…) : tables ON_WARP_INTO et ON_FRAME. */
    private boolean runFrameScript() {
        if (warpedIn) {
            warpedIn = false;
            int s = MapScripts.fromTable(game.data.rom, game.save, map, MapScripts.ON_WARP_INTO);
            if (s >= 0) newScript(s).runInstant(200);
        }
        int s = MapScripts.fromTable(game.data.rom, game.save, map, MapScripts.ON_FRAME);
        if (s < 0) return false;
        startScript(s, -1);
        return true;
    }

    /**
     * Juste après une arrivée, le Pokémon est caché sous le joueur : dès que celui-ci est libre (pas de porte,
     * pas de script, pas de pas automatique), il sort sur une case voisine — derrière le joueur si possible.
     */
    private void releaseFollower() {
        if (!follower.underPlayer(player) || player.moving || player.hidden || doorPhase != DoorPhase.NONE
                || autoStep != null || vm != null || leadSpecies() == 0) return;
        Direction f = player.facing;
        Direction[] order = {f.opposite(), sideOf(f, true), sideOf(f, false), f};
        for (Direction d : order) {
            int tx = player.x + d.dx, ty = player.y + d.dy;
            if (map.layout.inBounds(tx, ty) && canEnter(map, tx, ty) && map.warpAt(tx, ty).isEmpty()) {
                follower.popOut(tx, ty);
                return;
            }
        }
    }

    private static Direction sideOf(Direction d, boolean left) {
        return switch (d) {
            case UP, DOWN -> left ? Direction.LEFT : Direction.RIGHT;
            case LEFT, RIGHT -> left ? Direction.UP : Direction.DOWN;
        };
    }

    /** Espèce du premier Pokémon en forme de l'équipe (celui qui suit le joueur), ou 0. */
    private int leadSpecies() {
        for (int[] m : game.save.party) {
            boolean fainted = m.length >= 20 && m[3] <= 0;
            if (!fainted && m.length > 0) return m[0];
        }
        return 0;
    }

    String rivalName() {
        String r = game.save.rivalName;
        return r == null || r.isBlank() ? "Blue" : r;
    }

    // ================================================================== accès (menus, tests)

    public boolean scriptRunning() { return vm != null; }
    public MessageBox messageBox() { return box; }
    public List<Npc> npcs() { return npcs.all(); }
    public GameMap currentMap() { return map; }
    public boolean isMoving() { return player.moving; }
    public int playerX() { return player.x; }
    public int playerY() { return player.y; }
    public Direction facing() { return player.facing; }
    public boolean playerHidden() { return player.hidden; }

    /** Case du Pokémon qui suit le joueur ({x, y}), ou null s'il n'est pas visible — pour les tests. */
    public int[] followerTile() {
        return follower.visible(player) ? new int[]{follower.x(), follower.y()} : null;
    }

    /** Le Pokémon qui suit utilise-t-il son vrai sprite (sinon l'icône de la ROM) — pour les tests. */
    public boolean followerUsesWalkingSprite() {
        return follower.usesWalkingSprite();
    }

    /** Nombre d'herbes hautes en train de s'agiter — pour les tests. */
    public int rustlingGrass() { return grass.count(); }

    /** Image de porte affichée (-1 : aucune animation en cours) — pour les tests. */
    public int doorStage() { return doors.stage(); }

    /** Rend les rencontres reproductibles (tests, replays). */
    public void setRandomSeed(long seed) { rng.setSeed(seed); }

    /** Petit message en bas de l'écran pendant 3 secondes. */
    public void showMessage(String text) {
        notice = text;
        noticeTimer = 180;
    }

    // ================================================================== dessin

    /** Coin haut-gauche de la caméra (pixels du monde, non arrondi) : le joueur est au centre de l'écran. */
    private double camExactX() {
        return player.exactX(game.interpolation()) - (game.screenWidth() / 2 - T / 2);
    }

    private double camExactY() {
        return player.exactY(game.interpolation()) - (game.screenHeight() / 2 - T / 2);
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

    /** Écran virtuel (taille GBA) : le sol et la porte. Le reste est dessiné en haute résolution. */
    @Override
    public void render(Graphics2D g) {
        int camX = (int) Math.floor(camExactX());
        int camY = (int) Math.floor(camExactY());
        view.drawGround(g, map, camX, camY, game.screenWidth(), game.screenHeight());
        doors.draw(g, game.data, map, camX, camY);
    }

    // Repère du dernier rendu (pour placer la flèche de la boîte de dialogue)
    private double lastOriginX, lastOriginY;
    private int lastZoom = 1;

    /** Fenêtre réelle : personnages, toits et cimes par-dessus, bulles, puis l'interface. */
    @Override
    public void renderHiRes(Graphics2D g, int w, int h) {
        int zoom = game.blitScale();
        // Origine du monde dans la fenêtre, alignée exactement sur l'image du sol
        double originX = game.blitX() - Math.floor(camExactX()) * zoom;
        double originY = game.blitY() - Math.floor(camExactY()) * zoom;
        lastOriginX = originX;
        lastOriginY = originY;
        lastZoom = zoom;
        double interp = game.interpolation();
        double playerY = player.exactY(interp);

        // Personnages : ceux plus haut que le joueur passent derrière lui, les autres devant
        for (Npc n : npcs.all()) if (n.exactY(interp) <= playerY) OverworldHud.drawNpc(g, n, originX, originY, zoom, interp);
        boolean tallGrass = map.isLandEncounterAt(player.x, player.y)
                && map.behaviorAt(player.x, player.y) == BEHAVIOR_TALL_GRASS;
        // Le Pokémon qui suit : derrière le joueur s'il est plus haut sur l'écran, devant sinon
        boolean followerBehind = follower.exactY(interp) <= playerY;
        if (followerBehind) follower.draw(g, player, originX, originY, zoom, interp);
        player.draw(g, avatar, originX, originY, zoom, interp, view.render(map).below(), tallGrass);
        if (!followerBehind) follower.draw(g, player, originX, originY, zoom, interp);
        for (Npc n : npcs.all()) if (n.exactY(interp) > playerY) OverworldHud.drawNpc(g, n, originX, originY, zoom, interp);
        grass.draw(g, originX, originY, zoom);                   // l'herbe haute agitée cache les pieds

        // Toits et cimes des arbres, par-dessus les personnages
        Graphics2D world = (Graphics2D) g.create();
        world.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        world.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        world.translate(originX, originY);
        world.scale(zoom, zoom);
        view.drawAbove(world, map);
        world.dispose();

        // Bulles « ! » / « ? » au-dessus des têtes
        for (Npc n : npcs.all()) {
            if (n.emote() >= 0 && n.visible()) {
                OverworldHud.drawEmote(g, n.emote(), originX + (n.exactX(interp) + T / 2.0) * zoom,
                        originY + (n.exactY(interp) + T - n.height() - 2) * zoom, zoom);
            }
        }
        if (player.emoteTimer > 0 && !player.hidden) {
            OverworldHud.drawEmote(g, player.emote, originX + (player.exactX(interp) + T / 2.0) * zoom,
                    originY + (playerY - T - 2) * zoom, zoom);
        }

        // Interface
        OverworldHud.drawAreaName(g, h, map.name, areaPopupTimer);
        if (noticeTimer > 0 && notice != null) OverworldHud.drawNotice(g, w, h, notice);
        party.draw(g, w, h);
        if (monPic > 0) OverworldHud.drawMonPic(g, w, h, game.data.species.frontSprite(monPic));
        box.render(g, w, h);
    }

    /** Rectangle du sprite d'un PNJ dans la fenêtre (la flèche de la boîte de dialogue le vise). */
    Rectangle2D screenBounds(Npc n) {
        double i = game.interpolation();
        double wx = n.exactX(i) + (T - n.width()) / 2.0, wy = n.exactY(i) + T - n.height();
        double top = wy + (n.height() >= 32 ? 8 : 0);    // le haut des sprites 16x32 est souvent vide : on vise la tête
        return new Rectangle2D.Double(lastOriginX + wx * lastZoom, lastOriginY + top * lastZoom,
                n.width() * lastZoom, (wy + n.height() - top) * lastZoom);
    }
}
