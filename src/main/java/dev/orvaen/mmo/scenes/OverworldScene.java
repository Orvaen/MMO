package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.data.WildEncounters;
import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.Input.Button;
import dev.orvaen.mmo.engine.Scene;
import dev.orvaen.mmo.engine.Storage;
import dev.orvaen.mmo.engine.Ui;
import dev.orvaen.mmo.rom.RomException;
import dev.orvaen.mmo.script.Movement;
import dev.orvaen.mmo.script.ScriptVM;
import dev.orvaen.mmo.world.Connection;
import dev.orvaen.mmo.world.CoordEvent;
import dev.orvaen.mmo.world.DoorAnims;
import dev.orvaen.mmo.world.GameMap;
import dev.orvaen.mmo.world.MapLayout;
import dev.orvaen.mmo.world.MapRenderer;
import dev.orvaen.mmo.world.ObjectEvent;
import dev.orvaen.mmo.world.ObjectGraphics;
import dev.orvaen.mmo.world.ScriptText;
import dev.orvaen.mmo.world.SignEvent;
import dev.orvaen.mmo.world.TileAnims;
import dev.orvaen.mmo.world.Tileset;
import dev.orvaen.mmo.world.Warp;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
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
    private double progress;            // pixels parcourus vers la case suivante
    private int distance;               // 16 (pas) ou 32 (saut de corniche)
    private int turnTimer;
    private int animCounter;
    private boolean stepLeftFoot;
    private boolean running;            // pas en cours fait en courant (B)
    private boolean hidden;             // joueur passé derrière une porte

    // Porte animée (entrée : ouverture → on entre → fermeture → fondu ; sortie : l'inverse)
    private enum DoorPhase { NONE, OPEN_TO_ENTER, WALK_IN, CLOSE_THEN_WARP, WAIT_EXIT, WALK_OUT, CLOSE_AFTER_EXIT }
    private DoorPhase doorPhase = DoorPhase.NONE;
    private DoorAnims.Door door;
    private int doorX, doorY;
    private int[] doorSequence = {};    // étapes à jouer (-1 = fermée), STAGE_FRAMES frames chacune
    private int doorTick;
    private int doorStage = -1;         // étape affichée
    private Warp doorWarp;

    // Personnages de la carte et tuiles animées
    private final List<Npc> npcs = new ArrayList<>();
    private final Map<String, int[]> animCells = new HashMap<>();
    private Npc talking;

    // Histoire : script en cours, boîte de texte, état piloté par les scripts
    private ScriptVM vm;
    private final MessageBox box = new MessageBox();
    private final Map<Integer, int[]> objectPositions = new HashMap<>();   // setobjectxyperm (carte courante)
    private final Map<Integer, Integer> objectMovements = new HashMap<>(); // setobjectmovementtype
    private boolean scriptedStep, hopStep, animateStep = true;
    private int playerInPlace;
    private int playerEmote = -1, playerEmoteTimer;
    private int monPic = -1;
    private boolean battling;
    private Npc speaker;
    private boolean warpedIn;

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
        if (!save.eventsReady) {
            // Nouvelle partie : la ROM cache les personnages d'histoire pas encore apparus
            int reset = game.data.scripts.resetScript();
            if (reset >= 0) new ScriptVM(game.data.rom, new Host(), save, game.data.scripts, reset).runInstant(2000);
            save.eventsReady = true;
        }
        boolean fireRed = profile == dev.orvaen.mmo.rom.GameProfile.FIRERED_LEAFGREEN;
        if (!save.hasPosition() && fireRed && maps.exists(4, 1) && game.data.scripts.resetScript() >= 0) {
            // Rouge Feu / Vert Feuille : on commence dans sa chambre, comme dans le jeu
            enterMap(maps.load(4, 1), 6, 6);
            warpedIn = true;
        } else if (save.hasPosition() && maps.exists(save.group, save.num)) {
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
        battling = false;
    }

    // ------------------------------------------------------------------ logique

    /** Le monde remplit toute la fenêtre : on voit plus de carte au lieu d'agrandir davantage. */
    @Override
    public boolean fillsWindow() {
        return true;
    }

    @Override
    public boolean countsPlayTime() {
        return true;
    }

    @Override
    public void update(Input in) {
        animCounter++;
        if (popupTimer > 0) popupTimer--;
        if (messageTimer > 0) messageTimer--;
        animateTiles();
        boolean scripting = vm != null;
        for (Npc n : npcs) n.update((x, y) -> npcCanEnter(n, x, y), scripting || n == talking);
        if (playerInPlace > 0) playerInPlace--;
        if (playerEmoteTimer > 0) playerEmoteTimer--;
        if (doorPhase == DoorPhase.NONE && door != null) {
            updateDoor();                       // porte ouverte / fermée par un script
            if (doorDone() && doorStage < 0) door = null;
        }
        box.update(in);

        if (vm != null) {
            if (moving) advanceMove(in);
            vm.update();
            if (!vm.running() && !moving) {
                vm = null;
                speaker = null;
                monPic = -1;
                box.close();
                game.save.store();
            }
            return;
        }

        if (doorPhase != DoorPhase.NONE) {
            updateDoor();
            if (moving) advanceMove(in);
            return;
        }
        if (moving) {
            advanceMove(in);
            return;
        }
        if (game.isFading()) return;
        if (runFrameScript()) return;
        if (in.pressed(Button.START)) {
            game.push(new StartMenuScene(game, this));
            return;
        }
        if (in.pressed(Button.SELECT)) {
            game.push(new MapListScene(game, this));
            return;
        }
        if (in.pressed(Button.A) && interact()) return;
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

        // Porte animée : elle s'ouvre, on entre, elle se referme, puis fondu (comme sur GBA)
        Optional<Warp> front = map.warpAt(tx, ty);
        if (dir == Direction.UP && front.isPresent() && !isArrowWarp(map.behaviorAt(tx, ty)) && !game.isFading()) {
            DoorAnims.Door d = game.data.doors.at(MapLayout.metatileId(map.layout.blockAt(tx, ty)));
            if (d != null) {
                facing = dir;
                door = d;
                doorX = tx;
                doorY = ty;
                doorWarp = front.get();
                playDoor(true);
                doorPhase = DoorPhase.OPEN_TO_ENTER;
                return;
            }
        }

        if (canEnter(map, tx, ty)) {
            startMove(dir, running, T);
            return;
        }

        // Escalier ou porte sans animation, « bloquants » : on entre directement
        if (front.isPresent()) {
            doWarp(front.get());
            return;
        }
        bump();
    }

    private boolean canEnter(GameMap m, int x, int y) {
        if (m == map && npcAt(x, y) != null) return false;
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
        this.running = running && dist == T;
        // Marche : 16 frames par case (GBA). Course : plus posée que les chaussures de course GBA
        // (8 frames), plus proche de PokeMMO ; réglable dans config.properties (move.runFrames).
        speed = dist > T ? 2 : running ? T / (double) runFrames : 1;
    }

    // ------------------------------------------------------------------ portes

    /** Lance l'ouverture (fermée, 1, 2, 3) ou la fermeture (3, 2, 1, fermée) de la porte. */
    private void playDoor(boolean open) {
        doorSequence = open ? new int[]{-1, 0, 1, 2} : new int[]{2, 1, 0, -1};
        doorTick = 0;
        doorStage = doorSequence[0];
    }

    /** Vrai quand la séquence en cours est terminée (la dernière étape reste affichée). */
    private boolean doorDone() {
        return doorTick >= doorSequence.length * DoorAnims.STAGE_FRAMES;
    }

    private void updateDoor() {
        if (!doorDone()) {
            doorStage = doorSequence[Math.min(doorSequence.length - 1, doorTick / DoorAnims.STAGE_FRAMES)];
            doorTick++;
        }
        switch (doorPhase) {
            case OPEN_TO_ENTER -> {
                if (doorDone()) {
                    doorPhase = DoorPhase.WALK_IN;
                    startMove(facing, false, T);
                }
            }
            case CLOSE_THEN_WARP -> {
                if (doorDone()) {
                    doorPhase = DoorPhase.NONE;
                    door = null;
                    doorStage = -1;
                    doWarp(doorWarp);
                }
            }
            case WAIT_EXIT -> {
                // le fondu d'arrivée est fini (la scène ne tourne pas pendant le fondu) : on sort
                hidden = false;
                doorPhase = DoorPhase.WALK_OUT;
                startMove(Direction.DOWN, false, T);
            }
            case CLOSE_AFTER_EXIT -> {
                if (doorDone()) {
                    doorPhase = DoorPhase.NONE;
                    door = null;
                    doorStage = -1;
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
        door = exit;
        doorX = x;
        doorY = y;
        playDoor(true);
        doorTick = doorSequence.length * DoorAnims.STAGE_FRAMES;
        doorStage = 2;
        hidden = true;
        doorPhase = DoorPhase.WAIT_EXIT;
        return true;
    }

    /** Étape de porte affichée (-1 : aucune animation en cours) — pour les tests. */
    public int doorStage() {
        return door == null ? -1 : doorStage;
    }

    public boolean playerHidden() {
        return hidden;
    }

    private double speed = 1;
    private final int runFrames = Storage.runFramesPerTile();

    private void advanceMove(Input in) {
        progress += speed;
        if (progress < distance - 1e-6) return;
        moving = false;
        int tiles = distance / T;
        px += facing.dx * tiles;
        py += facing.dy * tiles;
        if (scriptedStep) {
            scriptedStep = false;
            hopStep = false;
            animateStep = true;
            return;
        }
        if (doorPhase == DoorPhase.WALK_IN) {
            // dans l'encadrement : le joueur disparaît et la porte se referme derrière lui
            hidden = true;
            doorPhase = DoorPhase.CLOSE_THEN_WARP;
            playDoor(false);
            return;
        }
        if (doorPhase == DoorPhase.WALK_OUT) {
            doorPhase = DoorPhase.CLOSE_AFTER_EXIT;
            playDoor(false);
            return;
        }
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
        for (CoordEvent t : map.triggers) {
            if (t.x() != px || t.y() != py || t.script() < 0) continue;
            if (t.var() == 0 || varValue(t.var()) == t.value()) {
                startScript(t.script(), -1);
                return;
            }
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
            hidden = false;
            try {
                if (w.isDynamic() || !game.data.maps.exists(w.targetGroup(), w.targetNum())) {
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
                if (exitThroughDoor(target, ax, ay)) {
                    // la sortie animée prend le relais
                } else if (target.behaviorAt(ax, ay) == BEHAVIOR_DOOR
                        || MapLayout.collision(target.layout.blockAt(ax, ay)) != 0) {
                    autoStep = Direction.DOWN;
                }
            } catch (RomException e) {
                showMessage("Warp impossible : " + e.getMessage());
            }
        });
    }

    // ------------------------------------------------------------------ personnages

    /** Graphismes « variables » (choisis par les scripts) : pas affichables sans moteur de scripts. */
    private static final int FIRST_VAR_GRAPHICS = 0xEF;

    private void loadNpcs() {
        npcs.clear();
        talking = null;
        for (ObjectEvent e : map.objects) {
            // Les personnages à drapeau n'apparaissent que si l'histoire ne les cache pas
            if (e.flag() != 0 && game.save.flags.get(e.flag())) continue;
            spawn(e);
        }
    }

    private Npc spawn(ObjectEvent e) {
        if (e.graphicsId() >= FIRST_VAR_GRAPHICS) return null;
        ObjectGraphics.Sprite sprite = game.data.objects.get(e.graphicsId());
        if (sprite == null) return null;
        Npc n = new Npc(e, sprite, (long) map.key().hashCode() * 31 + e.localId());
        int[] pos = objectPositions.get(e.localId());
        if (pos != null) n.setPosition(pos[0], pos[1]);
        Integer type = objectMovements.get(e.localId());
        if (type != null) n.setMovementType(type);
        npcs.add(n);
        return n;
    }

    private Npc npcById(int localId) {
        for (Npc n : npcs) if (n.event.localId() == localId) return n;
        return null;
    }

    // ------------------------------------------------------------------ scripts de l'histoire

    private static final int TEMP_FLAGS_END = 0x20, TEMP_VARS = 0x4000, TEMP_VARS_END = 0x4010;
    private static final int MAP_ON_LOAD = 1, MAP_ON_FRAME = 2, MAP_ON_TRANSITION = 3, MAP_ON_WARP_INTO = 4,
            MAP_ON_RESUME = 5;

    /** Valeur d'une variable de script (ou la valeur elle-même si ce n'est pas une variable). */
    private int varValue(int id) {
        if (id >= 0x4000 && id < 0x8000) return game.save.vars.getOrDefault(id, 0);
        return id;
    }

    /** Lance un script (bloquant : le joueur ne bouge plus jusqu'à la fin). */
    public void startScript(int script, int lastTalked) {
        if (script < 0) return;
        vm = new ScriptVM(game.data.rom, new Host(), game.save, game.data.scripts, script);
        vm.setSpecial(ScriptVM.VAR_FACING, facing.ordinal() + 1);
        if (lastTalked >= 0) vm.setSpecial(ScriptVM.VAR_LAST_TALKED, lastTalked);
        vm.update();
        if (!vm.running()) {
            vm = null;
            speaker = null;
            box.close();
        }
    }

    public boolean scriptRunning() {
        return vm != null;
    }

    public MessageBox messageBox() {
        return box;
    }

    /** Arrivée sur une carte : remise à zéro du temporaire, scripts d'arrivée, personnages. */
    private void mapLoaded() {
        game.save.flags.clear(0, TEMP_FLAGS_END);
        for (int v = TEMP_VARS; v < TEMP_VARS_END; v++) game.save.vars.remove(v);
        objectPositions.clear();
        objectMovements.clear();
        if (map.layout.reset()) renders.remove(map.key());
        runMapScript(MAP_ON_LOAD);
        runMapScript(MAP_ON_TRANSITION);
        loadNpcs();
        runMapScript(MAP_ON_RESUME);
        animCells.clear();
    }

    /** Scripts « simples » de la carte (exécutés tout de suite, sans attendre). */
    private void runMapScript(int type) {
        int ptr = mapScript(type);
        if (ptr >= 0) new ScriptVM(game.data.rom, new Host(), game.save, game.data.scripts, ptr).runInstant(500);
    }

    private int mapScript(int type) {
        var rom = game.data.rom;
        int o = map.scripts;
        if (o < 0) return -1;
        for (int i = 0; i < 16 && rom.inRange(o, 5); i++, o += 5) {
            int t = rom.u8(o);
            if (t == 0) break;
            if (t == type) return rom.pointer(o + 1);
        }
        return -1;
    }

    /** Tables « variable = valeur → script » (à chaque frame, à l'arrivée par un warp). */
    private int tableScript(int type) {
        var rom = game.data.rom;
        int table = mapScript(type);
        if (table < 0) return -1;
        for (int e = table; rom.inRange(e, 8); e += 8) {
            int var = rom.u16(e);
            if (var == 0) break;
            if (varValue(var) == varValue(rom.u16(e + 2))) return rom.pointer(e + 4);
        }
        return -1;
    }

    /** Scènes qui se lancent seules (le Professeur qui t'interpelle…) : table ON_FRAME. */
    private boolean runFrameScript() {
        if (warpedIn) {
            warpedIn = false;
            int s = tableScript(MAP_ON_WARP_INTO);
            if (s >= 0) new ScriptVM(game.data.rom, new Host(), game.save, game.data.scripts, s).runInstant(200);
        }
        int s = tableScript(MAP_ON_FRAME);
        if (s < 0) return false;
        startScript(s, -1);
        return true;
    }

    /** Le joueur vu par les scripts (applymovement LOCALID_PLAYER…). */
    private final Movement.Actor playerActor = new Movement.Actor() {
        @Override public int x() { return px; }
        @Override public int y() { return py; }
        @Override public int facing() { return facing.ordinal(); }
        @Override public void face(int dir) { if (!moving) facing = Direction.fromIndex(dir); }

        @Override
        public void step(int dir, int tiles, int frames, boolean hop, boolean animate) {
            facing = Direction.fromIndex(dir);
            if (tiles == 0) {
                playerInPlace = Math.max(1, frames);
                stepLeftFoot = !stepLeftFoot;
                return;
            }
            moving = true;
            progress = 0;
            distance = T * tiles;
            speed = distance / (double) Math.max(1, frames);
            running = false;
            stepLeftFoot = !stepLeftFoot;
            scriptedStep = true;
            hopStep = hop;
            animateStep = animate;
        }

        @Override public boolean busy() { return moving || playerInPlace > 0; }
        @Override public void setVisible(boolean visible) { hidden = !visible; }

        @Override
        public void emote(int kind) {
            playerEmote = kind;
            playerEmoteTimer = 60;
        }
    };

    /** Ce que les scripts peuvent faire dans le monde. */
    private final class Host implements ScriptVM.Host {
        @Override
        public void message(int textPtr) {
            if (textPtr < 0) return;
            String player = game.save.name == null || game.save.name.isBlank() ? "Dresseur" : game.save.name;
            List<String> pages = dev.orvaen.mmo.rom.TextCodec.dialogue(game.data.rom, textPtr, 1024, code -> switch (code) {
                case 0x01 -> player;
                case 0x02, 0x03, 0x04 -> vm == null ? "" : vm.buffer(code - 2);
                case 0x06 -> "RIVAL";
                default -> "";
            });
            Npc who = speaker;
            box.show(pages, who == null ? null : () -> new DialogueScene.Speaker(screenBounds(who), py < who.y()));
        }

        @Override public boolean messagePrinted() { return box.printed(); }
        @Override public boolean takeButton() { return box.takeButton(); }
        @Override public void closeMessage() { box.close(); }
        @Override public void askYesNo() { box.askYesNo(); }
        @Override public int yesNoResult() { return box.takeResult(); }

        @Override
        public Movement.Actor actor(int localId) {
            if (localId == 0xFF) return playerActor;
            return npcById(localId);
        }

        @Override
        public void faceplayer(int localId) {
            Npc n = npcById(localId);
            if (n != null) n.face(opposite(facing).ordinal());
        }

        @Override
        public void removeObject(int localId) {
            Npc n = npcById(localId);
            for (ObjectEvent e : map.objects) {
                if (e.localId() == localId && e.flag() != 0) game.save.flags.set(e.flag());
            }
            if (n != null) npcs.remove(n);
        }

        @Override
        public void addObject(int localId) {
            if (npcById(localId) != null) return;
            for (ObjectEvent e : map.objects) if (e.localId() == localId) spawn(e);
        }

        @Override
        public void setObjectXY(int localId, int x, int y, boolean permanent) {
            if (localId == 0xFF) {
                px = x;
                py = y;
                return;
            }
            if (permanent) objectPositions.put(localId, new int[]{x, y});
            Npc n = npcById(localId);
            if (n != null && !permanent) n.setPosition(x, y);
        }

        @Override
        public void setObjectMovementType(int localId, int type) {
            objectMovements.put(localId, type);
            Npc n = npcById(localId);
            if (n != null) n.setMovementType(type);
        }

        @Override
        public void showObject(int localId, boolean visible) {
            Npc n = npcById(localId);
            if (n != null) n.setVisible(visible);
        }

        @Override
        public void warp(int group, int num, int warpId, int x, int y) {
            if (!game.data.maps.exists(group, num)) return;
            game.fadeThen(() -> {
                GameMap target = game.data.maps.load(group, num);
                int ax = x, ay = y;
                if ((ax < 0 || ay < 0) && warpId < target.warps.size()) {
                    ax = target.warps.get(warpId).x();
                    ay = target.warps.get(warpId).y();
                }
                if (ax < 0 || ay < 0) {
                    int[] sp = defaultSpawn(target);
                    ax = sp[0];
                    ay = sp[1];
                }
                enterMap(target, ax, ay);
                warpedIn = true;
            });
        }

        @Override
        public void setMetatile(int x, int y, int metatile, boolean blocking) {
            if (!map.layout.inBounds(x, y)) return;
            int i = y * map.layout.width + x;
            int old = map.layout.blocks[i];
            map.layout.blocks[i] = (old & 0xF000) | (blocking ? 0x0C00 : 0) | (metatile & MapLayout.METATILE_MASK);
            game.data.renderer.redraw(map.layout, render(map), new int[]{i});
        }

        @Override
        public void door(int x, int y, boolean open) {
            DoorAnims.Door d = game.data.doors.at(MapLayout.metatileId(map.layout.blockAt(x, y)));
            if (d == null) return;
            door = d;
            doorX = x;
            doorY = y;
            playDoor(open);
        }

        @Override public boolean doorBusy() { return door != null && !doorDone(); }

        @Override public void fade(int mode) { game.fadeScreen(mode == 1 || mode == 3); }
        @Override public boolean fadeBusy() { return game.fadeScreenBusy(); }

        @Override
        public void trainerBattle(int trainerId, int introText, int defeatText) {
            battling = true;
            game.push(new TrainerBattleScene(game, game.data.trainers().get(trainerId), defeatText));
        }

        @Override
        public void wildBattle(int species, int level) {
            battling = true;
            game.push(new BattleScene(game, species, Math.max(1, level)));
        }

        @Override public boolean battleBusy() { return battling; }
        @Override public void showMonPic(int species) { monPic = species; }
        @Override public void hideMonPic() { monPic = -1; }

        @Override
        public String speciesName(int species) {
            return species > 0 && species < game.data.species.count() ? game.data.species.get(species).name() : "?";
        }

        @Override public int playerGender() { return game.save.look == null ? 0 : game.save.look.body(); }
        @Override public int playerX() { return px; }
        @Override public int playerY() { return py; }
        @Override public int playerFacing() { return facing.ordinal(); }
    }


    public List<Npc> npcs() {
        return npcs;
    }

    private Npc npcAt(int x, int y) {
        for (Npc n : npcs) if (n.occupies(x, y)) return n;
        return null;
    }

    /**
     * Un PNJ peut-il marcher sur cette case ? Comme sur GBA : pas d'obstacle, même niveau d'élévation
     * que lui (l'eau est à un autre niveau : un PNJ à pied n'y entre pas), pas de corniche, ni joueur,
     * ni autre PNJ, ni warp.
     */
    private boolean npcCanEnter(Npc n, int x, int y) {
        if (!map.layout.inBounds(x, y)) return false;
        int block = map.layout.blockAt(x, y);
        if (MapLayout.collision(block) != 0) return false;
        int z = MapLayout.elevation(block), own = n.event.elevation();
        if (z != 0 && z != 15 && own != 0 && own != 15 && z != own) return false;
        int behavior = map.behaviorAt(x, y);
        if (behavior >= 0x38 && behavior <= 0x3B) return false;      // corniches
        if (behavior >= 0x10 && behavior <= 0x15) return false;      // étang, eau profonde, cascade, mer
        if (x == px && y == py) return false;
        if (moving && x == px + facing.dx && y == py + facing.dy) return false;
        if (map.warpAt(x, y).isPresent()) return false;
        return npcAt(x, y) == null;
    }

    /** Comportement « comptoir » : on parle au vendeur par-dessus. */
    private static final int BEHAVIOR_COUNTER = 0x80;

    /** A face à un PNJ ou un panneau : il se tourne vers le joueur et parle. */
    private boolean interact() {
        int tx = px + facing.dx, ty = py + facing.dy;
        Npc n = npcAt(tx, ty);
        if (n == null && map.layout.inBounds(tx, ty) && map.behaviorAt(tx, ty) == BEHAVIOR_COUNTER) {
            n = npcAt(tx + facing.dx, ty + facing.dy);
        }
        if (n != null) {
            if (n.event.script() >= 0) {
                speaker = n;
                startScript(n.event.script(), n.event.localId());
                return true;
            }
            n.face(opposite(facing).ordinal());
            return true;
        }
        for (SignEvent s : map.signs) {
            if (s.x() != tx || s.y() != ty || !s.readable()) continue;
            boolean facingOk = switch (s.kind()) {
                case 1 -> facing == Direction.UP;
                case 2 -> facing == Direction.DOWN;
                case 3 -> facing == Direction.RIGHT;
                case 4 -> facing == Direction.LEFT;
                default -> true;
            };
            if (!facingOk) continue;
            startScript(s.script(), -1);
            return true;
        }
        return false;
    }

    // Repère du dernier rendu (pour placer les bulles de dialogue)
    private double lastOriginX, lastOriginY;
    private int lastZoom = 1;

    /** Rectangle du sprite d'un PNJ dans la fenêtre. */
    private java.awt.geom.Rectangle2D screenBounds(Npc n) {
        double i = game.interpolation();
        double wx = n.exactX(i) + (T - n.width()) / 2.0, wy = n.exactY(i) + T - n.height();
        // le haut du sprite est souvent vide (sprites 16x32) : on vise la tête
        double top = wy + (n.height() >= 32 ? 8 : 0);
        return new java.awt.geom.Rectangle2D.Double(lastOriginX + wx * lastZoom, lastOriginY + top * lastZoom,
                n.width() * lastZoom, (wy + n.height() - top) * lastZoom);
    }

    private java.awt.geom.Rectangle2D tileBounds(int x, int y) {
        return new java.awt.geom.Rectangle2D.Double(lastOriginX + x * T * lastZoom, lastOriginY + y * T * lastZoom,
                T * lastZoom, T * lastZoom);
    }

    private List<String> text(int script) {
        String player = game.save.name == null || game.save.name.isBlank() ? "Dresseur" : game.save.name;
        return ScriptText.firstMessage(game.data.rom, script, player, "RIVAL");
    }

    private static Direction opposite(Direction d) {
        return switch (d) {
            case DOWN -> Direction.UP;
            case UP -> Direction.DOWN;
            case LEFT -> Direction.RIGHT;
            case RIGHT -> Direction.LEFT;
        };
    }

    // ------------------------------------------------------------------ tuiles animées

    /** Eau, fleurs… : recopie les frames dues et redessine les cases concernées des cartes affichées. */
    private void animateTiles() {
        List<TileAnims.Step> steps = game.data.tileAnims.tick();
        if (steps.isEmpty()) return;
        List<GameMap> shown = new ArrayList<>();
        shown.add(map);
        for (Connection c : map.connections) {
            GameMap t = loadOrNull(c.targetGroup(), c.targetNum());
            if (t != null) shown.add(t);
        }
        for (TileAnims.Step step : steps) {
            TileAnims.Anim a = step.anim();
            Set<Tileset> done = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
            for (GameMap m : shown) {
                Tileset ts = m.layout.primary;
                if (!game.data.tileAnims.appliesTo(ts, a)) continue;
                if (done.add(ts)) game.data.tileAnims.apply(ts, step);
                int[] cells = animCells.computeIfAbsent(m.key() + "#" + a.dest(),
                        k -> game.data.renderer.cellsUsing(m.layout, a.dest(), a.dest() + a.tiles()));
                if (cells.length > 0) game.data.renderer.redraw(m.layout, render(m), cells);
            }
        }
    }

    /** Arrivée « avec écran noir » sur une carte (warp, téléportation, chargement). */
    public void enterMap(GameMap target, int x, int y) {
        boolean newPlace = map == null || !map.name.equals(target.name);
        switchMap(target, x, y);
        elevation = MapLayout.elevation(target.layout.blockAt(x, y));
        if (elevation == 15) elevation = 3;
        moving = false;
        facing = Direction.DOWN;
        hidden = false;
        doorPhase = DoorPhase.NONE;
        door = null;
        doorStage = -1;
        if (newPlace) popupTimer = POPUP_FRAMES;
        rememberPosition();
        game.save.store(); // sauvegarde auto à chaque entrée de carte
    }

    /** Bascule de carte sans écran noir (liaisons extérieures). */
    private void switchMap(GameMap target, int x, int y) {
        boolean newPlace = map != null && !map.name.equals(target.name);
        boolean changed = map != target;
        map = target;
        px = x;
        py = y;
        if (changed) mapLoaded();
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

        // 3. Porte en cours d'animation (remplace le métatile de la porte, et celui du dessus si elle est haute)
        if (door != null && doorStage >= 0) {
            BufferedImage[] frames = game.data.doors.frames(door, map.layout, game.data.renderer);
            int up = DoorAnims.heightInTiles(door) - 1;
            g.drawImage(frames[doorStage], doorX * T - camX, (doorY - up) * T - camY, null);
        }
    }

    /** Fenêtre réelle : joueur, herbe devant les jambes, toits et cimes, puis l'interface. */
    @Override
    public void renderHiRes(Graphics2D g, int w, int h) {
        int zoom = game.blitScale();
        double camX = camExactX(), camY = camExactY();
        // Origine du monde dans la fenêtre, alignée exactement sur l'image du sol
        double originX = game.blitX() - Math.floor(camX) * zoom;
        double originY = game.blitY() - Math.floor(camY) * zoom;
        lastOriginX = originX;
        lastOriginY = originY;
        lastZoom = zoom;

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
        if (moving && (distance > T || hopStep)) {
            hop = (int) Math.round(Math.sin(Math.PI * exactProgress() / distance) * (distance > T ? 10 : 6));
        }
        // Comme sur GBA : pied en avant pendant la 1re moitié du pas, position de repos pendant la 2de
        int step = moving && distance == T && animateStep && progress < distance / 2 ? (stepLeftFoot ? 1 : 2) : 0;
        if (playerInPlace > 0) step = (playerInPlace / 8) % 2 == 0 ? (stepLeftFoot ? 1 : 2) : 0; // marche sur place
        // Course, rythme GBA : phase en l'air (jambes groupées) sur 5/8 de la case, puis appui sur un pied ;
        // le pied d'appui alterne à chaque case (gauche, droite, gauche…)
        boolean run = moving && running;
        int bob = 0;
        if (run) {
            step = progress >= distance * 0.625 - 1e-6 ? (stepLeftFoot ? 1 : 2) : 0;
            // Rebond du corps lissé au pixel de l'écran : haut en l'air, bas à l'appui (sur GBA c'est un saut
            // d'1 pixel, trop sec une fois agrandi). Les frames, elles, sont recalées à hauteur de tête constante.
            double phase = exactProgress() / distance;
            bob = (int) Math.round(zoom * (1 - Math.sin(Math.PI * phase)));
        }
        // Joueur calé sur un pixel entier de l'écran : chaque pixel du sprite fait exactement « zoom » pixels
        // (sinon, en nearest-neighbor, certains pixels font 2 ou 4 de large et le sprite tremble en marchant)
        Graphics2D p = (Graphics2D) g.create();
        p.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        p.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_OFF);
        // Personnages plus haut que le joueur : derrière lui
        double interp = game.interpolation();
        for (Npc n : npcs) if (n.exactY(interp) <= ey) drawNpc(g, n, originX, originY, zoom, interp);
        p.translate(Math.round(originX + ex * zoom), Math.round(originY + ey * zoom) + bob);
        p.scale(zoom, zoom);
        if (!hidden) avatar.drawOverworld(p, 0, 0, facing, step, hop, run);
        if (!hidden && !moving && map.isLandEncounterAt(px, py) && map.behaviorAt(px, py) == 0x02) {
            BufferedImage below = current.below();
            int gx = px * T, gy = py * T + 9;
            if (gx >= 0 && gy >= 0 && gx + T <= below.getWidth() && gy + 7 <= below.getHeight()) {
                p.drawImage(below.getSubimage(gx, gy, T, 7), 0, 9, null);
            }
        }
        p.dispose();
        for (Npc n : npcs) if (n.exactY(interp) > ey) drawNpc(g, n, originX, originY, zoom, interp);

        // Calque supérieur (cimes, toits) des cartes liées puis de la carte courante
        for (Connection c : map.connections) {
            GameMap t = loadOrNull(c.targetGroup(), c.targetNum());
            if (t == null) continue;
            int[] o = originOf(c, t);
            world.drawImage(render(t).above(), o[0] * T, o[1] * T, null);
        }
        world.drawImage(current.above(), 0, 0, null);
        world.dispose();

        // Bulles « ! » / « ? » au-dessus des têtes
        for (Npc n : npcs) {
            if (n.emote() >= 0 && n.visible()) {
                drawEmote(g, n.emote(), originX + (n.exactX(interp) + T / 2.0) * zoom,
                        originY + (n.exactY(interp) + T - n.height() - 2) * zoom, zoom);
            }
        }
        if (playerEmoteTimer > 0 && !hidden) {
            drawEmote(g, playerEmote, originX + (ex + T / 2.0) * zoom, originY + (ey - T - 2) * zoom, zoom);
        }

        drawHud(g, w, h);
        if (monPic > 0) drawMonPic(g, w, h);
        box.render(g, w, h);
    }

    /** Bulle d'émotion : 0 « ! », 1 « ? », 2 « !! », 3 « ♥ », 4 « … ». */
    private void drawEmote(Graphics2D g, int kind, double cx, double bottom, int zoom) {
        double s = Math.max(2, zoom) / 3.0;
        double bw = 26 * s, bh = 24 * s, x = cx - bw / 2, y = bottom - bh - 4 * s;
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        java.awt.geom.Path2D tail = new java.awt.geom.Path2D.Double();
        tail.moveTo(cx - 5 * s, y + bh - 1);
        tail.lineTo(cx + 5 * s, y + bh - 1);
        tail.lineTo(cx, y + bh + 6 * s);
        tail.closePath();
        java.awt.geom.Area a = new java.awt.geom.Area(new java.awt.geom.RoundRectangle2D.Double(x, y, bw, bh, 10 * s, 10 * s));
        a.add(new java.awt.geom.Area(tail));
        g.setColor(java.awt.Color.WHITE);
        g.fill(a);
        g.setColor(new java.awt.Color(0x262838));
        g.setStroke(new java.awt.BasicStroke((float) (1.6 * s)));
        g.draw(a);
        String t = switch (kind) {
            case 1 -> "?";
            case 2 -> "!!";
            case 3 -> "♥";
            case 4 -> "…";
            default -> "!";
        };
        g.setColor(kind == 3 ? new java.awt.Color(0xE84855) : new java.awt.Color(0xE04040));
        g.setFont(dev.orvaen.mmo.engine.HiUi.font((float) (17 * s), true));
        dev.orvaen.mmo.engine.HiUi.centered(g, t, x, y - s, bw, bh);
    }

    /** Portrait d'un Pokémon montré par un script (choix du starter…). */
    private void drawMonPic(Graphics2D g, int w, int h) {
        BufferedImage img = game.data.species.frontSprite(monPic);
        if (img == null) return;
        double s = Math.max(0.8, Math.min(1.6, h / 720.0));
        double size = Math.floor(3 * s) * 64, pad = 18 * s;
        double x = (w - size) / 2 - pad, y = h * 0.16;
        dev.orvaen.mmo.engine.HiUi.fill(g, new java.awt.Color(0, 0, 0, 70), x + 3, y + 5, size + pad * 2, size + pad * 2, 16 * s);
        dev.orvaen.mmo.engine.HiUi.fill(g, new java.awt.Color(0xF2F3F8), x, y, size + pad * 2, size + pad * 2, 16 * s);
        dev.orvaen.mmo.engine.HiUi.stroke(g, dev.orvaen.mmo.engine.HiUi.PANEL_BORDER, x, y, size + pad * 2, size + pad * 2, 16 * s, 2f);
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(img, (int) (x + pad), (int) (y + pad), (int) size, (int) size, null);
    }

    /** Un PNJ, pieds en bas de sa case, calé au pixel de l'écran comme le joueur. */
    private void drawNpc(Graphics2D g, Npc n, double originX, double originY, int zoom, double interp) {
        if (!n.visible()) return;
        BufferedImage img = n.frame();
        double wx = n.exactX(interp) + (T - n.width()) / 2.0, wy = n.exactY(interp) + T - n.height();
        double lift = n.hopHeight(interp);
        Graphics2D q = (Graphics2D) g.create();
        q.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        q.translate(Math.round(originX + wx * zoom), Math.round(originY + wy * zoom));
        q.scale(zoom, zoom);
        if (n.height() >= 32) {
            q.setColor(new java.awt.Color(0, 0, 0, 60));
            q.fillOval((n.width() - 10) / 2, n.height() - 4, 10, 4);
        }
        q.drawImage(img, 0, (int) -Math.round(lift), null);
        q.dispose();
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
