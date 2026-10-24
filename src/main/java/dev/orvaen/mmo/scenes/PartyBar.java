package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.battle.Party;
import dev.orvaen.mmo.battle.Pokemon;
import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.HiUi;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

/**
 * L'équipe, toujours visible à droite de l'écran comme dans PokeMMO : 6 cases avec l'icône du Pokémon et
 * sa barre de PV. Un clic sur une case ouvre son résumé ({@link SummaryWindow}).
 */
final class PartyBar {

    private final Game game;
    private final Team team;
    private int frame;

    PartyBar(Game game) {
        this.game = game;
        this.team = new Team(game);
    }

    /** Taille de l'interface (comme les fenêtres) selon la hauteur de la fenêtre du jeu. */
    private static double scale(int h) {
        return Math.max(0.8, Math.min(1.15, h / 900.0));
    }

    /** Case n° {@code i} (0 à 5) dans une fenêtre de jeu {@code w} × {@code h}. */
    static Rectangle2D slot(int i, int w, int h) {
        double s = scale(h), size = 44 * s, gap = 8 * s, margin = 10 * s;
        double total = Party.MAX * size + (Party.MAX - 1) * gap;
        double top = Math.max(margin, (h - total) / 2);
        return new Rectangle2D.Double(w - margin - size, top + i * (size + gap), size, size);
    }

    /** Case sous la souris (dans les limites de l'équipe), ou -1. */
    int slotAt(int mx, int my, int w, int h) {
        int n = team.get().size();
        for (int i = 0; i < n; i++) if (slot(i, w, h).contains(mx, my)) return i;
        return -1;
    }

    void draw(Graphics2D g, int w, int h) {
        frame++;
        List<Pokemon> mons = team.get();
        double s = scale(h);
        int mx = game.input().mouseX(), my = game.input().mouseY();
        Graphics2D q = (Graphics2D) g.create();
        q.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int i = 0; i < Party.MAX; i++) {
            Rectangle2D r = slot(i, w, h);
            boolean filled = i < mons.size();
            boolean hover = filled && r.contains(mx, my);
            HiUi.fill(q, hover ? HiUi.SELECTOR_HOVER : HiUi.SELECTOR, r.getX(), r.getY(), r.getWidth(), r.getHeight(), 6 * s);
            HiUi.stroke(q, hover ? HiUi.ACCENT : HiUi.SELECTOR_BORDER, r.getX(), r.getY(), r.getWidth(), r.getHeight(), 6 * s, 1f);
            if (!filled) continue;
            Pokemon p = mons.get(i);
            BufferedImage[] icon = game.data.icons().frames(p.species);
            if (icon != null) {
                // l'icône bouge doucement quand on la survole (comme dans le jeu)
                BufferedImage img = icon[hover && (frame / 10) % 2 == 1 ? 1 : 0];
                double size = r.getWidth() - 6 * s;
                Graphics2D ic = (Graphics2D) q.create();
                ic.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                if (p.fainted()) ic.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.45f));
                ic.drawImage(img, (int) Math.round(r.getX() + 3 * s), (int) Math.round(r.getY() - 2 * s),
                        (int) Math.round(size), (int) Math.round(size), null);
                ic.dispose();
            }
            // barre de PV
            double bx = r.getX() + 5 * s, by = r.getMaxY() - 7 * s, bw = r.getWidth() - 10 * s, bh = 3.5 * s;
            q.setColor(new Color(0x15161F));
            q.fill(new Rectangle2D.Double(bx, by, bw, bh));
            double ratio = p.maxHp() <= 0 ? 0 : Math.max(0, Math.min(1, p.hp / (double) p.maxHp()));
            q.setColor(hpColor(ratio));
            q.fill(new Rectangle2D.Double(bx, by, bw * ratio, bh));
        }
        // nom et niveau du Pokémon survolé, à gauche de la case
        int over = slotAt(mx, my, w, h);
        if (over >= 0) {
            Pokemon p = mons.get(over);
            Rectangle2D r = slot(over, w, h);
            String label = p.name() + "  N. " + p.level;
            q.setFont(HiUi.font((float) (12.5 * s), true));
            double tw = q.getFontMetrics().stringWidth(label) + 16 * s, th = 24 * s;
            double tx = r.getX() - tw - 6 * s, ty = r.getCenterY() - th / 2;
            HiUi.fill(q, HiUi.HEADER, tx, ty, tw, th, 5 * s);
            q.setColor(HiUi.TEXT);
            q.drawString(label, (float) (tx + 8 * s), (float) (ty + 16.5 * s));
        }
        q.dispose();
    }

    static Color hpColor(double ratio) {
        return ratio > 0.5 ? new Color(0x5BD68A) : ratio > 0.2 ? new Color(0xF2C94C) : new Color(0xEB5757);
    }

    /**
     * L'équipe lue dans la sauvegarde, relue seulement quand elle change (après un combat, une capture…).
     */
    static final class Team {
        private final Game game;
        private int hash;
        private boolean loaded;
        private List<Pokemon> cached = List.of();

        Team(Game game) {
            this.game = game;
        }

        List<Pokemon> get() {
            int hsh = game.save.partyNames.hashCode();
            for (int[] v : game.save.party) hsh = hsh * 31 + Arrays.hashCode(v);
            if (!loaded || hsh != hash) {
                loaded = true;
                hash = hsh;
                try {
                    cached = Party.load(game.data, game.save);
                } catch (RuntimeException e) {
                    cached = List.of();
                }
            }
            return cached;
        }
    }
}
