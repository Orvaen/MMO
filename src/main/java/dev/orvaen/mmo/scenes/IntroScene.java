package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.data.IntroGraphics;
import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.Scene;
import dev.orvaen.mmo.player.PlayerGraphics;
import dev.orvaen.mmo.rom.GameProfile;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Accueil d'une nouvelle partie, comme dans Rouge Feu / Vert Feuille : le Professeur sur son estrade,
 * devant le fond de l'intro, libère un Pokémon de sa Poké Ball, puis présente le joueur et son rival.
 * <p>
 * Images, positions et rythme viennent du jeu (lus dans la ROM du joueur, scène de 240x160 agrandie
 * au pixel près) ; le texte est propre au moteur. Sans ces images (autre jeu, ROM modifiée), la scène
 * se rabat sur le fond animé et les portraits de combat.
 */
public final class IntroScene implements Scene {

    private static final int W = 240, H = 160;
    private static final int PIC_X = 88, PIC_Y = 16;                 // portrait 64x96 (tuile 11, 2)
    private static final int PLATFORM_X = 72, PLATFORM_Y = 96;       // trois sprites 32x32
    private static final int MON_X = 64, MON_Y = 64;                 // Pokémon de démonstration 64x64
    private static final int BALL_X = 100, BALL_Y = 66;              // point de sortie de la Poké Ball
    private static final int PIC_PROFESSOR_FRLG = 132, PIC_RIVAL_FRLG = 106;
    private static final int INTRO_SPECIES = 29;                     // Nidoran♀, comme dans le jeu

    private enum Step { WELCOME, THIS_WORLD, INHABITED, STUDY, RETURN_MON, ABOUT, PLAYER, RIVAL, RIVAL_NAMED, LETS_GO, EXIT }

    private final Game game;
    private final MessageBox box = new MessageBox();
    private final IntroGraphics gfx;
    private final BufferedImage professor, rival, player, mon;
    private final BufferedImage stage = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);

    private Step step = Step.WELCOME;
    private int frame, stepFrame;
    private BufferedImage pic, previousPic;
    private int picFade = 999;                // frames depuis le changement de portrait
    private int monTimer = -1;                // sortie (>= 0) ou retour (< -1) du Pokémon
    private boolean monOut;
    private boolean leaving;
    private BufferedImage monWhite;

    public IntroScene(Game game) {
        this.game = game;
        this.gfx = new IntroGraphics(game.data.rom);
        boolean frlg = game.data.rom.profile() == GameProfile.FIRERED_LEAFGREEN;
        this.professor = gfx.professor != null ? gfx.professor
                : frlg ? PlayerGraphics.trainerPic(game.data.rom, PIC_PROFESSOR_FRLG) : null;
        this.rival = gfx.rival != null ? gfx.rival : frlg ? PlayerGraphics.trainerPic(game.data.rom, PIC_RIVAL_FRLG) : null;
        this.player = new PlayerSprite(game.data.player, game.save.look).front();
        this.mon = INTRO_SPECIES < game.data.species.count() ? game.data.species.frontSprite(INTRO_SPECIES) : null;
        this.pic = professor;
        game.fadeInFromBlack();
        game.data.audio.playBgm(game.data.songs.intro());
    }

    private String playerName() {
        return game.save.name == null || game.save.name.isBlank() ? "Dresseur" : game.save.name;
    }

    private void go(Step next, String... pages) {
        step = next;
        stepFrame = 0;
        if (pages.length > 0) box.show(List.of(pages), null);
        else box.close();
    }

    private void showPic(BufferedImage next) {
        previousPic = pic;
        pic = next;
        picFade = 0;
    }

    @Override
    public void update(Input in) {
        frame++;
        stepFrame++;
        picFade++;
        if (monTimer >= 0) monTimer++;
        else if (monTimer < -1) monTimer--;
        box.update(in);
        if (leaving) return;

        switch (step) {
            case WELCOME -> {
                if (stepFrame == 50) box.show(List.of("Bonjour ! Bienvenue dans le monde des Pokémon !"), null);
                if (stepFrame > 50 && box.takeButton()) go(Step.THIS_WORLD, "Ce monde…");
            }
            case THIS_WORLD -> {
                if (box.takeButton()) {
                    monTimer = 0;                    // la Poké Ball s'ouvre
                    go(Step.INHABITED);
                }
            }
            case INHABITED -> {
                if (stepFrame == 32) {
                    box.show(List.of("… est peuplé de créatures appelées Pokémon !",
                            "Certains vivent avec les humains comme compagnons, d'autres font équipe avec eux pour combattre."), null);
                }
                if (stepFrame > 32 && box.takeButton()) go(Step.STUDY, "Moi, j'étudie les Pokémon. C'est mon métier.");
            }
            case STUDY -> {
                if (box.takeButton()) {
                    monTimer = -2;                   // retour dans la Poké Ball
                    go(Step.RETURN_MON);
                }
            }
            case RETURN_MON -> {
                if (stepFrame == 70) go(Step.ABOUT, "Mais parle-moi un peu de toi.");
            }
            case ABOUT -> {
                if (box.takeButton()) {
                    showPic(player);
                    go(Step.PLAYER, "Tu t'appelles " + playerName() + ", c'est bien ça ?");
                }
            }
            case PLAYER -> {
                if (box.takeButton()) {
                    showPic(rival);
                    go(Step.RIVAL, "Voici mon petit-fils. Vous êtes rivaux depuis que vous êtes tout petits.",
                            "… Euh, comment s'appelle-t-il déjà ?");
                }
            }
            case RIVAL -> {
                if (box.takeButton()) {
                    box.close();
                    game.push(new NamingScene(game, "Nom du rival", "Blue", rival, n -> {
                        game.save.rivalName = n;
                        game.save.store();
                        go(Step.RIVAL_NAMED, "Ah oui ! Je me souviens maintenant : il s'appelle " + n + " !");
                    }));
                }
            }
            case RIVAL_NAMED -> {
                if (box.takeButton()) {
                    showPic(player);
                    String region = game.data.rom.profile() == GameProfile.FIRERED_LEAFGREEN ? "Kanto" : "Hoenn";
                    go(Step.LETS_GO, playerName() + " ! Ta propre légende Pokémon va commencer à travers " + region + " !",
                            "Un monde de rêves et d'aventures avec les Pokémon t'attend ! Allons-y !");
                }
            }
            case LETS_GO -> {
                if (box.takeButton()) {
                    game.data.audio.fadeOutBgm(60);
                    go(Step.EXIT);
                }
            }
            case EXIT -> {
                if (stepFrame == 70) {
                    leaving = true;
                    game.fadeThen(() -> game.replace(new OverworldScene(game)));
                }
            }
        }
    }

    @Override
    public void render(Graphics2D g) {
        // haute résolution uniquement
    }

    // ------------------------------------------------------------------ scène 240x160

    private void drawStage() {
        Graphics2D s = stage.createGraphics();
        s.setComposite(AlphaComposite.Src);
        if (gfx.background != null) {
            s.drawImage(gfx.background, 0, 0, null);
        } else {
            s.setPaint(new GradientPaint(0, 0, new Color(0x2058B8), 0, H, new Color(0xE8F4EC)));
            s.fillRect(0, 0, W, H);
        }
        s.setComposite(AlphaComposite.SrcOver);

        // Estrade (pas pendant la sortie, où le joueur rétrécit et s'envole)
        boolean exiting = step == Step.EXIT;
        if (gfx.platform != null && !(exiting && stepFrame > 20)) {
            for (int i = 0; i < 3; i++) s.drawImage(gfx.platform[i], PLATFORM_X + i * 32, PLATFORM_Y, null);
        } else if (gfx.platform == null && !exiting) {
            s.setColor(new Color(0xC8EAD8));
            s.fill(new Ellipse2D.Double(PLATFORM_X + 4, PLATFORM_Y + 8, 88, 18));
        }

        // Portraits : fondu enchaîné, comme le jeu (16 frames)
        float t = Math.min(1f, picFade / 16f);
        if (previousPic != null && t < 1f) drawPic(s, previousPic, 1f - t, 1);
        if (exiting) {
            double shrink = Math.max(0.05, 1 - stepFrame / 40.0);
            drawPic(s, pic, 1f, shrink);
        } else {
            drawPic(s, pic, t, 1);
        }

        drawMonAndBall(s);
        if (exiting && stepFrame > 30) {                 // fondu au blanc
            s.setColor(new Color(255, 255, 255, Math.min(255, (stepFrame - 30) * 8)));
            s.fillRect(0, 0, W, H);
        }
        s.dispose();
    }

    private void drawPic(Graphics2D s, BufferedImage img, float alpha, double scale) {
        if (img == null || alpha <= 0) return;
        Composite c = s.getComposite();
        s.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        int w = (int) Math.round(img.getWidth() * scale), h = (int) Math.round(img.getHeight() * scale);
        // pieds sur l'estrade : 64x96 en (88, 16) ; un portrait 64x64 est posé au même niveau
        int x = PIC_X + (64 - w) / 2, y = PIC_Y + 96 - h;
        if (scale < 1) y = PIC_Y + 96 - 48 - h / 2;   // la sortie rétrécit vers le centre
        s.drawImage(img, x, y, w, h, null);
        s.setComposite(c);
    }

    /** Poké Ball qui s'ouvre, éclair blanc et Pokémon qui grandit (et l'inverse au retour). */
    private void drawMonAndBall(Graphics2D s) {
        if (mon == null) return;
        if (monTimer >= 0) {
            int tt = monTimer;
            if (tt < 24) {                                    // la ball arrive et s'ouvre
                drawBall(s, tt < 12 ? 0 : tt < 18 ? 1 : 2, tt < 12 ? (int) (Math.abs(Math.sin(tt / 12.0 * Math.PI)) * -6) : 0, 1f);
            } else {
                double grow = Math.min(1, (tt - 18) / 16.0);
                drawMon(s, grow, (float) Math.max(0, 1 - (tt - 18) / 20.0));
                if (tt < 40) drawBall(s, 2, 0, (float) Math.max(0, 1 - (tt - 24) / 16.0));
                if (tt < 36) flash(s, (tt - 18) / 18.0);
            }
            monOut = true;
        } else if (monTimer < -1) {
            int tt = -monTimer - 2;
            if (tt < 20) {                                    // rappel : rayon rouge, le Pokémon rétrécit
                drawMon(s, 1 - tt / 20.0, (float) Math.min(1, tt / 10.0));
                drawBall(s, 2, 0, 1f);
            } else if (tt < 44) {
                drawBall(s, tt < 26 ? 1 : 0, -(Math.max(0, tt - 26)) / 2, (float) Math.max(0, 1 - Math.max(0, tt - 32) / 12.0));
            }
            monOut = false;
        }
    }

    private void drawMon(Graphics2D s, double scale, float white) {
        if (scale <= 0) return;
        int size = (int) Math.round(64 * scale);
        int cx = MON_X + 32, bottom = MON_Y + 64;
        int x = cx - size / 2, y = bottom - size;
        s.drawImage(mon, x, y, size, size, null);
        if (white > 0) {
            // teinte blanche (sortie de la ball) : on repeint la silhouette
            if (monWhite == null) {
                monWhite = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
                for (int py = 0; py < 64; py++) {
                    for (int px = 0; px < 64; px++) {
                        if (mon.getRGB(px, py) >>> 24 != 0) monWhite.setRGB(px, py, 0xFFFFFFFF);
                    }
                }
            }
            BufferedImage tint = monWhite;
            Composite c = s.getComposite();
            s.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, white)));
            s.drawImage(tint, x, y, size, size, null);
            s.setComposite(c);
        }
    }

    private void drawBall(Graphics2D s, int f, int dy, float alpha) {
        if (alpha <= 0) return;
        Composite c = s.getComposite();
        s.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        if (gfx.ball != null) {
            s.drawImage(gfx.ball[Math.min(f, gfx.ball.length - 1)], BALL_X - 8, BALL_Y - 8 + dy, null);
        } else {
            s.setColor(new Color(0xE84855));
            s.fillArc(BALL_X - 6, BALL_Y - 6 + dy, 12, 12, 0, 180);
            s.setColor(Color.WHITE);
            s.fillArc(BALL_X - 6, BALL_Y - 6 + dy, 12, 12, 180, 180);
        }
        s.setComposite(c);
    }

    private void flash(Graphics2D s, double p) {
        float a = (float) Math.max(0, 1 - p);
        double r = 8 + p * 40;
        s.setPaint(new RadialGradientPaint(new Point2D.Double(BALL_X, BALL_Y), (float) r, new float[]{0f, 1f},
                new Color[]{new Color(1f, 1f, 1f, a), new Color(1f, 1f, 1f, 0f)}));
        s.fill(new Ellipse2D.Double(BALL_X - r, BALL_Y - r, 2 * r, 2 * r));
    }

    @Override
    public void renderHiRes(Graphics2D g, int w, int h) {
        drawStage();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        // Agrandissement entier (pixels nets), en laissant la place de la boîte de texte en bas
        int k = Math.max(1, Math.min(w / W, (int) ((h - 90) / (double) H)));
        int sw = W * k, sh = H * k;
        int ox = (w - sw) / 2, oy = Math.max(0, (h - 130 - sh) / 2);
        // Le fond déborde : ses bords sont prolongés (bandes horizontales du jeu)
        g.drawImage(stage, 0, 0, w, oy, 0, 0, W, 1, null);
        g.drawImage(stage, 0, oy + sh, w, h, 0, H - 1, W, H, null);
        g.drawImage(stage, 0, oy, ox, oy + sh, 0, 0, 1, H, null);
        g.drawImage(stage, ox + sw, oy, w, oy + sh, W - 1, 0, W, H, null);
        g.drawImage(stage, ox, oy, sw, sh, null);
        if (step == Step.EXIT && stepFrame > 30) {
            g.setColor(new Color(255, 255, 255, Math.min(255, (stepFrame - 30) * 8)));
            g.fillRect(0, 0, w, h);
        }
        box.render(g, w, h);
    }
}
