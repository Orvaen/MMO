package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.engine.HiUi;
import dev.orvaen.mmo.world.MapRenderer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Petits dessins par-dessus le monde : PNJ, bulles « ! », portrait de Pokémon, nom de la zone, messages.
 * Toutes les méthodes sont « statiques » : elles dessinent ce qu'on leur donne, sans rien retenir.
 */
final class OverworldHud {

    private static final int T = MapRenderer.TILE;

    /** Durée d'affichage du nom de la zone en arrivant (frames). */
    static final int AREA_POPUP_FRAMES = 150;

    private OverworldHud() {}

    /** Taille de l'interface selon la hauteur de la fenêtre. */
    private static double scale(int h) {
        return Math.max(0.8, Math.min(1.6, h / 720.0));
    }

    /** Un PNJ, pieds en bas de sa case, calé au pixel de l'écran comme le joueur. */
    static void drawNpc(Graphics2D g, Npc n, double originX, double originY, int zoom, double interpolation) {
        if (!n.visible()) return;
        BufferedImage img = n.frame();
        double wx = n.exactX(interpolation) + (T - n.width()) / 2.0;
        double wy = n.exactY(interpolation) + T - n.height();
        double lift = n.hopHeight(interpolation);
        Graphics2D q = (Graphics2D) g.create();
        q.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        q.translate(Math.round(originX + wx * zoom), Math.round(originY + wy * zoom));
        q.scale(zoom, zoom);
        q.drawImage(img, 0, (int) -Math.round(lift), null);
        q.dispose();
    }

    /** Bulle d'émotion au-dessus d'une tête : 0 « ! », 1 « ? », 2 « !! », 3 « ♥ », 4 « … ». */
    static void drawEmote(Graphics2D g, int kind, double centerX, double bottom, int zoom) {
        double s = Math.max(2, zoom) / 3.0;
        double bw = 26 * s, bh = 24 * s, x = centerX - bw / 2, y = bottom - bh - 4 * s;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Path2D tail = new Path2D.Double();
        tail.moveTo(centerX - 5 * s, y + bh - 1);
        tail.lineTo(centerX + 5 * s, y + bh - 1);
        tail.lineTo(centerX, y + bh + 6 * s);
        tail.closePath();
        Area bubble = new Area(new RoundRectangle2D.Double(x, y, bw, bh, 10 * s, 10 * s));
        bubble.add(new Area(tail));
        g.setColor(Color.WHITE);
        g.fill(bubble);
        g.setColor(new Color(0x262838));
        g.setStroke(new BasicStroke((float) (1.6 * s)));
        g.draw(bubble);
        String text = switch (kind) {
            case 1 -> "?";
            case 2 -> "!!";
            case 3 -> "♥";
            case 4 -> "…";
            default -> "!";
        };
        g.setColor(kind == 3 ? new Color(0xE84855) : new Color(0xE04040));
        g.setFont(HiUi.font((float) (17 * s), true));
        HiUi.centered(g, text, x, y - s, bw, bh);
    }

    /** Portrait d'un Pokémon montré par un script (choix du starter…). */
    static void drawMonPic(Graphics2D g, int w, int h, BufferedImage img) {
        if (img == null) return;
        double s = scale(h);
        double size = Math.floor(3 * s) * 64, pad = 18 * s;
        double x = (w - size) / 2 - pad, y = h * 0.16, box = size + pad * 2;
        HiUi.fill(g, new Color(0, 0, 0, 70), x + 3, y + 5, box, box, 16 * s);
        HiUi.fill(g, new Color(0xF2F3F8), x, y, box, box, 16 * s);
        HiUi.stroke(g, HiUi.PANEL_BORDER, x, y, box, box, 16 * s, 2f);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(img, (int) (x + pad), (int) (y + pad), (int) size, (int) size, null);
    }

    /** Nom de la zone en haut à gauche, qui glisse à l'arrivée puis repart. */
    static void drawAreaName(Graphics2D g, int h, String name, int timer) {
        if (timer <= 0) return;
        double s = scale(h);
        double t = Math.min(AREA_POPUP_FRAMES - timer, timer) / 12.0;
        double slide = (Math.min(1, t) - 1) * 60 * s;
        g.setFont(HiUi.font((float) (15 * s), true));
        double tw = g.getFontMetrics().stringWidth(name);
        double bw = tw + 44 * s, bh = 34 * s, x = 14 * s, y = 14 * s + slide;
        HiUi.fill(g, new Color(20, 22, 34, 225), x, y, bw, bh, 6 * s);
        HiUi.fill(g, HiUi.ACCENT, x, y, 4 * s, bh, 2 * s);
        g.setColor(Color.WHITE);
        HiUi.centered(g, name, x + 8 * s, y, bw - 8 * s, bh);
    }

    /** Message d'information en bas de l'écran (erreur de warp, téléportation…). */
    static void drawNotice(Graphics2D g, int w, int h, String message) {
        double s = scale(h);
        g.setFont(HiUi.font((float) (15 * s), false));
        double bw = Math.min(w - 40, 620 * s), bh = 56 * s, x = (w - bw) / 2, y = h - bh - 24 * s;
        HiUi.fill(g, new Color(20, 22, 34, 235), x, y, bw, bh, 6 * s);
        HiUi.stroke(g, HiUi.PANEL_BORDER, x, y, bw, bh, 6 * s, 1f);
        g.setColor(HiUi.TEXT);
        HiUi.centered(g, message, x + 12 * s, y, bw - 24 * s, bh);
    }
}
