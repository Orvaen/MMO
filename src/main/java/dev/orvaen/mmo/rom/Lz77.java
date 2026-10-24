package dev.orvaen.mmo.rom;

/**
 * Décompresseur LZ77 du BIOS GBA (type 0x10), utilisé pour les sprites,
 * les palettes de Pokémon et certains tilesets.
 */
public final class Lz77 {

    private static final int MAX_OUTPUT = 0x40000; // garde-fou : 256 Ko

    private Lz77() {}

    public static boolean looksCompressed(Rom rom, int offset) {
        if (!rom.inRange(offset, 4) || rom.u8(offset) != 0x10) return false;
        int size = (int) (rom.u32(offset) >>> 8);
        return size > 0 && size <= MAX_OUTPUT;
    }

    /**
     * Position juste après un bloc compressé (pour lire des données rangées à la suite),
     * ou -1 si le bloc est invalide.
     */
    public static int end(Rom rom, int offset) {
        byte[] src = rom.raw();
        if (offset < 0 || offset + 4 > src.length || (src[offset] & 0xFF) != 0x10) return -1;
        int size = (src[offset + 1] & 0xFF) | (src[offset + 2] & 0xFF) << 8 | (src[offset + 3] & 0xFF) << 16;
        if (size > MAX_OUTPUT) return -1;
        int in = offset + 4, pos = 0;
        while (pos < size) {
            if (in >= src.length) return -1;
            int flags = src[in++] & 0xFF;
            for (int bit = 7; bit >= 0 && pos < size; bit--) {
                if ((flags & (1 << bit)) == 0) {
                    in++;
                    pos++;
                } else {
                    if (in + 1 >= src.length) return -1;
                    int b0 = src[in] & 0xFF, b1 = src[in + 1] & 0xFF;
                    in += 2;
                    if (((b0 & 0x0F) << 8 | b1) + 1 > pos) return -1;   // référence avant le début
                    pos += (b0 >> 4) + 3;
                }
            }
        }
        return in <= src.length ? in : -1;
    }

    public static byte[] decompress(Rom rom, int offset) {
        return decompress(rom.raw(), offset);
    }

    public static byte[] decompress(byte[] src, int offset) {
        if (offset < 0 || offset + 4 > src.length || (src[offset] & 0xFF) != 0x10) {
            throw new RomException(String.format("Données LZ77 attendues à 0x%06X.", offset));
        }
        int size = (src[offset + 1] & 0xFF) | (src[offset + 2] & 0xFF) << 8 | (src[offset + 3] & 0xFF) << 16;
        if (size > MAX_OUTPUT) {
            throw new RomException(String.format("Bloc LZ77 démesuré (%d octets) à 0x%06X.", size, offset));
        }
        byte[] out = new byte[size];
        int in = offset + 4;
        int pos = 0;
        try {
            while (pos < size) {
                int flags = src[in++] & 0xFF;
                for (int bit = 7; bit >= 0 && pos < size; bit--) {
                    if ((flags & (1 << bit)) == 0) {
                        out[pos++] = src[in++];
                    } else {
                        int b0 = src[in++] & 0xFF;
                        int b1 = src[in++] & 0xFF;
                        int length = (b0 >> 4) + 3;
                        int disp = ((b0 & 0x0F) << 8 | b1) + 1;
                        int from = pos - disp;
                        if (from < 0) {
                            throw new RomException(String.format("Référence LZ77 invalide à 0x%06X.", offset));
                        }
                        for (int i = 0; i < length && pos < size; i++) {
                            out[pos++] = out[from + i];
                        }
                    }
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new RomException(String.format("Bloc LZ77 tronqué à 0x%06X.", offset), e);
        }
        return out;
    }
}
