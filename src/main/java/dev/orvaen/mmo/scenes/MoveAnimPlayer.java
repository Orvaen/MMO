package dev.orvaen.mmo.scenes;

import dev.orvaen.mmo.audio.AudioEngine;
import dev.orvaen.mmo.battle.Battle;
import dev.orvaen.mmo.battle.MoveAnimations;
import dev.orvaen.mmo.battle.MoveAnimations.Template;
import dev.orvaen.mmo.rom.Rom;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * Joue le script d'animation d'une attaque, lu dans la ROM : ses images (flammes, impacts, bulles…),
 * leurs animations image par image, ses sons et son rythme ({@code delay}, {@code waitforvisualfinish}).
 * <p>
 * Le jeu d'origine déplace chaque sprite avec une petite fonction écrite en code machine, que l'on ne peut
 * pas exécuter ici. On reproduit donc les déplacements les plus courants à partir des paramètres du script :
 * <ul>
 *   <li>5 paramètres ou plus, dont une durée : le sprite <b>voyage</b> de l'attaquant vers la cible
 *       (Flammèche, Écume…) ;</li>
 *   <li>sinon il <b>apparaît</b> sur l'attaquant ou sur la cible (impact de Charge, Griffe…) le temps de son
 *       animation ;</li>
 *   <li>un sprite sans image fait <b>s'élancer</b> l'attaquant ; une tâche « secouer » fait trembler un Pokémon.</li>
 * </ul>
 */
final class MoveAnimPlayer {

    private static final int MAX_FRAMES = 360, MAX_COMMANDS_PER_FRAME = 120;
    /** Centre des Pokémon sur l'écran GBA (comme dans le jeu). */
    private static final int[][] CENTER = {{68, 80}, {176, 40}};

    /** Un sprite d'animation en cours. */
    private static final class Particle {
        Template template;
        double x0, y0, x1, y1;
        int travel;                 // durée du trajet (0 : immobile)
        int life;                   // durée de vie en frames
        int age;
    }

    /** Un Pokémon qui tremble ou s'élance. */
    private static final class Motion {
        int side;
        boolean lunge;
        int amplitude, duration, age;
    }

    private final MoveAnimations anims;
    private final Rom rom;
    private final AudioEngine audio;
    private final int attacker, target;
    private int pc;
    private final Deque<Integer> calls = new ArrayDeque<>();
    private int delay;
    private boolean waitVisual, ended;
    private int frames;
    private final List<Particle> particles = new ArrayList<>();
    private final List<Motion> motions = new ArrayList<>();
    private final List<int[]> sounds = new ArrayList<>();       // {n° du son, frames restantes}
    private final boolean[] hidden = new boolean[2];

    MoveAnimPlayer(MoveAnimations anims, AudioEngine audio, int script, int attackerSide) {
        this.anims = anims;
        this.rom = anims.rom();
        this.audio = audio;
        this.pc = script;
        this.attacker = attackerSide;
        this.target = 1 - attackerSide;
    }

    /** Une frame ; renvoie vrai quand l'animation est terminée. */
    boolean update() {
        frames++;
        for (Iterator<int[]> it = sounds.iterator(); it.hasNext(); ) {
            int[] s = it.next();
            if (--s[1] <= 0) {
                audio.playSe(s[0]);
                it.remove();
            }
        }
        for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
            Particle p = it.next();
            if (++p.age >= p.life) it.remove();
        }
        for (Iterator<Motion> it = motions.iterator(); it.hasNext(); ) {
            Motion m = it.next();
            if (++m.age >= m.duration) it.remove();
        }
        if (frames > MAX_FRAMES) return true;
        if (delay > 0) {
            delay--;
        } else if (waitVisual) {
            if (busy()) return false;
            waitVisual = false;
        }
        if (delay == 0 && !waitVisual && !ended) run();
        return ended && !busy() && sounds.isEmpty();
    }

    private boolean busy() {
        return !particles.isEmpty() || !motions.isEmpty();
    }

    /** Exécute les commandes jusqu'à la prochaine attente. */
    private void run() {
        for (int n = 0; n < MAX_COMMANDS_PER_FRAME && !ended && delay == 0 && !waitVisual; n++) {
            int len = MoveAnimations.commandLength(rom, pc);
            if (len <= 0) {
                ended = true;
                return;
            }
            int op = rom.u8(pc), next = pc + len;
            switch (op) {
                case MoveAnimations.CREATE_SPRITE -> createSprite(pc);
                case MoveAnimations.CREATE_TASK -> createTask(pc);
                case MoveAnimations.DELAY -> delay = rom.u8(pc + 1);
                case MoveAnimations.WAIT_VISUAL -> waitVisual = true;
                case MoveAnimations.END -> ended = true;
                case MoveAnimations.PLAY_SE, MoveAnimations.PLAY_SE_PAN, MoveAnimations.PAN_SE,
                     MoveAnimations.PAN_SE_NONE, MoveAnimations.PAN_SE_ALL -> audio.playSe(rom.u16(pc + 1));
                case MoveAnimations.LOOP_SE -> {
                    int se = rom.u16(pc + 1), wait = Math.max(1, rom.u8(pc + 4)), times = Math.min(8, rom.u8(pc + 5));
                    for (int i = 0; i < times; i++) sounds.add(new int[]{se, 1 + i * wait});
                }
                case MoveAnimations.WAIT_PLAY_SE -> sounds.add(new int[]{rom.u16(pc + 1), Math.max(1, rom.u8(pc + 4))});
                case MoveAnimations.CALL -> {
                    if (calls.size() > 8) {
                        ended = true;
                        return;
                    }
                    calls.push(next);
                    next = rom.pointer(pc + 1);
                }
                case MoveAnimations.RETURN -> {
                    if (calls.isEmpty()) {
                        ended = true;
                        return;
                    }
                    next = calls.pop();
                }
                case MoveAnimations.GOTO, MoveAnimations.TWO_TURN -> next = rom.pointer(pc + 1);
                case MoveAnimations.INVISIBLE, MoveAnimations.VISIBLE -> {
                    int b = rom.u8(pc + 1);
                    if (b <= 1) hidden[b == 0 ? attacker : target] = op == MoveAnimations.INVISIBLE;
                }
                default -> { }                      // fonds, transparence, tâches sonores : ignorés
            }
            if (next < 0) {
                ended = true;
                return;
            }
            pc = next;
        }
    }

    private void createSprite(int o) {
        Template t = anims.template(rom.pointer(o + 1));
        boolean onTarget = (rom.u8(o + 5) & 0x80) != 0;
        int argc = rom.u8(o + 6);
        int[] a = new int[Math.max(argc, 6)];
        for (int i = 0; i < argc; i++) a[i] = rom.s16(o + 7 + i * 2);
        int flip = attacker == Battle.FOE ? -1 : 1;

        if (t.invisible()) {                                  // sprite sans image : il fait bouger un Pokémon
            Motion m = new Motion();
            m.side = onTarget ? target : attacker;
            m.lunge = !onTarget;
            m.amplitude = 10;
            m.duration = 16;
            motions.add(m);
            return;
        }
        Particle p = new Particle();
        p.template = t;
        int animLength = 0;
        for (int[] f : t.frames()) animLength += f[1];
        if (argc >= 5 && a[4] >= 2 && a[4] <= 90) {
            // voyage de l'attaquant vers la cible
            p.x0 = CENTER[attacker][0] + flip * a[0];
            p.y0 = CENTER[attacker][1] + a[1];
            p.x1 = CENTER[target][0] + flip * a[2];
            p.y1 = CENTER[target][1] + a[3];
            p.travel = a[4];
            p.life = a[4] + 2;
        } else {
            // apparaît sur un Pokémon (le 3e paramètre désigne souvent lequel : 0 attaquant, 1 cible)
            int anchor = argc >= 3 && (a[2] == 0 || a[2] == 1) ? (a[2] == 1 ? target : attacker)
                    : onTarget ? target : attacker;
            int f = anchor == Battle.FOE ? -1 : 1;
            p.x0 = p.x1 = CENTER[anchor][0] + f * a[0];
            p.y0 = p.y1 = CENTER[anchor][1] + a[1];
            p.life = !t.loops() && animLength > 2 ? animLength : 18;
        }
        particles.add(p);
    }

    private void createTask(int o) {
        int argc = rom.u8(o + 6);
        if (argc < 4) return;
        int[] a = new int[argc];
        for (int i = 0; i < argc; i++) a[i] = rom.s16(o + 7 + i * 2);
        // forme d'AnimTask_ShakeMon : {Pokémon, amplitude x, amplitude y, nombre, délai}
        if ((a[0] == 0 || a[0] == 1) && a[3] >= 1 && a[3] <= 40 && Math.abs(a[1]) <= 12 && Math.abs(a[2]) <= 12) {
            Motion m = new Motion();
            m.side = a[0] == 1 ? target : attacker;
            m.amplitude = Math.max(1, Math.min(4, Math.max(Math.abs(a[1]), Math.abs(a[2]))));
            m.duration = Math.max(8, Math.min(40, a[3] * Math.max(1, argc > 4 ? a[4] : 1) * 2));
            motions.add(m);
        }
    }

    // ------------------------------------------------------------------ ce que la scène dessine

    /** Décalage horizontal du Pokémon {@code side} (élan, tremblement). */
    double offsetX(int side) {
        double dx = 0;
        for (Motion m : motions) {
            if (m.side != side) continue;
            if (m.lunge) {
                double k = m.age < m.duration / 2 ? m.age / (m.duration / 2.0) : (m.duration - m.age) / (m.duration / 2.0);
                dx += (side == Battle.PLAYER ? 1 : -1) * m.amplitude * k;
            } else {
                dx += ((m.age / 2) % 2 == 0 ? 1 : -1) * m.amplitude;
            }
        }
        return dx;
    }

    double offsetY(int side) {
        double dy = 0;
        for (Motion m : motions) {
            if (m.side == side && m.lunge) {
                double k = m.age < m.duration / 2 ? m.age / (m.duration / 2.0) : (m.duration - m.age) / (m.duration / 2.0);
                dy += (side == Battle.PLAYER ? -1 : 1) * m.amplitude * 0.4 * k;
            }
        }
        return dy;
    }

    boolean hidden(int side) {
        return hidden[side];
    }

    /** Dessine les sprites d'animation (coordonnées de l'écran GBA). */
    void draw(Graphics2D g) {
        for (Particle p : particles) {
            double k = p.travel > 0 ? Math.min(1, p.age / (double) p.travel) : 0;
            double x = p.x0 + (p.x1 - p.x0) * k, y = p.y0 + (p.y1 - p.y0) * k;
            BufferedImage img = anims.frame(p.template, frameAt(p.template, p.age));
            if (img != null) g.drawImage(img, (int) Math.round(x - img.getWidth() / 2.0), (int) Math.round(y - img.getHeight() / 2.0), null);
        }
    }

    /** Décalage de tuile de l'image affichée à cet âge (l'animation boucle si elle le fait dans le jeu). */
    private static int frameAt(Template t, int age) {
        int[][] f = t.frames();
        int total = 0;
        for (int[] e : f) total += e[1];
        if (total <= 0) return f[0][0];
        int time = t.loops() ? age % total : Math.min(age, total - 1);
        for (int[] e : f) {
            if (time < e[1]) return e[0];
            time -= e[1];
        }
        return f[f.length - 1][0];
    }
}
