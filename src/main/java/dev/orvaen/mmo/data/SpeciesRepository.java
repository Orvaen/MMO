package dev.orvaen.mmo.data;

import dev.orvaen.mmo.gfx.GbaGfx;
import dev.orvaen.mmo.rom.GameProfile;
import dev.orvaen.mmo.rom.Lz77;
import dev.orvaen.mmo.rom.Rom;
import dev.orvaen.mmo.rom.RomException;
import dev.orvaen.mmo.rom.TextCodec;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Extrait les espèces (noms, statistiques, sprites de face) depuis la ROM. */
public final class SpeciesRepository {

    private final Rom rom;
    private final Species[] species;
    private final int frontSprites;
    private final int palettes;
    private final Map<Integer, BufferedImage> spriteCache = new ConcurrentHashMap<>();

    public SpeciesRepository(Rom rom) {
        this.rom = rom;
        int names = rom.requirePointer(GameProfile.PTR_SPECIES_NAMES, "les noms d'espèces");
        int info = rom.requirePointer(GameProfile.PTR_SPECIES_INFO, "les statistiques d'espèces");
        this.frontSprites = rom.requirePointer(GameProfile.PTR_FRONT_SPRITES, "les sprites de face");
        this.palettes = rom.requirePointer(GameProfile.PTR_SPECIES_PALETTES, "les palettes d'espèces");

        species = new Species[GameProfile.SPECIES_COUNT];
        for (int id = 0; id < species.length; id++) {
            String name = TextCodec.read(rom, names + id * GameProfile.SPECIES_NAME_LENGTH,
                    GameProfile.SPECIES_NAME_LENGTH);
            int o = info + id * GameProfile.SPECIES_INFO_SIZE;
            species[id] = new Species(id, name,
                    rom.u8(o), rom.u8(o + 1), rom.u8(o + 2), rom.u8(o + 3), rom.u8(o + 4), rom.u8(o + 5),
                    rom.u8(o + 6), rom.u8(o + 7),
                    rom.u8(o + 8), rom.u8(o + 9),
                    rom.u8(o + 0x13));
        }
    }

    public int count() {
        return species.length;
    }

    public Species get(int id) {
        if (id < 0 || id >= species.length) {
            throw new IllegalArgumentException("Espèce inconnue : " + id);
        }
        return species[id];
    }

    /** Espèces réelles, sans les emplacements vides. */
    public List<Species> all() {
        List<Species> out = new ArrayList<>();
        for (Species s : species) {
            if (!s.isPlaceholder()) out.add(s);
        }
        return Collections.unmodifiableList(out);
    }

    /** Sprite de face 64x64, extrait et mis en cache à la demande. */
    public BufferedImage frontSprite(int id) {
        return spriteCache.computeIfAbsent(id, this::extractFrontSprite);
    }

    private BufferedImage extractFrontSprite(int id) {
        try {
            int gfx = rom.requirePointer(frontSprites + id * 8, "le sprite #" + id);
            int pal = rom.requirePointer(palettes + id * 8, "la palette #" + id);
            byte[] tiles = Lz77.decompress(rom, gfx);
            int[] colors = GbaGfx.palettesFromBytes(Lz77.decompress(rom, pal));
            // Émeraude stocke 2 frames l'une sous l'autre (64x128) : on garde la première.
            return GbaGfx.sprite(tiles, 8, 8, colors);
        } catch (RomException e) {
            return new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        }
    }
}
