package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.data.Species;
import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.Input.Button;
import dev.orvaen.mmo.engine.Scene;
import dev.orvaen.mmo.engine.Ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;

/** Pokédex : toutes les espèces extraites de la ROM, avec sprite, types et statistiques. */
public final class DexScene implements Scene {

    private static final int VISIBLE = 9;
    private static final String[] STAT_LABELS = {"PV", "ATQ", "DEF", "ATS", "DFS", "VIT"};

    private final Game game;
    private final List<Species> list;
    private int index;
    private int scroll;

    public DexScene(Game game) {
        this.game = game;
        this.list = game.data.species.all();
    }

    @Override
    public void update(Input in) {
        int n = list.size();
        if (in.repeat(Button.DOWN)) index = (index + 1) % n;
        if (in.repeat(Button.UP)) index = (index - 1 + n) % n;
        if (in.repeat(Button.R)) index = Math.min(n - 1, index + VISIBLE);
        if (in.repeat(Button.L)) index = Math.max(0, index - VISIBLE);
        if (in.repeat(Button.RIGHT)) index = Math.min(n - 1, index + VISIBLE);
        if (in.repeat(Button.LEFT)) index = Math.max(0, index - VISIBLE);
        if (in.pressed(Button.B) || in.pressed(Button.START)) game.pop();
        if (index < scroll) scroll = index;
        if (index >= scroll + VISIBLE) scroll = index - VISIBLE + 1;
    }

    @Override
    public void render(Graphics2D g) {
        g.setColor(new Color(0xD8483C));
        g.fillRect(0, 0, Game.WIDTH, Game.HEIGHT);
        g.setColor(new Color(0xA8302A));
        g.fillRect(0, 0, Game.WIDTH, 16);
        g.setFont(Ui.FONT);
        Ui.text(g, "POKÉDEX", 8, 12, Color.WHITE);
        int caught = game.save.caught.size(), seen = game.save.seen.size();
        g.setFont(Ui.SMALL);
        Ui.textRight(g, "Vus " + seen + "  ·  Pris " + caught + "  ·  " + list.size() + " espèces", 234, 11);

        // Liste
        Ui.box(g, 4, 20, 104, 136);
        for (int i = 0; i < VISIBLE && scroll + i < list.size(); i++) {
            Species s = list.get(scroll + i);
            int y = 36 + i * 14;
            boolean known = game.save.seen.contains(s.id()) || game.save.caught.contains(s.id());
            if (scroll + i == index) Ui.cursor(g, 9, y);
            if (game.save.caught.contains(s.id())) BattleScene.drawCapsule(g, 16, y - 10, 0);
            g.setFont(Ui.SMALL);
            Ui.text(g, String.format("%03d", s.id()), 31, y, known ? Ui.INK : new Color(0x9090A0));
            g.setFont(Ui.FONT);
            Ui.text(g, s.name(), 49, y);
        }

        // Fiche
        Species s = list.get(index);
        Ui.box(g, 112, 20, 124, 136);
        g.setFont(Ui.FONT);
        Ui.text(g, s.name(), 120, 34);
        g.setColor(new Color(0xE8F0E0));
        g.fillRoundRect(118, 38, 66, 66, 6, 6);
        g.drawImage(game.data.species.frontSprite(s.id()), 119, 39, null);

        g.setFont(Ui.SMALL);
        Ui.text(g, String.format("N° %03d", s.id()), 188, 48);
        typeBadge(g, s.type1(), 188, 54);
        if (s.hasSecondType()) typeBadge(g, s.type2(), 188, 68);
        Ui.text(g, "Capture", 188, 92);
        Ui.text(g, String.valueOf(s.catchRate()), 188, 101);

        int[] stats = {s.hp(), s.attack(), s.defense(), s.spAttack(), s.spDefense(), s.speed()};
        for (int i = 0; i < stats.length; i++) {
            int y = 108 + i * 7;
            Ui.text(g, STAT_LABELS[i], 120, y + 6);
            Ui.text(g, String.valueOf(stats[i]), 140, y + 6);
            Ui.bar(g, 158, y + 1, 72, 5, stats[i] / 180.0, statColor(stats[i]));
        }
    }

    private static void typeBadge(Graphics2D g, int type, int x, int y) {
        g.setColor(new Color(Species.typeColor(type)));
        g.fillRoundRect(x, y, 40, 11, 4, 4);
        g.setColor(Color.WHITE);
        g.drawString(Species.typeName(type), x + 4, y + 9);
    }

    private static Color statColor(int v) {
        if (v < 50) return new Color(0xF08030);
        if (v < 80) return new Color(0xF8D030);
        if (v < 110) return new Color(0x78C850);
        return new Color(0x30A0E0);
    }
}
