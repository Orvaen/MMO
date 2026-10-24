package dev.orvaen.mmo.engine;

import dev.orvaen.mmo.data.PlayerLook;
import dev.orvaen.mmo.rom.RomCheck;

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

    /** ROM mémorisée pour un emplacement (Kanto, Hoenn), ou {@code null}. */
    public static String romPath(RomCheck.Slot slot) {
        Properties p = read(CONFIG);
        String v = p.getProperty("rom." + slot.key());
        if (v == null && slot == RomCheck.Slot.KANTO) v = p.getProperty("rom.path"); // ancien réglage
        return v == null || v.isBlank() ? null : v;
    }

    public static void setRomPath(RomCheck.Slot slot, String path) {
        Properties p = read(CONFIG);
        String legacy = p.getProperty("rom.path");
        if (legacy != null && !p.containsKey("rom.kanto")) p.setProperty("rom.kanto", legacy); // migration
        if (path == null || path.isBlank()) p.remove("rom." + slot.key());
        else p.setProperty("rom." + slot.key(), path);
        p.remove("rom.path");
        write(CONFIG, p, "Configuration du moteur");
    }

    /** Zoom du monde choisi par le joueur (0 = automatique). */
    public static int zoom() {
        try {
            return Integer.parseInt(read(CONFIG).getProperty("view.zoom", "0").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static void setZoom(int zoom) {
        Properties p = read(CONFIG);
        p.setProperty("view.zoom", String.valueOf(zoom));
        write(CONFIG, p, "Configuration du moteur");
    }

    /** Frames pour courir d'une case (8 = chaussures de course GBA ; 10 par défaut, plus posé). */
    public static int runFramesPerTile() {
        try {
            int v = Integer.parseInt(read(CONFIG).getProperty("move.runFrames", "10").trim());
            return Math.max(6, Math.min(16, v));
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    /** Position et taille mémorisées d'une fenêtre de l'interface (x, y, largeur, hauteur), ou {@code null}. */
    public static double[] uiRect(String key) {
        String v = read(CONFIG).getProperty("ui." + key);
        if (v == null) return null;
        try {
            String[] p = v.split(",");
            if (p.length != 4) return null;
            double[] r = new double[4];
            for (int i = 0; i < 4; i++) r[i] = Double.parseDouble(p[i].trim());
            return r;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static void setUiRect(String key, double x, double y, double w, double h) {
        Properties p = read(CONFIG);
        p.setProperty("ui." + key, String.format(java.util.Locale.ROOT, "%.0f,%.0f,%.0f,%.0f", x, y, w, h));
        write(CONFIG, p, "Configuration du moteur");
    }

    /** Son coupé (touche F9), mémorisé. */
    public static boolean muted() {
        return Boolean.parseBoolean(read(CONFIG).getProperty("audio.muted", "false"));
    }

    public static void setMuted(boolean muted) {
        Properties p = read(CONFIG);
        p.setProperty("audio.muted", String.valueOf(muted));
        write(CONFIG, p, "Configuration du moteur");
    }

    /** Emplacement à lancer : celui joué en dernier s'il est mémorisé. */
    public static RomCheck.Slot lastSlot() {
        String v = read(CONFIG).getProperty("rom.last", "");
        for (RomCheck.Slot s : RomCheck.Slot.values()) if (s.key().equals(v)) return s;
        return null;
    }

    public static void setLastSlot(RomCheck.Slot slot) {
        Properties p = read(CONFIG);
        p.setProperty("rom.last", slot.key());
        write(CONFIG, p, "Configuration du moteur");
    }

    /** Progression du joueur, une sauvegarde par jeu (code ROM). */
    public static final class Save {
        public int group = -1;
        public int num = -1;
        public int x;
        public int y;
        public int facing;
        public String name = "";
        public PlayerLook look;
        /** Carte de dresseur : n° ID (tiré au hasard à la création), argent, temps de jeu, début de l'aventure. */
        public int trainerId = -1;
        public int money = 3000;
        public long playFrames;
        public String started = "";
        /** Progression de l'histoire : drapeaux et variables des scripts de la ROM. */
        public final java.util.BitSet flags = new java.util.BitSet();
        public final java.util.Map<Integer, Integer> vars = new java.util.TreeMap<>();
        /** Drapeaux de début de partie posés (script « tout réinitialiser » de la ROM). */
        public boolean eventsReady;
        /** Équipe : espèce et niveau (le système de combat complet viendra ensuite). */
        public final java.util.List<int[]> party = new java.util.ArrayList<>();
        /** Surnoms de l'équipe (vide : nom de l'espèce), dans le même ordre que {@link #party}. */
        public final java.util.List<String> partyNames = new java.util.ArrayList<>();
        /** Nom du rival, choisi pendant l'accueil du Professeur. */
        public String rivalName = "";
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
            s.name = p.getProperty("player.name", "").trim();
            s.look = PlayerLook.decode(p.getProperty("player.look"));
            s.trainerId = intOf(p, "player.id", -1);
            s.money = intOf(p, "player.money", 3000);
            try {
                s.playFrames = Long.parseLong(p.getProperty("player.playtime", "0").trim()) * 60;
            } catch (NumberFormatException e) {
                s.playFrames = 0;
            }
            s.started = p.getProperty("player.started", "").trim();
            for (String f : p.getProperty("event.flags", "").split(",")) {
                try {
                    if (!f.isBlank()) s.flags.set(Integer.parseInt(f.trim(), 16));
                } catch (NumberFormatException ignored) {
                    // entrée corrompue
                }
            }
            for (String kv : p.getProperty("event.vars", "").split(",")) {
                String[] parts = kv.split(":");
                try {
                    if (parts.length == 2) s.vars.put(Integer.parseInt(parts[0].trim(), 16), Integer.parseInt(parts[1].trim()));
                } catch (NumberFormatException ignored) {
                    // entrée corrompue
                }
            }
            s.eventsReady = Boolean.parseBoolean(p.getProperty("event.ready", "false"));
            s.rivalName = p.getProperty("rival.name", "").trim();
            for (String n : p.getProperty("party.names", "").split("\\|", -1)) s.partyNames.add(n);
            for (String m : p.getProperty("party", "").split(",")) {
                String[] parts = m.split(":");
                try {
                    if (parts.length == 2) s.party.add(new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())});
                } catch (NumberFormatException ignored) {
                    // entrée corrompue
                }
            }
            parseSet(p.getProperty("dex.seen", ""), s.seen);
            parseSet(p.getProperty("dex.caught", ""), s.caught);
            return s;
        }

        /**
         * Nouvelle partie : tout est remis à zéro (personnage, position, histoire, équipe, Pokédex…).
         * L'ancienne sauvegarde est gardée à côté, en « .bak », au cas où.
         */
        public void reset() {
            try {
                if (Files.isRegularFile(file)) {
                    Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                System.err.println("Copie de secours impossible : " + e.getMessage());
            }
            group = -1;
            num = -1;
            x = 0;
            y = 0;
            facing = 0;
            name = "";
            look = null;
            trainerId = -1;
            money = 3000;
            playFrames = 0;
            started = "";
            flags.clear();
            vars.clear();
            eventsReady = false;
            party.clear();
            partyNames.clear();
            rivalName = "";
            seen.clear();
            caught.clear();
            store();
        }

        /** Le personnage a-t-il déjà été créé ? */
        public boolean hasCharacter() {
            return look != null && !name.isBlank();
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
            p.setProperty("player.name", name);
            if (look != null) p.setProperty("player.look", look.encode());
            if (trainerId < 0) trainerId = new java.util.Random().nextInt(65536);
            if (started.isEmpty()) started = java.time.LocalDate.now().toString();
            p.setProperty("player.id", String.valueOf(trainerId));
            p.setProperty("player.money", String.valueOf(money));
            p.setProperty("player.playtime", String.valueOf(playFrames / 60));
            p.setProperty("player.started", started);
            StringBuilder f = new StringBuilder();
            for (int i = flags.nextSetBit(0); i >= 0; i = flags.nextSetBit(i + 1)) {
                if (!f.isEmpty()) f.append(',');
                f.append(Integer.toHexString(i));
            }
            p.setProperty("event.flags", f.toString());
            StringBuilder v = new StringBuilder();
            for (var e : vars.entrySet()) {
                if (e.getValue() == 0) continue;
                if (!v.isEmpty()) v.append(',');
                v.append(Integer.toHexString(e.getKey())).append(':').append(e.getValue());
            }
            p.setProperty("event.vars", v.toString());
            p.setProperty("event.ready", String.valueOf(eventsReady));
            StringBuilder pt = new StringBuilder();
            for (int[] m : party) {
                if (!pt.isEmpty()) pt.append(',');
                pt.append(m[0]).append(':').append(m[1]);
            }
            p.setProperty("party", pt.toString());
            while (partyNames.size() < party.size()) partyNames.add("");
            p.setProperty("party.names", String.join("|", partyNames.subList(0, party.size())));
            p.setProperty("rival.name", rivalName);
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
