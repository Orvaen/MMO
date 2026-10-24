package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.data.PlayerLook;
import dev.orvaen.mmo.data.PlayerLook.Part;
import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.HiUi;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.Input.Button;
import dev.orvaen.mmo.engine.Scene;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Création (ou modification) du personnage, sur le modèle de l'écran « Nouveau personnage » de PokeMMO :
 * panneau sombre en haute résolution, une ligne par pièce avec menu déroulant et roue de couleur,
 * nom, région de départ, trois aperçus animés (face, dos, profil) et « Créer personnage ».
 * Entièrement utilisable à la souris comme au clavier.
 */
public final class CharacterCreatorScene implements Scene {

    public static final int NAME_MAX = 12;

    // ------------------------------------------------------------------ mise en page (coordonnées logiques)

    private static final double PW = 880, PH = 650, HEAD = 40;
    private static final double LX = 12, LABEL_W = 150, SEL_X = 168, SEL_W = 262, ROW_H = 40, ROW_STEP = 48, ROW_Y0 = 54;
    private static final double WHEEL_CX = SEL_X + SEL_W + 18, WHEEL_R = 9;
    private static final double RX = 476, R_FIELD_X = 632, R_FIELD_W = 236;
    private static final double NAME_Y = 54, REGION_Y = 102;
    private static final double PREVIEW_CX = 672, SPRITE_SCALE = 4;
    private static final double[] PREVIEW_Y = {146, 292, 438};
    private static final double ARROW_SIZE = 40, ARROW_Y = 292 + 44, ARROW_LX = 540, ARROW_RX = 764;
    private static final double BTN_Y = PH - 62, BTN_H = 44, RANDOM_W = 120;
    private static final double PICK_W = 330, PICK_H = 272;
    private static final double ITEM_H = 30;
    private static final int MAX_ITEMS = 9;

    /** Lignes de gauche : 0 = genre, puis les pièces dans l'ordre de PokeMMO. */
    private static final Part[] ROW_PARTS = {null, Part.SKIN, Part.HAT, Part.HAIR, Part.TOP, Part.BOTTOM, Part.SHOES, Part.BAG};
    private static final String[] ROW_LABELS = {"Genre", "Teint de\nPeau", "Chapeau", "Cheveux", "Haut", "Jambes", "Chaussures", "Dos"};
    private static final int ROWS = ROW_PARTS.length;
    private static final int FOCUS_NAME = ROWS, FOCUS_RANDOM = ROWS + 1, FOCUS_CREATE = ROWS + 2, FOCUS_COUNT = ROWS + 3;

    private static final Direction[] TURN = {Direction.DOWN, Direction.LEFT, Direction.UP, Direction.RIGHT};

    private final Game game;
    private final boolean editing;
    private final Random rng = new Random();

    private PlayerLook look;
    private PlayerSprite sprite;
    private String name;
    private int focus;
    private boolean typingName;
    private int frame;
    private int rotation;
    private String error;
    private int errorTimer;
    private String hover = "";

    // Menu déroulant ouvert (-1 : aucun)
    private int dropdownRow = -1;
    private int dropdownScroll;
    private int dropdownCursor;

    // Sélecteur de couleur ouvert
    private Part pickerPart;
    private float pickH = 0.6f, pickS = 0.5f, pickV = 0.8f;
    private String dragging;

    /** Premier écran du jeu : création si le personnage n'existe pas encore, sinon le monde. */
    public static Scene firstScene(Game game) {
        return game.save.hasCharacter() ? new OverworldScene(game) : new CharacterCreatorScene(game, false);
    }

    public CharacterCreatorScene(Game game, boolean editing) {
        this.game = game;
        this.editing = editing;
        this.name = game.save.name == null ? "" : game.save.name;
        setLook(game.save.look != null ? game.save.look : PlayerLook.defaults());
        if (name.isEmpty()) {
            focus = FOCUS_NAME;
            typingName = true;
        }
    }

    // ------------------------------------------------------------------ géométrie

    private record Layout(double scale, double ox, double oy) {
        double lx(int sx) { return (sx - ox) / scale; }
        double ly(int sy) { return (sy - oy) / scale; }
        Point screen(double x, double y) { return new Point((int) Math.round(ox + x * scale), (int) Math.round(oy + y * scale)); }
    }

    private Layout layout() {
        int w = game.viewWidth(), h = game.viewHeight();
        double s = Math.max(0.45, Math.min(2.2, Math.min(w / (PW + 60), h / (PH + 60))));
        return new Layout(s, (w - PW * s) / 2, (h - PH * s) / 2);
    }

    private static double rowY(int row) {
        return ROW_Y0 + row * ROW_STEP;
    }

    private static Rectangle2D selectorRect(int row) {
        return new Rectangle2D.Double(SEL_X, rowY(row), SEL_W, ROW_H);
    }

    private static boolean hasWheel(int row) {
        return ROW_PARTS[row] != null && ROW_PARTS[row] != Part.SKIN;
    }

    private static Rectangle2D wheelRect(int row) {
        double cy = rowY(row) + ROW_H / 2;
        return new Rectangle2D.Double(WHEEL_CX - WHEEL_R - 3, cy - WHEEL_R - 3, WHEEL_R * 2 + 6, WHEEL_R * 2 + 6);
    }

    private static final Rectangle2D NAME_FIELD = new Rectangle2D.Double(R_FIELD_X, NAME_Y, R_FIELD_W, ROW_H);
    private static final Rectangle2D RANDOM_BTN = new Rectangle2D.Double(RX, BTN_Y, RANDOM_W, BTN_H);
    private static final Rectangle2D CREATE_BTN = new Rectangle2D.Double(RX + RANDOM_W + 8, BTN_Y, 868 - RX - RANDOM_W - 8, BTN_H);
    private static final Rectangle2D CLOSE_BTN = new Rectangle2D.Double(PW - 34, 8, 24, 24);
    private static final Rectangle2D ARROW_L = new Rectangle2D.Double(ARROW_LX, ARROW_Y, ARROW_SIZE, ARROW_SIZE);
    private static final Rectangle2D ARROW_R = new Rectangle2D.Double(ARROW_RX, ARROW_Y, ARROW_SIZE, ARROW_SIZE);

    private int itemCount(int row) {
        return ROW_PARTS[row] == null ? PlayerLook.BODIES.length : PlayerLook.presets(ROW_PARTS[row]).length;
    }

    private Rectangle2D dropdownRect(int row) {
        int n = Math.min(MAX_ITEMS, itemCount(row));
        double h = n * ITEM_H + 6;
        double below = rowY(row) + ROW_H + 2;
        double y = below + h > PH - 6 ? rowY(row) - h - 2 : below;
        return new Rectangle2D.Double(SEL_X, y, SEL_W, h);
    }

    private Rectangle2D pickerRect() {
        int row = rowOf(pickerPart);
        double y = Math.max(HEAD + 8, Math.min(PH - PICK_H - 8, rowY(row) - 30));
        return new Rectangle2D.Double(WHEEL_CX + 18, y, PICK_W, PICK_H);
    }

    private Rectangle2D svRect() {
        Rectangle2D p = pickerRect();
        return new Rectangle2D.Double(p.getX() + 14, p.getY() + 42, 214, 168);
    }

    private Rectangle2D hueRect() {
        Rectangle2D p = pickerRect();
        return new Rectangle2D.Double(p.getX() + 238, p.getY() + 42, 22, 168);
    }

    private Rectangle2D originBtn() {
        Rectangle2D p = pickerRect();
        return new Rectangle2D.Double(p.getX() + 14, p.getY() + PICK_H - 46, 120, 34);
    }

    private Rectangle2D okBtn() {
        Rectangle2D p = pickerRect();
        return new Rectangle2D.Double(p.getX() + PICK_W - 104, p.getY() + PICK_H - 46, 90, 34);
    }

    private static int rowOf(Part p) {
        for (int i = 0; i < ROWS; i++) if (ROW_PARTS[i] == p) return i;
        return 0;
    }

    /** Point écran d'un élément (tests) : « selector:3 », « wheel:4 », « item:3:5 », « name », « create »… */
    public Point anchor(String id) {
        Layout l = layout();
        Rectangle2D r = switch (id.split(":")[0]) {
            case "selector" -> selectorRect(Integer.parseInt(id.split(":")[1]));
            case "wheel" -> wheelRect(Integer.parseInt(id.split(":")[1]));
            case "item" -> {
                int row = Integer.parseInt(id.split(":")[1]), i = Integer.parseInt(id.split(":")[2]);
                Rectangle2D d = dropdownRect(row);
                yield new Rectangle2D.Double(d.getX(), d.getY() + 3 + (i - dropdownScroll) * ITEM_H, d.getWidth(), ITEM_H);
            }
            case "name" -> NAME_FIELD;
            case "random" -> RANDOM_BTN;
            case "create" -> CREATE_BTN;
            case "close" -> CLOSE_BTN;
            case "arrowLeft" -> ARROW_L;
            case "arrowRight" -> ARROW_R;
            case "sv" -> svRect();
            case "hue" -> hueRect();
            case "origin" -> originBtn();
            case "ok" -> okBtn();
            default -> throw new IllegalArgumentException(id);
        };
        return l.screen(r.getCenterX(), r.getCenterY());
    }

    // ------------------------------------------------------------------ logique

    @Override
    public void update(Input in) {
        frame++;
        if (errorTimer > 0) errorTimer--;
        Layout l = layout();
        double mx = l.lx(in.mouseX()), my = l.ly(in.mouseY());
        hover = hoverAt(mx, my);

        if (pickerPart != null) {
            updatePicker(in, mx, my);
            return;
        }
        if (dropdownRow >= 0) {
            updateDropdown(in, mx, my);
            return;
        }

        if (in.mousePressed()) {
            if (typingName && !NAME_FIELD.contains(mx, my)) stopTyping(false);
            click(mx, my);
            return;
        }
        if (in.wheel() != 0) {
            for (int row = 0; row < ROWS; row++) {
                if (selectorRect(row).contains(mx, my)) change(row, in.wheel() > 0 ? 1 : -1);
            }
        }

        if (typingName) {
            handleTyping(in.typed());
            return;
        }

        if (in.repeat(Button.DOWN)) focus = (focus + 1) % FOCUS_COUNT;
        if (in.repeat(Button.UP)) focus = (focus - 1 + FOCUS_COUNT) % FOCUS_COUNT;
        if (focus < ROWS) {
            if (in.repeat(Button.RIGHT)) change(focus, 1);
            if (in.repeat(Button.LEFT)) change(focus, -1);
        } else {
            if (in.pressed(Button.RIGHT) || in.pressed(Button.R)) rotation = Math.floorMod(rotation + 1, 4);
            if (in.pressed(Button.LEFT) || in.pressed(Button.L)) rotation = Math.floorMod(rotation - 1, 4);
        }
        if (in.pressed(Button.START)) focus = FOCUS_CREATE;
        if (in.pressed(Button.B) && editing) {
            game.pop();
            return;
        }
        if (in.pressed(Button.A)) activate(focus);
    }

    private void click(double mx, double my) {
        for (int row = 0; row < ROWS; row++) {
            if (selectorRect(row).contains(mx, my)) {
                focus = row;
                openDropdown(row);
                return;
            }
            if (hasWheel(row) && wheelRect(row).contains(mx, my)) {
                focus = row;
                openPicker(ROW_PARTS[row]);
                return;
            }
        }
        if (NAME_FIELD.contains(mx, my)) {
            focus = FOCUS_NAME;
            typingName = true;
        } else if (ARROW_L.contains(mx, my)) {
            rotation = Math.floorMod(rotation - 1, 4);
        } else if (ARROW_R.contains(mx, my)) {
            rotation = Math.floorMod(rotation + 1, 4);
        } else if (RANDOM_BTN.contains(mx, my)) {
            focus = FOCUS_RANDOM;
            setLook(PlayerLook.random(rng));
        } else if (CREATE_BTN.contains(mx, my)) {
            focus = FOCUS_CREATE;
            finish();
        } else if (editing && CLOSE_BTN.contains(mx, my)) {
            game.pop();
        }
    }

    private void activate(int f) {
        if (f < ROWS) openDropdown(f);
        else if (f == FOCUS_NAME) typingName = true;
        else if (f == FOCUS_RANDOM) setLook(PlayerLook.random(rng));
        else finish();
    }

    private void change(int row, int delta) {
        Part p = ROW_PARTS[row];
        if (p == null) setLook(look.withBody(Math.floorMod(look.body() + delta, PlayerLook.BODIES.length)));
        else setLook(look.cyclePreset(p, delta));
    }

    // ------------------------------------------------------------------ menu déroulant

    private void openDropdown(int row) {
        dropdownRow = row;
        Part p = ROW_PARTS[row];
        int current = p == null ? look.body() : Math.max(0, look.presetIndex(p));
        dropdownCursor = current;
        dropdownScroll = Math.max(0, Math.min(current - MAX_ITEMS / 2, itemCount(row) - MAX_ITEMS));
    }

    private void updateDropdown(Input in, double mx, double my) {
        int n = itemCount(dropdownRow);
        int visible = Math.min(MAX_ITEMS, n);
        Rectangle2D d = dropdownRect(dropdownRow);
        if (in.wheel() != 0) dropdownScroll = Math.max(0, Math.min(n - visible, dropdownScroll + in.wheel()));
        int hovered = d.contains(mx, my) ? dropdownScroll + (int) ((my - d.getY() - 3) / ITEM_H) : -1;
        if (hovered >= n) hovered = -1;
        if (hovered >= 0 && (in.mouseX() >= 0)) dropdownCursor = hovered;

        if (in.mousePressed()) {
            if (hovered >= 0) choose(dropdownRow, hovered);
            dropdownRow = -1;
            return;
        }
        if (in.repeat(Button.DOWN)) dropdownCursor = Math.min(n - 1, dropdownCursor + 1);
        if (in.repeat(Button.UP)) dropdownCursor = Math.max(0, dropdownCursor - 1);
        if (dropdownCursor < dropdownScroll) dropdownScroll = dropdownCursor;
        if (dropdownCursor >= dropdownScroll + visible) dropdownScroll = dropdownCursor - visible + 1;
        if (in.pressed(Button.A)) {
            choose(dropdownRow, dropdownCursor);
            dropdownRow = -1;
        } else if (in.pressed(Button.B)) {
            dropdownRow = -1;
        }
    }

    private void choose(int row, int index) {
        Part p = ROW_PARTS[row];
        if (p == null) setLook(look.withBody(index));
        else setLook(look.with(p, PlayerLook.presets(p)[index]));
    }

    // ------------------------------------------------------------------ sélecteur de couleur

    private void openPicker(Part p) {
        pickerPart = p;
        dragging = null;
        int c = look.rgb(p);
        if (c >= 0) {
            float[] hsb = Color.RGBtoHSB(c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF, null);
            pickH = hsb[0];
            pickS = hsb[1];
            pickV = hsb[2];
        }
    }

    private void updatePicker(Input in, double mx, double my) {
        Rectangle2D sv = svRect(), hue = hueRect();
        if (in.mousePressed()) {
            if (sv.contains(mx, my)) dragging = "sv";
            else if (hue.contains(mx, my)) dragging = "hue";
            else if (originBtn().contains(mx, my)) {
                setLook(look.with(pickerPart, PlayerLook.ORIGIN));
                pickerPart = null;
                return;
            } else if (okBtn().contains(mx, my) || !pickerRect().contains(mx, my)) {
                pickerPart = null;
                return;
            }
        }
        if (in.mouseDown() && dragging != null) {
            if (dragging.equals("sv")) {
                pickS = (float) clamp01((mx - sv.getX()) / sv.getWidth());
                pickV = (float) clamp01(1 - (my - sv.getY()) / sv.getHeight());
            } else {
                pickH = (float) clamp01((my - hue.getY()) / hue.getHeight());
            }
            applyPicked();
        }
        if (in.mouseReleased()) dragging = null;

        // Clavier : flèches = teinte / luminosité, A = valider, B = fermer
        if (in.repeat(Button.LEFT)) { pickH = (pickH + 0.98f) % 1f; applyPicked(); }
        if (in.repeat(Button.RIGHT)) { pickH = (pickH + 0.02f) % 1f; applyPicked(); }
        if (in.repeat(Button.UP)) { pickV = (float) clamp01(pickV + 0.04); applyPicked(); }
        if (in.repeat(Button.DOWN)) { pickV = (float) clamp01(pickV - 0.04); applyPicked(); }
        if (in.pressed(Button.A) || in.pressed(Button.B)) pickerPart = null;
    }

    private void applyPicked() {
        setLook(look.with(pickerPart, Color.HSBtoRGB(pickH, pickS, pickV) & 0xFFFFFF));
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    // ------------------------------------------------------------------ nom

    private void handleTyping(String typed) {
        for (char c : typed.toCharArray()) {
            if (c == '\n' || c == '\r' || c == '\t' || c == '\u001b') {
                stopTyping(true);
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

    private void stopTyping(boolean moveOn) {
        typingName = false;
        name = name.strip();
        if (moveOn && !editing && !name.isEmpty()) focus = 0;
    }

    private void finish() {
        name = name.strip();
        if (name.isEmpty()) {
            error = "Choisis d'abord un nom de personnage.";
            errorTimer = 180;
            focus = FOCUS_NAME;
            typingName = true;
            return;
        }
        game.save.name = name;
        game.save.look = look;
        game.save.store();
        if (editing) game.pop();
        else game.fadeThen(() -> game.replace(game.save.hasPosition() ? new OverworldScene(game) : new IntroScene(game)));
    }

    private void setLook(PlayerLook next) {
        look = next;
        sprite = new PlayerSprite(game.data.player, look);
    }

    private String hoverAt(double mx, double my) {
        for (int row = 0; row < ROWS; row++) {
            if (selectorRect(row).contains(mx, my)) return "selector:" + row;
            if (hasWheel(row) && wheelRect(row).contains(mx, my)) return "wheel:" + row;
        }
        if (NAME_FIELD.contains(mx, my)) return "name";
        if (ARROW_L.contains(mx, my)) return "arrowLeft";
        if (ARROW_R.contains(mx, my)) return "arrowRight";
        if (RANDOM_BTN.contains(mx, my)) return "random";
        if (CREATE_BTN.contains(mx, my)) return "create";
        if (CLOSE_BTN.contains(mx, my)) return "close";
        return "";
    }

    // ------------------------------------------------------------------ accès (tests)

    public String name() { return name; }
    public PlayerLook look() { return look; }
    public boolean isTypingName() { return typingName; }
    public int focus() { return focus; }
    public PlayerSprite sprite() { return sprite; }
    public boolean dropdownOpen() { return dropdownRow >= 0; }
    public boolean pickerOpen() { return pickerPart != null; }

    // ------------------------------------------------------------------ rendu

    @Override
    public void render(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, Game.WIDTH, Game.HEIGHT);
    }

    @Override
    public void renderHiRes(Graphics2D g, int w, int h) {
        game.backdrop().render(g, w, h, frame);
        Layout l = layout();
        g.translate(l.ox(), l.oy());
        g.scale(l.scale(), l.scale());

        // Ombre et panneau
        for (int i = 6; i >= 1; i--) HiUi.fill(g, new Color(0, 0, 0, 22), -i * 2, -i * 2 + 6, PW + i * 4, PH + i * 4, 14);
        HiUi.fill(g, HiUi.PANEL, 0, 0, PW, PH, 6);
        HiUi.fill(g, HiUi.HEADER, 0, 0, PW, HEAD, 6);
        g.setColor(HiUi.HEADER);
        g.fill(new Rectangle2D.Double(0, HEAD - 8, PW, 8));
        g.setColor(HiUi.PANEL_BORDER);
        g.fill(new Rectangle2D.Double(0, HEAD, PW, 1));
        HiUi.stroke(g, HiUi.PANEL_BORDER, 0, 0, PW, PH, 6, 1f);
        g.setFont(HiUi.tracked(13.5f, true, 0.12f));
        g.setColor(HiUi.TEXT);
        g.drawString(editing ? "MODIFIER LE PERSONNAGE" : "NOUVEAU PERSONNAGE", 16, 26);
        if (editing) {
            g.setFont(HiUi.font(17, true));
            g.setColor(hover.equals("close") ? Color.WHITE : HiUi.CLOSE);
            HiUi.centered(g, "×", CLOSE_BTN.getX(), CLOSE_BTN.getY() - 1, CLOSE_BTN.getWidth(), CLOSE_BTN.getHeight());
        }

        for (int row = 0; row < ROWS; row++) drawRow(g, row);
        drawNameAndRegion(g);
        drawPortraits(g);
        drawPreviews(g);
        drawButtons(g);

        if (dropdownRow >= 0) drawDropdown(g);
        if (pickerPart != null) drawPicker(g);
    }

    private void labelCell(Graphics2D g, String text, double x, double y) {
        HiUi.fill(g, HiUi.CELL, x, y, LABEL_W, ROW_H, 6);
        g.setFont(HiUi.font(13, false));
        g.setColor(HiUi.TEXT);
        HiUi.centered(g, text, x, y, LABEL_W, ROW_H);
    }

    private void drawRow(Graphics2D g, int row) {
        double y = rowY(row);
        labelCell(g, ROW_LABELS[row], LX, y);
        boolean focused = focus == row && !typingName;
        boolean hovered = hover.equals("selector:" + row);
        Color fill = focused ? HiUi.SELECTOR_FOCUS : hovered ? HiUi.SELECTOR_HOVER : HiUi.SELECTOR;
        HiUi.fill(g, fill, SEL_X, y, SEL_W, ROW_H, 4);
        HiUi.stroke(g, focused ? HiUi.ACCENT : HiUi.SELECTOR_BORDER, SEL_X, y, SEL_W, ROW_H, 4, 1f);

        Part p = ROW_PARTS[row];
        String value = p == null ? PlayerLook.BODIES[look.body()] : look.colorName(p);
        g.setFont(HiUi.font(13, false));
        g.setColor(HiUi.TEXT);
        double textX = SEL_X, textW = SEL_W - 16;
        if (p != null && look.rgb(p) >= 0) {
            // pastille de la couleur choisie à gauche du texte
            double sw = 16;
            double tw = g.getFontMetrics().stringWidth(value) + sw + 8;
            double sx = SEL_X + (SEL_W - 16 - tw) / 2;
            HiUi.fill(g, new Color(look.rgb(p)), sx, y + (ROW_H - sw) / 2, sw, sw, 4);
            HiUi.stroke(g, new Color(255, 255, 255, 90), sx, y + (ROW_H - sw) / 2, sw, sw, 4, 1f);
            textX = sx + sw + 8 - (SEL_W - 16 - g.getFontMetrics().stringWidth(value)) / 2;
            g.setColor(HiUi.TEXT);
        }
        HiUi.centered(g, value, textX, y, textW, ROW_H);
        HiUi.updown(g, HiUi.TEXT, SEL_X + SEL_W - 12, y + ROW_H / 2);

        if (hasWheel(row)) HiUi.colorWheel(g, WHEEL_CX, y + ROW_H / 2, WHEEL_R, hover.equals("wheel:" + row));
    }

    private void drawNameAndRegion(Graphics2D g) {
        labelCell(g, "Nom du\nPersonnage", RX, NAME_Y);
        boolean active = typingName;
        HiUi.fill(g, active ? new Color(0x23253A) : HiUi.CELL, R_FIELD_X, NAME_Y, R_FIELD_W, ROW_H, 4);
        if (active || focus == FOCUS_NAME || hover.equals("name")) {
            HiUi.stroke(g, active ? HiUi.ACCENT : HiUi.SELECTOR_BORDER, R_FIELD_X, NAME_Y, R_FIELD_W, ROW_H, 4, 1.2f);
        }
        g.setFont(HiUi.font(14, false));
        String shown = name.isEmpty() && !active ? "Ton nom…" : name;
        g.setColor(name.isEmpty() && !active ? HiUi.MUTED : HiUi.TEXT);
        double tw = g.getFontMetrics().stringWidth(shown);
        double tx = R_FIELD_X + (R_FIELD_W - tw) / 2;
        HiUi.centered(g, shown, R_FIELD_X, NAME_Y, R_FIELD_W, ROW_H);
        if (active && (frame / 30) % 2 == 0) {
            g.setColor(HiUi.TEXT);
            g.fill(new Rectangle2D.Double(tx + tw + 2, NAME_Y + 11, 1.5, ROW_H - 22));
        }

        labelCell(g, "Région de Départ:", RX, REGION_Y);
        HiUi.fill(g, HiUi.SELECTOR, R_FIELD_X, REGION_Y, R_FIELD_W, ROW_H, 4);
        HiUi.stroke(g, HiUi.SELECTOR_BORDER, R_FIELD_X, REGION_Y, R_FIELD_W, ROW_H, 4, 1f);
        g.setFont(HiUi.font(13, false));
        g.setColor(HiUi.TEXT);
        HiUi.centered(g, regionName(), R_FIELD_X, REGION_Y, R_FIELD_W, ROW_H);

        if (errorTimer > 0 && error != null) {
            g.setFont(HiUi.font(12.5f, true));
            g.setColor(HiUi.ERROR);
            HiUi.centered(g, error, RX, REGION_Y + ROW_H + 4, 868 - RX, 20);
        }
    }

    private String regionName() {
        return switch (game.data.rom.profile()) {
            case FIRERED_LEAFGREEN -> "Kanto";
            case EMERALD -> "Hoenn";
        };
    }

    /** Aperçus 64x64 des sprites de combat (face et dos), sous les réglages. */
    private void drawPortraits(Graphics2D g) {
        double y = rowY(ROWS) + 4, h = PH - y - 12, w = (SEL_X + SEL_W - LX - 8) / 2;
        String[] labels = {"En combat (face)", "En combat (dos)"};
        BufferedImage[] imgs = {sprite.front(), sprite.back()};
        for (int i = 0; i < 2; i++) {
            double x = LX + i * (w + 8);
            HiUi.fill(g, HiUi.CELL, x, y, w, h, 6);
            g.setFont(HiUi.font(12, false));
            g.setColor(HiUi.MUTED);
            HiUi.centered(g, labels[i], x, y + 4, w, 20);
            double size = Math.min(w - 20, h - 34);
            int scale = Math.max(1, (int) (size / 64));
            double s = 64.0 * scale;
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(imgs[i], (int) (x + (w - s) / 2), (int) (y + 26 + (h - 26 - s) / 2), (int) s, (int) s, null);
        }
    }

    private void drawPreviews(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int[] cycle = {0, 1, 0, 2};
        int step = cycle[(frame / 10) % 4];
        Direction[] views = {TURN[rotation], TURN[(rotation + 2) % 4], TURN[(rotation + 1) % 4]};
        double w = 16 * SPRITE_SCALE, h = 32 * SPRITE_SCALE;
        for (int i = 0; i < 3; i++) {
            double x = PREVIEW_CX - w / 2, y = PREVIEW_Y[i];
            g.setColor(new Color(0, 0, 0, 70));
            g.fill(new java.awt.geom.Ellipse2D.Double(PREVIEW_CX - 20, y + h - 14, 40, 12));
            g.drawImage(sprite.overworldFrame(views[i], step), (int) x, (int) y, (int) w, (int) h, null);
        }
        HiUi.pixelArrow(g, ARROW_L.getX(), ARROW_L.getY(), ARROW_SIZE, false, hover.equals("arrowLeft"));
        HiUi.pixelArrow(g, ARROW_R.getX(), ARROW_R.getY(), ARROW_SIZE, true, hover.equals("arrowRight"));
    }

    private void button(Graphics2D g, Rectangle2D r, String text, boolean focused, boolean hovered) {
        HiUi.fill(g, hovered ? new Color(0x30334A) : new Color(0x202232), r.getX(), r.getY(), r.getWidth(), r.getHeight(), 3);
        HiUi.stroke(g, focused ? HiUi.ACCENT : new Color(0x8B8FA8), r.getX(), r.getY(), r.getWidth(), r.getHeight(), 3,
                focused ? 1.6f : 1f);
        g.setFont(HiUi.tracked(13, false, 0.06f));
        g.setColor(HiUi.TEXT);
        HiUi.centered(g, text, r.getX(), r.getY(), r.getWidth(), r.getHeight());
    }

    private void drawButtons(Graphics2D g) {
        button(g, RANDOM_BTN, "ALÉATOIRE", focus == FOCUS_RANDOM, hover.equals("random"));
        button(g, CREATE_BTN, editing ? "ENREGISTRER" : "CRÉER PERSONNAGE", focus == FOCUS_CREATE, hover.equals("create"));
    }

    private void drawDropdown(Graphics2D g) {
        Rectangle2D d = dropdownRect(dropdownRow);
        Part p = ROW_PARTS[dropdownRow];
        int n = itemCount(dropdownRow), visible = Math.min(MAX_ITEMS, n);
        for (int i = 4; i >= 1; i--) HiUi.fill(g, new Color(0, 0, 0, 30), d.getX() - i, d.getY() - i + 3, d.getWidth() + i * 2, d.getHeight() + i * 2, 8);
        HiUi.fill(g, new Color(0x2E3046), d.getX(), d.getY(), d.getWidth(), d.getHeight(), 5);
        HiUi.stroke(g, HiUi.ACCENT, d.getX(), d.getY(), d.getWidth(), d.getHeight(), 5, 1f);
        int current = p == null ? look.body() : look.presetIndex(p);
        g.setFont(HiUi.font(13, false));
        for (int k = 0; k < visible; k++) {
            int i = dropdownScroll + k;
            double y = d.getY() + 3 + k * ITEM_H;
            if (i == dropdownCursor) HiUi.fill(g, HiUi.SELECTOR_HOVER, d.getX() + 3, y, d.getWidth() - 6, ITEM_H, 4);
            double tx = d.getX() + 14;
            if (p != null) {
                int c = PlayerLook.presets(p)[i];
                if (c >= 0) {
                    HiUi.fill(g, new Color(c), tx, y + 8, 14, 14, 3);
                } else {
                    HiUi.stroke(g, HiUi.MUTED, tx, y + 8, 14, 14, 3, 1f);
                    g.setColor(HiUi.MUTED);
                    g.draw(new java.awt.geom.Line2D.Double(tx + 2, y + 20, tx + 12, y + 10));
                }
                tx += 24;
            }
            String label = p == null ? PlayerLook.BODIES[i] : PlayerLook.presetNames(p)[i];
            g.setColor(i == current ? Color.WHITE : HiUi.TEXT);
            g.setFont(HiUi.font(13, i == current));
            g.drawString(label, (float) tx, (float) (y + 20));
            if (i == current) {
                g.setColor(HiUi.ACCENT);
                g.setStroke(new BasicStroke(2f));
                double cx = d.getX() + d.getWidth() - 24;
                g.draw(new java.awt.geom.Line2D.Double(cx, y + 15, cx + 4, y + 19));
                g.draw(new java.awt.geom.Line2D.Double(cx + 4, y + 19, cx + 11, y + 10));
            }
        }
        if (n > visible) {
            double track = d.getHeight() - 10, thumb = track * visible / n;
            double ty = d.getY() + 5 + (track - thumb) * dropdownScroll / (n - visible);
            HiUi.fill(g, new Color(0x5B6090), d.getX() + d.getWidth() - 7, ty, 4, thumb, 4);
        }
    }

    private void drawPicker(Graphics2D g) {
        Rectangle2D p = pickerRect();
        for (int i = 5; i >= 1; i--) HiUi.fill(g, new Color(0, 0, 0, 28), p.getX() - i, p.getY() - i + 4, p.getWidth() + i * 2, p.getHeight() + i * 2, 10);
        HiUi.fill(g, HiUi.PANEL, p.getX(), p.getY(), p.getWidth(), p.getHeight(), 6);
        HiUi.fill(g, HiUi.HEADER, p.getX(), p.getY(), p.getWidth(), 32, 6);
        g.setColor(HiUi.HEADER);
        g.fill(new Rectangle2D.Double(p.getX(), p.getY() + 24, p.getWidth(), 8));
        HiUi.stroke(g, HiUi.ACCENT, p.getX(), p.getY(), p.getWidth(), p.getHeight(), 6, 1f);
        g.setFont(HiUi.tracked(12, true, 0.1f));
        g.setColor(HiUi.TEXT);
        g.drawString(("COULEUR — " + pickerPart.label).toUpperCase(), (float) p.getX() + 14, (float) p.getY() + 21);

        // Carré saturation / luminosité
        Rectangle2D sv = svRect();
        g.setPaint(new GradientPaint((float) sv.getX(), 0, Color.WHITE, (float) sv.getMaxX(), 0,
                Color.getHSBColor(pickH, 1, 1)));
        g.fill(sv);
        g.setPaint(new GradientPaint(0, (float) sv.getY(), new Color(0, 0, 0, 0), 0, (float) sv.getMaxY(), Color.BLACK));
        g.fill(sv);
        double kx = sv.getX() + pickS * sv.getWidth(), ky = sv.getY() + (1 - pickV) * sv.getHeight();
        g.setStroke(new BasicStroke(2f));
        g.setColor(Color.WHITE);
        g.draw(new java.awt.geom.Ellipse2D.Double(kx - 6, ky - 6, 12, 12));
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1f));
        g.draw(new java.awt.geom.Ellipse2D.Double(kx - 7.5, ky - 7.5, 15, 15));

        // Barre de teinte
        Rectangle2D hue = hueRect();
        for (int i = 0; i < (int) hue.getHeight(); i++) {
            g.setColor(Color.getHSBColor((float) (i / hue.getHeight()), 1, 1));
            g.fill(new Rectangle2D.Double(hue.getX(), hue.getY() + i, hue.getWidth(), 1.2));
        }
        double hy = hue.getY() + pickH * hue.getHeight();
        HiUi.stroke(g, Color.WHITE, hue.getX() - 3, hy - 4, hue.getWidth() + 6, 8, 3, 2f);

        // Aperçu de la couleur et code
        double px = hue.getMaxX() + 14, pw = p.getMaxX() - 14 - px;
        int rgb = look.rgb(pickerPart);
        if (rgb >= 0) HiUi.fill(g, new Color(rgb), px, sv.getY(), pw, 46, 5);
        else HiUi.stroke(g, HiUi.MUTED, px, sv.getY(), pw, 46, 5, 1f);
        HiUi.stroke(g, new Color(255, 255, 255, 80), px, sv.getY(), pw, 46, 5, 1f);
        g.setFont(HiUi.font(11.5f, false));
        g.setColor(HiUi.MUTED);
        HiUi.centered(g, rgb >= 0 ? String.format("#%06X", rgb) : "Origine", px - 4, sv.getY() + 50, pw + 8, 18);

        button(g, originBtn(), "D'ORIGINE", false, false);
        button(g, okBtn(), "OK", true, false);
    }
}
