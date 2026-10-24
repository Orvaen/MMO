package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.battle.Move;
import dev.orvaen.mmo.battle.Pokemon;
import dev.orvaen.mmo.data.PokemonTexts;
import dev.orvaen.mmo.data.Species;
import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.HiUi;
import dev.orvaen.mmo.engine.HiWindow;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.UiWindow;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;

/**
 * Résumé d'un Pokémon de l'équipe, en fenêtre comme dans PokeMMO (on peut jouer pendant qu'elle est ouverte).
 * <ul>
 *   <li><b>Infos</b> : n° du Pokédex, nom, nature, talent, dresseur d'origine, expérience ;</li>
 *   <li><b>Stats</b> : chaque stat avec sa base, ses IV, ses EV et l'effet de la nature ;</li>
 *   <li><b>Attaques</b> : type, catégorie, PP, puissance, précision.</li>
 * </ul>
 * À droite : le sprite de face (lu dans la ROM), le niveau, les types. Les flèches ‹ › (ou la molette)
 * passent au Pokémon suivant de l'équipe.
 */
public final class SummaryWindow implements UiWindow {

    public static final String ID = "resume";
    private static final String[] TABS = {"Infos", "Stats", "Attaques"};
    /** Ordre d'affichage des stats (comme le jeu) : PV, Att, Déf, Att.Spé, Déf.Spé, Vit. */
    private static final int[] STAT_ORDER = {Pokemon.HP, Pokemon.ATTACK, Pokemon.DEFENSE, Pokemon.SP_ATTACK,
            Pokemon.SP_DEFENSE, Pokemon.SPEED};
    private static final String[] STAT_NAMES = {"PV", "Attaque", "Défense", "Vitesse", "Att. Spé.", "Déf. Spé."};
    private static final String[] STATUS = {"", "SOM", "PSN", "BRL", "GEL", "PAR"};
    private static final Color UP = new Color(0xF2788F), DOWN = new Color(0x7FA8F5), BEST = new Color(0xF2C94C);

    private final Game game;
    private final HiWindow win;
    private final PartyBar.Team team;
    private int index;
    private int tab;

    public SummaryWindow(Game game, int index) {
        this.game = game;
        this.win = new HiWindow(game, "resume-pokemon", 600, 340, 520, 320);
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

    /** Point écran d'un élément (tests) : « close », « tab0 » à « tab2 », « prev », « next ». */
    public Point anchor(String id) {
        Rectangle2D r = switch (id) {
            case "prev" -> arrow(false);
            case "next" -> arrow(true);
            case "tab0", "tab1", "tab2" -> tabRect(id.charAt(3) - '0');
            default -> win.closeButton();
        };
        return new Point((int) r.getCenterX(), (int) r.getCenterY());
    }

    // ------------------------------------------------------------------ disposition

    private Rectangle2D tabRect(int i) {
        Rectangle2D c = win.content();
        double s = win.scale(), w = 82 * s;
        return new Rectangle2D.Double(c.getX() + 12 * s + i * (w + 4 * s), c.getY() + 8 * s, w, 26 * s);
    }

    private Rectangle2D arrow(boolean next) {
        Rectangle2D c = win.content();
        double s = win.scale(), w = 26 * s;
        double x = c.getMaxX() - 12 * s - (next ? w : 2 * w + 4 * s);
        return new Rectangle2D.Double(x, c.getY() + 8 * s, w, 26 * s);
    }

    /** Colonne de droite : le sprite et l'en-tête du Pokémon. */
    private Rectangle2D card() {
        Rectangle2D c = win.content();
        double s = win.scale(), w = Math.min(230 * s, c.getWidth() * 0.42);
        double top = c.getY() + 42 * s;
        return new Rectangle2D.Double(c.getMaxX() - 12 * s - w, top, w, c.getMaxY() - top - 12 * s);
    }

    /** Colonne de gauche : le contenu de l'onglet. */
    private Rectangle2D body() {
        Rectangle2D c = win.content(), k = card();
        double s = win.scale();
        return new Rectangle2D.Double(c.getX() + 12 * s, k.getY(), k.getX() - c.getX() - 24 * s, k.getHeight());
    }

    // ------------------------------------------------------------------ logique

    @Override
    public boolean update(Input in, boolean focused) {
        List<Pokemon> mons = team.get();
        if (mons.isEmpty()) return false;                  // plus personne dans l'équipe
        index = Math.floorMod(index, mons.size());
        if (!focused) return true;
        if (win.update(in)) return true;
        int mx = in.mouseX(), my = in.mouseY();
        if (in.mousePressed()) {
            if (win.closeButton().contains(mx, my)) return false;
            for (int i = 0; i < TABS.length; i++) if (tabRect(i).contains(mx, my)) tab = i;
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
        win.paintFrame(g, "Résumé Pokémon");
        double s = win.scale();
        int mx = game.input().mouseX(), my = game.input().mouseY();

        // onglets
        for (int i = 0; i < TABS.length; i++) {
            Rectangle2D r = tabRect(i);
            boolean on = i == tab, hover = r.contains(mx, my);
            HiUi.fill(g, on ? HiUi.SELECTOR_FOCUS : hover ? HiUi.SELECTOR_HOVER : HiUi.SELECTOR,
                    r.getX(), r.getY(), r.getWidth(), r.getHeight(), 5 * s);
            g.setFont(HiUi.font((float) (12.5 * s), on));
            g.setColor(on ? Color.WHITE : HiUi.TEXT);
            HiUi.centered(g, TABS[i], r.getX(), r.getY(), r.getWidth(), r.getHeight());
        }
        // ‹ n / 6 ›
        for (boolean next : new boolean[]{false, true}) {
            Rectangle2D r = arrow(next);
            HiUi.fill(g, r.contains(mx, my) ? HiUi.SELECTOR_HOVER : HiUi.SELECTOR, r.getX(), r.getY(), r.getWidth(), r.getHeight(), 5 * s);
            g.setFont(HiUi.font((float) (15 * s), true));
            g.setColor(HiUi.TEXT);
            HiUi.centered(g, next ? "›" : "‹", r.getX(), r.getY() - s, r.getWidth(), r.getHeight());
        }
        g.setFont(HiUi.font((float) (11.5 * s), false));
        g.setColor(HiUi.MUTED);
        String pos = (Math.floorMod(index, mons.size()) + 1) + " / " + mons.size();
        Rectangle2D a = arrow(false);
        g.drawString(pos, (float) (a.getX() - 8 * s - g.getFontMetrics().stringWidth(pos)), (float) (a.getCenterY() + 4 * s));

        drawCard(g, p, s);
        Rectangle2D b = body();
        switch (tab) {
            case 1 -> drawStats(g, p, b, s);
            case 2 -> drawMoves(g, p, b, s);
            default -> drawInfo(g, p, b, s);
        }
    }

    /** À droite : nom, niveau, sprite sur fond dégradé aux couleurs du type, types, objet tenu. */
    private void drawCard(Graphics2D g, Pokemon p, double s) {
        Rectangle2D k = card();
        Species sp = p.base();
        HiUi.fill(g, HiUi.CELL, k.getX(), k.getY(), k.getWidth(), k.getHeight(), 8 * s);
        double pad = 10 * s;
        g.setFont(HiUi.font((float) (14 * s), true));
        g.setColor(HiUi.TEXT);
        String lv = "N. " + p.level;
        double lvW = g.getFontMetrics().stringWidth(lv);
        g.drawString(HiUi.ellipsis(g, name(p), k.getWidth() - 3 * pad - lvW), (float) (k.getX() + pad), (float) (k.getY() + 22 * s));
        g.drawString(lv, (float) (k.getMaxX() - pad - lvW), (float) (k.getY() + 22 * s));

        // sprite
        double imgTop = k.getY() + 32 * s, imgH = Math.max(80 * s, k.getHeight() - 32 * s - 72 * s);
        double ix = k.getX() + pad, iw = k.getWidth() - 2 * pad;
        Color tint = new Color(Species.typeColor(sp.type1()));
        Graphics2D q = (Graphics2D) g.create();
        q.setClip(new java.awt.geom.RoundRectangle2D.Double(ix, imgTop, iw, imgH, 8 * s, 8 * s));
        q.setPaint(new GradientPaint((float) ix, (float) imgTop, mix(tint, HiUi.CELL, 0.45),
                (float) ix, (float) (imgTop + imgH), mix(tint, HiUi.CELL, 0.82)));
        q.fill(new Rectangle2D.Double(ix, imgTop, iw, imgH));
        q.setColor(new Color(255, 255, 255, 28));                       // un sol clair sous le Pokémon
        q.fill(new java.awt.geom.Ellipse2D.Double(ix + iw * 0.18, imgTop + imgH * 0.74, iw * 0.64, imgH * 0.18));
        BufferedImage img = game.data.species.frontSprite(p.species);
        if (img != null) {
            int k2 = (int) Math.max(1, Math.floor(Math.min(iw, imgH) / 64.0));   // agrandi d'un nombre entier : pixels nets
            int size = 64 * k2;
            q.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            q.drawImage(img, (int) Math.round(ix + (iw - size) / 2), (int) Math.round(imgTop + (imgH - size) / 2 + 4 * s), size, size, null);
        }
        q.dispose();
        if (p.status > 0 && p.status < STATUS.length) {
            badge(g, STATUS[p.status], new Color(0xB8577A), ix + 6 * s, imgTop + 6 * s, s);
        } else if (p.fainted()) {
            badge(g, "K.O.", new Color(0x8A3A4A), ix + 6 * s, imgTop + 6 * s, s);
        }

        // types
        double ty = imgTop + imgH + 10 * s, tx = ix;
        tx += badge(g, Species.typeName(sp.type1()), new Color(Species.typeColor(sp.type1())), tx, ty, s) + 6 * s;
        if (sp.hasSecondType()) badge(g, Species.typeName(sp.type2()), new Color(Species.typeColor(sp.type2())), tx, ty, s);

        // PV et objet tenu
        g.setFont(HiUi.font((float) (11.5 * s), false));
        g.setColor(HiUi.MUTED);
        g.drawString("Objet tenu", (float) ix, (float) (ty + 42 * s));
        g.setColor(HiUi.TEXT);
        g.setFont(HiUi.font((float) (12 * s), true));
        g.drawString("Aucun", (float) (ix + 76 * s), (float) (ty + 42 * s));
    }

    private void drawInfo(Graphics2D g, Pokemon p, Rectangle2D b, double s) {
        PokemonTexts t = game.data.texts();
        Species sp = p.base();
        int ability = p.ability();
        String nature = t.nature(p.nature);
        if (p.natureUp() >= 0) nature += "  (+" + STAT_NAMES[p.natureUp()] + ", −" + STAT_NAMES[p.natureDown()] + ")";
        String next = p.level >= 100 ? "Niveau maximum !" : fmt(p.expForLevel(p.level + 1) - p.exp) + " pts";
        String ot = game.save.name == null || game.save.name.isBlank() ? "Dresseur" : game.save.name;
        String[][] rows = {
                {"Pokédex", String.format("%03d", game.data.species.nationalNumber(p.species))},
                {"Nom", name(p) + (p.name().equals(sp.name()) ? "" : "  (" + PokemonTexts.pretty(sp.name()) + ")")},
                {"Nature", nature},
                {"Talent", t.ability(ability)},
                {"DO", ot + "  ·  N° ID " + String.format("%05d", Math.max(0, game.save.trainerId))},
                {"Exp.", fmt(p.exp)},
                {"Prochain niv.", next},
        };
        double y = b.getY() + 4 * s, rowH = 27 * s, labelW = Math.min(110 * s, b.getWidth() * 0.34);
        for (String[] r : rows) {
            HiUi.fill(g, HiUi.CELL, b.getX() + labelW, y, b.getWidth() - labelW, rowH - 5 * s, 5 * s);
            g.setFont(HiUi.font((float) (12 * s), false));
            g.setColor(HiUi.MUTED);
            g.drawString(r[0], (float) b.getX(), (float) (y + 15.5 * s));
            g.setFont(HiUi.font((float) (12.5 * s), true));
            g.setColor(HiUi.TEXT);
            g.drawString(HiUi.ellipsis(g, r[1], b.getWidth() - labelW - 16 * s), (float) (b.getX() + labelW + 8 * s), (float) (y + 15.5 * s));
            y += rowH;
        }
        // description du talent, puis la barre d'expérience
        String desc = t.abilityDescription(ability);
        if (!desc.isBlank()) {
            g.setFont(HiUi.font((float) (11 * s), false));
            g.setColor(HiUi.MUTED);
            g.drawString(HiUi.ellipsis(g, desc, b.getWidth()), (float) b.getX(), (float) (y + 8 * s));
            y += 14 * s;
        }
        double by = Math.min(b.getMaxY() - 6 * s, y + 8 * s);
        g.setColor(HiUi.CELL);
        g.fill(new Rectangle2D.Double(b.getX(), by, b.getWidth(), 4 * s));
        g.setColor(new Color(0x5FC8E8));
        g.fill(new Rectangle2D.Double(b.getX(), by, b.getWidth() * p.expProgress(), 4 * s));
    }

    private void drawStats(Graphics2D g, Pokemon p, Rectangle2D b, double s) {
        Species sp = p.base();
        int[] base = {sp.hp(), sp.attack(), sp.defense(), sp.speed(), sp.spAttack(), sp.spDefense()};
        double x = b.getX(), w = b.getWidth();
        // colonnes : nom · valeur · base · IV · EV (avec une barre)
        double cVal = x + w * 0.30, cBase = x + w * 0.46, cIv = x + w * 0.60, cEv = x + w * 0.73;
        double y = b.getY() + 14 * s, rowH = 25 * s;
        g.setFont(HiUi.font((float) (11 * s), true));
        g.setColor(HiUi.MUTED);
        right(g, "Valeur", cVal + 34 * s, y);
        right(g, "Base", cBase + 26 * s, y);
        right(g, "IV", cIv + 20 * s, y);
        g.drawString("EV", (float) cEv, (float) y);
        y += 8 * s;
        for (int stat : STAT_ORDER) {
            boolean up = stat == p.natureUp(), down = stat == p.natureDown();
            HiUi.fill(g, HiUi.CELL, x, y, w, rowH - 4 * s, 5 * s);
            double ty = y + 14.5 * s;
            g.setFont(HiUi.font((float) (12 * s), true));
            g.setColor(up ? UP : down ? DOWN : HiUi.TEXT);
            g.drawString(STAT_NAMES[stat] + (up ? " +" : down ? " −" : ""), (float) (x + 8 * s), (float) ty);
            g.setColor(HiUi.TEXT);
            String value = stat == Pokemon.HP ? p.hp + "/" + p.maxHp() : String.valueOf(p.stat(stat));
            right(g, value, cVal + 34 * s, ty);
            g.setFont(HiUi.font((float) (12 * s), false));
            g.setColor(HiUi.MUTED);
            right(g, String.valueOf(base[stat]), cBase + 26 * s, ty);
            g.setFont(HiUi.font((float) (12 * s), true));
            g.setColor(p.ivs[stat] == 31 ? BEST : HiUi.TEXT);
            right(g, String.valueOf(p.ivs[stat]), cIv + 20 * s, ty);
            // EV : nombre + petite barre (255 au plus)
            g.setColor(HiUi.TEXT);
            g.drawString(String.valueOf(p.evs[stat]), (float) cEv, (float) ty);
            double barX = cEv + 30 * s, barW = x + w - 8 * s - barX;
            if (barW > 10 * s) {
                g.setColor(HiUi.SELECTOR);
                g.fill(new Rectangle2D.Double(barX, ty - 6 * s, barW, 4 * s));
                g.setColor(p.evs[stat] >= Pokemon.MAX_EV ? BEST : HiUi.ACCENT);
                g.fill(new Rectangle2D.Double(barX, ty - 6 * s, barW * p.evs[stat] / Pokemon.MAX_EV, 4 * s));
            }
            y += rowH;
        }
        int ivTotal = 0;
        for (int v : p.ivs) ivTotal += v;
        g.setFont(HiUi.font((float) (11.5 * s), false));
        g.setColor(HiUi.MUTED);
        g.drawString("Total IV : " + ivTotal + " / 186     Total EV : " + p.evTotal() + " / " + Pokemon.MAX_EV_TOTAL,
                (float) x, (float) (y + 12 * s));
    }

    private void drawMoves(Graphics2D g, Pokemon p, Rectangle2D b, double s) {
        double y = b.getY() + 4 * s, rowH = Math.min(54 * s, (b.getHeight() - 8 * s) / 4);
        for (int i = 0; i < 4; i++) {
            HiUi.fill(g, HiUi.CELL, b.getX(), y, b.getWidth(), rowH - 6 * s, 6 * s);
            if (p.moves[i] == 0) {
                g.setFont(HiUi.font((float) (12 * s), false));
                g.setColor(HiUi.MUTED);
                g.drawString("—", (float) (b.getX() + 12 * s), (float) (y + rowH / 2 + 1 * s));
                y += rowH;
                continue;
            }
            Move m = game.data.battle().move(p.moves[i]);
            double x = b.getX() + 10 * s;
            double bw = badge(g, Species.typeName(m.type()), new Color(Species.typeColor(m.type())), x, y + 8 * s, s);
            g.setFont(HiUi.font((float) (13 * s), true));
            g.setColor(HiUi.TEXT);
            g.drawString(HiUi.ellipsis(g, PokemonTexts.pretty(m.name()), b.getWidth() * 0.5), (float) (x + bw + 8 * s), (float) (y + 21 * s));
            String pp = "PP " + p.pp[i] + "/" + m.pp();
            g.setFont(HiUi.font((float) (12 * s), true));
            g.setColor(p.pp[i] == 0 ? UP : HiUi.TEXT);
            right(g, pp, b.getMaxX() - 10 * s, y + 21 * s);
            // ligne du dessous : catégorie, puissance, précision
            String kind = m.power() == 0 ? "Statut" : m.type() < 9 ? "Physique" : "Spéciale";
            String detail = kind + "   ·   Puissance " + (m.power() <= 1 ? "—" : m.power())
                    + "   ·   Précision " + (m.accuracy() == 0 ? "—" : m.accuracy() + " %");
            g.setFont(HiUi.font((float) (11 * s), false));
            g.setColor(HiUi.MUTED);
            g.drawString(HiUi.ellipsis(g, detail, b.getWidth() - 20 * s), (float) x, (float) (y + 38 * s));
            y += rowH;
        }
    }

    // ------------------------------------------------------------------ petits outils de dessin

    /** Pastille de couleur avec un texte ; renvoie sa largeur. */
    private static double badge(Graphics2D g, String text, Color c, double x, double y, double s) {
        g.setFont(HiUi.font((float) (10.5 * s), true));
        double w = g.getFontMetrics().stringWidth(text) + 14 * s, h = 18 * s;
        HiUi.fill(g, c.darker(), x, y, w, h, 9 * s);
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
