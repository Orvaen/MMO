package dev.orvaen.mmo.world;

/**
 * Panneau ou élément à lire sur une carte (événement « bg » de la ROM, 12 octets) :
 * x (u16), y (u16), élévation (u8), type (u8), puis le pointeur du script.
 * Types 0 à 4 : lisible de n'importe où / depuis le sud, nord, est, ouest — les autres
 * (objets cachés, bases secrètes) ne sont pas des textes.
 */
public record SignEvent(int x, int y, int kind, int script) {

    public static final int BYTES = 12;

    public boolean readable() {
        return kind <= 4 && script >= 0;
    }
}
