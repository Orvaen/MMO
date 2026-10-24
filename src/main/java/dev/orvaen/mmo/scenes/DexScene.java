package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.data.Species;
import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.HiUi;
import dev.orvaen.mmo.engine.HiWindow;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.Input.Button;
import dev.orvaen.mmo.engine.Scene;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pokédex en fenêtre, façon PokeMMO : par-dessus le jeu, grille de toutes les espèces de la ROM dans
 * l'ordre national (silhouettes pour celles jamais vues), choix de la région, recherche par nom ou
 * numéro, fiche détaillée à droite, compteurs « vus » et « capturés ».
 */
public final class DexScene implements Scene {

    private static final double HEADER = HiWindow.HEADER, CELL_W = 66, CELL_H = 78;
    private static final String[] STAT_LABELS = {"PV", "Attaque", "Défense", "Atq. Spé.", "Déf. Spé.", "Vitesse"};

    /** Régions proposées, par plage de numéros nationaux. */
    private record Region(String name, int from, int to) {}

    private static final Region[] REGIONS = {
            new Region("Pokédex National", 1, 9999),
            new Region("Kanto Pokédex", 1, 151),
            new Region("Johto Pokédex", 152, 251),
            new Region("Hoenn Pokédex", 252, 386),
    };

    private final Game game;
    private final HiWindow win;
    private final List<Species> all;
    private final List<Region> regions = new ArrayList<>();
    private final Map<Integer, BufferedImage> silhouettes = new HashMap<>();
    private List<Species> shown = List.of();
    private int region;
    private boolean dropdownOpen;
    private boolean searchFocus;
    private String query = "";
    private int selected;
    private double scroll;        // en lignes
    private int frame;

    public DexScene(Game game) {
        this.game = game;
        this.win = new HiWindow(game, "pokedex", 900, 610, 560, 400);
        this.all = game.data.species.nationalOrder();
        int maxNational = 0;
        for (Species s : all) maxNational = Math.max(maxNational, national(s));
        for (Region r : REGIONS) if (r.from <= maxNational) regions.add(r);
        refilter();
    }

    @Override
    public boolean isOverlay() {
        return true;
    }

    private int national(Species s) {
        return game.data.species.nationalNumber(s.id());
    }

    private boolean known(Species s) {
        return game.save.seen.contains(s.id()) || game.save.caught.contains(s.id());
    }

    // ------------------------------------------------------------------ géométrie

    private double scale() {
        return win.scale();
    }

    private Rectangle2D rel(double dx, double dy, double w, double h) {
        Rectangle2D b = win.bounds();
        double sc = scale();
        return new Rectangle2D.Double(b.getX() + dx * sc, b.getY() + dy * sc, w * sc, h * sc);
    }

    private Rectangle2D panel() { return win.bounds(); }
    private Rectangle2D closeButton() { return win.closeButton(); }
    private Rectangle2D dropdown() { return rel(14, HEADER + 12, 200, 30); }
    private Rectangle2D dropdownItem(int i) { return rel(14, HEADER + 46 + i * 28, 200, 28); }

    private Rectangle2D search() {
        Rectangle2D r = rel(224, HEADER + 12, 250, 30);
        double max = detail().getX() - 12 * scale() - r.getX();
        return new Rectangle2D.Double(r.getX(), r.getY(), Math.max(60 * scale(), Math.min(r.getWidth(), max)), r.getHeight());
    }

    private Rectangle2D detail() {
        Rectangle2D b = win.bounds();
        double sc = scale(), dw = 236 * sc;
        return new Rectangle2D.Double(b.getMaxX() - 14 * sc - dw, b.getY() + (HEADER + 12) * sc, dw,
                b.getHeight() - (HEADER + 12 + 52) * sc);
    }

    private Rectangle2D grid() {
        Rectangle2D b = win.bounds();
        double sc = scale(), gx = b.getX() + 14 * sc;
        return new Rectangle2D.Double(gx, b.getY() + (HEADER + 54) * sc, detail().getX() - 12 * sc - gx,
                b.getHeight() - (HEADER + 54 + 52) * sc);
    }

    /** Colonnes de la grille : autant qu'il en tient dans la largeur de la fenêtre. */
    private int cols() {
        return Math.max(2, (int) Math.floor((grid().getWidth() - 14 * scale()) / (CELL_W * scale())));
    }

    private int visibleRows() {
        return (int) Math.floor(grid().getHeight() / (CELL_H * scale()));
    }

    private int rows() {
        return (int) Math.ceil(shown.size() / (double) cols());
    }

    private Rectangle2D cell(int index) {
        double s = scale();
        Rectangle2D g = grid();
        int col = index % cols(), row = index / cols();
        return new Rectangle2D.Double(g.getX() + col * CELL_W * s, g.getY() + (row - scroll) * CELL_H * s,
                CELL_W * s, CELL_H * s);
    }

    /** Point écran d'un élément (tests) : « close », « dropdown », « region:1 », « search », « cell:3 ». */
    public Point anchor(String id) {
        String[] p = id.split(":");
        Rectangle2D r = switch (p[0]) {
            case "close" -> closeButton();
            case "dropdown" -> dropdown();
            case "region" -> dropdownItem(Integer.parseInt(p[1]));
            case "search" -> search();
            case "cell" -> cell(Integer.parseInt(p[1]));
            default -> throw new IllegalArgumentException(id);
        };
        return new Point((int) r.getCenterX(), (int) r.getCenterY());
    }

    public int shownCount() {
        return shown.size();
    }

    public Species selectedSpecies() {
        return shown.isEmpty() ? null : shown.get(selected);
    }

    // ------------------------------------------------------------------ logique

    private void refilter() {
        Region r = regions.get(region);
        String q = fold(query.trim());
        List<Species> out = new ArrayList<>();
        for (Species s : all) {
            int n = national(s);
            if (n < r.from || n > r.to) continue;
            if (!q.isEmpty()) {
                boolean byNumber = q.chars().allMatch(Character::isDigit);
                if (byNumber ? !String.valueOf(n).startsWith(q.replaceFirst("^0+(?=.)", ""))
                        : !(known(s) && fold(s.name()).contains(q))) continue;
            }
            out.add(s);
        }
        shown = out;
        selected = Math.max(0, Math.min(selected, shown.size() - 1));
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows() - visibleRows())));
    }

    private static String fold(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    @Override
    public void update(Input in) {
        frame++;
        if (win.update(in)) return;
        int mx = in.mouseX(), my = in.mouseY();

        if (searchFocus) {
            boolean changed = false;
            for (char c : in.typed().toCharArray()) {
                if (c == '\b' || c == 127) {
                    if (!query.isEmpty()) query = query.substring(0, query.length() - 1);
                    changed = true;
                } else if (c == '\n' || c == '\r' || c == 27) {
                    searchFocus = false;
                } else if (!Character.isISOControl(c) && query.length() < 16) {
                    query += c;
                    changed = true;
                }
            }
            if (changed) {
                selected = 0;
                scroll = 0;
                refilter();
            }
        }

        if (in.mousePressed()) {
            if (closeButton().contains(mx, my)) {
                close();
                return;
            }
            if (dropdownOpen) {
                for (int i = 0; i < regions.size(); i++) {
                    if (dropdownItem(i).contains(mx, my)) {
                        region = i;
                        selected = 0;
                        scroll = 0;
                        refilter();
                    }
                }
                dropdownOpen = false;
                return;
            }
            if (dropdown().contains(mx, my)) {
                dropdownOpen = true;
                searchFocus = false;
                return;
            }
            searchFocus = search().contains(mx, my);
            if (grid().contains(mx, my)) {
                for (int i = firstVisible(); i < Math.min(shown.size(), firstVisible() + (visibleRows() + 1) * cols()); i++) {
                    if (cell(i).contains(mx, my)) selected = i;
                }
            }
        }
        if (in.wheel() != 0) {
            if (dropdownOpen) dropdownOpen = false;
            scroll = Math.max(0, Math.min(Math.max(0, rows() - visibleRows()), scroll + in.wheel()));
        }

        // En recherche, les lettres vont dans le champ : Maj (B), X (START) ou ZQSD ne doivent rien faire
        // d'autre ; Échap ou Entrée quittent le champ (gérés plus haut).
        boolean keys = !searchFocus || in.typed().isEmpty();
        if (!searchFocus && (in.pressed(Button.B) || in.pressed(Button.START))) {
            if (dropdownOpen) dropdownOpen = false;
            else close();
            return;
        }
        if (shown.isEmpty()) return;
        int cols = cols();
        int before = selected;
        if (keys) {
            if (in.repeat(Button.RIGHT)) selected = Math.min(shown.size() - 1, selected + 1);
            if (in.repeat(Button.LEFT)) selected = Math.max(0, selected - 1);
            if (in.repeat(Button.DOWN)) selected = Math.min(shown.size() - 1, selected + cols);
            if (in.repeat(Button.UP)) selected = Math.max(0, selected - cols);
            if (in.repeat(Button.R)) selected = Math.min(shown.size() - 1, selected + cols * visibleRows());
            if (in.repeat(Button.L)) selected = Math.max(0, selected - cols * visibleRows());
        }
        if (selected != before) {
            int row = selected / cols;
            if (row < scroll) scroll = row;
            if (row >= scroll + visibleRows()) scroll = row - visibleRows() + 1;
        }
    }

    private void close() {
        win.close();
        game.pop();
    }

    private int firstVisible() {
        return (int) Math.floor(scroll) * cols();
    }

    // ------------------------------------------------------------------ rendu

    @Override
    public void render(Graphics2D g) {
        // tout est en haute résolution, par-dessus le monde
    }

    @Override
    public void renderHiRes(Graphics2D g, int w, int h) {
        double s = scale();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0, 0, 0, 90));
        g.fillRect(0, 0, w, h);

        Rectangle2D p = panel();
        win.paintFrame(g, "Pokédex");

        drawToolbar(g, s);
        drawGrid(g, s);
        drawDetail(g, s);
        drawFooter(g, s, p);
        if (dropdownOpen) drawDropdown(g, s);
    }

    private void drawToolbar(Graphics2D g, double s) {
        Rectangle2D d = dropdown();
        boolean over = d.contains(game.input().mouseX(), game.input().mouseY());
        HiUi.fill(g, over || dropdownOpen ? HiUi.SELECTOR_HOVER : HiUi.SELECTOR, d.getX(), d.getY(), d.getWidth(), d.getHeight(), 4 * s);
        HiUi.stroke(g, HiUi.SELECTOR_BORDER, d.getX(), d.getY(), d.getWidth(), d.getHeight(), 4 * s, 1f);
        g.setFont(HiUi.font((float) (13 * s), false));
        g.setColor(HiUi.TEXT);
        HiUi.centered(g, regions.get(region).name, d.getX(), d.getY(), d.getWidth() - 18 * s, d.getHeight());
        HiUi.updown(g, HiUi.TEXT, d.getMaxX() - 14 * s, d.getCenterY());

        Rectangle2D q = search();
        HiUi.fill(g, HiUi.CELL, q.getX(), q.getY(), q.getWidth(), q.getHeight(), 4 * s);
        HiUi.stroke(g, searchFocus ? HiUi.ACCENT : HiUi.SELECTOR_BORDER, q.getX(), q.getY(), q.getWidth(), q.getHeight(), 4 * s, searchFocus ? 1.6f : 1f);
        g.setFont(HiUi.font((float) (13 * s), false));
        float ty = (float) (q.getCenterY() + g.getFontMetrics().getAscent() / 2.0 - 2 * s);
        if (query.isEmpty() && !searchFocus) {
            g.setColor(HiUi.MUTED);
            g.drawString("Rechercher un nom ou un n°", (float) (q.getX() + 10 * s), ty);
        } else {
            g.setColor(HiUi.TEXT);
            String t = query + (searchFocus && frame / 30 % 2 == 0 ? "|" : "");
            g.drawString(t, (float) (q.getX() + 10 * s), ty);
        }
    }

    private void drawGrid(Graphics2D g, double s) {
        Rectangle2D area = grid();
        Shape clip = g.getClip();
        g.clip(new Rectangle2D.Double(area.getX(), area.getY(), area.getWidth(), area.getHeight()));
        int mx = game.input().mouseX(), my = game.input().mouseY();
        int first = firstVisible(), last = Math.min(shown.size(), first + (visibleRows() + 2) * cols());
        for (int i = first; i < last; i++) {
            Species sp = shown.get(i);
            Rectangle2D r = cell(i);
            boolean hover = !dropdownOpen && r.contains(mx, my) && area.contains(mx, my);
            if (i == selected) {
                HiUi.fill(g, HiUi.SELECTOR, r.getX() + 2 * s, r.getY() + 2 * s, r.getWidth() - 4 * s, r.getHeight() - 4 * s, 6 * s);
                HiUi.stroke(g, HiUi.ACCENT, r.getX() + 2 * s, r.getY() + 2 * s, r.getWidth() - 4 * s, r.getHeight() - 4 * s, 6 * s, 1.4f);
            } else if (hover) {
                HiUi.fill(g, HiUi.SELECTOR_HOVER, r.getX() + 2 * s, r.getY() + 2 * s, r.getWidth() - 4 * s, r.getHeight() - 4 * s, 6 * s);
            }
            double size = 58 * s;
            drawSpecies(g, sp, r.getCenterX() - size / 2, r.getY() + 3 * s, size);
            if (game.save.caught.contains(sp.id())) ball(g, r.getMaxX() - 15 * s, r.getY() + 7 * s, 9 * s);
            g.setFont(HiUi.font((float) (12 * s), false));
            g.setColor(i == selected ? Color.WHITE : HiUi.TEXT);
            HiUi.centered(g, String.format("%03d", national(sp)), r.getX(), r.getY() + 60 * s, r.getWidth(), 16 * s);
        }
        g.setClip(clip);
        if (shown.isEmpty()) {
            g.setFont(HiUi.font((float) (14 * s), false));
            g.setColor(HiUi.MUTED);
            HiUi.centered(g, "Aucun Pokémon ne correspond", area.getX(), area.getY(), area.getWidth(), area.getHeight() / 2);
        }

        // Barre de défilement
        int rows = rows(), vis = visibleRows();
        if (rows > vis) {
            double tx = area.getMaxX() - 7 * s, th = area.getHeight();
            HiUi.fill(g, HiUi.CELL, tx, area.getY(), 5 * s, th, 3 * s);
            double bh = Math.max(24 * s, th * vis / rows), by = area.getY() + (th - bh) * scroll / (rows - vis);
            HiUi.fill(g, HiUi.SELECTOR_HOVER, tx, by, 5 * s, bh, 3 * s);
        }
    }

    /** Sprite (vu) ou silhouette sombre (jamais vu), net si l'échelle est entière. */
    private void drawSpecies(Graphics2D g, Species sp, double x, double y, double size) {
        BufferedImage img = known(sp) ? game.data.species.frontSprite(sp.id()) : silhouette(sp);
        if (img == null) return;
        Object hint = Math.abs(size / img.getWidth() - Math.rint(size / img.getWidth())) < 0.01
                ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BILINEAR;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
        g.drawImage(img, (int) Math.round(x), (int) Math.round(y), (int) Math.round(size), (int) Math.round(size), null);
    }

    private BufferedImage silhouette(Species sp) {
        return silhouettes.computeIfAbsent(sp.id(), id -> {
            BufferedImage src = game.data.species.frontSprite(id);
            if (src == null) return null;
            BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < src.getHeight(); y++) {
                for (int x = 0; x < src.getWidth(); x++) {
                    if (src.getRGB(x, y) >>> 24 != 0) out.setRGB(x, y, 0xFF101119);
                }
            }
            return out;
        });
    }

    private static void ball(Graphics2D g, double cx, double cy, double r) {
        g.setColor(new Color(0xE84855));
        g.fill(new java.awt.geom.Arc2D.Double(cx - r / 2, cy - r / 2, r, r, 0, 180, java.awt.geom.Arc2D.PIE));
        g.setColor(Color.WHITE);
        g.fill(new java.awt.geom.Arc2D.Double(cx - r / 2, cy - r / 2, r, r, 180, 180, java.awt.geom.Arc2D.PIE));
        g.setColor(new Color(0x15161F));
        g.setStroke(new java.awt.BasicStroke((float) (r / 9)));
        g.draw(new Ellipse2D.Double(cx - r / 2, cy - r / 2, r, r));
        g.draw(new java.awt.geom.Line2D.Double(cx - r / 2, cy, cx + r / 2, cy));
        g.fill(new Ellipse2D.Double(cx - r / 6, cy - r / 6, r / 3, r / 3));
    }

    private void drawDetail(Graphics2D g, double s) {
        Rectangle2D d = detail();
        HiUi.fill(g, HiUi.CELL, d.getX(), d.getY(), d.getWidth(), d.getHeight(), 6 * s);
        if (shown.isEmpty()) return;
        Species sp = shown.get(selected);
        boolean known = known(sp);
        double x = d.getX() + 14 * s, y = d.getY() + 14 * s, w = d.getWidth() - 28 * s;

        g.setFont(HiUi.font((float) (12 * s), true));
        g.setColor(HiUi.MUTED);
        g.drawString(String.format("N° %03d", national(sp)), (float) x, (float) (y + 12 * s));
        if (game.save.caught.contains(sp.id())) ball(g, x + w - 6 * s, y + 7 * s, 11 * s);

        double box = Math.min(w, 128 * s);
        double bx = x + (w - box) / 2, by = y + 22 * s;
        HiUi.fill(g, new Color(0x2E3147), bx, by, box, box, 8 * s);
        drawSpecies(g, sp, bx + (box - 128 * s) / 2, by + (box - 128 * s) / 2, 128 * s);

        double ty = by + box + 26 * s;
        g.setFont(HiUi.font((float) (18 * s), true));
        g.setColor(Color.WHITE);
        HiUi.centered(g, known ? sp.name() : "???", x, ty - 18 * s, w, 24 * s);
        ty += 16 * s;
        if (!known) {
            g.setFont(HiUi.font((float) (13 * s), false));
            g.setColor(HiUi.MUTED);
            HiUi.centered(g, "Pas encore rencontré", x, ty, w, 22 * s);
            return;
        }
        double bw = sp.hasSecondType() ? (w - 8 * s) / 2 : Math.min(w, 110 * s);
        double bx2 = sp.hasSecondType() ? x : x + (w - bw) / 2;
        typeBadge(g, sp.type1(), bx2, ty, bw, 22 * s, s);
        if (sp.hasSecondType()) typeBadge(g, sp.type2(), x + bw + 8 * s, ty, bw, 22 * s, s);
        ty += 36 * s;

        int[] stats = {sp.hp(), sp.attack(), sp.defense(), sp.spAttack(), sp.spDefense(), sp.speed()};
        g.setFont(HiUi.font((float) (12 * s), false));
        for (int i = 0; i < stats.length; i++) {
            double ry = ty + i * 22 * s;
            g.setColor(HiUi.MUTED);
            g.drawString(STAT_LABELS[i], (float) x, (float) (ry + 11 * s));
            g.setColor(HiUi.TEXT);
            String v = String.valueOf(stats[i]);
            g.drawString(v, (float) (x + 92 * s - g.getFontMetrics().stringWidth(v)), (float) (ry + 11 * s));
            double barX = x + 100 * s, barW = w - 100 * s;
            HiUi.fill(g, new Color(0x2E3147), barX, ry + 3 * s, barW, 8 * s, 4 * s);
            HiUi.fill(g, statColor(stats[i]), barX, ry + 3 * s, barW * Math.min(1, stats[i] / 180.0), 8 * s, 4 * s);
        }
        ty += stats.length * 22 * s + 6 * s;
        g.setColor(HiUi.MUTED);
        g.drawString("Total", (float) x, (float) (ty + 11 * s));
        g.setColor(Color.WHITE);
        String total = String.valueOf(sp.baseStatTotal());
        g.drawString(total, (float) (x + 92 * s - g.getFontMetrics().stringWidth(total)), (float) (ty + 11 * s));
        g.setColor(HiUi.MUTED);
        g.drawString("Capture : " + sp.catchRate(), (float) (x + 110 * s), (float) (ty + 11 * s));
    }

    private static void typeBadge(Graphics2D g, int type, double x, double y, double w, double h, double s) {
        HiUi.fill(g, new Color(Species.typeColor(type)), x, y, w, h, h);
        g.setFont(HiUi.font((float) (12 * s), true));
        g.setColor(Color.WHITE);
        HiUi.centered(g, Species.typeName(type), x, y, w, h);
    }

    private static Color statColor(int v) {
        if (v < 50) return new Color(0xF08030);
        if (v < 80) return new Color(0xF8D030);
        if (v < 110) return new Color(0x78C850);
        return new Color(0x30A0E0);
    }

    private void drawFooter(Graphics2D g, double s, Rectangle2D p) {
        double y = p.getMaxY() - 40 * s;
        g.setColor(HiUi.PANEL_BORDER);
        g.fill(new Rectangle2D.Double(p.getX() + 14 * s, y, p.getWidth() - 28 * s, 1));
        int total = 0, seen = 0, caught = 0;
        Region r = regions.get(region);
        for (Species sp : all) {
            int n = national(sp);
            if (n < r.from || n > r.to) continue;
            total++;
            if (known(sp)) seen++;
            if (game.save.caught.contains(sp.id())) caught++;
        }
        g.setFont(HiUi.font((float) (13 * s), false));
        g.setColor(HiUi.TEXT);
        float ty = (float) (y + 26 * s);
        g.drawString("Vus : " + seen + " / " + total, (float) (p.getX() + 18 * s), ty);
        g.drawString("Capturés : " + caught + " / " + total, (float) (p.getX() + 190 * s), ty);
        g.setColor(HiUi.MUTED);
        String hint = "Flèches : parcourir · Molette : défiler · Échap : fermer";
        double hx = p.getMaxX() - 18 * s - g.getFontMetrics().stringWidth(hint);
        if (hx > p.getX() + 380 * s) g.drawString(hint, (float) hx, ty);
    }

    private void drawDropdown(Graphics2D g, double s) {
        Rectangle2D first = dropdownItem(0);
        double h = regions.size() * first.getHeight();
        HiUi.fill(g, new Color(0, 0, 0, 80), first.getX() + 2, first.getY() + 4, first.getWidth(), h, 6 * s);
        HiUi.fill(g, HiUi.HEADER, first.getX(), first.getY(), first.getWidth(), h, 4 * s);
        HiUi.stroke(g, HiUi.SELECTOR_BORDER, first.getX(), first.getY(), first.getWidth(), h, 4 * s, 1f);
        int mx = game.input().mouseX(), my = game.input().mouseY();
        g.setFont(HiUi.font((float) (13 * s), false));
        for (int i = 0; i < regions.size(); i++) {
            Rectangle2D r = dropdownItem(i);
            if (i == region || r.contains(mx, my)) {
                HiUi.fill(g, i == region ? HiUi.SELECTOR_FOCUS : HiUi.SELECTOR_HOVER, r.getX() + 3 * s, r.getY() + 2 * s,
                        r.getWidth() - 6 * s, r.getHeight() - 4 * s, 4 * s);
            }
            g.setColor(HiUi.TEXT);
            HiUi.centered(g, regions.get(i).name, r.getX(), r.getY(), r.getWidth(), r.getHeight());
        }
    }
}
