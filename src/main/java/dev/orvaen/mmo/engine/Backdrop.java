package dev.orvaen.mmo.engine;

import dev.orvaen.mmo.data.GameData;
import dev.orvaen.mmo.data.Species;
import dev.orvaen.mmo.rom.GameProfile;
import dev.orvaen.mmo.rom.RomException;
import dev.orvaen.mmo.world.Connection;
import dev.orvaen.mmo.world.GameMap;
import dev.orvaen.mmo.world.MapRenderer;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Fond animé des écrans d'accueil, construit à partir de la ROM du joueur (rien n'est fourni par le jeu) :
 * la ville de départ et ses environs en panoramique lent, de nuit, avec le duo légendaire de la cartouche
 * qui flotte de part et d'autre (Ho-Oh / Lugia pour Rouge Feu - Vert Feuille, Groudon / Kyogre pour Émeraude).
 */
public final class Backdrop {

    private static final int TILE = MapRenderer.TILE;

    private final BufferedImage panorama;
    private final BufferedImage[] legends = new BufferedImage[2];
    private final Color[] glow = new Color[2];
    private final float[][] motes = new float[48][4]; // x, y, vitesse, taille

    public Backdrop(GameData data) {
        panorama = buildPanorama(data);
        int[] duo = data.rom.profile() == GameProfile.EMERALD ? new int[]{405, 404} : new int[]{250, 249};
        for (int i = 0; i < 2; i++) {
            int id = duo[i];
            if (id < data.species.count() && !data.species.get(id).isPlaceholder()) {
                legends[i] = data.species.frontSprite(id);
                glow[i] = averageColor(legends[i]);
            }
        }
        Random rng = new Random(7);
        for (float[] m : motes) {
            m[0] = rng.nextFloat();
            m[1] = rng.nextFloat();
            m[2] = 0.0004f + rng.nextFloat() * 0.0012f;
            m[3] = 1.5f + rng.nextFloat() * 2.5f;
        }
    }

    /** Ville de départ et cartes liées, assemblées en une seule image. */
    private static BufferedImage buildPanorama(GameData data) {
        GameProfile p = data.rom.profile();
        try {
            GameMap start = data.maps.exists(p.startGroup, p.startNum)
                    ? data.maps.load(p.startGroup, p.startNum) : data.maps.load(0, 0);
            record Piece(GameMap map, int x, int y) {}
            List<Piece> pieces = new ArrayList<>();
            pieces.add(new Piece(start, 0, 0));
            for (Connection c : start.connections) {
                GameMap t;
                try {
                    t = data.maps.load(c.targetGroup(), c.targetNum());
                } catch (RomException e) {
                    continue;
                }
                int[] o = switch (c.direction()) {
                    case SOUTH -> new int[]{c.offset(), start.height()};
                    case NORTH -> new int[]{c.offset(), -t.height()};
                    case WEST -> new int[]{-t.width(), c.offset()};
                    case EAST -> new int[]{start.width(), c.offset()};
                };
                pieces.add(new Piece(t, o[0], o[1]));
            }
            int minX = 0, minY = 0, maxX = 0, maxY = 0;
            for (Piece pc : pieces) {
                minX = Math.min(minX, pc.x);
                minY = Math.min(minY, pc.y);
                maxX = Math.max(maxX, pc.x + pc.map.width());
                maxY = Math.max(maxY, pc.y + pc.map.height());
            }
            BufferedImage img = new BufferedImage((maxX - minX) * TILE, (maxY - minY) * TILE, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            for (Piece pc : pieces) {
                MapRenderer.Rendered r = data.renderer.render(pc.map);
                int x = (pc.x - minX) * TILE, y = (pc.y - minY) * TILE;
                g.drawImage(r.below(), x, y, null);
                g.drawImage(r.above(), x, y, null);
            }
            g.dispose();
            return img;
        } catch (RuntimeException e) {
            return null; // ROM sans carte lisible : fond uni
        }
    }

    private static Color averageColor(BufferedImage img) {
        long r = 0, gr = 0, b = 0, n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int c = img.getRGB(x, y);
                if (c >>> 24 == 0) continue;
                r += c >> 16 & 0xFF;
                gr += c >> 8 & 0xFF;
                b += c & 0xFF;
                n++;
            }
        }
        if (n == 0) return new Color(0x8C93E6);
        return new Color((int) (r / n), (int) (gr / n), (int) (b / n));
    }

    public boolean hasLegends() {
        return legends[0] != null || legends[1] != null;
    }

    /** Dessine le fond plein écran ; {@code frame} fait avancer l'animation (60 par seconde). */
    public void render(Graphics2D g, int w, int h, int frame) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        double t = frame / 60.0;

        // 1. Panorama de la carte, zoomé pour couvrir l'écran, qui défile lentement
        if (panorama != null) {
            double zoom = Math.max(3, Math.max(w / (double) panorama.getWidth(), h / (double) panorama.getHeight()));
            double sw = w / zoom, sh = h / zoom;
            double ax = Math.max(0, panorama.getWidth() - sw), ay = Math.max(0, panorama.getHeight() - sh);
            double k = 0.5 + 0.5 * Math.sin(t * 0.045);
            double sx = ax * k, sy = ay * (0.5 + 0.5 * Math.sin(t * 0.031 + 1.3));
            g.drawImage(panorama, 0, 0, w, h, (int) sx, (int) sy, (int) (sx + sw), (int) (sy + sh), null);
        } else {
            g.setColor(new Color(0x14151F));
            g.fillRect(0, 0, w, h);
        }

        // 2. Nuit : teinte bleue profonde et dégradé, pour que l'interface reste lisible
        g.setPaint(new GradientPaint(0, 0, new Color(10, 12, 40, 205), 0, h, new Color(6, 6, 18, 230)));
        g.fillRect(0, 0, w, h);

        // 3. Lueurs et duo légendaire
        double size = Math.min(h * 0.52, w * 0.28);
        for (int i = 0; i < 2; i++) {
            if (legends[i] == null) continue;
            double bob = Math.sin(t * 1.1 + i * Math.PI) * h * 0.012;
            double x = i == 0 ? w * 0.01 : w * 0.99 - size;
            double y = h * 0.37 + bob;
            Color c = glow[i];
            g.setPaint(new RadialGradientPaint(new Point2D.Double(x + size / 2, y + size / 2), (float) (size * 0.75),
                    new float[]{0f, 1f}, new Color[]{new Color(c.getRed(), c.getGreen(), c.getBlue(), 110),
                    new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
            g.fill(new Ellipse2D.Double(x - size * 0.25, y - size * 0.25, size * 1.5, size * 1.5));
            Graphics2D s = (Graphics2D) g.create();
            s.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.92f));
            if (i == 1) {
                s.drawImage(legends[i], (int) (x + size), (int) y, (int) -size, (int) size, null); // tourné vers le centre
            } else {
                s.drawImage(legends[i], (int) x, (int) y, (int) size, (int) size, null);
            }
            s.dispose();
        }

        // 4. Particules lumineuses qui montent
        for (float[] m : motes) {
            double y = ((m[1] - frame * m[2]) % 1 + 1) % 1;
            double x = m[0] + Math.sin(t * 0.7 + m[0] * 20) * 0.01;
            int alpha = (int) (60 + 120 * Math.sin(Math.PI * y));
            g.setColor(new Color(200, 210, 255, Math.max(0, Math.min(255, alpha))));
            g.fill(new Ellipse2D.Double(x * w, y * h, m[3], m[3]));
        }

        // 5. Vignette
        g.setPaint(new RadialGradientPaint(new Point2D.Double(w / 2.0, h / 2.0), (float) (Math.max(w, h) * 0.72),
                new float[]{0f, 0.6f, 1f}, new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 60), new Color(0, 0, 0, 190)}));
        g.fillRect(0, 0, w, h);
    }

    /** Nom affiché du duo (pour l'écran titre). */
    public static String duoNames(GameData data) {
        int[] duo = data.rom.profile() == GameProfile.EMERALD ? new int[]{405, 404} : new int[]{250, 249};
        StringBuilder sb = new StringBuilder();
        for (int id : duo) {
            if (id >= data.species.count()) continue;
            Species s = data.species.get(id);
            if (s.isPlaceholder()) continue;
            if (!sb.isEmpty()) sb.append(" & ");
            sb.append(s.name());
        }
        return sb.toString();
    }
}
