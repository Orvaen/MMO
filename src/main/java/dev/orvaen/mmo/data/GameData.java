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

    /** Valeur calculée une seule fois, au premier appel (sûr entre plusieurs fils d'exécution). */
    private static final class Lazy<T> {
        private final java.util.function.Supplier<T> make;
        private volatile T value;

        Lazy(java.util.function.Supplier<T> make) {
            this.make = make;
        }

        T get() {
            T v = value;
            if (v != null) return v;
            synchronized (this) {
                if (value == null) value = make.get();
                return value;
            }
        }
    }

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
    // Lu au premier besoin (chaque élément a son propre verrou : en lire un n'en bloque pas un autre)
    private final Lazy<Trainers> trainers;
    private final Lazy<TitleGraphics> title;
    private final Lazy<dev.orvaen.mmo.battle.BattleData> battle;
    private final Lazy<dev.orvaen.mmo.battle.BattleGraphics> battleGraphics;
    private final Lazy<dev.orvaen.mmo.world.FieldEffects> fieldEffects;
    private final Lazy<dev.orvaen.mmo.battle.MoveAnimations> moveAnimations;
    private final Lazy<PokemonIcons> icons;
    private final Lazy<FollowerSprites> followerSprites;
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
        this.trainers = new Lazy<>(() -> new Trainers(rom, species.count()));
        this.title = new Lazy<>(() -> new TitleGraphics(rom));
        this.battle = new Lazy<>(() -> new dev.orvaen.mmo.battle.BattleData(rom));
        this.battleGraphics = new Lazy<>(() -> new dev.orvaen.mmo.battle.BattleGraphics(rom));
        this.fieldEffects = new Lazy<>(() -> new dev.orvaen.mmo.world.FieldEffects(rom));
        this.moveAnimations = new Lazy<>(() -> new dev.orvaen.mmo.battle.MoveAnimations(rom));
        this.icons = new Lazy<>(() -> new PokemonIcons(rom));
        this.followerSprites = new Lazy<>(FollowerSprites::fromConfig);
    }

    /** Images de l'écran titre (cherchées dans la ROM au premier appel). */
    public TitleGraphics titleGraphics() {
        return title.get();
    }

    /** Règles du combat : attaques, apprentissages, types, expérience (lues au premier appel). */
    public dev.orvaen.mmo.battle.BattleData battle() {
        return battle.get();
    }

    /** Images du combat : fonds, boîte de texte, Poké Ball (lues au premier combat). */
    public dev.orvaen.mmo.battle.BattleGraphics battleGraphics() {
        return battleGraphics.get();
    }

    /** Animations d'attaque (scripts, images et sons de la ROM), lues au premier combat. */
    public dev.orvaen.mmo.battle.MoveAnimations moveAnimations() {
        return moveAnimations.get();
    }

    /** Petites icônes animées des Pokémon (le Pokémon qui suit le joueur). */
    public PokemonIcons icons() {
        return icons.get();
    }

    /** Vrais sprites des Pokémon qui suivent (téléchargés une fois chez le joueur). */
    public FollowerSprites followerSprites() {
        return followerSprites.get();
    }

    /** Effets de terrain du monde (herbe haute qui bouge…). */
    public dev.orvaen.mmo.world.FieldEffects fieldEffects() {
        return fieldEffects.get();
    }

    /** Dresseurs de la ROM (cherchés au premier combat). */
    public Trainers trainers() {
        return trainers.get();
    }

    /**
     * Lit en arrière-plan ce qui servira plus tard (images du combat, animations d'attaque, effets de terrain…),
     * pendant que le joueur est sur l'écran titre : pas d'à-coup au premier pas ni au premier combat.
     */
    public void warmUp() {
        Thread t = new Thread(() -> {
            try {
                fieldEffects();
                icons();
                battle();
                battleGraphics();
                moveAnimations();
                trainers();
            } catch (RuntimeException ignored) {
                // sera relu au besoin
            }
        }, "Préchargement de la ROM");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
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
