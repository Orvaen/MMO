package dev.orvaen.mmo.world;

import dev.orvaen.mmo.gfx.GbaGfx;
import dev.orvaen.mmo.rom.GameProfile;
import dev.orvaen.mmo.rom.Lz77;
import dev.orvaen.mmo.rom.Rom;

/**
 * Tileset d'une carte : tuiles 8x8, palettes, métatiles 16x16 et leurs attributs.
 * Structure (FR/LG et Émeraude) :
 * <pre>
 * 0x00 u8  isCompressed     0x04 ptr tiles      0x08 ptr palettes
 * 0x01 u8  isSecondary      0x0C ptr metatiles
 * FR/LG    : 0x10 ptr callback · 0x14 ptr attributs (u32)
 * Émeraude : 0x10 ptr attributs (u16) · 0x14 ptr callback
 * </pre>
 */
public final class Tileset {

    public static final int METATILE_BYTES = 16; // 8 entrées u16 : 4 du calque bas, 4 du calque haut
    private static final int TOTAL_TILES = 1024;
    private static final int TOTAL_METATILES = 1024;

    public final int offset;
    public final boolean secondary;
    public final byte[] tiles;
    public final int[] palettes;   // 16 palettes x 16 couleurs (ARGB)
    private final Rom rom;
    private final GameProfile profile;
    private final int metatilesOffset;
    private final int attributesOffset;
    private final int metatileCount;

    public Tileset(Rom rom, int offset) {
        this.rom = rom;
        this.profile = rom.profile();
        this.offset = offset;
        boolean compressed = rom.u8(offset) != 0;
        this.secondary = rom.u8(offset + 1) != 0;

        int tilesPtr = rom.pointer(offset + 4);
        int expectedTiles = secondary ? TOTAL_TILES - profile.tilesInPrimary : profile.tilesInPrimary;
        if (tilesPtr < 0) {
            tiles = new byte[0];
        } else if (compressed && Lz77.looksCompressed(rom, tilesPtr)) {
            tiles = Lz77.decompress(rom, tilesPtr);
        } else {
            int bytes = Math.min(expectedTiles * GbaGfx.TILE_BYTES_4BPP, rom.size() - tilesPtr);
            tiles = rom.slice(tilesPtr, bytes);
        }

        int palPtr = rom.pointer(offset + 8);
        int palCount = 16;
        if (palPtr >= 0) {
            while (palCount > 0 && !rom.inRange(palPtr, palCount * 32)) palCount--;
            int[] read = GbaGfx.readPalettes(rom, palPtr, palCount);
            palettes = new int[16 * 16];
            System.arraycopy(read, 0, palettes, 0, read.length);
        } else {
            palettes = new int[16 * 16];
        }

        metatilesOffset = rom.pointer(offset + 0x0C);
        attributesOffset = rom.pointer(offset + profile.metatileAttrOffset);
        int expected = secondary ? TOTAL_METATILES - profile.metatilesInPrimary : profile.metatilesInPrimary;
        int count = 0;
        if (metatilesOffset >= 0) {
            count = Math.min(expected, (rom.size() - metatilesOffset) / METATILE_BYTES);
        }
        metatileCount = count;
    }

    public int metatileCount() {
        return metatileCount;
    }

    /** Entrée de tuile n° {@code slot} (0-7) du métatile local {@code index}. */
    public int metatileEntry(int index, int slot) {
        if (index < 0 || index >= metatileCount) return 0;
        return rom.u16(metatilesOffset + index * METATILE_BYTES + slot * 2);
    }

    /** Attributs bruts du métatile local (comportement, calque, rencontres…). */
    public long attributes(int index) {
        if (attributesOffset < 0 || index < 0 || index >= metatileCount) return 0;
        int o = attributesOffset + index * profile.metatileAttrSize;
        if (!rom.inRange(o, profile.metatileAttrSize)) return 0;
        return profile.metatileAttrSize == 4 ? rom.u32(o) : rom.u16(o);
    }
}
