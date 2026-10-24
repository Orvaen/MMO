package dev.orvaen.mmo.battle;

/**
 * Ce qui se passe pendant un tour, dans l'ordre. Le moteur de combat ({@link Battle}) produit cette liste
 * d'un coup ; l'écran de combat la « rejoue » ensuite à son rythme, avec les animations.
 * <p>
 * {@code side} : {@link Battle#PLAYER} (0) ou {@link Battle#FOE} (1).
 */
public sealed interface BattleEvent {

    /** Un message à afficher (on attend que le joueur appuie sur A pour certains). */
    record Message(String text) implements BattleEvent {}

    /** Un Pokémon lance une attaque (animation de l'attaquant). */
    record MoveUsed(int side, Move move) implements BattleEvent {}

    /**
     * Le Pokémon du côté {@code side} perd des PV : la barre descend jusqu'à {@code hpAfter}.
     * {@code effectiveness} × 10 (20 : super efficace, 5 : pas très efficace) pour le bruitage.
     */
    record Damage(int side, int hpAfter, int effectiveness, boolean critical) implements BattleEvent {}

    /** Le Pokémon regagne des PV (Vol-Vie, Soin…). */
    record Heal(int side, int hpAfter) implements BattleEvent {}

    /** Une statistique monte ou baisse (animation de flèches). */
    record StatChange(int side, boolean up) implements BattleEvent {}

    /** Un statut est infligé (empoisonné, brûlé…). */
    record Status(int side, int status) implements BattleEvent {}

    /** Le Pokémon est K.O. : il descend et disparaît. */
    record Faint(int side) implements BattleEvent {}

    /**
     * Un Pokémon entre en scène (lancer de Poké Ball). {@code index} : sa place dans l'équipe ;
     * PV, niveau et statut <em>à ce moment-là</em> (pour l'affichage).
     */
    record SendOut(int side, int index, int hp, int maxHp, int level, int status) implements BattleEvent {}

    /** Un Pokémon rentre dans sa Poké Ball (changement). */
    record Withdraw(int side) implements BattleEvent {}

    /** La barre d'expérience du Pokémon n° {@code index} de l'équipe se remplit. */
    record Exp(int index, long expAfter) implements BattleEvent {}

    /** Montée de niveau (fanfare) : nouveaux niveau, PV et PV max. */
    record LevelUp(int index, int level, int hp, int maxHp) implements BattleEvent {}

    /** Le Pokémon veut apprendre une attaque mais en connaît déjà 4 : il faut choisir. */
    record LearnPrompt(int index, int move) implements BattleEvent {}

    /** Lancer de Poké Ball : nombre de secousses (0 à 3) et capture réussie ou non. */
    record BallThrow(int shakes, boolean caught) implements BattleEvent {}

    /** Le combat est fini. */
    record End(Battle.Outcome outcome) implements BattleEvent {}
}
