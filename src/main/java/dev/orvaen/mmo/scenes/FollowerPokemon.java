package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.data.PokemonIcons;
import dev.orvaen.mmo.world.MapRenderer;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Le premier Pokémon de l'équipe, qui marche derrière le joueur (comme dans PokeMMO).
 * <p>
 * Principe simple : à chaque pas du joueur, le Pokémon va sur la case que le joueur vient de quitter,
 * à la même vitesse. Il est dessiné avec son icône animée de la ROM (2 images qui alternent), retournée
 * quand il va vers la droite.
 */
final class FollowerPokemon {

    private static final int T = MapRenderer.TILE;

    private final PokemonIcons icons;
    private int species;                // 0 : personne ne suit
    private int x, y;                   // case actuelle
    private int fromX, fromY;           // case de départ du pas en cours
    private boolean moving;
    private boolean faceRight;
    private int seenSteps;
    private int animTimer;

    FollowerPokemon(PokemonIcons icons) {
        this.icons = icons;
    }

    /** Choisit le Pokémon qui suit (0 : aucun). */
    void setSpecies(int species) {
        this.species = species;
    }

    int species() {
        return species;
    }

    /** Arrivée sur une carte : le Pokémon attend « sous » le joueur et sortira au premier pas. */
    void placeAt(int px, int py, OverworldPlayer player) {
        x = fromX = px;
        y = fromY = py;
        moving = false;
        seenSteps = player.steps;
    }

    /** Passage sur une carte voisine : tout le repère se décale. */
    void shift(int dx, int dy) {
        x += dx;
        y += dy;
        fromX += dx;
        fromY += dy;
    }

    /** Une frame : nouveau pas du joueur → le Pokémon prend la case qu'il vient de quitter. */
    void update(OverworldPlayer player) {
        animTimer++;
        if (player.steps != seenSteps) {
            seenSteps = player.steps;
            int px = player.x, py = player.y;             // case que le joueur quitte
            fromX = x;
            fromY = y;
            if (Math.abs(px - x) + Math.abs(py - y) > 1) {  // trop loin (saut, téléportation) : il arrive direct
                fromX = px;
                fromY = py;
            }
            x = px;
            y = py;
            moving = fromX != x || fromY != y;
            if (x != fromX) faceRight = x > fromX;
        }
        if (moving && !player.moving) moving = false;
    }

    /** Position exacte en pixels du monde (suit l'avancée du pas du joueur). */
    double exactX(OverworldPlayer player, double interpolation) {
        return (fromX + (x - fromX) * progress(player, interpolation)) * T;
    }

    double exactY(OverworldPlayer player, double interpolation) {
        return (fromY + (y - fromY) * progress(player, interpolation)) * T;
    }

    private double progress(OverworldPlayer player, double interpolation) {
        if (!moving || player.distance <= 0) return 1;
        return Math.min(1, player.exactProgress(interpolation) / player.distance);
    }

    /** Visible seulement s'il y a un Pokémon et qu'il n'est pas sur la case du joueur. */
    boolean visible(OverworldPlayer player) {
        if (species <= 0 || player.hidden) return false;
        return moving || x != player.x || y != player.y;
    }

    void draw(Graphics2D g, OverworldPlayer player, double originX, double originY, int zoom, double interpolation) {
        if (!visible(player)) return;
        BufferedImage[] frames = icons.frames(species);
        if (frames == null) return;
        int speed = moving ? 8 : 20;
        BufferedImage img = frames[(animTimer / speed) % 2];
        double wx = exactX(player, interpolation) + T / 2.0 - 16, wy = exactY(player, interpolation) + T - 30;
        Graphics2D q = (Graphics2D) g.create();
        q.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        q.translate(Math.round(originX + wx * zoom), Math.round(originY + wy * zoom));
        q.scale(zoom, zoom);
        q.setColor(new java.awt.Color(0, 0, 0, 55));          // petite ombre
        q.fillOval(9, 26, 14, 4);
        if (faceRight) q.drawImage(img, 32, 0, -32, 32, null);
        else q.drawImage(img, 0, 0, null);
        q.dispose();
    }

    int x() {
        return x;
    }

    int y() {
        return y;
    }
}
