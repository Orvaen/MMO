package dev.orvaen.mmo.battle;

import dev.orvaen.mmo.rom.GameProfile;
import dev.orvaen.mmo.rom.Rom;
import dev.orvaen.mmo.rom.TextCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Les règles du combat lues dans la ROM : attaques, attaques apprises par niveau, efficacité des types
 * et courbes d'expérience.
 * <ul>
 *   <li><b>Attaques</b> : l'en-tête de la ROM pointe vers leur table (12 octets chacune) et vers leurs noms.</li>
 *   <li><b>Apprentissages</b> : une table d'un pointeur par espèce ; chaque liste contient des valeurs
 *       {@code niveau << 9 | attaque} et finit par 0xFFFF. Retrouvée par sa forme.</li>
 *   <li><b>Types</b> : une liste de triplets {attaquant, défenseur, multiplicateur×10} qui commence toujours
 *       par « Normal contre Roche = 0,5 » ; finie par 0xFF.</li>
 *   <li><b>Expérience</b> : 6 courbes de 101 niveaux ; la première est « niveau³ » (0, 1, 8, 27, 64…).</li>
 * </ul>
 * Si un élément est introuvable (ROM modifiée), on utilise les formules officielles à la place.
 */
public final class BattleData {

    public static final int MOVE_COUNT = 355;
    private static final int MOVE_BYTES = 12, MOVE_NAME_BYTES = 13;
    private static final int MAX_LEARNSET = 32;

    private final Rom rom;
    private final Move[] moves;
    private final int[][] learnsets;
    private final int[][] typeChart = new int[18][18];   // multiplicateur × 10 (10 = normal)
    private final long[][] experience;                   // [courbe][niveau]

    public BattleData(Rom rom) {
        this.rom = rom;
        this.moves = readMoves();
        this.learnsets = readLearnsets();
        readTypeChart();
        this.experience = readExperience();
    }

    // ------------------------------------------------------------------ attaques

    public Move move(int id) {
        return id > 0 && id < moves.length && moves[id] != null ? moves[id] : Move.none();
    }

    public boolean movesAvailable() {
        return moves.length > 1;
    }

    private Move[] readMoves() {
        int table = rom.pointer(GameProfile.PTR_MOVES), names = rom.pointer(GameProfile.PTR_MOVE_NAMES);
        if (table < 0 || names < 0) return new Move[1];
        Move[] out = new Move[MOVE_COUNT];
        for (int id = 1; id < MOVE_COUNT; id++) {
            int o = table + id * MOVE_BYTES;
            if (!rom.inRange(o, MOVE_BYTES) || !rom.inRange(names + id * MOVE_NAME_BYTES, MOVE_NAME_BYTES)) break;
            String name = TextCodec.read(rom, names + id * MOVE_NAME_BYTES, MOVE_NAME_BYTES);
            out[id] = new Move(id, name, rom.u8(o), rom.u8(o + 1), rom.u8(o + 2), rom.u8(o + 3), rom.u8(o + 4),
                    rom.u8(o + 5), rom.u8(o + 6), rom.s8(o + 7), rom.u8(o + 8));
        }
        return out;
    }

    // ------------------------------------------------------------------ apprentissages

    /**
     * Attaques connues par un Pokémon sauvage de ce niveau : les 4 dernières apprises (règle du jeu).
     */
    public int[] movesAtLevel(int species, int level) {
        List<Integer> known = new ArrayList<>();
        for (int[] e : learnset(species)) {
            if (e[0] > level) break;
            if (known.contains(e[1])) continue;
            known.add(e[1]);
            if (known.size() > 4) known.remove(0);
        }
        int[] out = new int[4];
        for (int i = 0; i < known.size(); i++) out[i] = known.get(i);
        if (known.isEmpty()) out[0] = 33;                       // Charge, au cas où
        return out;
    }

    /** Attaques apprises en atteignant exactement ce niveau. */
    public List<Integer> movesLearnedAt(int species, int level) {
        List<Integer> out = new ArrayList<>();
        for (int[] e : learnset(species)) if (e[0] == level) out.add(e[1]);
        return out;
    }

    /** {niveau, attaque} dans l'ordre de la ROM. */
    private List<int[]> learnset(int species) {
        List<int[]> out = new ArrayList<>();
        if (learnsets == null || species < 0 || species >= learnsets.length) return out;
        for (int v : learnsets[species]) out.add(new int[]{v >> 9, v & 0x1FF});
        return out;
    }

    private int[][] readLearnsets() {
        int n = GameProfile.SPECIES_COUNT;
        for (int o = 0; o + n * 4 <= rom.size(); o += 4) {
            if (learnsetAt(rom.pointer(o)) == null) continue;
            int k = 1;
            while (k < n && learnsetAt(rom.pointer(o + k * 4)) != null) k++;
            if (k < n) {
                o += k * 4 - 4;
                continue;
            }
            int[][] out = new int[n][];
            for (int i = 0; i < n; i++) out[i] = learnsetAt(rom.pointer(o + i * 4));
            return out;
        }
        return null;
    }

    /** Liste d'apprentissage valide à cette adresse, ou null. */
    private int[] learnsetAt(int p) {
        if (p < 0 || (p & 1) != 0) return null;
        List<Integer> values = new ArrayList<>();
        int lastLevel = 0;
        for (int i = 0; i <= MAX_LEARNSET; i++) {
            if (!rom.inRange(p + i * 2, 2)) return null;
            int v = rom.u16(p + i * 2);
            if (v == 0xFFFF) break;
            int move = v & 0x1FF, level = v >> 9;
            if (move == 0 || move >= MOVE_COUNT || level == 0 || level > 100 || level < lastLevel) return null;
            lastLevel = level;
            values.add(v);
            if (i == MAX_LEARNSET) return null;
        }
        if (values.isEmpty()) return null;
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    // ------------------------------------------------------------------ types

    /** Multiplicateur × 10 d'un type d'attaque contre un type de défense (0, 5, 10 ou 20). */
    public int effectiveness(int attackType, int defenseType) {
        if (attackType < 0 || attackType >= 18 || defenseType < 0 || defenseType >= 18) return 10;
        return typeChart[attackType][defenseType];
    }

    private void readTypeChart() {
        for (int[] row : typeChart) java.util.Arrays.fill(row, 10);
        int o = rom.find("000505000805");
        if (o < 0) return;
        for (int i = 0; i < 200 && rom.inRange(o, 3); i++, o += 3) {
            int atk = rom.u8(o);
            if (atk == 0xFF) break;
            if (atk == 0xFE) continue;                          // triplet séparateur « Clairvoyance »
            int def = rom.u8(o + 1), mult = rom.u8(o + 2);
            if (atk < 18 && def < 18) typeChart[atk][def] = mult;
        }
    }

    // ------------------------------------------------------------------ expérience

    /** Expérience totale nécessaire pour atteindre ce niveau, selon la courbe de l'espèce. */
    public long experienceFor(int growthRate, int level) {
        level = Math.max(1, Math.min(100, level));
        int g = Math.max(0, Math.min(5, growthRate));
        if (experience != null) return experience[g][level];
        return formula(g, level);
    }

    private long[][] readExperience() {
        int o = rom.find("0000000001000000080000001B000000400000007D000000");
        if (o < 0 || !rom.inRange(o, 6 * 101 * 4)) return null;
        long[][] out = new long[6][101];
        for (int g = 0; g < 6; g++) {
            for (int l = 0; l <= 100; l++) out[g][l] = rom.u32(o + (g * 101 + l) * 4);
        }
        return out;
    }

    /** Formules officielles des 6 courbes (moyenne, erratique, fluctuante, parabolique, rapide, lente). */
    static long formula(int growth, int n) {
        long n3 = (long) n * n * n;
        return switch (growth) {
            case 1 -> n <= 50 ? n3 * (100 - n) / 50 : n <= 68 ? n3 * (150 - n) / 100
                    : n <= 98 ? n3 * ((1911 - 10L * n) / 3) / 500 : n3 * (160 - n) / 100;
            case 2 -> n <= 15 ? n3 * ((n + 1) / 3 + 24) / 50 : n <= 36 ? n3 * (n + 14) / 50 : n3 * (n / 2 + 32) / 50;
            case 3 -> n <= 1 ? 0 : 6 * n3 / 5 - 15L * n * n + 100L * n - 140;
            case 4 -> 4 * n3 / 5;
            case 5 -> 5 * n3 / 4;
            default -> n3;
        };
    }
}
