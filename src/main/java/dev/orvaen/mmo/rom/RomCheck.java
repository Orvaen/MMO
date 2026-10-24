package dev.orvaen.mmo.rom;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Vérification rapide d'un fichier ROM pour la fenêtre de sélection : on ne lit que l'en-tête
 * (192 octets), donc le résultat s'affiche immédiatement, même pour une ROM de 32 Mo.
 */
public final class RomCheck {

    /** Emplacements de ROM, comme les lignes de la fenêtre « ROM » de PokeMMO. */
    public enum Slot {
        KANTO("Rouge Feu / Vert Feuille", "Kanto", GameProfile.FIRERED_LEAFGREEN),
        HOENN("Émeraude", "Hoenn", GameProfile.EMERALD);

        public final String games;
        public final String region;
        public final GameProfile profile;

        Slot(String games, String region, GameProfile profile) {
            this.games = games;
            this.region = region;
            this.profile = profile;
        }

        public String key() {
            return name().toLowerCase();
        }

        static Slot of(GameProfile p) {
            for (Slot s : values()) if (s.profile == p) return s;
            return null;
        }
    }

    public enum Status { EMPTY, OK, ERROR }

    public record Result(Status status, String message, Slot slot) {
        public boolean ok() {
            return status == Status.OK;
        }
    }

    private static final int MIN_SIZE = 0x10_0000;   // 1 Mo
    private static final int MAX_SIZE = 0x200_0000;  // 32 Mo
    private static final int HEADER = 0xC0;

    private RomCheck() {}

    /** Vérifie le fichier pour l'emplacement attendu ({@code null} : n'importe lequel). */
    public static Result inspect(Path file, Slot expected) {
        if (file == null || file.toString().isBlank()) return new Result(Status.EMPTY, "Aucune ROM choisie", null);
        if (!Files.isRegularFile(file)) return error("Fichier introuvable");
        long size;
        byte[] h = new byte[HEADER];
        try (InputStream in = Files.newInputStream(file)) {
            size = Files.size(file);
            if (in.readNBytes(h, 0, HEADER) < HEADER) return error("Fichier trop petit pour une ROM GBA");
        } catch (IOException e) {
            return error("Lecture impossible : " + e.getMessage());
        }
        if (size < MIN_SIZE || size > MAX_SIZE) return error("Taille inhabituelle pour une ROM GBA (" + mb(size) + ")");
        if (h[0xB2] != (byte) 0x96 || checksum(h) != (h[0xBD] & 0xFF)) {
            return error("Ce n'est pas une ROM GBA valide (en-tête abîmé)");
        }
        String code = new String(h, 0xAC, 4, StandardCharsets.US_ASCII);
        GameProfile profile = GameProfile.detect(code);
        if (profile == null) {
            String title = new String(h, 0xA0, 12, StandardCharsets.US_ASCII).replace('\0', ' ').trim();
            return error("Jeu non pris en charge : « " + title + " » (" + code + ")");
        }
        Slot slot = Slot.of(profile);
        String name = profile.displayName(code);
        if (expected != null && slot != expected) {
            return new Result(Status.ERROR, name + " : à mettre sur la ligne « " + slot.games + " »", slot);
        }
        return new Result(Status.OK, name + " · " + mb(size), slot);
    }

    /** Somme de contrôle de l'en-tête GBA (octet 0xBD), vérifiée par le BIOS au démarrage. */
    static int checksum(byte[] h) {
        int sum = 0;
        for (int i = 0xA0; i <= 0xBC; i++) sum += h[i] & 0xFF;
        return (-(sum + 0x19)) & 0xFF;
    }

    private static Result error(String message) {
        return new Result(Status.ERROR, message, null);
    }

    private static String mb(long size) {
        return size % 0x10_0000 == 0 ? size / 0x10_0000 + " Mo" : String.format("%.1f Mo", size / 1048576.0);
    }
}
