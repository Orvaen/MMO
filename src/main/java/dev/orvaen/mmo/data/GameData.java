package dev.orvaen.mmo.data;

import dev.orvaen.mmo.rom.Rom;
import dev.orvaen.mmo.world.MapRenderer;
import dev.orvaen.mmo.world.MapRepository;

/** Tout ce que le moteur a extrait de la ROM du joueur, au même endroit. */
public final class GameData {

    public final Rom rom;
    public final SpeciesRepository species;
    public final MapRepository maps;
    public final WildEncounters wild;
    public final MapRenderer renderer;

    public GameData(Rom rom) {
        this.rom = rom;
        this.species = new SpeciesRepository(rom);
        this.maps = new MapRepository(rom);
        this.wild = new WildEncounters(rom, species.count());
        this.renderer = new MapRenderer(rom.profile());
    }

    public String summary() {
        int total = 0;
        for (int g = 0; g < maps.groupCount(); g++) total += maps.mapCount(g);
        return String.format("%s · %d espèces · %d cartes · %d zones de rencontres",
                rom, species.all().size(), total, wild.mapsWithLandEncounters());
    }
}
