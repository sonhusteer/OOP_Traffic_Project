package com.traffic.ui;

import javax.sound.sampled.*;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Quản lý âm thanh — Singleton, dùng chung toàn project.
 *
 * Hỗ trợ: play, loop, stop từng file.
 * Cache clip đã load để không đọc file lại mỗi lần.
 *
 * Cách dùng:
 *   SoundManager.getInstance().play("siren.wav");
 *   SoundManager.getInstance().loop("engine.wav");
 *   SoundManager.getInstance().stop("siren.wav");
 *
 * Đặt file .wav vào: src/main/resources/sounds/
 */
public class SoundManager {

    private static SoundManager instance;
    private final Map<String, Clip> cache = new HashMap<>();
    private boolean muted = false;

    private SoundManager() {}

    public static SoundManager getInstance() {
        if (instance == null) instance = new SoundManager();
        return instance;
    }

    // ── Phát âm thanh ────────────────────────────────────────────────────

    /** Phát 1 lần */
    public void play(String filename) {
        if (muted) return;
        Clip clip = getClip(filename);
        if (clip == null) return;
        clip.setFramePosition(0);
        clip.start();
    }

    /** Phát lặp liên tục (VD: còi xe chạy) */
    public void loop(String filename) {
        if (muted) return;
        Clip clip = getClip(filename);
        if (clip == null) return;
        if (clip.isRunning()) return; // đang loop rồi, không cần start lại
        clip.setFramePosition(0);
        clip.loop(Clip.LOOP_CONTINUOUSLY);
    }

    /** Dừng âm thanh */
    public void stop(String filename) {
        Clip clip = cache.get(filename);
        if (clip != null && clip.isRunning()) clip.stop();
    }

    /** Dừng tất cả âm thanh */
    public void stopAll() {
        cache.values().forEach(clip -> {
            if (clip.isRunning()) clip.stop();
        });
    }

    /** Tắt/bật âm thanh toàn bộ */
    public void setMuted(boolean muted) {
        this.muted = muted;
        if (muted) stopAll();
    }

    public boolean isMuted() { return muted; }

    // ── Load và cache clip ────────────────────────────────────────────────

    private static final String MISSING = "__MISSING__";
    private final java.util.Set<String> missingFiles = new java.util.HashSet<>();

    private Clip getClip(String filename) {
        // File đã biết là không tồn tại → bỏ qua hoàn toàn
        if (missingFiles.contains(filename)) return null;

        // Đã cache thì dùng lại
        if (cache.containsKey(filename)) return cache.get(filename);

        try {
            InputStream is = getClass().getResourceAsStream("/sounds/" + filename);
            if (is == null) {
                System.out.println("[Sound] Không tìm thấy: " + filename + " — âm thanh bị tắt.");
                missingFiles.add(filename); // đánh dấu để không log lại
                return null;
            }
            AudioInputStream audio = AudioSystem.getAudioInputStream(
                    new java.io.BufferedInputStream(is));
            Clip clip = AudioSystem.getClip();
            clip.open(audio);
            cache.put(filename, clip);
            return clip;
        } catch (Exception e) {
            System.out.println("[Sound] Lỗi load: " + filename + " — " + e.getMessage());
            missingFiles.add(filename);
            return null;
        }
    }
}
