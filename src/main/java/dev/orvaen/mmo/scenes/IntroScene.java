package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.Scene;
import dev.orvaen.mmo.player.PlayerGraphics;
import dev.orvaen.mmo.rom.GameProfile;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Accueil d'une nouvelle partie : le Professeur présente le monde des Pokémon, puis le joueur,
 * avant d'entrer dans le jeu. Images tirées de la ROM du joueur (portrait du Professeur, un Pokémon,
 * le dresseur avec sa tenue) ; le texte est propre au moteur.
 */
public final class IntroScene implements Scene {

    private static final int PIC_PROFESSOR_FRLG = 132;
    private static final int PIC_RIVAL_FRLG = 106;
    private static final int SHOWN_SPECIES = 25;

    private final Game game;
    private final MessageBox box = new MessageBox();
    private final BufferedImage professor;
    private final BufferedImage pokemon;
    private final BufferedImage player;
    private final BufferedImage rival;
    private int phase;          // 0 : présentation, 1 : le rival, 2 : départ
    private int frame;
    private int pageFrame;
    private int lastPage;
    private boolean leaving;

    public IntroScene(Game game) {
        this.game = game;
        boolean frlg = game.data.rom.profile() == GameProfile.FIRERED_LEAFGREEN;
        this.professor = frlg ? PlayerGraphics.trainerPic(game.data.rom, PIC_PROFESSOR_FRLG) : null;
        this.pokemon = SHOWN_SPECIES < game.data.species.count() ? game.data.species.frontSprite(SHOWN_SPECIES) : null;
        this.player = new PlayerSprite(game.data.player, game.save.look).front();
        this.rival = frlg ? PlayerGraphics.trainerPic(game.data.rom, PIC_RIVAL_FRLG) : null;
        String name = playerName();
        box.show(List.of(
                "Bonjour ! Bienvenue dans le monde des Pokémon !",
                "On m'appelle le Professeur. J'étudie les Pokémon depuis toujours.",
                "Ce monde est peuplé de créatures fascinantes : les Pokémon.\nCertains vivent avec nous, d'autres font équipe avec les dresseurs.",
                "Et toi, tu es " + name + ", c'est bien ça ?"), null);
    }

    private String playerName() {
        return game.save.name == null || game.save.name.isBlank() ? "Dresseur" : game.save.name;
    }

    /** Page affichée (0-3 : présentation, 4 : rival, 5+ : départ) pour choisir les images. */
    private int step() {
        return phase == 0 ? box.page() : phase == 1 ? 4 : 5;
    }

    @Override
    public void update(Input in) {
        frame++;
        pageFrame++;
        box.update(in);
        if (box.page() != lastPage) {
            lastPage = box.page();
            pageFrame = 0;
        }
        if (leaving || !box.takeButton()) return;
        if (phase == 0) {
            phase = 1;
            pageFrame = 0;
            box.show(List.of("Voici mon petit-fils. Vous êtes rivaux depuis que vous êtes tout petits.",
                    "… Euh, comment s'appelle-t-il déjà ?"), null);
        } else if (phase == 1) {
            game.push(new NamingScene(game, "Nom du rival", "Blue", rival, n -> {
                game.save.rivalName = n;
                game.save.store();
                phase = 2;
                pageFrame = 0;
                String region = game.data.rom.profile() == GameProfile.FIRERED_LEAFGREEN ? "Kanto" : "Hoenn";
                box.show(List.of("Ah oui ! Il s'appelle " + n + " !",
                        playerName() + ", ton voyage à travers " + region + " commence aujourd'hui.",
                        "Des rencontres, des combats et des amis t'attendent.\nAllez, en route !"), null);
            }));
        } else {
            leaving = true;
            box.close();
            game.fadeThen(() -> game.replace(new OverworldScene(game)));
        }
    }

    @Override
    public void render(Graphics2D g) {
        // haute résolution uniquement
    }

    @Override
    public void renderHiRes(Graphics2D g, int w, int h) {
        game.backdrop().render(g, w, h, frame);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double s = Math.max(0.8, Math.min(1.8, h / 720.0));
        double cx = w / 2.0, cy = h * 0.42;
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), (float) (260 * s), new float[]{0f, 1f},
                new Color[]{new Color(255, 255, 255, 46), new Color(255, 255, 255, 0)}));
        g.fill(new Ellipse2D.Double(cx - 260 * s, cy - 260 * s, 520 * s, 520 * s));
        g.setColor(new Color(0, 0, 0, 60));
        g.fill(new Ellipse2D.Double(cx - 120 * s, cy + 100 * s, 240 * s, 36 * s));

        int page = step();
        double appear = Math.min(1, pageFrame / 18.0);
        double size = Math.floor(3 * s) * 64;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        if (page <= 1 && professor != null) draw(g, professor, cx, cy + 118 * s, size, page == 0 ? appear : 1);
        if (page == 1 && professor == null && pokemon != null) draw(g, pokemon, cx, cy + 118 * s, size, appear);
        if (page == 2) {
            if (professor != null) draw(g, professor, cx - 150 * s, cy + 118 * s, size, 1);
            if (pokemon != null) draw(g, pokemon, cx + 150 * s, cy + 118 * s, size * 0.8, appear);
        }
        if (page == 3 && player != null) draw(g, player, cx, cy + 118 * s, size, appear);
        if (page == 4 && rival != null) draw(g, rival, cx, cy + 118 * s, size, appear);
        if (page == 4 && rival == null && professor != null) draw(g, professor, cx, cy + 118 * s, size, 1);
        if (page >= 5 && player != null) draw(g, player, cx, cy + 118 * s, size, 1);
        box.render(g, w, h);
    }

    private static void draw(Graphics2D g, BufferedImage img, double cx, double bottom, double size, double alpha) {
        java.awt.Composite c = g.getComposite();
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, alpha))));
        double rise = (1 - alpha) * 20;
        g.drawImage(img, (int) Math.round(cx - size / 2), (int) Math.round(bottom - size + rise), (int) size, (int) size, null);
        g.setComposite(c);
    }
}
