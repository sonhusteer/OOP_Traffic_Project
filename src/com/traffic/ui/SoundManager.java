package com.traffic.ui;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Central sound manager for the simulation.
 *
 * Important: Java Clip often cannot open 24-bit PCM WAV files directly.
 * The bundled horn/engine/siren assets may be 24-bit, so every real WAV is
 * decoded and converted to 16-bit PCM before opening the Clip.
 */
public class SoundManager {

    private static SoundManager instance;
    private final Map<String, Clip> cache = new HashMap<>();
    private final Set<String> failedLoads = new HashSet<>();
    private boolean muted = false;

    private SoundManager() {}

    public static SoundManager getInstance() {
        if (instance == null) instance = new SoundManager();
        return instance;
    }

    public void play(String filename) {
        if (muted) return;
        Clip clip = getClip(filename);
        if (clip == null) return;
        clip.setFramePosition(0);
        clip.start();
    }

    public void loop(String filename) {
        if (muted) return;
        Clip clip = getClip(filename);
        if (clip == null) return;
        if (clip.isRunning()) return;
        clip.setFramePosition(0);
        clip.loop(Clip.LOOP_CONTINUOUSLY);
    }

    public void stop(String filename) {
        Clip clip = cache.get(filename);
        if (clip != null && clip.isRunning()) {
            clip.stop();
        }
    }

    public void stopAll() {
        cache.values().forEach(clip -> {
            if (clip.isRunning()) clip.stop();
        });
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        if (muted) stopAll();
    }

    public boolean isMuted() { return muted; }

    private Clip getClip(String filename) {
        if (filename == null || filename.isBlank()) return null;
        if (cache.containsKey(filename)) return cache.get(filename);
        if (failedLoads.contains(filename)) return null;

        try {
            Clip clip;
            InputStream is = openAudioResource(filename);
            if (is == null) {
                clip = createSyntheticClip(filename);
                if (clip == null) {
                    System.out.println("[Sound] Missing: " + filename);
                    failedLoads.add(filename);
                    return null;
                }
            } else {
                clip = openConvertedClip(is);
            }
            cache.put(filename, clip);
            return clip;
        } catch (Exception e) {
            System.out.println("[Sound] Load failed: " + filename + " - " + e.getMessage());
            failedLoads.add(filename);
            return null;
        }
    }

    private Clip openConvertedClip(InputStream rawStream) throws Exception {
        try (BufferedInputStream buffered = new BufferedInputStream(rawStream);
             AudioInputStream decoded = AudioSystem.getAudioInputStream(buffered)) {
            AudioFormat source = decoded.getFormat();
            AudioFormat target = toClipFriendlyFormat(source);
            try (AudioInputStream converted = AudioSystem.getAudioInputStream(target, decoded)) {
                Clip clip = AudioSystem.getClip();
                clip.open(converted);
                return clip;
            }
        }
    }

    private AudioFormat toClipFriendlyFormat(AudioFormat source) {
        int channels = Math.max(1, source.getChannels());
        float sampleRate = source.getSampleRate() > 0 ? source.getSampleRate() : 44100f;
        return new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate,
                16,
                channels,
                channels * 2,
                sampleRate,
                false
        );
    }

    private InputStream openAudioResource(String filename) {
        String normalized = filename.startsWith("/") ? filename.substring(1) : filename;
        String[] resourceCandidates = {
                "/sounds/" + normalized,
                "/assets/" + normalized,
                "/audio/" + normalized,
                "/" + normalized
        };
        for (String path : resourceCandidates) {
            InputStream is = getClass().getResourceAsStream(path);
            if (is != null) return is;
        }

        String[] fileCandidates = {
                "sounds/" + normalized,
                "assets/" + normalized,
                "audio/" + normalized,
                "src/sounds/" + normalized,
                "src/assets/" + normalized,
                "src/audio/" + normalized,
                "src/main/resources/sounds/" + normalized,
                "src/main/resources/assets/" + normalized,
                normalized
        };
        for (String path : fileCandidates) {
            try {
                File f = new File(path);
                if (f.isFile()) {
                    return new FileInputStream(f);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private Clip createSyntheticClip(String filename) {
        try {
            String f = filename == null ? "" : filename.toLowerCase();
            if (f.contains("horn") || f.contains("beep")) {
                return createToneClip(0.18, 760.0, 0.75, false);
            }
            if (f.contains("siren")) {
                return createToneClip(1.2, 620.0, 0.55, true);
            }
            if (f.contains("engine")) {
                return createEngineIdleClip();
            }
            return null;
        } catch (Exception e) {
            System.out.println("[Sound] Synthetic sound failed: " + filename + " - " + e.getMessage());
            return null;
        }
    }

    private Clip createEngineIdleClip() throws Exception {
        float sampleRate = 22050f;
        double seconds = 1.0;
        int frames = Math.max(1, (int) (seconds * sampleRate));
        byte[] data = new byte[frames * 2];
        for (int i = 0; i < frames; i++) {
            double t = i / sampleRate;
            double rumble = 0.55 * Math.sin(2.0 * Math.PI * 82.0 * t)
                    + 0.25 * Math.sin(2.0 * Math.PI * 123.0 * t)
                    + 0.12 * Math.sin(2.0 * Math.PI * 41.0 * t);
            double pulse = 0.72 + 0.28 * Math.sin(2.0 * Math.PI * 7.0 * t);
            short pcm = (short) Math.max(Short.MIN_VALUE,
                    Math.min(Short.MAX_VALUE, rumble * pulse * 0.32 * 32767.0));
            data[i * 2] = (byte) (pcm & 0xff);
            data[i * 2 + 1] = (byte) ((pcm >> 8) & 0xff);
        }
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        AudioInputStream audio = new AudioInputStream(new ByteArrayInputStream(data), format, frames);
        Clip clip = AudioSystem.getClip();
        clip.open(audio);
        return clip;
    }

    private Clip createToneClip(double seconds, double baseFrequency, double volume, boolean sweep) throws Exception {
        float sampleRate = 22050f;
        int frames = Math.max(1, (int) (seconds * sampleRate));
        byte[] data = new byte[frames * 2];
        for (int i = 0; i < frames; i++) {
            double t = i / sampleRate;
            double env = envelope(i, frames);
            double freq = sweep
                    ? baseFrequency + 260.0 * (0.5 + 0.5 * Math.sin(2.0 * Math.PI * 2.0 * t))
                    : baseFrequency;
            double sample = Math.sin(2.0 * Math.PI * freq * t) * env * Math.max(0.0, Math.min(1.0, volume));
            short pcm = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample * 32767.0));
            data[i * 2] = (byte) (pcm & 0xff);
            data[i * 2 + 1] = (byte) ((pcm >> 8) & 0xff);
        }
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        AudioInputStream audio = new AudioInputStream(new ByteArrayInputStream(data), format, frames);
        Clip clip = AudioSystem.getClip();
        clip.open(audio);
        return clip;
    }

    private double envelope(int index, int total) {
        if (total <= 1) return 1.0;
        double x = index / (double) (total - 1);
        double attack = Math.min(1.0, x / 0.08);
        double release = Math.min(1.0, (1.0 - x) / 0.18);
        return Math.max(0.0, Math.min(1.0, Math.min(attack, release)));
    }
}
