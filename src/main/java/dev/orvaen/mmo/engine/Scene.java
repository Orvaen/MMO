package dev.orvaen.mmo.engine;

import java.awt.Graphics2D;

/** Un écran du jeu (monde, combat, Pokédex…). */
public interface Scene {

    void update(Input input);

    /** Dessine sur l'écran virtuel 240x160. */
    void render(Graphics2D g);

    /**
     * Dessine par-dessus, à la résolution réelle de la fenêtre (interface nette, texte lissé),
     * après l'image du jeu agrandie. Coordonnées en pixels de la fenêtre, comme la souris.
     */
    default void renderHiRes(Graphics2D g, int width, int height) {}

    /**
     * Vrai : la scène remplit toute la fenêtre (le monde) ; son écran virtuel fait la taille de la
     * fenêtre divisée par le zoom. Faux : écran classique 240x160 agrandi au mieux.
     */
    default boolean fillsWindow() {
        return false;
    }

    /**
     * Fraction de pixel virtuel (0 à 1) dont la caméra a avancé au-delà de ce qui est dessiné
     * dans l'écran virtuel : le moteur décale l'image d'autant de pixels réels (défilement fluide).
     */
    default double subpixelX() {
        return 0;
    }

    default double subpixelY() {
        return 0;
    }

    /** Les scènes en dessous restent-elles dessinées (menus en surimpression) ? */
    default boolean isOverlay() {
        return false;
    }

    default void onEnter() {}

    /** Le temps passé sous cette scène compte-t-il comme temps de jeu (carte de dresseur) ? */
    default boolean countsPlayTime() {
        return false;
    }
}
