package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.HiUi;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.Scene;
import dev.orvaen.mmo.rom.TextCodec;
import dev.orvaen.mmo.script.Trainers;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Combat de dresseur lancé par un script (rival, dresseurs des routes).
 * <p>
 * Le vrai système de combat (attaques, PV, équipe) n'existe pas encore : l'adversaire et son équipe
 * sont présentés à partir de la ROM, puis le combat est compté comme gagné pour que l'histoire continue.
 */
public final class TrainerBattleScene implements Scene {

    private final Game game;
    private final Trainers.Trainer trainer;
    private final BufferedImage lead;
    private final MessageBox box = new MessageBox();
    private int frame;

    public TrainerBattleScene(Game game, Trainers.Trainer trainer, int defeatText) {
        this.game = game;
        this.trainer = trainer;
        String name = trainer == null || trainer.name().isBlank() ? "Le dresseur" : trainer.name();
        List<String> pages = new ArrayList<>();
        pages.add(name + " veut se battre !");
        if (trainer != null && !trainer.party().isEmpty()) {
            int[] first = trainer.party().get(0);
            String sp = first[0] < game.data.species.count() ? game.data.species.get(first[0]).name() : "?";
            pages.add(name + " envoie " + sp + " (N. " + first[1] + ") !");
            game.save.seen.add(first[0]);
            lead = game.data.species.frontSprite(first[0]);
        } else {
            lead = null;
        }
        pages.add("Le système de combat arrive bientôt :\ncombat compté comme gagné.");
        if (defeatText >= 0) {
            String player = game.save.name == null || game.save.name.isBlank() ? "Dresseur" : game.save.name;
            pages.addAll(TextCodec.dialogue(game.data.rom, defeatText, 512, player, "RIVAL"));
        }
        box.show(pages, null);
    }

    @Override
    public void update(Input in) {
        frame++;
        box.update(in);
        if (box.takeButton()) {
            box.close();
            game.pop();
        }
    }

    @Override
    public void render(Graphics2D g) {
        // haute résolution uniquement
    }

    @Override
    public void renderHiRes(Graphics2D g, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, new Color(0x2B3550), 0, h, new Color(0x141724)));
        g.fillRect(0, 0, w, h);
        double s = Math.max(0.8, Math.min(1.8, h / 720.0));
        double cx = w * 0.68, cy = h * 0.36;
        g.setColor(new Color(0x3E4A6A));
        g.fill(new Ellipse2D.Double(cx - 130 * s, cy + 40 * s, 260 * s, 54 * s));
        if (lead != null) {
            double size = Math.floor(3 * s) * 64;
            double slide = Math.max(0, 1 - frame / 20.0) * w * 0.4;
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(lead, (int) (cx - size / 2 + slide), (int) (cy + 70 * s - size), (int) size, (int) size, null);
        }
        if (trainer != null) {
            g.setFont(HiUi.font((float) (18 * s), true));
            g.setColor(Color.WHITE);
            g.drawString(trainer.name(), (float) (w * 0.08), (float) (h * 0.12));
            g.setFont(HiUi.font((float) (13 * s), false));
            g.setColor(HiUi.MUTED);
            g.drawString(trainer.party().size() + " Pokémon", (float) (w * 0.08), (float) (h * 0.12 + 22 * s));
        }
        box.render(g, w, h);
    }
}
