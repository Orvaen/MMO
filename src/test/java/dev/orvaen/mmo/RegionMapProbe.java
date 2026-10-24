package dev.orvaen.mmo;

import dev.orvaen.mmo.data.RegionMap;

/** Pixel du centre d'une case de la grille des lieux, sur l'écran 240 × 160 de la carte. */
final class RegionMapProbe {

    private RegionMapProbe() {
    }

    static int cellX(int x) {
        return RegionMap.GRID_X + x * 8 + 4;
    }

    static int cellY(int y) {
        return RegionMap.GRID_Y + y * 8 + 4;
    }
}
