package dev.orvaen.mmo.data;

import dev.orvaen.mmo.gfx.GbaGfx;
import dev.orvaen.mmo.rom.Lz77;
import dev.orvaen.mmo.rom.Rom;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Images de l'accueil du Professeur, lues dans la ROM du joueur (Rouge Feu / Vert Feuille) :
 * le fond, l'estrade, les grands portraits (Professeur, rival, héros, héroïne) et la Poké Ball.
 * <p>
 * Rien n'est à une adresse fixe : chaque élément est retrouvé par la forme de ses données.
 * <ul>
 *   <li>portraits : 4 images 8 bits de 64x96 (LZ77, 0x1800 octets) précédées chacune de leur palette
 *       de 32 couleurs, rangées dans l'ordre héroïne, héros, Professeur, rival ;</li>
 *   <li>fond : 10 tuiles (0x140 octets) suivies de leur tilemap 32x20 (0x500 octets), palettes
 *       (4 x 16 couleurs) quelques kilo-octets avant ;</li>
 *   <li>estrade : 3 sprites 32x32 (0x600 octets) précédés de deux palettes de 16 couleurs ;</li>
 *   <li>Poké Ball : table des sprites de balls (entrées {ptr, 384, 55000 + n°}) et de leurs palettes.</li>
 * </ul>
 * Tout ce qui n'est pas trouvé reste {@code null} : l'accueil se dessine alors avec ses propres moyens.
 */
public final class IntroGraphics {

    private static final int PIC_BYTES = 0x1800, PIC_PAL = 64;
    private static final int BG_TILES = 0x140, BG_MAP = 0x500, BG_SHEET = 0x1400;
    private static final int PLATFORM = 0x600;
    private static final int BALL_TAG = 55000, BALL_BYTES = 384;

    public BufferedImage background;      // 240x160
    public BufferedImage professor, rival, hero, heroine;   // 64x96
    public BufferedImage[] platform;      // gauche, milieu, droite (32x32)
    public BufferedImage[] ball;          // fermée, entrouverte, ouverte (16x16)

    public IntroGraphics(Rom rom) {
        try {
            readPics(rom);
        } catch (RuntimeException ignored) {
            // ROM modifiée : portraits absents
        }
        try {
            readBackground(rom);
        } catch (RuntimeException ignored) {
            // fond absent
        }
        try {
            readPlatform(rom);
        } catch (RuntimeException ignored) {
            // estrade absente
        }
        try {
            readBall(rom);
        } catch (RuntimeException ignored) {
            // ball absente
        }
    }

    // ------------------------------------------------------------------ outils

    private static boolean lzHeader(Rom rom, int o, int size) {
        return rom.inRange(o, 4) && rom.u8(o) == 0x10 && (rom.u8(o + 1) | rom.u8(o + 2) << 8 | rom.u8(o + 3) << 16) == size;
    }

    /** Palette plausible : couleurs BGR555, bit 15 à zéro. */
    private static boolean palette(Rom rom, int o, int colors) {
        if (o < 0 || !rom.inRange(o, colors * 2)) return false;
        for (int i = 0; i < colors; i++) if ((rom.u16(o + i * 2) & 0x8000) != 0) return false;
        return true;
    }

    private static int[] readPalette(Rom rom, int o, int colors) {
        int[] p = new int[colors];
        for (int i = 0; i < colors; i++) p[i] = GbaGfx.bgr555ToArgb(rom.u16(o + i * 2));
        return p;
    }

    // ------------------------------------------------------------------ portraits 8 bits

    private void readPics(Rom rom) {
        List<Integer> found = new ArrayList<>();
        for (int o = PIC_PAL; o + 4 <= rom.size(); o += 4) {
            if (!lzHeader(rom, o, PIC_BYTES) || !palette(rom, o - PIC_PAL, 32)) continue;
            byte[] px = Lz77.decompress(rom, o);
            if (px.length == PIC_BYTES && paletteBase(px) >= 0) found.add(o);
        }
        // quatre portraits à la suite (moins de 8 Ko entre deux)
        for (int i = 0; i + 3 < found.size(); i++) {
            boolean chain = true;
            for (int k = 0; k < 3; k++) chain &= found.get(i + k + 1) - found.get(i + k) < 0x2000;
            if (!chain) continue;
            heroine = pic(rom, found.get(i));
            hero = pic(rom, found.get(i + 1));
            professor = pic(rom, found.get(i + 2));
            rival = pic(rom, found.get(i + 3));
            return;
        }
    }

    /**
     * Les portraits 8 bits utilisent une plage de 32 couleurs de la palette d'écran (le jeu les charge
     * en 4e ou 6e palette) : tous les pixels non transparents tombent dans la même plage. Renvoie son début.
     */
    private static int paletteBase(byte[] px) {
        int base = -1;
        for (byte b : px) {
            int ci = b & 0xFF;
            if (ci == 0) continue;
            int start = ci & ~31;
            if (base < 0) base = start;
            else if (start != base) return -1;
        }
        return base;
    }

    private static BufferedImage pic(Rom rom, int o) {
        byte[] px = Lz77.decompress(rom, o);
        int base = Math.max(0, paletteBase(px));
        int[] pal = readPalette(rom, o - PIC_PAL, 32);
        int w = 64, h = 96;
        int[] out = new int[w * h];
        for (int ty = 0; ty < h / 8; ty++) {
            for (int tx = 0; tx < w / 8; tx++) {
                int tile = (ty * (w / 8) + tx) * 64;
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        int ci = px[tile + y * 8 + x] & 0xFF;
                        if (ci != 0) out[(ty * 8 + y) * w + tx * 8 + x] = pal[(ci - base) & 31];
                    }
                }
            }
        }
        return GbaGfx.toImage(out, w, h);
    }

    // ------------------------------------------------------------------ fond

    private void readBackground(Rom rom) {
        for (int o = 0; o + 8 <= rom.size(); o += 4) {
            if (!lzHeader(rom, o, BG_TILES)) continue;
            byte[] tiles = Lz77.decompress(rom, o);
            // tilemap juste après (alignée sur 4 octets)
            int map = -1;
            for (int m = o + 8; m < o + 0x400 && m + 4 <= rom.size(); m += 4) {
                if (lzHeader(rom, m, BG_MAP)) {
                    map = m;
                    break;
                }
            }
            if (map < 0) continue;
            // palettes : 128 octets avant la feuille de tuiles 0x1400 qui précède
            int pal = -1;
            for (int a = o - 4; a > o - 0x6000 && a > 128; a -= 4) {
                if (lzHeader(rom, a, BG_SHEET) && palette(rom, a - 128, 64)) {
                    pal = a - 128;
                    break;
                }
            }
            if (pal < 0) continue;
            byte[] tm = Lz77.decompress(rom, map);
            int[] palettes = new int[16 * 16];
            System.arraycopy(readPalette(rom, pal, 64), 0, palettes, 0, 64);
            int[] out = new int[240 * 160];
            java.util.Arrays.fill(out, palettes[0] | 0xFF00_0000);
            for (int ty = 0; ty < 20; ty++) {
                for (int tx = 0; tx < 30; tx++) {
                    int e = (tm[(ty * 32 + tx) * 2] & 0xFF) | (tm[(ty * 32 + tx) * 2 + 1] & 0xFF) << 8;
                    GbaGfx.drawTile(out, 240, tx * 8, ty * 8, tiles, e & 0x3FF, palettes, (e >> 12) & 0xF,
                            (e & 0x400) != 0, (e & 0x800) != 0);
                }
            }
            // couleur de fond : la couleur 0 du jeu est opaque à l'écran
            for (int i = 0; i < out.length; i++) out[i] |= 0xFF00_0000;
            background = GbaGfx.toImage(out, 240, 160);
            return;
        }
    }

    // ------------------------------------------------------------------ estrade

    private void readPlatform(Rom rom) {
        for (int o = 64; o + 4 <= rom.size(); o += 4) {
            if (!lzHeader(rom, o, PLATFORM) || !palette(rom, o - 64, 16) || !palette(rom, o - 32, 16)) continue;
            byte[] tiles = Lz77.decompress(rom, o);
            if (tiles.length != PLATFORM) continue;
            int[] pal = readPalette(rom, o - 64, 16);
            platform = new BufferedImage[3];
            for (int f = 0; f < 3; f++) {
                byte[] frame = new byte[512];
                System.arraycopy(tiles, f * 512, frame, 0, 512);
                platform[f] = GbaGfx.sprite(frame, 4, 4, pal);
            }
            return;
        }
    }

    // ------------------------------------------------------------------ Poké Ball

    private void readBall(Rom rom) {
        int sheets = -1;
        for (int o = 0; o + 8 * 3 <= rom.size(); o += 4) {
            if (rom.u16(o + 4) == BALL_BYTES && rom.u16(o + 6) == BALL_TAG
                    && rom.u16(o + 12) == BALL_BYTES && rom.u16(o + 14) == BALL_TAG + 1 && rom.pointer(o) >= 0) {
                sheets = o;
                break;
            }
        }
        if (sheets < 0) return;
        int pals = -1;
        for (int o = 0; o + 8 * 3 <= rom.size(); o += 4) {
            if (rom.u16(o + 4) == BALL_TAG && rom.u16(o + 12) == BALL_TAG + 1 && rom.u16(o + 6) == 0
                    && rom.pointer(o) >= 0 && Lz77.looksCompressed(rom, rom.pointer(o))) {
                pals = o;
                break;
            }
        }
        if (pals < 0) return;
        byte[] tiles = Lz77.decompress(rom, rom.pointer(sheets));
        int[] pal = GbaGfx.palettesFromBytes(Lz77.decompress(rom, rom.pointer(pals)));
        ball = new BufferedImage[3];
        for (int f = 0; f < 3; f++) {
            byte[] frame = new byte[128];
            System.arraycopy(tiles, f * 128, frame, 0, Math.min(128, tiles.length - f * 128));
            ball[f] = GbaGfx.sprite(frame, 2, 2, pal);
        }
    }
}
