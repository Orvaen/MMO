package dev.orvaen.mmo.data;

import dev.orvaen.mmo.player.PlayerGraphics;
import dev.orvaen.mmo.rom.Rom;
import dev.orvaen.mmo.script.ScriptTables;
import dev.orvaen.mmo.script.Trainers;
import dev.orvaen.mmo.world.DoorAnims;
import dev.orvaen.mmo.world.MapRenderer;
import dev.orvaen.mmo.world.ObjectGraphics;
import dev.orvaen.mmo.world.TileAnims;
import dev.orvaen.mmo.world.MapRepository;

/** Tout ce que le moteur a extrait de la ROM du joueur, au même endroit. */
public final class GameData {

    public final Rom rom;
    public final SpeciesRepository species;
    public final MapRepository maps;
    public final WildEncounters wild;
    public final MapRenderer renderer;
    public final DoorAnims doors;
    public final ObjectGraphics objects;
    public final TileAnims tileAnims;
    public final ScriptTables scripts;
    public final dev.orvaen.mmo.audio.AudioEngine audio;
    public final dev.orvaen.mmo.audio.Songs songs;
    private Trainers trainers;
    private TitleGraphics title;
    private dev.orvaen.mmo.battle.BattleData battle;
    private dev.orvaen.mmo.battle.BattleGraphics battleGraphics;
    private dev.orvaen.mmo.world.FieldEffects fieldEffects;
    public PlayerGraphics player;

    public GameData(Rom rom) {
        this.rom = rom;
        this.species = new SpeciesRepository(rom);
        this.maps = new MapRepository(rom);
        this.wild = new WildEncounters(rom, species.count());
        this.renderer = new MapRenderer(rom.profile());
        this.doors = new DoorAnims(rom);
        this.objects = new ObjectGraphics(rom);
        this.tileAnims = new TileAnims(rom);
        this.scripts = new ScriptTables(rom);
        this.audio = new dev.orvaen.mmo.audio.AudioEngine(rom);
        this.songs = dev.orvaen.mmo.audio.Songs.of(rom.profile());
        this.player = new PlayerGraphics(rom);
    }

    /** Images de l'écran titre (cherchées dans la ROM au premier appel). */
    public TitleGraphics titleGraphics() {
        if (title == null) title = new TitleGraphics(rom);
        return title;
    }

    /** Règles du combat : attaques, apprentissages, types, expérience (lues au premier appel). */
    public dev.orvaen.mmo.battle.BattleData battle() {
        if (battle == null) battle = new dev.orvaen.mmo.battle.BattleData(rom);
        return battle;
    }

    /** Images du combat : fonds, boîte de texte, Poké Ball (lues au premier combat). */
    public dev.orvaen.mmo.battle.BattleGraphics battleGraphics() {
        if (battleGraphics == null) battleGraphics = new dev.orvaen.mmo.battle.BattleGraphics(rom);
        return battleGraphics;
    }

    /** Effets de terrain du monde (herbe haute qui bouge…). */
    public dev.orvaen.mmo.world.FieldEffects fieldEffects() {
        if (fieldEffects == null) fieldEffects = new dev.orvaen.mmo.world.FieldEffects(rom);
        return fieldEffects;
    }

    /** Dresseurs de la ROM (cherchés au premier combat). */
    public Trainers trainers() {
        if (trainers == null) trainers = new Trainers(rom, species.count());
        return trainers;
    }

    public String summary() {
        int total = 0;
        for (int g = 0; g < maps.groupCount(); g++) total += maps.mapCount(g);
        StringBuilder anims = new StringBuilder();
        for (var a : tileAnims.anims()) anims.append(anims.isEmpty() ? "" : ", ").append(a.name());
        return String.format("%s · %d espèces · %d cartes · %d zones de rencontres · sprites du joueur : %s"
                        + " · %d graphismes de PNJ · %d portes animées · tuiles animées : %s · %d morceaux",
                rom, species.all().size(), total, wild.mapsWithLandEncounters(),
                player.available() ? "ROM" : "avatar de secours (" + player.problem() + ")",
                objects.count(), doors.count(), anims.isEmpty() ? "aucune" : anims, audio.songCount());
    }
}
