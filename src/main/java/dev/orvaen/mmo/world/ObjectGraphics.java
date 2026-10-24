package dev.orvaen.mmo.world;

import dev.orvaen.mmo.gfx.GbaGfx;
import dev.orvaen.mmo.rom.Rom;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Sprites des personnages du monde (PNJ, objets), lus dans la ROM du joueur.
 * <p>
 * Deux tables retrouvées par leur forme :
 * <ul>
 *   <li>pointeurs vers les {@code ObjectEventGraphicsInfo} (0x24 octets : tags, taille, frames…),
 *       la n° 0 étant le héros ;</li>
 *   <li>palettes des objets : entrées {ptr, tag 0x11xx}.</li>
 * </ul>
 * Personnages « standard » : 9 frames (sud, nord, ouest, puis 2 pas par direction ; l'est = ouest miroir).
 */
public final class ObjectGraphics {

    private static final int INFO_BYTES = 0x24;
    private static final int MIN_TABLE = 40;

    /** Sprite d'un graphisme : frames prêtes à dessiner. */
    public record Sprite(BufferedImage[] frames, int width, int height) {
        /** Le personnage a-t-il des frames de marche (sinon : objet fixe, une seule frame) ? */
        public boolean walks() {
            return frames.length >= 9;
        }
    }

    private final Rom rom;
    private final int table;
    private final int count;
    private final Map<Integer, int[]> palettes = new HashMap<>();
    private final Map<Integer, Sprite> cache = new HashMap<>();

    public ObjectGraphics(Rom rom) {
        this.rom = rom;
        int t = findTable(rom);
        int n = 0;
        if (t >= 0) {
            // la table continue tant que les entrées ressemblent à des graphismes (certaines peuvent
            // être inutilisables : elles sont alors ignorées au chargement)
            while (n < 256 && looksLikeInfo(rom, rom.pointer(t + n * 4))) n++;
        }
        table = t;
        count = n;
        readPalettes();
    }

    public int count() {
        return count;
    }

    public int paletteCount() {
        return palettes.size();
    }

    /** Sprite du graphisme n° {@code id}, ou {@code null} (graphisme « variable », ROM modifiée…). */
    public Sprite get(int id) {
        if (id < 0 || id >= count) return null;
        return cache.computeIfAbsent(id, this::load);
    }

    private Sprite load(int id) {
        int info = rom.pointer(table + id * 4);
        if (!validInfo(rom, info)) return null;
        int w = rom.s16(info + 8), h = rom.s16(info + 10);
        int[] pal = palettes.get(rom.u16(info + 2));
        if (pal == null) return null;
        int images = rom.pointer(info + 0x1C);
        int frameBytes = w * h / 2;
        int frames = 0;
        while (frames < 9) {
            int e = images + frames * 8;
            if (!rom.inRange(e, 8) || rom.pointer(e) < 0 || rom.u16(e + 4) != frameBytes) break;
            frames++;
        }
        if (frames == 0) return null;
        if (frames < 9) frames = 1;
        BufferedImage[] out = new BufferedImage[frames];
        for (int f = 0; f < frames; f++) {
            byte[] tiles = rom.slice(rom.pointer(images + f * 8), frameBytes);
            out[f] = GbaGfx.sprite(tiles, w / 8, h / 8, pal);
        }
        return new Sprite(out, w, h);
    }

    // ------------------------------------------------------------------ recherche dans la ROM

    private static boolean validInfo(Rom rom, int o) {
        if (o < 0 || !rom.inRange(o, INFO_BYTES)) return false;
        if (rom.u16(o) != 0xFFFF || (rom.u16(o + 2) & 0xFF00) != 0x1100) return false;
        int w = rom.s16(o + 8), h = rom.s16(o + 10);
        if (!size(w) || !size(h)) return false;
        int images = rom.pointer(o + 0x1C);
        return images >= 0 && rom.inRange(images, 8) && rom.pointer(images) >= 0 && rom.u16(images + 4) == w * h / 2;
    }

    private static boolean looksLikeInfo(Rom rom, int o) {
        return o >= 0 && rom.inRange(o, INFO_BYTES) && rom.u16(o) == 0xFFFF;
    }

    private static boolean size(int v) {
        return v == 8 || v == 16 || v == 32 || v == 64;
    }

    /** Premier tableau d'au moins {@value #MIN_TABLE} pointeurs vers des graphismes d'objets. */
    static int findTable(Rom rom) {
        for (int o = 0; o + MIN_TABLE * 4 <= rom.size(); o += 4) {
            if (!validInfo(rom, rom.pointer(o))) continue;
            int n = 1;
            while (n < MIN_TABLE && validInfo(rom, rom.pointer(o + n * 4))) n++;
            if (n >= MIN_TABLE) return o;
            o += n * 4 - 4;
        }
        return -1;
    }

    /** Table {ptr, tag, 0} des palettes d'objets, autour de l'entrée du héros (0x1100). */
    private void readPalettes() {
        int start = -1;
        for (int o = 0; o + 8 <= rom.size(); o += 4) {
            if (paletteEntry(o) && rom.u16(o + 4) == 0x1100 && paletteEntry(o + 8)) {
                start = o;
                break;
            }
        }
        if (start < 0) return;
        while (paletteEntry(start - 8)) start -= 8;
        for (int e = start; paletteEntry(e); e += 8) {
            palettes.putIfAbsent(rom.u16(e + 4), GbaGfx.readPalettes(rom, rom.pointer(e), 1));
        }
    }

    private boolean paletteEntry(int e) {
        if (e < 0 || !rom.inRange(e, 8)) return false;
        int p = rom.pointer(e);
        return p >= 0 && rom.inRange(p, 32) && (rom.u16(e + 4) & 0xFF00) == 0x1100 && rom.u16(e + 6) == 0;
    }
}
