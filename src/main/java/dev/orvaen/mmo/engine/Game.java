package dev.orvaen.mmo.engine;

import dev.orvaen.mmo.data.GameData;

import javax.swing.JFrame;
import javax.swing.WindowConstants;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

/** Fenêtre, boucle à 60 images/s et pile de scènes. */
public final class Game {

    public static final int WIDTH = 240;
    public static final int HEIGHT = 160;
    private static final long FRAME_NANOS = 1_000_000_000L / 60;

    public final GameData data;
    public final Storage.Save save;
    private final Input input = new Input();
    private final Deque<Scene> scenes = new ArrayDeque<>();
    private final BufferedImage screen = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    private JFrame frame;
    private Canvas canvas;
    private volatile boolean running = true;

    // Fondu au noir : alpha monte jusqu'à 1, l'action s'exécute, puis redescend.
    private float fade;
    private int fadeDir;
    private Runnable fadeAction;

    public Game(GameData data) {
        this.data = data;
        this.save = Storage.Save.load(data.rom.gameCode());
    }

    private void createWindow() {
        canvas = new Canvas();
        canvas.setPreferredSize(new Dimension(WIDTH * 3, HEIGHT * 3));
        canvas.setBackground(Color.BLACK);
        canvas.addKeyListener(input);
        canvas.setFocusTraversalKeysEnabled(false);

        frame = new JFrame("Orvaen MMO — " + data.rom.profile().displayName(data.rom.gameCode()));
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setIconImages(java.util.List.of(RomLauncher.appIcon(16), RomLauncher.appIcon(32), RomLauncher.appIcon(64)));
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                running = false;
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                input.releaseAll();
            }
        });
        frame.add(canvas);
        frame.pack();
        frame.setLocationRelativeTo(null);
    }

    /** Clavier du jeu (exposé pour les tests et un futur client réseau). */
    public Input input() {
        return input;
    }

    public Scene top() {
        return scenes.peek();
    }

    /** Avance d'une frame sans fenêtre (tests, serveur, outils). */
    public void simulateFrame() {
        tick();
    }

    /** Dessine une frame sur l'écran virtuel et le renvoie (tests, captures). */
    public BufferedImage renderFrame() {
        drawScreen();
        return screen;
    }

    public void push(Scene scene) {
        scenes.push(scene);
        scene.onEnter();
    }

    public void pop() {
        scenes.pop();
        if (!scenes.isEmpty()) scenes.peek().onEnter();
    }

    public void replace(Scene scene) {
        scenes.pop();
        push(scene);
    }

    /** Fondu au noir, exécution de l'action à mi-parcours, puis retour de l'image. */
    public void fadeThen(Runnable action) {
        if (fadeDir != 0) return;
        fadeAction = action;
        fadeDir = 1;
    }

    public boolean isFading() {
        return fadeDir != 0;
    }

    public void run(Supplier<Scene> first) {
        createWindow();
        frame.setVisible(true);
        canvas.createBufferStrategy(2);
        canvas.requestFocus();
        push(first.get());

        long next = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            if (now < next) {
                long sleep = (next - now) / 1_000_000;
                if (sleep > 1) {
                    try {
                        Thread.sleep(sleep - 1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    Thread.onSpinWait();
                }
                continue;
            }
            int steps = 0;
            while (now >= next && steps++ < 5) {
                tick();
                next += FRAME_NANOS;
            }
            if (now - next > FRAME_NANOS * 10) next = now; // rattrapage après une pause
            draw();
        }
        save.store();
        frame.dispose();
        System.exit(0);
    }

    private void tick() {
        input.poll();
        if (fadeDir != 0) {
            fade += fadeDir * 0.1f;
            if (fade >= 1f) {
                fade = 1f;
                if (fadeAction != null) {
                    Runnable a = fadeAction;
                    fadeAction = null;
                    a.run();
                }
                fadeDir = -1;
            } else if (fade <= 0f) {
                fade = 0f;
                fadeDir = 0;
            }
            return;
        }
        if (!scenes.isEmpty()) scenes.peek().update(input);
    }

    private void draw() {
        drawScreen();
        BufferStrategy bs = canvas.getBufferStrategy();
        if (bs == null) return;
        Graphics2D out = (Graphics2D) bs.getDrawGraphics();
        try {
            int cw = canvas.getWidth(), ch = canvas.getHeight();
            out.setColor(Color.BLACK);
            out.fillRect(0, 0, cw, ch);
            int scale = Math.max(1, Math.min(cw / WIDTH, ch / HEIGHT));
            int w = WIDTH * scale, h = HEIGHT * scale;
            out.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            out.drawImage(screen, (cw - w) / 2, (ch - h) / 2, w, h, null);
        } finally {
            out.dispose();
        }
        bs.show();
    }

    private void drawScreen() {
        Graphics2D g = screen.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // On dessine depuis la dernière scène opaque jusqu'au sommet de la pile.
        List<Scene> visible = new ArrayList<>();
        for (Scene s : scenes) {
            visible.add(0, s);
            if (!s.isOverlay()) break;
        }
        for (Scene s : visible) {
            g.setFont(Ui.FONT);
            s.render(g);
        }
        if (fade > 0) {
            g.setColor(new Color(0, 0, 0, Math.min(1f, fade)));
            g.fillRect(0, 0, WIDTH, HEIGHT);
        }
        g.dispose();
    }
}
