package dev.orvaen.mmo.battle;

import dev.orvaen.mmo.data.GameData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Le moteur de combat : les règles de la 3e génération, sans aucun dessin.
 * <p>
 * Utilisation : on crée un combat, puis à chaque tour le joueur choisit une action
 * ({@link #useMove}, {@link #throwBall}, {@link #run}, {@link #switchTo}). Chaque action renvoie la liste
 * des {@link BattleEvent} du tour (messages, dégâts, K.O., expérience…), que l'écran de combat anime.
 *
 * <h2>Un tour</h2>
 * <ol>
 *   <li>Fuite, Poké Ball et changement passent avant les attaques.</li>
 *   <li>Sinon, l'attaque de plus haute priorité passe d'abord, puis la plus rapide (au hasard si égalité).</li>
 *   <li>Fin du tour : poison, brûlure, Vampigraine.</li>
 *   <li>K.O. : expérience, Pokémon suivant du dresseur, ou fin du combat.</li>
 * </ol>
 */
public final class Battle {

    public static final int PLAYER = 0, FOE = 1;

    public enum Outcome { WON, LOST, FLED, CAUGHT }

    // Index des « crans » de statistiques en combat (Attaque… Défense Spé. comme Pokemon, puis précision, esquive)
    private static final int ACCURACY = 6, EVASION = 7;
    private static final String[] STAT_NAMES = {"", "L'ATTAQUE", "La DÉFENSE", "La VITESSE", "L'ATQ. SPÉ.",
            "La DÉF. SPÉ.", "La PRÉCISION", "L'ESQUIVE"};
    private static final int STRUGGLE = 165;
    private static final int TYPE_POISON = 3, TYPE_STEEL = 8, TYPE_FIRE = 10, TYPE_GRASS = 12, TYPE_ICE = 15;

    /** Ce qui ne dure que le temps du combat (crans de stats, confusion…). */
    private static final class Battler {
        Pokemon mon;
        int index;                                   // place dans l'équipe
        final int[] stages = new int[8];
        int confusion;                               // tours restants
        boolean flinch, seeded;

        void reset(Pokemon p, int i) {
            mon = p;
            index = i;
            java.util.Arrays.fill(stages, 0);
            confusion = 0;
            flinch = false;
            seeded = false;
        }
    }

    private final GameData data;
    private final BattleData rules;
    private final Random rng;
    private final List<Pokemon> playerTeam;
    private final List<Pokemon> foeTeam;
    private final boolean trainer;
    private final String trainerName;
    private final String playerName;
    private final Battler[] sides = {new Battler(), new Battler()};
    private final Set<Integer> participants = new HashSet<>();   // Pokémon du joueur qui ont combattu l'adversaire actuel
    private final List<BattleEvent> events = new ArrayList<>();
    private int runAttempts;
    private boolean mustReplace;
    private Outcome outcome;
    private int prizeMoney;

    /**
     * @param foeTeam     l'adversaire (un seul Pokémon pour un combat sauvage)
     * @param trainerName nom du dresseur, ou null pour un Pokémon sauvage
     */
    public Battle(GameData data, List<Pokemon> playerTeam, List<Pokemon> foeTeam, String trainerName,
                  String playerName, Random rng) {
        this.data = data;
        this.rules = data.battle();
        this.rng = rng;
        this.playerTeam = playerTeam;
        this.foeTeam = foeTeam;
        this.trainer = trainerName != null;
        this.trainerName = trainerName;
        this.playerName = playerName;
        int first = firstHealthy(playerTeam, -1);
        sides[PLAYER].reset(playerTeam.get(Math.max(0, first)), Math.max(0, first));
        sides[FOE].reset(foeTeam.get(0), 0);
        participants.add(sides[PLAYER].index);
    }

    /** Messages et entrées en scène du début du combat. */
    public List<BattleEvent> start() {
        events.clear();
        Pokemon foe = sides[FOE].mon;
        if (trainer) {
            say(trainerName + " veut se battre !");
            events.add(sendOutEvent(FOE));
            say(trainerName + " envoie " + foe.name() + " !");
        } else {
            events.add(sendOutEvent(FOE));
            say("Un " + foe.name() + " sauvage apparaît !");
        }
        events.add(sendOutEvent(PLAYER));
        say("En avant ! " + sides[PLAYER].mon.name() + " !");
        return copy();
    }

    // ================================================================== état

    public Pokemon active(int side) {
        return sides[side].mon;
    }

    public int activeIndex(int side) {
        return sides[side].index;
    }

    public List<Pokemon> playerTeam() {
        return playerTeam;
    }

    public boolean isTrainerBattle() {
        return trainer;
    }

    /** Le Pokémon du joueur est K.O. et il lui en reste : il doit en choisir un autre. */
    public boolean mustChooseReplacement() {
        return mustReplace;
    }

    public Outcome outcome() {
        return outcome;
    }

    public boolean over() {
        return outcome != null;
    }

    /** Argent gagné (combat de dresseur gagné). */
    public int prizeMoney() {
        return prizeMoney;
    }

    // ================================================================== actions du joueur

    /** Le joueur attaque avec l'attaque n° {@code slot} (0 à 3) ; -1 : Lutte (plus de PP). */
    public List<BattleEvent> useMove(int slot) {
        events.clear();
        Battler p = sides[PLAYER], f = sides[FOE];
        Move playerMove = slot < 0 ? rules.move(STRUGGLE) : rules.move(p.mon.moves[slot]);
        int foeSlot = chooseFoeMove();
        Move foeMove = foeSlot < 0 ? rules.move(STRUGGLE) : rules.move(f.mon.moves[foeSlot]);

        boolean playerFirst = goesFirst(playerMove, foeMove);
        p.flinch = false;
        f.flinch = false;
        if (playerFirst) {
            act(PLAYER, slot, playerMove, true);
            if (!checkFaints() && !f.mon.fainted()) act(FOE, foeSlot, foeMove, false);
        } else {
            act(FOE, foeSlot, foeMove, true);
            if (!checkFaints() && !p.mon.fainted()) act(PLAYER, slot, playerMove, false);
        }
        if (!checkFaints()) {
            endOfTurn();
            checkFaints();
        }
        return copy();
    }

    /** Tentative de fuite (impossible contre un dresseur). */
    public List<BattleEvent> run() {
        events.clear();
        if (trainer) {
            say("Impossible ! On ne fuit pas un combat de dresseur !");
            return copy();
        }
        runAttempts++;
        int mine = speed(PLAYER), theirs = Math.max(1, speed(FOE));
        int odds = (mine * 128 / theirs + 30 * runAttempts) % 256;
        if (mine >= theirs || rng.nextInt(256) < odds) {
            say("Vous prenez la fuite !");
            finish(Outcome.FLED);
            return copy();
        }
        say("Impossible de fuir !");
        foeOnlyTurn();
        return copy();
    }

    /** Lancer d'une Poké Ball (formule de capture de la 3e génération). */
    public List<BattleEvent> throwBall() {
        events.clear();
        say(playerName + " lance une POKé BALL !");
        if (trainer) {
            events.add(new BattleEvent.BallThrow(0, false));
            say("Le dresseur dévie la POKé BALL !");
            say("Voler, c'est mal !");
            foeOnlyTurn();
            return copy();
        }
        Pokemon foe = sides[FOE].mon;
        int max = foe.maxHp();
        double statusBonus = switch (foe.status) {
            case Pokemon.SLEEP, Pokemon.FREEZE -> 2.0;
            case Pokemon.POISON, Pokemon.BURN, Pokemon.PARALYSIS -> 1.5;
            default -> 1.0;
        };
        double a = (3.0 * max - 2.0 * foe.hp) * foe.base().catchRate() / (3.0 * max) * statusBonus;
        int shakes;
        if (a >= 255) {
            shakes = 4;
        } else {
            double b = 1048560 / Math.sqrt(Math.sqrt(16711680 / Math.max(1, a)));
            shakes = 0;
            while (shakes < 4 && rng.nextInt(65536) < b) shakes++;
        }
        boolean caught = shakes >= 4;
        events.add(new BattleEvent.BallThrow(Math.min(3, shakes), caught));
        if (caught) {
            say("Et hop ! " + foe.base().name() + " est attrapé !");
            finish(Outcome.CAUGHT);
        } else {
            say(switch (shakes) {
                case 0 -> "Oh non ! Le POKéMON s'est libéré !";
                case 1 -> "Raah ! Ça avait l'air réussi !";
                case 2 -> "Aaah ! Presque !";
                default -> "Mince ! Il y était presque !";
            });
            foeOnlyTurn();
        }
        return copy();
    }

    /** Changer de Pokémon (compte comme une action ; l'adversaire attaque ensuite). */
    public List<BattleEvent> switchTo(int index) {
        events.clear();
        if (!canSwitchTo(index)) return copy();
        say(sides[PLAYER].mon.name() + ", reviens !");
        events.add(new BattleEvent.Withdraw(PLAYER));
        sendOutPlayer(index);
        foeOnlyTurn();
        return copy();
    }

    /** Remplacement obligatoire après un K.O. (pas d'attaque adverse). */
    public List<BattleEvent> replaceFainted(int index) {
        events.clear();
        if (!mustReplace || !canSwitchTo(index)) return copy();
        mustReplace = false;
        sendOutPlayer(index);
        return copy();
    }

    public boolean canSwitchTo(int index) {
        return index >= 0 && index < playerTeam.size() && index != sides[PLAYER].index
                && !playerTeam.get(index).fainted();
    }

    /** Réponse à {@link BattleEvent.LearnPrompt} : {@code slot} = attaque à oublier (0-3), -1 = ne pas apprendre. */
    public void resolveLearn(int index, int move, int slot) {
        if (slot >= 0 && slot < 4 && index >= 0 && index < playerTeam.size()) playerTeam.get(index).replaceMove(slot, move);
    }

    // ================================================================== déroulement d'un tour

    private void sendOutPlayer(int index) {
        sides[PLAYER].reset(playerTeam.get(index), index);
        participants.add(index);
        events.add(sendOutEvent(PLAYER));
        say("En avant ! " + sides[PLAYER].mon.name() + " !");
    }

    /** L'adversaire est seul à agir ce tour (après une fuite ratée, une ball, un changement). */
    private void foeOnlyTurn() {
        if (over()) return;
        int slot = chooseFoeMove();
        Move m = slot < 0 ? rules.move(STRUGGLE) : rules.move(sides[FOE].mon.moves[slot]);
        sides[FOE].flinch = false;
        act(FOE, slot, m, true);
        if (!checkFaints()) {
            endOfTurn();
            checkFaints();
        }
    }

    private boolean goesFirst(Move mine, Move theirs) {
        if (mine.priority() != theirs.priority()) return mine.priority() > theirs.priority();
        int a = speed(PLAYER), b = speed(FOE);
        if (a != b) return a > b;
        return rng.nextBoolean();
    }

    private int speed(int side) {
        Battler b = sides[side];
        int s = staged(b.mon.stat(Pokemon.SPEED), b.stages[Pokemon.SPEED]);
        if (b.mon.status == Pokemon.PARALYSIS) s /= 4;
        return Math.max(1, s);
    }

    /** Choix de l'adversaire : au hasard (sauvage) ou l'attaque qui fait le plus mal (dresseur). */
    private int chooseFoeMove() {
        Pokemon f = sides[FOE].mon;
        List<Integer> usable = new ArrayList<>();
        for (int i = 0; i < 4; i++) if (f.moves[i] != 0 && f.pp[i] > 0) usable.add(i);
        if (usable.isEmpty()) return -1;
        if (!trainer) return usable.get(rng.nextInt(usable.size()));
        int best = usable.get(0);
        double bestScore = -1;
        for (int i : usable) {
            Move m = rules.move(f.moves[i]);
            double score = m.status() ? 8 + rng.nextInt(10)
                    : m.power() * typeMultiplier(m.type(), sides[PLAYER].mon) * (stab(f, m) ? 1.5 : 1) * (0.85 + rng.nextDouble() * 0.3);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    /** Une attaque, du début (statut qui empêche d'agir) à la fin (effets secondaires). */
    private void act(int side, int slot, Move move, boolean movesFirst) {
        Battler a = sides[side], d = sides[1 - side];
        Pokemon user = a.mon;
        if (user.fainted() || over()) return;
        if (!canAct(side)) return;

        if (slot >= 0) user.pp[slot] = Math.max(0, user.pp[slot] - 1);
        say(name(side) + " utilise " + move.name() + " !");
        events.add(new BattleEvent.MoveUsed(side, move));

        if (move.effect() == Effects.TELEPORT && !trainer) {
            say(name(side) + " s'enfuit !");
            finish(Outcome.FLED);
            return;
        }
        boolean targetsSelf = targetsUser(move.effect());
        if (!targetsSelf && !hits(a, d, move)) {
            say(name(side) + " rate son attaque !");
            return;
        }
        if (move.status()) {
            statusMove(side, move);
        } else {
            damagingMove(side, move, movesFirst);
        }
    }

    /** Sommeil, gel, peur, confusion, paralysie : le Pokémon peut-il agir ce tour ? */
    private boolean canAct(int side) {
        Battler b = sides[side];
        Pokemon p = b.mon;
        switch (p.status) {
            case Pokemon.SLEEP -> {
                if (--p.sleepTurns > 0) {
                    say(name(side) + " dort profondément.");
                    return false;
                }
                p.status = Pokemon.OK;
                say(name(side) + " se réveille !");
                events.add(new BattleEvent.Status(side, Pokemon.OK));
            }
            case Pokemon.FREEZE -> {
                if (rng.nextInt(5) != 0) {
                    say(name(side) + " est gelé !");
                    return false;
                }
                p.status = Pokemon.OK;
                say(name(side) + " dégèle !");
                events.add(new BattleEvent.Status(side, Pokemon.OK));
            }
            default -> { }
        }
        if (b.flinch) {
            say(name(side) + " a peur ! Il ne peut pas attaquer !");
            return false;
        }
        if (b.confusion > 0) {
            if (--b.confusion == 0) {
                say(name(side) + " n'est plus confus !");
            } else {
                say(name(side) + " est confus !");
                if (rng.nextBoolean()) {
                    // il se frappe lui-même : attaque physique de puissance 40, sans type
                    int dmg = baseDamage(p.level, 40, staged(p.stat(Pokemon.ATTACK), b.stages[Pokemon.ATTACK]),
                            staged(p.stat(Pokemon.DEFENSE), b.stages[Pokemon.DEFENSE]));
                    hurt(side, dmg, 10, false);
                    say("Il se blesse dans sa confusion !");
                    return false;
                }
            }
        }
        if (p.status == Pokemon.PARALYSIS && rng.nextInt(4) == 0) {
            say(name(side) + " est paralysé ! Il ne peut pas attaquer !");
            return false;
        }
        return true;
    }

    private boolean hits(Battler a, Battler d, Move move) {
        if (move.accuracy() == 0 || move.effect() == Effects.ALWAYS_HIT) return true;
        int stage = Math.max(-6, Math.min(6, a.stages[ACCURACY] - d.stages[EVASION]));
        double chance = move.accuracy() * accuracyRatio(stage);
        return rng.nextInt(100) < chance;
    }

    // ------------------------------------------------------------------ attaques qui font des dégâts

    private void damagingMove(int side, Move move, boolean movesFirst) {
        Battler a = sides[side], d = sides[1 - side];
        double typeMult = typeMultiplier(move.type(), d.mon);
        if (move.id() == STRUGGLE) typeMult = 1;
        if (typeMult == 0) {
            say("Ça n'affecte pas " + name(1 - side) + "…");
            return;
        }
        int hitsCount = switch (move.effect()) {
            case Effects.MULTI_HIT -> new int[]{2, 2, 2, 3, 3, 3, 4, 5}[rng.nextInt(8)];
            case Effects.DOUBLE_HIT, Effects.TWINEEDLE -> 2;
            case Effects.TRIPLE_KICK -> 3;
            default -> 1;
        };
        int dealt = 0, done = 0;
        boolean anyCrit = false;
        for (int h = 0; h < hitsCount && !d.mon.fainted(); h++) {
            boolean crit = criticalHit(a, move);
            int dmg = fixedDamage(a, d, move);
            if (dmg < 0) dmg = damage(a, d, move, crit, typeMult);
            else crit = false;
            if (move.effect() == Effects.FALSE_SWIPE) dmg = Math.min(dmg, d.mon.hp - 1);
            dmg = Math.min(dmg, d.mon.hp);
            dealt += dmg;
            done++;
            anyCrit |= crit;
            hurt(1 - side, dmg, (int) Math.round(typeMult * 10), crit);
            if (crit) say("Coup critique !");
        }
        if (hitsCount > 1) say("Touché " + done + " fois !");
        if (fixedDamage(a, d, move) < 0 && move.id() != STRUGGLE) {
            if (typeMult > 1) say("C'est super efficace !");
            else if (typeMult < 1) say("Ce n'est pas très efficace…");
        }
        afterDamage(side, move, dealt, movesFirst);
    }

    /** Dégâts fixes (Sonicboom, Draco-Rage, Frappe Atlas…), ou -1. */
    private int fixedDamage(Battler a, Battler d, Move move) {
        return switch (move.effect()) {
            case Effects.SONICBOOM -> 20;
            case Effects.DRAGON_RAGE -> 40;
            case Effects.LEVEL_DAMAGE -> a.mon.level;
            case Effects.SUPER_FANG -> Math.max(1, d.mon.hp / 2);
            case Effects.PSYWAVE -> Math.max(1, a.mon.level * (rng.nextInt(11) + 5) / 10);
            default -> -1;
        };
    }

    /**
     * Formule des dégâts de la 3e génération :
     * ((2 × niveau / 5 + 2) × puissance × Att / Déf) / 50 + 2, puis coup critique ×2, même type ×1,5,
     * efficacité du type, et un tirage entre 85 % et 100 %.
     */
    private int damage(Battler a, Battler d, Move move, boolean crit, double typeMult) {
        boolean physical = move.physical();
        int atkStat = physical ? Pokemon.ATTACK : Pokemon.SP_ATTACK;
        int defStat = physical ? Pokemon.DEFENSE : Pokemon.SP_DEFENSE;
        int atkStage = a.stages[atkStat], defStage = d.stages[defStat];
        if (crit) {                                                  // le critique ignore les crans défavorables
            atkStage = Math.max(0, atkStage);
            defStage = Math.min(0, defStage);
        }
        int atk = staged(a.mon.stat(atkStat), atkStage);
        int def = staged(d.mon.stat(defStat), defStage);
        int dmg = baseDamage(a.mon.level, move.power(), atk, def);
        if (physical && a.mon.status == Pokemon.BURN) dmg /= 2;
        if (crit) dmg *= 2;
        if (stab(a.mon, move)) dmg = dmg * 3 / 2;
        dmg = (int) (dmg * typeMult);
        dmg = dmg * (100 - rng.nextInt(16)) / 100;
        return Math.max(1, dmg);
    }

    private static int baseDamage(int level, int power, int atk, int def) {
        return (2 * level / 5 + 2) * power * atk / Math.max(1, def) / 50 + 2;
    }

    private boolean criticalHit(Battler a, Move move) {
        int stage = move.effect() == Effects.HIGH_CRITICAL || move.effect() == Effects.BLAZE_KICK
                || move.effect() == Effects.POISON_TAIL ? 1 : 0;
        int[] odds = {16, 8, 4, 3, 2};
        return rng.nextInt(odds[stage]) == 0;
    }

    private static boolean stab(Pokemon p, Move m) {
        return m.type() == p.base().type1() || m.type() == p.base().type2();
    }

    private double typeMultiplier(int moveType, Pokemon target) {
        double m = rules.effectiveness(moveType, target.base().type1()) / 10.0;
        if (target.base().type2() != target.base().type1()) m *= rules.effectiveness(moveType, target.base().type2()) / 10.0;
        return m;
    }

    /** Vol-Vie, contrecoup, effets secondaires (brûlure de Flammèche…). */
    private void afterDamage(int side, Move move, int dealt, boolean movesFirst) {
        Battler a = sides[side], d = sides[1 - side];
        switch (move.effect()) {
            case Effects.ABSORB, Effects.DREAM_EATER -> heal(side, Math.max(1, dealt / 2), name(1 - side) + " perd de l'énergie !");
            case Effects.RECOIL -> recoil(side, dealt / 4);
            case Effects.DOUBLE_EDGE -> recoil(side, dealt / 3);
            case Effects.EXPLOSION -> hurt(side, a.mon.hp, 10, false);
            default -> { }
        }
        if (move.id() == STRUGGLE) recoil(side, Math.max(1, dealt / 4));
        if (d.mon.fainted()) return;
        int chance = move.chance() == 0 ? 100 : move.chance();
        if (rng.nextInt(100) >= chance) return;
        switch (move.effect()) {
            case Effects.POISON_HIT, Effects.TWINEEDLE, Effects.POISON_TAIL, Effects.POISON_FANG -> inflict(1 - side, Pokemon.POISON, false);
            case Effects.BURN_HIT, Effects.BLAZE_KICK -> inflict(1 - side, Pokemon.BURN, false);
            case Effects.FREEZE_HIT -> inflict(1 - side, Pokemon.FREEZE, false);
            case Effects.PARALYZE_HIT, Effects.THUNDER -> inflict(1 - side, Pokemon.PARALYSIS, false);
            case Effects.TRI_ATTACK -> inflict(1 - side, new int[]{Pokemon.BURN, Pokemon.FREEZE, Pokemon.PARALYSIS}[rng.nextInt(3)], false);
            case Effects.CONFUSE_HIT -> confuse(1 - side, false);
            case Effects.FLINCH_HIT, Effects.FLINCH_MINIMIZE_HIT, Effects.TWISTER -> {
                if (movesFirst) d.flinch = true;
            }
            case Effects.ATTACK_DOWN_HIT -> changeStat(1 - side, Pokemon.ATTACK, -1, false);
            case Effects.DEFENSE_DOWN_HIT -> changeStat(1 - side, Pokemon.DEFENSE, -1, false);
            case Effects.SPEED_DOWN_HIT -> changeStat(1 - side, Pokemon.SPEED, -1, false);
            case Effects.SP_ATTACK_DOWN_HIT -> changeStat(1 - side, Pokemon.SP_ATTACK, -1, false);
            case Effects.SP_DEFENSE_DOWN_HIT -> changeStat(1 - side, Pokemon.SP_DEFENSE, -1, false);
            case Effects.ACCURACY_DOWN_HIT -> changeStat(1 - side, ACCURACY, -1, false);
            case Effects.EVASION_DOWN_HIT -> changeStat(1 - side, EVASION, -1, false);
            case Effects.ATTACK_UP_HIT -> changeStat(side, Pokemon.ATTACK, 1, false);
            case Effects.DEFENSE_UP_HIT -> changeStat(side, Pokemon.DEFENSE, 1, false);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ attaques de statut

    private static boolean targetsUser(int effect) {
        return (effect >= Effects.ATTACK_UP && effect <= Effects.EVASION_UP)
                || (effect >= Effects.ATTACK_UP_2 && effect <= Effects.EVASION_UP_2)
                || effect == Effects.RESTORE_HP || effect == Effects.REST || effect == Effects.DEFENSE_CURL
                || effect == Effects.SOFTBOILED || effect == Effects.MORNING_SUN || effect == Effects.SYNTHESIS
                || effect == Effects.MOONLIGHT || effect == Effects.SPLASH || effect == Effects.HAZE
                || effect == Effects.COSMIC_POWER || effect == Effects.BULK_UP || effect == Effects.CALM_MIND
                || effect == Effects.DRAGON_DANCE;
    }

    private void statusMove(int side, Move move) {
        int foe = 1 - side;
        int e = move.effect();
        if (!targetsUser(e) && typeMultiplier(move.type(), sides[foe].mon) == 0
                && (e == Effects.PARALYZE || e == Effects.POISON || e == Effects.TOXIC)) {
            say("Ça n'affecte pas " + name(foe) + "…");
            return;
        }
        switch (e) {
            case Effects.ATTACK_UP, Effects.DEFENSE_UP, Effects.SPEED_UP, Effects.SP_ATTACK_UP, Effects.SP_DEFENSE_UP,
                 Effects.ACCURACY_UP, Effects.EVASION_UP -> changeStat(side, e - Effects.ATTACK_UP + 1, 1, true);
            case Effects.ATTACK_UP_2, Effects.DEFENSE_UP_2, Effects.SPEED_UP_2, Effects.SP_ATTACK_UP_2,
                 Effects.SP_DEFENSE_UP_2, Effects.ACCURACY_UP_2, Effects.EVASION_UP_2 ->
                    changeStat(side, e - Effects.ATTACK_UP_2 + 1, 2, true);
            case Effects.ATTACK_DOWN, Effects.DEFENSE_DOWN, Effects.SPEED_DOWN, Effects.SP_ATTACK_DOWN,
                 Effects.SP_DEFENSE_DOWN, Effects.ACCURACY_DOWN, Effects.EVASION_DOWN ->
                    changeStat(foe, e - Effects.ATTACK_DOWN + 1, -1, true);
            case Effects.ATTACK_DOWN_2, Effects.DEFENSE_DOWN_2, Effects.SPEED_DOWN_2, Effects.SP_ATTACK_DOWN_2,
                 Effects.SP_DEFENSE_DOWN_2, Effects.ACCURACY_DOWN_2, Effects.EVASION_DOWN_2 ->
                    changeStat(foe, e - Effects.ATTACK_DOWN_2 + 1, -2, true);
            case Effects.DEFENSE_CURL -> changeStat(side, Pokemon.DEFENSE, 1, true);
            case Effects.COSMIC_POWER -> {
                changeStat(side, Pokemon.DEFENSE, 1, true);
                changeStat(side, Pokemon.SP_DEFENSE, 1, true);
            }
            case Effects.BULK_UP -> {
                changeStat(side, Pokemon.ATTACK, 1, true);
                changeStat(side, Pokemon.DEFENSE, 1, true);
            }
            case Effects.CALM_MIND -> {
                changeStat(side, Pokemon.SP_ATTACK, 1, true);
                changeStat(side, Pokemon.SP_DEFENSE, 1, true);
            }
            case Effects.DRAGON_DANCE -> {
                changeStat(side, Pokemon.ATTACK, 1, true);
                changeStat(side, Pokemon.SPEED, 1, true);
            }
            case Effects.SLEEP -> inflict(foe, Pokemon.SLEEP, true);
            case Effects.POISON, Effects.TOXIC -> inflict(foe, Pokemon.POISON, true);
            case Effects.PARALYZE -> inflict(foe, Pokemon.PARALYSIS, true);
            case Effects.WILL_O_WISP -> inflict(foe, Pokemon.BURN, true);
            case Effects.CONFUSE -> confuse(foe, true);
            case Effects.LEECH_SEED -> {
                Battler d = sides[foe];
                if (d.seeded || isType(d.mon, TYPE_GRASS)) {
                    say(name(foe) + " évite l'attaque !");
                } else {
                    d.seeded = true;
                    say(name(foe) + " est infecté !");
                }
            }
            case Effects.RESTORE_HP, Effects.SOFTBOILED, Effects.MORNING_SUN, Effects.SYNTHESIS, Effects.MOONLIGHT -> {
                Pokemon p = sides[side].mon;
                if (p.hp >= p.maxHp()) say("Mais les PV de " + name(side) + " sont au maximum !");
                else heal(side, p.maxHp() / 2, name(side) + " récupère des PV !");
            }
            case Effects.REST -> {
                Pokemon p = sides[side].mon;
                if (p.hp >= p.maxHp()) {
                    say("Mais cela échoue !");
                } else {
                    p.status = Pokemon.SLEEP;
                    p.sleepTurns = 3;
                    events.add(new BattleEvent.Status(side, Pokemon.SLEEP));
                    heal(side, p.maxHp(), name(side) + " s'endort et récupère ses PV !");
                }
            }
            case Effects.HAZE -> {
                java.util.Arrays.fill(sides[PLAYER].stages, 0);
                java.util.Arrays.fill(sides[FOE].stages, 0);
                say("Les changements de stats sont annulés !");
            }
            default -> say("Mais rien ne se passe !");
        }
    }

    private boolean isType(Pokemon p, int type) {
        return p.base().type1() == type || p.base().type2() == type;
    }

    /** Monte ou baisse une statistique de {@code delta} crans (de -6 à +6). */
    private void changeStat(int side, int stat, int delta, boolean announceFailure) {
        Battler b = sides[side];
        int before = b.stages[stat];
        int after = Math.max(-6, Math.min(6, before + delta));
        String who = name(side);
        if (after == before) {
            if (announceFailure) say(STAT_NAMES[stat] + " de " + who + (delta > 0 ? " ne peut plus monter !" : " ne peut plus baisser !"));
            return;
        }
        b.stages[stat] = after;
        events.add(new BattleEvent.StatChange(side, delta > 0));
        String how = Math.abs(after - before) >= 2 ? (delta > 0 ? " monte beaucoup !" : " baisse beaucoup !")
                : (delta > 0 ? " monte !" : " baisse !");
        say(STAT_NAMES[stat] + " de " + who + how);
    }

    /** Inflige un statut (s'il n'en a pas déjà un, et si son type ne l'en protège pas). */
    private void inflict(int side, int status, boolean announceFailure) {
        Pokemon p = sides[side].mon;
        boolean immune = (status == Pokemon.POISON && (isType(p, TYPE_POISON) || isType(p, TYPE_STEEL)))
                || (status == Pokemon.BURN && isType(p, TYPE_FIRE))
                || (status == Pokemon.FREEZE && isType(p, TYPE_ICE));
        if (p.status != Pokemon.OK || immune) {
            if (announceFailure) say("Mais cela échoue !");
            return;
        }
        p.status = status;
        if (status == Pokemon.SLEEP) p.sleepTurns = 2 + rng.nextInt(4);
        events.add(new BattleEvent.Status(side, status));
        say(name(side) + switch (status) {
            case Pokemon.SLEEP -> " s'endort !";
            case Pokemon.POISON -> " est empoisonné !";
            case Pokemon.BURN -> " est brûlé !";
            case Pokemon.FREEZE -> " est gelé !";
            default -> " est paralysé ! Il aura du mal à attaquer !";
        });
    }

    private void confuse(int side, boolean announceFailure) {
        Battler b = sides[side];
        if (b.confusion > 0) {
            if (announceFailure) say(name(side) + " est déjà confus !");
            return;
        }
        b.confusion = 2 + rng.nextInt(4);
        say(name(side) + " est confus !");
    }

    // ------------------------------------------------------------------ PV

    private void hurt(int side, int amount, int effectiveness, boolean crit) {
        Pokemon p = sides[side].mon;
        p.hp = Math.max(0, p.hp - Math.max(0, amount));
        events.add(new BattleEvent.Damage(side, p.hp, effectiveness, crit));
    }

    private void heal(int side, int amount, String message) {
        Pokemon p = sides[side].mon;
        p.hp = Math.min(p.maxHp(), p.hp + amount);
        events.add(new BattleEvent.Heal(side, p.hp));
        say(message);
    }

    private void recoil(int side, int amount) {
        if (amount <= 0) return;
        hurt(side, amount, 10, false);
        say(name(side) + " subit le contrecoup !");
    }

    /** Fin du tour : poison, brûlure (1/8 des PV max), Vampigraine. */
    private void endOfTurn() {
        for (int side : new int[]{PLAYER, FOE}) {
            Battler b = sides[side];
            Pokemon p = b.mon;
            if (p.fainted() || over()) continue;
            if (p.status == Pokemon.POISON) {
                hurt(side, Math.max(1, p.maxHp() / 8), 10, false);
                say(name(side) + " souffre du poison !");
            } else if (p.status == Pokemon.BURN) {
                hurt(side, Math.max(1, p.maxHp() / 8), 10, false);
                say(name(side) + " souffre de sa brûlure !");
            }
            if (b.seeded && !p.fainted()) {
                int drain = Math.min(p.hp, Math.max(1, p.maxHp() / 8));
                hurt(side, drain, 10, false);
                Pokemon other = sides[1 - side].mon;
                if (!other.fainted()) {
                    other.hp = Math.min(other.maxHp(), other.hp + drain);
                    events.add(new BattleEvent.Heal(1 - side, other.hp));
                }
                say("Vampigraine draine l'énergie de " + name(side) + " !");
            }
        }
    }

    // ------------------------------------------------------------------ K.O., expérience, fin

    /** Gère les K.O. ; renvoie vrai si le tour doit s'arrêter là. */
    private boolean checkFaints() {
        if (over()) return true;
        boolean stop = false;
        if (sides[FOE].mon.fainted()) {
            events.add(new BattleEvent.Faint(FOE));
            say(name(FOE) + " est K.O. !");
            giveExperience();
            int next = firstHealthy(foeTeam, -1);
            if (next < 0) {
                if (trainer) {
                    prizeMoney = 20 * maxLevel(foeTeam);
                    say("Vous avez battu " + trainerName + " !");
                    say(playerName + " remporte " + prizeMoney + " ₽ !");
                }
                finish(Outcome.WON);
                return true;
            }
            sides[FOE].reset(foeTeam.get(next), next);
            participants.clear();
            if (!sides[PLAYER].mon.fainted()) participants.add(sides[PLAYER].index);
            events.add(sendOutEvent(FOE));
            say(trainerName + " envoie " + sides[FOE].mon.name() + " !");
            stop = true;
        }
        if (sides[PLAYER].mon.fainted()) {
            events.add(new BattleEvent.Faint(PLAYER));
            say(sides[PLAYER].mon.name() + " est K.O. !");
            participants.remove(sides[PLAYER].index);
            if (firstHealthy(playerTeam, -1) < 0) {
                say(playerName + " n'a plus de POKéMON en forme !");
                say(playerName + " est hors-jeu !");
                finish(Outcome.LOST);
            } else {
                mustReplace = true;
            }
            stop = true;
        }
        return stop;
    }

    /**
     * Expérience de la 3e génération : (expérience de base de l'espèce × niveau) / 7, ×1,5 contre un
     * dresseur, partagée entre les Pokémon qui ont combattu.
     */
    private void giveExperience() {
        Pokemon foe = sides[FOE].mon;
        List<Integer> winners = new ArrayList<>();
        for (int i : participants) if (i < playerTeam.size() && !playerTeam.get(i).fainted()) winners.add(i);
        if (winners.isEmpty()) return;
        long total = (long) foe.base().expYield() * foe.level / 7;
        if (trainer) total = total * 3 / 2;
        long each = Math.max(1, total / winners.size());
        for (int i : winners) {
            Pokemon p = playerTeam.get(i);
            if (p.level >= 100) continue;
            say(p.name() + " gagne " + each + " points EXP. !");
            p.exp += each;
            while (p.level < 100 && p.exp >= p.expForLevel(p.level + 1)) {
                events.add(new BattleEvent.Exp(i, p.expForLevel(p.level + 1)));
                p.levelUp();
                events.add(new BattleEvent.LevelUp(i, p.level, p.hp, p.maxHp()));
                say(p.name() + " monte au N. " + p.level + " !");
                for (int move : rules.movesLearnedAt(p.species, p.level)) {
                    if (p.learn(move)) {
                        say(p.name() + " apprend " + rules.move(move).name() + " !");
                    } else {
                        events.add(new BattleEvent.LearnPrompt(i, move));
                    }
                }
            }
            events.add(new BattleEvent.Exp(i, p.exp));
        }
    }

    private BattleEvent sendOutEvent(int side) {
        Battler b = sides[side];
        return new BattleEvent.SendOut(side, b.index, b.mon.hp, b.mon.maxHp(), b.mon.level, b.mon.status);
    }

    private void finish(Outcome o) {
        outcome = o;
        events.add(new BattleEvent.End(o));
    }

    private static int firstHealthy(List<Pokemon> team, int except) {
        for (int i = 0; i < team.size(); i++) if (i != except && !team.get(i).fainted()) return i;
        return -1;
    }

    private static int maxLevel(List<Pokemon> team) {
        int m = 1;
        for (Pokemon p : team) m = Math.max(m, p.level);
        return m;
    }

    // ------------------------------------------------------------------ outils

    /** « SALAMÈCHE » pour le joueur, « RATTATA ennemi » pour l'adversaire. */
    private String name(int side) {
        String n = sides[side].mon.name();
        return side == FOE ? n + (trainer ? " ennemi" : " sauvage") : n;
    }

    private void say(String text) {
        if (!text.isEmpty()) events.add(new BattleEvent.Message(text));
    }

    private List<BattleEvent> copy() {
        return new ArrayList<>(events);
    }

    /** Multiplicateur d'un cran de statistique : 2/8 … 2/2 … 8/2. */
    private static int staged(int value, int stage) {
        int s = Math.max(-6, Math.min(6, stage));
        return s >= 0 ? value * (2 + s) / 2 : value * 2 / (2 - s);
    }

    /** Précision / esquive : table de la 3e génération (33 % … 300 %). */
    private static double accuracyRatio(int stage) {
        double[] r = {0.33, 0.36, 0.43, 0.50, 0.60, 0.75, 1.0, 1.33, 1.66, 2.0, 2.33, 2.66, 3.0};
        return r[stage + 6];
    }

    /** Pour les tests : le tirage au sort est-il reproductible ? */
    Random random() {
        return rng;
    }

    GameData data() {
        return data;
    }
}
