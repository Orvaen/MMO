package dev.orvaen.mmo;

import dev.orvaen.mmo.data.GameData;
import dev.orvaen.mmo.data.PlayerLook;
import dev.orvaen.mmo.data.Species;
import dev.orvaen.mmo.data.WildEncounters;
import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.Input.Button;
import dev.orvaen.mmo.engine.RomLauncher;
import dev.orvaen.mmo.engine.Storage;
import dev.orvaen.mmo.gfx.GbaGfx;
import dev.orvaen.mmo.rom.GameProfile;
import dev.orvaen.mmo.rom.Lz77;
import dev.orvaen.mmo.rom.Rom;
import dev.orvaen.mmo.rom.RomCheck;
import dev.orvaen.mmo.rom.RomException;
import dev.orvaen.mmo.scenes.BattleScene;
import dev.orvaen.mmo.scenes.CharacterCreatorScene;
import dev.orvaen.mmo.scenes.DexScene;
import dev.orvaen.mmo.scenes.Direction;
import dev.orvaen.mmo.scenes.OverworldScene;
import dev.orvaen.mmo.scenes.PlayerSprite;
import dev.orvaen.mmo.scenes.StartMenuScene;
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
        void run() throws Throwable;
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
        test("Personnage : apparence, encodage et valeurs hors bornes", SelfTest::lookEncoding);
        test("Sprites du joueur : localisés dans la ROM (marche, face, dos)", SelfTest::playerGraphicsFound);
        test("Sprites du joueur : teinture par pièce, ombres et zones respectées", SelfTest::playerRecolor);
        test("Sprites du joueur : repli sur l'avatar dessiné", SelfTest::playerFallback);
        test("Personnage : 1er lancement → création, nom tapé, tenue changée", SelfTest::createCharacter);
        test("Personnage : impossible de valider sans nom", SelfTest::creatorRequiresName);
        test("Personnage : modification depuis le menu, puis annulation", SelfTest::editCharacter);
        test("Personnage : le personnage est rechargé au lancement suivant", SelfTest::characterPersists);
        test("ROM : vérification de l'en-tête pour la fenêtre de choix", () -> romCheck(home));
        test("ROM : un chemin par jeu, ancien réglage repris, dernier jeu relancé", () -> romSlots(home));
        test("Fenêtres : appel natif de la barre de titre sombre (sonde libc hors Windows)", SelfTest::nativeTitleBar);
        test("Écran titre : fond tiré de la ROM, Jouer / Personnage", SelfTest::titleScreen);
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

    /** Nouvelle partie vierge (sauvegarde effacée) pour isoler chaque test. */
    private static Game freshGame() {
        try {
            Files.deleteIfExists(Storage.DIR.resolve("save-BPRE.properties"));
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        return new Game(data());
    }

    private static Game game() {
        Game g = freshGame();
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
        eq(g.screenWidth(), img.getWidth(), "largeur écran");
        check(g.screenWidth() > Game.WIDTH, "le monde voit plus large que la GBA : " + g.screenWidth());
        // Le joueur est au centre ; à gauche du centre on doit voir de l'herbe bleue (ligne 2)
        eq(0xFF0000FF, img.getRGB(g.screenWidth() / 2 - 8 - 12, g.screenHeight() / 2 - 8 + 2), "herbe visible à gauche du joueur");
        // Zoom : 960x640 en automatique → x2 ; « + » rapproche, l'écran virtuel rétrécit
        eq(2, g.zoom(), "zoom automatique pour 960x640");
        int before = g.screenWidth();
        g.input().addZoom(1);
        g.simulateFrame();
        eq(3, g.zoom(), "zoom +1");
        check(g.screenWidth() < before, "zoomer montre moins de carte");
        g.input().addZoom(-1);
        g.simulateFrame();
        BufferedImage window = g.renderWindow(1920, 1080);
        check(window.getRGB(5, 540) != 0xFF000000 && window.getRGB(1914, 540) != 0xFF000000,
                "le monde remplit la fenêtre jusqu'aux bords (pas de bandes noires)");
        g.push(new DexScene(g));
        BufferedImage dex = g.renderFrame();
        check(dex.getRGB(5, 5) != 0xFF000000, "Pokédex dessiné");
    }

    // ------------------------------------------------------------------ personnage

    private static void lookEncoding() {
        PlayerLook a = new PlayerLook(1, PlayerLook.SKIN_TONES[3], 0xFF8800, PlayerLook.ORIGIN, 0x2A9D8F,
                0x123456, PlayerLook.ORIGIN, 0xFFFFFF);
        eq(a, PlayerLook.decode(a.encode()), "aller-retour");
        eq("v3.1.e0ac80.ff8800.o.2a9d8f.123456.o.ffffff", a.encode(), "encodage");
        check(PlayerLook.decode("abc") == null && PlayerLook.decode("v3.1.o") == null
                && PlayerLook.decode("v3.1.zz.o.o.o.o.o.o") == null && PlayerLook.decode(null) == null,
                "les chaînes abîmées sont refusées");
        PlayerLook v2 = PlayerLook.decode("v2.1.3.4.1.7.2.9.5");
        check(v2 != null && v2.body() == 1 && v2.skin() == PlayerLook.SKIN_TONES[3] && v2.hair() == PlayerLook.DYES[4],
                "ancien format v2 (index de teintes) converti en couleurs");
        eq(1, PlayerLook.decode("2.2.4.1.7.2.9.5.6").body(), "ancien format v1 (cheveux longs) → femme");
        eq(0, PlayerLook.decode("2.0.4.1.7.2.9.5.6").body(), "ancien format v1 (cheveux courts) → homme");
        PlayerLook clamped = new PlayerLook(5, -7, 0x1FFFFFF, 0, 0, 0, 0, 0);
        check(clamped.body() < PlayerLook.BODIES.length && clamped.skin() == PlayerLook.ORIGIN
                && clamped.hair() == 0xFFFFFF, "valeurs ramenées dans les bornes");

        PlayerLook d = PlayerLook.defaults();
        eq(PlayerLook.ORIGIN, d.rgb(PlayerLook.Part.TOP), "par défaut : couleurs d'origine");
        eq("D'origine", d.colorName(PlayerLook.Part.TOP), "nom de la couleur d'origine");
        eq("Teint d'origine", d.colorName(PlayerLook.Part.SKIN), "nom du teint d'origine");
        eq(PlayerLook.DYES[1], d.cyclePreset(PlayerLook.Part.HAT, 1).hat(), "teinte suivante");
        eq(PlayerLook.DYES[PlayerLook.DYES.length - 1], d.cyclePreset(PlayerLook.Part.HAT, -1).hat(), "boucle vers la fin");
        PlayerLook custom = d.with(PlayerLook.Part.HAIR, 0x123457);
        eq("Personnalisée", custom.colorName(PlayerLook.Part.HAIR), "couleur libre de la roue");
        eq(-1, custom.presetIndex(PlayerLook.Part.HAIR), "pas une teinte proposée");
        eq(PlayerLook.DYES[0], custom.cyclePreset(PlayerLook.Part.HAIR, 1).hair(), "depuis une couleur libre, on repart du début");
        eq(PlayerLook.DYES.length, PlayerLook.DYE_NAMES.length, "un nom par teinture");
        eq(PlayerLook.SKIN_TONES.length, PlayerLook.SKIN_NAMES.length, "un nom par teinte de peau");
        Random r = new Random(5);
        for (int i = 0; i < 200; i++) {
            PlayerLook p = PlayerLook.random(r);
            eq(p, PlayerLook.decode(p.encode()), "tenue aléatoire valide");
        }
    }

    // ------------------------------------------------------------------ sprites du joueur

    private static double lightness(int argb) {
        int r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
        return (Math.max(r, Math.max(g, b)) + Math.min(r, Math.min(g, b))) / 510.0;
    }

    private static void playerGraphicsFound() {
        GameData d = data();
        check(d.player.available(), "sprites trouvés : " + d.player.problem());
        eq(16, d.player.overworld(0, 0).width(), "largeur frame de marche");
        eq(32, d.player.overworld(1, 8).height(), "hauteur frame de marche");
        eq(64, d.player.front(1).width(), "portrait 64x64");
        eq(64, d.player.back(0).height(), "dos 64x64");
        eq(0xc, d.player.front(0).index(30, 12), "pixel repère du portrait");
        eq(0x5, d.player.back(1).index(30, 45), "pixel repère du dos (non compressé)");
        check(d.summary().contains("sprites du joueur : ROM"), "résumé --info");
    }

    private static void playerRecolor() {
        GameData d = data();
        int red = GbaGfx.bgr555ToArgb(SyntheticRom.PLAYER_RED);
        int redDark = GbaGfx.bgr555ToArgb(SyntheticRom.PLAYER_RED_DARK);

        PlayerSprite original = new PlayerSprite(d.player, PlayerLook.defaults());
        check(original.fromRom(), "sprites ROM utilisés");
        BufferedImage o = original.overworldFrame(Direction.DOWN, 0);
        eq(red, o.getRGB(5, 14), "origine : couvre-chef inchangé");
        eq(red, o.getRGB(5, 24), "origine : haut inchangé");
        eq(0, o.getRGB(0, 0) >>> 24, "fond transparent");

        int blue = indexOf("Bleu");
        PlayerLook look = PlayerLook.defaults().with(PlayerLook.Part.HAT, PlayerLook.DYES[blue]);
        BufferedImage dyed = new PlayerSprite(d.player, look).overworldFrame(Direction.DOWN, 0);
        int hat = dyed.getRGB(5, 14), hatShade = dyed.getRGB(5, 15);
        check((hat & 0xFF) > (hat >> 16 & 0xFF), "couvre-chef teint en bleu");
        check(lightness(hatShade) < lightness(hat), "l'ombre du couvre-chef reste plus sombre");
        check(lightness(redDark) < lightness(red), "(contrôle de la palette de test)");
        eq(red, dyed.getRGB(5, 24), "le haut, même index, garde sa couleur");
        eq(o.getRGB(5, 18), dyed.getRGB(5, 18), "la peau n'est pas touchée");

        BufferedImage right = new PlayerSprite(d.player, look).overworldFrame(Direction.RIGHT, 0);
        BufferedImage left = new PlayerSprite(d.player, look).overworldFrame(Direction.LEFT, 0);
        eq(left.getRGB(5, 15), right.getRGB(10, 15), "la vue de droite est le miroir de la gauche");

        PlayerLook top = PlayerLook.defaults().with(PlayerLook.Part.TOP, PlayerLook.DYES[indexOf("Vert")]);
        BufferedImage face = new PlayerSprite(d.player, top).front();
        int shirt = face.getRGB(30, 30);
        check((shirt >> 8 & 0xFF) > (shirt >> 16 & 0xFF), "portrait : haut teint en vert");
        eq(red, face.getRGB(30, 12), "portrait : casquette d'origine");
        eq(red, face.getRGB(46, 31), "portrait : la balle tenue en main n'est pas teinte");

        PlayerLook skin = PlayerLook.defaults().with(PlayerLook.Part.SKIN, PlayerLook.SKIN_TONES[6]);
        BufferedImage s = new PlayerSprite(d.player, skin).overworldFrame(Direction.DOWN, 0);
        check(s.getRGB(5, 18) != o.getRGB(5, 18), "la teinte de peau change la peau");
    }

    private static int indexOf(String dyeName) {
        for (int i = 0; i < PlayerLook.DYE_NAMES.length; i++) if (PlayerLook.DYE_NAMES[i].equals(dyeName)) return i;
        throw new AssertionError("teinture inconnue " + dyeName);
    }

    private static void playerFallback() {
        byte[] bytes = SyntheticRom.build();
        bytes[0xAC + 2] = 'E'; // BPEE : Émeraude, sprites du joueur non pris en charge
        GameData d;
        try {
            d = new GameData(new Rom(bytes));
        } catch (RomException e) {
            // la ROM synthétique n'a pas les structures Émeraude : on vérifie le repli autrement
            d = null;
        }
        dev.orvaen.mmo.player.PlayerGraphics none = new dev.orvaen.mmo.player.PlayerGraphics(new Rom(bytes));
        check(!none.available() && none.problem() != null, "Émeraude : repli annoncé");
        PlayerSprite fallback = new PlayerSprite(none, new PlayerLook(1, 2, 3, 4, 5, 6, 7, 8));
        check(!fallback.fromRom(), "avatar de secours");
        eq(64, fallback.front().getWidth(), "portrait de secours");
        eq(32, fallback.overworldFrame(Direction.UP, 1).getHeight(), "frame de secours");
        check(d == null || d.summary().contains("avatar de secours"), "résumé --info");
    }

    private static void tap(Game g, Button b) {
        g.input().set(b, true);
        g.simulateFrame();
        g.input().set(b, false);
        g.simulateFrame();
    }

    private static void typeText(Game g, String text) {
        g.input().type(text);
        for (int i = 0; i < 10; i++) g.simulateFrame();
    }

    /** Clic de souris sur un point de la fenêtre (vue 1280x800). */
    private static void click(Game g, java.awt.Point p) {
        g.input().move(p.x, p.y);
        g.simulateFrame();
        g.input().setMouseButton(true);
        g.simulateFrame();
        g.input().setMouseButton(false);
        g.simulateFrame();
    }

    private static void createCharacter() {
        Game g = freshGame();
        g.push(CharacterCreatorScene.firstScene(g));
        g.renderWindow(1280, 800);
        check(g.top() instanceof CharacterCreatorScene, "le 1er lancement ouvre la création");
        CharacterCreatorScene c = (CharacterCreatorScene) g.top();
        check(c.isTypingName(), "le champ du nom est actif d'entrée");

        typeText(g, "Ra7yan#\b\b");               // '#' refusé, puis 2 effacements → "Ra7y"
        eq("Ra7y", c.name(), "saisie + effacement + caractère refusé");
        typeText(g, "an Lefèvre-Dupont\n");          // limité à 12 caractères, Entrée valide
        eq("Ra7yan Lefèv", c.name(), "limite de 12 caractères");
        check(!c.isTypingName(), "Entrée termine la saisie");
        eq(0, c.focus(), "on passe à la première ligne (Genre)");

        // Clavier : genre et teint
        tap(g, Button.RIGHT);
        eq(1, c.look().body(), "genre changé au clavier");
        tap(g, Button.DOWN);
        tap(g, Button.RIGHT);
        eq(PlayerLook.SKIN_TONES[1], c.look().skin(), "teint changé au clavier");

        // Souris : menu déroulant des cheveux (ligne 3), choix « Rouge »
        click(g, c.anchor("selector:3"));
        check(c.dropdownOpen(), "clic sur le sélecteur → menu déroulant");
        click(g, c.anchor("item:3:1"));
        check(!c.dropdownOpen(), "le menu se ferme après un choix");
        eq(PlayerLook.DYES[1], c.look().hair(), "cheveux teints par le menu");

        // Molette sur le sélecteur du chapeau (ligne 2) : teinte suivante
        java.awt.Point hat = c.anchor("selector:2");
        g.input().move(hat.x, hat.y);
        g.input().addWheel(1);
        g.simulateFrame();
        eq(PlayerLook.DYES[1], c.look().hat(), "molette sur un sélecteur");

        // Roue de couleur du haut (ligne 4) : couleur libre dans le carré, puis OK
        click(g, c.anchor("wheel:4"));
        check(c.pickerOpen(), "clic sur la roue → sélecteur de couleur");
        java.awt.Point sv = c.anchor("sv");
        click(g, new java.awt.Point(sv.x + 40, sv.y - 30));
        int picked = c.look().top();
        check(picked >= 0 && c.look().presetIndex(PlayerLook.Part.TOP) < 0, "couleur libre choisie : " + picked);
        click(g, c.anchor("ok"));
        check(!c.pickerOpen(), "OK ferme le sélecteur");

        // Flèches de rotation, rendu et validation
        click(g, c.anchor("arrowRight"));
        check(c.sprite().fromRom(), "l'aperçu utilise les sprites de la ROM");
        BufferedImage img = g.renderWindow(1280, 800);
        eq(1280, img.getWidth(), "rendu haute résolution");
        click(g, c.anchor("create"));
        for (int i = 0; i < 30; i++) g.simulateFrame();
        check(g.top() instanceof OverworldScene, "après création, on arrive dans le monde");
        eq("Ra7yan Lefèv", g.save.name, "nom sauvegardé");
        eq(c.look(), g.save.look, "apparence sauvegardée");
        eq(picked, g.save.look.top(), "couleur libre sauvegardée");
    }

    private static void creatorRequiresName() {
        Game g = freshGame();
        g.push(CharacterCreatorScene.firstScene(g));
        g.renderWindow(1280, 800);
        CharacterCreatorScene c = (CharacterCreatorScene) g.top();
        typeText(g, "   \n");                       // que des espaces
        check(!c.isTypingName(), "saisie terminée");
        click(g, c.anchor("create"));
        check(g.top() == c, "on reste sur la création");
        check(c.isTypingName(), "retour à la saisie du nom");
        check(!g.save.hasCharacter(), "rien n'est sauvegardé");
    }

    private static void editCharacter() {
        Game g = game();
        g.save.name = "Rayan";
        g.save.look = PlayerLook.defaults();
        OverworldScene w = world(g);
        g.push(new StartMenuScene(g, w));
        tap(g, Button.DOWN);                      // PERSO
        tap(g, Button.A);
        check(g.top() instanceof CharacterCreatorScene, "le menu ouvre la modification");
        CharacterCreatorScene c = (CharacterCreatorScene) g.top();
        g.renderWindow(1280, 800);
        check(!c.isTypingName(), "le nom existant n'est pas ressaisi");
        tap(g, Button.RIGHT);                     // genre (ligne 0)
        check(!c.look().equals(PlayerLook.defaults()), "aperçu modifié");
        click(g, c.anchor("close"));              // ✕ : annuler
        check(g.top() == w, "retour au monde");
        eq(PlayerLook.defaults(), g.save.look, "l'annulation ne change pas la sauvegarde");

        g.push(new CharacterCreatorScene(g, true));
        CharacterCreatorScene c2 = (CharacterCreatorScene) g.top();
        tap(g, Button.RIGHT);
        PlayerLook edited = c2.look();
        click(g, c2.anchor("create"));            // « Enregistrer »
        check(g.top() == w, "validation → retour au monde");
        eq(edited, g.save.look, "la modification est sauvegardée");
    }

    private static void characterPersists() {
        Game g = freshGame();
        g.save.name = "Rayan";
        g.save.look = new PlayerLook(1, 0xE0AC80, 0x9B5DE5, 0x111111, 0xF15BB5, PlayerLook.ORIGIN, 0x3A86FF, 0xFFD23F);
        g.save.store();
        Game again = new Game(data());
        check(again.save.hasCharacter(), "personnage relu");
        eq("Rayan", again.save.name, "nom relu");
        eq(g.save.look, again.save.look, "apparence relue");
        again.push(CharacterCreatorScene.firstScene(again));
        check(again.top() instanceof OverworldScene, "pas de nouvelle création si le personnage existe");
    }

    // ------------------------------------------------------------------ choix des ROM

    private static void romCheck(Path home) throws Exception {
        Path dir = Files.createDirectories(home.resolve("roms"));
        Path good = SyntheticRom.writeFile(dir.resolve("rouge feu.gba"), SyntheticRom.build());

        RomCheck.Result ok = RomCheck.inspect(good, RomCheck.Slot.KANTO);
        check(ok.ok(), "ROM valide : " + ok.message());
        eq("Rouge Feu (anglais) · 1 Mo", ok.message(), "message affiché");
        check(RomCheck.inspect(good, null).ok(), "sans emplacement imposé");

        RomCheck.Result wrong = RomCheck.inspect(good, RomCheck.Slot.HOENN);
        check(!wrong.ok() && wrong.message().contains("Rouge Feu / Vert Feuille"), "mauvaise ligne : " + wrong.message());

        byte[] broken = SyntheticRom.build();
        broken[0xBD]++;
        RomCheck.Result bad = RomCheck.inspect(SyntheticRom.writeFile(dir.resolve("abimee.gba"), broken), null);
        check(!bad.ok() && bad.message().contains("en-tête"), "somme de contrôle fausse : " + bad.message());

        byte[] other = SyntheticRom.build();
        other[0xAC] = 'A';
        other[0xAD] = 'X';
        other[0xAE] = 'V';
        SyntheticRom.fixHeader(other);
        RomCheck.Result ruby = RomCheck.inspect(SyntheticRom.writeFile(dir.resolve("rubis.gba"), other), null);
        check(!ruby.ok() && ruby.message().contains("non pris en charge"), "jeu non pris en charge : " + ruby.message());

        Path tiny = Files.write(dir.resolve("mini.gba"), new byte[0x1000]);
        check(!RomCheck.inspect(tiny, null).ok(), "fichier minuscule refusé");
        check(RomCheck.inspect(dir.resolve("absente.gba"), null).message().contains("introuvable"), "fichier absent");
        eq(RomCheck.Status.EMPTY, RomCheck.inspect(null, null).status(), "aucune ROM");

        GameData d = new GameData(Rom.load(good));
        eq("BPRE", d.rom.gameCode(), "le fichier vérifié se charge vraiment");
    }

    private static void romSlots(Path home) throws Exception {
        Files.deleteIfExists(Storage.DIR.resolve("config.properties"));
        Path good = SyntheticRom.writeFile(Files.createDirectories(home.resolve("roms2")).resolve("rf.gba"),
                SyntheticRom.build());
        check(RomLauncher.remembered() == null, "rien de mémorisé : la fenêtre doit s'ouvrir");

        // Ancien réglage (une seule ROM) : repris sur la ligne Kanto
        Files.writeString(Storage.DIR.resolve("config.properties"), "rom.path=" + good.toString().replace("\\", "\\\\") + "\n");
        eq(good.toString(), Storage.romPath(RomCheck.Slot.KANTO), "ancien rom.path repris");
        RomLauncher.Choice c = RomLauncher.remembered();
        check(c != null && c.slot() == RomCheck.Slot.KANTO, "relance directe de la ROM mémorisée");

        Storage.setRomPath(RomCheck.Slot.HOENN, home.resolve("absente.gba").toString());
        Storage.setLastSlot(RomCheck.Slot.HOENN);
        c = RomLauncher.remembered();
        check(c != null && c.slot() == RomCheck.Slot.KANTO, "dernier jeu introuvable → l'autre ROM valide");

        Storage.setRomPath(RomCheck.Slot.KANTO, null);
        check(Storage.romPath(RomCheck.Slot.KANTO) == null, "ligne vidée");
        check(RomLauncher.remembered() == null, "plus aucune ROM valide : la fenêtre doit s'ouvrir");
    }

    private static void nativeTitleBar() throws Throwable {
        check(isWindows() || !dev.orvaen.mmo.engine.WindowsTheme.darkTitleBar(null),
                "hors Windows : aucun appel, renvoie faux");
        if (System.getProperty("os.name", "").toLowerCase().contains("linux")) {
            eq(ProcessHandle.current().pid(), dev.orvaen.mmo.engine.WindowsTheme.nativeProbe("libc.so.6"),
                    "le mécanisme d'appel natif (réflexion + java.lang.foreign) fonctionne");
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows");
    }

    private static void titleScreen() {
        Game g = freshGame();
        dev.orvaen.mmo.engine.Backdrop b = g.backdrop();
        check(!b.hasLegends(), "ROM de test : pas de légendaires (emplacements vides) → pas d'image cassée");
        BufferedImage img = g.renderWindow(1280, 800);
        check(img != null, "rendu possible");

        dev.orvaen.mmo.scenes.TitleScene t = new dev.orvaen.mmo.scenes.TitleScene(g);
        g.push(t);
        BufferedImage frame = g.renderWindow(1280, 800);
        check(frame.getRGB(640, 30) != frame.getRGB(5, 5) || frame.getRGB(640, 30) != 0xFF000000, "fond dessiné");
        click(g, t.anchor("PLAY"));
        for (int i = 0; i < 30; i++) g.simulateFrame();
        check(g.top() instanceof CharacterCreatorScene, "sans personnage : Jouer → création");

        Game g2 = freshGame();
        g2.save.name = "Rayan";
        g2.save.look = PlayerLook.defaults();
        dev.orvaen.mmo.scenes.TitleScene t2 = new dev.orvaen.mmo.scenes.TitleScene(g2);
        g2.push(t2);
        g2.renderWindow(1280, 800);
        click(g2, t2.anchor("CHARACTER"));
        check(g2.top() instanceof CharacterCreatorScene, "Modifier le personnage");
        tap(g2, Button.B);
        check(g2.top() == t2, "retour à l'accueil");
        click(g2, t2.anchor("PLAY"));
        for (int i = 0; i < 30; i++) g2.simulateFrame();
        check(g2.top() instanceof OverworldScene, "avec personnage : Jouer → le monde");
    }

    private static void exportTo(Path dir) throws Exception {
        new Extractor(data()).exportAll(dir);
        check(Files.exists(dir.resolve("especes.csv")), "CSV");
        check(Files.exists(dir.resolve("sprites/001_TESTMON.png")), "sprite PNG");
        check(Files.exists(dir.resolve("maps/00.000_BOURG_TEST.png")), "carte PNG");
        String csv = Files.readString(dir.resolve("especes.csv"));
        check(csv.contains("1;TESTMON;Feu;Plante;45"), "ligne CSV de TESTMON");
        check(Files.exists(dir.resolve("sprites/002_Bebe-Eclair.png")), "nom de fichier sans accents");
        check(Files.exists(dir.resolve("joueur/fille_marche.png")), "sprites du joueur exportés");
        check(Files.exists(dir.resolve("joueur/garcon_dos.png")), "vue de dos exportée");
    }
}
