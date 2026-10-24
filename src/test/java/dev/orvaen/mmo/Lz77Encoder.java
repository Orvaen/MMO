package dev.orvaen.mmo;

import java.io.ByteArrayOutputStream;

/** Compresseur LZ77 (format BIOS GBA 0x10), utilisé uniquement pour fabriquer des données de test. */
public final class Lz77Encoder {

    private Lz77Encoder() {}

    public static byte[] encode(byte[] in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x10);
        out.write(in.length & 0xFF);
        out.write((in.length >> 8) & 0xFF);
        out.write((in.length >> 16) & 0xFF);

        int pos = 0;
        while (pos < in.length) {
            int flags = 0;
            ByteArrayOutputStream block = new ByteArrayOutputStream();
            for (int bit = 7; bit >= 0 && pos < in.length; bit--) {
                int bestLen = 0, bestDisp = 0;
                for (int disp = 1; disp <= Math.min(4096, pos); disp++) {
                    int len = 0;
                    while (len < 18 && pos + len < in.length && in[pos + len] == in[pos - disp + len]) len++;
                    if (len > bestLen) {
                        bestLen = len;
                        bestDisp = disp;
                        if (len == 18) break;
                    }
                }
                if (bestLen >= 3) {
                    flags |= 1 << bit;
                    int d = bestDisp - 1;
                    block.write(((bestLen - 3) << 4) | (d >> 8));
                    block.write(d & 0xFF);
                    pos += bestLen;
                } else {
                    block.write(in[pos++]);
                }
            }
            out.write(flags);
            out.writeBytes(block.toByteArray());
        }
        while (out.size() % 4 != 0) out.write(0);
        return out.toByteArray();
    }
}
