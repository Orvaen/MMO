package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.script.Movement;
import dev.orvaen.mmo.world.MapRenderer;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Le joueur dans le monde : sa case, sa direction, et le pas en cours (marche, course, saut de corniche).
 * <p>
 * Un pas dure plusieurs frames : {@code progress} avance de {@code speed} pixels par frame jusqu'à
 * {@code distance} (16 pixels = une case). Pendant ce temps {@code x}/{@code y} restent sur la case de départ ;
 * on ne change de case qu'à l'arrivée ({@link #advance()}).
 * <p>
 * C'est aussi le joueur « vu par les scripts » ({@link Movement.Actor}) : un script peut le faire marcher,
 * le tourner, le cacher ou afficher une bulle au-dessus de sa tête.
 */
final class OverworldPlayer implements Movement.Actor {

    private static final int T = MapRenderer.TILE;   // une case = 16 pixels

    // Position
    int x, y;                           // case actuelle
    Direction facing = Direction.DOWN;
    int elevation = 3;                  // niveau (sol, pont…) : on ne passe que sur les cases du même niveau

    // Pas en cours
    boolean moving;
    double progress;                    // pixels déjà parcourus vers la case suivante
    int distance;                       // 16 (un pas) ou 32 (saut de corniche)
    double speed = 1;                   // pixels par frame
    boolean running;                    // pas fait en courant (bouton B)
    boolean stepLeftFoot;               // les pieds alternent à chaque pas

    // Pas commandés par un script
    boolean scriptedStep, hopStep, animateStep = true;
    int inPlace;                        // frames restantes de « marche sur place »

    // Apparence
    boolean hidden;                     // passé derrière une porte, ou caché par un script
    int emote = -1, emoteTimer;         // bulle « ! », « ? »…

    /** À appeler une fois par frame : timers de la marche sur place et de la bulle. */
    void tick() {
        if (inPlace > 0) inPlace--;
        if (emoteTimer > 0) emoteTimer--;
    }

    /**
     * Commence un pas vers {@code dir}. {@code dist} = 16 pour une case, 32 pour une corniche.
     * {@code runFrames} : nombre de frames par case en courant (réglage du joueur).
     */
    void startStep(Direction dir, boolean run, int dist, int runFrames) {
        facing = dir;
        moving = true;
        progress = 0;
        distance = dist;
        stepLeftFoot = !stepLeftFoot;
        running = run && dist == T;
        // Marche : 16 frames par case (comme sur GBA). Corniche : 2 pixels par frame.
        speed = dist > T ? 2 : running ? T / (double) runFrames : 1;
    }

    /** Avance le pas d'une frame. Renvoie vrai à l'arrivée sur la nouvelle case. */
    boolean advance() {
        progress += speed;
        if (progress < distance - 1e-6) return false;
        moving = false;
        int tiles = distance / T;
        x += facing.dx * tiles;
        y += facing.dy * tiles;
        return true;
    }

    /** Vrai si le pas qui vient de finir était commandé par un script (et remet ses réglages à zéro). */
    boolean endScriptedStep() {
        if (!scriptedStep) return false;
        scriptedStep = false;
        hopStep = false;
        animateStep = true;
        return true;
    }

    /** Placement direct (arrivée sur une carte, téléportation par un script). */
    void placeAt(int x, int y) {
        this.x = x;
        this.y = y;
        moving = false;
    }

    // ------------------------------------------------------------------ position exacte (pour l'affichage)

    /** Pixels parcourus, en tenant compte du temps écoulé depuis la dernière frame de logique. */
    double exactProgress(double interpolation) {
        if (!moving) return 0;
        return Math.min(distance, progress + speed * interpolation);
    }

    double exactX(double interpolation) {
        return x * T + facing.dx * exactProgress(interpolation);
    }

    double exactY(double interpolation) {
        return y * T + facing.dy * exactProgress(interpolation);
    }

    // ------------------------------------------------------------------ le joueur vu par les scripts

    @Override public int x() { return x; }
    @Override public int y() { return y; }
    @Override public int facing() { return facing.ordinal(); }

    @Override
    public void face(int dir) {
        if (!moving) facing = Direction.fromIndex(dir);
    }

    @Override
    public void step(int dir, int tiles, int frames, boolean hop, boolean animate) {
        facing = Direction.fromIndex(dir);
        stepLeftFoot = !stepLeftFoot;
        if (tiles == 0) {                     // marche sur place
            inPlace = Math.max(1, frames);
            return;
        }
        moving = true;
        progress = 0;
        distance = T * tiles;
        speed = distance / (double) Math.max(1, frames);
        running = false;
        scriptedStep = true;
        hopStep = hop;
        animateStep = animate;
    }

    @Override public boolean busy() { return moving || inPlace > 0; }
    @Override public void setVisible(boolean visible) { hidden = !visible; }

    @Override
    public void emote(int kind) {
        emote = kind;
        emoteTimer = 60;
    }

    // ------------------------------------------------------------------ dessin

    /**
     * Dessine le joueur à sa position exacte.
     *
     * @param originX   position de la case (0,0) de la carte dans la fenêtre
     * @param zoom      taille d'un pixel du jeu en pixels de l'écran
     * @param ground    image du sol de la carte (pour repasser l'herbe haute devant les jambes)
     * @param tallGrass vrai si le joueur est arrêté dans de l'herbe haute
     */
    void draw(Graphics2D g, PlayerSprite avatar, double originX, double originY, int zoom, double interpolation,
              BufferedImage ground, boolean tallGrass) {
        if (hidden) return;
        double progressNow = exactProgress(interpolation);

        // Saut (corniche, ou pas « hop » d'un script) : le sprite monte puis redescend
        int hop = 0;
        if (moving && (distance > T || hopStep)) {
            hop = (int) Math.round(Math.sin(Math.PI * progressNow / distance) * (distance > T ? 10 : 6));
        }

        // Image de marche : comme sur GBA, pied en avant pendant la 1re moitié du pas, repos ensuite
        int frame = moving && distance == T && animateStep && progress < distance / 2.0 ? foot() : 0;
        if (inPlace > 0) frame = (inPlace / 8) % 2 == 0 ? foot() : 0;

        // Course : phase en l'air sur 5/8 de la case, puis appui sur un pied (qui alterne à chaque case).
        // Le corps rebondit doucement, au pixel de l'écran près.
        boolean run = moving && running;
        int bob = 0;
        if (run) {
            frame = progress >= distance * 0.625 - 1e-6 ? foot() : 0;
            bob = (int) Math.round(zoom * (1 - Math.sin(Math.PI * progressNow / distance)));
        }

        // Calé sur un pixel entier de l'écran : chaque pixel du sprite fait exactement « zoom » pixels
        // (sinon certains pixels font 2 ou 4 de large et le sprite tremble en marchant).
        Graphics2D p = (Graphics2D) g.create();
        p.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        p.translate(Math.round(originX + exactX(interpolation) * zoom),
                Math.round(originY + exactY(interpolation) * zoom) + bob);
        p.scale(zoom, zoom);
        avatar.drawOverworld(p, 0, 0, facing, frame, hop, run);
        if (tallGrass && !moving) {
            // le bas de la case d'herbe repasse devant les jambes
            int gx = x * T, gy = y * T + 9;
            if (gx >= 0 && gy >= 0 && gx + T <= ground.getWidth() && gy + 7 <= ground.getHeight()) {
                p.drawImage(ground.getSubimage(gx, gy, T, 7), 0, 9, null);
            }
        }
        p.dispose();
    }

    /** Image « pied gauche » (1) ou « pied droit » (2). */
    private int foot() {
        return stepLeftFoot ? 1 : 2;
    }
}
