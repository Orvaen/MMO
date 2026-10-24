package dev.orvaen.mmo.world;

/**
 * Case « déclencheur » d'une carte (16 octets) : quand le joueur y pose le pied et que la variable
 * {@code var} vaut {@code value}, le script se lance (ex. le Professeur qui t'arrête à la sortie du bourg).
 * <pre>0x00 u16 x · 0x02 u16 y · 0x04 u8 élévation · 0x06 u16 variable · 0x08 u16 valeur · 0x0C ptr script</pre>
 */
public record CoordEvent(int x, int y, int elevation, int var, int value, int script) {

    public static final int BYTES = 16;
}
