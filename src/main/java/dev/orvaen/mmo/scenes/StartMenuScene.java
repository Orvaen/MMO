package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.HiUi;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.Input.Button;
import dev.orvaen.mmo.engine.Scene;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Rectangle2D;

/**
 * Menu principal (touche X), en panneau net façon PokeMMO à droite de l'écran.
 * Clavier (flèches, A, B) ou souris (survol, clic ; clic en dehors pour fermer).
 */
public final class StartMenuScene implements Scene {

    private static final String[] ITEMS = {"Pokédex", "Personnage", "Cartes", "Sauvegarder", "Retour"};
    private static final String[] HINTS = {
            "Espèces vues et capturées", "Modifier ton apparence", "Aller sur une autre carte",
            "Enregistrer la partie", "Revenir au jeu"
    };
    private static final double PANEL_W = 260, HEADER_H = 46, ITEM_H = 48;

    private final Game game;
    private final OverworldScene world;
    private int cursor;
    private int frame;

    public StartMenuScene(Game game, OverworldScene world) {
        this.game = game;
        this.world = world;
    }

    @Override
    public boolean isOverlay() {
        return true;
    }

    private double scale() {
        return Math.max(0.75, Math.min(1.6, game.viewHeight() / 720.0));
    }

    private Rectangle2D panel() {
        double s = scale(), w = PANEL_W * s, h = (HEADER_H + ITEMS.length * ITEM_H + 10) * s;
        return new Rectangle2D.Double(game.viewWidth() - w - 16 * s, 16 * s, w, h);
    }

    private Rectangle2D item(int i) {
        Rectangle2D p = panel();
        double s = scale();
        return new Rectangle2D.Double(p.getX() + 6 * s, p.getY() + (HEADER_H + 4 + i * ITEM_H) * s, p.getWidth() - 12 * s, (ITEM_H - 4) * s);
    }

    /** Point écran d'une entrée (tests). */
    public Point anchor(int i) {
        Rectangle2D r = item(i);
        return new Point((int) r.getCenterX(), (int) r.getCenterY());
    }

    @Override
    public void update(Input in) {
        frame++;
        if (in.repeat(Button.DOWN)) cursor = (cursor + 1) % ITEMS.length;
        if (in.repeat(Button.UP)) cursor = (cursor - 1 + ITEMS.length) % ITEMS.length;
        if (in.pressed(Button.B) || in.pressed(Button.START)) {
            game.pop();
            return;
        }
        for (int i = 0; i < ITEMS.length; i++) {
            if (item(i).contains(in.mouseX(), in.mouseY())) {
                cursor = i;
                if (in.mousePressed()) {
                    choose(i);
                    return;
                }
            }
        }
        if (in.mousePressed() && !panel().contains(in.mouseX(), in.mouseY())) {
            game.pop();
            return;
        }
        if (in.pressed(Button.A)) choose(cursor);
    }

    private void choose(int i) {
        switch (i) {
            case 0 -> game.replace(new DexScene(game));
            case 1 -> game.replace(new CharacterCreatorScene(game, true));
            case 2 -> game.replace(new MapListScene(game, world));
            case 3 -> {
                world.rememberPosition();
                game.save.store();
                game.pop();
                world.showMessage("Partie sauvegardée.");
            }
            default -> game.pop();
        }
    }

    @Override
    public void render(Graphics2D g) {
        // tout est dessiné en haute résolution
    }

    @Override
    public void renderHiRes(Graphics2D g, int w, int h) {
        double s = scale();
        Rectangle2D p = panel();
        double appear = Math.min(1, frame / 8.0);
        g.translate((1 - appear) * 30 * s, 0);

        for (int i = 5; i >= 1; i--) {
            HiUi.fill(g, new Color(0, 0, 0, 26), p.getX() - i * 2, p.getY() - i * 2 + 4, p.getWidth() + i * 4, p.getHeight() + i * 4, 12);
        }
        HiUi.fill(g, new Color(0x262838), p.getX(), p.getY(), p.getWidth(), p.getHeight(), 7 * s);
        HiUi.fill(g, HiUi.HEADER, p.getX(), p.getY(), p.getWidth(), HEADER_H * s, 7 * s);
        g.setColor(HiUi.HEADER);
        g.fill(new Rectangle2D.Double(p.getX(), p.getY() + (HEADER_H - 8) * s, p.getWidth(), 8 * s));
        HiUi.stroke(g, HiUi.PANEL_BORDER, p.getX(), p.getY(), p.getWidth(), p.getHeight(), 7 * s, 1f);

        String name = game.save.name == null || game.save.name.isBlank() ? "Dresseur" : game.save.name;
        g.setFont(HiUi.tracked((float) (12 * s), true, 0.12f));
        g.setColor(HiUi.MUTED);
        g.drawString("MENU", (float) (p.getX() + 16 * s), (float) (p.getY() + 19 * s));
        g.setFont(HiUi.font((float) (16 * s), true));
        g.setColor(Color.WHITE);
        g.drawString(name, (float) (p.getX() + 16 * s), (float) (p.getY() + 38 * s));

        for (int i = 0; i < ITEMS.length; i++) {
            Rectangle2D r = item(i);
            boolean on = i == cursor;
            if (on) {
                HiUi.fill(g, HiUi.SELECTOR_HOVER, r.getX(), r.getY(), r.getWidth(), r.getHeight(), 5 * s);
                HiUi.fill(g, HiUi.ACCENT, r.getX(), r.getY() + 8 * s, 3 * s, r.getHeight() - 16 * s, 2 * s);
            }
            g.setFont(HiUi.font((float) (15 * s), on));
            g.setColor(on ? Color.WHITE : HiUi.TEXT);
            g.drawString(ITEMS[i], (float) (r.getX() + 16 * s), (float) (r.getY() + 20 * s));
            g.setFont(HiUi.font((float) (11.5 * s), false));
            g.setColor(on ? new Color(0xC9CFF7) : HiUi.MUTED);
            g.drawString(HINTS[i], (float) (r.getX() + 16 * s), (float) (r.getY() + 36 * s));
        }
    }
}
