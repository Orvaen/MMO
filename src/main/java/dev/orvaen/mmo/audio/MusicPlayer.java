package dev.orvaen.mmo.audio;

import dev.orvaen.mmo.rom.Rom;

import java.util.ArrayList;
import java.util.List;

/**
 * Lecteur d'un morceau MP2K : pistes, commandes (notes, attentes, boucles, volume, panoramique,
 * tempo, instruments…), et notes envoyées au mixeur. Avance au rythme du moteur du jeu :
 * 59,73 frames par seconde, un « tick » chaque fois que le tempo accumulé dépasse 150.
 */
final class MusicPlayer {

    /** Durées des attentes et des notes (en ticks), table du moteur. */
    private static final int[] CLOCK = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24,
            28, 30, 32, 36, 40, 42, 44, 48, 52, 54, 56, 60, 64, 66, 68, 72, 76, 78, 80, 84, 88, 90, 92, 96};

    private static final int FINE = 0xB1, GOTO = 0xB2, PATT = 0xB3, PEND = 0xB4, REPT = 0xB5, MEMACC = 0xB9,
            PRIO = 0xBA, TEMPO = 0xBB, KEYSH = 0xBC, VOICE = 0xBD, VOL = 0xBE, PAN = 0xBF, BEND = 0xC0,
            BENDR = 0xC1, LFOS = 0xC2, LFODL = 0xC3, MOD = 0xC4, MODT = 0xC5, TUNE = 0xC8, XCMD = 0xCD,
            EOT = 0xCE, TIE = 0xCF;

    final class Track {
        int pc;
        final int[] stack = new int[3];
        int depth;
        int reptCount;
        int wait;
        boolean active = true;
        int voice;                 // ToneData courant
        int volume = 100, pan, keyShift, tune, bend, bendRange = 2;
        int lfoSpeed, lfoDelay, mod, modType, lfoPhase, lfoWait;
        int lastCmd, lastKey = 60, lastVelocity = 127, lastGate;

        double bendSemitones() {
            return bend / 64.0 * bendRange;
        }

        int modM;                  // écart du vibrato en cours (-mod..mod)

        /** Vibrato en demi-tons (m4a : 16 × modM, en 1/256 de demi-ton). */
        double vibrato() {
            return modType == 0 ? modM / 16.0 : 0;
        }

        /** Le LFO avance d'un cran par tick, en triangle (comme le moteur m4a). */
        void lfoTick() {
            if (lfoSpeed == 0 || mod == 0) {
                modM = 0;
                return;
            }
            if (lfoWait > 0) {
                lfoWait--;
                return;
            }
            lfoPhase = (lfoPhase + lfoSpeed) & 0xFF;
            int r = (byte) (lfoPhase - 64) < 0 ? (byte) lfoPhase : 128 - lfoPhase;
            modM = mod * r >> 6;
        }
    }

    private final Rom rom;
    private final AudioEngine engine;
    final int songId;
    private final List<Track> tracks = new ArrayList<>();
    private final int toneGroup;
    private int tempo = 150;           // tempoD (= BPM, le jeu stocke BPM / 2)
    private int tempoCounter;
    double volume = 1.0;               // fondus
    double fadeStep;                   // par frame (négatif : fondu sortant)
    boolean stopWhenSilent;
    boolean finished;
    boolean paused;

    MusicPlayer(Rom rom, AudioEngine engine, int songId, int header) {
        this.rom = rom;
        this.engine = engine;
        this.songId = songId;
        int n = rom.u8(header);
        this.toneGroup = rom.pointer(header + 4);
        for (int i = 0; i < n; i++) {
            Track t = new Track();
            t.pc = rom.pointer(header + 8 + i * 4);
            if (t.pc >= 0) tracks.add(t);
        }
        if (tracks.isEmpty()) finished = true;
    }

    /** Une frame du moteur (1/59,73 s). */
    void frame() {
        if (finished || paused) return;
        if (fadeStep != 0) {
            volume = Math.max(0, Math.min(1, volume + fadeStep));
            if (volume <= 0 && fadeStep < 0) {
                if (stopWhenSilent) {
                    stop();
                    return;
                }
                fadeStep = 0;
            } else if (volume >= 1 && fadeStep > 0) {
                fadeStep = 0;
            }
        }
        tempoCounter += tempo;
        while (tempoCounter >= 150) {
            tempoCounter -= 150;
            tick();
        }
        boolean any = false;
        for (Track t : tracks) any |= t.active;
        if (!any && engine.voicesOf(this) == 0) finished = true;
    }

    void stop() {
        finished = true;
        engine.releaseAll(this, true);
    }

    private void tick() {
        // durée des notes
        engine.tickNotes(this);
        for (Track t : tracks) {
            if (!t.active) continue;
            if (t.wait > 0) t.wait--;
            int guard = 0;
            while (t.active && t.wait == 0 && guard++ < 512) execute(t);
            t.lfoTick();
        }
    }

    private int u8(Track t) {
        return rom.u8(t.pc++);
    }

    private int peek(Track t) {
        return rom.inRange(t.pc, 1) ? rom.u8(t.pc) : FINE;
    }

    private void execute(Track t) {
        if (!rom.inRange(t.pc, 1)) {
            t.active = false;
            return;
        }
        int cmd = peek(t);
        if (cmd < 0x80) {
            cmd = t.lastCmd;                 // statut courant : on répète la dernière commande
            if (cmd == 0) {
                t.active = false;
                return;
            }
        } else {
            t.pc++;
            if (cmd >= 0xBD) t.lastCmd = cmd;
        }

        if (cmd >= 0x80 && cmd <= 0xB0) {
            t.wait = CLOCK[cmd - 0x80];
            return;
        }
        if (cmd >= 0xD0 || cmd == TIE) {
            note(t, cmd);
            return;
        }
        switch (cmd) {
            case FINE -> {
                t.active = false;
                engine.releaseTrack(this, t);
            }
            case GOTO -> t.pc = rom.pointer(t.pc);
            case PATT -> {
                int dest = rom.pointer(t.pc);
                if (t.depth < 3) t.stack[t.depth++] = t.pc + 4;
                t.pc = dest;
            }
            case PEND -> {
                if (t.depth > 0) t.pc = t.stack[--t.depth];
            }
            case REPT -> {
                int count = u8(t);
                int dest = rom.pointer(t.pc);
                if (count == 0) {
                    t.pc = dest;                         // boucle infinie
                } else if (++t.reptCount < count) {
                    t.pc = dest;
                } else {
                    t.reptCount = 0;
                    t.pc += 4;
                }
            }
            case MEMACC -> t.pc += 3;
            case PRIO -> u8(t);
            case TEMPO -> tempo = u8(t) * 2;
            case KEYSH -> t.keyShift = (byte) u8(t);
            case VOICE -> t.voice = toneGroup < 0 ? -1 : toneGroup + u8(t) * 12;
            case VOL -> t.volume = u8(t);
            case PAN -> t.pan = u8(t) - 64;
            case BEND -> t.bend = u8(t) - 64;
            case BENDR -> t.bendRange = u8(t);
            case LFOS -> t.lfoSpeed = u8(t);
            case LFODL -> {
                t.lfoDelay = u8(t);
                t.lfoWait = t.lfoDelay;
            }
            case MOD -> {
                t.mod = u8(t);
                if (t.mod == 0) t.modM = 0;
            }
            case MODT -> t.modType = u8(t);
            case TUNE -> t.tune = (u8(t) - 64) * 4;
            case XCMD -> t.pc += 2;
            case EOT -> {
                int key = peek(t) < 0x80 ? u8(t) : t.lastKey;
                engine.endTie(this, t, key);
            }
            default -> { }                               // commandes réservées : ignorées
        }
    }

    private void note(Track t, int cmd) {
        int gate = 0;
        if (peek(t) < 0x80) {
            t.lastKey = u8(t);
            if (peek(t) < 0x80) {
                t.lastVelocity = u8(t);
                if (peek(t) < 0x80) gate = u8(t);
            }
        }
        t.lastGate = gate;
        if (t.lfoDelay > 0) {                         // le vibrato retardé repart à chaque note
            t.lfoWait = t.lfoDelay;
            t.lfoPhase = 0;
            t.modM = 0;
        }
        int length = cmd == TIE ? -1 : CLOCK[cmd - 0xCF] + t.lastGate;
        engine.noteOn(this, t, t.voice, t.lastKey, t.lastVelocity, length);
    }
}
