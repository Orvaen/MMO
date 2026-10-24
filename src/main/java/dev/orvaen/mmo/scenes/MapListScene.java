package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.Input.Button;
import dev.orvaen.mmo.engine.Scene;
import dev.orvaen.mmo.engine.Ui;
import dev.orvaen.mmo.rom.RomException;
import dev.orvaen.mmo.world.GameMap;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/** Outil de développement : liste de toutes les cartes extraites, avec téléportation. */
public final class MapListScene implements Scene {

    private static final int VISIBLE = 10;

    private record Entry(int group, int num, String label) {}

    private final Game game;
    private final OverworldScene world;
    private final List<Entry> entries = new ArrayList<>();
    private int index;
    private int scroll;
    private String error;

    public MapListScene(Game game, OverworldScene world) {
        this.game = game;
        this.world = world;
        var maps = game.data.maps;
        GameMap here = world.currentMap();
        for (int g = 0; g < maps.groupCount(); g++) {
            for (int n = 0; n < maps.mapCount(g); n++) {
                String name;
                try {
                    name = maps.load(g, n).name;
                } catch (RomException e) {
                    name = "(illisible)";
                }
                if (g == here.group && n == here.num) index = entries.size();
                entries.add(new Entry(g, n, String.format("%2d.%-3d %s", g, n, name)));
            }
        }
        scroll = Math.max(0, index - VISIBLE / 2);
    }

    @Override
    public void update(Input in) {
        int n = entries.size();
        if (n == 0 || in.pressed(Button.B) || in.pressed(Button.SELECT)) {
            game.pop();
            return;
        }
        if (in.repeat(Button.DOWN)) index = (index + 1) % n;
        if (in.repeat(Button.UP)) index = (index - 1 + n) % n;
        if (in.repeat(Button.RIGHT) || in.repeat(Button.R)) index = Math.min(n - 1, index + VISIBLE);
        if (in.repeat(Button.LEFT) || in.repeat(Button.L)) index = Math.max(0, index - VISIBLE);
        if (index < scroll) scroll = index;
        if (index >= scroll + VISIBLE) scroll = index - VISIBLE + 1;

        if (in.pressed(Button.A)) {
            Entry e = entries.get(index);
            try {
                GameMap target = game.data.maps.load(e.group, e.num);
                game.data.renderer.render(target); // vérifie que la carte se dessine
                game.pop();
                game.fadeThen(() -> world.teleport(target));
            } catch (RomException | IllegalArgumentException ex) {
                error = "Carte illisible : " + ex.getMessage();
            }
        }
    }

    @Override
    public void render(Graphics2D g) {
        g.setColor(new Color(0x203048));
        g.fillRect(0, 0, Game.WIDTH, Game.HEIGHT);
        g.setFont(Ui.FONT);
        Ui.text(g, "CARTES DE LA ROM", 8, 13, Color.WHITE);
        g.setFont(Ui.SMALL);
        Ui.textRight(g, entries.size() + " cartes · A : y aller", 234, 12);

        Ui.box(g, 4, 18, Game.WIDTH - 8, 138);
        g.setFont(Ui.SMALL);
        for (int i = 0; i < VISIBLE && scroll + i < entries.size(); i++) {
            int y = 33 + i * 12;
            Ui.text(g, entries.get(scroll + i).label, 20, y);
            if (scroll + i == index) Ui.cursor(g, 11, y);
        }
        if (error != null) {
            Ui.text(g, error, 10, 152, Ui.ACCENT);
        }
    }
}
