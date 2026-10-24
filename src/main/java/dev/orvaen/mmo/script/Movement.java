package dev.orvaen.mmo.script;

import dev.orvaen.mmo.rom.Rom;

/**
 * Exécute une liste de mouvements de la ROM (applymovement) sur un personnage : marcher, sauter,
 * se tourner, attendre, s'exclamer… un octet par action, 0xFE pour finir.
 * Numérotation des actions de Rouge Feu / Vert Feuille (identique à Émeraude pour celles-ci).
 */
public final class Movement {

    /** Ce qu'un personnage (PNJ ou joueur) sait faire pour un script. */
    public interface Actor {
        int x();
        int y();
        /** 0 bas, 1 haut, 2 gauche, 3 droite. */
        int facing();
        void face(int dir);
        /** Avance de {@code tiles} cases (0 : sur place) en {@code frames} frames ; {@code hop} : saut. */
        void step(int dir, int tiles, int frames, boolean hop, boolean animate);
        boolean busy();
        void setVisible(boolean visible);
        /** Bulle au-dessus de la tête : 0 « ! », 1 « ? », 2 « !! », 3 « ♥ », 4 « × ». */
        void emote(int kind);
    }

    private static final int END = 0xFE;

    private final Rom rom;
    private final Actor actor;
    private final Actor player;
    private int pc;
    private int wait;
    private boolean done;

    public Movement(Rom rom, int script, Actor actor, Actor player) {
        this.rom = rom;
        this.pc = script;
        this.actor = actor;
        this.player = player;
        this.done = script < 0;
    }

    public boolean done() {
        return done;
    }

    /** Une frame. */
    public void update() {
        if (done || actor.busy()) return;
        if (wait > 0) {
            wait--;
            return;
        }
        // Les actions instantanées s'enchaînent dans la même frame
        for (int guard = 0; guard < 16 && !done && !actor.busy() && wait == 0; guard++) {
            if (!rom.inRange(pc, 1)) {
                done = true;
                return;
            }
            int a = rom.u8(pc++);
            if (a == END) {
                done = true;
                return;
            }
            run(a);
        }
    }

    private void run(int a) {
        if (a <= 0x03) actor.face(a);                                   // face_*
        else if (a <= 0x07) actor.face(a - 0x04);                       // face_*_fast
        else if (a <= 0x0B) actor.step(a - 0x08, 1, 48, false, true);   // walk_slower
        else if (a <= 0x0F) actor.step(a - 0x0C, 1, 32, false, true);   // walk_slow
        else if (a <= 0x13) actor.step(a - 0x10, 1, 16, false, true);   // walk
        else if (a <= 0x17) actor.step(a - 0x14, 2, 16, true, false);   // jump_2
        else if (a <= 0x1C) wait = new int[]{1, 2, 4, 8, 16}[a - 0x18];  // delay_*
        else if (a <= 0x20) actor.step(a - 0x1D, 1, 8, false, true);    // walk_fast
        else if (a <= 0x24) actor.step(a - 0x21, 0, 32, false, true);   // walk_in_place_slow
        else if (a <= 0x28) actor.step(a - 0x25, 0, 16, false, true);   // walk_in_place
        else if (a <= 0x2C) actor.step(a - 0x29, 0, 8, false, true);    // walk_in_place_fast
        else if (a <= 0x30) actor.step(a - 0x2D, 0, 4, false, true);    // walk_in_place_faster
        else if (a <= 0x34) actor.step(a - 0x31, 1, 8, false, false);   // courant
        else if (a <= 0x38) actor.step(a - 0x35, 1, 4, false, true);    // walk_faster
        else if (a <= 0x3C) actor.step(a - 0x39, 1, 4, false, false);   // slide
        else if (a <= 0x40) actor.step(a - 0x3D, 1, 8, false, true);    // player_run
        else if (a <= 0x44) actor.step(a - 0x41, 1, 12, false, true);   // player_run_slow
        else if (a <= 0x49) actor.step(Math.max(0, a - 0x46), 2, 16, true, false); // jump_special
        else if (a == 0x4A) actor.face(towardPlayer(false));
        else if (a == 0x4B) actor.face(towardPlayer(true));
        else if (a >= 0x4E && a <= 0x51) actor.step(a - 0x4E, 1, 16, true, false); // jump
        else if (a >= 0x52 && a <= 0x59) actor.step((a - 0x52) % 4, 0, 16, true, false); // jump_in_place
        else if (a == 0x60) actor.setVisible(false);
        else if (a == 0x61) actor.setVisible(true);
        else if (a >= 0x62 && a <= 0x66) {                              // émotes
            actor.emote(new int[]{0, 1, 4, 2, 3}[a - 0x62]);
            wait = 48;
        }
        // le reste (priorités, animations spéciales, vélo acrobatique…) n'a pas d'effet visible ici
    }

    /** Direction vers le joueur (ou à l'opposé). */
    private int towardPlayer(boolean away) {
        int dx = player.x() - actor.x(), dy = player.y() - actor.y();
        int dir;
        if (Math.abs(dx) > Math.abs(dy)) dir = dx > 0 ? 3 : 2;
        else dir = dy > 0 ? 0 : 1;
        if (!away) return dir;
        return new int[]{1, 0, 3, 2}[dir];
    }
}
