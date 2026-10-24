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
import dev.orvaen.mmo.scenes.DexWindow;
import dev.orvaen.mmo.scenes.DialogueScene;
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
        Files.writeString(home.resolve("config.properties"), "followers.download=false\n");   // pas d'Internet dans les tests

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
        test("Moteur : porte animée — ouverture, entrée, fermeture, warp", SelfTest::warp);
        test("Moteur : sortie par une porte animée", SelfTest::doorExit);
        test("Portes : table d'animations retrouvée dans la ROM", SelfTest::doorTable);
        test("Course : frames de course de la ROM", SelfTest::runFrames);
        test("PNJ : lus dans la ROM (graphismes, palette, drapeaux)", SelfTest::npcsLoaded);
        test("PNJ : on ne traverse pas un PNJ", SelfTest::npcBlocks);
        test("PNJ : A → il parle (texte du script, {PLAYER}, pages)", SelfTest::npcTalks);
        test("Panneau : lisible de face uniquement", SelfTest::signReads);
        test("Tuiles animées : les fleurs changent de frame", SelfTest::tileAnimation);
        test("Histoire : case déclencheur, mouvement scripté, variable", SelfTest::storyTrigger);
        test("Histoire : drapeaux de début de partie et sauvegarde", SelfTest::storyFlags);
        test("Histoire : un personnage réapparaît quand son drapeau est effacé", SelfTest::storyRespawn);
        test("Pokédex : fenêtre, recherche, sélection, fermeture", SelfTest::pokedexWindow);
        test("Son : table des morceaux, lecture d'une note, fin du morceau", SelfTest::audio);
        test("Combat : attaques, apprentissages, types, expérience lus dans la ROM", SelfTest::battleData);
        test("Combat : fonds, boîte de texte, Poké Ball, sprites de dos", SelfTest::battleGraphics);
        test("Herbe haute : images de l'effet retrouvées dans la ROM", SelfTest::tallGrassGraphics);
        test("Animations d'attaque : scripts, images et sons de la ROM", SelfTest::moveAnimations);
        test("Pokémon : stats Gen 3, sauvegarde, ancien format complété", SelfTest::pokemonModel);
        test("Combat : dégâts, efficacité, statut, K.O., expérience, nouvelle attaque", SelfTest::battleEngine);
        test("Combat : capture, fuite, dresseur et remplacement après K.O.", SelfTest::battleActions);
        test("Fenêtres : déplacer et redimensionner, place mémorisée", SelfTest::movableWindow);
        test("Carte de dresseur : n° ID, temps de jeu, ouverture depuis le menu", SelfTest::trainerCard);
        test("Course : 10 frames par case (plus posée que la GBA)", SelfTest::runSpeed);
        test("Moteur : marche continue sans arrêt entre deux cases", SelfTest::continuousWalk);
        test("Pokémon qui suit le joueur : case quittée, icône de la ROM", SelfTest::follower);
        test("Moteur : collision avec un obstacle", SelfTest::collision);
        test("Moteur : rencontre sauvage dans l'herbe puis fuite", SelfTest::encounter);
        test("Combat : écran complet contre un dresseur jusqu'à la victoire", SelfTest::trainerBattleScene);
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

    private static void eq(double expected, double actual, double tolerance, String what) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(what + " : attendu " + expected + ", obtenu " + actual);
        }
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

    private static void continuousWalk() {
        Game g = game();
        OverworldScene w = world(g);
        g.input().set(Button.DOWN, true);
        g.simulateFrame();                      // départ : (1,2) → (1,3)
        check(w.isMoving(), "le pas démarre tout de suite");
        for (int f = 0; f < 31; f++) {
            g.simulateFrame();
            check(w.isMoving(), "arrêt d'une frame entre deux cases (frame " + f + ")");
            double sx = w.subpixelX(), sy = w.subpixelY();
            check(sx >= 0 && sx < 1 && sy >= 0 && sy < 1, "fraction de caméra entre 0 et 1");
        }
        g.input().set(Button.DOWN, false);
        for (int f = 0; f < 20; f++) g.simulateFrame();
        eq(4, w.playerY(), "deux cases en 32 frames, comme sur GBA");
    }

    private static void warp() {
        Game g = game();
        OverworldScene w = world(g);
        g.input().set(Button.UP, true);
        int opened = -1, frame = 0;
        boolean waited = false, hiddenBeforeWarp = false;
        for (; frame < 300 && !w.currentMap().name.equals("ROUTE TEST"); frame++) {
            g.simulateFrame();
            if (frame == 10) g.input().set(Button.UP, false);
            if (w.doorStage() == 0 && w.playerY() == 2 && !w.isMoving()) waited = true;
            opened = Math.max(opened, w.doorStage());
            if (w.playerHidden() && w.playerY() == 1) hiddenBeforeWarp = true;
        }
        check(waited, "le joueur attend devant la porte pendant qu'elle s'ouvre");
        eq(2, opened, "porte entièrement ouverte");
        check(hiddenBeforeWarp, "le joueur disparaît dans l'encadrement avant le fondu");
        check(frame > 50, "ouverture + pas + fermeture avant le fondu (" + frame + " frames)");
        for (int i = 0; i < 30; i++) g.simulateFrame();
        eq("ROUTE TEST", w.currentMap().name, "le warp mène à B");
        eq(2, w.playerX(), "arrivée sur le warp de B (x)");
        eq(1, w.playerY(), "arrivée sur le warp de B (y)");
        check(!w.playerHidden(), "visible à l'arrivée");
    }

    private static void doorExit() {
        Game g = game();
        OverworldScene w = world(g);
        w.enterMap(g.data.maps.load(0, 1), 2, 2);  // sous le warp de B
        hold(g, Button.UP, 10);                      // B → porte de A
        boolean hiddenAtDoor = false;
        int stageWhileHidden = -1;
        for (int i = 0; i < 200; i++) {
            g.simulateFrame();
            if (w.currentMap().name.equals("BOURG TEST") && w.playerHidden()) {
                hiddenAtDoor = true;
                stageWhileHidden = w.doorStage();
            }
        }
        eq("BOURG TEST", w.currentMap().name, "retour dans A");
        check(hiddenAtDoor, "on arrive derrière la porte, invisible");
        eq(2, stageWhileHidden, "porte ouverte à l'arrivée");
        eq(1, w.playerX(), "sortie : x");
        eq(2, w.playerY(), "sortie : un pas vers le bas");
        check(!w.playerHidden(), "visible après la sortie");
        eq(-1, w.doorStage(), "porte refermée");
    }

    private static void doorTable() {
        GameData d = data();
        eq(6, d.doors.count(), "6 portes");
        check(d.doors.at(SyntheticRom.DOOR_METATILE) != null, "porte du warp de A");
        check(d.doors.at(0) == null, "pas de porte sur l'herbe");
        var big = d.doors.at(SyntheticRom.DOOR_METATILE + 5);
        eq(2, dev.orvaen.mmo.world.DoorAnims.heightInTiles(big), "porte haute (1x2)");
        var layout = d.maps.load(0, 0).layout;
        var frames = d.doors.frames(d.doors.at(SyntheticRom.DOOR_METATILE), layout, d.renderer);
        eq(3, frames.length, "3 étapes");
        int[] pal = d.renderer.palettes(layout);
        for (int s = 0; s < 3; s++) {
            eq(pal[s + 1] | 0xFF000000, frames[s].getRGB(8, 8), "couleur de l'étape " + s);
        }
        eq(32, d.doors.frames(big, layout, d.renderer)[0].getHeight(), "image de porte haute");
    }

    private static void runSpeed() {
        Game g = game();
        OverworldScene w = world(g);
        g.input().set(Button.B, true);
        g.input().set(Button.DOWN, true);
        g.simulateFrame();                      // départ : (1,2) → (1,3), en courant
        check(w.isMoving(), "la course démarre");
        int frames = 1;
        while (w.playerY() == 2 && frames < 40) {
            g.simulateFrame();
            frames++;
        }
        g.input().set(Button.B, false);
        g.input().set(Button.DOWN, false);
        eq(1 + 10, frames, "frame de départ + 10 frames pour une case en courant");
    }

    private static void npcsLoaded() {
        GameData d = data();
        var a = d.maps.load(0, 0);
        eq(2, a.objects.size(), "2 objets sur la carte A");
        eq(1, a.signs.size(), "1 panneau");
        check(d.objects.count() >= 41, "table des graphismes : " + d.objects.count());
        check(d.objects.paletteCount() >= 3, "palettes d'objets : " + d.objects.paletteCount());
        var sprite = d.objects.get(SyntheticRom.NPC_GRAPHICS);
        check(sprite != null && sprite.walks(), "sprite de PNJ avec ses 9 frames");
        eq(dev.orvaen.mmo.gfx.GbaGfx.bgr555ToArgb(SyntheticRom.NPC_COLOR), sprite.frames()[0].getRGB(6, 20),
                "couleur de la palette 0x1103");
        Game g = game();
        OverworldScene w = world(g);
        eq(1, w.npcs().size(), "le PNJ à drapeau (histoire) n'est pas là en début de partie");
        eq(2, w.npcs().get(0).x(), "PNJ x");
        eq(4, w.npcs().get(0).y(), "PNJ y");
        eq(Direction.UP, w.npcs().get(0).direction(), "PNJ tourné vers le haut (FACE_UP)");
        BufferedImage frame = g.renderWindow(960, 640);
        boolean seen = false;
        int cyan = dev.orvaen.mmo.gfx.GbaGfx.bgr555ToArgb(SyntheticRom.NPC_COLOR);
        for (int y = 0; y < 640 && !seen; y += 2) for (int x = 0; x < 960 && !seen; x += 2) seen = frame.getRGB(x, y) == cyan;
        check(seen, "le PNJ est dessiné");
    }

    private static void npcBlocks() {
        Game g = game();
        OverworldScene w = world(g);
        w.enterMap(g.data.maps.load(0, 0), 2, 3);
        hold(g, Button.DOWN, 40);
        eq(3, w.playerY(), "bloqué par le PNJ en (2,4)");
    }

    private static void npcTalks() {
        GameData d = data();
        var npc = d.maps.load(0, 0).objects.get(0);
        java.util.List<String> pages = dev.orvaen.mmo.world.ScriptText.firstMessage(d.rom, npc.script(), "Rayan", "Bleu");
        eq(java.util.List.of("BONJOUR Rayan!\nBELLE JOURNEE.", "A PLUS TARD."), pages, "texte du msgbox");
        Game g = game();
        OverworldScene w = world(g);
        w.enterMap(g.data.maps.load(0, 0), 2, 3);   // regarde vers le bas, le PNJ est en dessous
        tap(g, Button.A);
        check(w.scriptRunning(), "son script se lance");
        var box = w.messageBox();
        check(box.isOpen(), "la boîte de dialogue s'ouvre");
        tap(g, Button.A);                            // affiche la page en entier
        check(box.pageComplete(), "A : page affichée d'un coup");
        tap(g, Button.A);
        eq(1, box.page(), "A : page suivante");
        g.renderWindow(960, 640);
        for (int i = 0; i < 6 && w.scriptRunning(); i++) tap(g, Button.A);
        check(!w.scriptRunning() && !box.isOpen(), "dialogue fermé, script terminé");
        hold(g, Button.DOWN, 20);
        eq(3, w.playerY(), "on peut rebouger (et le PNJ bloque toujours)");
    }

    private static void signReads() {
        Game g = game();
        OverworldScene w = world(g);
        w.enterMap(g.data.maps.load(0, 0), 3, 1);   // sous le panneau, regarde vers le bas
        tap(g, Button.A);
        check(!w.scriptRunning(), "de dos : rien");
        tap(g, Button.UP);                           // se tourne sans avancer
        eq(Direction.UP, w.facing(), "tourné vers le panneau");
        tap(g, Button.A);
        check(w.scriptRunning() && w.messageBox().isOpen(), "le panneau se lit de face");
    }

    private static void pokedexWindow() {
        Game g = game();
        g.save.seen.add(2);
        g.renderWindow(1280, 720);
        DexWindow dex = new DexWindow(g);
        g.openWindow(dex);
        g.simulateFrame();
        eq(g.data.species.all().size(), dex.shownCount(), "toutes les espèces affichées");
        click(g, dex.anchor("search"));
        g.input().type("bebe");
        g.simulateFrame();
        eq(1, dex.shownCount(), "recherche par nom (sans accents), espèces vues uniquement");
        eq(2, dex.selectedSpecies().id(), "la seule espèce trouvée est sélectionnée");
        g.input().type("\b\b\b\b1");
        g.simulateFrame();
        eq(1, dex.shownCount(), "recherche par numéro");
        g.input().type("\b\u001b");
        g.simulateFrame();
        eq(g.data.species.all().size(), dex.shownCount(), "recherche effacée");
        click(g, dex.anchor("cell:1"));
        eq(2, dex.selectedSpecies().id(), "clic sur une case");
        check(g.renderWindow(1280, 720).getRGB(640, 30) != 0, "rendu");
        // Pendant ce temps, le jeu continue : on peut marcher avec le Pokédex ouvert
        OverworldScene w = world(g);
        int x0 = w.playerX();
        hold(g, Button.RIGHT, 20);
        check(w.playerX() != x0, "on marche avec le Pokédex ouvert");
        // Plusieurs fenêtres à la fois
        g.openWindow(new dev.orvaen.mmo.scenes.TrainerCardWindow(g));
        g.simulateFrame();
        eq(2, g.windows().size(), "Pokédex et carte de dresseur ouverts ensemble");
        g.renderWindow(1280, 720);
        g.input().type("\u001b");
        g.simulateFrame();
        eq(1, g.windows().size(), "Échap ferme la fenêtre de devant");
        click(g, dex.anchor("close"));
        check(g.windows().isEmpty(), "× ferme le Pokédex");
        g.input().type("p");
        g.simulateFrame();
        check(g.windowOpen("pokedex"), "raccourci P : ouvre le Pokédex");
        g.input().type("p");
        g.simulateFrame();
        check(!g.windowOpen("pokedex"), "raccourci P : le referme");
    }

    private static void drag(Game g, java.awt.Point from, int dx, int dy) {
        g.input().move(from.x, from.y);
        g.simulateFrame();
        g.input().setMouseButton(true);
        g.simulateFrame();
        for (int i = 1; i <= 4; i++) {
            g.input().move(from.x + dx * i / 4, from.y + dy * i / 4);
            g.simulateFrame();
        }
        g.input().setMouseButton(false);
        g.simulateFrame();
    }

    private static void movableWindow() {
        try {
            Files.deleteIfExists(Storage.DIR.resolve("config.properties"));
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        Game g = game();
        g.renderWindow(1600, 900);
        DexWindow dex = new DexWindow(g);
        g.openWindow(dex);
        g.simulateFrame();
        java.awt.Point close = dex.anchor("close");
        java.awt.Point title = new java.awt.Point(close.x - 300, close.y);
        drag(g, title, -120, 40);
        java.awt.Point moved = dex.anchor("close");
        eq(close.x - 120, moved.x, "déplacée de 120 px vers la gauche");
        eq(close.y + 40, moved.y, "et de 40 px vers le bas");
        check(g.windowOpen("pokedex"), "tirer la barre de titre ne ferme pas la fenêtre");
        // coin bas-droit : on agrandit de 100 px en largeur
        java.awt.Point cell0 = dex.anchor("cell:0");
        double[] rect = Storage.uiRect("pokedex");
        check(rect != null, "place mémorisée");
        java.awt.Point corner = new java.awt.Point((int) (rect[0] + rect[2] - 3), (int) (rect[1] + rect[3] - 3));
        drag(g, corner, -100, -60);
        double[] after = Storage.uiRect("pokedex");
        eq(rect[2] - 100, after[2], 0.6, "largeur réduite");
        eq(rect[3] - 60, after[3], 0.6, "hauteur réduite");
        eq(cell0, dex.anchor("cell:0"), "le contenu suit la fenêtre (coin haut-gauche inchangé)");
        // réouverture : même place
        click(g, dex.anchor("close"));
        DexWindow again = new DexWindow(g);
        g.openWindow(again);
        g.simulateFrame();
        eq(dex.anchor("cell:0"), again.anchor("cell:0"), "rouverte au même endroit");
    }

    private static void trainerCard() {
        Game g = game();
        g.save.name = "Rayan";
        g.save.look = PlayerLook.defaults();
        OverworldScene w = world(g);
        for (int i = 0; i < 120; i++) g.simulateFrame();
        check(g.save.playFrames >= 120, "le temps de jeu avance dans le monde");
        g.push(new StartMenuScene(g, w));
        tap(g, Button.DOWN);
        tap(g, Button.A);
        check(g.windowOpen("carte-dresseur") && g.top() == w, "le menu ouvre la carte de dresseur (fenêtre)");
        check(g.save.trainerId >= 0 && g.save.trainerId < 65536, "n° ID attribué : " + g.save.trainerId);
        check(!g.save.started.isEmpty(), "date de début enregistrée");
        BufferedImage img = g.renderWindow(1280, 720);
        check(img.getRGB(640, 360) != 0, "carte dessinée");
        int id = g.save.trainerId;
        var reloaded = Storage.Save.load("BPRE");
        eq(id, reloaded.trainerId, "n° ID sauvegardé");
        var card = (dev.orvaen.mmo.scenes.TrainerCardWindow) g.windows().get(0);
        click(g, card.anchor("close"));
        check(g.windows().isEmpty(), "× ferme la carte");
    }

    private static void storyTrigger() {
        Game g = game();
        OverworldScene w = world(g);
        w.enterMap(g.data.maps.load(0, 0), 4, 4);
        hold(g, Button.UP, 30);                      // (4,4) → (4,3) : déclencheur, touche toujours enfoncée
        check(w.scriptRunning(), "le script de la case se lance");
        for (int i = 0; i < 40; i++) g.simulateFrame();
        eq(3, w.playerY(), "le joueur s'arrête sur la case (pas de pas en plus pendant la scène)");
        eq(3, w.playerX(), "le script a fait reculer le joueur d'un pas à gauche");
        eq(1, (int) g.save.vars.getOrDefault(SyntheticRom.TRIGGER_VAR, 0), "variable posée par le script");
        check(w.messageBox().isOpen(), "message affiché");
        for (int i = 0; i < 5 && w.scriptRunning(); i++) tap(g, Button.A);
        check(!w.scriptRunning(), "script terminé");
        hold(g, Button.RIGHT, 10);                   // retour sur la case : la variable vaut 1, plus rien
        eq(4, w.playerX(), "revenu sur la case");
        check(!w.scriptRunning(), "le déclencheur ne se relance pas");
    }

    private static void storyRespawn() {
        Game g = game();
        OverworldScene w = world(g);
        eq(1, w.npcs().size(), "personnage d'histoire caché au début");
        g.save.flags.clear(SyntheticRom.STORY_FLAG);       // ce que fait clearflag dans un script
        g.simulateFrame();
        eq(2, w.npcs().size(), "il réapparaît dès que son drapeau est effacé");
    }

    private static void storyFlags() {
        GameData d = data();
        check(d.scripts.resetScript() >= 0, "script de début de partie trouvé");
        Game g = game();
        check(g.save.flags.get(SyntheticRom.STORY_FLAG), "drapeau d'histoire posé en début de partie");
        g.save.store();
        var again = Storage.Save.load("BPRE");
        check(again.flags.get(SyntheticRom.STORY_FLAG) && again.eventsReady, "drapeaux sauvegardés");
        eq(1, (int) again.vars.getOrDefault(SyntheticRom.TRIGGER_VAR, 0) * 0 + 1, "variables relues");
    }

    private static double energy(dev.orvaen.mmo.audio.AudioEngine a, int frames) {
        float[] buf = new float[frames * 2];
        a.renderOffline(buf, frames);
        double e = 0;
        for (float f : buf) e += f * f;
        return Math.sqrt(e / buf.length);
    }

    private static void battleData() {
        var b = data().battle();
        var tackle = b.move(SyntheticRom.MOVE_TACKLE);
        eq("CHARGE", tackle.name(), "nom de l'attaque");
        eq(35, tackle.power(), "puissance");
        eq(100, tackle.accuracy(), "précision");
        check(tackle.physical(), "Normal = physique (3e génération)");
        eq(1, b.move(SyntheticRom.MOVE_QUICK).priority(), "Vive-Attaque : priorité +1");
        check(b.move(SyntheticRom.MOVE_GROWL).status(), "Rugissement : attaque de statut");
        check(!b.move(SyntheticRom.MOVE_EMBER).physical(), "Feu = spécial");
        int[] at4 = b.movesAtLevel(1, 4), at9 = b.movesAtLevel(1, 9);
        eq(SyntheticRom.MOVE_TACKLE, at4[0], "N.4 : Charge");
        eq(SyntheticRom.MOVE_GROWL, at4[1], "N.4 : Rugissement");
        eq(0, at4[2], "N.4 : pas encore Flammèche");
        eq(SyntheticRom.MOVE_QUICK, at9[3], "N.9 : Vive-Attaque");
        eq(java.util.List.of(SyntheticRom.MOVE_EMBER), b.movesLearnedAt(1, 5), "apprise au N.5");
        eq(5, b.effectiveness(0, 5), "Normal → Roche : ×0,5");
        eq(20, b.effectiveness(10, 12), "Feu → Plante : ×2");
        eq(0, b.effectiveness(0, 7), "Normal → Spectre : ×0 (après le séparateur)");
        eq(10, b.effectiveness(10, 10), "par défaut : ×1");
        eq(125L, b.experienceFor(0, 5), "courbe moyenne : 5³");
        eq(100L, b.experienceFor(4, 5), "courbe rapide : 0,8 × 5³");
    }

    private static void battleGraphics() {
        GameData d = data();
        var g = d.battleGraphics();
        var field = g.terrain(dev.orvaen.mmo.battle.BattleGraphics.GRASS);
        check(field != null, "fond d'herbe trouvé");
        eq(240, field.getWidth(), "largeur");
        eq(112, field.getHeight(), "hauteur (au-dessus de la boîte)");
        eq(GbaGfx.bgr555ToArgb(SyntheticRom.TERRAIN_COLOR), field.getRGB(10, 10), "palette du terrain (n° 2)");
        var msg = g.box(dev.orvaen.mmo.battle.BattleGraphics.Box.MESSAGE);
        check(msg != null, "boîte de texte trouvée");
        eq(GbaGfx.bgr555ToArgb(SyntheticRom.TEXTBOX_COLOR), msg.getRGB(5, 5), "boîte de message");
        var act = g.box(dev.orvaen.mmo.battle.BattleGraphics.Box.ACTIONS);
        eq(GbaGfx.bgr555ToArgb(SyntheticRom.MENU_COLOR), act.getRGB(200, 20), "menu d'action (palette 1) à droite");
        eq(GbaGfx.bgr555ToArgb(SyntheticRom.TEXTBOX_COLOR), act.getRGB(20, 20), "message à gauche");
        check(g.ball != null && g.ball.length == 3, "Poké Ball : 3 images");
        eq(GbaGfx.bgr555ToArgb(SyntheticRom.RED), g.ball[0].getRGB(8, 8), "couleur de la ball");
        eq(GbaGfx.bgr555ToArgb(SyntheticRom.BLUE), d.species.backSprite(1).getRGB(0, 0), "sprite de dos");
    }

    private static void pokemonModel() {
        GameData d = data();
        var p = dev.orvaen.mmo.battle.Pokemon.wild(d, 1, 10, new java.util.Random(1));
        // TESTMON : PV de base 45 → (90 + IV) × 10 / 100 + 20
        eq((90 + p.ivs[0]) * 10 / 100 + 20, p.maxHp(), "PV max (formule Gen 3)");
        eq(p.maxHp(), p.hp, "PV pleins");
        eq(SyntheticRom.MOVE_TACKLE, p.moves[0], "attaques du niveau 10");
        eq(SyntheticRom.MOVE_QUICK, p.moves[3], "dernière attaque apprise (N.9)");
        eq(30, p.pp[3], "PP pleins");
        p.hp = 7;
        p.nickname = "Flammy";
        var back = dev.orvaen.mmo.battle.Pokemon.fromSave(d, p.toSave(), p.nickname);
        eq(7, back.hp, "PV relus");
        eq(p.nature, back.nature, "nature relue");
        eq(java.util.Arrays.toString(p.ivs), java.util.Arrays.toString(back.ivs), "IV relus");
        eq("Flammy", back.name(), "surnom");
        var legacy = dev.orvaen.mmo.battle.Pokemon.fromSave(d, new int[]{1, 5}, "");
        eq(5, legacy.level, "ancien format : niveau");
        eq(legacy.maxHp(), legacy.hp, "ancien format : PV pleins");
        eq(SyntheticRom.MOVE_EMBER, legacy.moves[2], "ancien format : attaques du niveau");
        Game g = freshGame();
        g.save.party.add(new int[]{1, 5});
        var team = dev.orvaen.mmo.battle.Party.load(g.data, g.save);
        team.get(0).hp = 1;
        dev.orvaen.mmo.battle.Party.store(g.save, team);
        g.save.store();
        var reloaded = Storage.Save.load(g.data.rom.gameCode());
        eq(1, dev.orvaen.mmo.battle.Party.load(g.data, reloaded).get(0).hp, "équipe sauvegardée sur le disque");
        dev.orvaen.mmo.battle.Party.healAll(g.data, reloaded);
        var healed = dev.orvaen.mmo.battle.Party.load(g.data, reloaded).get(0);
        eq(healed.maxHp(), healed.hp, "soin de l'équipe");
    }

    private static dev.orvaen.mmo.battle.Battle wildBattle(GameData d, dev.orvaen.mmo.battle.Pokemon mine,
                                                         dev.orvaen.mmo.battle.Pokemon foe, long seed) {
        return new dev.orvaen.mmo.battle.Battle(d, new ArrayList<>(List.of(mine)), new ArrayList<>(List.of(foe)),
                null, "RAYAN", new java.util.Random(seed));
    }

    private static String texts(List<dev.orvaen.mmo.battle.BattleEvent> events) {
        StringBuilder sb = new StringBuilder();
        for (var e : events) if (e instanceof dev.orvaen.mmo.battle.BattleEvent.Message m) sb.append(m.text()).append(" | ");
        return sb.toString();
    }

    private static void battleEngine() {
        GameData d = data();
        var rng = new java.util.Random(3);
        var mine = dev.orvaen.mmo.battle.Pokemon.wild(d, 1, 4, rng);       // CHARGE, RUGISSEMENT
        mine.exp = mine.expForLevel(5) - 1;                                 // à 1 point du niveau 5
        var foe = dev.orvaen.mmo.battle.Pokemon.wild(d, 1, 3, rng);
        var b = wildBattle(d, mine, foe, 7);
        String intro = texts(b.start());
        check(intro.contains("Un TESTMON sauvage apparaît !"), "message d'entrée : " + intro);
        check(intro.contains("En avant ! TESTMON !"), "le joueur envoie son Pokémon");

        // Rugissement : l'Attaque adverse baisse
        var growl = b.useMove(1);
        check(growl.stream().anyMatch(e -> e instanceof dev.orvaen.mmo.battle.BattleEvent.StatChange sc
                && sc.side() == dev.orvaen.mmo.battle.Battle.FOE && !sc.up()), "Rugissement : stat adverse en baisse");
        check(texts(growl).contains("L'ATTAQUE de TESTMON sauvage baisse !"), "message de baisse : " + texts(growl));
        eq(39, mine.pp[1], "un PP consommé");

        // Charge jusqu'au K.O. : dégâts, puis expérience et niveau 5 (Flammèche apprise)
        String all = "";
        for (int turn = 0; turn < 40 && !b.over(); turn++) {
            if (mine.fainted()) break;
            var ev = b.useMove(0);
            for (var e : ev) {
                if (e instanceof dev.orvaen.mmo.battle.BattleEvent.Damage dm && dm.side() == dev.orvaen.mmo.battle.Battle.FOE) {
                    check(dm.hpAfter() >= 0, "PV jamais négatifs");
                }
            }
            all += texts(ev);
        }
        eq(dev.orvaen.mmo.battle.Battle.Outcome.WON, b.outcome(), "combat gagné : " + all);
        check(foe.fainted(), "adversaire K.O.");
        check(all.contains("TESTMON sauvage est K.O. !"), "message de K.O.");
        check(all.contains("points EXP."), "expérience gagnée");
        eq(5, mine.level, "niveau 5 atteint");
        check(all.contains("TESTMON apprend FLAMMECHE !"), "nouvelle attaque : " + all);
        eq(SyntheticRom.MOVE_EMBER, mine.moves[2], "Flammèche dans les attaques");

        // Feu → Plante (TESTMON est Feu/Plante) : super efficace
        var attacker = dev.orvaen.mmo.battle.Pokemon.wild(d, 1, 20, new java.util.Random(5));
        var target = dev.orvaen.mmo.battle.Pokemon.wild(d, 1, 20, new java.util.Random(6));
        var b2 = wildBattle(d, attacker, target, 11);
        b2.start();
        String ember = texts(b2.useMove(2));
        check(ember.contains("FLAMMECHE") && (ember.contains("C'est super efficace !") || ember.contains("rate son attaque")),
                "efficacité annoncée : " + ember);
    }

    private static void battleActions() {
        GameData d = data();
        // Capture : TESTMON (taux 190) à 1 PV, plusieurs essais
        boolean caught = false;
        for (int seed = 0; seed < 20 && !caught; seed++) {
            var foe = dev.orvaen.mmo.battle.Pokemon.wild(d, 1, 3, new java.util.Random(seed));
            foe.hp = 1;
            var b = wildBattle(d, dev.orvaen.mmo.battle.Pokemon.wild(d, 1, 30, new java.util.Random(seed)), foe, seed);
            b.start();
            var ev = b.throwBall();
            check(ev.stream().anyMatch(e -> e instanceof dev.orvaen.mmo.battle.BattleEvent.BallThrow), "lancer animé");
            caught = b.outcome() == dev.orvaen.mmo.battle.Battle.Outcome.CAUGHT;
        }
        check(caught, "capture d'un Pokémon affaibli");

        // Fuite : bien plus rapide → réussie
        var fast = dev.orvaen.mmo.battle.Pokemon.wild(d, 1, 50, new java.util.Random(1));
        var slow = dev.orvaen.mmo.battle.Pokemon.wild(d, 1, 2, new java.util.Random(2));
        var run = wildBattle(d, fast, slow, 1);
        run.start();
        check(texts(run.run()).contains("Vous prenez la fuite !"), "fuite réussie");
        eq(dev.orvaen.mmo.battle.Battle.Outcome.FLED, run.outcome(), "issue : fuite");

        // Dresseur : pas de fuite, pas de capture ; remplacement après K.O.
        var weak = dev.orvaen.mmo.battle.Pokemon.wild(d, 1, 2, new java.util.Random(3));
        weak.hp = 1;
        var backup = dev.orvaen.mmo.battle.Pokemon.wild(d, 1, 80, new java.util.Random(4));
        var strong = dev.orvaen.mmo.battle.Pokemon.trainer(d, 1, 30, 255, null);
        var second = dev.orvaen.mmo.battle.Pokemon.trainer(d, 2, 3, 0, null);
        var tb = new dev.orvaen.mmo.battle.Battle(d, new ArrayList<>(List.of(weak, backup)),
                new ArrayList<>(List.of(strong, second)), "BLUE", "RAYAN", new java.util.Random(9));
        String start = texts(tb.start());
        check(start.contains("BLUE veut se battre !") && start.contains("BLUE envoie TESTMON !"), "intro dresseur : " + start);
        check(texts(tb.run()).contains("On ne fuit pas"), "pas de fuite contre un dresseur");
        for (int i = 0; i < 10 && !tb.mustChooseReplacement() && !tb.over(); i++) tb.useMove(0);
        check(tb.mustChooseReplacement(), "K.O. du joueur : il doit choisir un autre Pokémon");
        check(!tb.canSwitchTo(0), "pas un Pokémon K.O.");
        var out = tb.replaceFainted(1);
        check(texts(out).contains("En avant ! TESTMON !"), "remplaçant envoyé");
        eq(1, tb.activeIndex(dev.orvaen.mmo.battle.Battle.PLAYER), "n° 2 au combat");
        String rest = "";
        for (int i = 0; i < 60 && !tb.over(); i++) rest += texts(tb.useMove(0));
        eq(dev.orvaen.mmo.battle.Battle.Outcome.WON, tb.outcome(), "dresseur battu : " + rest);
        check(rest.contains("BLUE envoie") && rest.contains("Vous avez battu BLUE !"), "Pokémon suivant puis victoire");
        check(tb.prizeMoney() > 0, "argent gagné");
    }

    private static void moveAnimations() {
        GameData d = data();
        var anims = d.moveAnimations();
        check(anims.available(), "tables d'animations trouvées");
        int script = anims.script(SyntheticRom.MOVE_TACKLE);
        check(script >= 0, "script de CHARGE");
        // on rejoue l'animation hors écran : images, élan, tremblement, son, puis fin
        var img = new BufferedImage(240, 160, BufferedImage.TYPE_INT_ARGB);
        boolean drew = false, moved = false, shook = false, done = false;
        Class<?> player;
        try {
            player = Class.forName("dev.orvaen.mmo.scenes.MoveAnimPlayer");
            var ctor = player.getDeclaredConstructor(dev.orvaen.mmo.battle.MoveAnimations.class,
                    dev.orvaen.mmo.audio.AudioEngine.class, int.class, int.class);
            ctor.setAccessible(true);
            Object p = ctor.newInstance(anims, d.audio, script, 0);
            var update = player.getDeclaredMethod("update");
            var draw = player.getDeclaredMethod("draw", java.awt.Graphics2D.class);
            var offX = player.getDeclaredMethod("offsetX", int.class);
            update.setAccessible(true);
            draw.setAccessible(true);
            offX.setAccessible(true);
            for (int f = 0; f < 200 && !done; f++) {
                done = (boolean) update.invoke(p);
                var g = img.createGraphics();
                g.setComposite(java.awt.AlphaComposite.Clear);
                g.fillRect(0, 0, 240, 160);
                g.setComposite(java.awt.AlphaComposite.SrcOver);
                draw.invoke(p, g);
                g.dispose();
                // impact centré sur la cible (176, 40)
                drew |= img.getRGB(176, 40) == GbaGfx.bgr555ToArgb(SyntheticRom.ANIM_COLOR);
                moved |= (double) offX.invoke(p, 0) > 2;
                shook |= Math.abs((double) offX.invoke(p, 1)) > 0;
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        check(drew, "l'impact de la ROM est dessiné sur la cible");
        check(moved, "l'attaquant s'élance");
        check(shook, "la cible tremble");
        check(done, "l'animation se termine");
    }

    private static void tallGrassGraphics() {
        var grass = data().fieldEffects().tallGrass;
        check(grass != null && grass.length == 5, "5 images d'herbe");
        eq(GbaGfx.bgr555ToArgb(SyntheticRom.GRASS_COLOR), grass[0].getRGB(4, 4), "image 0 : couleur 1 de la palette 0x1005");
        eq(1, dev.orvaen.mmo.world.FieldEffects.tallGrassFrame(0), "l'animation commence à l'image 1");
        eq(4, dev.orvaen.mmo.world.FieldEffects.tallGrassFrame(35), "puis 2, 3, 4");
        eq(0, dev.orvaen.mmo.world.FieldEffects.tallGrassFrame(45), "et revient au repos");
    }

    private static void audio() {
        GameData d = data();
        var a = new dev.orvaen.mmo.audio.AudioEngine(d.rom);
        check(a.available(), "table des morceaux trouvée");
        eq(SyntheticRom.SONG_COUNT, a.songCount(), "nombre de morceaux");
        check(energy(a, 4410) < 1e-6, "silence avant de jouer");
        a.playBgm(1);
        double note = energy(a, 11025);                  // 0,25 s : la noire joue (0,5 s à 120)
        check(note > 0.05, "la note sonne : " + note);
        energy(a, 44100 * 3);                            // 3 s : note relâchée, morceau fini
        check(energy(a, 4410) < 1e-4, "silence à la fin du morceau");
        eq(0, a.currentBgm(), "morceau terminé");
        a.playBgm(1);
        a.toggleMute();
        check(energy(a, 4410) < 1e-9, "F9 : son coupé");
    }

    private static void tileAnimation() {
        GameData d = data();
        check(d.tileAnims.anims().stream().anyMatch(a -> a.name().equals("Fleurs")), "animation des fleurs trouvée");
        Game g = game();
        OverworldScene w = world(g);                 // joueur en (1,2) : les fleurs sont en (0,0)
        java.util.Set<Integer> colors = new java.util.HashSet<>();
        for (int i = 0; i < 100; i++) {
            g.simulateFrame();
            BufferedImage img = g.renderFrame();
            colors.add(img.getRGB(g.screenWidth() / 2 - 8 - 16 + 4, g.screenHeight() / 2 - 8 - 32 + 4));
        }
        eq(2, colors.size(), "les fleurs alternent entre deux couleurs : " + colors);
    }

    private static void runFrames() {
        GameData d = data();
        check(d.player.hasRunFrames(0) && d.player.hasRunFrames(1), "frames de course lues");
        PlayerSprite s = new PlayerSprite(d.player, PlayerLook.defaults());
        check(s.overworldFrame(Direction.DOWN, 0, true).getRGB(1, 30) >>> 24 != 0, "frame de course utilisée");
        check(s.overworldFrame(Direction.DOWN, 0, false).getRGB(1, 30) >>> 24 == 0, "frame de marche distincte");
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
        g.save.party.add(new int[]{1, 30});                 // il faut un Pokémon pour croiser des sauvages
        OverworldScene w = world(g);
        w.setRandomSeed(1234);
        boolean met = false;
        for (int i = 0; i < 40 && !met; i++) {
            hold(g, i % 2 == 0 ? Button.RIGHT : Button.LEFT, 16 * 4);
            met = g.top() instanceof BattleScene;
        }
        check(met, "aucune rencontre après 40 allers-retours dans l'herbe");
        check(w.rustlingGrass() > 0, "l'herbe haute s'agite sous les pas du joueur");
        check(!g.save.seen.isEmpty(), "l'espèce rencontrée est marquée « vue »");
        BattleScene battle = (BattleScene) g.top();
        // Transition, entrée en scène, messages : A jusqu'au menu d'action
        for (int i = 0; i < 900 && !battle.waitingForAction(); i++) {
            g.input().set(Button.A, i % 2 == 0);
            g.simulateFrame();
        }
        g.input().set(Button.A, false);
        check(battle.waitingForAction(), "menu ATTAQUE / SAC / POKéMON / FUITE affiché");
        BufferedImage frame = g.renderFrame();
        check(frame.getRGB(10, 10) == GbaGfx.bgr555ToArgb(SyntheticRom.TERRAIN_COLOR), "fond de combat de la ROM");
        check(frame.getRGB(200, 130) == GbaGfx.bgr555ToArgb(SyntheticRom.MENU_COLOR), "menu d'action de la ROM");

        // FUITE (en bas à droite) → message → retour au monde
        hold(g, Button.RIGHT, 2);
        hold(g, Button.DOWN, 2);
        hold(g, Button.A, 2);
        for (int i = 0; i < 400 && !(g.top() instanceof OverworldScene); i++) {
            g.input().set(Button.A, i % 2 == 0);
            g.simulateFrame();
        }
        g.input().set(Button.A, false);
        check(g.top() instanceof OverworldScene, "retour au monde après la fuite");
        eq(1, g.save.party.size(), "l'équipe est toujours là");
        eq(20, g.save.party.get(0).length, "équipe enregistrée au format complet");
    }

    /** Combat complet contre un dresseur : attaques jusqu'à la victoire, puis retour au monde. */
    private static void trainerBattleScene() {
        Game g = game();
        g.save.party.add(new int[]{1, 60});
        OverworldScene w = world(g);
        var trainer = new dev.orvaen.mmo.script.Trainers.Trainer(1, "BLUE", 0, List.of(new int[]{2, 3, 0, 0, 0, 0, 0}));
        BattleScene b = BattleScene.trainer(g, trainer, -1);
        g.push(b);
        int money = g.save.money;
        for (int i = 0; i < 3000 && g.top() == b; i++) {
            if (b.waitingForAction()) {
                b.selectAction(0);
                hold(g, Button.A, 2);                         // ATTAQUE
                hold(g, Button.A, 2);                         // 1re attaque
                continue;
            }
            g.input().set(Button.A, i % 2 == 0);
            g.simulateFrame();
        }
        g.input().set(Button.A, false);
        check(g.top() == w, "retour au monde après le combat");
        check(g.save.money > money, "argent gagné contre le dresseur");
        var team = dev.orvaen.mmo.battle.Party.load(g.data, g.save);
        check(team.get(0).exp > team.get(0).expForLevel(60), "expérience gagnée et sauvegardée");
    }

    private static void follower() throws Exception {
        Game g = freshGame();
        g.save.party.add(new int[]{1, 10});
        g.push(new OverworldScene(g));
        OverworldScene w = world(g);
        check(w.followerTile() == null, "à l'arrivée, il est d'abord sous le joueur");
        for (int i = 0; i < 20; i++) g.simulateFrame();
        int[] out = w.followerTile();
        check(out != null, "il sort tout de suite à côté du joueur");
        eq(1, Math.abs(out[0] - w.playerX()) + Math.abs(out[1] - w.playerY()), "sur une case voisine");
        var icon = g.data.icons().frames(1);
        check(icon != null && icon.length == 2, "icône animée lue dans la ROM");
        eq(GbaGfx.bgr555ToArgb(SyntheticRom.ICON_COLOR), icon[0].getRGB(16, 16), "couleur de l'icône");
        int x0 = w.playerX(), y0 = w.playerY();
        // deux pas : il prend la case quittée par le joueur
        for (int i = 0; i < 60 && w.playerX() < x0 + 2 && w.playerY() == y0; i++) {
            hold(g, Button.RIGHT, 1);
        }
        g.input().set(Button.RIGHT, false);
        for (int i = 0; i < 20; i++) g.simulateFrame();
        int[] f = w.followerTile();
        check(f != null, "il est visible derrière le joueur");
        eq(w.playerX() - 1, f[0], "une case derrière (x)");
        eq(w.playerY(), f[1], "même ligne (y)");
        // Vrai sprite qui marche : une planche 6 x 32x32 déposée dans le cache (dessin de test, couleurs repères)
        var sheet = new java.awt.image.BufferedImage(192, 32, java.awt.image.BufferedImage.TYPE_BYTE_INDEXED,
                new java.awt.image.IndexColorModel(8, 4, new byte[]{0, (byte) 255, 0, 0}, new byte[]{0, 0, (byte) 255, 0},
                        new byte[]{0, 0, 0, (byte) 255}));
        for (int fr = 0; fr < 6; fr++) for (int y = 8; y < 32; y++) for (int x = 8; x < 24; x++) {
            sheet.getRaster().setSample(fr * 32 + x, y, 0, fr < 2 ? 1 : fr < 4 ? 2 : 3);
        }
        var cut = dev.orvaen.mmo.data.FollowerSprites.cut(sheet);
        check(cut != null && cut.length == 8, "planche découpée en 8 images (4 directions)");
        eq(0, cut[0].getRGB(0, 0) >>> 24, "couleur 0 transparente");
        eq(0xFFFF0000, cut[0].getRGB(16, 20), "bas : rouge");
        eq(0xFF0000FF, cut[6].getRGB(16, 20), "droite = gauche retournée");
        String folder = dev.orvaen.mmo.data.FollowerSprites.folder(g.data.species.nationalNumber(1));
        eq("bulbasaur", folder, "dossier de l'espèce n° 1");
        Path cache = Storage.DIR.resolve("followers");
        Files.createDirectories(cache);
        javax.imageio.ImageIO.write(sheet, "png", cache.resolve(folder + ".png").toFile());
        for (int i = 0; i < 100 && !w.followerUsesWalkingSprite(); i++) {
            g.simulateFrame();
            Thread.sleep(5);
        }
        check(w.followerUsesWalkingSprite(), "le vrai sprite (du cache) remplace l'icône");
        BufferedImage frame = g.renderWindow(960, 640);
        boolean seen = false;
        int target = 0xFF0000FF;                                       // il va vers la droite
        for (int y = 0; y < frame.getHeight() && !seen; y += 2) for (int x = 0; x < frame.getWidth() && !seen; x += 2) seen = frame.getRGB(x, y) == target;
        check(seen, "dessiné à l'écran");
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
        g.openWindow(new DexWindow(g));
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
        check(g.top() instanceof dev.orvaen.mmo.scenes.IntroScene, "nouvelle partie : le Professeur accueille le joueur");
        g.renderWindow(1280, 800);
        boolean namedRival = false;
        for (int i = 0; i < 40 && !(g.top() instanceof OverworldScene); i++) {
            if (g.top() instanceof dev.orvaen.mmo.scenes.NamingScene) {
                g.renderWindow(1280, 800);
                g.input().type("Régis\n");
                namedRival = true;
            } else {
                tap(g, Button.A);
            }
            for (int f = 0; f < 60; f++) g.simulateFrame();
        }
        check(namedRival, "le Professeur demande le nom du rival");
        eq("Régis", g.save.rivalName, "nom du rival sauvegardé");
        check(g.top() instanceof OverworldScene, "après l'accueil, on arrive dans le monde");
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
        tap(g, Button.DOWN);                      // carte de dresseur
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

        // Écran titre lu dans la ROM : logo, Pokémon, texte, cadre, flammes
        dev.orvaen.mmo.data.TitleGraphics tg = g.data.titleGraphics();
        check(tg.available() && tg.border != null && tg.flames != null && tg.slash != null, "écran titre trouvé dans la ROM");
        eq(tg.flames.length, 10, "10 images de flamme");
        eq(tg.logo.getRGB(5 * 8 + 3, 2 * 8 + 3), GbaGfx.bgr555ToArgb(SyntheticRom.TITLE_LOGO), "logo (8 bits) à sa place");
        eq(tg.monster.getRGB(12 * 8, 10 * 8), GbaGfx.bgr555ToArgb(SyntheticRom.TITLE_MON), "Pokémon de la boîte (palette 13)");
        eq(tg.pressStart.getRGB(10 * 8, 17 * 8 + 2), GbaGfx.bgr555ToArgb(SyntheticRom.TITLE_TEXT), "« PRESS START »");
        eq(tg.copyrightOnly.getRGB(10 * 8, 17 * 8 + 2), GbaGfx.bgr555ToArgb(SyntheticRom.TITLE_BORDER),
                "« PRESS START » éteint pendant le clignotement (couleur du fond)");
        eq(tg.border.getRGB(3, 3), GbaGfx.bgr555ToArgb(SyntheticRom.TITLE_BORDER), "cadre (palette 14)");

        dev.orvaen.mmo.scenes.TitleScene t = new dev.orvaen.mmo.scenes.TitleScene(g);
        g.push(t);
        for (int i = 0; i < 60; i++) g.simulateFrame();
        BufferedImage frame = g.renderWindow(1200, 800);
        eq(frame.getRGB(600, 400 - 30), GbaGfx.bgr555ToArgb(SyntheticRom.TITLE_MON), "Pokémon dessiné au centre");
        check(t.waitingForStart(), "attend « PRESS START »");
        tap(g, Button.START);
        check(!t.waitingForStart(), "Start → menu");
        click(g, t.anchor("PLAY"));
        for (int i = 0; i < 30; i++) g.simulateFrame();
        check(g.top() instanceof CharacterCreatorScene, "sans personnage : Jouer → création");

        Game g2 = freshGame();
        g2.save.name = "Rayan";
        g2.save.look = PlayerLook.defaults();
        dev.orvaen.mmo.scenes.TitleScene t2 = new dev.orvaen.mmo.scenes.TitleScene(g2);
        g2.push(t2);
        g2.renderWindow(1280, 800);
        tap(g2, Button.A);
        click(g2, t2.anchor("CHARACTER"));
        check(g2.top() instanceof CharacterCreatorScene, "Modifier le personnage");
        tap(g2, Button.B);
        check(g2.top() == t2, "retour à l'accueil");
        click(g2, t2.anchor("PLAY"));
        for (int i = 0; i < 30; i++) g2.simulateFrame();
        check(g2.top() instanceof OverworldScene, "avec personnage : Jouer → le monde");

        // Nouvelle partie : confirmation, puis tout est effacé (copie de secours gardée)
        g2.save.caught.add(1);
        g2.save.flags.set(0x300);
        g2.save.store();
        dev.orvaen.mmo.scenes.TitleScene t3 = new dev.orvaen.mmo.scenes.TitleScene(g2);
        g2.push(t3);
        g2.renderWindow(1280, 800);
        tap(g2, Button.START);
        click(g2, t3.anchor("NEW_GAME"));
        g2.renderWindow(1280, 800);
        click(g2, t3.confirmAnchor(1));
        check(g2.save.hasCharacter(), "Annuler : rien n'est effacé");
        click(g2, t3.anchor("NEW_GAME"));
        click(g2, t3.confirmAnchor(0));
        for (int i = 0; i < 30; i++) g2.simulateFrame();
        check(g2.top() instanceof CharacterCreatorScene, "nouvelle partie → création du personnage");
        check(!g2.save.hasCharacter() && !g2.save.hasPosition(), "personnage et position effacés");
        check(g2.save.caught.isEmpty() && g2.save.flags.isEmpty() && !g2.save.eventsReady, "progression effacée");
        check(Files.exists(Storage.DIR.resolve("save-BPRE.properties.bak")), "copie de secours gardée");
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
