package com.traffic.ui;

import com.traffic.core.TrafficEngine;
import com.traffic.core.IRenderer;
import com.traffic.core.Vehicle;
import com.traffic.core.VehicleFactory;
import com.traffic.map.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Entry point của toàn bộ simulation.
 *
 * Tận dụng:
 *  - VehicleFactory     → tạo xe không cần biết class cụ thể
 *  - RoadNetwork        → quản lý và đăng ký đèn vào engine
 *  - TrafficEngine      → tick + render
 *  - IRenderer          → đổi chế độ vẽ lúc runtime (BasicRenderer / JavaFXRenderer)
 *  - SoundManager       → âm thanh xe ưu tiên
 */
public class MainApp {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MainApp::launch);
    }

    private static void launch() {

        // ──── 1. THIẾT LẬP ĐÈN ĐỒNG BỘ ──────────────────────────────────
        // Pha ngang: bắt đầu XANH 10s
        TrafficLight lightH1 = new CountdownLight(10, 13, 370, 280);
        TrafficLight lightH2 = new NoCountdownLight(10, 13, 430, 320);
        lightH1.setInitialState(TrafficLight.State.GREEN, 10);
        lightH2.setInitialState(TrafficLight.State.GREEN, 10);

        // Pha dọc: bắt đầu ĐỎ 13s (chờ pha ngang xong)
        TrafficLight lightV1 = new SmartTrafficLight(10, 13, 380, 270);
        TrafficLight lightV2 = new Last10SecondsLight(10, 13, 420, 330);
        lightV1.setInitialState(TrafficLight.State.RED, 13);
        lightV2.setInitialState(TrafficLight.State.RED, 13);

        // ──── 2. THIẾT LẬP LÀN ĐƯỜNG & NGÃ TƯ ───────────────────────────
        List<Lane> allLanes = new ArrayList<>();

        Lane road1 = new Lane(50, 280, 750, 280, lightH1); // → Phải
        road1.addwaypoint(370, 280);
        allLanes.add(road1);

        Lane road2 = new Lane(750, 320, 50, 320, lightH2); // ← Trái
        road2.addwaypoint(430, 320);
        allLanes.add(road2);

        Lane road3 = new Lane(380, 50, 380, 550, lightV1); // ↓ Xuống
        road3.addwaypoint(380, 270);
        allLanes.add(road3);

        Lane road4 = new Lane(420, 550, 420, 50, lightV2); // ↑ Lên
        road4.addwaypoint(420, 330);
        allLanes.add(road4);

        // Dùng RoadNetwork để đăng ký đèn vào engine
        Intersection ngaTu = new Intersection(
                Intersection.Type.CROSSROADS, 400, 300);
        for (Lane lane : allLanes) ngaTu.addLane(lane);

        RoadNetwork network = new RoadNetwork();
        network.addIntersection(ngaTu);

        // ──── 3. RENDERER & ENGINE ────────────────────────────────────────
        // Bắt đầu bằng BasicRenderer — có thể đổi sang JavaFXRenderer lúc runtime
        AtomicReference<IRenderer> rendererRef = new AtomicReference<>();

        BasicRenderer    basicRenderer   = new BasicRenderer(allLanes);
        JavaFXRenderer   graphicRenderer = new JavaFXRenderer(allLanes);
        rendererRef.set(basicRenderer);

        TrafficEngine engine = new TrafficEngine(basicRenderer);

        // Đăng ký đèn từ RoadNetwork vào engine (1 lần duy nhất)
        network.registerTo(engine);

        // ──── 4. TẠO PHƯƠNG TIỆN ─────────────────────────────────────────
        spawnVehicles(engine, road1, road2, road3, road4);

        // ──── 5. GIAO DIỆN CHÍNH ─────────────────────────────────────────
        JFrame frame = new JFrame("🚦 Traffic Simulation");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // Panel trung tâm — hiển thị renderer đang active
        JPanel centerPanel = new JPanel(new CardLayout());
        centerPanel.add(basicRenderer,   "basic");
        centerPanel.add(graphicRenderer, "graphic");
        frame.add(centerPanel, BorderLayout.CENTER);

        // ──── 6. CONTROL PANEL ───────────────────────────────────────────
        AtomicReference<Double>  simSpeed = new AtomicReference<>(1.0);
        AtomicBoolean            paused   = new AtomicBoolean(false);
        AtomicBoolean            isBasic  = new AtomicBoolean(true);

        // Slider tốc độ 0.1x → 3.0x
        JSlider speedSlider = new JSlider(1, 30, 10);
        speedSlider.setMajorTickSpacing(10);
        speedSlider.setMinorTickSpacing(5);
        speedSlider.setPaintTicks(true);
        speedSlider.setPreferredSize(new Dimension(180, 40));
        speedSlider.setOpaque(false);

        JLabel speedLabel = new JLabel("Tốc độ: 1.0x");
        speedLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        speedLabel.setPreferredSize(new Dimension(100, 30));

        speedSlider.addChangeListener(e -> {
            double s = speedSlider.getValue() / 10.0;
            simSpeed.set(s);
            speedLabel.setText(String.format("Tốc độ: %.1fx", s));
        });

        // Nút Pause / Resume
        JButton btnPause = new JButton("⏸  Pause");
        btnPause.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnPause.setPreferredSize(new Dimension(110, 32));
        btnPause.addActionListener(e -> {
            boolean now = !paused.get();
            paused.set(now);
            btnPause.setText(now ? "▶  Resume" : "⏸  Pause");
        });

        // Nút đổi chế độ vẽ — tận dụng IRenderer polymorphism
        JButton btnMode = new JButton("🎨 → Graphic");
        btnMode.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnMode.setPreferredSize(new Dimension(130, 32));
        btnMode.addActionListener(e -> {
            CardLayout cl = (CardLayout) centerPanel.getLayout();
            if (isBasic.get()) {
                // Đổi sang Graphic
                engine.setRenderer(graphicRenderer);
                cl.show(centerPanel, "graphic");
                btnMode.setText("⬜ → Basic");
                isBasic.set(false);
            } else {
                // Đổi về Basic
                engine.setRenderer(basicRenderer);
                cl.show(centerPanel, "basic");
                btnMode.setText("🎨 → Graphic");
                isBasic.set(true);
            }
        });

        // Nút Mute âm thanh
        JButton btnMute = new JButton("🔊 Mute");
        btnMute.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnMute.setPreferredSize(new Dimension(100, 32));
        btnMute.addActionListener(e -> {
            SoundManager sm = SoundManager.getInstance();
            sm.setMuted(!sm.isMuted());
            btnMute.setText(sm.isMuted() ? "🔇 Unmute" : "🔊 Mute");
        });

        // Label đếm số xe
        JLabel lblVehicles = new JLabel("Xe: 0");
        lblVehicles.setFont(new Font("SansSerif", Font.PLAIN, 12));

        // Ghép control panel
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
        controlPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY));
        controlPanel.add(new JLabel("Tốc độ:"));
        controlPanel.add(speedSlider);
        controlPanel.add(speedLabel);
        controlPanel.add(Box.createHorizontalStrut(10));
        controlPanel.add(btnPause);
        controlPanel.add(btnMode);
        controlPanel.add(btnMute);
        controlPanel.add(Box.createHorizontalStrut(10));
        controlPanel.add(lblVehicles);

        frame.add(controlPanel, BorderLayout.SOUTH);
        frame.setSize(820, 660);
        frame.setLocationRelativeTo(null);

        // ──── 7. VÒNG LẶP SIMULATION ─────────────────────────────────────
        Timer timer = new Timer(30, e -> {
            if (!paused.get()) {
                double dt = 0.03 * simSpeed.get();
                engine.tick(dt);
                engine.render();

                // Phát còi cho xe ưu tiên
                for (Vehicle v : engine.getVehicles()) {
                    if (v.isPriority() && v.getSpeed() > 0) {
                        SoundManager.getInstance().loop("siren.wav");
                    }
                }

                // Cập nhật label số xe
                lblVehicles.setText("Xe: " + engine.getVehicles().size());
            }

            // Repaint renderer đang active
            if (isBasic.get()) basicRenderer.repaint();
            else                graphicRenderer.repaint();
        });

        timer.start();
        frame.setVisible(true);
    }

    // ── Tạo xe — tận dụng VehicleFactory ─────────────────────────────────

    private static void spawnVehicles(TrafficEngine engine,
                                      Lane road1, Lane road2,
                                      Lane road3, Lane road4) {
        // Làn 1: → Phải
        Vehicle car1 = VehicleFactory.create("car", 0, 0);
        car1.setLane(road1);
        engine.addVehicle(car1);

        Vehicle ambulance = VehicleFactory.create("ambulance", 0, 0);
        ambulance.setLane(road1);
        ambulance.setLaneStartOffset(-80, 0);
        engine.addVehicle(ambulance);

        // Làn 2: ← Trái
        Vehicle car2 = VehicleFactory.create("car", 0, 0);
        car2.setLane(road2);
        engine.addVehicle(car2);

        Vehicle moto1 = VehicleFactory.create("motorcycle", 0, 0);
        moto1.setLane(road2);
        moto1.setLaneStartOffset(60, 0);
        engine.addVehicle(moto1);

        // Làn 3: ↓ Xuống
        Vehicle car3 = VehicleFactory.create("car", 0, 0);
        car3.setLane(road3);
        engine.addVehicle(car3);

        Vehicle bicycle = VehicleFactory.create("bicycle", 0, 0);
        bicycle.setLane(road3);
        bicycle.setLaneStartOffset(0, -40);
        engine.addVehicle(bicycle);

        // Làn 4: ↑ Lên
        Vehicle firetruck = VehicleFactory.create("firetruck", 0, 0);
        firetruck.setLane(road4);
        engine.addVehicle(firetruck);

        Vehicle moto2 = VehicleFactory.create("motorcycle", 0, 0);
        moto2.setLane(road4);
        moto2.setLaneStartOffset(0, 70);
        engine.addVehicle(moto2);
    }
}
