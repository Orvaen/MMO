package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.data.PlayerLook;
import dev.orvaen.mmo.player.CosmeticMasks;
import dev.orvaen.mmo.player.PlayerGraphics;
import dev.orvaen.mmo.player.Recolor;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Le personnage du joueur à l'écran : sprites de la ROM recolorés selon son {@link PlayerLook},
 * ou l'avatar dessiné par le moteur si la ROM ne fournit pas de sprites exploitables.
 */
public final class PlayerSprite {

    private final PlayerGraphics gfx;
    private final PlayerLook look;
    private final Avatar fallback;
    private final Map<Integer, BufferedImage> frames = new HashMap<>();
    private BufferedImage front;
    private BufferedImage back;

    public PlayerSprite(PlayerGraphics gfx, PlayerLook look) {
        this.look = look == null ? PlayerLook.defaults() : look;
        this.gfx = gfx != null && gfx.available() ? gfx : null;
        this.fallback = new Avatar(this.look);
    }

    public boolean fromRom() {
        return gfx != null;
    }

    public PlayerLook look() {
        return look;
    }

    /**
     * Dessine le joueur sur le monde, pieds en bas de la case 16x16 dont le coin est (x, y).
     * @param step 0 immobile, 1 / 2 : pied gauche / droit en avant
     */
    public void drawOverworld(Graphics2D g, int x, int y, Direction facing, int step, int hop) {
        drawOverworld(g, x, y, facing, step, hop, false);
    }

    /** Idem, avec les frames de course (bras qui balancent, buste penché) si {@code running}. */
    public void drawOverworld(Graphics2D g, int x, int y, Direction facing, int step, int hop, boolean running) {
        if (gfx == null) {
            fallback.draw(g, x, y, facing, step, hop);
            return;
        }
        BufferedImage img = overworldFrame(facing, step, running);
        // En course, les frames de foulée ont la tête 1 px plus bas que la frame d'appui : à l'écran
        // agrandi, ce rebond fait « sauter » le personnage. On garde la tête à hauteur constante.
        int steady = running && step != 0 ? topRow(overworldFrame(facing, 0, true)) - topRow(img) : 0;
        g.drawImage(img, x, y - 16 - hop + steady, null);
    }

    private final Map<BufferedImage, Integer> tops = new java.util.IdentityHashMap<>();

    /** Première ligne non transparente d'une frame (haut de la tête). */
    private int topRow(BufferedImage img) {
        return tops.computeIfAbsent(img, im -> {
            for (int y = 0; y < im.getHeight(); y++) {
                for (int x = 0; x < im.getWidth(); x++) if (im.getRGB(x, y) >>> 24 != 0) return y;
            }
            return 0;
        });
    }

    /** Frame de marche 16x32 (exposée pour les tests et l'écran de création). */
    public BufferedImage overworldFrame(Direction facing, int step) {
        return overworldFrame(facing, step, false);
    }

    /**
     * Frame 16x32 du monde.
     * @param running frames de course de la ROM : 9 + 3 × orientation + (0 élan, 1 / 2 foulée)
     */
    public BufferedImage overworldFrame(Direction facing, int step, boolean running) {
        int base = switch (facing) {
            case DOWN -> 0;
            case UP -> 1;
            case LEFT, RIGHT -> 2;
        };
        boolean run = running && gfx != null && gfx.hasRunFrames(look.body());
        int frame = run ? PlayerGraphics.RUN_FIRST + base * 3 + step
                : step == 0 ? base : 3 + base * 2 + (step - 1);
        boolean flip = facing == Direction.RIGHT;
        int key = frame * 2 + (flip ? 1 : 0);
        return frames.computeIfAbsent(key, k -> {
            if (gfx == null) return drawFallback(facing, step, 16, 32, 1);
            int body = look.body();
            int facingBit = switch (base) {
                case 0 -> CosmeticMasks.SOUTH;
                case 1 -> CosmeticMasks.NORTH;
                default -> CosmeticMasks.WEST;
            };
            List<CosmeticMasks.Rule> rules = body == PlayerGraphics.GIRL
                    ? CosmeticMasks.OVERWORLD_GIRL : CosmeticMasks.OVERWORLD_BOY;
            // Les frames de pas sont décalées d'1 px par rapport aux masques ; celles de course non
            int shift = frame >= 3 && frame < PlayerGraphics.RUN_FIRST ? 1 : 0;
            return Recolor.render(gfx.overworld(body, frame), rules, look, facingBit, shift, flip);
        });
    }

    /** Portrait de face 64x64 (écran de création, carte de dresseur). */
    public BufferedImage front() {
        if (front == null) {
            if (gfx == null) {
                front = drawFallback(Direction.DOWN, 0, 64, 64, 3);
            } else {
                boolean girl = look.body() == PlayerGraphics.GIRL;
                front = Recolor.render(gfx.front(look.body()),
                        girl ? CosmeticMasks.FRONT_GIRL : CosmeticMasks.FRONT_BOY, look, CosmeticMasks.ALL, 0, false);
            }
        }
        return front;
    }

    /** Vue de dos 64x64 (combat). */
    public BufferedImage back() {
        if (back == null) {
            if (gfx == null) {
                back = drawFallback(Direction.UP, 0, 64, 64, 3);
            } else {
                boolean girl = look.body() == PlayerGraphics.GIRL;
                back = Recolor.render(gfx.back(look.body()),
                        girl ? CosmeticMasks.BACK_GIRL : CosmeticMasks.BACK_BOY, look, CosmeticMasks.ALL, 0, false);
            }
        }
        return back;
    }

    private BufferedImage drawFallback(Direction facing, int step, int w, int h, int scale) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.translate((w - 16 * scale) / 2.0, h - 20 * scale);
        g.scale(scale, scale);
        fallback.draw(g, 0, 4, facing, step, 0);
        g.dispose();
        return img;
    }
}
