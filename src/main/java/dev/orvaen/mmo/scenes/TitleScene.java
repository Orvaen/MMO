package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.engine.Backdrop;
import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.HiUi;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.Input.Button;
import dev.orvaen.mmo.engine.Scene;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Écran d'accueil : fond animé tiré de la ROM (carte en panoramique + duo légendaire),
 * logo, et les choix « Jouer », « Personnage », « Quitter ».
 */
public final class TitleScene implements Scene {

    private enum Choice { PLAY, CHARACTER, QUIT }

    private static final double BTN_W = 300, BTN_H = 46, BTN_GAP = 12;

    private final Game game;
    private final List<Choice> choices = new ArrayList<>();
    private int cursor;
    private int frame;
    private String hover = "";

    public TitleScene(Game game) {
        this.game = game;
    }

    @Override
    public void onEnter() {
        choices.clear();
        choices.add(Choice.PLAY);
        if (game.save.hasCharacter()) choices.add(Choice.CHARACTER);
        choices.add(Choice.QUIT);
        cursor = Math.min(cursor, choices.size() - 1);
    }

    private String label(Choice c) {
        return switch (c) {
            case PLAY -> game.save.hasCharacter() ? "JOUER" : "NOUVEAU PERSONNAGE";
            case CHARACTER -> "MODIFIER LE PERSONNAGE";
            case QUIT -> "QUITTER";
        };
    }

    private Rectangle2D button(int i) {
        int w = game.viewWidth(), h = game.viewHeight();
        double scale = uiScale(w, h);
        double bw = BTN_W * scale, bh = BTN_H * scale;
        double top = h * 0.60;
        return new Rectangle2D.Double((w - bw) / 2, top + i * (bh + BTN_GAP * scale), bw, bh);
    }

    private static double uiScale(int w, int h) {
        return Math.max(0.6, Math.min(1.8, Math.min(w / 1100.0, h / 720.0)));
    }

    /** Point écran d'un bouton (tests) : « PLAY », « CHARACTER », « QUIT ». */
    public Point anchor(String choice) {
        Rectangle2D r = button(choices.indexOf(Choice.valueOf(choice)));
        return new Point((int) r.getCenterX(), (int) r.getCenterY());
    }

    @Override
    public void update(Input in) {
        frame++;
        hover = "";
        for (int i = 0; i < choices.size(); i++) {
            if (button(i).contains(in.mouseX(), in.mouseY())) {
                hover = choices.get(i).name();
                cursor = i;
                if (in.mousePressed()) {
                    select(choices.get(i));
                    return;
                }
            }
        }
        if (in.repeat(Button.DOWN)) cursor = (cursor + 1) % choices.size();
        if (in.repeat(Button.UP)) cursor = (cursor - 1 + choices.size()) % choices.size();
        if (in.pressed(Button.A) || in.pressed(Button.START)) select(choices.get(cursor));
    }

    private void select(Choice c) {
        switch (c) {
            case PLAY -> game.fadeThen(() -> game.replace(CharacterCreatorScene.firstScene(game)));
            case CHARACTER -> game.push(new CharacterCreatorScene(game, true));
            case QUIT -> game.quit();
        }
    }

    @Override
    public void render(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, Game.WIDTH, Game.HEIGHT);
    }

    @Override
    public void renderHiRes(Graphics2D g, int w, int h) {
        game.backdrop().render(g, w, h, frame);
        double s = uiScale(w, h);

        // Logo
        String title = "ORVAEN MMO";
        g.setFont(HiUi.tracked((float) (64 * s), true, 0.14f));
        FontMetrics fm = g.getFontMetrics();
        int tx = (w - fm.stringWidth(title)) / 2, ty = (int) (h * 0.30);
        for (int r = 6; r >= 1; r--) {
            g.setColor(new Color(140, 147, 230, 14));
            for (int dx = -r; dx <= r; dx += r) g.drawString(title, tx + dx, ty + r / 2);
        }
        g.setColor(new Color(0, 0, 0, 160));
        g.drawString(title, tx + 3, ty + 4);
        g.setColor(Color.WHITE);
        g.drawString(title, tx, ty);

        g.setFont(HiUi.tracked((float) (15 * s), false, 0.25f));
        g.setColor(new Color(0xC9CFF7));
        String sub = game.data.rom.profile().displayName(game.data.rom.gameCode()).toUpperCase();
        HiUi.centered(g, sub, 0, ty + 14 * s, w, 24 * s);

        // Boutons
        for (int i = 0; i < choices.size(); i++) {
            Rectangle2D r = button(i);
            boolean on = i == cursor;
            HiUi.fill(g, on ? new Color(0x3A3E5C) : new Color(20, 22, 34, 215), r.getX(), r.getY(), r.getWidth(), r.getHeight(), 4);
            HiUi.stroke(g, on ? HiUi.ACCENT : new Color(0x8B8FA8), r.getX(), r.getY(), r.getWidth(), r.getHeight(), 4,
                    on ? 1.8f : 1f);
            g.setFont(HiUi.tracked((float) (14 * s), on, 0.1f));
            g.setColor(on ? Color.WHITE : HiUi.TEXT);
            HiUi.centered(g, label(choices.get(i)), r.getX(), r.getY(), r.getWidth(), r.getHeight());
        }

        // Pied de page
        g.setFont(HiUi.font((float) (12 * s), false));
        g.setColor(new Color(255, 255, 255, 120));
        String who = game.save.hasCharacter() ? "Personnage : " + game.save.name : "Aucun personnage";
        g.drawString(who, (float) (16 * s), (float) (h - 16 * s));
        String duo = Backdrop.duoNames(game.data);
        String right = duo.isEmpty() ? "Données lues depuis ta ROM" : duo + " · lus depuis ta ROM";
        g.drawString(right, (float) (w - 16 * s - g.getFontMetrics().stringWidth(right)), (float) (h - 16 * s));
    }
}
