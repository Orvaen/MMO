package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.data.TitleGraphics;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * L'image animée de l'écran titre, comme sur la console : cadre, flammes qui montent, Dracaufeu,
 * logo avec son reflet, « PRESS START » qui clignote.
 * <p>
 * L'image est composée en 240x160 (la taille de l'écran GBA), puis agrandie à la taille de la fenêtre.
 * Les règles d'animation (vitesses, délais) viennent du jeu d'origine.
 */
final class TitleArt {

    /** Abscisses fixes des 15 flammes d'une vague (valeurs du jeu). */
    private static final int[] FLAME_X = {4, 16, 26, 32, 48, 200, 216, 224, 232, 60, 76, 92, 108, 128, 144};
    private static final int FLAME_EVERY = 18;          // une vague de flammes toutes les 18 frames
    private static final int SLASH_EVERY = 540;         // un reflet sur le logo toutes les 9 secondes
    private static final int FADE_IN = 40;              // apparition depuis le blanc au lancement

    /** Une flamme : position et vitesse en 1/16 de pixel, comme dans le jeu. */
    private static final class Flame {
        int x16, y16, speedX, speedY, age;
    }

    private final TitleGraphics gfx;
    private final BufferedImage screen = new BufferedImage(TitleGraphics.WIDTH, TitleGraphics.HEIGHT,
            BufferedImage.TYPE_INT_RGB);
    private final List<Flame> flames = new ArrayList<>();
    private final Random random = new Random(30840);
    private int frame;
    private int flameOffset;
    private int slashTimer = SLASH_EVERY, slashX = -32, slashY = 27;
    private boolean slashActive;

    TitleArt(TitleGraphics gfx) {
        this.gfx = gfx;
    }

    boolean available() {
        return gfx.available();
    }

    /** Avance l'animation d'une frame (1/60 s). */
    void update() {
        frame++;
        updateFlames();
        updateSlash();
    }

    // ------------------------------------------------------------------ flammes

    private void updateFlames() {
        if (gfx.flames == null) return;
        if (frame % FLAME_EVERY == 0) {
            int y = 116 + random.nextInt(3);
            for (int x : FLAME_X) spawnFlame(x + flameOffset, y);
            spawnFlame(random.nextInt(TitleGraphics.WIDTH), y);
            flameOffset = (flameOffset + 1) % 4;
        }
        for (Iterator<Flame> it = flames.iterator(); it.hasNext(); ) {
            Flame f = it.next();
            f.x16 -= f.speedX;
            f.y16 += f.speedY;
            f.age++;
            int x = f.x16 >> 4, y = f.y16 >> 4;
            if (x < -8 || y < 16 || y > 200 || flameImage(f) < 0) it.remove();
        }
    }

    private void spawnFlame(int x, int y) {
        Flame f = new Flame();
        f.x16 = x * 16;
        f.y16 = y * 16;
        f.speedX = random.nextInt(4) - 2;      // -2..1 : légère dérive sur le côté
        f.speedY = random.nextInt(8) - 16;     // -16..-9 : monte d'environ 1 pixel par frame
        flames.add(f);
    }

    /** Image de l'animation selon l'âge : la 1re dure 3 frames, les suivantes 6 ; -1 quand c'est fini. */
    private int flameImage(Flame f) {
        int i = f.age < 3 ? 0 : 1 + (f.age - 3) / 6;
        int count = Math.min(10, gfx.flames.length);
        return i < count ? i : -1;
    }

    // ------------------------------------------------------------------ reflet du logo

    private void updateSlash() {
        if (gfx.slash == null) return;
        if (!slashActive) {
            if (--slashTimer <= 0) {
                slashActive = true;
                slashX = -32;
                slashY = 27;
            }
            return;
        }
        slashX += 9;
        if (slashX == 67) slashY -= 7;
        if (slashX == 148) slashY += 7;
        if (slashX > TitleGraphics.WIDTH + 32) {
            slashActive = false;
            slashTimer = SLASH_EVERY;
        }
    }

    // ------------------------------------------------------------------ dessin

    /** « PRESS START » visible ? (1 s allumé, 0,5 s éteint, comme dans le jeu) */
    private boolean pressStartVisible() {
        return frame % 90 < 60;
    }

    /** Compose l'image 240x160 de la frame courante. */
    BufferedImage compose(boolean showPressStart) {
        Graphics2D g = screen.createGraphics();
        g.setColor(new java.awt.Color(gfx.backdrop));
        g.fillRect(0, 0, TitleGraphics.WIDTH, TitleGraphics.HEIGHT);
        if (gfx.border != null) g.drawImage(gfx.border, 0, 0, null);
        if (gfx.flames != null) {
            for (Flame f : flames) {
                int i = flameImage(f);
                if (i >= 0) g.drawImage(gfx.flames[i], (f.x16 >> 4) - 8, (f.y16 >> 4) - 8, null);
            }
        }
        boolean blinkOn = showPressStart && pressStartVisible();
        BufferedImage text = blinkOn ? gfx.pressStart : gfx.copyrightOnly;
        if (text != null) g.drawImage(text, 0, 0, null);
        hideOriginalFooter(g);
        g.drawImage(gfx.monster, 0, 0, null);
        g.drawImage(gfx.logo, 0, 0, null);
        g.dispose();
        if (slashActive) lightenLogoUnderSlash();
        if (frame < FADE_IN) whiten(1 - frame / (double) FADE_IN);
        return screen;
    }

    /** Le reflet éclaircit les pixels du logo situés sous sa forme (effet « LIGHTEN » de la GBA, force 13/16). */
    private void lightenLogoUnderSlash() {
        int left = slashX - 32, top = slashY - 32;
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int sx = left + x, sy = top + y;
                if (sx < 0 || sy < 0 || sx >= TitleGraphics.WIDTH || sy >= TitleGraphics.HEIGHT) continue;
                if (!gfx.slash[y * 64 + x] || (gfx.logo.getRGB(sx, sy) >>> 24) == 0) continue;
                screen.setRGB(sx, sy, lighten(screen.getRGB(sx, sy), 13 / 16.0));
            }
        }
    }

    private void whiten(double amount) {
        for (int y = 0; y < TitleGraphics.HEIGHT; y++) {
            for (int x = 0; x < TitleGraphics.WIDTH; x++) screen.setRGB(x, y, lighten(screen.getRGB(x, y), amount));
        }
    }

    private static int lighten(int rgb, double k) {
        int r = (rgb >> 16) & 0xFF, gr = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r += (int) ((255 - r) * k);
        gr += (int) ((255 - gr) * k);
        b += (int) ((255 - b) * k);
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }

    // ------------------------------------------------------------------ bas de l'écran

    /** Texte affiché dans la bande du bas, à la place du copyright du jeu d'origine. */
    static final String FOOTER_TEXT = "ORVAEN MMO";
    /** La bande du bas : les 10 dernières lignes de pixels de l'écran. */
    private static final int FOOTER_Y = TitleGraphics.HEIGHT - 10, FOOTER_H = 10;
    private int footerColor = -1;

    /** Couleur de la bande du bas (la plus fréquente sur sa ligne du milieu), calculée une fois. */
    private int footerColor() {
        if (footerColor == -1) {
            java.util.Map<Integer, Integer> count = new java.util.HashMap<>();
            BufferedImage layer = gfx.copyrightOnly != null ? gfx.copyrightOnly : gfx.border;
            for (int x = 0; layer != null && x < TitleGraphics.WIDTH; x++) {
                int c = layer.getRGB(x, FOOTER_Y + FOOTER_H / 2);
                if ((c >>> 24) != 0) count.merge(c, 1, Integer::sum);
            }
            footerColor = count.entrySet().stream().max(java.util.Map.Entry.comparingByValue())
                    .map(java.util.Map.Entry::getKey).orElse(0xFF000000);
        }
        return footerColor;
    }

    /** Efface le texte d'origine de la bande du bas (on garde sa couleur). */
    private void hideOriginalFooter(Graphics2D g) {
        g.setColor(new java.awt.Color(footerColor(), true));
        g.fillRect(0, FOOTER_Y, TitleGraphics.WIDTH, FOOTER_H);
    }

    /** Notre texte dans la bande du bas, net à la taille de la fenêtre, avec une ombre comme le jeu. */
    private void drawFooterText(Graphics2D g, java.awt.Rectangle r) {
        double px = r.height / (double) TitleGraphics.HEIGHT;          // taille d'un pixel GBA à l'écran
        Graphics2D t = (Graphics2D) g.create();
        t.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        t.setFont(dev.orvaen.mmo.engine.HiUi.tracked((float) (7 * px), true, 0.12f));
        java.awt.FontMetrics fm = t.getFontMetrics();
        float x = (float) (r.x + (r.width - fm.stringWidth(FOOTER_TEXT)) / 2.0);
        float y = (float) (r.y + (FOOTER_Y + FOOTER_H / 2.0) * px + (fm.getAscent() - fm.getDescent()) / 2.0);
        t.setColor(new java.awt.Color(0, 0, 0, 140));
        t.drawString(FOOTER_TEXT, x + (float) px * 0.6f, y + (float) px * 0.6f);
        t.setColor(new java.awt.Color(0xF0F0E8));
        t.drawString(FOOTER_TEXT, x, y);
        t.dispose();
    }

    private BufferedImage edges;

    /** Le décor fixe (cadre + bandes du texte), pour prolonger l'image sur les côtés. */
    private BufferedImage edges() {
        if (edges == null) {
            edges = new BufferedImage(TitleGraphics.WIDTH, TitleGraphics.HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = edges.createGraphics();
            if (gfx.border != null) g.drawImage(gfx.border, 0, 0, null);
            if (gfx.copyrightOnly != null) g.drawImage(gfx.copyrightOnly, 0, 0, null);
            hideOriginalFooter(g);
            g.dispose();
        }
        return edges;
    }

    /** Rectangle occupé par l'image 240x160 agrandie au centre d'une fenêtre w x h (proportions conservées). */
    static java.awt.Rectangle screenRect(int w, int h) {
        double scale = Math.min(w / (double) TitleGraphics.WIDTH, h / (double) TitleGraphics.HEIGHT);
        int dw = (int) Math.round(TitleGraphics.WIDTH * scale), dh = (int) Math.round(TitleGraphics.HEIGHT * scale);
        return new java.awt.Rectangle((w - dw) / 2, (h - dh) / 2, dw, dh);
    }

    /**
     * Dessine l'écran titre agrandi au centre de la fenêtre. Si la fenêtre est plus large que l'écran
     * de la GBA, le cadre de fond est prolongé sur les côtés.
     */
    void render(Graphics2D g, int w, int h, boolean showPressStart) {
        BufferedImage img = compose(showPressStart);
        java.awt.Rectangle r = screenRect(w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(java.awt.Color.BLACK);
        g.fillRect(0, 0, w, h);
        if (r.x > 0) {
            // bandes de côté : première et dernière colonne du décor (sans les flammes), étirées
            BufferedImage edges = edges();
            g.drawImage(edges, 0, r.y, r.x, r.y + r.height, 0, 0, 1, TitleGraphics.HEIGHT, null);
            g.drawImage(edges, r.x + r.width, r.y, w, r.y + r.height,
                    TitleGraphics.WIDTH - 1, 0, TitleGraphics.WIDTH, TitleGraphics.HEIGHT, null);
        }
        g.drawImage(img, r.x, r.y, r.width, r.height, null);
        drawFooterText(g, r);
    }
}
