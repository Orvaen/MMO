package dev.orvaen.mmo.audio;

import dev.orvaen.mmo.rom.Rom;

/** Petits essais du moteur sonore qui ont besoin de voir l'intérieur du paquet audio. */
public final class AudioProbe {

    private AudioProbe() {
    }

    /**
     * Joue trois notes de suite sur un carré à longue relâche et renvoie le plus grand nombre de
     * notes « carré » entendues en même temps (la console n'a qu'un canal : ça doit rester 1).
     */
    public static int maxSquareVoices(byte[] synthetic) {
        byte[] rom = java.util.Arrays.copyOf(synthetic, synthetic.length + 0x100);
        int base = synthetic.length;
        int tone = base, track = base + 0x20, header = base + 0x60;
        rom[tone] = 1;                                     // carré 1
        rom[tone + 1] = 60;
        rom[tone + 4] = 2;                                 // rapport cyclique 50 %
        rom[tone + 10] = 15;                               // maintien plein
        rom[tone + 11] = 7;                                // longue relâche
        int[] cmds = {0xBD, 0, 0xBE, 127, 0xBB, 60,
                0xDB, 60, 127, 0x8C, 0xDB, 64, 127, 0x8C, 0xDB, 67, 127, 0x8C, 0xB0, 0xB1};
        for (int i = 0; i < cmds.length; i++) rom[track + i] = (byte) cmds[i];
        rom[header] = 1;
        writePtr(rom, header + 4, tone);
        writePtr(rom, header + 8, track);
        Rom r = new Rom(rom);
        SongTable table = new SongTable(r);
        int old = table.header(1);
        for (int o = 0; o + 4 <= base; o += 4) if (r.u32(o) == 0x0800_0000L + old) writePtr(rom, o, header);
        AudioEngine a = new AudioEngine(new Rom(rom));
        a.playBgm(1);
        float[] buf = new float[512];
        int max = 0;
        for (int i = 0; i < 200; i++) {
            a.renderOffline(buf, 256);
            max = Math.max(max, a.liveGameBoyVoices());
        }
        return max;
    }

    private static void writePtr(byte[] rom, int at, int offset) {
        int v = 0x0800_0000 + offset;
        for (int k = 0; k < 4; k++) rom[at + k] = (byte) (v >> (8 * k));
    }
}
