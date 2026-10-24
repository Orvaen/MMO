package dev.orvaen.mmo.rom;

/**
 * Décodeur du jeu de caractères Gen 3 (versions occidentales).
 * Table générée à partir du charmap de la décompilation pret.
 */
public final class TextCodec {

    public static final int TERMINATOR = 0xFF;
    private static final int NEWLINE = 0xFE;
    private static final int PLACEHOLDER = 0xFD;
    private static final int CONTROL = 0xFC;

    private static final String[] TABLE = {
            " ", "À", "Á", "Â", "Ç", "È", "É", "Ê", "Ë", "Ì", "こ", "Î", "Ï", "Ò", "Ó", "Ô",  // 00
            "Œ", "Ù", "Ú", "Û", "Ñ", "ß", "à", "á", "ね", "ç", "è", "é", "ê", "ë", "ì", "ま",  // 10
            "î", "ï", "ò", "ó", "ô", "œ", "ù", "ú", "û", "ñ", "º", "ª", "わ", "&", "+", "ぁ",  // 20
            "ぃ", "ぅ", "ぇ", "ぉ", "ゃ", "=", ";", "が", "ぎ", "ぐ", "げ", "ご", "ざ", "じ", "ず", "ぜ",  // 30
            "ぞ", "だ", "ぢ", "づ", "で", "ど", "ば", "び", "ぶ", "べ", "ぼ", "ぱ", "ぴ", "ぷ", "ぺ", "ぽ",  // 40
            "っ", "¿", "¡", "ウ", "エ", "オ", "カ", "キ", "ク", "ケ", "Í", "%", "(", ")", "セ", "ソ",  // 50
            "タ", "チ", "ツ", "テ", "ト", "ナ", "ニ", "ヌ", "â", "ノ", "ハ", "ヒ", "フ", "ヘ", "ホ", "í",  // 60
            "ミ", "ム", "メ", "モ", "ヤ", "ユ", "ヨ", "ラ", "リ", "ル", "レ", "ロ", "ワ", "ヲ", "ン", "ァ",  // 70
            "ィ", "ゥ", "ェ", "ォ", "ャ", "<", ">", "ガ", "ギ", "グ", "ゲ", "ゴ", "ザ", "ジ", "ズ", "ゼ",  // 80
            "ゾ", "ダ", "ヂ", "ヅ", "デ", "ド", "バ", "ビ", "ブ", "ベ", "ボ", "パ", "ピ", "プ", "ペ", "ポ",  // 90
            "ッ", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "!", "?", ".", "-", "·",  // A0
            "…", "“", "”", "‘", "’", "♂", "♀", "¥", ",", "×", "/", "A", "B", "C", "D", "E",  // B0
            "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U",  // C0
            "V", "W", "X", "Y", "Z", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k",  // D0
            "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z", "▶",  // E0
            ":", "Ä", "Ö", "Ü", "ä", "ö", "ü", null, null, null, null, null, null, null, null, null,  // F0
    };

    private TextCodec() {}

    /** Lit une chaîne terminée par 0xFF, dans la limite de {@code maxLength} octets. */
    public static String read(Rom rom, int offset, int maxLength) {
        StringBuilder sb = new StringBuilder();
        int end = Math.min(rom.size(), offset + maxLength);
        for (int i = offset; i < end; i++) {
            int c = rom.u8(i);
            if (c == TERMINATOR) break;
            if (c == NEWLINE) {
                sb.append(' ');
            } else if (c == PLACEHOLDER) {
                i++; // ex. {PLAYER}, {RIVAL} : on ignore l'argument
            } else if (c == CONTROL) {
                i += controlArgs(i + 1 < end ? rom.u8(i + 1) : 0);
            } else if (TABLE[c] != null) {
                sb.append(TABLE[c]);
            }
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private static final int SCROLL = 0xFA;     // \l : la ligne défile
    private static final int PAGE = 0xFB;       // \p : nouvelle page

    /**
     * Lit un texte de dialogue en pages (une page = une boîte), retours à la ligne conservés.
     * {@code {PLAYER}} est remplacé par {@code player}, {@code {RIVAL}} par {@code rival}.
     */
    public static java.util.List<String> dialogue(Rom rom, int offset, int maxLength, String player, String rival) {
        return dialogue(rom, offset, maxLength, code -> code == 0x01 ? player : code == 0x06 ? rival : "");
    }

    /**
     * Idem, avec les « emplacements » du jeu remplacés par {@code placeholder} :
     * 0x01 joueur, 0x02 à 0x04 textes préparés par le script (STR_VAR_1 à 3), 0x06 rival…
     */
    public static java.util.List<String> dialogue(Rom rom, int offset, int maxLength,
                                                  java.util.function.IntFunction<String> placeholder) {
        java.util.List<String> pages = new java.util.ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int end = Math.min(rom.size(), offset + maxLength);
        for (int i = offset; i < end; i++) {
            int c = rom.u8(i);
            if (c == TERMINATOR) break;
            if (c == NEWLINE || c == SCROLL) {
                sb.append('\n');
            } else if (c == PAGE) {
                pages.add(sb.toString().trim());
                sb.setLength(0);
            } else if (c == PLACEHOLDER) {
                int arg = i + 1 < end ? rom.u8(i + 1) : 0;
                i++;
                String sub = placeholder.apply(arg);
                if (sub != null) sb.append(sub);
            } else if (c == CONTROL) {
                i += controlArgs(i + 1 < end ? rom.u8(i + 1) : 0);
            } else if (TABLE[c] != null) {
                sb.append(TABLE[c]);
            }
        }
        if (!sb.toString().isBlank()) pages.add(sb.toString().trim());
        pages.removeIf(String::isBlank);
        return pages;
    }

    public static String decodeChar(int code) {
        return TABLE[code & 0xFF];
    }

    /** Nombre d'octets à sauter après 0xFC (code + arguments). */
    private static int controlArgs(int code) {
        return switch (code) {
            case 0x01, 0x02, 0x03, 0x05, 0x07, 0x08, 0x0B, 0x0C, 0x0D, 0x0E, 0x10, 0x13, 0x14 -> 2;
            case 0x04 -> 4;
            case 0x09, 0x0A, 0x0F, 0x15, 0x16, 0x17, 0x18 -> 1;
            case 0x06 -> 2;
            default -> 1;
        };
    }
}
