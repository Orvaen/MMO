package dev.orvaen.mmo.world;

import dev.orvaen.mmo.gfx.GbaGfx;
import dev.orvaen.mmo.rom.GameProfile;

import java.awt.image.BufferedImage;

/**
 * Transforme une carte en images : un calque « sol » (sous le joueur)
 * et un calque « dessus » (cimes d'arbres, toits… au-dessus du joueur).
 */
public final class MapRenderer {

    public static final int TILE = 16;
    private static final int LAYER_COVERED = 1;

    private final GameProfile profile;

    public MapRenderer(GameProfile profile) {
        this.profile = profile;
    }

    /** Résultat du rendu d'une carte. */
    public record Rendered(BufferedImage below, BufferedImage above, BufferedImage[] borderBelow,
                           BufferedImage[] borderAbove) {}

    public Rendered render(GameMap map) {
        MapLayout layout = map.layout;
        int[] palettes = combinedPalettes(layout);
        int w = layout.width * TILE, h = layout.height * TILE;
        int[] below = new int[w * h];
        int[] above = new int[w * h];
        java.util.Arrays.fill(below, palettes[0] | 0xFF00_0000);
        for (int y = 0; y < layout.height; y++) {
            for (int x = 0; x < layout.width; x++) {
                int id = MapLayout.metatileId(layout.blocks[y * layout.width + x]);
                drawMetatile(layout, palettes, id, below, above, w, x * TILE, y * TILE);
            }
        }

        int n = layout.border.length;
        BufferedImage[] bBelow = new BufferedImage[n];
        BufferedImage[] bAbove = new BufferedImage[n];
        for (int i = 0; i < n; i++) {
            int[] lo = new int[TILE * TILE];
            int[] hi = new int[TILE * TILE];
            java.util.Arrays.fill(lo, palettes[0] | 0xFF00_0000);
            drawMetatile(layout, palettes, MapLayout.metatileId(layout.border[i]), lo, hi, TILE, 0, 0);
            bBelow[i] = GbaGfx.toImage(lo, TILE, TILE);
            bAbove[i] = GbaGfx.toImage(hi, TILE, TILE);
        }
        return new Rendered(GbaGfx.toImage(below, w, h), GbaGfx.toImage(above, w, h), bBelow, bAbove);
    }

    /** Palettes 0..N du tileset primaire, puis N..12 du secondaire, comme dans la VRAM du jeu. */
    private int[] combinedPalettes(MapLayout layout) {
        int[] out = new int[16 * 16];
        int split = profile.palsInPrimary * 16;
        System.arraycopy(layout.primary.palettes, 0, out, 0, split);
        if (layout.secondary != null) {
            System.arraycopy(layout.secondary.palettes, split, out, split, GameProfile.PALS_TOTAL * 16 - split);
        }
        out[0] = 0xFF00_0000; // couleur de fond : noir
        return out;
    }

    private void drawMetatile(MapLayout layout, int[] palettes, int metatileId,
                              int[] below, int[] above, int destWidth, int dx, int dy) {
        Tileset ts = layout.primary;
        int local = metatileId;
        if (metatileId >= profile.metatilesInPrimary) {
            ts = layout.secondary;
            local = metatileId - profile.metatilesInPrimary;
        }
        if (ts == null || local >= ts.metatileCount()) return;

        boolean topIsBelow = profile.layerType(ts.attributes(local)) == LAYER_COVERED;
        for (int slot = 0; slot < 8; slot++) {
            int entry = ts.metatileEntry(local, slot);
            int tileIndex = entry & 0x3FF;
            boolean hflip = (entry & 0x400) != 0;
            boolean vflip = (entry & 0x800) != 0;
            int pal = (entry >> 12) & 0xF;

            byte[] tiles = layout.primary.tiles;
            if (tileIndex >= profile.tilesInPrimary) {
                if (layout.secondary == null) continue;
                tiles = layout.secondary.tiles;
                tileIndex -= profile.tilesInPrimary;
            }
            int quadrant = slot & 3;
            int tx = dx + (quadrant & 1) * 8;
            int ty = dy + (quadrant >> 1) * 8;
            int[] target = (slot < 4 || topIsBelow) ? below : above;
            GbaGfx.drawTile(target, destWidth, tx, ty, tiles, tileIndex, palettes, pal, hflip, vflip);
        }
    }
}
