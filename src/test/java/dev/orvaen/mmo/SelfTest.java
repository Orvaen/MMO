package dev.orvaen.mmo;

import dev.orvaen.mmo.data.GameData;
import dev.orvaen.mmo.data.Species;
import dev.orvaen.mmo.data.WildEncounters;
import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.Input.Button;
import dev.orvaen.mmo.gfx.GbaGfx;
import dev.orvaen.mmo.rom.GameProfile;
import dev.orvaen.mmo.rom.Lz77;
import dev.orvaen.mmo.rom.Rom;
import dev.orvaen.mmo.rom.RomException;
import dev.orvaen.mmo.scenes.BattleScene;
import dev.orvaen.mmo.scenes.DexScene;
import dev.orvaen.mmo.scenes.Direction;
import dev.orvaen.mmo.scenes.OverworldScene;
import dev.orvaen.mmo.tools.Extractor;
import dev.orvaen.mmo.world.Connection;
import dev.orvaen.mmo.world.GameMap;
import dev.orvaen.mmo.world.MapLayout;
import dev.orvaen.mmo.world.MapRenderer;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Tests du moteur, sans dépendance externe : {@code java ... dev.orvaen.mmo.SelfTest}.
 * Ils tournent sur une ROM synthétique (aucune donnée de jeu réelle).
 */
public final class SelfTest {

    private interface Check {
        void run() throws Exception;
    }

    private static final List<String> failures = new ArrayList<>();
    private static int passed;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Path home = Files.createTempDirectory("orvaen-mmo-test");
        System.setProperty("orvaen.home", home.toString());

        test("LZ77 : aller-retour sur données aléatoires et répétitives", SelfTest::lz77RoundTrip);
        test("LZ77 : copie chevauchante (run d'un octet)", SelfTest::lz77Overlap);
        test("LZ77 : un bloc tronqué lève une RomException", SelfTest::lz77Truncated);
        test("Couleurs BGR555 → ARGB", SelfTest::colors);
        test("En-tête : détection du jeu et refus des ROM inconnues", SelfTest::header);
        test("Espèces : noms, stats, types, emplacements vides", SelfTest::species);
        test("Espèces : sprite de face décompressé avec sa palette", SelfTest::sprite);
        test("Cartes : groupes, noms, dimensions, warps, liaisons", SelfTest::maps);
        test("Cartes : collisions, élévation, herbe, bordure", SelfTest::mapBlocks);
        test("Rendu : sol, calque supérieur, tileset secondaire", SelfTest::rendering);
        test("Rencontres : table d'herbe et probabilités", SelfTest::wild);
        test("Moteur : marche, liaison vers la carte voisine", SelfTest::walkThroughConnection);
        test("Moteur : warp en marchant sur une porte", SelfTest::warp);
        test("Moteur : collision avec un obstacle", SelfTest::collision);
        test("Moteur : rencontre sauvage dans l'herbe puis fuite", SelfTest::encounter);
        test("Moteur : rendu d'une frame et du Pokédex", SelfTest::frames);
        test("Export : sprites, cartes et CSV", () -> exportTo(home.resolve("export")));

        System.out.printf("%n%d réussi(s), %d échec(s)%n", passed, failures.size());
        for (String f : failures) System.out.println("  ✗ " + f);
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static void test(String name, Check check) {
        try {
            check.run();
            passed++;
            System.out.println("  ✓ " + name);
        } catch (Throwable t) {
            failures.add(name + " — " + t);
            System.out.println("  ✗ " + name + " — " + t);
        }
    }

    private static void check(boolean cond, String message) {
        if (!cond) throw new AssertionError(message);
    }

    private static void eq(Object expected, Object actual, String what) {
        if (!expected.equals(actual)) throw new AssertionError(what + " : attendu " + expected + ", obtenu " + actual);
    }

    private static GameData data() {
        return new GameData(new Rom(SyntheticRom.build()));
    }

    // ------------------------------------------------------------------ couche ROM

    private static void lz77RoundTrip() {
        Random r = new Random(42);
        for (int size : new int[]{1, 7, 8, 9, 100, 2048, 5000}) {
            byte[] noise = new byte[size];
            r.nextBytes(noise);
            byte[] pattern = new byte[size];
            for (int i = 0; i < size; i++) pattern[i] = (byte) (i % 13 < 9 ? 0x11 : i);
            for (byte[] in : List.of(noise, pattern)) {
                byte[] out = Lz77.decompress(Lz77Encoder.encode(in), 0);
                check(Arrays.equals(in, out), "aller-retour LZ77 différent pour " + size + " octets");
            }
        }
    }

    private static void lz77Overlap() {
        // 'A' littéral puis référence (distance 1, longueur 18) : 19 fois 'A'
        byte[] lz = {0x10, 19, 0, 0, 0x40, 'A', (byte) 0xF0, 0x00};
        byte[] out = Lz77.decompress(lz, 0);
        eq(19, out.length, "taille");
        for (byte b : out) check(b == 'A', "copie chevauchante incorrecte");
    }

    private static void lz77Truncated() {
        byte[] lz = {0x10, 64, 0, 0, 0x00, 1, 2};
        try {
            Lz77.decompress(lz, 0);
            throw new AssertionError("aucune exception");
        } catch (RomException expected) {
            // ok
        }
    }

    private static void colors() {
        eq(0xFFFFFFFF, GbaGfx.bgr555ToArgb(0x7FFF), "blanc");
        eq(0xFFFF0000, GbaGfx.bgr555ToArgb(0x001F), "rouge");
        eq(0xFF00FF00, GbaGfx.bgr555ToArgb(0x03E0), "vert");
        eq(0xFF0000FF, GbaGfx.bgr555ToArgb(0x7C00), "bleu");
        eq(0xFF000000, GbaGfx.bgr555ToArgb(0), "noir");
    }

    private static void header() {
        Rom rom = new Rom(SyntheticRom.build());
        eq("BPRE", rom.gameCode(), "code jeu");
        eq(GameProfile.FIRERED_LEAFGREEN, rom.profile(), "profil");
        eq("Rouge Feu (anglais)", rom.profile().displayName("BPRE"), "nom affiché");
        eq("Vert Feuille (français)", GameProfile.FIRERED_LEAFGREEN.displayName("BPGF"), "nom affiché VF");
        eq(GameProfile.EMERALD, GameProfile.detect("BPEF"), "Émeraude FR");
        check(GameProfile.detect("AXVE") == null, "Rubis ne doit pas encore être accepté");

        byte[] other = SyntheticRom.build();
        other[0xAC] = 'Z';
        try {
            new Rom(other);
            throw new AssertionError("ROM inconnue acceptée");
        } catch (RomException expected) {
            check(expected.getMessage().contains("non supporté"), "message clair attendu");
        }
        try {
            new Rom(new byte[16]);
            throw new AssertionError("fichier minuscule accepté");
        } catch (RomException expected) {
            // ok
        }
    }

    private static void species() {
        GameData d = data();
        Species s = d.species.get(1);
        eq("TESTMON", s.name(), "nom");
        eq(45, s.hp(), "PV");
        eq(49, s.attack(), "attaque");
        eq(53, s.spDefense(), "déf. spé.");
        eq(10, s.type1(), "type 1");
        eq("Feu", Species.typeName(s.type1()), "nom du type 1");
        eq("Plante", Species.typeName(s.type2()), "nom du type 2");
        eq(190, s.catchRate(), "taux de capture");
        eq("Bébé-Éclair", d.species.get(2).name(), "nom accentué");
        check(!d.species.get(2).hasSecondType(), "type unique attendu");
        eq(2, d.species.all().size(), "espèces réelles (les vides sont filtrées)");
        eq(300, s.baseStatTotal(), "total des stats");
    }

    private static void sprite() {
        GameData d = data();
        BufferedImage img = d.species.frontSprite(1);
        eq(64, img.getWidth(), "largeur");
        eq(64, img.getHeight(), "hauteur");
        eq(0xFFFF0000, img.getRGB(0, 0), "pixel (0,0) rouge");
        eq(0xFF0000FF, img.getRGB(63, 63), "pixel (63,63) bleu");
        eq(0, img.getRGB(1, 0) >>> 24, "couleur 0 transparente");
        check(d.species.frontSprite(1) == img, "le sprite doit être mis en cache");
    }

    private static void maps() {
        GameData d = data();
        eq(1, d.maps.groupCount(), "groupes");
        eq(2, d.maps.mapCount(0), "cartes du groupe 0");
        GameMap a = d.maps.load(0, 0);
        GameMap b = d.maps.load(0, 1);
        eq("BOURG TEST", a.name, "nom A");
        eq("ROUTE TEST", b.name, "nom B");
        eq(6, a.width(), "largeur A");
        eq(5, a.height(), "hauteur A");
        eq(1, a.warps.size(), "warps A");
        eq(1, a.warps.get(0).x(), "warp x");
        eq(1, a.warps.get(0).targetNum(), "warp → carte 1");
        eq(1, a.connections.size(), "liaisons A");
        Connection c = a.connections.get(0);
        eq(Connection.Direction.SOUTH, c.direction(), "liaison au sud");
        eq(1, c.offset(), "décalage");
        eq(0, b.connections.size(), "liaisons B");
        check(a == d.maps.load(0, 0), "cache des cartes");
        check(!d.maps.exists(0, 2) && !d.maps.exists(3, 0), "cartes inexistantes");
    }

    private static void mapBlocks() {
        GameMap a = data().maps.load(0, 0);
        MapLayout l = a.layout;
        eq(0, MapLayout.collision(l.blockAt(0, 0)), "sol libre");
        eq(1, MapLayout.collision(l.blockAt(5, 0)), "arbre bloquant");
        eq(3, MapLayout.elevation(l.blockAt(0, 0)), "élévation 3");
        check(a.isLandEncounterAt(0, 2), "herbe en ligne 2");
        check(!a.isLandEncounterAt(0, 3), "pas d'herbe en ligne 3");
        eq(0x02, a.behaviorAt(3, 2), "comportement herbe haute");
        check(MapLayout.collision(l.blockAt(-1, -1)) != 0, "la bordure bloque");
        eq(2, MapLayout.metatileId(l.blockAt(-3, 7)), "la bordure se répète");
    }

    private static void rendering() {
        GameData d = data();
        GameMap a = d.maps.load(0, 0);
        MapRenderer.Rendered r = d.renderer.render(a);
        eq(96, r.below().getWidth(), "largeur en pixels");
        eq(80, r.below().getHeight(), "hauteur en pixels");
        eq(0xFF00FF00, r.below().getRGB(3, 3), "sol vert");
        eq(0xFF0000FF, r.below().getRGB(20, 36), "herbe bleue");
        eq(0xFF00FF00, r.below().getRGB(85, 5), "pied de l'arbre vert (sous le joueur)");
        eq(0xFF0000FF, r.above().getRGB(85, 5), "cime de l'arbre bleue (au-dessus du joueur)");
        eq(0, r.above().getRGB(3, 3) >>> 24, "calque supérieur transparent ailleurs");
        eq(0xFFFFFF00, r.below().getRGB(3 * 16 + 4, 4 * 16 + 4), "métatile du tileset secondaire (palette 7)");
        eq(4, r.borderBelow().length, "bordure 2x2");
    }

    private static void wild() {
        GameData d = data();
        eq(1, d.wild.mapsWithLandEncounters(), "zones");
        WildEncounters.Table t = d.wild.land(0, 0).orElseThrow();
        eq(21, t.rate(), "taux");
        eq(12, t.slots().size(), "emplacements");
        check(d.wild.land(0, 1).isEmpty(), "pas de rencontres sur B");
        Random r = new Random(7);
        int first = 0;
        for (int i = 0; i < 10000; i++) if (t.roll(r).species() == 1) first++;
        check(first > 7700 && first < 8300, "≈80 % pour les 6 premiers emplacements (20+20+10x4), obtenu " + first);
    }

    // ------------------------------------------------------------------ moteur

    private static Game game() {
        Game g = new Game(data());
        g.push(new OverworldScene(g));
        return g;
    }

    private static OverworldScene world(Game g) {
        return (OverworldScene) g.top();
    }

    private static void hold(Game g, Button b, int frames) {
        g.input().set(b, true);
        for (int i = 0; i < frames; i++) g.simulateFrame();
        g.input().set(b, false);
        for (int i = 0; i < 25; i++) g.simulateFrame(); // fin de pas / fondu
    }

    private static void walkThroughConnection() {
        Game g = game();
        OverworldScene w = world(g);
        eq("BOURG TEST", w.currentMap().name, "carte de départ");
        eq(1, w.playerX(), "x de départ (sous le warp)");
        eq(2, w.playerY(), "y de départ");
        hold(g, Button.DOWN, 16 * 6);
        eq("ROUTE TEST", w.currentMap().name, "passage par la liaison sud");
        eq(0, w.playerX(), "x dans la carte voisine (décalage appliqué)");
        eq(2, w.playerY(), "arrêt au bord bas de la carte B");
    }

    private static void warp() {
        Game g = game();
        OverworldScene w = world(g);
        hold(g, Button.UP, 16);
        eq("ROUTE TEST", w.currentMap().name, "le warp mène à B");
        eq(2, w.playerX(), "arrivée sur le warp de B (x)");
        eq(1, w.playerY(), "arrivée sur le warp de B (y)");
    }

    private static void collision() {
        Game g = game();
        OverworldScene w = world(g);
        w.teleport(g.data.maps.load(0, 0));
        // Aller en (4,0) puis tenter d'entrer dans l'arbre en (5,0)
        hold(g, Button.RIGHT, 16 * 3 + 6);
        eq(4, w.playerX(), "x avant l'arbre");
        hold(g, Button.UP, 16 * 3 + 6);
        eq(0, w.playerY(), "en haut de la carte");
        hold(g, Button.RIGHT, 16 * 3);
        eq(4, w.playerX(), "l'arbre bloque le passage");
        eq(Direction.RIGHT, w.facing(), "le joueur regarde l'arbre");
    }

    private static void encounter() {
        Game g = game();
        OverworldScene w = world(g);
        w.setRandomSeed(1234);
        boolean met = false;
        for (int i = 0; i < 40 && !met; i++) {
            hold(g, i % 2 == 0 ? Button.RIGHT : Button.LEFT, 16 * 4);
            met = g.top() instanceof BattleScene;
        }
        check(met, "aucune rencontre après 40 allers-retours dans l'herbe");
        check(!g.save.seen.isEmpty(), "l'espèce rencontrée est marquée « vue »");
        for (int i = 0; i < 40; i++) g.simulateFrame(); // fin de l'intro
        BufferedImage frame = g.renderFrame();
        check(frame.getRGB(160, 20) != 0xFF000000, "l'écran de combat est dessiné");

        // Intro → menu → descendre sur FUIR → valider → texte de fuite → retour au monde
        hold(g, Button.A, 2);
        hold(g, Button.DOWN, 2);
        hold(g, Button.A, 2);
        hold(g, Button.A, 2);
        for (int i = 0; i < 60; i++) g.simulateFrame();
        check(g.top() instanceof OverworldScene, "retour au monde après la fuite");
    }

    private static void frames() {
        Game g = game();
        BufferedImage img = g.renderFrame();
        eq(Game.WIDTH, img.getWidth(), "largeur écran");
        // Le joueur est au centre ; à gauche du centre on doit voir de l'herbe bleue (ligne 2)
        eq(0xFF0000FF, img.getRGB(Game.WIDTH / 2 - 8 - 12, Game.HEIGHT / 2 - 8 + 2), "herbe visible à gauche du joueur");
        g.push(new DexScene(g));
        BufferedImage dex = g.renderFrame();
        check(dex.getRGB(5, 5) != 0xFF000000, "Pokédex dessiné");
    }

    private static void exportTo(Path dir) throws Exception {
        new Extractor(data()).exportAll(dir);
        check(Files.exists(dir.resolve("especes.csv")), "CSV");
        check(Files.exists(dir.resolve("sprites/001_TESTMON.png")), "sprite PNG");
        check(Files.exists(dir.resolve("maps/00.000_BOURG_TEST.png")), "carte PNG");
        String csv = Files.readString(dir.resolve("especes.csv"));
        check(csv.contains("1;TESTMON;Feu;Plante;45"), "ligne CSV de TESTMON");
        check(Files.exists(dir.resolve("sprites/002_Bebe-Eclair.png")), "nom de fichier sans accents");
    }
}
