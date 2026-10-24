package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.data.Species;
import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.Input.Button;
import dev.orvaen.mmo.engine.Scene;
import dev.orvaen.mmo.engine.Ui;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Rencontre sauvage. Prototype : on peut tenter une capture ou fuir.
 * Le vrai système de combat (attaques, équipe) viendra ensuite.
 */
public final class BattleScene implements Scene {

    private enum State { INTRO, MENU, THROW, SHAKE, RESULT, END }

    private static final String[] MENU = {"CAPTURER", "FUIR"};

    private final Game game;
    private final Species species;
    private final int level;
    private final BufferedImage sprite;
    private final Random rng = new Random();
    private final PlayerSprite avatar;

    private State state = State.INTRO;
    private int timer;
    private int cursor;
    private boolean caught;
    private int shakes;
    private String text;

    public BattleScene(Game game, int speciesId, int level) {
        this.game = game;
        this.species = game.data.species.get(speciesId);
        this.level = level;
        this.sprite = game.data.species.frontSprite(speciesId);
        this.avatar = new PlayerSprite(game.data.player, game.save.look);
        this.text = "Un " + species.name() + " sauvage apparaît !";
    }

    @Override
    public void update(Input in) {
        timer++;
        switch (state) {
            case INTRO -> {
                if (timer > 30 && (in.pressed(Button.A) || in.pressed(Button.B))) go(State.MENU, "Que faire ?");
            }
            case MENU -> {
                if (in.repeat(Button.DOWN) || in.repeat(Button.UP)) cursor = 1 - cursor;
                if (in.pressed(Button.B)) cursor = 1;
                if (in.pressed(Button.A)) {
                    if (cursor == 0) {
                        resolveCapture();
                        go(State.THROW, playerName() + " lance une Capsule !");
                    } else {
                        go(State.END, "Vous prenez la fuite !");
                    }
                }
            }
            case THROW -> {
                if (timer >= 40) go(State.SHAKE, text);
            }
            case SHAKE -> {
                if (timer >= 30 * Math.max(1, shakes)) {
                    go(State.RESULT, caught
                            ? "Et hop ! " + species.name() + " est capturé !"
                            : "Mince ! " + species.name() + " s'est libéré !");
                    if (caught) game.save.caught.add(species.id());
                }
            }
            case RESULT -> {
                if (timer > 20 && in.pressed(Button.A)) {
                    if (caught) go(State.END, species.name() + " a été ajouté au Pokédex.");
                    else go(State.MENU, "Que faire ?");
                }
            }
            case END -> {
                if (timer > 20 && (in.pressed(Button.A) || in.pressed(Button.B) || timer > 120)) {
                    game.fadeThen(game::pop);
                    timer = Integer.MIN_VALUE / 2;
                }
            }
        }
    }

    private String playerName() {
        return game.save.name == null || game.save.name.isBlank() ? "Vous" : game.save.name;
    }

    /** Formule simplifiée inspirée de la Gen 3 (PV pleins, Capsule standard). */
    private void resolveCapture() {
        double chance = Math.min(0.95, species.catchRate() / 255.0 * 0.6 + 0.12);
        caught = rng.nextDouble() < chance;
        shakes = caught ? 3 : 1 + rng.nextInt(3);
    }

    private void go(State next, String message) {
        state = next;
        timer = 0;
        text = message;
    }

    @Override
    public void render(Graphics2D g) {
        int w = Game.WIDTH, h = Game.HEIGHT;
        g.setPaint(new GradientPaint(0, 0, new Color(0xB8E0F8), 0, 112, new Color(0xF0F8E0)));
        g.fillRect(0, 0, w, h);

        // Plateformes
        g.setColor(new Color(0x8CC878));
        g.fillOval(128, 60, 104, 26);
        g.fillOval(-10, 100, 120, 26);
        g.setColor(new Color(0x6AA858));
        g.drawOval(128, 60, 104, 26);

        // Pokémon sauvage (glisse depuis la gauche à l'intro)
        int slide = state == State.INTRO ? Math.max(0, 120 - timer * 6) : 0;
        boolean hidden = state == State.SHAKE || (state == State.RESULT && caught) || (state == State.END && caught);
        if (!hidden) g.drawImage(sprite, 148 - slide, 8, null);

        // Capsule (dessin maison)
        if (state == State.THROW) {
            double t = Math.min(1, timer / 40.0);
            int bx = (int) (40 + t * 140);
            int by = (int) (100 - Math.sin(Math.PI * t) * 70 - t * 40);
            drawCapsule(g, bx, by, 0);
        } else if (state == State.SHAKE || (state == State.RESULT && caught)) {
            int wobble = state == State.SHAKE && (timer / 8) % 2 == 0 ? (timer % 30 < 16 ? 2 : -2) : 0;
            drawCapsule(g, 176, 62, wobble);
        }

        // Dresseur (vu de dos), coupé par la boîte de texte comme sur GBA
        int enter = state == State.INTRO ? Math.max(0, 120 - timer * 6) : 0;
        g.drawImage(avatar.back(), 20 + enter, h - 44 - 60, null);

        // Encadré d'informations
        Ui.box(g, 6, 8, 110, 36);
        g.setFont(Ui.FONT);
        Ui.text(g, species.name(), 14, 22);
        Ui.textRight(g, "Nv." + level, 108, 22);
        g.setFont(Ui.SMALL);
        Ui.text(g, "PV", 14, 36);
        Ui.bar(g, 28, 30, 80, 6, 1.0, new Color(0x58D080));
        if (game.save.caught.contains(species.id())) {
            drawCapsule(g, 101, 9, 0);
        }

        // Boîte de texte + menu
        g.setFont(Ui.FONT);
        Ui.box(g, 0, h - 44, w, 44);
        int textWidth = state == State.MENU ? 130 : w - 24;
        int y = h - 27;
        for (String line : Ui.wrap(g, text, textWidth)) {
            Ui.text(g, line, 10, y);
            y += 14;
        }
        if (state == State.MENU) {
            Ui.box(g, w - 92, h - 44, 92, 44);
            for (int i = 0; i < MENU.length; i++) {
                Ui.text(g, MENU[i], w - 74, h - 27 + i * 15);
            }
            Ui.cursor(g, w - 84, h - 27 + cursor * 15);
        }
    }

    /** Capsule de capture : design original du moteur. */
    static void drawCapsule(Graphics2D g, int x, int y, int wobble) {
        Graphics2D c = (Graphics2D) g.create();
        c.rotate(Math.toRadians(wobble * 8), x + 6, y + 6);
        c.setColor(new Color(0x282830));
        c.fillRoundRect(x, y, 12, 12, 5, 5);
        c.setColor(new Color(0x2A9D8F));
        c.fillRoundRect(x + 1, y + 1, 10, 5, 4, 4);
        c.setColor(new Color(0xF4F1DE));
        c.fillRoundRect(x + 1, y + 6, 10, 5, 4, 4);
        c.setColor(new Color(0xE9C46A));
        c.fillRect(x + 5, y + 4, 2, 4);
        c.dispose();
    }
}
