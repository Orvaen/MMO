package dev.orvaen.mmo.world;

import dev.orvaen.mmo.gfx.GbaGfx;
import dev.orvaen.mmo.rom.GameProfile;
import dev.orvaen.mmo.rom.Rom;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tuiles animées du tileset principal (eau, courant, bord de plage, fleurs, cascade), comme sur GBA :
 * à intervalles fixes, le jeu recopie une frame dans la mémoire vidéo à la place de certaines tuiles.
 * <p>
 * Le rythme et les emplacements viennent de la décompilation (pret) ; les images, elles, sont lues
 * dans la ROM du joueur. Chaque animation y est rangée sous forme d'un tableau de pointeurs vers ses
 * frames, stockées les unes à la suite des autres : c'est cette forme que l'on recherche.
 */
public final class TileAnims {

    /**
     * Une animation : {@code tiles} tuiles copiées à partir de la tuile {@code dest}, quand
     * {@code compteur % period == phase}, frame n° {@code (compteur / divisor) % frames.length}.
     */
    public record Anim(String name, int dest, int tiles, List<int[]> candidates, int length, int period, int phase,
                       int divisor) {
        public int bytes() {
            return tiles * GbaGfx.TILE_BYTES_4BPP;
        }
    }

    /** Animation à appliquer à cette frame. */
    public record Step(Anim anim, int frame) {}

    private record Spec(String name, int dest, int tiles, int[] pattern, int period, int phase, int divisor) {}

    private static final List<Spec> FIRERED = List.of(
            new Spec("Bord de plage", 464, 18, seq(8), 8, 0, 8),
            new Spec("Eau et courant", 416, 48, seq(8), 16, 1, 16),
            new Spec("Fleurs", 508, 4, seq(5), 16, 2, 16));
    private static final List<Spec> EMERALD = List.of(
            new Spec("Fleurs", 508, 4, new int[]{0, 1, 0, 2}, 16, 0, 16),
            new Spec("Eau", 432, 30, seq(8), 16, 1, 16),
            new Spec("Bord de plage", 464, 10, new int[]{0, 1, 2, 3, 4, 5, 6, 0}, 16, 2, 16),
            new Spec("Cascade", 496, 6, seq(4), 16, 3, 16),
            new Spec("Bord de l'eau", 480, 10, seq(4), 16, 4, 16));

    private final Rom rom;
    private final List<Anim> anims = new ArrayList<>();
    private final int counterMax;
    private final Map<Tileset, int[][]> chosen = new IdentityHashMap<>();
    private int counter;

    public TileAnims(Rom rom) {
        this.rom = rom;
        boolean emerald = rom.profile() == GameProfile.EMERALD;
        counterMax = emerald ? 256 : 640;
        for (Spec s : emerald ? EMERALD : FIRERED) {
            List<int[]> found = find(rom, s.pattern, s.tiles * GbaGfx.TILE_BYTES_4BPP);
            if (!found.isEmpty()) {
                anims.add(new Anim(s.name, s.dest, s.tiles, found, s.pattern.length, s.period, s.phase, s.divisor));
            }
        }
    }

    public List<Anim> anims() {
        return anims;
    }

    /** Avance d'une frame (60 par seconde) et renvoie les animations à recopier maintenant. */
    public List<Step> tick() {
        List<Step> due = new ArrayList<>(2);
        for (Anim a : anims) {
            if (counter % a.period == a.phase) due.add(new Step(a, (counter / a.divisor) % a.length));
        }
        counter = (counter + 1) % counterMax;
        return due;
    }

    /**
     * Ce tileset utilise-t-il cette animation ? Les tuiles qu'elle remplace doivent ressembler à l'une
     * de ses frames : un tileset d'intérieur, qui a d'autres tuiles à cet endroit, n'est pas touché.
     */
    public boolean appliesTo(Tileset ts, Anim a) {
        return frames(ts, a) != null;
    }

    /** Frames de l'animation pour ce tileset (le tableau trouvé dont une frame ressemble à ses tuiles). */
    private int[] frames(Tileset ts, Anim a) {
        int[][] r = chosen.computeIfAbsent(ts, t -> {
            int[][] out = new int[anims.size()][];
            for (int i = 0; i < anims.size(); i++) {
                for (int[] c : anims.get(i).candidates) {
                    if (resembles(t, anims.get(i), c)) {
                        out[i] = c;
                        break;
                    }
                }
            }
            return out;
        });
        int i = anims.indexOf(a);
        return i >= 0 ? r[i] : null;
    }

    /** Recopie la frame dans les tuiles du tileset (comme le jeu le fait en mémoire vidéo). */
    public void apply(Tileset ts, Step step) {
        Anim a = step.anim;
        int[] frames = frames(ts, a);
        int at = a.dest * GbaGfx.TILE_BYTES_4BPP;
        if (frames == null || at + a.bytes() > ts.tiles.length) return;
        System.arraycopy(rom.slice(frames[step.frame], a.bytes()), 0, ts.tiles, at, a.bytes());
    }

    private boolean resembles(Tileset ts, Anim a, int[] frames) {
        int at = a.dest * GbaGfx.TILE_BYTES_4BPP;
        if (ts.secondary || at + a.bytes() > ts.tiles.length) return false;
        for (int f : frames) {
            int same = 0;
            for (int i = 0; i < a.bytes(); i++) if (ts.tiles[at + i] == (byte) rom.u8(f + i)) same++;
            if (same >= a.bytes() * 0.6) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ recherche dans la ROM

    private static int[] seq(int n) {
        int[] s = new int[n];
        for (int i = 0; i < n; i++) s[i] = i;
        return s;
    }

    /** Tableaux de pointeurs dont les cibles suivent {@code pattern}, frames contiguës de {@code size} octets. */
    static List<int[]> find(Rom rom, int[] pattern, int size) {
        List<int[]> out = new ArrayList<>();
        int distinct = 0;
        for (int p : pattern) distinct = Math.max(distinct, p + 1);
        for (int o = 0; o + pattern.length * 4 <= rom.size(); o += 4) {
            int base = rom.pointer(o);
            if (base < 0 || (base & 3) != 0 || !rom.inRange(base, distinct * size)) continue;
            boolean ok = true;
            for (int j = 1; j < pattern.length && ok; j++) ok = rom.pointer(o + j * 4) == base + pattern[j] * size;
            if (!ok) continue;
            int[] frames = new int[pattern.length];
            for (int j = 0; j < pattern.length; j++) frames[j] = base + pattern[j] * size;
            out.add(frames);
            if (out.size() >= 8) break;
        }
        return out;
    }
}
