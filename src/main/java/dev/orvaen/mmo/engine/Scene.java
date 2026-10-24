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

    /** Les scènes en dessous restent-elles dessinées (menus en surimpression) ? */
    default boolean isOverlay() {
        return false;
    }

    default void onEnter() {}
}
