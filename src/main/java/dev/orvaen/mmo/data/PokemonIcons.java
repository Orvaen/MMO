package dev.orvaen.mmo.data;

import dev.orvaen.mmo.gfx.GbaGfx;
import dev.orvaen.mmo.rom.GameProfile;
import dev.orvaen.mmo.rom.Rom;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Les petites icônes animées des Pokémon (celles du menu d'équipe), lues dans la ROM.
 * Elles servent au Pokémon qui suit le joueur dans le monde.
 * <p>
 * L'en-tête de la ROM pointe vers : une table d'icônes (un pointeur par espèce vers 2 images 32x32, non
 * compressées), le n° de palette de chaque icône (0, 1 ou 2), et les 3 palettes.
 */
public final class PokemonIcons {

    private static final int FRAME_BYTES = 32 * 32 / 2;

    private final Rom rom;
    private final Map<Integer, BufferedImage[]> cache = new HashMap<>();

    public PokemonIcons(Rom rom) {
        this.rom = rom;
    }

    /** Les 2 images de l'icône de l'espèce, ou null. */
    public BufferedImage[] frames(int species) {
        return cache.computeIfAbsent(species, this::read);
    }

    private BufferedImage[] read(int species) {
        try {
            int icons = rom.pointer(GameProfile.PTR_ICONS), ids = rom.pointer(GameProfile.PTR_ICON_PALETTE_IDS);
            int pals = rom.pointer(GameProfile.PTR_ICON_PALETTES);
            if (icons < 0 || ids < 0 || pals < 0) return null;
            int data = rom.pointer(icons + species * 4);
            if (data < 0 || !rom.inRange(data, FRAME_BYTES * 2)) return null;
            int palId = Math.min(2, rom.u8(ids + species));
            int pal = rom.pointer(pals + palId * 8);
            if (pal < 0) return null;
            int[] colors = GbaGfx.readPalettes(rom, pal, 1);
            BufferedImage[] out = new BufferedImage[2];
            for (int f = 0; f < 2; f++) out[f] = GbaGfx.sprite(rom.slice(data + f * FRAME_BYTES, FRAME_BYTES), 4, 4, colors);
            return out;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
