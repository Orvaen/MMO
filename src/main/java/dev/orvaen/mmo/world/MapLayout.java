package dev.orvaen.mmo.world;

/**
 * Grille d'une carte : un bloc par case de 16x16.
 * Chaque bloc = id de métatile (bits 0-9), collision (10-11), élévation (12-15).
 */
public final class MapLayout {

    public static final int METATILE_MASK = 0x03FF;

    public final int width;
    public final int height;
    public final int[] blocks;
    public final int borderWidth;
    public final int borderHeight;
    public final int[] border;
    public final Tileset primary;
    public final Tileset secondary;

    public MapLayout(int width, int height, int[] blocks, int borderWidth, int borderHeight, int[] border,
                     Tileset primary, Tileset secondary) {
        this.width = width;
        this.height = height;
        this.blocks = blocks;
        this.borderWidth = borderWidth;
        this.borderHeight = borderHeight;
        this.border = border;
        this.primary = primary;
        this.secondary = secondary;
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    /** Bloc à (x, y) ; hors carte, la bordure se répète comme dans le jeu original. */
    public int blockAt(int x, int y) {
        if (inBounds(x, y)) return blocks[y * width + x];
        if (border.length == 0 || borderWidth == 0 || borderHeight == 0) return 0x0C00; // bloquant
        int bx = Math.floorMod(x, borderWidth);
        int by = Math.floorMod(y, borderHeight);
        return border[by * borderWidth + bx] | 0x0C00; // la bordure est toujours infranchissable
    }

    public static int metatileId(int block) { return block & METATILE_MASK; }
    public static int collision(int block) { return (block >> 10) & 0x3; }
    public static int elevation(int block) { return (block >> 12) & 0xF; }
}
