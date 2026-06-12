package com.traffic.ui;

import com.traffic.core.TrafficEngine;
import com.traffic.core.Vehicle;
import com.traffic.maps.MapConfig;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;

/**
 * Điều phối simulation: engine + renderers + game loop + trạng thái.
 * Tách khỏi MainApp để UI không trộn lẫn logic mô phỏng.
 */
public class SimulationController {

    private final TrafficEngine engine;
    private final AbstractBaseRenderer basicRenderer;
    private final AbstractBaseRenderer graphicRenderer;
    private AbstractBaseRenderer activeRenderer;
    private final Canvas canvas;

    private boolean isBasicMode = true;
    private boolean paused = false;
    private double simSpeed = 1.0;

    private AnimationTimer timer;
    private Runnable onTick; // callback để MainApp cập nhật UI mỗi frame

    public SimulationController(Canvas canvas, MapConfig initialMap) {
        this.canvas = canvas;
        this.basicRenderer = new BasicRenderer(initialMap.getLanes());
        this.graphicRenderer = new JavaFXRenderer(initialMap.getLanes());
        this.activeRenderer = basicRenderer;

        this.engine = new TrafficEngine(activeRenderer);
        MapLoader.registerMap(engine, initialMap);
    }

    /** Bắt đầu game loop, dùng deltaTime thật từ AnimationTimer.now. */
    public void start() {
        timer = new AnimationTimer() {
            private long lastNow = -1L;

            @Override
            public void handle(long now) {
                if (lastNow < 0) {
                    lastNow = now;
                }

                double realDt = (now - lastNow) / 1_000_000_000.0;
                lastNow = now;
                double dt = Math.min(realDt, 0.1) * simSpeed;

                if (!paused) {
                    engine.tick(dt);
                    engine.render();
                    updateSiren();
                } else {
                    SoundManager.getInstance().stop("siren.wav");
                }

                GraphicsContext gc = canvas.getGraphicsContext2D();
                gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
                activeRenderer.draw(gc, canvas.getWidth(), canvas.getHeight());

                if (onTick != null) onTick.run();
            }
        };
        timer.start();
    }

    /** Đổi map. Nên truyền map object mới để tránh giữ state cũ của đèn/lane. */
    public void loadMap(MapConfig newMap) {
        MapLoader.loadMap(engine, newMap);
        basicRenderer.setLanes(newMap.getLanes());
        graphicRenderer.setLanes(newMap.getLanes());
    }

    /** Toggle Basic ↔ Graphic. */
    public String toggleMode() {
        if (isBasicMode) {
            activeRenderer = graphicRenderer;
            engine.setRenderer(graphicRenderer);
            isBasicMode = false;
            return "📐 Basic";
        } else {
            activeRenderer = basicRenderer;
            engine.setRenderer(basicRenderer);
            isBasicMode = true;
            return "🎨 Graphic";
        }
    }

    private void updateSiren() {
        boolean hasEmergency = false;
        for (Vehicle v : engine.getVehicles()) {
            if (v.isPriority() && v.getSpeed() > 0) {
                hasEmergency = true;
                break;
            }
        }
        if (hasEmergency) {
            SoundManager.getInstance().loop("siren.wav");
        } else {
            SoundManager.getInstance().stop("siren.wav");
        }
    }

    public void togglePause() { paused = !paused; }
    public boolean isPaused() { return paused; }
    public void setSimSpeed(double s) { this.simSpeed = s; }
    public double getSimSpeed() { return simSpeed; }
    public TrafficEngine getEngine() { return engine; }
    public Canvas getCanvas() { return canvas; }
    public AbstractBaseRenderer getActiveRenderer() { return activeRenderer; }

    public void setOnTick(Runnable r) { this.onTick = r; }
}
