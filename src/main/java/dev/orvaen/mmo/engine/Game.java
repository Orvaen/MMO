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
    private BufferedImage screen = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    private boolean screenFills;
    private int zoomSetting = Storage.zoom(); // 0 = automatique
    private int viewWidth = WIDTH * 4;
    private int viewHeight = HEIGHT * 4;
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
        canvas.setPreferredSize(new Dimension(WIDTH * 4, HEIGHT * 4));
        canvas.setBackground(Color.BLACK);
        canvas.addKeyListener(input);
        canvas.addMouseListener(input.mouse());
        canvas.addMouseMotionListener(input.mouse());
        canvas.addMouseWheelListener(input.mouse());
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
        WindowsTheme.darkTitleBar(frame);
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

    private Backdrop backdrop;

    /** Fond animé des écrans d'accueil, construit une fois à partir de la ROM. */
    public Backdrop backdrop() {
        if (backdrop == null) backdrop = new Backdrop(data);
        return backdrop;
    }

    /** Ferme le jeu proprement (sauvegarde comprise) à la fin de la frame. */
    public void quit() {
        running = false;
    }

    /** Zoom du monde : réglé par le joueur, ou automatique selon la hauteur de la fenêtre. */
    public int zoom() {
        if (zoomSetting > 0) return zoomSetting;
        return Math.max(2, Math.min(6, (int) Math.round(viewHeight / 360.0)));
    }

    /** Zoom suivant / précédent (touches + et -, Ctrl + molette), mémorisé. */
    public void adjustZoom(int delta) {
        int z = Math.max(1, Math.min(8, zoom() + delta));
        zoomSetting = z;
        Storage.setZoom(z);
    }

    private int blitX, blitY, blitScale = 1;

    /** Position et échelle de l'écran virtuel dans la fenêtre lors du dernier rendu (pour l'interface). */
    public int blitX() {
        return blitX;
    }

    public int blitY() {
        return blitY;
    }

    public int blitScale() {
        return blitScale;
    }

    /** Taille de l'écran virtuel où dessinent les scènes (240x160, ou plus grand pour le monde). */
    public int screenWidth() {
        return screen.getWidth();
    }

    public int screenHeight() {
        return screen.getHeight();
    }

    /** Taille de la fenêtre de jeu en pixels (interface haute résolution, souris). */
    public int viewWidth() {
        return viewWidth;
    }

    public int viewHeight() {
        return viewHeight;
    }

    /** Dessine une frame complète (jeu + interface haute résolution) à la taille donnée (tests, captures). */
    public BufferedImage renderWindow(int width, int height) {
        viewWidth = width;
        viewHeight = height;
        drawScreen();
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        composite(g, width, height);
        g.dispose();
        return img;
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
        preciseTimer();

        // La logique avance à 60 pas/s (comme la GBA) ; l'affichage suit la fréquence de l'écran
        // (60, 144 Hz…) avec une position interpolée entre deux pas : pas de saccade.
        int refresh = displayRefreshRate();
        long frameNanos = 1_000_000_000L / refresh;
        long nextTick = System.nanoTime(), nextFrame = nextTick, lastTick = nextTick;
        while (running) {
            long now = System.nanoTime();
            int ticks = 0;
            while (now >= nextTick && ticks++ < 5) {
                tick();
                lastTick = nextTick;
                nextTick += FRAME_NANOS;
            }
            if (now - nextTick > FRAME_NANOS * 10) { // rattrapage après une pause (fenêtre déplacée…)
                nextTick = now + FRAME_NANOS;
                lastTick = now;
            }
            if (now >= nextFrame) {
                interpolation = Math.max(0, Math.min(1, (now - lastTick) / (double) FRAME_NANOS));
                draw();
                nextFrame += frameNanos;
                if (now - nextFrame > frameNanos * 4) nextFrame = now + frameNanos;
            }
            waitUntil(Math.min(nextTick, nextFrame));
        }
        save.store();
        frame.dispose();
        System.exit(0);
    }

    /** Fraction (0 à 1) écoulée depuis le dernier pas de logique, pour lisser les mouvements à l'écran. */
    public double interpolation() {
        return interpolation;
    }

    private double interpolation;

    /** Attente précise : on dort tant qu'il reste du temps, puis on attend activement la dernière milliseconde. */
    private static void waitUntil(long deadline) {
        while (true) {
            long left = deadline - System.nanoTime();
            if (left <= 0) return;
            if (left > 2_000_000) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } else {
                Thread.onSpinWait();
            }
        }
    }

    /**
     * Sous Windows, la minuterie système n'a qu'une précision d'environ 15 ms : Thread.sleep(1) peut
     * durer 15 ms et faire sauter des images. Un fil qui dort indéfiniment force la JVM à demander
     * la minuterie à 1 ms pendant toute la partie (astuce classique de la JVM sous Windows).
     */
    private static void preciseTimer() {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException ignored) {
                // fin du jeu
            }
        }, "minuterie-precise");
        t.setDaemon(true);
        t.start();
    }

    /** Fréquence de l'écran où se trouve la fenêtre (60 par défaut, 240 au plus). */
    private int displayRefreshRate() {
        try {
            int r = frame.getGraphicsConfiguration().getDevice().getDisplayMode().getRefreshRate();
            if (r >= 30) return Math.min(240, r);
        } catch (RuntimeException ignored) {
            // inconnue : 60
        }
        return 60;
    }

    private void tick() {
        input.poll();
        if (input.zoomSteps() != 0) adjustZoom(input.zoomSteps());
        sizeScreen();
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
            viewWidth = canvas.getWidth();
            viewHeight = canvas.getHeight();
            composite(out, viewWidth, viewHeight);
        } finally {
            out.dispose();
        }
        bs.show();
        java.awt.Toolkit.getDefaultToolkit().sync();
    }

    /** Image du jeu agrandie (pixels nets), puis interface haute résolution, puis fondu. */
    private void composite(Graphics2D out, int cw, int ch) {
        out.setColor(Color.BLACK);
        out.fillRect(0, 0, cw, ch);
        int scale = screenFills ? zoom() : Math.max(1, Math.min(cw / WIDTH, ch / HEIGHT));
        int w = screen.getWidth() * scale, h = screen.getHeight() * scale;
        // Défilement au pixel de l'écran près : la scène indique la fraction de pixel virtuel de sa caméra
        Scene base = visibleScenes().isEmpty() ? null : visibleScenes().get(0);
        double fx = base != null && screenFills ? base.subpixelX() : 0, fy = base != null && screenFills ? base.subpixelY() : 0;
        blitX = (int) Math.round((cw - w) / 2.0 - fx * scale);
        blitY = (int) Math.round((ch - h) / 2.0 - fy * scale);
        blitScale = scale;
        out.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        out.drawImage(screen, blitX, blitY, w, h, null);

        for (Scene s : visibleScenes()) {
            Graphics2D g = (Graphics2D) out.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            s.renderHiRes(g, cw, ch);
            g.dispose();
        }
        if (fade > 0) {
            out.setColor(new Color(0, 0, 0, Math.min(1f, fade)));
            out.fillRect(0, 0, cw, ch);
        }
    }

    private List<Scene> visibleScenes() {
        List<Scene> visible = new ArrayList<>();
        for (Scene s : scenes) {
            visible.add(0, s);
            if (!s.isOverlay()) break;
        }
        return visible;
    }

    /** Écran virtuel à la bonne taille : celle de la fenêtre / zoom pour le monde, 240x160 sinon. */
    private void sizeScreen() {
        List<Scene> visible = visibleScenes();
        screenFills = !visible.isEmpty() && visible.get(0).fillsWindow();
        int w = WIDTH, h = HEIGHT;
        if (screenFills) {
            int z = zoom();
            w = Math.max(WIDTH, (viewWidth + z - 1) / z + 2);   // +2 : marge pour le défilement au sous-pixel
            h = Math.max(HEIGHT, (viewHeight + z - 1) / z + 2);
        }
        if (screen.getWidth() != w || screen.getHeight() != h) {
            screen = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        }
    }

    private void drawScreen() {
        sizeScreen();
        Graphics2D g = screen.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, screen.getWidth(), screen.getHeight());

        // On dessine depuis la dernière scène opaque jusqu'au sommet de la pile.
        for (Scene s : visibleScenes()) {
            g.setFont(Ui.FONT);
            s.render(g);
        }
        g.dispose();
    }
}
