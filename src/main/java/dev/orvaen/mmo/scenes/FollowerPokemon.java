package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.data.FollowerSprites;
import dev.orvaen.mmo.data.PokemonIcons;
import dev.orvaen.mmo.world.MapRenderer;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Le premier Pokémon de l'équipe, qui marche derrière le joueur (comme dans PokeMMO).
 * <p>
 * À chaque pas du joueur, le Pokémon part vers la case que le joueur quitte, à la même vitesse.
 * En arrivant sur une carte, il sort tout de suite à côté du joueur (derrière lui si possible).
 * Il est dessiné avec un vrai sprite qui marche dans les 4 directions ({@link FollowerSprites}, téléchargé
 * une fois) ; en attendant, ou sans Internet, avec son icône de la ROM.
 */
final class FollowerPokemon {

    private static final int T = MapRenderer.TILE;

    private final PokemonIcons icons;
    private final FollowerSprites sprites;
    private final java.util.function.IntUnaryOperator nationalNumber;
    private int species;                // 0 : personne ne suit
    private int direction = FollowerSprites.DOWN;
    private int x, y;                   // case visée
    private int fromX, fromY;           // case de départ du déplacement en cours
    private double progress = 1;        // 0 → 1 pendant un déplacement
    private double rate;                // avancée par frame
    private boolean faceRight;
    private int seenSteps;
    private int idle;                   // frames sans bouger (petite respiration)

    FollowerPokemon(PokemonIcons icons, FollowerSprites sprites, java.util.function.IntUnaryOperator nationalNumber) {
        this.icons = icons;
        this.sprites = sprites;
        this.nationalNumber = nationalNumber;
    }

    void setSpecies(int species) {
        this.species = species;
    }

    /** Arrivée sur une carte : le Pokémon est sous le joueur ; {@link #popOut} le fait sortir à côté. */
    void placeAt(int px, int py, OverworldPlayer player) {
        x = fromX = px;
        y = fromY = py;
        progress = 1;
        seenSteps = player.steps;
    }

    /** Passage sur une carte voisine : tout le repère se décale. */
    void shift(int dx, int dy) {
        x += dx;
        y += dy;
        fromX += dx;
        fromY += dy;
    }

    /** Vrai s'il est encore « sous » le joueur (juste après une arrivée). */
    boolean underPlayer(OverworldPlayer player) {
        return progress >= 1 && x == player.x && y == player.y;
    }

    /** Sort de sous le joueur vers une case voisine libre (en marchant). */
    void popOut(int tx, int ty) {
        moveTo(tx, ty, 1.0 / 16);
    }

    /** Nouveau pas du joueur ? Le Pokémon part vers la case que le joueur quitte (sans avancer le temps). */
    void follow(OverworldPlayer player) {
        if (player.steps == seenSteps) return;
        seenSteps = player.steps;
        int px = player.x, py = player.y;
        if (Math.abs(px - x) + Math.abs(py - y) > 1) {
            x = fromX = px;
            y = fromY = py;
            progress = 1;
        } else {
            double frames = player.distance / Math.max(0.1, player.speed);
            moveTo(px, py, 1.0 / Math.max(1, frames));
        }
    }

    /** Une frame : suit les nouveaux pas du joueur, puis avance. */
    void update(OverworldPlayer player) {
        follow(player);
        if (progress < 1) {
            progress = Math.min(1, progress + rate);
            idle = 0;
        } else {
            idle++;
        }
    }

    private void moveTo(int tx, int ty, double perFrame) {
        // part de là où il est vraiment (s'il n'avait pas fini son pas précédent)
        fromX = x;
        fromY = y;
        x = tx;
        y = ty;
        if (fromX == x && fromY == y) {
            progress = 1;
            return;
        }
        if (x != fromX) faceRight = x > fromX;
        direction = x > fromX ? FollowerSprites.RIGHT : x < fromX ? FollowerSprites.LEFT
                : y < fromY ? FollowerSprites.UP : FollowerSprites.DOWN;
        progress = 0;
        rate = perFrame;
    }

    /** Avancée lissée entre deux frames de logique (comme le joueur). */
    private double smooth(double interpolation) {
        return progress < 1 ? Math.min(1, progress + rate * interpolation) : 1;
    }

    double exactX(double interpolation) {
        return (fromX + (x - fromX) * smooth(interpolation)) * T;
    }

    double exactY(double interpolation) {
        return (fromY + (y - fromY) * smooth(interpolation)) * T;
    }

    /** Visible s'il y a un Pokémon et qu'il n'est pas caché sous le joueur. */
    boolean visible(OverworldPlayer player) {
        if (species <= 0 || player.hidden) return false;
        return progress < 1 || x != player.x || y != player.y;
    }

    void draw(Graphics2D g, OverworldPlayer player, double originX, double originY, int zoom, double interpolation) {
        if (!visible(player)) return;
        BufferedImage[] walk = sprites == null ? null : sprites.frames(nationalNumber.applyAsInt(species));
        if (walk != null) {
            drawWalking(g, walk, originX, originY, zoom, interpolation);
            return;
        }
        BufferedImage[] frames = icons.frames(species);
        if (frames == null) return;
        // un petit saut pendant la 1re moitié de chaque pas ; au repos, une « respiration » lente
        boolean hop = progress < 1 && progress < 0.5;
        int frame = hop ? 1 : 0;                                   // immobile : toujours la même image
        int lift = hop ? (int) Math.round(Math.sin(progress * 2 * Math.PI) * 2) : 0;
        double ex = exactX(interpolation), ey = exactY(interpolation);
        double wx = ex + T / 2.0 - 16, wy = ey + T - 29 - lift;
        Graphics2D q = (Graphics2D) g.create();
        q.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        q.translate(Math.round(originX + (ex + T / 2.0 - 7) * zoom), Math.round(originY + (ey + T - 4) * zoom));
        q.scale(zoom, zoom);
        q.setColor(new java.awt.Color(0, 0, 0, 55));              // ombre au sol (ne saute pas)
        q.fillOval(0, 0, 14, 4);
        q.dispose();
        q = (Graphics2D) g.create();
        q.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        q.translate(Math.round(originX + wx * zoom), Math.round(originY + wy * zoom));
        q.scale(zoom, zoom);
        BufferedImage img = frames[frame];
        if (faceRight) q.drawImage(img, 32, 0, -32, 32, null);
        else q.drawImage(img, 0, 0, null);
        q.dispose();
    }

    /** Vrai sprite : un pas à chaque case, et au repos une image fixe. */
    private void drawWalking(Graphics2D g, BufferedImage[] walk, double originX, double originY, int zoom, double interp) {
        int step = progress < 1 && progress < 0.5 ? 1 : 0;         // immobile : toujours la même image
        BufferedImage img = FollowerSprites.pick(walk, direction, step);
        int size = img.getWidth();
        double wx = exactX(interp) + T / 2.0 - size / 2.0, wy = exactY(interp) + T - size;
        Graphics2D q = (Graphics2D) g.create();
        q.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        q.translate(Math.round(originX + wx * zoom), Math.round(originY + wy * zoom));
        q.scale(zoom, zoom);
        q.drawImage(img, 0, 0, null);
        q.dispose();
    }

    /** Pour les tests : le vrai sprite est-il affiché (sinon l'icône) ? */
    boolean usesWalkingSprite() {
        return sprites != null && species > 0 && sprites.frames(nationalNumber.applyAsInt(species)) != null;
    }

    int x() {
        return x;
    }

    int y() {
        return y;
    }
}
