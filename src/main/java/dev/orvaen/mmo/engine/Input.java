package dev.orvaen.mmo.engine;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.Map;

/** Clavier → boutons façon GBA. Les états sont figés une fois par frame. */
public final class Input extends KeyAdapter {

    public enum Button { UP, DOWN, LEFT, RIGHT, A, B, START, SELECT, L, R }

    private final Map<Button, Boolean> live = new EnumMap<>(Button.class);
    private final Map<Button, Integer> held = new EnumMap<>(Button.class);
    private final Map<Button, Boolean> pressed = new EnumMap<>(Button.class);
    private final StringBuilder pendingTyped = new StringBuilder();
    private String frameTyped = "";

    public Input() {
        for (Button b : Button.values()) {
            live.put(b, false);
            held.put(b, 0);
            pressed.put(b, false);
        }
    }

    private static Button map(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.VK_UP, KeyEvent.VK_Z -> Button.UP;
            case KeyEvent.VK_DOWN, KeyEvent.VK_S -> Button.DOWN;
            case KeyEvent.VK_LEFT, KeyEvent.VK_Q -> Button.LEFT;
            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> Button.RIGHT;
            case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE, KeyEvent.VK_K -> Button.A;
            case KeyEvent.VK_ESCAPE, KeyEvent.VK_BACK_SPACE, KeyEvent.VK_SHIFT, KeyEvent.VK_L -> Button.B;
            case KeyEvent.VK_X -> Button.START;
            case KeyEvent.VK_M -> Button.SELECT;
            case KeyEvent.VK_PAGE_UP, KeyEvent.VK_A -> Button.L;
            case KeyEvent.VK_PAGE_DOWN, KeyEvent.VK_E -> Button.R;
            default -> null;
        };
    }

    @Override
    public synchronized void keyPressed(KeyEvent e) {
        Button b = map(e.getKeyCode());
        if (b != null) live.put(b, true);
    }

    /** Caractères tapés (saisie du nom) : '\b' = effacer, '\n' = valider, '\u001b' = Échap. */
    @Override
    public synchronized void keyTyped(KeyEvent e) {
        char c = e.getKeyChar();
        if (c != KeyEvent.CHAR_UNDEFINED && pendingTyped.length() < 32) pendingTyped.append(c);
    }

    /** Saisie programmatique (tests). */
    public synchronized void type(String text) {
        pendingTyped.append(text);
    }

    @Override
    public synchronized void keyReleased(KeyEvent e) {
        Button b = map(e.getKeyCode());
        if (b != null) live.put(b, false);
    }

    /** Appui programmatique (tests, rejeu, futur contrôle à distance). */
    public synchronized void set(Button b, boolean down) {
        live.put(b, down);
    }

    /** À appeler au début de chaque frame. */
    public synchronized void poll() {
        frameTyped = pendingTyped.toString();
        pendingTyped.setLength(0);
        for (Button b : Button.values()) {
            boolean down = live.get(b);
            int h = held.get(b);
            pressed.put(b, down && h == 0);
            held.put(b, down ? h + 1 : 0);
        }
    }

    public synchronized void releaseAll() {
        for (Button b : Button.values()) live.put(b, false);
        pendingTyped.setLength(0);
    }

    /** Texte tapé pendant cette frame. */
    public String typed() {
        return frameTyped;
    }

    public boolean down(Button b) { return held.get(b) > 0; }
    public boolean pressed(Button b) { return pressed.get(b); }
    public int heldFrames(Button b) { return held.get(b); }

    /** Appui avec répétition automatique (menus). */
    public boolean repeat(Button b) {
        int h = held.get(b);
        return pressed.get(b) || (h > 18 && h % 4 == 0);
    }
}
