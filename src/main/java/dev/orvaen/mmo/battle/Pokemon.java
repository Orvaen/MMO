package dev.orvaen.mmo.battle;

import dev.orvaen.mmo.data.GameData;
import dev.orvaen.mmo.data.Species;

import java.util.Arrays;
import java.util.Random;

/**
 * Un Pokémon de l'équipe (ou un adversaire) : niveau, expérience, PV, attaques, statut.
 * <p>
 * Les statistiques suivent les formules de la 3e génération :
 * <pre>
 *   PV          = (2 × base + IV) × niveau / 100 + niveau + 10
 *   autre stat  = ((2 × base + IV) × niveau / 100 + 5) × nature (×1,1 / ×0,9 / ×1)
 * </pre>
 * (Les EV ne sont pas encore gérés.) Les IV vont de 0 à 31 ; la nature (0 à 24) monte une stat de 10 %
 * et en baisse une autre de 10 %.
 */
public final class Pokemon {

    /** Index des statistiques, dans l'ordre de la ROM. */
    public static final int HP = 0, ATTACK = 1, DEFENSE = 2, SPEED = 3, SP_ATTACK = 4, SP_DEFENSE = 5;

    /** Statuts durables (gardés après le combat). */
    public static final int OK = 0, SLEEP = 1, POISON = 2, BURN = 3, FREEZE = 4, PARALYSIS = 5;

    public final int species;
    public int level;
    public long exp;
    public final int nature;
    public final int[] ivs = new int[6];
    public int hp;
    public final int[] moves = new int[4];      // n° d'attaque (0 : emplacement vide)
    public final int[] pp = new int[4];
    public int status;
    public int sleepTurns;
    public String nickname = "";

    private final transient GameData data;

    private Pokemon(GameData data, int species, int level, int nature) {
        this.data = data;
        this.species = species;
        this.level = Math.max(1, Math.min(100, level));
        this.nature = Math.floorMod(nature, 25);
    }

    // ------------------------------------------------------------------ création

    /** Pokémon sauvage : IV et nature au hasard, les 4 dernières attaques apprises à ce niveau. */
    public static Pokemon wild(GameData data, int species, int level, Random rng) {
        Pokemon p = new Pokemon(data, species, level, rng.nextInt(25));
        for (int i = 0; i < 6; i++) p.ivs[i] = rng.nextInt(32);
        p.learnDefaultMoves();
        p.exp = data.battle().experienceFor(p.base().growthRate(), p.level);
        p.hp = p.maxHp();
        return p;
    }

    /**
     * Pokémon de dresseur : la ROM donne un « IV » de 0 à 255 pour toutes les stats, et parfois ses attaques.
     */
    public static Pokemon trainer(GameData data, int species, int level, int ivByte, int[] moves) {
        Pokemon p = new Pokemon(data, species, level, 0);
        Arrays.fill(p.ivs, ivByte * 31 / 255);
        boolean custom = moves != null && Arrays.stream(moves).anyMatch(m -> m != 0);
        if (custom) {
            System.arraycopy(moves, 0, p.moves, 0, 4);
            for (int i = 0; i < 4; i++) p.pp[i] = data.battle().move(p.moves[i]).pp();
        } else {
            p.learnDefaultMoves();
        }
        p.exp = data.battle().experienceFor(p.base().growthRate(), p.level);
        p.hp = p.maxHp();
        return p;
    }

    private void learnDefaultMoves() {
        int[] m = data.battle().movesAtLevel(species, level);
        System.arraycopy(m, 0, moves, 0, 4);
        for (int i = 0; i < 4; i++) pp[i] = data.battle().move(moves[i]).pp();
    }

    // ------------------------------------------------------------------ sauvegarde

    /**
     * Relit un Pokémon de la sauvegarde. Format (nombres) :
     * {@code espèce, niveau, exp, PV, nature, 6 IV, 4 attaques, 4 PP, statut}.
     * L'ancien format {@code espèce, niveau} (Pokémon reçu avant le système de combat) est complété.
     */
    public static Pokemon fromSave(GameData data, int[] v, String nickname) {
        int species = v[0], level = v.length > 1 ? v[1] : 5;
        if (v.length < 20) {
            Random rng = new Random(species * 7919L + level);       // même résultat à chaque lecture
            Pokemon p = wild(data, species, level, rng);
            p.nickname = nickname == null ? "" : nickname;
            return p;
        }
        Pokemon p = new Pokemon(data, species, level, v[4]);
        p.exp = v[2];
        p.hp = v[3];
        System.arraycopy(v, 5, p.ivs, 0, 6);
        System.arraycopy(v, 11, p.moves, 0, 4);
        System.arraycopy(v, 15, p.pp, 0, 4);
        p.status = v[19];
        p.hp = Math.max(0, Math.min(p.maxHp(), p.hp));
        p.nickname = nickname == null ? "" : nickname;
        return p;
    }

    /** Valeurs à écrire dans la sauvegarde (voir {@link #fromSave}). */
    public int[] toSave() {
        int[] v = new int[20];
        v[0] = species;
        v[1] = level;
        v[2] = (int) exp;
        v[3] = hp;
        v[4] = nature;
        System.arraycopy(ivs, 0, v, 5, 6);
        System.arraycopy(moves, 0, v, 11, 4);
        System.arraycopy(pp, 0, v, 15, 4);
        v[19] = status;
        return v;
    }

    // ------------------------------------------------------------------ statistiques

    public Species base() {
        return data.species.get(species);
    }

    public String name() {
        return nickname != null && !nickname.isBlank() ? nickname : base().name();
    }

    public int maxHp() {
        if (base().hp() == 1) return 1;                          // Munja
        return (2 * base().hp() + ivs[HP]) * level / 100 + level + 10;
    }

    /** Attaque, Défense, Vitesse, Attaque Spé. ou Défense Spé. (sans les modifications du combat). */
    public int stat(int which) {
        int b = switch (which) {
            case ATTACK -> base().attack();
            case DEFENSE -> base().defense();
            case SPEED -> base().speed();
            case SP_ATTACK -> base().spAttack();
            default -> base().spDefense();
        };
        int value = (2 * b + ivs[which]) * level / 100 + 5;
        int up = nature / 5 + 1, down = nature % 5 + 1;          // ordre des natures : Att, Déf, Vit, Att.Spé, Déf.Spé
        if (up != down) {
            if (which == up) value = value * 110 / 100;
            if (which == down) value = value * 90 / 100;
        }
        return value;
    }

    public boolean fainted() {
        return hp <= 0;
    }

    public int[] types() {
        return new int[]{base().type1(), base().type2()};
    }

    // ------------------------------------------------------------------ expérience

    public long expForLevel(int l) {
        return data.battle().experienceFor(base().growthRate(), l);
    }

    /** Part de la barre d'expérience remplie (0 à 1) vers le niveau suivant. */
    public double expProgress() {
        if (level >= 100) return 1;
        long from = expForLevel(level), to = expForLevel(level + 1);
        return to <= from ? 0 : Math.max(0, Math.min(1, (exp - from) / (double) (to - from)));
    }

    /** Remet PV, PP et statut au maximum (Centre Pokémon, maman…). */
    public void heal() {
        hp = maxHp();
        status = OK;
        sleepTurns = 0;
        for (int i = 0; i < 4; i++) pp[i] = data.battle().move(moves[i]).pp();
    }

    /** Monte d'un niveau ; les PV gagnés s'ajoutent aux PV actuels (comme dans le jeu). */
    public void levelUp() {
        int before = maxHp();
        level = Math.min(100, level + 1);
        hp = Math.min(maxHp(), hp + maxHp() - before);
    }

    /** Apprend une attaque s'il reste une place ; renvoie faux si les 4 emplacements sont pris. */
    public boolean learn(int move) {
        for (int i = 0; i < 4; i++) {
            if (moves[i] == move) return true;
            if (moves[i] == 0) {
                moves[i] = move;
                pp[i] = data.battle().move(move).pp();
                return true;
            }
        }
        return false;
    }

    /** Remplace l'attaque de l'emplacement {@code slot}. */
    public void replaceMove(int slot, int move) {
        moves[slot] = move;
        pp[slot] = data.battle().move(move).pp();
    }
}
