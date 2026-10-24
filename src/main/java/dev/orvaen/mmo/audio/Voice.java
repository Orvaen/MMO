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
    float volL, volR;
    boolean done;

    Voice(MusicPlayer.Track track, int key) {
        this.track = track;
        this.key = key;
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
            // canaux GB : niveaux 0-15, un cran toutes les « valeur » frames
            double level = env;
            switch (stage) {
                case 0 -> {
                    if (attack == 0) {
                        level = 15;
                    } else if (++cgbTimer >= attack) {
                        cgbTimer = 0;
                        level++;
                    }
                    if (level >= 15) {
                        level = 15;
                        stage = 1;
                    }
                }
                case 1 -> {
                    if (decay == 0) {
                        level = sustain;
                    } else if (++cgbTimer >= decay) {
                        cgbTimer = 0;
                        level--;
                    }
                    if (level <= sustain) {
                        level = sustain;
                        stage = 2;
                    }
                }
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
        if (done) return;

        double e = cgb() ? env / 15.0 : env / 255.0;
        // Plusieurs pistes jouent en même temps : chaque voix ne prend qu'une part du volume total
        double v = velocity / 127.0 * track.volume / 127.0 * masterVolume * e * (cgb() ? 0.45 : 1.0) * MIX;
        int pan = track.pan;
        if (instrumentPan != Integer.MIN_VALUE) pan = Math.max(-64, Math.min(63, pan + instrumentPan));
        volL = (float) (v * Math.min(1.0, (64 - pan) / 64.0));
        volR = (float) (v * Math.min(1.0, (64 + pan) / 64.0));

        double semis = pitchKey - 60 + track.keyShift + track.tune / 256.0 + track.bendSemitones() + track.vibrato();
        double ratio = Math.pow(2, semis / 12.0);
        switch (kind) {
            case SAMPLE -> step = (fixed ? MIXER_RATE_FIX : baseRate * ratio) / outRate;
            case SQUARE, WAVE -> step = 261.6256 * ratio / outRate;              // cycles par échantillon
            case NOISE -> step = 261.6256 * ratio * 16 / outRate;
        }
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
                    s = (pos % 1.0) < DUTY[duty & 3] ? 0.5f : -0.5f;
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
                    s = (lfsr & 1) == 0 ? 0.4f : -0.4f;
                }
            }
            buf[i * 2] += s * volL;
            buf[i * 2 + 1] += s * volR;
        }
    }
}
