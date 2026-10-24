package dev.orvaen.mmo;

import dev.orvaen.mmo.data.GameData;
import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.Storage;
import dev.orvaen.mmo.rom.Rom;
import dev.orvaen.mmo.rom.RomException;
import dev.orvaen.mmo.scenes.CharacterCreatorScene;
import dev.orvaen.mmo.tools.Extractor;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Point d'entrée.
 * <pre>
 *   java -jar orvaen-mmo.jar                     jouer (demande la ROM au premier lancement)
 *   java -jar orvaen-mmo.jar --rom jeu.gba       jouer avec cette ROM
 *   java -jar orvaen-mmo.jar --rom jeu.gba --info           afficher ce que le moteur a trouvé
 *   java -jar orvaen-mmo.jar --rom jeu.gba --extract dossier exporter sprites et cartes en PNG
 * </pre>
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        String romArg = null, extractDir = null;
        boolean info = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--rom" -> romArg = next(args, ++i, "--rom");
                case "--extract" -> extractDir = next(args, ++i, "--extract");
                case "--info" -> info = true;
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
            String path = romArg != null ? romArg : Storage.romPath();
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
        GameData data = loadInteractive(romArg);
        if (data == null) return;
        Storage.setRomPath(lastPath);
        System.out.println(data.summary());
        Game game = new Game(data);
        game.run(() -> CharacterCreatorScene.firstScene(game));
    }

    private static String lastPath;

    /** Ouvre la ROM passée en argument, celle mémorisée, ou demande au joueur d'en choisir une. */
    private static GameData loadInteractive(String romArg) throws Exception {
        String candidate = romArg != null ? romArg : Storage.romPath();
        while (true) {
            if (candidate == null || !Files.isRegularFile(Path.of(candidate))) {
                candidate = chooseRom();
                if (candidate == null) return null;
            }
            try {
                GameData data = new GameData(Rom.load(Path.of(candidate)));
                lastPath = Path.of(candidate).toAbsolutePath().toString();
                return data;
            } catch (RomException | IOException e) {
                error("Impossible d'utiliser cette ROM :\n" + e.getMessage());
                candidate = null;
            }
        }
    }

    private static String chooseRom() throws Exception {
        AtomicReference<String> out = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Choisis ta ROM (Rouge Feu, Vert Feuille ou Émeraude)");
            chooser.setFileFilter(new FileNameExtensionFilter("ROM Game Boy Advance (*.gba)", "gba"));
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                out.set(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        return out.get();
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
                  --rom <fichier.gba>     ROM à utiliser (mémorisée pour les prochains lancements)
                  --info                  affiche ce que le moteur a extrait, sans ouvrir de fenêtre
                  --extract <dossier>     exporte sprites, cartes et espèces (PNG/CSV) pour vérification
                """);
    }
}
