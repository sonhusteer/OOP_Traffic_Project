package com.traffic.ui;

import javax.sound.sampled.*;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Quản lý âm thanh — Singleton, dùng chung toàn project.
 *
 * Hỗ trợ: play, loop, stop từng file.
 * Cache clip đã load để không đọc file lại mỗi lần.
 *
 * Đặt file .wav vào: src/main/resources/sounds/
 */
public class SoundManager {

    private static SoundManager instance;
    private final Map<String, Clip> cache = new HashMap<>();

    /**
     * FIX: cache cả file bị thiếu/lỗi để không in log lỗi hàng chục lần mỗi giây
     * khi siren.wav chưa có trong resources.
     */
    private final Set<String> unavailable = new HashSet<>();

    private boolean muted = false;

    private SoundManager() {}

    public static SoundManager getInstance() {
        if (instance == null) instance = new SoundManager();
        return instance;
    }

    // ── Phát âm thanh ────────────────────────────────────────────────────

    /** Phát 1 lần. */
    public void play(String filename) {
        if (muted) return;
        Clip clip = getClip(filename);
        if (clip == null) return;
        clip.setFramePosition(0);
        clip.start();
    }

    /** Phát lặp liên tục, ví dụ còi xe ưu tiên. */
    public void loop(String filename) {
        if (muted) return;
        Clip clip = getClip(filename);
        if (clip == null) return;
        if (clip.isRunning()) return;
        clip.setFramePosition(0);
        clip.loop(Clip.LOOP_CONTINUOUSLY);
    }

    /** Dừng âm thanh theo tên file. */
    public void stop(String filename) {
        Clip clip = cache.get(filename);
        if (clip != null && clip.isRunning()) clip.stop();
    }

    /** Dừng tất cả âm thanh. */
    public void stopAll() {
        cache.values().forEach(clip -> {
            if (clip.isRunning()) clip.stop();
        });
    }

    /** Tắt/bật âm thanh toàn bộ. */
    public void setMuted(boolean muted) {
        this.muted = muted;
        if (muted) stopAll();
    }

    public boolean isMuted() {
        return muted;
    }

    // ── Load và cache clip ───────────────────────────────────────────────

    private Clip getClip(String filename) {
        if (cache.containsKey(filename)) return cache.get(filename);
        if (unavailable.contains(filename)) return null;

        try {
            InputStream is = getClass().getResourceAsStream("/sounds/" + filename);
            if (is == null) {
                unavailable.add(filename);
                System.out.println("[Sound] Không tìm thấy: " + filename);
                return null;
            }

            AudioInputStream audio = AudioSystem.getAudioInputStream(
                new java.io.BufferedInputStream(is)
            );
            Clip clip = AudioSystem.getClip();
            clip.open(audio);
            cache.put(filename, clip);
            return clip;
        } catch (Exception e) {
            unavailable.add(filename);
            System.out.println("[Sound] Lỗi load: " + filename + " — " + e.getMessage());
            return null;
        }
    }
}
