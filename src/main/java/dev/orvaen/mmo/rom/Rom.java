package dev.orvaen.mmo.rom;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

/**
 * Image d'une cartouche GBA fournie par le joueur.
 * Le moteur ne fait que LIRE ces octets : rien n'est copié dans le dépôt.
 */
public final class Rom {

    public static final int GBA_ROM_BASE = 0x0800_0000;
    private static final int MAX_ROM_SIZE = 0x0200_0000; // 32 Mo

    private final byte[] data;
    private final String title;
    private final String gameCode;
    private final int version;
    private final GameProfile profile;

    public Rom(byte[] data) {
        if (data.length < 0x200) {
            throw new RomException("Fichier trop petit pour être une ROM GBA (" + data.length + " octets).");
        }
        this.data = data;
        this.title = ascii(0xA0, 12);
        this.gameCode = ascii(0xAC, 4);
        this.version = u8(0xBC);
        this.profile = GameProfile.detect(gameCode);
        if (profile == null) {
            throw new RomException("Jeu non supporté : « " + title + " » (code " + gameCode + ").\n"
                    + "Jeux pris en charge : Rouge Feu, Vert Feuille, Émeraude (toutes langues).");
        }
    }

    public static Rom load(Path path) throws IOException {
        long size = Files.size(path);
        if (size > MAX_ROM_SIZE) {
            throw new RomException("Fichier trop gros pour une ROM GBA : " + size + " octets.");
        }
        return new Rom(Files.readAllBytes(path));
    }

    public int size() { return data.length; }
    public String title() { return title; }
    public String gameCode() { return gameCode; }
    public int version() { return version; }
    public GameProfile profile() { return profile; }

    public boolean inRange(int offset, int length) {
        return offset >= 0 && length >= 0 && (long) offset + length <= data.length;
    }

    public int u8(int offset) {
        check(offset, 1);
        return data[offset] & 0xFF;
    }

    public int s8(int offset) {
        check(offset, 1);
        return data[offset];
    }

    public int u16(int offset) {
        check(offset, 2);
        return (data[offset] & 0xFF) | (data[offset + 1] & 0xFF) << 8;
    }

    public int s16(int offset) {
        return (short) u16(offset);
    }

    public int s32(int offset) {
        check(offset, 4);
        return (data[offset] & 0xFF)
                | (data[offset + 1] & 0xFF) << 8
                | (data[offset + 2] & 0xFF) << 16
                | (data[offset + 3] & 0xFF) << 24;
    }

    public long u32(int offset) {
        return s32(offset) & 0xFFFF_FFFFL;
    }

    /** Lit un pointeur GBA (0x08xxxxxx) et le convertit en offset fichier, ou -1 s'il est invalide. */
    public int pointer(int offset) {
        if (!inRange(offset, 4)) return -1;
        long raw = u32(offset);
        long local = raw - GBA_ROM_BASE;
        if (local < 0 || local >= data.length) return -1;
        return (int) local;
    }

    /** Comme {@link #pointer(int)} mais lève une exception explicite si le pointeur est cassé. */
    public int requirePointer(int offset, String what) {
        int p = pointer(offset);
        if (p < 0) {
            throw new RomException(String.format("Pointeur invalide pour %s à 0x%06X.", what, offset));
        }
        return p;
    }

    public byte[] slice(int offset, int length) {
        check(offset, length);
        byte[] out = new byte[length];
        System.arraycopy(data, offset, out, 0, length);
        return out;
    }

    /** Recherche une suite d'octets donnée en hexadécimal ; renvoie l'offset ou -1. */
    public int find(String hex) {
        return find(HexFormat.of().parseHex(hex), 0);
    }

    public int find(byte[] needle, int from) {
        outer:
        for (int i = Math.max(0, from); i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    /** Accès brut en lecture seule pour les décodeurs (LZ77, tuiles). */
    byte[] raw() { return data; }

    private String ascii(int offset, int length) {
        return new String(data, offset, length, StandardCharsets.US_ASCII).replace('\0', ' ').trim();
    }

    private void check(int offset, int length) {
        if (!inRange(offset, length)) {
            throw new RomException(String.format("Lecture hors ROM : 0x%X (+%d).", offset, length));
        }
    }

    @Override
    public String toString() {
        return String.format("%s [%s v%d] — %s", title, gameCode, version, profile.displayName(gameCode));
    }
}
