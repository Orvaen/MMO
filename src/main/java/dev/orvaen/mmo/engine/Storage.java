package dev.orvaen.mmo.engine;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Configuration et sauvegarde locales, dans ~/.orvaen-mmo/.
 * La ROM n'est jamais copiée : on mémorise seulement son chemin.
 */
public final class Storage {

    public static final Path DIR = System.getProperty("orvaen.home") != null
            ? Path.of(System.getProperty("orvaen.home"))
            : Path.of(System.getProperty("user.home"), ".orvaen-mmo");
    private static final Path CONFIG = DIR.resolve("config.properties");

    private Storage() {}

    public static String romPath() {
        return read(CONFIG).getProperty("rom.path");
    }

    public static void setRomPath(String path) {
        Properties p = read(CONFIG);
        p.setProperty("rom.path", path);
        write(CONFIG, p, "Configuration du moteur");
    }

    /** Progression du joueur, une sauvegarde par jeu (code ROM). */
    public static final class Save {
        public int group = -1;
        public int num = -1;
        public int x;
        public int y;
        public int facing;
        public final Set<Integer> seen = new TreeSet<>();
        public final Set<Integer> caught = new TreeSet<>();

        private final Path file;

        private Save(Path file) {
            this.file = file;
        }

        public static Save load(String gameCode) {
            Save s = new Save(DIR.resolve("save-" + gameCode + ".properties"));
            Properties p = read(s.file);
            s.group = intOf(p, "map.group", -1);
            s.num = intOf(p, "map.num", -1);
            s.x = intOf(p, "player.x", 0);
            s.y = intOf(p, "player.y", 0);
            s.facing = intOf(p, "player.facing", 0);
            parseSet(p.getProperty("dex.seen", ""), s.seen);
            parseSet(p.getProperty("dex.caught", ""), s.caught);
            return s;
        }

        public boolean hasPosition() {
            return group >= 0 && num >= 0;
        }

        public void store() {
            Properties p = new Properties();
            p.setProperty("map.group", String.valueOf(group));
            p.setProperty("map.num", String.valueOf(num));
            p.setProperty("player.x", String.valueOf(x));
            p.setProperty("player.y", String.valueOf(y));
            p.setProperty("player.facing", String.valueOf(facing));
            p.setProperty("dex.seen", join(seen));
            p.setProperty("dex.caught", join(caught));
            write(file, p, "Sauvegarde");
        }
    }

    private static Properties read(Path file) {
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException e) {
                System.err.println("Lecture impossible de " + file + " : " + e.getMessage());
            }
        }
        return p;
    }

    private static void write(Path file, Properties p, String comment) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                p.store(w, comment);
            }
        } catch (IOException e) {
            System.err.println("Écriture impossible de " + file + " : " + e.getMessage());
        }
    }

    private static int intOf(Properties p, String key, int fallback) {
        try {
            return Integer.parseInt(p.getProperty(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static void parseSet(String csv, Set<Integer> out) {
        for (String s : csv.split(",")) {
            s = s.trim();
            if (s.isEmpty()) continue;
            try {
                out.add(Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
                // entrée corrompue : ignorée
            }
        }
    }

    private static String join(Set<Integer> set) {
        StringBuilder sb = new StringBuilder();
        for (int v : set) {
            if (!sb.isEmpty()) sb.append(',');
            sb.append(v);
        }
        return sb.toString();
    }
}
