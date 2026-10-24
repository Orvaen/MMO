package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.data.PlayerLook;
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
 * Création (ou modification) du personnage : nom, peau, coiffure, couvre-chef, tenue et couleurs.
 * Aperçu en direct, l'avatar tourne sur lui-même.
 */
public final class CharacterCreatorScene implements Scene {

    public static final int NAME_MAX = 10;

    private static final int ROW_NAME = 0;
    private static final int ROW_RANDOM = 10;
    private static final int ROW_DONE = 11;
    private static final String[] LABELS = {
            "Nom", "Peau", "Coiffure", "Cheveux", "Couvre-chef", "Couleur",
            "Haut", "Couleur haut", "Pantalon", "Chaussures", "Aléatoire", "Terminer"
    };
    private static final Direction[] TURN = {Direction.DOWN, Direction.LEFT, Direction.UP, Direction.RIGHT};

    private final Game game;
    private final boolean editing;   // true : modification depuis le menu, false : début de partie
    private final Random rng = new Random();

    private PlayerLook look;
    private Avatar avatar;
    private String name;
    private int row;
    private boolean typingName;
    private int guard;               // frames pendant lesquelles on ignore Entrée (début/fin de saisie)
    private int frame;
    private int turn;                // index dans TURN
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
        this.look = game.save.look != null ? game.save.look : PlayerLook.defaults();
        this.name = game.save.name == null ? "" : game.save.name;
        this.avatar = new Avatar(look);
        if (name.isEmpty()) startTyping(false);
    }

    // ------------------------------------------------------------------ logique

    @Override
    public void update(Input in) {
        frame++;
        if (guard > 0) guard--;
        if (warningTimer > 0) warningTimer--;
        if (++turnTimer >= 75) {
            turnTimer = 0;
            turn = (turn + 1) % TURN.length;
        }

        if (typingName) {
            handleTyping(in.typed());
            return;
        }

        if (in.pressed(Button.L)) rotate(-1);
        if (in.pressed(Button.R)) rotate(1);
        if (in.repeat(Button.DOWN)) row = (row + 1) % LABELS.length;
        if (in.repeat(Button.UP)) row = (row - 1 + LABELS.length) % LABELS.length;

        int field = fieldOf(row);
        if (field >= 0) {
            if (in.repeat(Button.RIGHT)) setLook(look.cycle(field, 1));
            if (in.repeat(Button.LEFT)) setLook(look.cycle(field, -1));
        }

        if (in.pressed(Button.B) && editing && guard == 0) {
            game.pop(); // annuler la modification
            return;
        }
        if (in.pressed(Button.START)) row = ROW_DONE;

        if (in.pressed(Button.A) && guard == 0) {
            switch (row) {
                case ROW_NAME -> startTyping(true);
                case ROW_RANDOM -> setLook(PlayerLook.random(rng));
                case ROW_DONE -> finish();
                default -> {
                    if (field >= 0) setLook(look.cycle(field, 1));
                }
            }
        }
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
        if (!name.isEmpty() && !editing) row = 1;
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
        avatar = new Avatar(look);
    }

    private void rotate(int delta) {
        turn = Math.floorMod(turn + delta, TURN.length);
        turnTimer = 0;
    }

    private void warn(String text) {
        warning = text;
        warningTimer = 120;
    }

    /** Ligne du menu → champ de {@link PlayerLook} (ou -1). */
    private static int fieldOf(int row) {
        return row >= 1 && row <= 9 ? row - 1 : -1;
    }

    // ------------------------------------------------------------------ accès (tests)

    public String name() { return name; }
    public PlayerLook look() { return look; }
    public boolean isTypingName() { return typingName; }
    public int row() { return row; }

    // ------------------------------------------------------------------ rendu

    @Override
    public void render(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(0x264653), 0, Game.HEIGHT, new Color(0x1D3557)));
        g.fillRect(0, 0, Game.WIDTH, Game.HEIGHT);

        g.setFont(Ui.FONT);
        Ui.text(g, editing ? "MODIFIER MON PERSONNAGE" : "CRÉE TON PERSONNAGE", 8, 13, Color.WHITE);

        drawPreview(g);
        drawOptions(g);

        g.setFont(Ui.SMALL);
        String help = typingName ? "Tape ton nom · Entrée : valider"
                : "Flèches : choisir et changer · A/E : tourner";
        Ui.text(g, help, 8, 157, new Color(0xA8DADC));
    }

    private void drawPreview(Graphics2D g) {
        Ui.box(g, 4, 18, 92, 128);
        // socle
        g.setColor(new Color(0xA8DADC));
        g.fillOval(18, 114, 64, 12);
        g.setColor(new Color(0x7FB7BE));
        g.drawOval(18, 114, 64, 12);

        Graphics2D t = (Graphics2D) g.create();
        t.translate(18, 36);
        t.scale(4, 4);
        int step = turnTimer > 20 && turnTimer < 60 ? ((turnTimer / 10) % 2 == 0 ? 1 : 2) : 0;
        avatar.draw(t, 0, 4, TURN[turn], step, 0);
        t.dispose();

        g.setFont(Ui.FONT);
        String shown = name.isEmpty() && !typingName ? "???" : name;
        int w = g.getFontMetrics().stringWidth(shown);
        Ui.text(g, shown, 50 - w / 2, 141);
        if (typingName && (frame / 20) % 2 == 0) {
            g.setColor(Ui.INK);
            g.fillRect(50 - w / 2 + w + 1, 132, 1, 10);
        }
    }

    private void drawOptions(Graphics2D g) {
        int x = 100, y0 = 27, rowH = 10;
        Ui.box(g, x, 18, 136, 128);
        g.setFont(Ui.SMALL);
        for (int i = 0; i < LABELS.length; i++) {
            int y = y0 + i * rowH;
            boolean sel = i == row;
            if (sel) {
                g.setColor(new Color(0xDDEEF0));
                g.fillRect(x + 4, y - 7, 128, 10);
                Ui.cursor(g, x + 6, y + 1);
            }
            Color labelColor = i >= ROW_RANDOM ? Ui.FRAME : Ui.INK;
            Ui.text(g, LABELS[i], x + 13, y, labelColor);
            drawValue(g, i, x + 132, y, sel);
        }
        if (warningTimer > 0 && warning != null) {
            Ui.box(g, 30, 60, 180, 28);
            g.setFont(Ui.FONT);
            Ui.text(g, warning, 42, 78, Ui.ACCENT);
        }
    }

    /** Valeur à droite de la ligne (texte ou pastille de couleur), alignée sur {@code right}. */
    private void drawValue(Graphics2D g, int row, int right, int y, boolean selected) {
        int field = fieldOf(row);
        if (row == ROW_NAME) {
            String v = typingName ? name + ((frame / 20) % 2 == 0 ? "_" : " ") : (name.isEmpty() ? "—" : name);
            Ui.textRight(g, v, right - 2, y);
            return;
        }
        if (field < 0) return;
        Integer rgb = colorOf(field);
        int valueRight = selected ? right - 8 : right - 2;
        if (rgb != null) {
            g.setColor(Ui.INK);
            g.fillRect(valueRight - 18, y - 6, 18, 7);
            g.setColor(new Color(rgb));
            g.fillRect(valueRight - 17, y - 5, 16, 5);
            if (selected) arrows(g, valueRight - 26, valueRight + 2, y);
        } else {
            String v = labelOf(field);
            Ui.textRight(g, v, valueRight, y);
            if (selected) arrows(g, valueRight - g.getFontMetrics().stringWidth(v) - 8, valueRight + 2, y);
        }
    }

    private static void arrows(Graphics2D g, int left, int right, int y) {
        g.setColor(Ui.ACCENT);
        g.fillPolygon(new int[]{left + 4, left, left + 4}, new int[]{y - 6, y - 3, y}, 3);
        g.fillPolygon(new int[]{right, right + 4, right}, new int[]{y - 6, y - 3, y}, 3);
    }

    private Integer colorOf(int field) {
        return switch (field) {
            case 0 -> look.skinRgb();
            case 2 -> look.hairRgb();
            case 4 -> look.hatRgb();
            case 6 -> look.topRgb();
            case 7 -> look.pantsRgb();
            case 8 -> look.shoesRgb();
            default -> null;
        };
    }

    private String labelOf(int field) {
        return switch (field) {
            case 1 -> PlayerLook.HAIR_STYLES[look.hairStyle()];
            case 3 -> PlayerLook.HATS[look.hat()];
            case 5 -> PlayerLook.TOPS[look.top()];
            default -> "";
        };
    }
}
