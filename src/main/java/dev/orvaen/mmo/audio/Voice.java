package dev.orvaen.mmo.audio;

/**
 * Une note qui sonne : échantillon (« DirectSound ») ou canal Game Boy (carré, onde, bruit),
 * avec son enveloppe ADSR avancée 60 fois par seconde comme dans le moteur du jeu.
 */
final class Voice {

    enum Kind { SAMPLE, SQUARE, WAVE, NOISE }

    private static final double MIXER_RATE_FIX = 13379.0; // fréquence du mixeur de Rouge Feu (sons « fixes »)
    private static final double[] DUTY = {0.125, 0.25, 0.5, 0.75};
    private static final double MIX = 0.3;
    /**
     * Niveau des canaux Game Boy par rapport aux échantillons. Sur la console (mixage PSG à 100 %), un carré
     * au niveau 15 vaut ~0,48 d'un échantillon plein à volume égal (avec notre loi de panoramique) ; on reste
     * un peu en dessous, le filtre de sortie adoucit le reste.
     */
    private static final double CGB_LEVEL = 0.8;
    private static final float NOISE_LEVEL = 0.22f;
    /** Vitesse à laquelle le volume rejoint sa nouvelle valeur (par échantillon : ~2 ms). */
    private static final float VOLUME_GLIDE = 0.012f;

    final MusicPlayer.Track track;
    final int key;              // note jouée (pour EOT key)
    Kind kind;
    // échantillon
    byte[] pcm;
    int loopStart, size;
    boolean loop;
    double baseRate;            // échantillons/seconde à la note 60
    boolean fixed;
    // canaux Game Boy
    int duty;
    float[] wave;               // 32 niveaux -1..1
    boolean shortNoise;
    int lfsr = 0x7FFF;
    // hauteur
    int pitchKey;               // note utilisée pour la hauteur (clé du sous-instrument pour les percussions)
    double pos;
    double step;
    // volume
    int velocity;
    int instrumentPan;          // -64..63, ou Integer.MIN_VALUE
    // enveloppe
    int attack, decay, sustain, release;
    double env;
    int stage;                  // 0 attaque, 1 déclin, 2 maintien, 3 relâche, 4 fini
    int cgbTimer;
    int gate;                   // ticks restants (-1 : tenue jusqu'à EOT)
    float volL, volR;           // volume visé (recalculé à chaque frame du moteur)
    private float curL, curR;   // volume réellement appliqué : glisse vers volL/volR (pas de « clic »)
    boolean done;

    Voice(MusicPlayer.Track track, int key) {
        this.track = track;
        this.key = key;
    }

    /** Canal Game Boy utilisé (1 carré 1, 2 carré 2, 3 onde, 4 bruit), 0 pour un échantillon. */
    int channel;

    /** Même canal matériel que {@code old} : on reprend sa phase et son volume (pas de « clic »). */
    void takeOver(Voice old) {
        if (old.kind == kind) pos = old.pos % 1.0;
        curL = old.curL;
        curR = old.curR;
        lfsr = old.lfsr;
    }

    boolean cgb() {
        return kind != Kind.SAMPLE;
    }

    void release() {
        if (stage < 3) stage = 3;
    }

    /** Une frame (1/60 s) : enveloppe, puis volume et hauteur depuis l'état de la piste. */
    void frame(double outRate, double masterVolume) {
        if (done) return;
        if (!cgb()) {
            switch (stage) {
                case 0 -> {
                    env += attack;
                    if (env >= 255) {
                        env = 255;
                        stage = 1;
                    }
                }
                case 1 -> {
                    env = env * decay / 256.0;
                    if (env <= sustain) {
                        env = sustain;
                        stage = 2;
                    }
                }
                case 3 -> {
                    env = env * release / 256.0;
                    if (env < 2) done = true;
                }
                default -> { }
            }
        } else {
            cgbEnvelope();
        }
        if (done) return;

        int pan = track.pan;
        if (instrumentPan != Integer.MIN_VALUE) pan = Math.max(-64, Math.min(63, pan + instrumentPan));
        if (!cgb()) {
            // Plusieurs pistes jouent en même temps : chaque voix ne prend qu'une part du volume total
            double v = velocity / 127.0 * track.volume / 127.0 * masterVolume * (env / 255.0) * MIX;
            volL = (float) (v * Math.min(1.0, (64 - pan) / 64.0));
            volR = (float) (v * Math.min(1.0, (64 + pan) / 64.0));
        } else {
            // Canal Game Boy : niveau 0-15 (déjà calculé avec la vélocité et le volume de la piste) ;
            // pas de panoramique progressif sur la console : gauche, droite ou les deux.
            double v = env / 15.0 * masterVolume * CGB_LEVEL * MIX;
            volL = (float) (pan > 21 ? 0 : v);
            volR = (float) (pan < -21 ? 0 : v);
        }

        double semis = pitchKey - 60 + track.keyShift + track.tune / 256.0 + track.bendSemitones() + track.vibrato();
        double ratio = Math.pow(2, semis / 12.0);
        switch (kind) {
            case SAMPLE -> step = (fixed ? MIXER_RATE_FIX : baseRate * ratio) / outRate;
            case SQUARE, WAVE -> step = 261.6256 * ratio / outRate;              // cycles par échantillon
            case NOISE -> step = 261.6256 * ratio * 16 / outRate;
        }
    }

    /**
     * Enveloppe des canaux Game Boy, comme le moteur m4a : le niveau (0-15) monte d'un cran toutes les
     * {@code attack} frames jusqu'au « but » (vélocité × volume de la piste), descend d'un cran toutes les
     * {@code decay} frames jusqu'au maintien (but × sustain / 16), puis d'un cran toutes les {@code release}.
     */
    private void cgbEnvelope() {
        int goal = Math.min(15, velocity * track.volume * 2 / 128 >> 4);
        int sustainGoal = (goal * sustain + 15) >> 4;
        int level = (int) env;
        switch (stage) {
            case 0 -> {
                if (attack == 0) {
                    level = goal;
                } else if (++cgbTimer >= attack) {
                    cgbTimer = 0;
                    level++;
                }
                if (level >= goal) {
                    level = goal;
                    stage = 1;
                    cgbTimer = 0;
                }
            }
            case 1 -> {
                if (decay == 0) {
                    level = sustainGoal;
                } else if (++cgbTimer >= decay) {
                    cgbTimer = 0;
                    level--;
                }
                if (level <= sustainGoal) {
                    level = sustainGoal;
                    stage = 2;
                }
                if (level <= 0) done = true;          // maintien nul : la note est finie
            }
            case 2 -> level = sustainGoal;
            case 3 -> {
                if (release == 0) {
                    level = 0;
                } else if (++cgbTimer >= release) {
                    cgbTimer = 0;
                    level--;
                }
                if (level <= 0) done = true;
            }
            default -> { }
        }
        env = Math.max(0, level);
    }

    /** Ajoute la voix au tampon stéréo (entrelacé). */
    void mix(float[] buf, int frames) {
        if (done) return;
        for (int i = 0; i < frames; i++) {
            float s;
            switch (kind) {
                case SAMPLE -> {
                    int ip = (int) pos;
                    if (ip >= size) {
                        if (!loop) {
                            done = true;
                            return;
                        }
                        pos = loopStart + (pos - size);
                        ip = (int) pos;
                    }
                    int next = ip + 1 < size ? ip + 1 : loop ? loopStart : ip;
                    double f = pos - ip;
                    s = (float) ((pcm[ip] * (1 - f) + pcm[next] * f) / 128.0);
                    pos += step;
                }
                case SQUARE -> {
                    s = (float) (square(pos % 1.0, DUTY[duty & 3], step) * 0.5);
                    pos += step;
                }
                case WAVE -> {
                    s = wave[(int) ((pos % 1.0) * 32) & 31] * 0.5f;
                    pos += step;
                }
                default -> {
                    double before = pos;
                    pos += step;
                    for (int k = (int) before; k < (int) pos; k++) {
                        int bit = (lfsr ^ (lfsr >> 1)) & 1;
                        lfsr = (lfsr >> 1) | (bit << 14);
                        if (shortNoise) lfsr = (lfsr & ~0x40) | (bit << 6);
                    }
                    s = (lfsr & 1) == 0 ? NOISE_LEVEL : -NOISE_LEVEL;
                }
            }
            curL += (volL - curL) * VOLUME_GLIDE;
            curR += (volR - curR) * VOLUME_GLIDE;
            buf[i * 2] += s * curL;
            buf[i * 2 + 1] += s * curR;
        }
    }

    /**
     * Onde carrée « adoucie » (méthode PolyBLEP) : on arrondit chaque front montant / descendant sur la
     * durée d'un échantillon. Une carrée parfaite crée des aigus parasites qui sifflent et agressent l'oreille.
     */
    private static double square(double phase, double duty, double dt) {
        double v = phase < duty ? 1 : -1;
        v += blep(phase, dt);
        v -= blep((phase - duty + 1) % 1.0, dt);
        return v;
    }

    private static double blep(double t, double dt) {
        if (dt <= 0) return 0;
        if (t < dt) {
            t /= dt;
            return t + t - t * t - 1;
        }
        if (t > 1 - dt) {
            t = (t - 1) / dt;
            return t * t + t + t + 1;
        }
        return 0;
    }
}
