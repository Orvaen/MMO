package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.battle.BattleGraphics;
import dev.orvaen.mmo.battle.Move;
import dev.orvaen.mmo.battle.Pokemon;
import dev.orvaen.mmo.data.PokemonTexts;
import dev.orvaen.mmo.data.Species;
import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.HiUi;
import dev.orvaen.mmo.engine.HiWindow;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.UiIcons;
import dev.orvaen.mmo.engine.UiIcons.Icon;
import dev.orvaen.mmo.engine.UiWindow;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;

/**
 * Résumé d'un Pokémon de l'équipe, en fenêtre façon PokeMMO (on peut jouer pendant qu'elle est ouverte).
 * <p>
 * Onglets en icônes dans la barre de titre :
 * <ul>
 *   <li><b>Infos</b> : n° du Pokédex, nom, nature et son effet, talent, dresseur d'origine, expérience ;</li>
 *   <li><b>Stats</b> : chaque stat en barre de couleur, avec sa base ;</li>
 *   <li><b>IV / EV</b> : un hexagone qui montre d'un coup d'œil les points forts du Pokémon ;</li>
 *   <li><b>Attaques</b> : type, catégorie, PP, puissance, précision.</li>
 * </ul>
 * À droite, le Pokémon sur un vrai fond de combat lu dans la ROM (choisi selon son type), avec son niveau
 * et ses types.
 * <p>
 * Clavier (comme le résumé du jeu) : ← → changent d'onglet, ↑ ↓ changent de Pokémon, B ou Échap ferme.
 * Souris : clic sur les icônes, ‹ › ou la molette.
 */
public final class SummaryWindow implements UiWindow {

    public static final String ID = "resume";

    private static final Icon[] TAB_ICONS = {Icon.CARD, Icon.BARS, Icon.HEXAGON, Icon.BOLT};
    private static final String[] TAB_NAMES = {"Infos", "Stats", "IV / EV", "Attaques"};
    /** Ordre d'affichage des stats (comme le jeu) : PV, Att, Déf, Att.Spé, Déf.Spé, Vit. */
    private static final int[] STAT_ORDER = {Pokemon.HP, Pokemon.ATTACK, Pokemon.DEFENSE, Pokemon.SP_ATTACK,
            Pokemon.SP_DEFENSE, Pokemon.SPEED};
    private static final String[] STAT_NAMES = {"PV", "Attaque", "Défense", "Vitesse", "Att. Spé.", "Déf. Spé."};
    private static final String[] STAT_SHORT = {"PV", "Att", "Déf", "Vit", "A.Spé", "D.Spé"};
    /** Une couleur par stat (index de la ROM). */
    private static final Color[] STAT_COLORS = {new Color(0x69DC8E), new Color(0xF5A25D), new Color(0xF4D35E),
            new Color(0xF26D9A), new Color(0x7FA8F5), new Color(0x8DDBD0)};
    /** Sommets de l'hexagone, dans le sens des aiguilles d'une montre depuis le haut. */
    private static final int[] RADAR_ORDER = {Pokemon.HP, Pokemon.ATTACK, Pokemon.DEFENSE, Pokemon.SPEED,
            Pokemon.SP_DEFENSE, Pokemon.SP_ATTACK};
    private static final String[] STATUS = {"", "SOM", "PSN", "BRL", "GEL", "PAR"};
    private static final Color UP = new Color(0xF2788F), DOWN = new Color(0x7FA8F5), GOLD = new Color(0xF2C94C),
            IV_COLOR = new Color(0xF2C94C), EV_COLOR = new Color(0x8C93E6);

    private final Game game;
    private final HiWindow win;
    private final PartyBar.Team team;
    private int index;
    private int tab;
    private int frame;

    public SummaryWindow(Game game, int index) {
        this.game = game;
        this.win = new HiWindow(game, "resume-pokemon-v2", 640, 370, 560, 340);
        this.team = new PartyBar.Team(game);
        this.index = index;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public HiWindow frame() {
        return win;
    }

    /** Montre un autre Pokémon de l'équipe (clic sur la barre d'équipe alors que la fenêtre est ouverte). */
    public void show(int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }

    public int tab() {
        return tab;
    }

    /** Point écran d'un élément (tests) : « close », « tab0 » à « tab3 », « prev », « next ». */
    public Point anchor(String id) {
        Rectangle2D r = switch (id) {
            case "prev" -> arrow(false);
            case "next" -> arrow(true);
            case "tab0", "tab1", "tab2", "tab3" -> tabRect(id.charAt(3) - '0');
            default -> win.closeButton();
        };
        return new Point((int) r.getCenterX(), (int) r.getCenterY());
    }

    // ------------------------------------------------------------------ disposition

    /** Onglet n° i : une icône dans la barre de titre. */
    private Rectangle2D tabRect(int i) {
        Rectangle2D h = win.header();
        double s = win.scale(), size = 30 * s;
        return new Rectangle2D.Double(h.getX() + 8 * s + i * (size + 4 * s), h.getY() + (h.getHeight() - size) / 2, size, size);
    }

    /** Colonne de droite : la carte du Pokémon (fond, sprite, nom). */
    private Rectangle2D card() {
        Rectangle2D c = win.content();
        double s = win.scale(), w = Math.min(250 * s, c.getWidth() * 0.42);
        return new Rectangle2D.Double(c.getMaxX() - 12 * s - w, c.getY() + 12 * s, w, c.getHeight() - 24 * s);
    }

    /** Partie image de la carte (le reste : PV et objet tenu). */
    private Rectangle2D scene() {
        Rectangle2D k = card();
        double s = win.scale();
        return new Rectangle2D.Double(k.getX(), k.getY(), k.getWidth(), k.getHeight() - 62 * s);
    }

    private Rectangle2D arrow(boolean next) {
        Rectangle2D sc = scene();
        double s = win.scale(), size = 22 * s;
        double x = sc.getMaxX() - 8 * s - (next ? size : 2 * size + 4 * s);
        return new Rectangle2D.Double(x, sc.getMaxY() - 8 * s - size, size, size);
    }

    /** Colonne de gauche : le contenu de l'onglet. */
    private Rectangle2D body() {
        Rectangle2D c = win.content(), k = card();
        double s = win.scale();
        return new Rectangle2D.Double(c.getX() + 14 * s, k.getY(), k.getX() - c.getX() - 28 * s, k.getHeight());
    }

    // ------------------------------------------------------------------ logique

    @Override
    public Input.Button[] usesKeys() {
        return new Input.Button[]{Input.Button.LEFT, Input.Button.RIGHT, Input.Button.UP, Input.Button.DOWN, Input.Button.B};
    }

    @Override
    public boolean update(Input in, boolean focused) {
        frame++;
        List<Pokemon> mons = team.get();
        if (mons.isEmpty()) return false;                  // plus personne dans l'équipe
        index = Math.floorMod(index, mons.size());
        if (!focused) return true;
        // clavier
        if (in.repeat(Input.Button.RIGHT)) tab = (tab + 1) % TAB_ICONS.length;
        if (in.repeat(Input.Button.LEFT)) tab = (tab + TAB_ICONS.length - 1) % TAB_ICONS.length;
        if (in.repeat(Input.Button.DOWN)) index = Math.floorMod(index + 1, mons.size());
        if (in.repeat(Input.Button.UP)) index = Math.floorMod(index - 1, mons.size());
        if (in.pressed(Input.Button.B)) return false;
        int mx = in.mouseX(), my = in.mouseY();
        // les onglets sont dans la barre de titre : on les teste avant le déplacement de la fenêtre
        if (in.mousePressed()) {
            for (int i = 0; i < TAB_ICONS.length; i++) {
                if (tabRect(i).contains(mx, my)) {
                    tab = i;
                    return true;
                }
            }
        }
        if (win.update(in)) return true;
        if (in.mousePressed()) {
            if (win.closeButton().contains(mx, my)) return false;
            if (arrow(false).contains(mx, my)) index = Math.floorMod(index - 1, mons.size());
            if (arrow(true).contains(mx, my)) index = Math.floorMod(index + 1, mons.size());
        }
        if (in.wheel() != 0 && win.bounds().contains(mx, my)) index = Math.floorMod(index + Integer.signum(in.wheel()), mons.size());
        return true;
    }

    // ------------------------------------------------------------------ dessin

    @Override
    public void render(Graphics2D g, int w, int h, boolean focused) {
        List<Pokemon> mons = team.get();
        if (mons.isEmpty()) return;
        Pokemon p = mons.get(Math.floorMod(index, mons.size()));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        win.paintFrame(g, "Résumé", true);
        double s = win.scale();
        int mx = game.input().mouseX(), my = game.input().mouseY();

        // onglets : icône claire pour l'onglet ouvert, avec un trait dessous
        int hover = -1;
        for (int i = 0; i < TAB_ICONS.length; i++) {
            Rectangle2D r = tabRect(i);
            boolean on = i == tab, over = r.contains(mx, my);
            if (over) hover = i;
            if (on || over) HiUi.fill(g, on ? new Color(0x2E3150) : new Color(0x262838), r.getX(), r.getY(), r.getWidth(), r.getHeight(), 7 * s);
            double is = 16 * s;
            UiIcons.draw(g, TAB_ICONS[i], r.getCenterX() - is / 2, r.getCenterY() - is / 2, is,
                    on ? HiUi.ACCENT : over ? HiUi.TEXT : HiUi.MUTED);
            if (on) {
                g.setColor(HiUi.ACCENT);
                g.fill(new RoundRectangle2D.Double(r.getX() + 8 * s, r.getMaxY() - 2.5 * s, r.getWidth() - 16 * s, 2.5 * s, 2 * s, 2 * s));
            }
        }

        drawCard(g, p, s, mons.size());
        Rectangle2D b = body();
        switch (tab) {
            case 1 -> drawStats(g, p, b, s);
            case 2 -> drawRadar(g, p, b, s);
            case 3 -> drawMoves(g, p, b, s);
            default -> drawInfo(g, p, b, s);
        }
        if (hover >= 0) {
            Rectangle2D r = tabRect(hover);
            UiIcons.tooltip(g, TAB_NAMES[hover], r.getCenterX(), r.getMaxY() + 6 * s, s);
        }
    }

    // ------------------------------------------------------------------ la carte du Pokémon

    /** Fond de combat selon le type : eau, montagne, grotte, bâtiment, hautes herbes, sinon prairie. */
    private static int terrainFor(Species sp) {
        return switch (sp.type1()) {
            case 11, 15 -> BattleGraphics.WATER;                 // Eau, Glace
            case 4, 5 -> BattleGraphics.MOUNTAIN;               // Sol, Roche
            case 7, 17, 3 -> BattleGraphics.CAVE;               // Spectre, Ténèbres, Poison
            case 8, 13, 14 -> BattleGraphics.BUILDING;          // Acier, Électrik, Psy
            case 6, 12 -> BattleGraphics.LONG_GRASS;            // Insecte, Plante
            default -> BattleGraphics.GRASS;
        };
    }

    private void drawCard(Graphics2D g, Pokemon p, double s, int teamSize) {
        Species sp = p.base();
        Rectangle2D k = card(), sc = scene();
        Shape clip = new RoundRectangle2D.Double(sc.getX(), sc.getY(), sc.getWidth(), sc.getHeight(), 10 * s, 10 * s);
        Graphics2D q = (Graphics2D) g.create();
        q.clip(clip);
        q.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // fond : le côté adverse d'un vrai fond de combat de la ROM, agrandi pour remplir la carte
        BufferedImage bg = null;
        try {
            bg = game.data.battleGraphics().terrain(terrainFor(sp));
        } catch (RuntimeException ignored) {
            // pas de fond : dégradé
        }
        Color tint = new Color(Species.typeColor(sp.type1()));
        if (bg != null) {
            int srcX = bg.getWidth() / 2, srcW = bg.getWidth() - srcX, srcH = bg.getHeight();
            double k2 = Math.max(sc.getWidth() / srcW, sc.getHeight() / srcH);
            double dw = srcW * k2, dh = srcH * k2;
            q.drawImage(bg, (int) Math.round(sc.getCenterX() - dw / 2), (int) Math.round(sc.getCenterY() - dh / 2),
                    (int) Math.round(sc.getCenterX() + dw / 2), (int) Math.round(sc.getCenterY() + dh / 2),
                    srcX, 0, srcX + srcW, srcH, null);
        } else {
            q.setPaint(new GradientPaint((float) sc.getX(), (float) sc.getY(), mix(tint, HiUi.CELL, 0.4),
                    (float) sc.getX(), (float) sc.getMaxY(), mix(tint, HiUi.CELL, 0.85)));
            q.fill(sc);
            q.setColor(new Color(255, 255, 255, 30));
            q.fill(new Ellipse2D.Double(sc.getX() + sc.getWidth() * 0.15, sc.getY() + sc.getHeight() * 0.68,
                    sc.getWidth() * 0.7, sc.getHeight() * 0.16));
        }
        // voiles sombres en haut et en bas : le texte reste lisible sur n'importe quel fond
        q.setPaint(new GradientPaint(0, (float) sc.getY(), new Color(10, 11, 20, 170), 0, (float) (sc.getY() + 44 * s), new Color(10, 11, 20, 0)));
        q.fill(new Rectangle2D.Double(sc.getX(), sc.getY(), sc.getWidth(), 44 * s));
        q.setPaint(new GradientPaint(0, (float) (sc.getMaxY() - 46 * s), new Color(10, 11, 20, 0), 0, (float) sc.getMaxY(), new Color(10, 11, 20, 190)));
        q.fill(new Rectangle2D.Double(sc.getX(), sc.getMaxY() - 46 * s, sc.getWidth(), 46 * s));

        // le Pokémon (agrandi d'un nombre entier : pixels nets), qui « respire » doucement
        BufferedImage img = game.data.species.frontSprite(p.species);
        if (img != null) {
            int zoom = (int) Math.max(1, Math.floor(Math.min(sc.getWidth() * 0.85, sc.getHeight() * 0.8) / 64.0));
            int size = 64 * zoom;
            double breathe = Math.round(Math.sin(frame / 40.0) * 1.5) * zoom * 0.5;
            if (p.fainted()) q.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
            q.drawImage(img, (int) Math.round(sc.getCenterX() - size / 2.0),
                    (int) Math.round(sc.getY() + sc.getHeight() * 0.56 - size / 2.0 + breathe), size, size, null);
            q.setComposite(AlphaComposite.SrcOver);
        }
        q.dispose();
        HiUi.stroke(g, new Color(255, 255, 255, 30), sc.getX(), sc.getY(), sc.getWidth(), sc.getHeight(), 10 * s, 1f);

        // en haut : Poké Ball + nom, niveau
        double pad = 10 * s;
        UiIcons.draw(g, Icon.BALL, sc.getX() + pad, sc.getY() + pad, 14 * s, new Color(0xEB5757));
        g.setFont(HiUi.font((float) (14 * s), true));
        String lv = "Niv. " + p.level;
        double lvW = g.getFontMetrics().stringWidth(lv);
        g.setColor(Color.WHITE);
        g.drawString(HiUi.ellipsis(g, name(p), sc.getWidth() - 3 * pad - lvW - 20 * s),
                (float) (sc.getX() + pad + 20 * s), (float) (sc.getY() + pad + 12 * s));
        g.drawString(lv, (float) (sc.getMaxX() - pad - lvW), (float) (sc.getY() + pad + 12 * s));

        // en bas : types, statut, et ‹ n/6 ›
        double ty = sc.getMaxY() - 8 * s - 20 * s, tx = sc.getX() + pad;
        tx += chip(g, Species.typeName(sp.type1()), new Color(Species.typeColor(sp.type1())), tx, ty, s) + 5 * s;
        if (sp.hasSecondType()) tx += chip(g, Species.typeName(sp.type2()), new Color(Species.typeColor(sp.type2())), tx, ty, s) + 5 * s;
        if (p.status > 0 && p.status < STATUS.length) chip(g, STATUS[p.status], new Color(0xB8577A), tx, ty, s);
        else if (p.fainted()) chip(g, "K.O.", new Color(0x8A3A4A), tx, ty, s);
        int mx = game.input().mouseX(), my = game.input().mouseY();
        if (teamSize > 1) {
            for (boolean next : new boolean[]{false, true}) {
                Rectangle2D r = arrow(next);
                HiUi.fill(g, r.contains(mx, my) ? new Color(255, 255, 255, 60) : new Color(0, 0, 0, 110),
                        r.getX(), r.getY(), r.getWidth(), r.getHeight(), r.getWidth());
                g.setColor(Color.WHITE);
                g.setFont(HiUi.font((float) (14 * s), true));
                HiUi.centered(g, next ? "›" : "‹", r.getX(), r.getY() - s, r.getWidth(), r.getHeight());
            }
        }

        // sous l'image : PV et objet tenu
        double ry = sc.getMaxY() + 12 * s;
        UiIcons.draw(g, Icon.HEART, k.getX() + 2 * s, ry, 14 * s, new Color(0xF26D7D));
        double barX = k.getX() + 24 * s, barW = k.getWidth() - 24 * s - 70 * s;
        double ratio = p.maxHp() <= 0 ? 0 : Math.max(0, Math.min(1, p.hp / (double) p.maxHp()));
        HiUi.fill(g, HiUi.CELL, barX, ry + 4 * s, barW, 6 * s, 6 * s);
        HiUi.fill(g, PartyBar.hpColor(ratio), barX, ry + 4 * s, Math.max(6 * s * Math.signum(ratio), barW * ratio), 6 * s, 6 * s);
        g.setFont(HiUi.font((float) (12 * s), true));
        g.setColor(HiUi.TEXT);
        right(g, p.hp + " / " + p.maxHp(), k.getMaxX(), ry + 11.5 * s);
        ry += 24 * s;
        UiIcons.draw(g, Icon.BAG, k.getX() + 2 * s, ry, 14 * s, HiUi.MUTED);
        g.setFont(HiUi.font((float) (12 * s), false));
        g.setColor(HiUi.MUTED);
        g.drawString("Objet tenu", (float) (k.getX() + 24 * s), (float) (ry + 11.5 * s));
        g.setFont(HiUi.font((float) (12 * s), true));
        g.setColor(HiUi.TEXT);
        right(g, "Aucun", k.getMaxX(), ry + 11.5 * s);
    }

    // ------------------------------------------------------------------ onglets

    private void drawInfo(Graphics2D g, Pokemon p, Rectangle2D b, double s) {
        PokemonTexts t = game.data.texts();
        Species sp = p.base();
        String ot = game.save.name == null || game.save.name.isBlank() ? "Dresseur" : game.save.name;
        String next = p.level >= 100 ? "Niveau maximum !" : fmt(p.expForLevel(p.level + 1) - p.exp) + " pts";
        Object[][] rows = {
                {Icon.HASH, "Pokédex", String.format("N° %03d", game.data.species.nationalNumber(p.species))},
                {Icon.TAG, "Nom", name(p) + (p.name().equals(sp.name()) ? "" : "  · " + PokemonTexts.pretty(sp.name()))},
                {Icon.LEAF, "Nature", t.nature(p.nature)},
                {Icon.STAR, "Talent", t.ability(p.ability())},
                {Icon.PERSON, "DO", ot + "   N° " + String.format("%05d", Math.max(0, game.save.trainerId))},
                {Icon.SPARK, "Exp.", fmt(p.exp)},
                {Icon.ARROW_UP, "Prochain niv.", next},
        };
        double y = b.getY(), rowH = Math.min(30 * s, (b.getHeight() - 40 * s) / rows.length);
        double labelW = Math.min(118 * s, b.getWidth() * 0.38);
        for (Object[] r : rows) {
            double cy = y + rowH / 2 - 2 * s;
            UiIcons.draw(g, (Icon) r[0], b.getX(), cy - 7 * s, 14 * s, HiUi.ACCENT);
            g.setFont(HiUi.font((float) (12 * s), false));
            g.setColor(HiUi.MUTED);
            g.drawString((String) r[1], (float) (b.getX() + 22 * s), (float) (cy + 4.5 * s));
            double vx = b.getX() + labelW, vw = b.getWidth() - labelW, vh = rowH - 6 * s;
            HiUi.fill(g, HiUi.CELL, vx, cy - vh / 2, vw, vh, vh);
            g.setFont(HiUi.font((float) (12.5 * s), true));
            g.setColor(HiUi.TEXT);
            String value = (String) r[2];
            double textX = vx + 12 * s;
            if (r[1].equals("Nature") && p.natureUp() >= 0) {
                // l'effet de la nature en deux petites pastilles : +stat (rouge), −stat (bleu)
                g.drawString(value, (float) textX, (float) (cy + 4.5 * s));
                double cx = textX + g.getFontMetrics().stringWidth(value) + 8 * s;
                cx += chip(g, "+" + STAT_SHORT[p.natureUp()], UP, cx, cy - 8 * s, s) + 4 * s;
                chip(g, "−" + STAT_SHORT[p.natureDown()], DOWN, cx, cy - 8 * s, s);
            } else {
                g.drawString(HiUi.ellipsis(g, value, vw - 24 * s), (float) textX, (float) (cy + 4.5 * s));
            }
            y += rowH;
        }
        // talent : sa description ; puis la barre d'expérience
        String desc = t.abilityDescription(p.ability());
        g.setFont(HiUi.font((float) (11 * s), false));
        g.setColor(HiUi.MUTED);
        if (!desc.isBlank()) {
            g.drawString(HiUi.ellipsis(g, "★ " + desc, b.getWidth()), (float) b.getX(), (float) (y + 8 * s));
            y += 14 * s;
        }
        double by = Math.min(b.getMaxY() - 6 * s, y + 10 * s);
        HiUi.fill(g, HiUi.CELL, b.getX(), by, b.getWidth(), 5 * s, 5 * s);
        g.setPaint(new GradientPaint((float) b.getX(), 0, new Color(0x5FC8E8), (float) b.getMaxX(), 0, HiUi.ACCENT));
        g.fill(new RoundRectangle2D.Double(b.getX(), by, Math.max(5 * s, b.getWidth() * p.expProgress()), 5 * s, 5 * s, 5 * s));
    }

    private void drawStats(Graphics2D g, Pokemon p, Rectangle2D b, double s) {
        Species sp = p.base();
        int[] base = {sp.hp(), sp.attack(), sp.defense(), sp.speed(), sp.spAttack(), sp.spDefense()};
        int[] value = new int[6];
        int max = 1;
        for (int st = 0; st < 6; st++) {
            value[st] = st == Pokemon.HP ? p.maxHp() : p.stat(st);
            max = Math.max(max, value[st]);
        }
        double rowH = Math.min(40 * s, (b.getHeight() - 26 * s) / 6), y = b.getY();
        double nameW = 92 * s, valW = 40 * s, barX = b.getX() + nameW, barW = b.getWidth() - nameW - valW - 8 * s;
        for (int st : STAT_ORDER) {
            boolean up = st == p.natureUp(), down = st == p.natureDown();
            double cy = y + rowH / 2;
            g.setFont(HiUi.font((float) (12.5 * s), true));
            g.setColor(up ? UP : down ? DOWN : HiUi.TEXT);
            g.drawString(STAT_NAMES[st] + (up ? " ▲" : down ? " ▼" : ""), (float) b.getX(), (float) (cy - 1 * s));
            g.setFont(HiUi.font((float) (10.5 * s), false));
            g.setColor(HiUi.MUTED);
            g.drawString("base " + base[st], (float) b.getX(), (float) (cy + 12 * s));
            // barre de la stat, à sa couleur
            double bh = 9 * s;
            HiUi.fill(g, HiUi.CELL, barX, cy - bh / 2, barW, bh, bh);
            Color c = STAT_COLORS[st];
            g.setPaint(new GradientPaint((float) barX, 0, mix(c, HiUi.CELL, 0.35), (float) (barX + barW), 0, c));
            g.fill(new RoundRectangle2D.Double(barX, cy - bh / 2, Math.max(bh, barW * value[st] / (double) max), bh, bh, bh));
            g.setFont(HiUi.font((float) (13 * s), true));
            g.setColor(HiUi.TEXT);
            right(g, String.valueOf(value[st]), b.getMaxX(), cy + 4.5 * s);
            y += rowH;
        }
        g.setFont(HiUi.font((float) (11 * s), false));
        g.setColor(HiUi.MUTED);
        g.drawString("Total des bases : " + sp.baseStatTotal() + "     ▲ ▼ : effet de la nature", (float) b.getX(), (float) (b.getMaxY() - 4 * s));
    }

    /** IV (sur 31) et EV (sur 252) des 6 stats, en deux polygones sur un hexagone. */
    private void drawRadar(Graphics2D g, Pokemon p, Rectangle2D b, double s) {
        // l'hexagone à gauche, la légende dans la place qui reste à droite
        double legendW = 104 * s;
        double r = Math.max(30 * s, Math.min(b.getHeight() * 0.36, (b.getWidth() - legendW - 70 * s) / 2));
        double cx = b.getX() + r + 30 * s, cy = b.getY() + b.getHeight() * 0.47;
        // grille
        g.setStroke(new BasicStroke((float) Math.max(1, s)));
        for (int ring = 1; ring <= 4; ring++) {
            Path2D hex = UiIcons.hexagon(cx, cy, r * ring / 4);
            if (ring == 4) {
                g.setColor(new Color(0x1C1D2A));
                g.fill(hex);
            }
            g.setColor(new Color(0x3B3E57));
            g.draw(hex);
        }
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(-90 + i * 60);
            g.draw(new Line2D.Double(cx, cy, cx + r * Math.cos(a), cy + r * Math.sin(a)));
        }
        // polygones : EV en dessous, IV par-dessus
        polygon(g, p.evs, Pokemon.MAX_EV, cx, cy, r, EV_COLOR, s);
        polygon(g, p.ivs, 31, cx, cy, r, IV_COLOR, s);
        // étiquettes aux sommets : nom, IV · EV
        for (int i = 0; i < 6; i++) {
            int st = RADAR_ORDER[i];
            double a = Math.toRadians(-90 + i * 60), lx = cx + (r + 22 * s) * Math.cos(a), ly = cy + (r + 16 * s) * Math.sin(a);
            boolean up = st == p.natureUp(), down = st == p.natureDown();
            g.setFont(HiUi.font((float) (11 * s), true));
            g.setColor(up ? UP : down ? DOWN : HiUi.TEXT);
            HiUi.centered(g, STAT_SHORT[st], lx - 30 * s, ly - 14 * s, 60 * s, 14 * s);
            g.setFont(HiUi.font((float) (10.5 * s), true));
            String iv = String.valueOf(p.ivs[st]), ev = String.valueOf(p.evs[st]);
            double wIv = g.getFontMetrics().stringWidth(iv), wSep = g.getFontMetrics().stringWidth(" · "), wEv = g.getFontMetrics().stringWidth(ev);
            double sx = lx - (wIv + wSep + wEv) / 2, sy = ly + 10 * s;
            g.setColor(IV_COLOR);
            g.drawString(iv, (float) sx, (float) sy);
            g.setColor(HiUi.MUTED);
            g.drawString(" · ", (float) (sx + wIv), (float) sy);
            g.setColor(EV_COLOR);
            g.drawString(ev, (float) (sx + wIv + wSep), (float) sy);
        }
        // légende et totaux, à droite de l'hexagone
        int ivTotal = 0, perfect = 0;
        for (int v : p.ivs) {
            ivTotal += v;
            if (v == 31) perfect++;
        }
        double lx = Math.max(cx + r + 36 * s, b.getMaxX() - legendW), ly = b.getY() + 24 * s;
        legend(g, "IV", ivTotal + " / 186", IV_COLOR, lx, ly, s);
        legend(g, "EV", p.evTotal() + " / " + Pokemon.MAX_EV_TOTAL, EV_COLOR, lx, ly + 44 * s, s);
        g.setFont(HiUi.font((float) (11 * s), false));
        g.setColor(HiUi.MUTED);
        double ty = ly + 92 * s;
        g.drawString(perfect + " IV à 31", (float) lx, (float) ty);
        g.drawString((Pokemon.MAX_EV_TOTAL - p.evTotal()) + " EV libres", (float) lx, (float) (ty + 17 * s));
    }

    private static void polygon(Graphics2D g, int[] values, int max, double cx, double cy, double r, Color c, double s) {
        Path2D poly = new Path2D.Double();
        for (int i = 0; i < 6; i++) {
            double v = Math.max(0.04, Math.min(1, values[RADAR_ORDER[i]] / (double) max));
            double a = Math.toRadians(-90 + i * 60), px = cx + r * v * Math.cos(a), py = cy + r * v * Math.sin(a);
            if (i == 0) poly.moveTo(px, py);
            else poly.lineTo(px, py);
        }
        poly.closePath();
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 70));
        g.fill(poly);
        g.setColor(c);
        g.setStroke(new BasicStroke((float) (1.6 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(poly);
        g.setStroke(new BasicStroke(1f));
    }

    private static void legend(Graphics2D g, String name, String value, Color c, double x, double y, double s) {
        HiUi.fill(g, c, x, y - 9 * s, 10 * s, 10 * s, 3 * s);
        g.setFont(HiUi.font((float) (12 * s), true));
        g.setColor(HiUi.TEXT);
        g.drawString(name, (float) (x + 16 * s), (float) y);
        g.setFont(HiUi.font((float) (16 * s), true));
        g.drawString(value, (float) x, (float) (y + 22 * s));
    }

    private void drawMoves(Graphics2D g, Pokemon p, Rectangle2D b, double s) {
        double gap = 8 * s, rowH = (b.getHeight() - 3 * gap) / 4, y = b.getY();
        for (int i = 0; i < 4; i++) {
            double h = rowH;
            HiUi.fill(g, HiUi.CELL, b.getX(), y, b.getWidth(), h, 8 * s);
            if (p.moves[i] == 0) {
                g.setFont(HiUi.font((float) (12 * s), false));
                g.setColor(new Color(0x55586F));
                HiUi.centered(g, "— emplacement libre —", b.getX(), y, b.getWidth(), h);
                y += h + gap;
                continue;
            }
            Move m = game.data.battle().move(p.moves[i]);
            Color tc = new Color(Species.typeColor(m.type()));
            // bande de la couleur du type, à gauche
            Graphics2D q = (Graphics2D) g.create();
            q.clip(new RoundRectangle2D.Double(b.getX(), y, b.getWidth(), h, 8 * s, 8 * s));
            q.setPaint(new GradientPaint((float) b.getX(), 0, mix(tc, HiUi.CELL, 0.55), (float) (b.getX() + b.getWidth() * 0.6), 0, HiUi.CELL));
            q.fill(new Rectangle2D.Double(b.getX(), y, b.getWidth(), h));
            q.setColor(tc);
            q.fill(new Rectangle2D.Double(b.getX(), y, 4 * s, h));
            q.dispose();

            double x = b.getX() + 14 * s, top = y + h / 2 - 9 * s;
            g.setFont(HiUi.font((float) (13.5 * s), true));
            g.setColor(Color.WHITE);
            g.drawString(HiUi.ellipsis(g, PokemonTexts.pretty(m.name()), b.getWidth() * 0.5), (float) x, (float) (top + 7 * s));
            String kind = m.power() == 0 ? "Statut" : m.type() < 9 ? "Physique" : "Spéciale";
            String detail = Species.typeName(m.type()) + " · " + kind
                    + (m.power() > 1 ? " · Puiss. " + m.power() : "")
                    + (m.accuracy() > 0 ? " · Préc. " + m.accuracy() + " %" : "");
            g.setFont(HiUi.font((float) (10.5 * s), false));
            g.setColor(new Color(0xB4B6CC));
            g.drawString(HiUi.ellipsis(g, detail, b.getWidth() * 0.66), (float) x, (float) (top + 22 * s));
            // PP : chiffre + petite jauge
            double pw = 54 * s, px = b.getMaxX() - 12 * s - pw;
            g.setFont(HiUi.font((float) (12.5 * s), true));
            g.setColor(p.pp[i] == 0 ? UP : HiUi.TEXT);
            right(g, p.pp[i] + "/" + m.pp(), b.getMaxX() - 12 * s, top + 7 * s);
            double ratio = m.pp() <= 0 ? 0 : p.pp[i] / (double) m.pp();
            HiUi.fill(g, new Color(0x15161F), px, top + 15 * s, pw, 4 * s, 4 * s);
            HiUi.fill(g, ratio > 0.25 ? HiUi.ACCENT : UP, px, top + 15 * s, Math.max(4 * s * Math.signum(ratio), pw * ratio), 4 * s, 4 * s);
            y += h + gap;
        }
    }

    // ------------------------------------------------------------------ petits outils de dessin

    /** Pastille de couleur avec un texte ; renvoie sa largeur. */
    private static double chip(Graphics2D g, String text, Color c, double x, double y, double s) {
        g.setFont(HiUi.font((float) (10.5 * s), true));
        double w = g.getFontMetrics().stringWidth(text) + 14 * s, h = 17 * s;
        HiUi.fill(g, mix(c, Color.BLACK, 0.25), x, y, w, h, h);
        g.setColor(Color.WHITE);
        HiUi.centered(g, text, x, y, w, h);
        return w;
    }

    private static void right(Graphics2D g, String text, double rightX, double baseline) {
        g.drawString(text, (float) (rightX - g.getFontMetrics().stringWidth(text)), (float) baseline);
    }

    private static Color mix(Color a, Color b, double t) {
        return new Color((int) (a.getRed() * (1 - t) + b.getRed() * t), (int) (a.getGreen() * (1 - t) + b.getGreen() * t),
                (int) (a.getBlue() * (1 - t) + b.getBlue() * t));
    }

    /** Surnom tel quel, sinon nom de l'espèce en minuscules (« Porygon-Z » plutôt que « PORYGON-Z »). */
    private static String name(Pokemon p) {
        return p.nickname != null && !p.nickname.isBlank() ? p.nickname : PokemonTexts.pretty(p.base().name());
    }

    private static String fmt(long n) {
        return String.format(Locale.FRANCE, "%,d", n).replace(' ', ' ').replace(' ', ' ');
    }
}
