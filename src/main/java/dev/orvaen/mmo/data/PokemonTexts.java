package dev.orvaen.mmo.data;

import dev.orvaen.mmo.rom.Rom;
import dev.orvaen.mmo.rom.TextCodec;

/**
 * Textes du résumé d'un Pokémon lus dans la ROM du joueur (donc dans la langue de sa ROM) :
 * noms et descriptions des talents, noms des natures.
 * <p>
 * Retrouvés par leur forme, comme le reste :
 * <ul>
 *   <li><b>talents</b> : 78 noms de 13 octets (le premier est « ------- »), juste après la table des 78
 *       pointeurs vers leurs descriptions ;</li>
 *   <li><b>natures</b> : 25 pointeurs vers 25 noms courts rangés les uns à la suite des autres, juste avant
 *       la table.</li>
 * </ul>
 */
public final class PokemonTexts {

    public static final int ABILITY_COUNT = 78;
    public static final int NATURE_COUNT = 25;
    private static final int NAME_LENGTH = 13;
    private static final int DASH = 0xAE;            // « - » dans le jeu de caractères Gen 3

    private final String[] abilities = new String[ABILITY_COUNT];
    private final String[] descriptions = new String[ABILITY_COUNT];
    private final String[] natures = new String[NATURE_COUNT];
    private final boolean abilitiesFound, naturesFound;

    public PokemonTexts(Rom rom) {
        abilitiesFound = readAbilities(rom);
        naturesFound = readNatures(rom);
    }

    public boolean abilitiesFound() {
        return abilitiesFound;
    }

    public boolean naturesFound() {
        return naturesFound;
    }

    /** Nom du talent (« Engrais »), ou « — ». */
    public String ability(int id) {
        String s = id > 0 && id < ABILITY_COUNT ? abilities[id] : null;
        return s == null || s.isBlank() ? "—" : s;
    }

    /** Description courte du talent, ou "". */
    public String abilityDescription(int id) {
        String s = id >= 0 && id < ABILITY_COUNT ? descriptions[id] : null;
        return s == null ? "" : s;
    }

    /** Nom de la nature (0 à 24). */
    public String nature(int n) {
        String s = n >= 0 && n < NATURE_COUNT ? natures[n] : null;
        return s == null || s.isBlank() ? "Nature " + (n + 1) : s;
    }

    // ------------------------------------------------------------------ talents

    private boolean readAbilities(Rom rom) {
        for (int o = 0; o + NAME_LENGTH * ABILITY_COUNT <= rom.size(); o += 4) {
            if (!dashes(rom, o)) continue;
            int n = 1;
            while (n < ABILITY_COUNT && name(rom, o + n * NAME_LENGTH)) n++;
            if (n < ABILITY_COUNT) continue;
            // la table des attaques a la même forme (355 noms) : un talent de plus et ce n'est pas la bonne
            if (name(rom, o + ABILITY_COUNT * NAME_LENGTH)) continue;
            for (int i = 1; i < ABILITY_COUNT; i++) abilities[i] = pretty(TextCodec.read(rom, o + i * NAME_LENGTH, NAME_LENGTH));
            int table = o - ABILITY_COUNT * 4;               // pointeurs vers les descriptions, juste avant
            if (table >= 0) {
                for (int i = 0; i < ABILITY_COUNT; i++) {
                    int d = rom.pointer(table + i * 4);
                    if (d < 0) break;
                    descriptions[i] = TextCodec.read(rom, d, 64);
                }
            }
            return true;
        }
        return false;
    }

    /** « ------- » terminé par 0xFF, complété de zéros. */
    private static boolean dashes(Rom rom, int o) {
        for (int i = 0; i < 7; i++) if (rom.u8(o + i) != DASH) return false;
        if (rom.u8(o + 7) != TextCodec.TERMINATOR) return false;
        for (int i = 8; i < NAME_LENGTH; i++) if (rom.u8(o + i) != 0) return false;
        return true;
    }

    /** Un nom de 13 octets : au moins une lettre, 0xFF, puis des zéros jusqu'au bout. */
    private static boolean name(Rom rom, int o) {
        if (!rom.inRange(o, NAME_LENGTH)) return false;
        int end = -1;
        for (int i = 0; i < NAME_LENGTH; i++) {
            if (rom.u8(o + i) == TextCodec.TERMINATOR) {
                end = i;
                break;
            }
        }
        if (end < 1) return false;
        for (int i = end + 1; i < NAME_LENGTH; i++) if (rom.u8(o + i) != 0) return false;
        for (int i = 0; i < end; i++) {
            int c = rom.u8(o + i);
            if (c >= 0xF7 || (c != 0 && TextCodec.decodeChar(c) == null)) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ natures

    private boolean readNatures(Rom rom) {
        for (int o = 0; o + NATURE_COUNT * 4 <= rom.size(); o += 4) {
            int first = rom.pointer(o);
            if (first < 0 || first >= o || o - first > 400) continue;
            int at = first;
            boolean ok = true;
            String[] found = new String[NATURE_COUNT];
            for (int i = 0; i < NATURE_COUNT && ok; i++) {
                int p = rom.pointer(o + i * 4);
                // les noms se suivent (à quelques octets d'alignement près)
                if (p < at || p > at + 3 || p >= o) {
                    ok = false;
                    break;
                }
                int len = 0;
                while (len < 13 && rom.u8(p + len) != TextCodec.TERMINATOR) {
                    int c = rom.u8(p + len);
                    if (c == 0 || c >= 0xF7 || TextCodec.decodeChar(c) == null) ok = false;
                    len++;
                }
                if (len < 3 || len >= 13) ok = false;
                found[i] = TextCodec.read(rom, p, 13);
                at = p + len + 1;
            }
            if (!ok || o - at > 3) continue;                 // la table suit directement le dernier nom
            for (int i = 0; i < NATURE_COUNT; i++) natures[i] = pretty(found[i]);
            return true;
        }
        return false;
    }

    /** « SPEED BOOST » → « Speed Boost », « TÉNACITÉ » → « Ténacité » (comme l'affiche PokeMMO). */
    public static String pretty(String s) {
        if (s == null) return null;
        StringBuilder b = new StringBuilder(s.length());
        boolean start = true;
        for (char c : s.toCharArray()) {
            b.append(start ? Character.toUpperCase(c) : Character.toLowerCase(c));
            start = c == ' ' || c == '-' || c == '.';
        }
        return b.toString();
    }
}
