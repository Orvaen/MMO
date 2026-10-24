package dev.orvaen.mmo.audio;

import dev.orvaen.mmo.rom.Rom;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Le son du jeu : musiques, fanfares et effets sonores lus dans la ROM du joueur (moteur MP2K des
 * jeux GBA), mixés en 44,1 kHz stéréo sur la sortie audio de l'ordinateur.
 * <p>
 * Sans carte son (serveur, tests), tout fonctionne en silence ; {@link #renderOffline} permet de
 * calculer le son sans le jouer.
 */
public final class AudioEngine {

    public static final int RATE = 44100;
    private static final double ENGINE_FPS = 59.7275;
    private static final int CHUNK = 256;
    /** Réserve de son d'avance (~90 ms) : le son ne grésille pas si le jeu a un petit à-coup (chargement…). */
    private static final int LINE_FRAMES = 4096;
    private static final int MAX_VOICES = 40;

    private final Rom rom;
    private final SongTable songs;
    private final List<Voice> voices = new ArrayList<>();
    private final Map<Integer, byte[]> samples = new HashMap<>();
    private MusicPlayer bgm, fanfare;
    private final List<MusicPlayer> effects = new ArrayList<>();
    private double frameAcc;
    private double master = 0.5;
    private final OutputFilter filter = new OutputFilter(RATE);
    private boolean muted;
    private volatile boolean running;
    private float[] mixBuffer = new float[CHUNK * 2];
    private Thread thread;

    public AudioEngine(Rom rom) {
        this.rom = rom;
        this.songs = new SongTable(rom);
    }

    AudioEngine(Rom rom, SongTable songs) {
        this.rom = rom;
        this.songs = songs;
    }

    public boolean available() {
        return songs.available();
    }

    public int songCount() {
        return songs.count();
    }

    // ------------------------------------------------------------------ commandes du jeu

    /** Musique de fond : change avec un court fondu (rien si c'est déjà celle-là). */
    public synchronized void playBgm(int id) {
        if (!songs.available() || id <= 0) return;
        if (bgm != null && !bgm.finished && bgm.songId == id && bgm.fadeStep >= 0) return;
        if (bgm != null && !bgm.finished) {
            MusicPlayer old = bgm;
            old.stopWhenSilent = true;
            old.fadeStep = -1.0 / 12;
            effects.add(old);                       // finit son fondu dans la liste des effets
        }
        bgm = start(id);
    }

    /** Fondu de sortie de la musique de fond (en frames). */
    public synchronized void fadeOutBgm(int frames) {
        if (bgm == null) return;
        bgm.stopWhenSilent = true;
        bgm.fadeStep = -1.0 / Math.max(1, frames);
    }

    public synchronized void stopBgm() {
        if (bgm != null) bgm.stop();
        bgm = null;
    }

    public synchronized int currentBgm() {
        return bgm == null || bgm.finished ? 0 : bgm.songId;
    }

    public synchronized void playSe(int id) {
        MusicPlayer p = start(id);
        if (p != null) effects.add(p);
    }

    /** Fanfare (objet reçu, Pokémon soigné…) : la musique s'interrompt le temps qu'elle se joue. */
    public synchronized void playFanfare(int id) {
        MusicPlayer p = start(id);
        if (p == null) return;
        if (fanfare != null) fanfare.stop();
        fanfare = p;
        if (bgm != null) {
            bgm.paused = true;
            releaseAll(bgm, true);
        }
    }

    public synchronized boolean fanfareBusy() {
        return fanfare != null && !fanfare.finished;
    }

    public synchronized boolean effectsBusy() {
        for (MusicPlayer p : effects) if (!p.finished && p != bgm) return true;
        return false;
    }

    public synchronized void setMasterVolume(double v) {
        master = Math.max(0, Math.min(1, v));
    }

    public synchronized double masterVolume() {
        return master;
    }

    public synchronized boolean toggleMute() {
        muted = !muted;
        return muted;
    }

    public synchronized boolean muted() {
        return muted;
    }

    private MusicPlayer start(int id) {
        int h = songs.header(id);
        if (h < 0 || !SongTable.header(rom, h)) return null;
        return new MusicPlayer(rom, this, id, h);
    }

    // ------------------------------------------------------------------ sortie audio

    /** Ouvre la sortie audio et lance le mixage (sans effet s'il n'y a pas de carte son). */
    public synchronized void start() {
        if (running || !songs.available()) return;
        running = true;
        thread = new Thread(this::loop, "Son (MP2K)");
        thread.setDaemon(true);
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    private volatile int underruns;

    /** Nombre de fois où le son a manqué de réserve depuis le lancement (grésillements). */
    public int underruns() {
        return underruns;
    }

    public void shutdown() {
        running = false;
    }

    private void loop() {
        AudioFormat format = new AudioFormat(RATE, 16, 2, true, false);
        SourceDataLine line;
        try {
            line = AudioSystem.getSourceDataLine(format);
            line.open(format, LINE_FRAMES * 4);
            line.start();
        } catch (Exception | LinkageError e) {
            running = false;
            System.err.println("Son indisponible : " + e.getMessage());
            return;
        }
        float[] buf = new float[CHUNK * 2];
        byte[] out = new byte[CHUNK * 4];
        while (running) {
            renderOffline(buf, CHUNK);
            for (int i = 0; i < CHUNK * 2; i++) {
                int v = (int) (buf[i] * 32000);
                out[i * 2] = (byte) v;
                out[i * 2 + 1] = (byte) (v >> 8);
            }
            // réserve presque vide : le son a dû « trou » (compté, visible avec F3)
            if (line.available() >= line.getBufferSize() - out.length) underruns++;
            line.write(out, 0, out.length);
        }
        line.drain();
        line.close();
    }

    /** Calcule {@code frames} échantillons stéréo (entrelacés) dans {@code buf}. */
    public synchronized void renderOffline(float[] buf, int frames) {
        java.util.Arrays.fill(buf, 0, frames * 2, 0f);
        double perFrame = RATE / ENGINE_FPS;
        int done = 0;
        if (mixBuffer.length < frames * 2) mixBuffer = new float[frames * 2];
        float[] tmp = mixBuffer;
        while (done < frames) {
            if (frameAcc <= 0) {
                engineFrame();
                frameAcc += perFrame;
            }
            int n = (int) Math.min(frames - done, Math.ceil(frameAcc));
            java.util.Arrays.fill(tmp, 0, n * 2, 0f);
            for (Voice v : voices) v.mix(tmp, n);
            for (int i = 0; i < n * 2; i++) buf[done * 2 + i] = tmp[i];
            done += n;
            frameAcc -= n;
        }
        filter.process(buf, frames);
        if (muted) java.util.Arrays.fill(buf, 0, frames * 2, 0f);
    }

    private void engineFrame() {
        if (bgm != null) bgm.frame();
        if (fanfare != null) {
            fanfare.frame();
            if (fanfare.finished) {
                fanfare = null;
                if (bgm != null) bgm.paused = false;
            }
        }
        for (Iterator<MusicPlayer> it = effects.iterator(); it.hasNext(); ) {
            MusicPlayer p = it.next();
            p.frame();
            if (p.finished) it.remove();
        }
        for (Iterator<Voice> it = voices.iterator(); it.hasNext(); ) {
            Voice v = it.next();
            MusicPlayer owner = ownerOf(v);
            v.frame(RATE, master * (owner == null ? 1 : owner.volume));
            if (v.done) it.remove();
        }
        if (owners.size() > 64) owners.values().removeIf(p -> p.finished && voicesOf(p) == 0);
    }

    private final Map<MusicPlayer.Track, MusicPlayer> owners = new HashMap<>();

    private MusicPlayer ownerOf(Voice v) {
        return owners.get(v.track);
    }

    // ------------------------------------------------------------------ notes (appelé par les lecteurs)

    void noteOn(MusicPlayer player, MusicPlayer.Track track, int tone, int key, int velocity, int length) {
        if (tone < 0 || !rom.inRange(tone, 12)) return;
        owners.put(track, player);
        int type = rom.u8(tone);
        int pitchKey = key;
        if ((type & 0x40) != 0) {                                     // partage du clavier
            int group = rom.pointer(tone + 4), map = rom.pointer(tone + 8);
            if (group < 0 || map < 0) return;
            tone = group + rom.u8(map + (key & 0x7F)) * 12;
            type = rom.u8(tone);
        } else if ((type & 0x80) != 0) {                              // percussions : un instrument par note
            int group = rom.pointer(tone + 4);
            if (group < 0) return;
            tone = group + (key & 0x7F) * 12;
            type = rom.u8(tone);
            pitchKey = rom.u8(tone + 1);
        }
        if (!rom.inRange(tone, 12)) return;

        Voice v = new Voice(track, key);
        v.pitchKey = pitchKey;
        v.velocity = velocity;
        v.gate = length;
        v.attack = rom.u8(tone + 8);
        v.decay = rom.u8(tone + 9);
        v.sustain = rom.u8(tone + 10);
        v.release = rom.u8(tone + 11);
        v.instrumentPan = Integer.MIN_VALUE;
        switch (type & 7) {
            case 0 -> {
                int wav = rom.pointer(tone + 4);
                if (wav < 0 || !rom.inRange(wav, 16)) return;
                int size = (int) Math.min(rom.u32(wav + 12), 4_000_000L);
                if (size <= 0 || !rom.inRange(wav + 16, size)) return;
                v.kind = Voice.Kind.SAMPLE;
                v.pcm = samples.computeIfAbsent(wav, w -> rom.slice(w + 16, size));
                v.size = size;
                v.loop = (rom.u16(wav + 2) & 0xC000) != 0;
                v.loopStart = (int) Math.min(rom.u32(wav + 8), size - 1);
                v.baseRate = rom.u32(wav + 4) / 1024.0;
                v.fixed = (type & 0x08) != 0;
                int ps = rom.u8(tone + 3);
                if ((ps & 0x80) != 0) v.instrumentPan = ps - 0xC0;
            }
            case 1, 2 -> {
                v.kind = Voice.Kind.SQUARE;
                v.duty = (int) (rom.u32(tone + 4) & 3);
            }
            case 3 -> {
                int wav = rom.pointer(tone + 4);
                if (wav < 0 || !rom.inRange(wav, 16)) return;
                v.kind = Voice.Kind.WAVE;
                v.wave = new float[32];
                for (int i = 0; i < 16; i++) {
                    int b = rom.u8(wav + i);
                    v.wave[i * 2] = ((b >> 4) - 7.5f) / 7.5f;
                    v.wave[i * 2 + 1] = ((b & 15) - 7.5f) / 7.5f;
                }
            }
            case 4 -> {
                v.kind = Voice.Kind.NOISE;
                v.shortNoise = (rom.u32(tone + 4) & 1) != 0;
            }
            default -> {
                return;
            }
        }
        if (voices.size() >= MAX_VOICES) {
            // on libère la plus ancienne note déjà relâchée (sinon la plus ancienne)
            Voice drop = null;
            for (Voice o : voices) if (o.stage >= 3) { drop = o; break; }
            if (drop == null) drop = voices.get(0);
            voices.remove(drop);
        }
        voices.add(v);
    }

    void tickNotes(MusicPlayer player) {
        for (Voice v : voices) {
            if (v.gate > 0 && owners.get(v.track) == player && --v.gate == 0) v.release();
        }
    }

    void endTie(MusicPlayer player, MusicPlayer.Track track, int key) {
        for (Voice v : voices) if (v.track == track && v.gate < 0 && v.key == key) v.release();
    }

    void releaseTrack(MusicPlayer player, MusicPlayer.Track track) {
        for (Voice v : voices) if (v.track == track && v.gate < 0) v.release();
    }

    void releaseAll(MusicPlayer player, boolean immediate) {
        for (Voice v : voices) {
            if (owners.get(v.track) != player) continue;
            if (immediate) v.done = true;
            else v.release();
        }
    }

    int voicesOf(MusicPlayer player) {
        int n = 0;
        for (Voice v : voices) if (!v.done && owners.get(v.track) == player) n++;
        return n;
    }
}
