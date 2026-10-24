package dev.orvaen.mmo;

import dev.orvaen.mmo.data.GameData;
import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.RomLauncher;
import dev.orvaen.mmo.engine.Storage;
import dev.orvaen.mmo.rom.Rom;
import dev.orvaen.mmo.rom.RomCheck;
import dev.orvaen.mmo.rom.RomException;
import dev.orvaen.mmo.scenes.CharacterCreatorScene;
import dev.orvaen.mmo.tools.Extractor;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Point d'entrée.
 * <pre>
 *   java -jar orvaen-mmo.jar                     jouer (fenêtre de choix des ROM au premier lancement)
 *   java -jar orvaen-mmo.jar --roms              rouvrir la fenêtre de choix des ROM
 *   java -jar orvaen-mmo.jar --rom jeu.gba       jouer avec cette ROM
 *   java -jar orvaen-mmo.jar --rom jeu.gba --info           afficher ce que le moteur a trouvé
 *   java -jar orvaen-mmo.jar --rom jeu.gba --extract dossier exporter sprites et cartes en PNG
 * </pre>
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        String romArg = null, extractDir = null;
        boolean info = false, roms = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--rom" -> romArg = next(args, ++i, "--rom");
                case "--extract" -> extractDir = next(args, ++i, "--extract");
                case "--info" -> info = true;
                case "--roms" -> roms = true;
                case "-h", "--help" -> {
                    usage();
                    return;
                }
                default -> {
                    System.err.println("Option inconnue : " + args[i]);
                    usage();
                    System.exit(2);
                }
            }
        }

        boolean headless = info || extractDir != null;
        if (headless) {
            RomLauncher.Choice saved = RomLauncher.remembered();
            String path = romArg != null ? romArg : saved == null ? null : saved.path().toString();
            if (path == null) {
                System.err.println("Indique ta ROM avec --rom chemin/vers/jeu.gba");
                System.exit(2);
            }
            GameData data = loadOrExit(Path.of(path));
            System.out.println(data.summary());
            if (extractDir != null) new Extractor(data).exportAll(Path.of(extractDir));
            return;
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // apparence par défaut
        }
        GameData data = loadInteractive(romArg, roms);
        if (data == null) return;
        System.out.println(data.summary());
        Game game = new Game(data);
        game.run(() -> CharacterCreatorScene.firstScene(game));
    }

    /**
     * ROM passée en argument, sinon celle mémorisée (si elle est toujours valide), sinon la fenêtre
     * de choix des ROM. Si une ROM est refusée au chargement complet, la fenêtre revient avec l'erreur.
     */
    private static GameData loadInteractive(String romArg, boolean forceWindow) throws Exception {
        if (romArg != null) {
            try {
                return new GameData(Rom.load(Path.of(romArg)));
            } catch (RomException | IOException e) {
                error("Impossible d'utiliser " + romArg + " :\n" + e.getMessage());
                return null;
            }
        }
        RomLauncher.Choice choice = forceWindow ? null : RomLauncher.remembered();
        String problem = null;
        RomCheck.Slot problemSlot = null;
        while (true) {
            if (choice == null) {
                choice = RomLauncher.show(problem, problemSlot);
                if (choice == null) return null; // le joueur a quitté
            }
            try {
                GameData data = new GameData(Rom.load(choice.path()));
                Storage.setLastSlot(choice.slot());
                return data;
            } catch (RomException | IOException e) {
                problem = "Refusée au chargement : " + e.getMessage();
                problemSlot = choice.slot();
                choice = null;
            }
        }
    }

    private static void error(String message) throws Exception {
        SwingUtilities.invokeAndWait(() ->
                JOptionPane.showMessageDialog(null, message, "Orvaen MMO", JOptionPane.ERROR_MESSAGE));
    }

    private static GameData loadOrExit(Path path) {
        try {
            return new GameData(Rom.load(path));
        } catch (RomException | IOException e) {
            System.err.println("Impossible d'utiliser cette ROM : " + e.getMessage());
            System.exit(1);
            return null;
        }
    }

    private static String next(String[] args, int i, String option) {
        if (i >= args.length) {
            System.err.println("Valeur manquante après " + option);
            System.exit(2);
        }
        return args[i];
    }

    private static void usage() {
        System.out.println("""
                Orvaen MMO — moteur qui lit ta propre ROM GBA (aucun fichier du jeu n'est fourni).
                  (sans option)           lance la dernière ROM, ou ouvre la fenêtre de choix des ROM
                  --roms                  ouvre la fenêtre de choix des ROM (Parcourir…)
                  --rom <fichier.gba>     joue directement avec ce fichier
                  --info                  affiche ce que le moteur a extrait, sans ouvrir de fenêtre
                  --extract <dossier>     exporte sprites, cartes et espèces (PNG/CSV) pour vérification
                """);
    }
}
