package dev.orvaen.mmo.scenes;

import java.awt.geom.Rectangle2D;

/** Accès de test à la barre d'équipe (classe interne au paquet des scènes). */
public final class PartyBarProbe {

    private PartyBarProbe() {
    }

    public static Rectangle2D shortcut(int i, int w, int h) {
        return ShortcutBar.button(i, w, h);
    }

    public static Rectangle2D slot(int i, int w, int h) {
        return PartyBar.slot(i, w, h);
    }
}
