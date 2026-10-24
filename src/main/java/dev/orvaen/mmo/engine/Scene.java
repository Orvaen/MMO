package dev.orvaen.mmo.engine;

import java.awt.Graphics2D;

/** Un écran du jeu (monde, combat, Pokédex…). */
public interface Scene {

    void update(Input input);

    /** Dessine sur l'écran virtuel 240x160. */
    void render(Graphics2D g);

    /** Les scènes en dessous restent-elles dessinées (menus en surimpression) ? */
    default boolean isOverlay() {
        return false;
    }

    default void onEnter() {}
}
