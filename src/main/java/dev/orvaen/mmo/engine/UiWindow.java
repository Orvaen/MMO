package dev.orvaen.mmo.engine;

import java.awt.Graphics2D;

/**
 * Fenêtre d'interface non bloquante (Pokédex, carte de dresseur…), comme sur PokeMMO : plusieurs peuvent
 * rester ouvertes pendant qu'on joue. Elles prennent la souris quand elle est au-dessus d'elles ;
 * le clavier reste au jeu, sauf si la fenêtre au premier plan saisit du texte.
 */
public interface UiWindow {

    /** Identifiant (une seule fenêtre ouverte par identifiant). */
    String id();

    HiWindow frame();

    /**
     * Une frame. {@code focused} : fenêtre au premier plan (elle reçoit la souris et le clavier).
     * Renvoie faux pour se fermer.
     */
    boolean update(Input in, boolean focused);

    void render(Graphics2D g, int width, int height, boolean focused);

    /** La fenêtre saisit du texte : le jeu ne reçoit pas le clavier. */
    default boolean capturesKeyboard() {
        return false;
    }
}
