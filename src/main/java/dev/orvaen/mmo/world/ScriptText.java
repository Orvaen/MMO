package dev.orvaen.mmo.world;

import dev.orvaen.mmo.rom.Rom;
import dev.orvaen.mmo.rom.TextCodec;

import java.util.List;

/**
 * Texte qu'un personnage dit quand on lui parle, retrouvé dans son script.
 * <p>
 * Le moteur n'exécute pas (encore) les scripts : il cherche le premier message affiché.
 * <ul>
 *   <li>{@code msgbox} = {@code loadword 0, texte} (0x0F 0x00 ptr) suivi d'un {@code callstd} ;</li>
 *   <li>dresseur : {@code trainerbattle} (0x5C), texte d'intro 6 octets plus loin.</li>
 * </ul>
 */
public final class ScriptText {

    private static final int LOADWORD = 0x0F;
    private static final int TRAINERBATTLE = 0x5C;
    private static final int SCAN = 160;
    private static final int MAX_TEXT = 1024;

    private ScriptText() {}

    /** Pages de dialogue, ou liste vide si le script n'affiche pas de texte simple. */
    public static List<String> firstMessage(Rom rom, int script, String player, String rival) {
        if (script < 0 || !rom.inRange(script, 8)) return List.of();
        if (rom.u8(script) == TRAINERBATTLE) {
            List<String> intro = text(rom, rom.pointer(script + 6), player, rival);
            if (!intro.isEmpty()) return intro;
        }
        int end = Math.min(rom.size() - 6, script + SCAN);
        for (int o = script; o < end; o++) {
            if (rom.u8(o) != LOADWORD || rom.u8(o + 1) != 0) continue;
            List<String> t = text(rom, rom.pointer(o + 2), player, rival);
            if (!t.isEmpty()) return t;
        }
        return List.of();
    }

    private static List<String> text(Rom rom, int ptr, String player, String rival) {
        if (ptr < 0) return List.of();
        List<String> pages = TextCodec.dialogue(rom, ptr, MAX_TEXT, player, rival);
        int letters = 0;
        for (String p : pages) for (char c : p.toCharArray()) if (Character.isLetter(c)) letters++;
        return letters >= 2 ? pages : List.of();
    }
}
