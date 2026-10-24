package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.Input.Button;
import dev.orvaen.mmo.engine.Scene;
import dev.orvaen.mmo.engine.Ui;

import java.awt.Graphics2D;

/** Menu principal (touche X), en surimpression sur le monde. */
public final class StartMenuScene implements Scene {

    private static final String[] ITEMS = {"POKÉDEX", "PERSO", "CARTES", "SAUVER", "RETOUR"};

    private final Game game;
    private final OverworldScene world;
    private int cursor;

    public StartMenuScene(Game game, OverworldScene world) {
        this.game = game;
        this.world = world;
    }

    @Override
    public boolean isOverlay() {
        return true;
    }

    @Override
    public void update(Input in) {
        if (in.repeat(Button.DOWN)) cursor = (cursor + 1) % ITEMS.length;
        if (in.repeat(Button.UP)) cursor = (cursor - 1 + ITEMS.length) % ITEMS.length;
        if (in.pressed(Button.B) || in.pressed(Button.START)) {
            game.pop();
            return;
        }
        if (!in.pressed(Button.A)) return;
        switch (cursor) {
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
        int w = 84, x = Game.WIDTH - w - 2, top = 16;
        Ui.box(g, x, 2, w, 30 + ITEMS.length * 16);
        g.setFont(Ui.SMALL);
        Ui.text(g, game.save.name == null ? "" : game.save.name, x + 10, 15, Ui.FRAME);
        g.setFont(Ui.FONT);
        for (int i = 0; i < ITEMS.length; i++) {
            Ui.text(g, ITEMS[i], x + 18, top + 20 + i * 16);
        }
        Ui.cursor(g, x + 9, top + 20 + cursor * 16);
    }
}
