package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.HiUi;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.Input.Button;
import dev.orvaen.mmo.engine.Scene;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Écran d'accueil, comme sur la console.
 * <ol>
 *   <li><b>Écran titre</b> : l'image animée de la ROM (logo, Dracaufeu, flammes) et « PRESS START ».</li>
 *   <li><b>Menu</b> (après Start, A ou un clic) : des cadres blancs façon Rouge Feu — « Continuer » avec
 *       le nom du joueur, son temps de jeu et son Pokédex, puis les autres choix.</li>
 * </ol>
 * Si la ROM n'a pas d'écran titre reconnaissable, le menu s'affiche directement sur le fond de secours.
 */
public final class TitleScene implements Scene {

    /** Les choix du menu. */
    private enum Choice { PLAY, CHARACTER, NEW_GAME, QUIT }

    /** Où en est l'écran : image titre, menu, ou question « effacer la partie ? ». */
    private enum Phase { PRESS_START, MENU, CONFIRM_NEW_GAME }

    // Couleurs des cadres de menu de Rouge Feu
    private static final Color BOX_FILL = new Color(0xFFFFFF);
    private static final Color BOX_FRAME = new Color(0x5A6B8C);
    private static final Color BOX_FRAME_LIGHT = new Color(0xA8B8D0);
    private static final Color INK = new Color(0x484848);
    private static final Color INK_SHADOW = new Color(0xD0D0C8);
    private static final Color INK_BLUE = new Color(0x3060B8);
    private static final Color CURSOR = new Color(0xE05040);

    private final Game game;
    private final TitleArt art;
    private final List<Choice> choices = new ArrayList<>();
    private Phase phase;
    private int cursor;
    private int confirmCursor = 1;
    private int frame;

    public TitleScene(Game game) {
        this.game = game;
        this.art = new TitleArt(game.data.titleGraphics());
        this.phase = art.available() ? Phase.PRESS_START : Phase.MENU;
    }

    @Override
    public void onEnter() {
        game.data.audio.playBgm(game.data.songs.title());
        choices.clear();
        choices.add(Choice.PLAY);
        if (game.save.hasCharacter()) {
            choices.add(Choice.CHARACTER);
            choices.add(Choice.NEW_GAME);
        }
        choices.add(Choice.QUIT);
        cursor = Math.min(cursor, choices.size() - 1);
    }

    /** Texte de chaque choix. */
    private String label(Choice c) {
        return switch (c) {
            case PLAY -> game.save.hasCharacter() ? "CONTINUER" : "NOUVELLE PARTIE";
            case CHARACTER -> "MODIFIER LE PERSONNAGE";
            case NEW_GAME -> "NOUVELLE PARTIE";
            case QUIT -> "QUITTER";
        };
    }

    // ------------------------------------------------------------------ logique

    @Override
    public void update(Input in) {
        frame++;
        art.update();
        switch (phase) {
            case PRESS_START -> updatePressStart(in);
            case MENU -> updateMenu(in);
            case CONFIRM_NEW_GAME -> updateConfirm(in);
        }
    }

    private void updatePressStart(Input in) {
        if (in.pressed(Button.START) || in.pressed(Button.A) || in.mousePressed()) {
            game.data.audio.playSe(game.data.songs.select());
            phase = Phase.MENU;
        }
    }

    private void updateMenu(Input in) {
        for (int i = 0; i < choices.size(); i++) {
            if (button(i).contains(in.mouseX(), in.mouseY())) {
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
        else if (in.pressed(Button.B) && art.available()) phase = Phase.PRESS_START;
    }

    private void updateConfirm(Input in) {
        for (int i = 0; i < 2; i++) {
            if (confirmButton(i).contains(in.mouseX(), in.mouseY())) {
                confirmCursor = i;
                if (in.mousePressed()) {
                    confirm(i == 0);
                    return;
                }
            }
        }
        if (in.repeat(Button.LEFT) || in.repeat(Button.RIGHT)) confirmCursor = 1 - confirmCursor;
        if (in.pressed(Button.B)) confirm(false);
        else if (in.pressed(Button.A)) confirm(confirmCursor == 0);
    }

    private void select(Choice c) {
        game.data.audio.playSe(game.data.songs.select());
        switch (c) {
            case PLAY -> game.fadeThen(() -> game.replace(CharacterCreatorScene.firstScene(game)));
            case CHARACTER -> game.push(new CharacterCreatorScene(game, true));
            case NEW_GAME -> {
                phase = Phase.CONFIRM_NEW_GAME;
                confirmCursor = 1;
            }
            case QUIT -> game.quit();
        }
    }

    /** Réponse à « Commencer une nouvelle partie ? » : oui efface la sauvegarde (une copie est gardée). */
    private void confirm(boolean yes) {
        phase = Phase.MENU;
        if (!yes) return;
        game.save.reset();
        game.fadeThen(() -> game.replace(new CharacterCreatorScene(game, false)));
    }

    // ------------------------------------------------------------------ disposition (positions à l'écran)

    private double uiScale() {
        int w = game.viewWidth(), h = game.viewHeight();
        return Math.max(0.6, Math.min(1.8, Math.min(w / 1100.0, h / 720.0)));
    }

    /** Hauteur d'un cadre : « Continuer » est plus haut car il montre les infos de la partie. */
    private double boxHeight(Choice c) {
        boolean details = c == Choice.PLAY && game.save.hasCharacter();
        return (details ? 92 : 40) * uiScale();
    }

    /**
     * Rectangle du cadre n° i. Les cadres sont empilés sous le logo, à gauche du Pokémon de la boîte
     * (ou au centre de l'écran quand il n'y a pas d'écran titre de la ROM).
     */
    private Rectangle2D button(int i) {
        int w = game.viewWidth(), h = game.viewHeight();
        double s = uiScale(), gap = 8 * s, bw = 420 * s;
        double total = -gap;
        for (Choice c : choices) total += boxHeight(c) + gap;
        double x, y;
        if (art.available()) {
            java.awt.Rectangle screen = TitleArt.screenRect(w, h);
            x = Math.max(8, screen.x + screen.width * 0.33 - bw / 2);
            y = Math.min(screen.y + screen.height * 0.60, h - total - 8 * s);
        } else {
            x = (w - bw) / 2;
            y = (h - total) / 2;
        }
        for (int k = 0; k < i; k++) y += boxHeight(choices.get(k)) + gap;
        return new Rectangle2D.Double(x, y, bw, boxHeight(choices.get(i)));
    }

    private Rectangle2D confirmButton(int i) {
        double s = uiScale(), bw = 170 * s, bh = 44 * s;
        double cx = game.viewWidth() / 2.0, y = game.viewHeight() / 2.0 + 24 * s;
        return new Rectangle2D.Double(i == 0 ? cx - bw - 10 * s : cx + 10 * s, y, bw, bh);
    }

    /** Point écran d'un choix du menu (tests) : « PLAY », « CHARACTER », « NEW_GAME », « QUIT ». */
    public Point anchor(String choice) {
        Rectangle2D r = button(choices.indexOf(Choice.valueOf(choice)));
        return new Point((int) r.getCenterX(), (int) r.getCenterY());
    }

    /** Bouton de la confirmation (tests) : 0 « Oui », 1 « Non ». */
    public Point confirmAnchor(int i) {
        Rectangle2D r = confirmButton(i);
        return new Point((int) r.getCenterX(), (int) r.getCenterY());
    }

    /** Vrai tant que l'écran attend « PRESS START » (tests). */
    public boolean waitingForStart() {
        return phase == Phase.PRESS_START;
    }

    // ------------------------------------------------------------------ dessin

    @Override
    public void render(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, Game.WIDTH, Game.HEIGHT);
    }

    @Override
    public void renderHiRes(Graphics2D g, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (art.available()) {
            art.render(g, w, h, phase == Phase.PRESS_START);
        } else {
            game.backdrop().render(g, w, h, frame);
        }
        if (phase == Phase.PRESS_START) return;

        g.setColor(new Color(0, 0, 0, 60));         // l'écran titre reste visible derrière le menu
        g.fillRect(0, 0, w, h);
        for (int i = 0; i < choices.size(); i++) drawChoice(g, i);
        if (phase == Phase.CONFIRM_NEW_GAME) drawConfirm(g, w, h);
    }

    private void drawChoice(Graphics2D g, int i) {
        Choice c = choices.get(i);
        Rectangle2D r = button(i);
        double s = uiScale();
        boolean on = i == cursor;
        drawGbaBox(g, r.getX(), r.getY(), r.getWidth(), r.getHeight(), s, on);

        double x = r.getX() + 34 * s, y = r.getY() + 27 * s;
        shadowText(g, label(c), x, y, (float) (18 * s), INK, s);
        if (on) drawCursor(g, r.getX() + 14 * s, y - 7 * s, s);

        if (c == Choice.PLAY && game.save.hasCharacter()) {
            // Infos de la partie, comme le cadre « CONTINUER » du jeu
            long minutes = game.save.playFrames / 3600;
            double col2 = r.getWidth() / 2;
            float size = (float) (15 * s);
            shadowText(g, "JOUEUR", x, y + 28 * s, size, INK_BLUE, s);
            shadowText(g, game.save.name, x + 96 * s, y + 28 * s, size, INK_BLUE, s);
            shadowText(g, "TEMPS", x + col2, y + 28 * s, size, INK_BLUE, s);
            shadowText(g, String.format("%d:%02d", minutes / 60, minutes % 60), x + col2 + 76 * s, y + 28 * s, size, INK_BLUE, s);
            shadowText(g, "POKéDEX", x, y + 52 * s, size, INK_BLUE, s);
            shadowText(g, String.valueOf(game.save.caught.size()), x + 96 * s, y + 52 * s, size, INK_BLUE, s);
        }
    }

    private void drawConfirm(Graphics2D g, int w, int h) {
        double s = uiScale();
        double pw = 460 * s, ph = 150 * s, px = (w - pw) / 2, py = h / 2.0 - 96 * s;
        g.setColor(new Color(0, 0, 0, 110));
        g.fillRect(0, 0, w, h);
        drawGbaBox(g, px, py, pw, ph, s, false);
        shadowText(g, "Effacer la partie de " + game.save.name + " ?", px + 26 * s, py + 40 * s, (float) (18 * s), INK, s);
        g.setFont(HiUi.font((float) (13 * s), false));
        g.setColor(new Color(0x707070));
        g.drawString("(une copie de secours est gardée)", (float) (px + 26 * s), (float) (py + 66 * s));
        String[] labels = {"OUI", "NON"};
        for (int i = 0; i < 2; i++) {
            Rectangle2D r = confirmButton(i);
            boolean on = i == confirmCursor;
            drawGbaBox(g, r.getX(), r.getY(), r.getWidth(), r.getHeight(), s, on);
            shadowText(g, labels[i], r.getX() + 40 * s, r.getY() + 29 * s, (float) (17 * s), INK, s);
            if (on) drawCursor(g, r.getX() + 18 * s, r.getY() + 22 * s, s);
        }
    }

    /** Cadre blanc à double bordure, comme les menus du jeu. */
    private static void drawGbaBox(Graphics2D g, double x, double y, double w, double h, double s, boolean focused) {
        double arc = 12 * s;
        g.setColor(BOX_FILL);
        g.fill(new RoundRectangle2D.Double(x, y, w, h, arc, arc));
        g.setStroke(new BasicStroke((float) (4 * s)));
        g.setColor(focused ? CURSOR : BOX_FRAME);
        g.draw(new RoundRectangle2D.Double(x + 2 * s, y + 2 * s, w - 4 * s, h - 4 * s, arc, arc));
        g.setStroke(new BasicStroke((float) (2 * s)));
        g.setColor(BOX_FRAME_LIGHT);
        g.draw(new RoundRectangle2D.Double(x + 6 * s, y + 6 * s, w - 12 * s, h - 12 * s, arc * 0.6, arc * 0.6));
        g.setStroke(new BasicStroke(1f));
    }

    /** Texte avec l'ombre décalée typique de la police du jeu. */
    private static void shadowText(Graphics2D g, String text, double x, double y, float size, Color ink, double s) {
        g.setFont(HiUi.font(size, true));
        g.setColor(INK_SHADOW);
        g.drawString(text, (float) (x + 2 * s), (float) (y + 2 * s));
        g.setColor(ink);
        g.drawString(text, (float) x, (float) y);
    }

    /** Petite flèche ▶ devant le choix sélectionné. */
    private static void drawCursor(Graphics2D g, double x, double y, double s) {
        Path2D p = new Path2D.Double();
        p.moveTo(x, y - 7 * s);
        p.lineTo(x + 10 * s, y);
        p.lineTo(x, y + 7 * s);
        p.closePath();
        g.setColor(INK);
        g.fill(p);
    }
}
