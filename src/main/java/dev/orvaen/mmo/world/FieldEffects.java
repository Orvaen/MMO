package dev.orvaen.mmo.world;

import dev.orvaen.mmo.gfx.GbaGfx;
import dev.orvaen.mmo.rom.Rom;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Effets de terrain du monde, lus dans la ROM. Pour l'instant : l'herbe haute qui bouge quand on y entre.
 * <p>
 * Dans le jeu, cet effet est un sprite décrit par un « modèle » de 24 octets :
 * {@code {tuiles 0xFFFF, palette 0x1005, forme, animation, images, …}}. Son animation est unique :
 * images 1, 2, 3, 4 puis 0, 10 frames chacune. On retrouve donc cette animation, puis le modèle qui
 * l'utilise, puis ses 5 images (16x16) ; la palette est celle qui porte l'étiquette 0x1005.
 */
public final class FieldEffects {

    private static final int GENERAL_PALETTE_TAG = 0x1005;
    private static final String TALL_GRASS_ANIM = "01000A0002000A0003000A0004000A0000000A00";
    private static final int FRAME_BYTES = 0x80;              // 16x16 en 4 bits

    /** Images de l'herbe haute (0 : au repos, 1 à 4 : elle s'agite), ou null. */
    public final BufferedImage[] tallGrass;

    public FieldEffects(Rom rom) {
        BufferedImage[] grass = null;
        try {
            grass = readTallGrass(rom);
        } catch (RuntimeException ignored) {
            // ROM modifiée : pas d'herbe animée
        }
        this.tallGrass = grass;
    }

    /** Image à afficher {@code age} frames après l'entrée dans l'herbe (comme l'animation du jeu). */
    public static int tallGrassFrame(int age) {
        int step = age / 10;
        return step < 4 ? step + 1 : 0;
    }

    private static BufferedImage[] readTallGrass(Rom rom) {
        int anim = rom.find(TALL_GRASS_ANIM);
        if (anim < 0) return null;
        int animTable = findPointerTo(rom, anim, 0);
        while (animTable >= 0) {
            // le modèle de sprite : {0xFFFF, 0x1005, forme, animations, images…}
            int template = findTemplate(rom, animTable);
            if (template >= 0) return readFrames(rom, rom.pointer(template + 12));
            animTable = findPointerTo(rom, anim, animTable + 4);
        }
        return null;
    }

    private static int findTemplate(Rom rom, int animTable) {
        for (int ref = findPointerTo(rom, animTable, 0); ref >= 0; ref = findPointerTo(rom, animTable, ref + 4)) {
            int t = ref - 8;
            if (t >= 0 && rom.u16(t) == 0xFFFF && rom.u16(t + 2) == GENERAL_PALETTE_TAG && rom.pointer(t + 12) >= 0) {
                return t;
            }
        }
        return -1;
    }

    private static BufferedImage[] readFrames(Rom rom, int images) {
        int[] pal = findPalette(rom);
        if (images < 0 || pal == null) return null;
        BufferedImage[] out = new BufferedImage[5];
        for (int f = 0; f < 5; f++) {
            int data = rom.pointer(images + f * 8);
            if (data < 0 || rom.u16(images + f * 8 + 4) != FRAME_BYTES) return null;
            out[f] = GbaGfx.sprite(rom.slice(data, FRAME_BYTES), 2, 2, pal);
        }
        return out;
    }

    /** Palette {pointeur, 0x1005} des effets de terrain. */
    private static int[] findPalette(Rom rom) {
        for (int o = 0; o + 8 <= rom.size(); o += 4) {
            if (rom.u16(o + 4) != GENERAL_PALETTE_TAG || rom.u16(o + 6) != 0) continue;
            int p = rom.pointer(o);
            if (p < 0 || !rom.inRange(p, 32)) continue;
            boolean ok = true;
            for (int i = 0; i < 16 && ok; i++) ok = (rom.u16(p + i * 2) & 0x8000) == 0;
            if (ok) return GbaGfx.readPalettes(rom, p, 1);
        }
        return null;
    }

    /** Prochain endroit (aligné sur 4, à partir de {@code from}) qui contient un pointeur vers {@code target}. */
    private static int findPointerTo(Rom rom, int target, int from) {
        int v = 0x0800_0000 + target;
        byte[] needle = {(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)};
        for (int o = rom.find(needle, from); o >= 0; o = rom.find(needle, o + 1)) {
            if ((o & 3) == 0) return o;
        }
        return -1;
    }

    /** Copie défensive pour les tests. */
    public BufferedImage[] tallGrassFrames() {
        return tallGrass == null ? null : Arrays.copyOf(tallGrass, tallGrass.length);
    }
}
