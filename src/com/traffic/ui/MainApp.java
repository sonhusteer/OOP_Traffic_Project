package com.traffic.ui;

import com.traffic.core.IRenderer;
import com.traffic.core.TrafficEngine;
import com.traffic.core.Vehicle;
import com.traffic.core.VehicleFactory;
import com.traffic.map.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;

/**
 * Entry point của toàn bộ simulation.
 *
 * Tận dụng:
 *  - VehicleFactory     → tạo xe không cần biết class cụ thể
 *  - RoadNetwork        → quản lý và đăng ký đèn vào engine
 *  - TrafficEngine      → tick + render
 *  - IRenderer          → đổi chế độ vẽ lúc runtime (BasicRenderer / JavaFXRenderer)
 *  - SoundManager       → âm thanh xe ưu tiên
 *
 * [MỚI] SpawnPanel sidebar bên phải:
 *  - Chọn loại xe (car/motorcycle/bicycle/ambulance/firetruck)
 *  - Chọn làn đường (road1–road4)
 *  - Chọn offset xuất phát (đầu / giữa / cuối làn)
 *  - Nút Spawn → thêm xe ngay lập tức
 *  - Nút Clear All → xóa toàn bộ xe
 *  - Log hiển thị lịch sử spawn
 */
public class MainApp {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MainApp::launch);
    }

    private static void launch() {

        // ──── 1. THIẾT LẬP ĐÈN ĐỒNG BỘ ──────────────────────────────────
        TrafficLight lightH1 = new CountdownLight(10, 13, 355, 255);
        TrafficLight lightH2 = new NoCountdownLight(10, 13, 445, 325);
        lightH1.setInitialState(TrafficLight.State.GREEN, 10);
        lightH2.setInitialState(TrafficLight.State.GREEN, 10);

        TrafficLight lightV1 = new SmartTrafficLight(10, 13, 425, 255);
        TrafficLight lightV2 = new Last10SecondsLight(10, 13, 355, 325);
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

        Intersection ngaTu = new Intersection(Intersection.Type.CROSSROADS, 400, 300);
        for (Lane lane : allLanes) ngaTu.addLane(lane);

        RoadNetwork network = new RoadNetwork();
        network.addIntersection(ngaTu);

        // ──── 3. RENDERER & ENGINE ────────────────────────────────────────
        AtomicReference<IRenderer> rendererRef = new AtomicReference<>();

        BasicRenderer  basicRenderer   = new BasicRenderer(allLanes);
        JavaFXRenderer graphicRenderer = new JavaFXRenderer(allLanes);
        rendererRef.set(basicRenderer);

        TrafficEngine engine = new TrafficEngine(basicRenderer);
        network.registerTo(engine);

        // ──── 4. TẠO PHƯƠNG TIỆN BAN ĐẦU ────────────────────────────────
        spawnVehicles(engine, road1, road2, road3, road4);

        // ──── 5. GIAO DIỆN CHÍNH ─────────────────────────────────────────
        JFrame frame = new JFrame("🚦 Traffic Simulation");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JPanel centerPanel = new JPanel(new CardLayout());
        centerPanel.add(basicRenderer,   "basic");
        centerPanel.add(graphicRenderer, "graphic");
        frame.add(centerPanel, BorderLayout.CENTER);

        // ──── 6. SPAWN PANEL (sidebar phải) ──────────────────────────────
        Lane[] laneArray = { road1, road2, road3, road4 };
        JTextArea spawnLog = new JTextArea(6, 22);
        JPanel spawnPanel = buildSpawnPanel(engine, laneArray, spawnLog);
        frame.add(spawnPanel, BorderLayout.EAST);

        // ──── 7. CONTROL PANEL (dưới) ────────────────────────────────────
        AtomicReference<Double> simSpeed = new AtomicReference<>(1.0);
        AtomicBoolean paused  = new AtomicBoolean(false);
        AtomicBoolean isBasic = new AtomicBoolean(true);

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

        JButton btnPause = new JButton("⏸  Pause");
        btnPause.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnPause.setPreferredSize(new Dimension(110, 32));
        btnPause.addActionListener(e -> {
            boolean now = !paused.get();
            paused.set(now);
            btnPause.setText(now ? "▶  Resume" : "⏸  Pause");
        });

        JButton btnMode = new JButton("🎨 → Graphic");
        btnMode.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnMode.setPreferredSize(new Dimension(130, 32));
        btnMode.addActionListener(e -> {
            CardLayout cl = (CardLayout) centerPanel.getLayout();
            if (isBasic.get()) {
                engine.setRenderer(graphicRenderer);
                cl.show(centerPanel, "graphic");
                btnMode.setText("⬜ → Basic");
                isBasic.set(false);
            } else {
                engine.setRenderer(basicRenderer);
                cl.show(centerPanel, "basic");
                btnMode.setText("🎨 → Graphic");
                isBasic.set(true);
            }
        });

        JButton btnMute = new JButton("🔊 Mute");
        btnMute.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnMute.setPreferredSize(new Dimension(100, 32));
        btnMute.addActionListener(e -> {
            SoundManager sm = SoundManager.getInstance();
            sm.setMuted(!sm.isMuted());
            btnMute.setText(sm.isMuted() ? "🔇 Unmute" : "🔊 Mute");
        });

        JLabel lblVehicles = new JLabel("Xe: 0");
        lblVehicles.setFont(new Font("SansSerif", Font.PLAIN, 12));

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
        frame.setSize(1060, 660);  // rộng hơn để chứa sidebar
        frame.setLocationRelativeTo(null);

        // ──── 8. VÒNG LẶP SIMULATION ─────────────────────────────────────
        Timer timer = new Timer(30, e -> {
            if (!paused.get()) {
                double dt = 0.03 * simSpeed.get();
                engine.tick(dt);
                engine.render();

                for (Vehicle v : engine.getVehicles()) {
                    if (v.isPriority() && v.getSpeed() > 0)
                        SoundManager.getInstance().loop("siren.wav");
                }

                lblVehicles.setText("Xe: " + engine.getVehicles().size());
            }

            if (isBasic.get()) basicRenderer.repaint();
            else                graphicRenderer.repaint();
        });

        timer.start();
        frame.setVisible(true);
    }

    // ── Spawn Panel sidebar ───────────────────────────────────────────────

    /**
     * Tạo panel bên phải để spawn xe lúc runtime.
     * Gồm:
     *  - Combo chọn loại xe
     *  - Combo chọn làn đường
     *  - Combo chọn offset xuất phát
     *  - Spinner số lượng xe (1–10)
     *  - Nút Spawn + Clear All
     *  - Text area log
     */
    private static JPanel buildSpawnPanel(TrafficEngine engine,
                                          Lane[] lanes,
                                          JTextArea logArea) {

        // ── Outer panel ──────────────────────────────────────────────────
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(30, 30, 40));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(80, 80, 100)),
                BorderFactory.createEmptyBorder(12, 10, 12, 10)
        ));
        panel.setPreferredSize(new Dimension(220, 0));

        // ── Tiêu đề ──────────────────────────────────────────────────────
        JLabel title = new JLabel("🚗  Spawn Vehicle");
        title.setFont(new Font("SansSerif", Font.BOLD, 14));
        title.setForeground(new Color(200, 220, 255));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(12));

        // ── Loại xe ──────────────────────────────────────────────────────
        panel.add(makeLabel("Loại xe:"));
        String[] types = { "car", "motorcycle", "bicycle", "ambulance", "firetruck" };
        String[] typeLabels = { "🚗 Car", "🏍 Motorcycle", "🚲 Bicycle", "🚑 Ambulance", "🚒 Firetruck" };
        JComboBox<String> cmbType = makeCombo(typeLabels);
        panel.add(cmbType);
        panel.add(Box.createVerticalStrut(10));

        // ── Làn đường ────────────────────────────────────────────────────
        panel.add(makeLabel("Làn đường:"));
        String[] laneLabels = {
            "Làn 1 → Phải",
            "Làn 2 ← Trái",
            "Làn 3 ↓ Xuống",
            "Làn 4 ↑ Lên"
        };
        JComboBox<String> cmbLane = makeCombo(laneLabels);
        panel.add(cmbLane);
        panel.add(Box.createVerticalStrut(10));

        // ── Vị trí xuất phát ─────────────────────────────────────────────
        panel.add(makeLabel("Vị trí xuất phát:"));
        String[] offsetLabels = { "▶ Đầu làn (mặc định)", "◉ Giữa làn", "◀ Cuối làn" };
        JComboBox<String> cmbOffset = makeCombo(offsetLabels);
        panel.add(cmbOffset);
        panel.add(Box.createVerticalStrut(10));

        // ── Số lượng xe ──────────────────────────────────────────────────
        panel.add(makeLabel("Số lượng:"));
        SpinnerNumberModel spinModel = new SpinnerNumberModel(1, 1, 10, 1);
        JSpinner spinner = new JSpinner(spinModel);
        spinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        spinner.setAlignmentX(Component.LEFT_ALIGNMENT);
        styleSpinner(spinner);
        panel.add(spinner);
        panel.add(Box.createVerticalStrut(14));

        // ── Nút Spawn ─────────────────────────────────────────────────────
        JButton btnSpawn = makeButton("➕  Spawn", new Color(40, 160, 80));
        btnSpawn.addActionListener(e -> {
            String type   = types[cmbType.getSelectedIndex()];
            Lane   lane   = lanes[cmbLane.getSelectedIndex()];
            int    count  = (int) spinner.getValue();
            int    offIdx = cmbOffset.getSelectedIndex();

            for (int i = 0; i < count; i++) {
                Vehicle v = VehicleFactory.create(type, 0, 0);
                v.setLane(lane);

                // Áp dụng offset theo lựa chọn
                applyOffset(v, lane, offIdx, i);

                engine.addVehicle(v);
            }

            // Ghi log
            String laneStr = laneLabels[cmbLane.getSelectedIndex()];
            String msg = String.format("[+] %dx %s → %s\n", count, type, laneStr);
            logArea.insert(msg, 0);  // chèn lên đầu để thấy mới nhất
        });
        panel.add(btnSpawn);
        panel.add(Box.createVerticalStrut(8));

        // ── Nút Clear All ─────────────────────────────────────────────────
        JButton btnClear = makeButton("🗑  Clear All", new Color(180, 50, 50));
        btnClear.addActionListener(e -> {
            engine.clearVehicles();
            logArea.insert("[!] Đã xóa tất cả xe\n", 0);
        });
        panel.add(btnClear);
        panel.add(Box.createVerticalStrut(16));

        // ── Separator ────────────────────────────────────────────────────
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(80, 80, 100));
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        panel.add(sep);
        panel.add(Box.createVerticalStrut(10));

        // ── Log ───────────────────────────────────────────────────────────
        JLabel logTitle = new JLabel("📋  Lịch sử spawn:");
        logTitle.setFont(new Font("SansSerif", Font.BOLD, 12));
        logTitle.setForeground(new Color(180, 200, 230));
        logTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(logTitle);
        panel.add(Box.createVerticalStrut(6));

        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        logArea.setBackground(new Color(18, 18, 28));
        logArea.setForeground(new Color(160, 220, 160));
        logArea.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JScrollPane scrollLog = new JScrollPane(logArea);
        scrollLog.setAlignmentX(Component.LEFT_ALIGNMENT);
        scrollLog.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        scrollLog.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 80)));
        panel.add(scrollLog);

        return panel;
    }

    // ── Helper: tính offset xuất phát ────────────────────────────────────

    /**
     * offIdx:
     *   0 = đầu làn  → offset (0, 0), xe xếp hàng cách nhau 60px
     *   1 = giữa làn → đặt xe ở khoảng giữa waypoint đầu–cuối
     *   2 = cuối làn → spawn từ cuối, dồn về đầu
     *
     * i = thứ tự xe trong batch (để xếp hàng không đè nhau)
     */
    private static void applyOffset(Vehicle v, Lane lane, int offIdx, int i) {
        int gap = 55; // khoảng cách giữa các xe cùng batch
        switch (offIdx) {
            case 0 -> v.setLaneStartOffset(i * gap, 0);          // đầu làn
            case 1 -> v.setLaneStartOffset(i * gap - 200, 0);    // giữa làn (xấp xỉ)
            case 2 -> v.setLaneStartOffset(i * gap - 400, 0);    // cuối làn
        }
    }

    // ── Helper: style components ──────────────────────────────────────────

    private static JLabel makeLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lbl.setForeground(new Color(190, 200, 220));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    private static JComboBox<String> makeCombo(String[] items) {
        JComboBox<String> combo = new JComboBox<>(items);
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        combo.setAlignmentX(Component.LEFT_ALIGNMENT);
        combo.setBackground(new Color(50, 50, 65));
        combo.setForeground(Color.WHITE);
        combo.setFont(new Font("SansSerif", Font.PLAIN, 12));
        return combo;
    }

    private static void styleSpinner(JSpinner spinner) {
        spinner.getEditor().getComponent(0).setBackground(new Color(50, 50, 65));
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField()
                .setForeground(Color.WHITE);
    }

    private static JButton makeButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // ── Spawn ban đầu ─────────────────────────────────────────────────────

    private static void spawnVehicles(TrafficEngine engine,
                                      Lane road1, Lane road2,
                                      Lane road3, Lane road4) {
        Vehicle car1 = VehicleFactory.create("car", 0, 0);
        car1.setLane(road1);
        engine.addVehicle(car1);

        Vehicle ambulance = VehicleFactory.create("ambulance", 0, 0);
        ambulance.setLane(road1);
        ambulance.setLaneStartOffset(-80, 0);
        engine.addVehicle(ambulance);

        Vehicle car2 = VehicleFactory.create("car", 0, 0);
        car2.setLane(road2);
        engine.addVehicle(car2);

        Vehicle moto1 = VehicleFactory.create("motorcycle", 0, 0);
        moto1.setLane(road2);
        moto1.setLaneStartOffset(60, 0);
        engine.addVehicle(moto1);

        Vehicle car3 = VehicleFactory.create("car", 0, 0);
        car3.setLane(road3);
        engine.addVehicle(car3);

        Vehicle bicycle = VehicleFactory.create("bicycle", 0, 0);
        bicycle.setLane(road3);
        bicycle.setLaneStartOffset(0, -40);
        engine.addVehicle(bicycle);

        Vehicle firetruck = VehicleFactory.create("firetruck", 0, 0);
        firetruck.setLane(road4);
        engine.addVehicle(firetruck);

        Vehicle moto2 = VehicleFactory.create("motorcycle", 0, 0);
        moto2.setLane(road4);
        moto2.setLaneStartOffset(0, 70);
        engine.addVehicle(moto2);
    }
}