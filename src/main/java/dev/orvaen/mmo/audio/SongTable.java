package dev.orvaen.mmo.audio;

import dev.orvaen.mmo.rom.Rom;

/**
 * Table des morceaux de la ROM (musiques, effets sonores, fanfares) du moteur sonore « MP2K » des jeux GBA.
 * <p>
 * Chaque entrée fait 8 octets : pointeur vers l'en-tête du morceau, puis n° de lecteur (u16) deux fois.
 * L'en-tête : nombre de pistes, blocs, priorité, réverbération, pointeur vers le jeu d'instruments,
 * puis un pointeur par piste. La table est retrouvée par sa forme : une longue suite d'entrées valides.
 */
public final class SongTable {

    private static final int MIN_SONGS = 100;

    private final Rom rom;
    private final int table;
    private final int count;

    public SongTable(Rom rom) {
        this(rom, MIN_SONGS);
    }

    SongTable(Rom rom, int minSongs) {
        this.rom = rom;
        int t = -1, n = 0;
        for (int o = 0; o + 8 * minSongs <= rom.size(); o += 4) {
            if (!entry(rom, o)) continue;
            int k = 1;
            while (entry(rom, o + k * 8)) k++;
            if (k >= minSongs) {
                t = o;
                n = k;
                break;
            }
            o += k * 8 - 4;
        }
        table = t;
        count = n;
    }

    public boolean available() {
        return table >= 0;
    }

    public int count() {
        return count;
    }

    /** En-tête du morceau n° {@code id}, ou -1. */
    public int header(int id) {
        if (id < 0 || id >= count) return -1;
        return rom.pointer(table + id * 8);
    }

    private static boolean entry(Rom rom, int o) {
        if (!rom.inRange(o, 8) || rom.u16(o + 4) > 31 || rom.u16(o + 6) > 31) return false;
        return header(rom, rom.pointer(o));
    }

    static boolean header(Rom rom, int h) {
        if (h < 0 || !rom.inRange(h, 8)) return false;
        int tracks = rom.u8(h);
        if (tracks > 16 || rom.u8(h + 1) != 0) return false;
        if (tracks > 0 && rom.pointer(h + 4) < 0) return false;
        if (!rom.inRange(h + 8, tracks * 4)) return false;
        for (int i = 0; i < tracks; i++) if (rom.pointer(h + 8 + i * 4) < 0) return false;
        return true;
    }
}
