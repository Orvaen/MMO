package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.audio.BattleSounds;
import dev.orvaen.mmo.battle.Battle;
import dev.orvaen.mmo.battle.BattleEvent;
import dev.orvaen.mmo.battle.BattleGraphics;
import dev.orvaen.mmo.battle.Move;
import dev.orvaen.mmo.battle.Party;
import dev.orvaen.mmo.battle.Pokemon;
import dev.orvaen.mmo.data.Species;
import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.HiUi;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.Input.Button;
import dev.orvaen.mmo.engine.Scene;
import dev.orvaen.mmo.player.PlayerGraphics;
import dev.orvaen.mmo.rom.TextCodec;
import dev.orvaen.mmo.script.Trainers;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * L'écran de combat, comme dans Rouge Feu : transition, fond et boîte de texte de la ROM, entrée des
 * Pokémon, menus ATTAQUE / SAC / POKéMON / FUITE, barres de PV et d'expérience animées, musiques.
 * <p>
 * Les règles sont dans {@link Battle} (paquet {@code battle}) ; cette scène ne fait que :
 * <ol>
 *   <li>demander au joueur ce qu'il veut faire (menus) ;</li>
 *   <li>donner ce choix au moteur, qui renvoie la liste des {@link BattleEvent} du tour ;</li>
 *   <li>jouer ces événements un par un, chacun avec son animation (voir {@link #begin}).</li>
 * </ol>
 */
public final class BattleScene implements Scene {

    /** Où en est l'écran. */
    private enum Mode { TRANSITION, INTRO, EVENTS, ACTIONS, MOVES, BAG, PARTY, LEARN_ASK, LEARN_PICK, OUTRO }

    private static final int TRANSITION_FRAMES = 64, INTRO_FRAMES = 50;
    private static final int FOE_X = 144, FOE_Y = 8, PLAYER_X = 36, PLAYER_Y = 48;   // sprites 64x64 (écran GBA)
    private static final String[] ACTIONS = {"ATTAQUE", "SAC", "POKéMON", "FUITE"};

    /** Ce qu'on voit d'un côté du terrain (peut être en retard sur le moteur, le temps des animations). */
    private static final class SideView {
        Pokemon mon;
        BufferedImage sprite, dark;
        boolean visible, boxVisible;
        double hp, hpTarget;
        int maxHp, level, status;
        double exp, expTarget;                 // barre d'expérience (0 à 1), joueur seulement
        double dx, dy, scale = 1, white;       // animations : décalage, taille, blanchiment
        boolean blink;
        double faint;                          // 0 à 1 : le Pokémon descend hors de l'écran
    }

    private final Game game;
    private final BattleGraphics gfx;
    private final BattleSounds sfx;
    private final Battle battle;
    private final List<Pokemon> team;
    private final boolean trainerBattle;
    private final String trainerName;
    private final BufferedImage trainerPic;
    private final List<String> defeatLines;
    private final int terrain;
    private final PlayerSprite avatar;
    private final SideView[] views = {new SideView(), new SideView()};

    private Mode mode = Mode.TRANSITION;
    private int frame, modeTimer;
    private final Deque<BattleEvent> queue = new ArrayDeque<>();
    private BattleEvent current;
    private int eventTimer;

    // Message en cours
    private String message = "";
    private double shown;                       // caractères affichés (effet machine à écrire)
    private int messageWait;

    // Menus
    private int actionCursor, moveCursor, listCursor;
    private boolean forcedSwitch;
    private int learnIndex = -1, learnMove;

    // Animations d'entrée en scène
    private double trainerOffset = -1;          // portrait du dresseur adverse (-1 : caché)
    private double playerTrainerOffset;         // dresseur du joueur, vu de dos (0 : en place, 1 : sorti)
    private boolean playerTrainerShown = true;
    private BallAnim ball;
    private int statFlashSide = -1, statFlashTimer;
    private boolean statFlashUp;
    private String status = "";

    /** Boule d'énergie d'une attaque spéciale ({@code k} : avancement de 0 à 1). */
    private record Projectile(int fromSide, double k, Color color) {}

    private Projectile projectile;

    /** La Poké Ball en vol ou qui tremble. */
    private record BallAnim(double x, double y, int frameIndex, double angle) {}

    // ================================================================== création

    /** Rencontre sauvage dans l'herbe. */
    public BattleScene(Game game, int species, int level) {
        this(game, species, level, BattleGraphics.GRASS);
    }

    /** Rencontre sauvage sur un terrain donné (voir {@link BattleGraphics#GRASS}…). */
    public BattleScene(Game game, int species, int level, int terrain) {
        this(game, null, List.of(Pokemon.wild(game.data, species, level, new Random())), null, -1, terrain);
    }

    /** Combat de dresseur lancé par un script. {@code defeatText} : ce qu'il dit en perdant (pointeur ROM). */
    public static BattleScene trainer(Game game, Trainers.Trainer trainer, int defeatText) {
        List<Pokemon> foes = new ArrayList<>();
        if (trainer != null) {
            for (int[] m : trainer.party()) {
                int[] moves = m.length >= 7 ? new int[]{m[3], m[4], m[5], m[6]} : null;
                foes.add(Pokemon.trainer(game.data, m[0], m[1], m.length > 2 ? m[2] : 0, moves));
            }
        }
        if (foes.isEmpty()) foes.add(Pokemon.trainer(game.data, 1, 5, 0, null));
        String name = trainer == null || trainer.name().isBlank() ? "DRESSEUR" : trainer.name();
        BufferedImage pic = trainer == null ? null : PlayerGraphics.trainerPic(game.data.rom, trainer.pic());
        return new BattleScene(game, name, foes, pic, defeatText, BattleGraphics.BUILDING);
    }

    private BattleScene(Game game, String trainerName, List<Pokemon> foes, BufferedImage trainerPic, int defeatText,
                        int terrain) {
        this.game = game;
        this.gfx = game.data.battleGraphics();
        this.sfx = BattleSounds.of(game.data.rom.profile());
        this.trainerBattle = trainerName != null;
        this.trainerName = trainerName;
        this.trainerPic = trainerPic;
        this.terrain = terrain;
        this.avatar = new PlayerSprite(game.data.player, game.save.look);
        this.team = Party.load(game.data, game.save);
        if (team.isEmpty() || team.stream().allMatch(Pokemon::fainted)) {
            // sans Pokémon en forme (ne devrait pas arriver) : un Pokémon de secours, soigné
            if (team.isEmpty()) team.add(Pokemon.wild(game.data, 1, 5, new Random(1)));
            for (Pokemon p : team) p.heal();
        }
        String player = game.save.name == null || game.save.name.isBlank() ? "Vous" : game.save.name;
        this.battle = new Battle(game.data, team, new ArrayList<>(foes), trainerName, player, new Random());
        this.defeatLines = defeatText >= 0
                ? TextCodec.dialogue(game.data.rom, defeatText, 512, player, rivalName()) : List.of();
        for (Pokemon f : foes) game.save.seen.add(f.species);
        game.data.audio.playBgm(trainerBattle ? game.data.songs.vsTrainer() : game.data.songs.vsWild());
    }

    private String rivalName() {
        String r = game.save.rivalName;
        return r == null || r.isBlank() ? "BLUE" : r;
    }

    // ================================================================== boucle

    /** Pendant la transition, le monde reste visible derrière les flashs. */
    @Override
    public boolean isOverlay() {
        return mode == Mode.TRANSITION;
    }

    @Override
    public void update(Input in) {
        frame++;
        modeTimer++;
        if (statFlashTimer > 0) statFlashTimer--;
        animateBars();
        switch (mode) {
            case TRANSITION -> {
                if (modeTimer >= TRANSITION_FRAMES) startIntro();
            }
            case INTRO -> updateIntro();
            case EVENTS -> updateEvents(in);
            case ACTIONS -> updateActions(in);
            case MOVES -> updateMoves(in);
            case BAG -> updateBag(in);
            case PARTY -> updateParty(in);
            case LEARN_ASK -> updateLearnAsk(in);
            case LEARN_PICK -> updateLearnPick(in);
            case OUTRO -> updateOutro(in);
        }
    }

    private void go(Mode m) {
        mode = m;
        modeTimer = 0;
    }

    private void startIntro() {
        go(Mode.INTRO);
        trainerOffset = trainerBattle ? 0 : -1;
        playerTrainerOffset = 0;
    }

    /** Entrée : l'adversaire glisse depuis la gauche (en ombre), le dresseur du joueur depuis la droite. */
    private void updateIntro() {
        if (modeTimer < INTRO_FRAMES) return;
        enqueue(battle.start());
        go(Mode.EVENTS);
    }

    private double introSlide() {
        if (mode != Mode.INTRO) return 0;
        double t = Math.min(1, modeTimer / (double) INTRO_FRAMES);
        return 1 - t;                                       // 1 → 0
    }

    // ================================================================== événements du tour

    private void enqueue(List<BattleEvent> events) {
        queue.addAll(events);
    }

    private void updateEvents(Input in) {
        if (current == null) {
            current = queue.poll();
            if (current == null) {
                afterEvents();
                return;
            }
            eventTimer = 0;
            begin(current);
        }
        eventTimer++;
        if (step(current, in)) current = null;
    }

    /** Tous les événements sont joués : menu suivant, remplacement forcé, ou fin du combat. */
    private void afterEvents() {
        if (mode != Mode.EVENTS) return;
        if (battle.over()) {
            finishBattle();
        } else if (battle.mustChooseReplacement()) {
            forcedSwitch = true;
            listCursor = firstHealthy();
            go(Mode.PARTY);
        } else {
            setMessage("Que doit faire " + battle.active(Battle.PLAYER).name() + " ?");
            shown = message.length();
            go(Mode.ACTIONS);
        }
    }

    /** Début d'un événement : son, mise à jour de l'affichage. */
    private void begin(BattleEvent e) {
        switch (e) {
            case BattleEvent.Message m -> setMessage(m.text());
            case BattleEvent.MoveUsed u -> { }
            case BattleEvent.Damage d -> {
                views[d.side()].hpTarget = d.hpAfter();
                play(d.effectiveness() > 10 ? sfx.hitSuper() : d.effectiveness() < 10 ? sfx.hitWeak() : sfx.hitNormal());
            }
            case BattleEvent.Heal h -> views[h.side()].hpTarget = h.hpAfter();
            case BattleEvent.StatChange s -> {
                statFlashSide = s.side();
                statFlashUp = s.up();
                statFlashTimer = 36;
                play(s.up() ? sfx.statUp() : sfx.statDown());
            }
            case BattleEvent.Status s -> views[s.side()].status = s.status();
            case BattleEvent.Faint f -> play(sfx.faint());
            case BattleEvent.SendOut s -> beginSendOut(s);
            case BattleEvent.Withdraw w -> { }
            case BattleEvent.Exp x -> {
                SideView v = views[Battle.PLAYER];
                if (x.index() == battle.activeIndex(Battle.PLAYER) && v.mon != null) {
                    v.expTarget = expFraction(v.mon, v.level, x.expAfter());
                    play(sfx.exp());
                }
            }
            case BattleEvent.LevelUp l -> {
                game.data.audio.playFanfare(sfx.levelUp());
                SideView v = views[Battle.PLAYER];
                if (l.index() == battle.activeIndex(Battle.PLAYER)) {
                    v.level = l.level();
                    v.maxHp = l.maxHp();
                    v.hp = v.hpTarget = l.hp();
                    v.exp = v.expTarget = 0;
                }
            }
            case BattleEvent.LearnPrompt p -> {
                learnIndex = p.index();
                learnMove = p.move();
                Pokemon mon = team.get(p.index());
                setMessage(mon.name() + " veut apprendre " + moveName(p.move()) + ". Mais " + mon.name()
                        + " connaît déjà 4 attaques. Oublier une attaque ?");
                listCursor = 0;
            }
            case BattleEvent.BallThrow b -> play(sfx.ballThrow());
            case BattleEvent.End end -> { }
        }
    }

    /** Une frame de l'événement en cours ; renvoie vrai quand il est fini. */
    private boolean step(BattleEvent e, Input in) {
        int t = eventTimer;
        switch (e) {
            case BattleEvent.Message m -> {
                return messageDone(in);
            }
            case BattleEvent.MoveUsed u -> {
                SideView v = views[u.side()];
                Move mv = u.move();
                if (!mv.status() && !mv.physical()) {
                    // attaque spéciale : une boule d'énergie de la couleur du type traverse le terrain
                    projectile = t < 20 ? new Projectile(u.side(), t / 20.0, new Color(Species.typeColor(mv.type()))) : null;
                    return t >= 20;
                }
                // attaque physique (ou de statut) : le Pokémon s'élance vers l'adversaire puis revient
                double dir = u.side() == Battle.PLAYER ? 1 : -1;
                double reach = mv.status() ? 3 : 10;
                double lunge = t < 8 ? t / 8.0 : t < 16 ? (16 - t) / 8.0 : 0;
                v.dx = dir * reach * lunge;
                v.dy = -dir * reach * 0.4 * lunge;
                if (t >= 18) {
                    v.dx = v.dy = 0;
                    return true;
                }
                return false;
            }
            case BattleEvent.Damage d -> {
                SideView v = views[d.side()];
                v.blink = t < 24 && (t / 3) % 2 == 0;
                return t >= 24 && Math.abs(v.hp - v.hpTarget) < 0.01;
            }
            case BattleEvent.Heal h -> {
                return t >= 8 && Math.abs(views[h.side()].hp - views[h.side()].hpTarget) < 0.01;
            }
            case BattleEvent.StatChange s -> {
                return t >= 30;
            }
            case BattleEvent.Status s -> {
                views[s.side()].white = t < 16 ? 0.5 * Math.sin(t / 16.0 * Math.PI) : 0;
                return t >= 16;
            }
            case BattleEvent.Faint f -> {
                SideView v = views[f.side()];
                v.faint = Math.min(1, t / 24.0);
                if (t >= 26) {
                    v.visible = false;
                    v.boxVisible = false;
                    v.faint = 0;
                    return true;
                }
                return false;
            }
            case BattleEvent.SendOut s -> {
                return stepSendOut(s, t);
            }
            case BattleEvent.Withdraw w -> {
                SideView v = views[w.side()];
                v.scale = Math.max(0, 1 - t / 14.0);
                v.white = Math.min(1, t / 8.0);
                if (t >= 14) {
                    v.visible = false;
                    v.boxVisible = false;
                    v.scale = 1;
                    v.white = 0;
                    return true;
                }
                return false;
            }
            case BattleEvent.Exp x -> {
                SideView v = views[Battle.PLAYER];
                return t >= 6 && Math.abs(v.exp - v.expTarget) < 0.005;
            }
            case BattleEvent.LevelUp l -> {
                return t >= 20;
            }
            case BattleEvent.LearnPrompt p -> {
                if (!messageDone(in)) return false;
                go(Mode.LEARN_ASK);
                listCursor = 0;
                return true;
            }
            case BattleEvent.BallThrow b -> {
                return stepBall(b, t);
            }
            case BattleEvent.End end -> {
                return true;
            }
        }
    }

    // ------------------------------------------------------------------ entrée d'un Pokémon

    private void beginSendOut(BattleEvent.SendOut s) {
        SideView v = views[s.side()];
        Pokemon mon = s.side() == Battle.PLAYER ? team.get(s.index()) : battle.active(Battle.FOE);
        v.mon = mon;
        v.sprite = s.side() == Battle.PLAYER ? game.data.species.backSprite(mon.species) : game.data.species.frontSprite(mon.species);
        v.dark = darken(v.sprite);
        v.hp = v.hpTarget = s.hp();
        v.maxHp = s.maxHp();
        v.level = s.level();
        v.status = s.status();
        if (s.side() == Battle.PLAYER) v.exp = v.expTarget = expFraction(mon, s.level(), mon.exp);
        v.faint = 0;
        v.scale = 1;
        v.white = 0;
        v.dx = v.dy = 0;
        boolean wildAlreadyShown = s.side() == Battle.FOE && !trainerBattle;
        if (wildAlreadyShown) {
            v.visible = true;
            v.boxVisible = true;
        } else {
            v.visible = false;
            v.boxVisible = false;
        }
    }

    /** Lancer de Poké Ball : le dresseur s'efface, la ball vole, s'ouvre, et le Pokémon grandit en blanc. */
    private boolean stepSendOut(BattleEvent.SendOut s, int t) {
        SideView v = views[s.side()];
        if (s.side() == Battle.FOE && !trainerBattle) {
            // le Pokémon sauvage passe de la silhouette à ses couleurs
            v.white = 0;
            return t >= 8;
        }
        boolean player = s.side() == Battle.PLAYER;
        // 0-14 : le dresseur sort de l'écran (seulement la première fois)
        if (player && playerTrainerShown) playerTrainerOffset = Math.min(1, t / 14.0);
        if (!player && trainerOffset >= 0) trainerOffset = Math.min(1, t / 14.0);
        if (t == 14) play(sfx.ballThrow());
        if (t >= 14 && t < 30) {
            double k = (t - 14) / 16.0;
            double sx = player ? 20 : 260, sy = player ? 100 : 20;
            double ex = player ? PLAYER_X + 32 : FOE_X + 32, ey = player ? PLAYER_Y + 40 : FOE_Y + 44;
            double x = sx + (ex - sx) * k, y = sy + (ey - sy) * k - Math.sin(k * Math.PI) * 30;
            ball = new BallAnim(x, y, 0, k * 6);
        }
        if (t == 30) {
            ball = new BallAnim(player ? PLAYER_X + 32 : FOE_X + 32, player ? PLAYER_Y + 40 : FOE_Y + 44, 2, 0);
            play(sfx.ballOpen());
            v.visible = true;
            v.scale = 0.1;
            v.white = 1;
        }
        if (t > 30) {
            ball = t < 36 ? ball : null;
            v.scale = Math.min(1, 0.1 + (t - 30) / 12.0);
            v.white = Math.max(0, 1 - (t - 34) / 10.0);
        }
        if (t >= 46) {
            if (player) playerTrainerShown = false;
            else trainerOffset = -1;
            v.scale = 1;
            v.white = 0;
            v.boxVisible = true;
            ball = null;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ Poké Ball lancée sur un Pokémon sauvage

    private boolean stepBall(BattleEvent.BallThrow b, int t) {
        SideView foe = views[Battle.FOE];
        double tx = FOE_X + 32, ty = FOE_Y + 30;
        if (t < 24) {                                            // vol
            double k = t / 24.0;
            ball = new BallAnim(30 + (tx - 30) * k, 104 + (ty - 104) * k - Math.sin(k * Math.PI) * 40, 0, k * 8);
            return false;
        }
        if (!trainerBattle) {
            if (t == 24) {
                play(sfx.ballOpen());
                ball = new BallAnim(tx, ty, 2, 0);
            }
            if (t >= 24 && t < 40) {                             // le Pokémon est aspiré dans la ball
                foe.white = Math.min(1, (t - 24) / 6.0);
                foe.scale = Math.max(0, 1 - (t - 24) / 16.0);
                return false;
            }
            if (t == 40) {
                foe.visible = false;
                foe.scale = 1;
                foe.white = 0;
                ball = new BallAnim(tx, ty, 0, 0);
            }
            if (t >= 40 && t < 56) {                             // la ball tombe et rebondit
                double k = (t - 40) / 16.0;
                double floor = FOE_Y + 52;
                ball = new BallAnim(tx, ty + (floor - ty) * Math.min(1, k * 1.6) - (k > 0.62 ? Math.sin((k - 0.62) / 0.38 * Math.PI) * 6 : 0), 0, 0);
                if (t == 50) play(sfx.ballBounce());
                return false;
            }
            int shakeStart = 56, perShake = 34;
            int shakeT = t - shakeStart;
            int shakeIndex = shakeT / perShake;
            if (shakeIndex < b.shakes()) {
                int local = shakeT % perShake;
                if (local == 0) play(sfx.ballShake());
                double angle = local < 16 ? Math.sin(local / 16.0 * Math.PI * 2) * 0.35 : 0;
                ball = new BallAnim(tx, FOE_Y + 52, 0, angle);
                return false;
            }
            int end = shakeStart + b.shakes() * perShake;
            if (b.caught()) {
                if (t == end) {
                    play(sfx.ballClick());
                    game.data.audio.playBgm(sfx.caught());
                }
                ball = new BallAnim(tx, FOE_Y + 52, 0, 0);
                return t >= end + 20;
            }
            if (t == end) {                                       // il s'échappe
                play(sfx.ballOpen());
                ball = null;
                foe.visible = true;
                foe.white = 1;
                foe.scale = 0.3;
            }
            foe.scale = Math.min(1, 0.3 + (t - end) / 10.0);
            foe.white = Math.max(0, 1 - (t - end) / 10.0);
            if (t >= end + 12) {
                foe.scale = 1;
                foe.white = 0;
                return true;
            }
            return false;
        }
        // contre un dresseur : la ball est renvoyée
        if (t < 36) {
            double k = (t - 24) / 12.0;
            ball = new BallAnim(tx - k * 40, ty - Math.sin(k * Math.PI) * 20 + k * 30, 0, -k * 6);
            return false;
        }
        ball = null;
        return true;
    }

    // ================================================================== messages

    private void setMessage(String text) {
        message = text;
        shown = 0;
        messageWait = 0;
    }

    /** Machine à écrire, puis petite pause (A ou B pour accélérer). */
    private boolean messageDone(Input in) {
        boolean press = in.pressed(Button.A) || in.pressed(Button.B) || in.mousePressed();
        if (shown < message.length()) {
            shown = press ? message.length() : Math.min(message.length(), shown + 1.5);
            return false;
        }
        messageWait++;
        return press || messageWait >= 48;
    }

    // ================================================================== menus

    private void updateActions(Input in) {
        actionCursor = gridMove(in, actionCursor);
        int click = clickedOption(in, actionRects());
        if (click >= 0) actionCursor = click;
        if (in.pressed(Button.A) || click >= 0) {
            play(sfx.select());
            switch (actionCursor) {
                case 0 -> {
                    Pokemon p = battle.active(Battle.PLAYER);
                    boolean anyPp = false;
                    for (int i = 0; i < 4; i++) anyPp |= p.moves[i] != 0 && p.pp[i] > 0;
                    if (!anyPp) {
                        runTurn(battle.useMove(-1));
                    } else {
                        moveCursor = Math.min(moveCursor, moveCount(p) - 1);
                        go(Mode.MOVES);
                    }
                }
                case 1 -> {
                    listCursor = 0;
                    go(Mode.BAG);
                }
                case 2 -> {
                    forcedSwitch = false;
                    listCursor = battle.activeIndex(Battle.PLAYER);
                    go(Mode.PARTY);
                }
                default -> runTurn(battle.run());
            }
        }
    }

    private void updateMoves(Input in) {
        Pokemon p = battle.active(Battle.PLAYER);
        int n = moveCount(p);
        moveCursor = Math.min(gridMove(in, moveCursor), n - 1);
        int click = clickedOption(in, moveRects());
        if (click >= 0 && click < n) moveCursor = click;
        if (in.pressed(Button.B)) {
            go(Mode.ACTIONS);
            return;
        }
        if (in.pressed(Button.A) || (click >= 0 && click < n)) {
            if (p.pp[moveCursor] <= 0) {
                setMessage("Plus de PP pour cette attaque !");
                return;
            }
            play(sfx.select());
            runTurn(battle.useMove(moveCursor));
        }
    }

    private void updateBag(Input in) {
        if (in.repeat(Button.DOWN) || in.repeat(Button.UP)) listCursor = 1 - listCursor;
        int click = clickedOption(in, bagRects());
        if (click >= 0) listCursor = click;
        if (in.pressed(Button.B) || ((in.pressed(Button.A) || click >= 0) && listCursor == 1)) {
            go(Mode.ACTIONS);
            return;
        }
        if (in.pressed(Button.A) || click == 0) {
            if (game.save.pokeBalls <= 0) {
                setMessage("Vous n'avez plus de POKé BALL !");
                shown = message.length();
                go(Mode.ACTIONS);
                return;
            }
            game.save.pokeBalls--;
            runTurn(battle.throwBall());
        }
    }

    private void updateParty(Input in) {
        int n = team.size();
        if (in.repeat(Button.DOWN)) listCursor = (listCursor + 1) % (n + (forcedSwitch ? 0 : 1));
        if (in.repeat(Button.UP)) listCursor = (listCursor - 1 + n + (forcedSwitch ? 0 : 1)) % (n + (forcedSwitch ? 0 : 1));
        int click = clickedOption(in, partyRects());
        if (click >= 0) listCursor = click;
        boolean back = !forcedSwitch && (in.pressed(Button.B) || ((in.pressed(Button.A) || click >= 0) && listCursor == n));
        if (back) {
            go(Mode.ACTIONS);
            return;
        }
        if ((in.pressed(Button.A) || click >= 0) && listCursor < n) {
            Pokemon p = team.get(listCursor);
            if (p.fainted()) {
                status = p.name() + " n'a plus d'énergie pour se battre !";
                return;
            }
            if (listCursor == battle.activeIndex(Battle.PLAYER) && !forcedSwitch) {
                status = p.name() + " est déjà au combat !";
                return;
            }
            status = "";
            play(sfx.select());
            playerTrainerShown = false;
            if (forcedSwitch) {
                forcedSwitch = false;
                runTurn(battle.replaceFainted(listCursor));
            } else {
                runTurn(battle.switchTo(listCursor));
            }
        }
    }

    private void updateLearnAsk(Input in) {
        if (in.repeat(Button.DOWN) || in.repeat(Button.UP)) listCursor = 1 - listCursor;
        int click = clickedOption(in, yesNoRects());
        if (click >= 0) listCursor = click;
        if (in.pressed(Button.B) || ((in.pressed(Button.A) || click >= 0) && listCursor == 1)) {
            Pokemon mon = team.get(learnIndex);
            queue.addFirst(new BattleEvent.Message(mon.name() + " n'a pas appris " + moveName(learnMove) + "."));
            go(Mode.EVENTS);
            return;
        }
        if (in.pressed(Button.A) || click == 0) {
            listCursor = 0;
            go(Mode.LEARN_PICK);
        }
    }

    private void updateLearnPick(Input in) {
        if (in.repeat(Button.DOWN)) listCursor = (listCursor + 1) % 5;
        if (in.repeat(Button.UP)) listCursor = (listCursor + 4) % 5;
        int click = clickedOption(in, learnRects());
        if (click >= 0) listCursor = click;
        Pokemon mon = team.get(learnIndex);
        if (in.pressed(Button.B) || ((in.pressed(Button.A) || click >= 0) && listCursor == 4)) {
            queue.addFirst(new BattleEvent.Message(mon.name() + " n'a pas appris " + moveName(learnMove) + "."));
            go(Mode.EVENTS);
            return;
        }
        if (in.pressed(Button.A) || (click >= 0 && click < 4)) {
            String old = moveName(mon.moves[listCursor]);
            battle.resolveLearn(learnIndex, learnMove, listCursor);
            queue.addFirst(new BattleEvent.Message("Et… Tadaa ! " + mon.name() + " oublie " + old + " et apprend "
                    + moveName(learnMove) + " !"));
            go(Mode.EVENTS);
        }
    }

    private void runTurn(List<BattleEvent> events) {
        enqueue(events);
        go(Mode.EVENTS);
    }

    /** Déplacement dans une grille 2x2 (ATTAQUE / SAC / POKéMON / FUITE). */
    private static int gridMove(Input in, int c) {
        if (in.repeat(Button.RIGHT) && c % 2 == 0) c++;
        else if (in.repeat(Button.LEFT) && c % 2 == 1) c--;
        else if (in.repeat(Button.DOWN) && c < 2) c += 2;
        else if (in.repeat(Button.UP) && c >= 2) c -= 2;
        return c;
    }

    private static int moveCount(Pokemon p) {
        int n = 0;
        for (int m : p.moves) if (m != 0) n++;
        return Math.max(1, n);
    }

    /** Indice de l'option cliquée (rectangles en coordonnées de la fenêtre), ou -1. */
    private static int clickedOption(Input in, List<Rectangle2D> rects) {
        if (!in.mousePressed()) return -1;
        for (int i = 0; i < rects.size(); i++) if (rects.get(i).contains(in.mouseX(), in.mouseY())) return i;
        return -1;
    }

    // ================================================================== fin du combat

    private void finishBattle() {
        Battle.Outcome o = battle.outcome();
        List<String> lines = new ArrayList<>();
        switch (o) {
            case WON -> {
                game.data.audio.playBgm(trainerBattle ? sfx.victoryTrainer() : sfx.victoryWild());
                if (trainerBattle) {
                    trainerOffset = 0;
                    lines.addAll(defeatLines);
                    game.save.money += battle.prizeMoney();
                }
            }
            case CAUGHT -> {
                Pokemon caught = battle.active(Battle.FOE);   // garde ses PV et son statut, comme dans le jeu
                game.save.caught.add(caught.species);
                if (team.size() < Party.MAX) {
                    team.add(caught);
                } else {
                    lines.add(caught.name() + " est envoyé dans le PC… (pas encore disponible)");
                }
                lines.add(caught.name() + " a été ajouté au POKéDEX.");
            }
            case FLED -> play(sfx.flee());
            case LOST -> {
                game.save.money /= 2;
                for (Pokemon p : team) p.heal();
            }
        }
        Party.store(game.save, team);
        game.save.store();
        outroLines = new ArrayDeque<>(lines);
        if (!outroLines.isEmpty()) setMessage(outroLines.poll());
        else messageWait = 0;
        go(Mode.OUTRO);
    }

    private Deque<String> outroLines = new ArrayDeque<>();
    private boolean leaving;

    private void updateOutro(Input in) {
        if (leaving || !messageDone(in)) return;
        if (!outroLines.isEmpty()) {
            setMessage(outroLines.poll());
            return;
        }
        leaving = true;
        game.fadeThen(game::pop);
    }

    // ================================================================== animations continues

    /** Les barres de PV et d'expérience glissent vers leur nouvelle valeur. */
    private void animateBars() {
        for (SideView v : views) {
            double speed = Math.max(0.25, v.maxHp / 48.0);
            if (v.hp > v.hpTarget) v.hp = Math.max(v.hpTarget, v.hp - speed);
            else if (v.hp < v.hpTarget) v.hp = Math.min(v.hpTarget, v.hp + speed);
            if (v.exp < v.expTarget) v.exp = Math.min(v.expTarget, v.exp + 0.025);
            else if (v.exp > v.expTarget) v.exp = v.expTarget;
        }
    }

    private static double expFraction(Pokemon mon, int level, long exp) {
        if (level >= 100) return 1;
        long from = mon.expForLevel(level), to = mon.expForLevel(level + 1);
        return to <= from ? 0 : Math.max(0, Math.min(1, (exp - from) / (double) (to - from)));
    }

    private int firstHealthy() {
        for (int i = 0; i < team.size(); i++) if (!team.get(i).fainted()) return i;
        return 0;
    }

    private String moveName(int id) {
        return game.data.battle().move(id).name();
    }

    private void play(int song) {
        if (song > 0) game.data.audio.playSe(song);
    }

    /** Silhouette sombre (le Pokémon sauvage qui arrive pendant l'intro). */
    private static BufferedImage darken(BufferedImage src) {
        if (src == null) return null;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int c = src.getRGB(x, y);
                if ((c >>> 24) == 0) continue;
                int r = ((c >> 16) & 0xFF) / 4, g = ((c >> 8) & 0xFF) / 4, b = (c & 0xFF) / 4;
                out.setRGB(x, y, 0xFF000000 | r << 16 | g << 8 | b);
            }
        }
        return out;
    }

    // ================================================================== dessin (écran GBA)

    @Override
    public void render(Graphics2D g) {
        if (mode == Mode.TRANSITION) return;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        drawField(g);
        drawSprites(g);
        drawBoxes(g);
        drawBottomBox(g);
        if (projectile != null) drawProjectile(g, projectile);
        if (ball != null && gfx.ball != null) {
            BufferedImage img = gfx.ball[Math.min(ball.frameIndex(), gfx.ball.length - 1)];
            Graphics2D b = (Graphics2D) g.create();
            b.rotate(ball.angle(), ball.x(), ball.y());
            b.drawImage(img, (int) ball.x() - 8, (int) ball.y() - 8, null);
            b.dispose();
        } else if (ball != null) {
            g.setColor(new Color(232, 64, 56));
            g.fillOval((int) ball.x() - 6, (int) ball.y() - 6, 12, 12);
            g.setColor(Color.WHITE);
            g.fillRect((int) ball.x() - 6, (int) ball.y(), 12, 6);
        }
        // fondu d'entrée depuis le noir
        if (mode == Mode.INTRO && modeTimer < 10) {
            g.setColor(new Color(0, 0, 0, (int) (255 * (1 - modeTimer / 10.0))));
            g.fillRect(0, 0, 240, 160);
        }
    }

    private void drawProjectile(Graphics2D g, Projectile p) {
        double sx = PLAYER_X + 40, sy = PLAYER_Y + 30, ex = FOE_X + 32, ey = FOE_Y + 36;
        if (p.fromSide() == Battle.FOE) {
            double tx = sx, ty = sy;
            sx = ex;
            sy = ey;
            ex = tx;
            ey = ty;
        }
        double x = sx + (ex - sx) * p.k(), y = sy + (ey - sy) * p.k() - Math.sin(p.k() * Math.PI) * 8;
        for (int i = 3; i >= 0; i--) {                         // traînée
            double kk = Math.max(0, p.k() - i * 0.05);
            double tx = sx + (ex - sx) * kk, ty = sy + (ey - sy) * kk - Math.sin(kk * Math.PI) * 8;
            g.setColor(new Color(p.color().getRed(), p.color().getGreen(), p.color().getBlue(), 60 + (3 - i) * 40));
            int r = 3 + (3 - i);
            g.fillOval((int) tx - r, (int) ty - r, r * 2, r * 2);
        }
        g.setColor(Color.WHITE);
        g.fillOval((int) x - 2, (int) y - 2, 4, 4);
    }

    private void drawField(Graphics2D g) {
        BufferedImage bg = gfx.terrain(terrain);
        if (bg != null) {
            g.drawImage(bg, 0, 0, null);
        } else {
            g.setPaint(new GradientPaint(0, 0, new Color(0xB8E0F8), 0, 112, new Color(0xF0F8E0)));
            g.fillRect(0, 0, 240, 112);
            g.setColor(new Color(0x8CC878));
            g.fillOval(130, 52, 100, 22);
            g.fillOval(-8, 96, 120, 24);
        }
    }

    private void drawSprites(Graphics2D g) {
        double slide = introSlide();
        // Adversaire (et portrait du dresseur adverse)
        SideView foe = views[Battle.FOE];
        int foeSlide = (int) Math.round(-slide * 240);
        if (trainerOffset >= 0 && trainerPic != null) {
            g.drawImage(trainerPic, (int) (FOE_X + trainerOffset * 120) + foeSlide, FOE_Y, null);
        }
        if (mode == Mode.INTRO && !trainerBattle && foe.sprite == null) {
            // l'adversaire sauvage arrive en silhouette pendant l'intro
            if (silhouette == null) silhouette = darken(game.data.species.frontSprite(battle.active(Battle.FOE).species));
            g.drawImage(silhouette, FOE_X + foeSlide, FOE_Y, null);
        }
        drawMon(g, foe, FOE_X, FOE_Y);

        // Joueur : son dresseur (de dos), puis son Pokémon
        SideView me = views[Battle.PLAYER];
        int mySlide = (int) Math.round(slide * 240);
        if (playerTrainerShown) {
            g.drawImage(avatar.back(), (int) (PLAYER_X - playerTrainerOffset * 100) + mySlide, PLAYER_Y, null);
        }
        drawMon(g, me, PLAYER_X, PLAYER_Y);
    }

    private BufferedImage silhouette;

    private void drawMon(Graphics2D g, SideView v, int x, int y) {
        if (!v.visible || v.sprite == null || v.blink) return;
        Graphics2D m = (Graphics2D) g.create();
        // K.O. : le Pokémon descend derrière la ligne du sol
        m.clipRect(0, 0, 240, y + 64);
        double s = v.scale;
        int w = (int) Math.round(64 * s), h = (int) Math.round(64 * s);
        int dx = (int) Math.round(x + v.dx + (64 - w) / 2.0);
        int dy = (int) Math.round(y + v.dy + (64 - h) + v.faint * 64);
        m.drawImage(v.sprite, dx, dy, w, h, null);
        if (v.white > 0) {
            BufferedImage white = whiteOf(v.sprite);
            m.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.min(1, v.white)));
            m.drawImage(white, dx, dy, w, h, null);
        }
        boolean flash = statFlashTimer > 0 && statFlashSide == (v == views[Battle.FOE] ? Battle.FOE : Battle.PLAYER);
        if (flash && (statFlashTimer / 4) % 2 == 0) {
            m.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.35f));
            m.setColor(statFlashUp ? new Color(255, 120, 80) : new Color(80, 140, 255));
            m.fillRect(dx, dy, w, h);
        }
        m.dispose();
    }

    private final java.util.Map<BufferedImage, BufferedImage> whites = new java.util.IdentityHashMap<>();

    private BufferedImage whiteOf(BufferedImage src) {
        return whites.computeIfAbsent(src, s -> {
            BufferedImage out = new BufferedImage(s.getWidth(), s.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < s.getHeight(); y++) {
                for (int x = 0; x < s.getWidth(); x++) if ((s.getRGB(x, y) >>> 24) != 0) out.setRGB(x, y, 0xFFFFFFFF);
            }
            return out;
        });
    }

    private void drawBoxes(Graphics2D g) {
        SideView foe = views[Battle.FOE], me = views[Battle.PLAYER];
        if (foe.boxVisible) {
            Rectangle2D r = BattleHud.FOE_BOX;
            BattleHud.drawBoxFrame(g, r, false);
            BattleHud.drawHpBar(g, (int) (r.getX() + r.getWidth() - 7 - 65), (int) (r.getY() + 15), foe.hp / Math.max(1, foe.maxHp));
        }
        if (me.boxVisible) {
            Rectangle2D r = BattleHud.PLAYER_BOX;
            BattleHud.drawBoxFrame(g, r, true);
            BattleHud.drawHpBar(g, (int) (r.getX() + r.getWidth() - 7 - 65), (int) (r.getY() + 15), me.hp / Math.max(1, me.maxHp));
            BattleHud.drawExpBar(g, (int) r.getX() + 12, (int) (r.getY() + r.getHeight() - 7), (int) r.getWidth() - 20, me.exp);
        }
    }

    private void drawBottomBox(Graphics2D g) {
        BattleGraphics.Box layout = switch (mode) {
            case ACTIONS, BAG -> BattleGraphics.Box.ACTIONS;
            case MOVES -> BattleGraphics.Box.MOVES;
            default -> BattleGraphics.Box.MESSAGE;
        };
        BufferedImage box = gfx.box(layout);
        if (box != null) g.drawImage(box, 0, BattleGraphics.FIELD_HEIGHT, null);
        else BattleHud.drawFallbackBox(g, layout == BattleGraphics.Box.ACTIONS, layout == BattleGraphics.Box.MOVES);
    }

    // ================================================================== dessin (haute résolution)

    private BattleHud.Mapping mapping() {
        return new BattleHud.Mapping(game.blitX(), game.blitY(), game.blitScale());
    }

    @Override
    public void renderHiRes(Graphics2D g, int w, int h) {
        if (mode == Mode.TRANSITION) {
            drawTransition(g, w, h);
            return;
        }
        BattleHud.Mapping m = mapping();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        SideView foe = views[Battle.FOE], me = views[Battle.PLAYER];
        if (foe.boxVisible && foe.mon != null) {
            BattleHud.drawBoxTexts(g, m, BattleHud.FOE_BOX, foe.mon.name(), foe.level, foe.status,
                    (int) Math.ceil(foe.hp), foe.maxHp, false);
        }
        if (me.boxVisible && me.mon != null) {
            BattleHud.drawBoxTexts(g, m, BattleHud.PLAYER_BOX, me.mon.name(), me.level, me.status,
                    (int) Math.ceil(me.hp), me.maxHp, true);
        }
        switch (mode) {
            case ACTIONS -> {
                drawMessage(g, m, 112);
                drawActions(g, m);
            }
            case MOVES -> drawMoves(g, m);
            case BAG -> {
                drawMessage(g, m, 112);
                drawBag(g, m);
            }
            case PARTY -> drawParty(g, m, w, h);
            case LEARN_ASK -> {
                drawMessage(g, m, 228);
                drawYesNo(g, m);
            }
            case LEARN_PICK -> {
                drawMessage(g, m, 228);
                drawLearnPick(g, m);
            }
            default -> drawMessage(g, m, 222);
        }
    }

    /** Message de la boîte du bas (blanc, ombre violette), sur 2 lignes. */
    private void drawMessage(Graphics2D g, BattleHud.Mapping m, double width) {
        if (message == null || message.isEmpty()) return;
        g.setFont(BattleHud.font(m, 10, false));
        String visible = message.substring(0, (int) Math.min(message.length(), Math.floor(shown)));
        List<String> all = BattleHud.wrap(g, m, message, width - 16);
        List<String> lines = BattleHud.wrap(g, m, visible, width - 16);
        // on affiche les 2 dernières lignes atteintes (le texte « remonte » comme dans le jeu)
        int first = Math.max(0, lines.size() - 2);
        for (int i = first; i < lines.size() && i < all.size(); i++) {
            BattleHud.text(g, m, lines.get(i), 11, 131 + (i - first) * 16, BattleHud.MESSAGE_INK, BattleHud.MESSAGE_SHADOW);
        }
    }

    private List<Rectangle2D> actionRects() {
        BattleHud.Mapping m = mapping();
        List<Rectangle2D> r = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            double gx = 128 + (i % 2) * 54, gy = 120 + (i / 2) * 16;
            r.add(new Rectangle2D.Double(m.x(gx), m.y(gy), m.size(52), m.size(15)));
        }
        return r;
    }

    private void drawActions(Graphics2D g, BattleHud.Mapping m) {
        g.setFont(BattleHud.font(m, 8.5, true));
        for (int i = 0; i < 4; i++) {
            double gx = 136 + (i % 2) * 54, gy = 131 + (i / 2) * 16;
            BattleHud.text(g, m, ACTIONS[i], gx, gy, BattleHud.MENU_INK, BattleHud.MENU_SHADOW);
            if (i == actionCursor) BattleHud.cursor(g, m, gx - 8, gy - 3);
        }
    }

    private List<Rectangle2D> moveRects() {
        BattleHud.Mapping m = mapping();
        List<Rectangle2D> r = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            double gx = 8 + (i % 2) * 72, gy = 120 + (i / 2) * 16;
            r.add(new Rectangle2D.Double(m.x(gx), m.y(gy), m.size(70), m.size(15)));
        }
        return r;
    }

    private void drawMoves(Graphics2D g, BattleHud.Mapping m) {
        Pokemon p = battle.active(Battle.PLAYER);
        g.setFont(BattleHud.font(m, 8, true));
        for (int i = 0; i < 4; i++) {
            double gx = 16 + (i % 2) * 72, gy = 131 + (i / 2) * 16;
            String name = p.moves[i] == 0 ? "-" : moveName(p.moves[i]);
            BattleHud.text(g, m, name, gx, gy, BattleHud.MENU_INK, BattleHud.MENU_SHADOW);
            if (i == moveCursor) BattleHud.cursor(g, m, gx - 8, gy - 3);
        }
        Move sel = game.data.battle().move(p.moves[moveCursor]);
        g.setFont(BattleHud.font(m, 8, true));
        int maxPp = sel.pp();
        int pp = p.pp[moveCursor];
        Color ppColor = pp == 0 ? new Color(232, 80, 72) : pp * 4 <= maxPp ? new Color(232, 136, 40) : BattleHud.MENU_INK;
        BattleHud.text(g, m, "PP", 170, 131, BattleHud.MENU_INK, BattleHud.MENU_SHADOW);
        BattleHud.text(g, m, pp + "/" + maxPp, 200, 131, ppColor, BattleHud.MENU_SHADOW);
        BattleHud.text(g, m, "TYPE/", 170, 147, BattleHud.MENU_INK, BattleHud.MENU_SHADOW);
        g.setFont(BattleHud.font(m, 7, true));
        BattleHud.text(g, m, Species.typeName(sel.type()).toUpperCase(), 196, 147,
                new Color(Species.typeColor(sel.type())).darker(), BattleHud.MENU_SHADOW);
    }

    private List<Rectangle2D> bagRects() {
        BattleHud.Mapping m = mapping();
        return List.of(new Rectangle2D.Double(m.x(128), m.y(120), m.size(104), m.size(15)),
                new Rectangle2D.Double(m.x(128), m.y(136), m.size(104), m.size(15)));
    }

    private void drawBag(Graphics2D g, BattleHud.Mapping m) {
        g.setFont(BattleHud.font(m, 8.5, true));
        String[] items = {"POKé BALL ×" + game.save.pokeBalls, "RETOUR"};
        for (int i = 0; i < 2; i++) {
            BattleHud.text(g, m, items[i], 136, 131 + i * 16, BattleHud.MENU_INK, BattleHud.MENU_SHADOW);
            if (i == listCursor) BattleHud.cursor(g, m, 128, 128 + i * 16);
        }
    }

    // ------------------------------------------------------------------ équipe

    private Rectangle2D partyPanel(int w, int h) {
        double s = Math.max(0.8, Math.min(1.6, h / 720.0));
        double pw = 460 * s, ph = (64 + (team.size() + 1) * 52) * s;
        return new Rectangle2D.Double((w - pw) / 2, (h - ph) / 2, pw, ph);
    }

    private List<Rectangle2D> partyRects() {
        int w = game.viewWidth(), h = game.viewHeight();
        double s = Math.max(0.8, Math.min(1.6, h / 720.0));
        Rectangle2D p = partyPanel(w, h);
        List<Rectangle2D> r = new ArrayList<>();
        for (int i = 0; i <= team.size(); i++) {
            r.add(new Rectangle2D.Double(p.getX() + 14 * s, p.getY() + (48 + i * 52) * s, p.getWidth() - 28 * s, 46 * s));
        }
        return r;
    }

    private void drawParty(Graphics2D g, BattleHud.Mapping m, int w, int h) {
        double s = Math.max(0.8, Math.min(1.6, h / 720.0));
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRect(0, 0, w, h);
        Rectangle2D p = partyPanel(w, h);
        BattleHud.whitePanel(g, p.getX(), p.getY(), p.getWidth(), p.getHeight(), s);
        g.setFont(HiUi.font((float) (16 * s), true));
        g.setColor(BattleHud.MENU_INK);
        g.drawString(forcedSwitch ? "Choisis le POKéMON suivant." : "Quel POKéMON envoyer ?",
                (float) (p.getX() + 22 * s), (float) (p.getY() + 34 * s));
        List<Rectangle2D> rows = partyRects();
        for (int i = 0; i < team.size(); i++) {
            Rectangle2D r = rows.get(i);
            Pokemon mon = team.get(i);
            boolean on = i == listCursor;
            g.setColor(on ? new Color(255, 236, 196) : new Color(244, 244, 248));
            g.fill(new java.awt.geom.RoundRectangle2D.Double(r.getX(), r.getY(), r.getWidth(), r.getHeight(), 10 * s, 10 * s));
            if (on) {
                g.setColor(new Color(232, 96, 64));
                g.setStroke(new java.awt.BasicStroke((float) (2 * s)));
                g.draw(new java.awt.geom.RoundRectangle2D.Double(r.getX(), r.getY(), r.getWidth(), r.getHeight(), 10 * s, 10 * s));
                g.setStroke(new java.awt.BasicStroke(1f));
            }
            BufferedImage icon = game.data.species.frontSprite(mon.species);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(icon, (int) (r.getX() + 4 * s), (int) (r.getY() - 6 * s), (int) (52 * s), (int) (52 * s), null);
            g.setFont(HiUi.font((float) (15 * s), true));
            g.setColor(mon.fainted() ? new Color(160, 160, 160) : BattleHud.MENU_INK);
            g.drawString(mon.name() + (i == battle.activeIndex(Battle.PLAYER) ? "  (au combat)" : ""),
                    (float) (r.getX() + 64 * s), (float) (r.getY() + 20 * s));
            g.setFont(HiUi.font((float) (13 * s), false));
            g.drawString("N." + mon.level, (float) (r.getX() + 64 * s), (float) (r.getY() + 38 * s));
            double barX = r.getX() + 130 * s, barY = r.getY() + 29 * s, barW = 150 * s;
            g.setColor(BattleHud.BAR_BG);
            g.fill(new java.awt.geom.RoundRectangle2D.Double(barX, barY, barW, 8 * s, 6 * s, 6 * s));
            double f = mon.hp / (double) Math.max(1, mon.maxHp());
            g.setColor(BattleHud.hpColors(f)[0]);
            g.fill(new java.awt.geom.RoundRectangle2D.Double(barX + 2 * s, barY + 2 * s, (barW - 4 * s) * f, 4 * s, 3 * s, 3 * s));
            g.setColor(BattleHud.MENU_INK);
            g.drawString(mon.hp + " / " + mon.maxHp(), (float) (barX + barW + 12 * s), (float) (barY + 8 * s));
        }
        if (!forcedSwitch) {
            Rectangle2D r = rows.get(team.size());
            boolean on = listCursor == team.size();
            g.setColor(on ? new Color(255, 236, 196) : new Color(244, 244, 248));
            g.fill(new java.awt.geom.RoundRectangle2D.Double(r.getX(), r.getY(), r.getWidth(), r.getHeight(), 10 * s, 10 * s));
            g.setFont(HiUi.font((float) (15 * s), true));
            g.setColor(BattleHud.MENU_INK);
            HiUi.centered(g, "RETOUR", r.getX(), r.getY(), r.getWidth(), r.getHeight());
        }
        if (!status.isEmpty()) {
            g.setFont(HiUi.font((float) (14 * s), false));
            g.setColor(new Color(200, 60, 60));
            g.drawString(status, (float) (p.getX() + 22 * s), (float) (p.getY() + p.getHeight() - 10 * s));
        }
    }

    // ------------------------------------------------------------------ apprendre une attaque

    private List<Rectangle2D> yesNoRects() {
        BattleHud.Mapping m = mapping();
        return List.of(new Rectangle2D.Double(m.x(176), m.y(72), m.size(56), m.size(16)),
                new Rectangle2D.Double(m.x(176), m.y(88), m.size(56), m.size(16)));
    }

    private void drawYesNo(Graphics2D g, BattleHud.Mapping m) {
        BattleHud.whitePanel(g, m.x(170), m.y(66), m.size(64), m.size(42), m.scale() / 2);
        g.setFont(BattleHud.font(m, 8.5, true));
        String[] o = {"OUI", "NON"};
        for (int i = 0; i < 2; i++) {
            BattleHud.text(g, m, o[i], 188, 84 + i * 16, BattleHud.MENU_INK, BattleHud.MENU_SHADOW);
            if (i == listCursor) BattleHud.cursor(g, m, 180, 81 + i * 16);
        }
    }

    private List<Rectangle2D> learnRects() {
        BattleHud.Mapping m = mapping();
        List<Rectangle2D> r = new ArrayList<>();
        for (int i = 0; i < 5; i++) r.add(new Rectangle2D.Double(m.x(132), m.y(24 + i * 16), m.size(100), m.size(15)));
        return r;
    }

    private void drawLearnPick(Graphics2D g, BattleHud.Mapping m) {
        Pokemon mon = team.get(learnIndex);
        BattleHud.whitePanel(g, m.x(124), m.y(16), m.size(112), m.size(92), m.scale() / 2);
        g.setFont(BattleHud.font(m, 8.5, true));
        for (int i = 0; i < 5; i++) {
            String s = i < 4 ? moveName(mon.moves[i]) : "ANNULER";
            BattleHud.text(g, m, s, 140, 35 + i * 16, BattleHud.MENU_INK, BattleHud.MENU_SHADOW);
            if (i == listCursor) BattleHud.cursor(g, m, 132, 32 + i * 16);
        }
    }

    // ------------------------------------------------------------------ transition depuis le monde

    /**
     * Comme sur la console : l'écran clignote deux fois, puis des bandes noires se referment
     * en alternance depuis la gauche et la droite.
     */
    private void drawTransition(Graphics2D g, int w, int h) {
        int t = modeTimer;
        if (t < 24) {
            double phase = (t % 12) / 12.0;
            int a = (int) (200 * Math.sin(phase * Math.PI));
            g.setColor(new Color(255, 255, 255, Math.max(0, a)));
            g.fillRect(0, 0, w, h);
            return;
        }
        double k = Math.min(1, (t - 24) / (double) (TRANSITION_FRAMES - 30));
        int bands = 16;
        double bh = h / (double) bands;
        g.setColor(Color.BLACK);
        for (int i = 0; i < bands; i++) {
            double len = w * k;
            double y = i * bh;
            if (i % 2 == 0) g.fill(new Rectangle2D.Double(0, y, len, bh + 1));
            else g.fill(new Rectangle2D.Double(w - len, y, len, bh + 1));
        }
    }

    /** Pour les tests : vrai quand le joueur doit choisir une action. */
    public boolean waitingForAction() {
        return mode == Mode.ACTIONS;
    }

    /** Pour les tests : choisit directement une action du menu (0 ATTAQUE, 1 SAC, 2 POKéMON, 3 FUITE). */
    public void selectAction(int action) {
        if (mode != Mode.ACTIONS) return;
        actionCursor = action;
    }
}
