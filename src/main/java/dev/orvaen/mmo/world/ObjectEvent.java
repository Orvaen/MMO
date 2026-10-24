package dev.orvaen.mmo.world;

/**
 * Un personnage (ou objet) posé sur une carte, tel que décrit dans les événements de la ROM.
 * Structure de 24 octets (Rouge Feu / Vert Feuille et Émeraude) :
 * <pre>
 * 0x00 u8  n° local         0x01 u8  graphisme       0x04 s16 x    0x06 s16 y
 * 0x08 u8  élévation        0x09 u8  déplacement     0x0A u8  rayon (x : 4 bits bas, y : 4 bits hauts)
 * 0x0C u16 type de dresseur 0x10 ptr script          0x14 u16 drapeau (0 : toujours présent)
 * </pre>
 */
public record ObjectEvent(int localId, int graphicsId, int x, int y, int elevation, int movementType,
                          int rangeX, int rangeY, int trainerType, int script, int flag) {

    public static final int BYTES = 24;

    /** Types de déplacement de la ROM utilisés par le moteur. */
    public static final int MOVE_LOOK_AROUND = 1, MOVE_WANDER = 2, MOVE_WANDER_UD = 3, MOVE_WANDER_DU = 4,
            MOVE_WANDER_LR = 5, MOVE_WANDER_RL = 6, MOVE_FACE_UP = 7, MOVE_FACE_DOWN = 8, MOVE_FACE_LEFT = 9,
            MOVE_FACE_RIGHT = 10;

    /**
     * Présent dans une nouvelle partie ? Les objets à drapeau sont des personnages d'histoire ou des objets
     * ramassables qui apparaissent / disparaissent selon la progression : sans moteur de scripts,
     * on n'affiche que ceux qui sont toujours là.
     */
    public boolean alwaysPresent() {
        return flag == 0;
    }
}
