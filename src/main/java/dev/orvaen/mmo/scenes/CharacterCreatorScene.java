package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.data.PlayerLook;
import dev.orvaen.mmo.data.PlayerLook.Part;
import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.Input.Button;
import dev.orvaen.mmo.engine.Scene;
import dev.orvaen.mmo.engine.Ui;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.util.Random;

/**
 * Création (ou modification) du personnage : nom, gabarit et teinture de chaque pièce
 * (peau, cheveux, couvre-chef, haut, bas, chaussures, sac), avec aperçu en direct.
 */
public final class CharacterCreatorScene implements Scene {

    public static final int NAME_MAX = 10;

    private static final int ROW_NAME = 0;
    private static final int ROW_BODY = 1;
    private static final int FIRST_PART_ROW = 2;
    private static final Part[] PARTS = Part.values();
    private static final int ROW_RANDOM = FIRST_PART_ROW + PARTS.length;
    private static final int ROW_DONE = ROW_RANDOM + 1;
    private static final int ROWS = ROW_DONE + 1;
    private static final Direction[] TURN = {Direction.DOWN, Direction.LEFT, Direction.UP, Direction.RIGHT};

    private final Game game;
    private final boolean editing;   // true : modification depuis le menu, false : début de partie
    private final Random rng = new Random();

    private PlayerLook look;
    private PlayerSprite sprite;
    private String name;
    private int row;
    private boolean typingName;
    private int guard;               // frames pendant lesquelles on ignore Entrée (début/fin de saisie)
    private int frame;
    private int turn;
    private int turnTimer;
    private String warning;
    private int warningTimer;

    /** Premier écran du jeu : création si le personnage n'existe pas encore, sinon le monde. */
    public static Scene firstScene(Game game) {
        return game.save.hasCharacter() ? new OverworldScene(game) : new CharacterCreatorScene(game, false);
    }

    public CharacterCreatorScene(Game game, boolean editing) {
        this.game = game;
        this.editing = editing;
        this.name = game.save.name == null ? "" : game.save.name;
        setLook(game.save.look != null ? game.save.look : PlayerLook.defaults());
        if (name.isEmpty()) startTyping(false);
    }

    // ------------------------------------------------------------------ logique

    @Override
    public void update(Input in) {
        frame++;
        if (guard > 0) guard--;
        if (warningTimer > 0) warningTimer--;
        if (++turnTimer >= 80) {
            turnTimer = 0;
            turn = (turn + 1) % TURN.length;
        }

        if (typingName) {
            handleTyping(in.typed());
            return;
        }

        if (in.pressed(Button.L)) rotate(-1);
        if (in.pressed(Button.R)) rotate(1);
        if (in.repeat(Button.DOWN)) row = (row + 1) % ROWS;
        if (in.repeat(Button.UP)) row = (row - 1 + ROWS) % ROWS;

        if (in.repeat(Button.RIGHT)) change(1);
        if (in.repeat(Button.LEFT)) change(-1);

        if (in.pressed(Button.B) && editing && guard == 0) {
            game.pop(); // annuler la modification
            return;
        }
        if (in.pressed(Button.START)) row = ROW_DONE;

        if (in.pressed(Button.A) && guard == 0) {
            if (row == ROW_NAME) startTyping(true);
            else if (row == ROW_RANDOM) setLook(PlayerLook.random(rng));
            else if (row == ROW_DONE) finish();
            else change(1);
        }
    }

    private void change(int delta) {
        if (row == ROW_BODY) {
            setLook(look.withBody(Math.floorMod(look.body() + delta, PlayerLook.BODIES.length)));
        } else if (partOf(row) != null) {
            setLook(look.cycle(partOf(row), delta));
        }
    }

    private static Part partOf(int row) {
        int i = row - FIRST_PART_ROW;
        return i >= 0 && i < PARTS.length ? PARTS[i] : null;
    }

    private void handleTyping(String typed) {
        for (char c : typed.toCharArray()) {
            if (c == '\n' || c == '\r') {
                if (guard > 0) continue;
                stopTyping();
                return;
            } else if (c == '\u001b') {
                stopTyping();
                return;
            } else if (c == '\b' || c == 127) {
                if (!name.isEmpty()) name = name.substring(0, name.length() - 1);
            } else if (isNameChar(c) && name.length() < NAME_MAX) {
                if (c == ' ' && name.isEmpty()) continue;
                name += c;
            }
        }
    }

    static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '\'' || c == '.';
    }

    /** @param fromKey vrai si la saisie démarre sur un appui (Entrée) : on ignore alors ce même Entrée. */
    private void startTyping(boolean fromKey) {
        typingName = true;
        row = ROW_NAME;
        guard = fromKey ? 6 : 0;
    }

    private void stopTyping() {
        typingName = false;
        name = name.strip();
        guard = 8; // l'appui sur Entrée qui valide ne doit pas relancer la saisie
        if (!name.isEmpty() && !editing) row = ROW_BODY;
    }

    private void finish() {
        name = name.strip();
        if (name.isEmpty()) {
            warn("Choisis d'abord un nom !");
            startTyping(true);
            return;
        }
        game.save.name = name;
        game.save.look = look;
        game.save.store();
        if (editing) {
            game.pop();
        } else {
            game.fadeThen(() -> game.replace(new OverworldScene(game)));
        }
    }

    private void setLook(PlayerLook next) {
        look = next;
        sprite = new PlayerSprite(game.data.player, look);
    }

    private void rotate(int delta) {
        turn = Math.floorMod(turn + delta, TURN.length);
        turnTimer = 0;
    }

    private void warn(String text) {
        warning = text;
        warningTimer = 120;
    }

    // ------------------------------------------------------------------ accès (tests)

    public String name() { return name; }
    public PlayerLook look() { return look; }
    public boolean isTypingName() { return typingName; }
    public int row() { return row; }
    public PlayerSprite sprite() { return sprite; }

    // ------------------------------------------------------------------ rendu

    @Override
    public void render(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(0x264653), 0, Game.HEIGHT, new Color(0x1D3557)));
        g.fillRect(0, 0, Game.WIDTH, Game.HEIGHT);

        g.setFont(Ui.FONT);
        Ui.text(g, editing ? "MODIFIER MON PERSONNAGE" : "CRÉE TON PERSONNAGE", 8, 13, Color.WHITE);

        drawPreview(g);
        drawOptions(g);
        drawFooter(g);
    }

    private void drawPreview(Graphics2D g) {
        Ui.box(g, 4, 18, 112, 122);
        g.setColor(new Color(0xE3F1EE));
        g.fillRoundRect(9, 23, 102, 112, 6, 6);

        // Portrait de face
        g.setColor(new Color(0xA8DADC));
        g.fillOval(12, 82, 64, 12);
        g.drawImage(sprite.front(), 12, 26, null);

        // Sprite de marche qui tourne sur lui-même (x2)
        g.setColor(new Color(0xA8DADC));
        g.fillOval(76, 110, 30, 8);
        int step = turnTimer > 24 && turnTimer < 64 ? ((turnTimer / 8) % 2 == 0 ? 1 : 2) : 0;
        g.drawImage(sprite.overworldFrame(TURN[turn], step), 75, 52, 32, 64, null);

        g.setFont(Ui.FONT);
        String shown = name.isEmpty() && !typingName ? "???" : name;
        int w = g.getFontMetrics().stringWidth(shown);
        Ui.text(g, shown, 44 - w / 2, 108);
        if (typingName && (frame / 20) % 2 == 0) {
            g.setColor(Ui.INK);
            g.fillRect(44 - w / 2 + w + 1, 99, 1, 10);
        }
        g.setFont(Ui.SMALL);
        Ui.text(g, PlayerLook.BODIES[look.body()], 30, 124, Ui.FRAME);
        if (!sprite.fromRom()) Ui.text(g, "(avatar de secours)", 14, 133, new Color(0x8D99AE));
    }

    private void drawOptions(Graphics2D g) {
        int x = 120, y0 = 28, rowH = 10;
        Ui.box(g, x, 18, 116, 122);
        g.setFont(Ui.SMALL);
        for (int i = 0; i < ROWS; i++) {
            int y = y0 + i * rowH;
            boolean sel = i == row;
            if (sel) {
                g.setColor(new Color(0xDDEEF0));
                g.fillRect(x + 4, y - 7, 108, 10);
                Ui.cursor(g, x + 6, y + 1);
            }
            Color labelColor = i >= ROW_RANDOM ? Ui.FRAME : Ui.INK;
            Ui.text(g, label(i), x + 13, y, labelColor);
            drawValue(g, i, x + 110, y, sel);
        }
    }

    private static String label(int row) {
        if (row == ROW_NAME) return "Nom";
        if (row == ROW_BODY) return "Gabarit";
        if (row == ROW_RANDOM) return "Aléatoire";
        if (row == ROW_DONE) return "Terminer";
        return partOf(row).label;
    }

    /** Valeur à droite de la ligne (texte ou pastille de couleur), alignée sur {@code right}. */
    private void drawValue(Graphics2D g, int row, int right, int y, boolean selected) {
        int valueRight = selected ? right - 6 : right;
        if (row == ROW_NAME) {
            String v = typingName ? name + ((frame / 20) % 2 == 0 ? "_" : " ") : (name.isEmpty() ? "—" : name);
            Ui.textRight(g, v, right, y);
            return;
        }
        if (row == ROW_BODY) {
            String v = PlayerLook.BODIES[look.body()];
            Ui.textRight(g, v, valueRight, y);
            if (selected) arrows(g, valueRight - g.getFontMetrics().stringWidth(v) - 8, valueRight + 2, y);
            return;
        }
        Part p = partOf(row);
        if (p == null) return;
        int rgb = look.rgb(p);
        if (rgb < 0) {
            Ui.textRight(g, "Origine", valueRight, y);
            if (selected) arrows(g, valueRight - g.getFontMetrics().stringWidth("Origine") - 8, valueRight + 2, y);
        } else {
            g.setColor(Ui.INK);
            g.fillRect(valueRight - 20, y - 6, 20, 7);
            g.setColor(new Color(rgb));
            g.fillRect(valueRight - 19, y - 5, 18, 5);
            if (selected) arrows(g, valueRight - 28, valueRight + 2, y);
        }
    }

    private void drawFooter(Graphics2D g) {
        g.setFont(Ui.SMALL);
        String help;
        Part p = partOf(row);
        if (typingName) {
            help = "Tape ton nom · Entrée : valider";
        } else if (p != null) {
            int i = look.colorIndex(p);
            help = p.label + " : " + PlayerLook.colorName(p, i) + "  (" + (i + 1) + "/" + PlayerLook.choices(p) + ")"
                    + "  ·  A/E : tourner";
        } else {
            help = "Flèches : choisir et changer · A/E : tourner";
        }
        Ui.text(g, help, 8, 152, new Color(0xA8DADC));

        if (warningTimer > 0 && warning != null) {
            Ui.box(g, 30, 60, 180, 28);
            g.setFont(Ui.FONT);
            Ui.text(g, warning, 42, 78, Ui.ACCENT);
        }
    }

    private static void arrows(Graphics2D g, int left, int right, int y) {
        g.setColor(Ui.ACCENT);
        g.fillPolygon(new int[]{left + 4, left, left + 4}, new int[]{y - 6, y - 3, y}, 3);
        g.fillPolygon(new int[]{right, right + 4, right}, new int[]{y - 6, y - 3, y}, 3);
    }
}
