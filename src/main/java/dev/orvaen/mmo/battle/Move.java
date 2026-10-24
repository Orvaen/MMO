package dev.orvaen.mmo.battle;

/**
 * Une attaque, telle qu'elle est décrite dans la ROM (12 octets par attaque) :
 * <pre>
 * 0 effet · 1 puissance · 2 type · 3 précision · 4 PP · 5 chance d'effet secondaire (%)
 * 6 cible · 7 priorité (signée) · 8 drapeaux (contact…)
 * </pre>
 *
 * @param effect   ce que fait l'attaque en plus des dégâts (voir {@link Effects})
 * @param power    puissance (0 : attaque de statut, sans dégâts directs)
 * @param accuracy précision en % (0 : ne rate jamais)
 * @param chance   chance de l'effet secondaire en % (brûlure de Flammèche…)
 * @param priority +1 pour Vive-Attaque : agit avant les autres
 */
public record Move(int id, String name, int effect, int power, int type, int accuracy, int pp, int chance,
                   int target, int priority, int flags) {

    /** Types « physiques » en 3e génération (les autres sont « spéciaux ») : dépend du type, pas de l'attaque. */
    private static final boolean[] PHYSICAL = {
            true, true, true, true, true, true, true, true, true, false, // Normal… Acier, ???
            false, false, false, false, false, false, false, false        // Feu… Ténèbres
    };

    /** Vrai si l'attaque utilise l'Attaque et la Défense (sinon l'Attaque Spé. et la Défense Spé.). */
    public boolean physical() {
        return type >= 0 && type < PHYSICAL.length && PHYSICAL[type];
    }

    /** Vrai si l'attaque ne fait pas de dégâts directs (Rugissement, Poudre Dodo…). */
    public boolean status() {
        return power == 0;
    }

    /** Attaque vide (emplacement libre). */
    public static Move none() {
        return new Move(0, "-", 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
