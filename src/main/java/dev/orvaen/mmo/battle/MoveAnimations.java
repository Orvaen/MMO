package dev.orvaen.mmo.battle;

import dev.orvaen.mmo.gfx.GbaGfx;
import dev.orvaen.mmo.rom.Lz77;
import dev.orvaen.mmo.rom.Rom;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Les animations d'attaque de la ROM : leurs « scripts », leurs images (flammes, étoiles, impacts…) et leurs
 * petites animations image par image.
 * <p>
 * Dans le jeu, chaque attaque a un script d'animation (table {@code gBattleAnims_Moves}, un pointeur par
 * attaque). Ce script charge des images ({@code loadspritegfx}), crée des sprites ({@code createsprite}),
 * joue des sons ({@code playsewithpan}), attend ({@code delay})… Cette classe lit tout ça ; c'est
 * {@code scenes.MoveAnimPlayer} qui le joue.
 * <p>
 * Images : table {@code {pointeur, taille, n° 10000 + i}} ; palettes : {@code {pointeur, n° 10000 + i}}.
 * Toutes ces tables sont retrouvées par leur forme.
 */
public final class MoveAnimations {

    public static final int FIRST_TAG = 10000;
    private static final int MIN_PICS = 60;

    // Codes des commandes de script d'animation (asm/macros/battle_anim_script.inc)
    public static final int LOAD_GFX = 0x00, UNLOAD_GFX = 0x01, CREATE_SPRITE = 0x02, CREATE_TASK = 0x03,
            DELAY = 0x04, WAIT_VISUAL = 0x05, NOP = 0x06, NOP2 = 0x07, END = 0x08, PLAY_SE = 0x09, MON_BG = 0x0A,
            CLEAR_MON_BG = 0x0B, SET_ALPHA = 0x0C, BLEND_OFF = 0x0D, CALL = 0x0E, RETURN = 0x0F, SET_ARG = 0x10,
            TWO_TURN = 0x11, IF_MOVE_TURN = 0x12, GOTO = 0x13, FADE_TO_BG = 0x14, RESTORE_BG = 0x15,
            WAIT_BG_OUT = 0x16, WAIT_BG_IN = 0x17, CHANGE_BG = 0x18, PLAY_SE_PAN = 0x19, SET_PAN = 0x1A,
            PAN_SE = 0x1B, LOOP_SE = 0x1C, WAIT_PLAY_SE = 0x1D, SET_BLDCNT = 0x1E, SOUND_TASK = 0x1F,
            WAIT_SOUND = 0x20, IF_ARG = 0x21, MON_BG_STATIC = 0x22, CLEAR_MON_BG_STATIC = 0x23, IF_CONTEST = 0x24,
            FADE_BG_SET = 0x25, PAN_SE_NONE = 0x26, PAN_SE_ALL = 0x27, SPLIT_BG = 0x28, SPLIT_BG_ALL = 0x29,
            SPLIT_BG_FOES = 0x2A, INVISIBLE = 0x2B, VISIBLE = 0x2C, TEAM_BACK = 0x2D, TEAM_FWD = 0x2E,
            STOP_SOUND = 0x2F;

    /** Taille en octets de chaque commande (sans ses arguments variables). */
    private static final int[] SIZE = new int[0x30];

    static {
        Arrays.fill(SIZE, -1);
        int[][] s = {{LOAD_GFX, 3}, {UNLOAD_GFX, 3}, {DELAY, 2}, {WAIT_VISUAL, 1}, {NOP, 1}, {NOP2, 1},
                {END, 1}, {PLAY_SE, 3}, {MON_BG, 2}, {CLEAR_MON_BG, 2}, {SET_ALPHA, 3}, {BLEND_OFF, 1}, {CALL, 5},
                {RETURN, 1}, {SET_ARG, 4}, {TWO_TURN, 9}, {IF_MOVE_TURN, 6}, {GOTO, 5}, {FADE_TO_BG, 2},
                {RESTORE_BG, 1}, {WAIT_BG_OUT, 1}, {WAIT_BG_IN, 1}, {CHANGE_BG, 2}, {PLAY_SE_PAN, 4},
                {SET_PAN, 2}, {PAN_SE, 7}, {LOOP_SE, 6}, {WAIT_PLAY_SE, 5}, {SET_BLDCNT, 3}, {WAIT_SOUND, 1},
                {IF_ARG, 8}, {MON_BG_STATIC, 2}, {CLEAR_MON_BG_STATIC, 2}, {IF_CONTEST, 5}, {FADE_BG_SET, 4},
                {PAN_SE_NONE, 7}, {PAN_SE_ALL, 7}, {SPLIT_BG, 2}, {SPLIT_BG_ALL, 1}, {SPLIT_BG_FOES, 2},
                {INVISIBLE, 2}, {VISIBLE, 2}, {TEAM_BACK, 2}, {TEAM_FWD, 2}, {STOP_SOUND, 1}};
        for (int[] e : s) SIZE[e[0]] = e[1];
    }

    /** Longueur d'une commande à l'adresse {@code o} (arguments variables compris), ou -1. */
    public static int commandLength(Rom rom, int o) {
        if (!rom.inRange(o, 1)) return -1;
        int op = rom.u8(o);
        if (op >= SIZE.length) return -1;
        return switch (op) {
            case CREATE_SPRITE -> rom.inRange(o + 6, 1) ? 7 + rom.u8(o + 6) * 2 : -1;
            case CREATE_TASK -> rom.inRange(o + 6, 1) ? 7 + rom.u8(o + 6) * 2 : -1;
            case SOUND_TASK -> rom.inRange(o + 5, 1) ? 6 + rom.u8(o + 5) * 2 : -1;
            default -> SIZE[op];
        };
    }

    /** Un sprite d'animation : image (n° de planche), palette, taille, et animation image par image. */
    public record Template(int tileTag, int paletteTag, int width, int height, int[][] frames, boolean loops) {
        /** Vrai si le sprite n'a pas d'image (il sert à déplacer un Pokémon). */
        public boolean invisible() {
            return tileTag < FIRST_TAG || tileTag >= 0xFFFF;
        }
    }

    private final Rom rom;
    private int scriptTable = -1, picTable = -1, palTable = -1;
    private final Map<Integer, byte[]> sheets = new HashMap<>();
    private final Map<Integer, int[]> palettes = new HashMap<>();
    private final Map<String, BufferedImage> frames = new HashMap<>();
    private final Map<Integer, Template> templates = new HashMap<>();

    public MoveAnimations(Rom rom) {
        this.rom = rom;
        try {
            picTable = findTagTable(8, true);
            palTable = findTagTable(8, false);
            scriptTable = findScriptTable();
        } catch (RuntimeException ignored) {
            // ROM modifiée : pas d'animations d'attaque
        }
    }

    public boolean available() {
        return scriptTable >= 0 && picTable >= 0;
    }

    public Rom rom() {
        return rom;
    }

    /** Début du script d'animation de l'attaque, ou -1. */
    public int script(int move) {
        if (scriptTable < 0 || move <= 0 || move >= BattleData.MOVE_COUNT) return -1;
        return rom.pointer(scriptTable + move * 4);
    }

    // ------------------------------------------------------------------ images

    /** Une image d'un sprite d'animation, à partir de la tuile {@code tileOffset} de sa planche. */
    public BufferedImage frame(Template t, int tileOffset) {
        if (t.invisible()) return null;
        String key = t.tileTag() + ":" + t.paletteTag() + ":" + tileOffset + ":" + t.width() + "x" + t.height();
        return frames.computeIfAbsent(key, k -> {
            byte[] sheet = sheet(t.tileTag());
            int[] pal = palette(t.paletteTag());
            if (sheet == null || pal == null) return null;
            int tiles = (t.width() / 8) * (t.height() / 8);
            int from = tileOffset * 32, to = from + tiles * 32;
            if (to > sheet.length) return null;
            return GbaGfx.sprite(Arrays.copyOfRange(sheet, from, to), t.width() / 8, t.height() / 8, pal);
        });
    }

    private byte[] sheet(int tag) {
        return sheets.computeIfAbsent(tag, k -> {
            int e = entry(picTable, k);
            return e < 0 ? null : Lz77.decompress(rom, rom.pointer(e));
        });
    }

    private int[] palette(int tag) {
        return palettes.computeIfAbsent(tag, k -> {
            int e = entry(palTable, k);
            return e < 0 ? null : GbaGfx.palettesFromBytes(Lz77.decompress(rom, rom.pointer(e)));
        });
    }

    /** Entrée de la table pour ce n° d'image (rangée dans l'ordre : n° 10000 + i à l'indice i). */
    private int entry(int table, int tag) {
        if (table < 0 || tag < FIRST_TAG) return -1;
        int e = table + (tag - FIRST_TAG) * 8;
        if (!rom.inRange(e, 8) || rom.pointer(e) < 0) return -1;
        boolean sheet = table == picTable;
        int t = rom.u16(e + (sheet ? 6 : 4));
        return t == tag ? e : -1;
    }

    // ------------------------------------------------------------------ modèles de sprites

    /**
     * Modèle de sprite (24 octets) : {n° d'image, n° de palette, forme (OAM), animations, images, …}.
     * La forme donne la taille ; l'animation 0 donne la suite d'images {décalage de tuile, durée}.
     */
    public Template template(int address) {
        return templates.computeIfAbsent(address, a -> {
            if (!rom.inRange(a, 24)) return new Template(0xFFFF, 0, 0, 0, new int[0][], false);
            int tileTag = rom.u16(a), palTag = rom.u16(a + 2);
            int oam = rom.pointer(a + 4), anims = rom.pointer(a + 8);
            int w = 32, h = 32;
            if (oam >= 0 && rom.inRange(oam, 4)) {
                long attr = rom.u32(oam);
                int shape = (int) ((attr >> 14) & 3), size = (int) ((attr >> 30) & 3);
                int[][][] dims = {
                        {{8, 8}, {16, 16}, {32, 32}, {64, 64}},
                        {{16, 8}, {32, 8}, {32, 16}, {64, 32}},
                        {{8, 16}, {8, 32}, {16, 32}, {32, 64}},
                        {{8, 8}, {8, 8}, {8, 8}, {8, 8}}};
                w = dims[shape][size][0];
                h = dims[shape][size][1];
            }
            List<int[]> seq = new ArrayList<>();
            boolean loops = false;
            int first = anims >= 0 && rom.inRange(anims, 4) ? rom.pointer(anims) : -1;
            for (int i = 0; first >= 0 && i < 64 && rom.inRange(first + i * 4, 4); i++) {
                long v = rom.u32(first + i * 4);
                int low = (int) (v & 0xFFFF);
                if (low == 0xFFFF) break;                               // fin
                if (low == 0xFFFE || low == 0xFFFD) {                   // saut / boucle
                    loops = true;
                    break;
                }
                int duration = (int) ((v >> 16) & 0x3F);
                seq.add(new int[]{low, Math.max(1, duration)});
            }
            if (seq.isEmpty()) seq.add(new int[]{0, 1});
            return new Template(tileTag, palTag, w, h, seq.toArray(new int[0][]), loops);
        });
    }

    // ------------------------------------------------------------------ recherche des tables

    /**
     * Table d'images ({@code sheet} : {ptr, taille, n°}) ou de palettes ({ptr, n°}) : une longue suite
     * d'entrées dont les n° se suivent à partir de 10000.
     */
    private int findTagTable(int stride, boolean sheet) {
        int tagAt = sheet ? 6 : 4;
        for (int o = 0; o + stride * MIN_PICS <= rom.size(); o += 4) {
            if (rom.u16(o + tagAt) != FIRST_TAG || rom.u16(o + stride + tagAt) != FIRST_TAG + 1) continue;
            if (!sheet && rom.u16(o + 6) != 0) continue;
            int n = 0;
            while (n < 400 && rom.inRange(o + n * stride, stride) && rom.u16(o + n * stride + tagAt) == FIRST_TAG + n
                    && Lz77.looksCompressed(rom, rom.pointer(o + n * stride))) n++;
            if (n >= MIN_PICS) return o;
        }
        return -1;
    }

    /** Table des scripts : un pointeur par attaque, chacun vers un script valide. */
    private int findScriptTable() {
        int need = BattleData.MOVE_COUNT;
        int enough = need - 25;                       // quelques scripts exotiques peuvent ne pas être reconnus
        for (int o = 0; o + need * 4 <= rom.size(); o += 4) {
            if (!validScript(rom.pointer(o))) continue;
            int n = 1, bad = 0;
            while (n < need && rom.pointer(o + n * 4) >= 0 && (validScript(rom.pointer(o + n * 4)) || ++bad <= 25)) n++;
            if (n >= enough && bad <= 25) return o;
            o += Math.max(0, (n - 1) * 4);
        }
        return -1;
    }

    /** Un script plausible : des commandes connues jusqu'à « end », « return » ou un saut. */
    boolean validScript(int p) {
        if (p < 0) return false;
        int o = p;
        for (int i = 0; i < 1000; i++) {
            int len = commandLength(rom, o);
            if (len <= 0 || !rom.inRange(o, len)) return false;
            int op = rom.u8(o);
            if (op == LOAD_GFX || op == UNLOAD_GFX) {
                int tag = rom.u16(o + 1);
                if (tag < FIRST_TAG || tag > FIRST_TAG + 400) return false;
            }
            if ((op == CREATE_SPRITE || op == CREATE_TASK || op == CALL || op == GOTO) && rom.pointer(o + 1) < 0) return false;
            if (op == END || op == RETURN || op == GOTO) return true;
            o += len;
        }
        return false;
    }
}
