package dev.orvaen.mmo.script;

import dev.orvaen.mmo.rom.Rom;
import dev.orvaen.mmo.rom.TextCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Dresseurs de la ROM (table de 40 octets par dresseur), retrouvés par leur forme :
 * <pre>
 * 0x00 u8 options de l'équipe (bit 0 : attaques choisies, bit 1 : objet tenu)
 * 0x01 u8 classe · 0x03 u8 portrait · 0x04 nom (12 octets) · 0x20 u8 taille de l'équipe · 0x24 ptr équipe
 * </pre>
 * Chaque Pokémon de l'équipe commence par : u16 IV, u8 niveau, u8 —, u16 espèce (8 ou 16 octets).
 * Selon les options : u16 objet tenu (bit 1), puis 4 attaques u16 (bit 0).
 * <p>
 * {@link Trainer#party()} donne pour chaque Pokémon : {@code {espèce, niveau, IV (0-255), att1, att2, att3, att4}}
 * (attaques à 0 : celles qu'il connaît naturellement à ce niveau).
 */
public final class Trainers {

    public static final int BYTES = 40;
    private static final int MIN_RUN = 60;

    public record Trainer(int id, String name, int pic, List<int[]> party) {}

    private final Rom rom;
    private final int table;
    private final int speciesCount;

    public Trainers(Rom rom, int speciesCount) {
        this.rom = rom;
        this.speciesCount = speciesCount;
        this.table = find();
    }

    public boolean available() {
        return table >= 0;
    }

    /** Dresseur n° {@code id}, ou {@code null}. */
    public Trainer get(int id) {
        if (table < 0 || id <= 0) return null;
        int o = table + id * BYTES;
        if (!valid(o)) return null;
        int flags = rom.u8(o), size = rom.u8(o + 0x20), party = rom.pointer(o + 0x24);
        int stride = (flags & 1) != 0 ? 16 : 8;
        List<int[]> mons = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            int m = party + i * stride;
            int[] mon = {rom.u16(m + 4), rom.u8(m + 2), rom.u16(m) & 0xFF, 0, 0, 0, 0};
            if ((flags & 1) != 0) {
                int movesAt = m + ((flags & 2) != 0 ? 8 : 6);
                for (int k = 0; k < 4; k++) mon[3 + k] = rom.u16(movesAt + k * 2);
            }
            mons.add(mon);
        }
        return new Trainer(id, TextCodec.read(rom, o + 4, 12), rom.u8(o + 3), mons);
    }

    private boolean valid(int o) {
        if (!rom.inRange(o, BYTES)) return false;
        int flags = rom.u8(o), size = rom.u8(o + 0x20), party = rom.pointer(o + 0x24);
        if (flags > 3 || size < 1 || size > 6 || party < 0) return false;
        int stride = (flags & 1) != 0 ? 16 : 8;
        if (!rom.inRange(party, size * stride)) return false;
        for (int i = 0; i < size; i++) {
            int m = party + i * stride;
            int species = rom.u16(m + 4), level = rom.u8(m + 2);
            if (species == 0 || species >= speciesCount || level == 0 || level > 100) return false;
        }
        return true;
    }

    /** Début de la table : l'entrée 0 (« aucun dresseur ») précède une longue suite d'entrées valides. */
    private int find() {
        for (int o = 0; o + BYTES * (MIN_RUN + 1) <= rom.size(); o += 4) {
            if (!valid(o + BYTES)) continue;
            int n = 1;
            while (n < MIN_RUN && valid(o + BYTES * (n + 1))) n++;
            if (n >= MIN_RUN && rom.u8(o + 0x20) == 0) return o;
        }
        return -1;
    }
}
