package dev.orvaen.mmo.world;

/** Point de passage entre deux cartes (porte, escalier, tapis de sortie…). */
public record Warp(int x, int y, int elevation, int targetWarpId, int targetGroup, int targetNum) {

    /** Id spécial des jeux Gen 3 : « retourner là d'où l'on vient ». */
    public static final int DYNAMIC = 0x7F;

    public boolean isDynamic() {
        return targetWarpId == DYNAMIC || (targetGroup == DYNAMIC && targetNum == DYNAMIC);
    }
}
