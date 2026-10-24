package dev.orvaen.mmo.player;

import dev.orvaen.mmo.gfx.GbaGfx;
import dev.orvaen.mmo.player.Recolor.Source;
import dev.orvaen.mmo.rom.GameProfile;
import dev.orvaen.mmo.rom.Lz77;
import dev.orvaen.mmo.rom.Rom;
import dev.orvaen.mmo.rom.RomException;

/**
 * Sprites du héros et de l'héroïne, lus dans la ROM du joueur (Rouge Feu / Vert Feuille).
 * <p>
 * Les tables sont retrouvées par leur forme, sans adresse codée en dur :
 * <ul>
 *   <li>marche : {@code ObjectEventGraphicsInfo} (tileTag 0xFFFF, palette 0x1100 / 0x1110, 16x32),
 *       9 frames non compressées de 256 octets, suivies des 9 frames de course (bras qui balancent,
 *       buste penché) ;</li>
 *   <li>palette de marche : entrée {ptr, 0x1100} de la table des palettes d'objets ;</li>
 *   <li>portraits : tables {ptr, 0x800, n°} et {ptr, n°} des dresseurs (n° 135 / 136) ;</li>
 *   <li>dos en combat : table {ptr, 0x2800, 0} {ptr, 0x2800, 1} et ses palettes.</li>
 * </ul>
 * Si quelque chose manque (ROM modifiée, Émeraude…), {@link #available()} renvoie faux
 * et le moteur se rabat sur l'avatar dessiné.
 */
public final class PlayerGraphics {

    public static final int BOY = 0, GIRL = 1;
    public static final int OW_FRAMES = 9;
    /** Frames de course, rangées juste après la marche : sud 9-11, nord 12-14, ouest 15-17. */
    public static final int RUN_FIRST = 9, RUN_FRAMES = 9;

    private static final int[] OW_PALETTE_TAGS = {0x1100, 0x1110};
    private static final int[] FRONT_PIC_IDS = {135, 136};
    private static final int FRONT_PIC_SIZE = 0x800;
    private static final int OW_FRAME_BYTES = 0x100;

    private final Source[][] overworld = new Source[2][];
    private final Source[] front = new Source[2];
    private final Source[] back = new Source[2];
    private final String problem;

    public PlayerGraphics(Rom rom) {
        String err = null;
        try {
            if (rom.profile() != GameProfile.FIRERED_LEAFGREEN) {
                throw new RomException("sprites du joueur pris en charge pour Rouge Feu / Vert Feuille uniquement");
            }
            int[] owPalette = findObjectPalette(rom, OW_PALETTE_TAGS[BOY]);
            for (int body = BOY; body <= GIRL; body++) {
                overworld[body] = readOverworld(rom, OW_PALETTE_TAGS[body], owPalette);
            }
            readTrainerPics(rom);
            readBackPics(rom);
        } catch (RomException e) {
            err = e.getMessage();
        }
        problem = err;
    }

    /** Sprites fournis directement (packs de sprites, outils, tests) ; index 0 = garçon, 1 = fille. */
    public PlayerGraphics(Source[][] overworld, Source[] front, Source[] back) {
        for (int body = BOY; body <= GIRL; body++) {
            int n = overworld[body].length;
            if (n != OW_FRAMES && n != OW_FRAMES + RUN_FRAMES) {
                throw new IllegalArgumentException("9 frames de marche (+ 9 de course) attendues");
            }
            this.overworld[body] = overworld[body].clone();
            this.front[body] = front[body];
            this.back[body] = back[body];
        }
        this.problem = null;
    }

    public boolean available() {
        return problem == null;
    }

    /** Pourquoi les sprites n'ont pas pu être lus (ou {@code null}). */
    public String problem() {
        return problem;
    }

    public Source overworld(int body, int frame) {
        return overworld[body][frame];
    }

    /** La ROM fournit-elle les frames de course (sinon on court avec celles de marche) ? */
    public boolean hasRunFrames(int body) {
        return overworld[body].length >= RUN_FIRST + RUN_FRAMES;
    }

    public Source front(int body) {
        return front[body];
    }

    public Source back(int body) {
        return back[body];
    }

    // ------------------------------------------------------------------ sprites de marche

    private static Source[] readOverworld(Rom rom, int paletteTag, int[] palette) {
        int info = findGraphicsInfo(rom, paletteTag);
        int images = rom.requirePointer(info + 0x1C, "les frames du joueur");
        boolean run = true;
        for (int f = RUN_FIRST; f < RUN_FIRST + RUN_FRAMES; f++) {
            int e = images + f * 8;
            if (!rom.inRange(e, 8) || rom.pointer(e) < 0 || rom.u16(e + 4) != OW_FRAME_BYTES) {
                run = false;
                break;
            }
        }
        Source[] frames = new Source[run ? RUN_FIRST + RUN_FRAMES : OW_FRAMES];
        for (int f = 0; f < frames.length; f++) {
            int data = rom.requirePointer(images + f * 8, "la frame " + f + " du joueur");
            frames[f] = new Source(rom.slice(data, OW_FRAME_BYTES), 2, 4, palette);
        }
        return frames;
    }

    /** Première structure 16x32 du joueur portant ce tag de palette (la tenue « normale »). */
    static int findGraphicsInfo(Rom rom, int paletteTag) {
        for (int o = 0; o + 0x24 <= rom.size(); o += 4) {
            if (rom.u16(o) != 0xFFFF || rom.u16(o + 2) != paletteTag) continue;
            if (rom.s16(o + 8) != 16 || rom.s16(o + 10) != 32) continue;
            int images = rom.pointer(o + 0x1C);
            if (images < 0 || !rom.inRange(images, OW_FRAMES * 8)) continue;
            if (rom.pointer(images) < 0 || rom.u16(images + 4) != OW_FRAME_BYTES) continue;
            return o;
        }
        throw new RomException(String.format("sprite de marche du joueur (palette 0x%04X) introuvable", paletteTag));
    }

    /** Palette non compressée de la table {ptr, tag} des objets du monde. */
    static int[] findObjectPalette(Rom rom, int tag) {
        for (int o = 4; o + 16 <= rom.size(); o += 4) {
            if (rom.u16(o + 4) != tag || rom.u16(o + 6) != 0) continue;
            int pal = rom.pointer(o);
            if (pal < 0 || !rom.inRange(pal, 32)) continue;
            int next = rom.u16(o + 12), prev = rom.u16(o - 4);
            if ((next & 0xFF00) != (tag & 0xFF00) && (prev & 0xFF00) != (tag & 0xFF00)) continue;
            return GbaGfx.readPalettes(rom, pal, 1);
        }
        throw new RomException(String.format("palette du joueur (0x%04X) introuvable", tag));
    }

    // ------------------------------------------------------------------ portraits et dos

    /** Portrait de dresseur n° {@code id} (Professeur, rival…), ou {@code null}. */
    public static java.awt.image.BufferedImage trainerPic(Rom rom, int id) {
        try {
            int sheets = findSheetTable(rom);
            int palettes = findPaletteTable(rom, FRONT_PIC_IDS[GIRL] + 1, true);
            byte[] tiles = Lz77.decompress(rom, rom.requirePointer(sheets + id * 8, "le portrait"));
            int[] pal = GbaGfx.palettesFromBytes(Lz77.decompress(rom, rom.requirePointer(palettes + id * 8, "la palette")));
            return GbaGfx.sprite(tiles, 8, 8, pal);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void readTrainerPics(Rom rom) {
        int sheets = findSheetTable(rom);
        int palettes = findPaletteTable(rom, FRONT_PIC_IDS[GIRL] + 1, true);
        for (int body = BOY; body <= GIRL; body++) {
            int id = FRONT_PIC_IDS[body];
            byte[] tiles = Lz77.decompress(rom, rom.requirePointer(sheets + id * 8, "le portrait du joueur"));
            int[] pal = GbaGfx.palettesFromBytes(Lz77.decompress(rom,
                    rom.requirePointer(palettes + id * 8, "la palette du portrait")));
            front[body] = new Source(tiles, 8, 8, pal);
        }
    }

    private void readBackPics(Rom rom) {
        int sheets = -1;
        for (int o = 0; o + 24 <= rom.size(); o += 4) {
            if (rom.u16(o + 4) == 0x2800 && rom.u16(o + 6) == 0 && rom.u16(o + 12) == 0x2800 && rom.u16(o + 14) == 1
                    && rom.pointer(o) >= 0 && rom.pointer(o + 8) >= 0) {
                sheets = o;
                break;
            }
        }
        if (sheets < 0) throw new RomException("vue de dos du joueur introuvable");
        int palettes = findPaletteTable(rom, 2, false);
        for (int body = BOY; body <= GIRL; body++) {
            int data = rom.requirePointer(sheets + body * 8, "la vue de dos");
            byte[] tiles = Lz77.looksCompressed(rom, data)
                    ? Lz77.decompress(rom, data)
                    : rom.slice(data, FRONT_PIC_SIZE); // stockée non compressée : 1re frame seulement
            int[] pal = GbaGfx.palettesFromBytes(Lz77.decompress(rom,
                    rom.requirePointer(palettes + body * 8, "la palette de dos")));
            back[body] = new Source(tiles, 8, 8, pal);
        }
    }

    /**
     * Pointeurs vers les tables d'espèces (sprites de face / de dos, palettes normales / chromatiques).
     * Elles ont exactement la même forme que celles des dresseurs ({image, 0x800, n°}, {palette, n°}) :
     * l'entrée n° 136 serait Pyroli au lieu de l'héroïne.
     */
    private static final int[] SPECIES_TABLE_POINTERS = {0x128, 0x12C, 0x130, 0x134};
    /** Les tables d'espèces comptent 412 entrées, celles des dresseurs moins de 200. */
    private static final int SPECIES_LIKE = 200;

    private static boolean isSpeciesTable(Rom rom, int offset, int tagOffset) {
        for (int ptr : SPECIES_TABLE_POINTERS) {
            if (rom.pointer(ptr) == offset) return true;
        }
        int e = offset + SPECIES_LIKE * 8;
        return rom.inRange(e, 8) && rom.u16(e + tagOffset) == SPECIES_LIKE && rom.pointer(e) >= 0;
    }

    /** Table {ptr, 0x800, n°} des portraits de dresseurs, numérotée depuis 0. */
    private static int findSheetTable(Rom rom) {
        outer:
        for (int o = 0; o + 8 * (FRONT_PIC_IDS[GIRL] + 1) <= rom.size(); o += 4) {
            for (int k = 0; k < 8; k++) {
                int e = o + k * 8;
                if (rom.u16(e + 4) != FRONT_PIC_SIZE || rom.u16(e + 6) != k) continue outer;
                int p = rom.pointer(e);
                if (p < 0 || !Lz77.looksCompressed(rom, p)) continue outer;
            }
            int last = o + FRONT_PIC_IDS[GIRL] * 8;
            if (rom.u16(last + 6) == FRONT_PIC_IDS[GIRL] && rom.u16(last + 4) == FRONT_PIC_SIZE
                    && !isSpeciesTable(rom, o, 6)) {
                return o;
            }
        }
        throw new RomException("table des portraits de dresseurs introuvable");
    }

    /**
     * Table {ptr palette compressée, n°, 0} numérotée depuis 0.
     * @param longTable vrai : au moins {@code count} entrées ; faux : exactement les entrées de la vue de dos
     *                  (l'entrée suivant les {@code count} premières ne continue pas la numérotation)
     */
    private static int findPaletteTable(Rom rom, int count, boolean longTable) {
        int need = longTable ? count : 6;
        outer:
        for (int o = 0; o + 8 * (need + 1) <= rom.size(); o += 4) {
            for (int k = 0; k < need; k++) {
                int e = o + k * 8;
                if (rom.u16(e + 4) != k || rom.u16(e + 6) != 0) continue outer;
                int p = rom.pointer(e);
                if (p < 0 || !Lz77.looksCompressed(rom, p) || rom.u8(p + 1) != 0x20) continue outer;
            }
            if (!longTable && rom.u16(o + need * 8 + 4) == need && rom.pointer(o + need * 8) >= 0) continue;
            if (longTable && isSpeciesTable(rom, o, 4)) continue;
            return o;
        }
        throw new RomException("table des palettes de dresseurs introuvable");
    }
}
