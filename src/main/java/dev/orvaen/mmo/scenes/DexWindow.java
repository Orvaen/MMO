package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.data.Species;
import dev.orvaen.mmo.engine.Game;
import dev.orvaen.mmo.engine.HiUi;
import dev.orvaen.mmo.engine.HiWindow;
import dev.orvaen.mmo.engine.Input;
import dev.orvaen.mmo.engine.Input.Button;
import dev.orvaen.mmo.engine.UiWindow;

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
public final class DexWindow implements UiWindow {

    private static final double HEADER = HiWindow.HEADER, CELL_W = 54, CELL_H = 66, DETAIL_W = 196;
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

    public DexWindow(Game game) {
        this.game = game;
        this.win = new HiWindow(game, "pokedex", 660, 470, 470, 340);
        this.all = game.data.species.nationalOrder();
        int maxNational = 0;
        for (Species s : all) maxNational = Math.max(maxNational, national(s));
        for (Region r : REGIONS) if (r.from <= maxNational) regions.add(r);
        refilter();
    }

    private boolean closed;

    @Override
    public String id() {
        return "pokedex";
    }

    @Override
    public HiWindow frame() {
        return win;
    }

    @Override
    public boolean capturesKeyboard() {
        return searchFocus;
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
    private Rectangle2D dropdown() { return rel(12, HEADER + 10, 160, 26); }
    private Rectangle2D dropdownItem(int i) { return rel(12, HEADER + 40 + i * 26, 160, 26); }

    private Rectangle2D search() {
        Rectangle2D r = rel(180, HEADER + 10, 220, 26);
        double max = detail().getX() - 12 * scale() - r.getX();
        return new Rectangle2D.Double(r.getX(), r.getY(), Math.max(60 * scale(), Math.min(r.getWidth(), max)), r.getHeight());
    }

    private Rectangle2D detail() {
        Rectangle2D b = win.bounds();
        double sc = scale(), dw = DETAIL_W * sc;
        return new Rectangle2D.Double(b.getMaxX() - 12 * sc - dw, b.getY() + (HEADER + 10) * sc, dw,
                b.getHeight() - (HEADER + 10 + 40) * sc);
    }

    private Rectangle2D grid() {
        Rectangle2D b = win.bounds();
        double sc = scale(), gx = b.getX() + 10 * sc;
        return new Rectangle2D.Double(gx, b.getY() + (HEADER + 44) * sc, detail().getX() - 10 * sc - gx,
                b.getHeight() - (HEADER + 44 + 40) * sc);
    }

    /** Colonnes de la grille : autant qu'il en tient dans la largeur de la fenêtre. */
    private int cols() {
        return Math.max(2, (int) Math.floor((grid().getWidth() - 10 * scale()) / (CELL_W * scale())));
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
    public boolean update(Input in, boolean focused) {
        frame++;
        if (closed) return false;
        if (!focused) return true;
        if (win.update(in)) return true;
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
            if (closeButton().contains(mx, my)) return false;
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
                return true;
            }
            if (dropdown().contains(mx, my)) {
                dropdownOpen = true;
                searchFocus = false;
                return true;
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

        return true;
    }

    private void close() {
        closed = true;
    }

    private int firstVisible() {
        return (int) Math.floor(scroll) * cols();
    }

    // ------------------------------------------------------------------ rendu

    @Override
    public void render(Graphics2D g, int w, int h, boolean focused) {
        double s = scale();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

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
        boolean over = d.contains(game.input().rawMouseX(), game.input().rawMouseY());
        HiUi.fill(g, over || dropdownOpen ? HiUi.SELECTOR_HOVER : HiUi.SELECTOR, d.getX(), d.getY(), d.getWidth(), d.getHeight(), 4 * s);
        HiUi.stroke(g, HiUi.SELECTOR_BORDER, d.getX(), d.getY(), d.getWidth(), d.getHeight(), 4 * s, 1f);
        g.setFont(HiUi.font((float) (12 * s), false));
        g.setColor(HiUi.TEXT);
        HiUi.centered(g, regions.get(region).name, d.getX(), d.getY(), d.getWidth() - 18 * s, d.getHeight());
        HiUi.updown(g, HiUi.TEXT, d.getMaxX() - 14 * s, d.getCenterY());

        Rectangle2D q = search();
        HiUi.fill(g, HiUi.CELL, q.getX(), q.getY(), q.getWidth(), q.getHeight(), 4 * s);
        HiUi.stroke(g, searchFocus ? HiUi.ACCENT : HiUi.SELECTOR_BORDER, q.getX(), q.getY(), q.getWidth(), q.getHeight(), 4 * s, searchFocus ? 1.6f : 1f);
        g.setFont(HiUi.font((float) (12 * s), false));
        float ty = (float) (q.getCenterY() + g.getFontMetrics().getAscent() / 2.0 - 2 * s);
        if (query.isEmpty() && !searchFocus) {
            g.setColor(HiUi.MUTED);
            g.drawString("Rechercher…", (float) (q.getX() + 10 * s), ty);
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
        int mx = game.input().rawMouseX(), my = game.input().rawMouseY();
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
            drawFit(g, sp, r.getCenterX(), r.getY() + 4 * s, 44 * s, s);
            if (game.save.caught.contains(sp.id())) ball(g, r.getMaxX() - 12 * s, r.getY() + 8 * s, 8 * s);
            g.setFont(HiUi.font((float) (11 * s), false));
            g.setColor(i == selected ? Color.WHITE : HiUi.MUTED);
            HiUi.centered(g, String.format("%03d", national(sp)), r.getX(), r.getY() + 48 * s, r.getWidth(), 14 * s);
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

    private final Map<Integer, int[]> bounds = new HashMap<>();

    /**
     * Sprite recadré sur le Pokémon (sans les marges vides du 64x64) et tenu dans {@code box} pixels :
     * taille d'origine (nette) s'il tient, sinon réduit en douceur.
     */
    private void drawFit(Graphics2D g, Species sp, double cx, double top, double box, double s) {
        BufferedImage img = known(sp) ? game.data.species.frontSprite(sp.id()) : silhouette(sp);
        if (img == null) return;
        int[] b = bounds.computeIfAbsent(sp.id(), id -> {
            BufferedImage src = game.data.species.frontSprite(id);
            int x0 = src.getWidth(), y0 = src.getHeight(), x1 = -1, y1 = -1;
            for (int y = 0; y < src.getHeight(); y++) {
                for (int x = 0; x < src.getWidth(); x++) {
                    if (src.getRGB(x, y) >>> 24 == 0) continue;
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                }
            }
            return x1 < 0 ? new int[]{0, 0, src.getWidth(), src.getHeight()} : new int[]{x0, y0, x1 - x0 + 1, y1 - y0 + 1};
        });
        double k = Math.min(Math.floor(s * 2) / 2, box / Math.max(b[2], b[3]));
        boolean crisp = Math.abs(k - Math.rint(k)) < 0.01 && k >= 1;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, crisp
                ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int dw = (int) Math.round(b[2] * k), dh = (int) Math.round(b[3] * k);
        int dx = (int) Math.round(cx - dw / 2.0), dy = (int) Math.round(top + (box - dh));
        g.drawImage(img, dx, dy, dx + dw, dy + dh, b[0], b[1], b[0] + b[2], b[1] + b[3], null);
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

        double box = Math.min(w, 112 * s);
        double bx = x + (w - box) / 2, by = y + 22 * s;
        HiUi.fill(g, new Color(0x2E3147), bx, by, box, box, 8 * s);
        drawFit(g, sp, bx + box / 2, by + 8 * s, box - 16 * s, Math.max(1, Math.floor(s * 2)));

        double ty = by + box + 26 * s;
        g.setFont(HiUi.font((float) (15 * s), true));
        g.setColor(Color.WHITE);
        HiUi.centered(g, known ? dev.orvaen.mmo.data.PokemonTexts.pretty(sp.name()) : "???", x, ty - 18 * s, w, 24 * s);
        ty += 16 * s;
        if (!known) {
            g.setFont(HiUi.font((float) (13 * s), false));
            g.setColor(HiUi.MUTED);
            HiUi.centered(g, "Pas encore rencontré", x, ty, w, 22 * s);
            return;
        }
        double bw = sp.hasSecondType() ? (w - 6 * s) / 2 : Math.min(w, 90 * s);
        double bx2 = sp.hasSecondType() ? x : x + (w - bw) / 2;
        typeBadge(g, sp.type1(), bx2, ty, bw, 18 * s, s);
        if (sp.hasSecondType()) typeBadge(g, sp.type2(), x + bw + 6 * s, ty, bw, 18 * s, s);
        ty += 28 * s;

        int[] stats = {sp.hp(), sp.attack(), sp.defense(), sp.spAttack(), sp.spDefense(), sp.speed()};
        g.setFont(HiUi.font((float) (12 * s), false));
        g.setFont(HiUi.font((float) (11 * s), false));
        for (int i = 0; i < stats.length; i++) {
            double ry = ty + i * 18 * s;
            g.setColor(HiUi.MUTED);
            g.drawString(STAT_LABELS[i], (float) x, (float) (ry + 11 * s));
            g.setColor(HiUi.TEXT);
            String v = String.valueOf(stats[i]);
            g.drawString(v, (float) (x + 82 * s - g.getFontMetrics().stringWidth(v)), (float) (ry + 11 * s));
            double barX = x + 88 * s, barW = w - 88 * s;
            HiUi.fill(g, new Color(0x2E3147), barX, ry + 4 * s, barW, 6 * s, 3 * s);
            HiUi.fill(g, statColor(stats[i]), barX, ry + 4 * s, barW * Math.min(1, stats[i] / 180.0), 6 * s, 3 * s);
        }
        ty += stats.length * 18 * s + 4 * s;
        g.setColor(HiUi.MUTED);
        g.drawString("Total", (float) x, (float) (ty + 11 * s));
        g.setColor(Color.WHITE);
        String total = String.valueOf(sp.baseStatTotal());
        g.drawString(total, (float) (x + 82 * s - g.getFontMetrics().stringWidth(total)), (float) (ty + 11 * s));
    }

    private static void typeBadge(Graphics2D g, int type, double x, double y, double w, double h, double s) {
        HiUi.fill(g, new Color(Species.typeColor(type)), x, y, w, h, h);
        g.setFont(HiUi.font((float) (11 * s), true));
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
        double y = p.getMaxY() - 32 * s;
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
        g.setFont(HiUi.font((float) (12 * s), false));
        g.setColor(HiUi.TEXT);
        float ty = (float) (y + 21 * s);
        g.drawString("Vus : " + seen + " / " + total, (float) (p.getX() + 14 * s), ty);
        g.drawString("Capturés : " + caught + " / " + total, (float) (p.getX() + 150 * s), ty);
    }

    private void drawDropdown(Graphics2D g, double s) {
        Rectangle2D first = dropdownItem(0);
        double h = regions.size() * first.getHeight();
        HiUi.fill(g, new Color(0, 0, 0, 80), first.getX() + 2, first.getY() + 4, first.getWidth(), h, 6 * s);
        HiUi.fill(g, HiUi.HEADER, first.getX(), first.getY(), first.getWidth(), h, 4 * s);
        HiUi.stroke(g, HiUi.SELECTOR_BORDER, first.getX(), first.getY(), first.getWidth(), h, 4 * s, 1f);
        int mx = game.input().rawMouseX(), my = game.input().rawMouseY();
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
