package com.traffic.ui;

import com.traffic.core.TrafficEngine;
import com.traffic.core.Vehicle;
import com.traffic.core.VehicleSpawner;
import com.traffic.map.*;
import com.traffic.maps.*;
import javafx.application.Application;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Entry‑point JavaFX — thay thế hoàn toàn Swing.
 */
public class MainApp extends Application {

    private static final MapConfig[] ALL_MAPS = {
        new CrossroadsMap(),
        new TJunctionMap(),
        new FiveWayMap(),
        new NetworkMap(),
        new HighwayMap()
    };

    private static final int MAX_AUTO_SPAWN_BURSTS_PER_FRAME = 2;

    // ── State ────────────────────────────────────────────────────────────
    private MapConfig currentMap;
    private TrafficEngine engine;
    private VehicleSpawner vehicleSpawner;
    private AbstractBaseRenderer basicRenderer;
    private AbstractBaseRenderer graphicRenderer;
    private AbstractBaseRenderer activeRenderer;
    private boolean isBasicMode = true;
    private boolean paused = false;
    private double simSpeed = 1.0;

    private final Random random = new Random();
    private boolean autoSpawnEnabled = false;
    private double autoSpawnTimerSeconds = 0.0;
    private double autoSpawnIntervalSeconds = 1.15;

    private Canvas canvas;
    private Label lblVehicles;
    private Label lblSpeed;
    private VBox spawnContainer;
    private TextArea spawnLog;

    // ── Entry Point ──────────────────────────────────────────────────────

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        // Khởi tạo map & renderer
        currentMap = ALL_MAPS[0];
        basicRenderer   = new BasicRenderer(currentMap.getLanes());
        graphicRenderer = new JavaFXRenderer(currentMap.getLanes());
        activeRenderer  = basicRenderer;

        engine = new TrafficEngine(activeRenderer);
        vehicleSpawner = new VehicleSpawner(engine);
        registerMap(engine, currentMap);

        // ── Layout ───────────────────────────────────────────────────────
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1a1a2e;");

        // Canvas (center)
        canvas = new Canvas(800, 600);
        StackPane canvasHolder = new StackPane(canvas);
        canvasHolder.setStyle("-fx-background-color: #1a1a2e;");
        root.setCenter(canvasHolder);

        // Mouse handler
        canvas.setOnMouseClicked(e -> {
            boolean left = e.getButton() == MouseButton.PRIMARY;
            activeRenderer.handleClick(e.getX(), e.getY(), left);
        });

        // Sidebar (right)
        spawnLog = new TextArea();
        spawnContainer = buildSidebar(currentMap);
        root.setRight(spawnContainer);

        // Toolbar (bottom)
        HBox toolbar = buildToolbar();
        root.setBottom(toolbar);

        // ── Scene ────────────────────────────────────────────────────────
        Scene scene = new Scene(root, 1080, 680);
        try {
            String css = getClass().getResource("/style.css") != null
                       ? getClass().getResource("/style.css").toExternalForm()
                       : null;
            if (css != null) scene.getStylesheets().add(css);
        } catch (Exception ignored) {}

        stage.setTitle("🚦 Traffic Simulation");
        stage.setScene(scene);
        stage.show();

        // ── Game Loop ────────────────────────────────────────────────────
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!paused) {
                    double dt = 0.03 * simSpeed;
                    updateAutoSpawn(dt);
                    engine.tick(dt);
                    engine.render();

                    // Siren
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

                // Draw
                GraphicsContext gc = canvas.getGraphicsContext2D();
                gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
                activeRenderer.draw(gc, canvas.getWidth(), canvas.getHeight());

                lblVehicles.setText("🚗 " + engine.getVehicles().size());
            }
        };
        timer.start();
    }

    // ── Sidebar ──────────────────────────────────────────────────────────

    private VBox buildSidebar(MapConfig map) {
        VBox sidebar = new VBox(10);
        sidebar.setPadding(new Insets(16, 12, 16, 12));
        sidebar.setPrefWidth(230);
        sidebar.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #1e1e30, #16162a);"
          + "-fx-border-color: #333355; -fx-border-width: 0 0 0 1;"
        );

        // Title
        Label title = new Label("🚘 Spawn Vehicle");
        title.setFont(Font.font("SansSerif", FontWeight.BOLD, 15));
        title.setTextFill(Color.rgb(180, 200, 255));

        // Vehicle type
        Label lblType = makeLabel("Loại xe:");
        String[] types = {"car", "motorcycle", "bicycle", "ambulance", "firetruck"};
        String[] typeLabels = {"Car", "Motorcycle", "Bicycle", "Ambulance", "Firetruck"};
        ComboBox<String> cmbType = new ComboBox<>();
        cmbType.getItems().addAll(typeLabels);
        cmbType.getSelectionModel().selectFirst();
        cmbType.setMaxWidth(Double.MAX_VALUE);
        cmbType.getStyleClass().add("dark-combo");

        // Lane
        Label lblLane = makeLabel("Làn đường:");
        List<Lane> spawnableLanes = getSpawnableLanes(map);
        String[] laneNames = getSpawnLaneNames(map, spawnableLanes);
        ComboBox<String> cmbLane = new ComboBox<>();
        cmbLane.getItems().addAll(laneNames);
        cmbLane.getSelectionModel().selectFirst();
        cmbLane.setMaxWidth(Double.MAX_VALUE);
        cmbLane.getStyleClass().add("dark-combo");

        // Longitudinal spawn position
        Label lblOffset = makeLabel("Vị trí dọc làn:");
        ComboBox<String> cmbOffset = new ComboBox<>();
        cmbOffset.getItems().addAll("Đầu làn", "Giữa làn", "Cuối làn");
        cmbOffset.getSelectionModel().selectFirst();
        cmbOffset.setMaxWidth(Double.MAX_VALUE);
        cmbOffset.getStyleClass().add("dark-combo");

        // Lateral slot
        Label lblLateral = makeLabel("Phần ngang:");
        ComboBox<String> cmbLateral = new ComboBox<>();
        cmbLateral.getItems().addAll("Tự động trái/phải", "Nửa trái", "Giữa làn", "Nửa phải");
        cmbLateral.getSelectionModel().selectFirst();
        cmbLateral.setMaxWidth(Double.MAX_VALUE);
        cmbLateral.getStyleClass().add("dark-combo");

        // Count
        Label lblCount = makeLabel("Số lượng:");
        Spinner<Integer> spinner = new Spinner<>(1, 6, 1);
        spinner.setMaxWidth(Double.MAX_VALUE);

        // Auto spawn controls
        Label lblAuto = makeLabel("Tự động:");
        ToggleButton btnAutoSpawn = new ToggleButton(autoSpawnEnabled ? "⏸ Auto Spawn: ON" : "▶ Auto Spawn: OFF");
        btnAutoSpawn.getStyleClass().add("btn-action");
        btnAutoSpawn.setMaxWidth(Double.MAX_VALUE);
        btnAutoSpawn.setSelected(autoSpawnEnabled);
        btnAutoSpawn.setOnAction(e -> {
            autoSpawnEnabled = btnAutoSpawn.isSelected();
            autoSpawnTimerSeconds = 0.0;
            btnAutoSpawn.setText(autoSpawnEnabled ? "⏸ Auto Spawn: ON" : "▶ Auto Spawn: OFF");
            appendSpawnLog(autoSpawnEnabled
                    ? "[A] Auto spawn bật: sinh xe ngẫu nhiên trái/phải\n"
                    : "[A] Auto spawn tắt\n");
        });

        Slider autoRateSlider = new Slider(0.45, 3.0, autoSpawnIntervalSeconds);
        autoRateSlider.setShowTickMarks(true);
        autoRateSlider.setMajorTickUnit(0.5);
        Label lblAutoRate = makeLabel(String.format("Nhịp: %.2fs", autoSpawnIntervalSeconds));
        autoRateSlider.valueProperty().addListener((obs, oldV, newV) -> {
            autoSpawnIntervalSeconds = newV.doubleValue();
            lblAutoRate.setText(String.format("Nhịp: %.2fs", autoSpawnIntervalSeconds));
        });

        // Spawn button
        Button btnSpawn = new Button("✦ Spawn");
        btnSpawn.getStyleClass().add("btn-spawn");
        btnSpawn.setMaxWidth(Double.MAX_VALUE);
        Lane[] laneArray = spawnableLanes.toArray(new Lane[0]);
        btnSpawn.setOnAction(e -> {
            int laneIdx = cmbLane.getSelectionModel().getSelectedIndex();
            if (laneIdx < 0 || laneIdx >= laneArray.length) return;

            String type = types[cmbType.getSelectionModel().getSelectedIndex()];
            Lane lane = laneArray[laneIdx];
            int count = spinner.getValue();
            VehicleSpawner.SpawnPosition spawnPosition = toSpawnPosition(
                cmbOffset.getSelectionModel().getSelectedIndex());
            VehicleSpawner.SpawnLateralMode lateralMode = toSpawnLateralMode(
                cmbLateral.getSelectionModel().getSelectedIndex());

            int spawned = vehicleSpawner.spawn(
                type, lane, spawnPosition, lateralMode, count);

            appendSpawnLog(String.format("[+] %d/%d %s → %s (%s)\n",
                spawned, count, type, laneNames[laneIdx],
                cmbLateral.getSelectionModel().getSelectedItem()));
        });

        // Clear button
        Button btnClear = new Button("✕ Clear All");
        btnClear.getStyleClass().add("btn-clear");
        btnClear.setMaxWidth(Double.MAX_VALUE);
        btnClear.setOnAction(e -> {
            engine.clearVehicles();
            vehicleSpawner.clearState();
            appendSpawnLog("[!] Đã xóa tất cả xe\n");
        });

        // Separator
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #444466;");

        // Log
        Label lblLog = makeLabel("📋 Lịch sử:");
        spawnLog.setEditable(false);
        spawnLog.setPrefRowCount(5);
        spawnLog.setWrapText(true);
        spawnLog.setStyle(
            "-fx-control-inner-background: #111120;"
          + "-fx-text-fill: #88cc88;"
          + "-fx-font-family: 'Consolas';"
          + "-fx-font-size: 11px;"
        );

        sidebar.getChildren().addAll(
            title,
            lblType, cmbType,
            lblLane, cmbLane,
            lblOffset, cmbOffset,
            lblLateral, cmbLateral,
            lblCount, spinner,
            lblAuto, btnAutoSpawn, lblAutoRate, autoRateSlider,
            btnSpawn, btnClear,
            sep, lblLog, spawnLog
        );
        VBox.setVgrow(spawnLog, Priority.ALWAYS);
        return sidebar;
    }

    // ── Toolbar ──────────────────────────────────────────────────────────

    private HBox buildToolbar() {
        HBox toolbar = new HBox(14);
        toolbar.setPadding(new Insets(8, 16, 8, 16));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle(
            "-fx-background-color: linear-gradient(to right, #1a1a2e, #16213e);"
          + "-fx-border-color: #333355; -fx-border-width: 1 0 0 0;"
        );

        // Map selector
        Label lblMap = new Label("🗺");
        lblMap.setFont(Font.font(16));
        ComboBox<String> cmbMap = new ComboBox<>();
        for (MapConfig m : ALL_MAPS) cmbMap.getItems().add(m.getName());
        cmbMap.getSelectionModel().selectFirst();
        cmbMap.getStyleClass().add("dark-combo");

        Button btnLoad = new Button("Load");
        btnLoad.getStyleClass().add("btn-action");
        btnLoad.setOnAction(e -> {
            int idx = cmbMap.getSelectionModel().getSelectedIndex();
            if (idx < 0) return;
            loadMap(ALL_MAPS[idx]);
        });

        // Speed slider
        Slider speedSlider = new Slider(0.1, 3.0, 1.0);
        speedSlider.setPrefWidth(160);
        speedSlider.setShowTickLabels(true);
        speedSlider.setMajorTickUnit(1.0);
        lblSpeed = new Label("1.0×");
        lblSpeed.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
        lblSpeed.setTextFill(Color.rgb(180, 200, 255));
        lblSpeed.setPrefWidth(50);
        speedSlider.valueProperty().addListener((obs, oldV, newV) -> {
            simSpeed = newV.doubleValue();
            lblSpeed.setText(String.format("%.1f×", simSpeed));
        });

        // Buttons
        Button btnPause = new Button("⏸ Pause");
        btnPause.getStyleClass().add("btn-action");
        btnPause.setOnAction(e -> {
            paused = !paused;
            btnPause.setText(paused ? "▶ Resume" : "⏸ Pause");
        });

        Button btnMode = new Button("🎨 Graphic");
        btnMode.getStyleClass().add("btn-action");
        btnMode.setOnAction(e -> {
            if (isBasicMode) {
                activeRenderer = graphicRenderer;
                engine.setRenderer(graphicRenderer);
                btnMode.setText("📐 Basic");
                isBasicMode = false;
            } else {
                activeRenderer = basicRenderer;
                engine.setRenderer(basicRenderer);
                btnMode.setText("🎨 Graphic");
                isBasicMode = true;
            }
        });

        Button btnMute = new Button("🔊 Mute");
        btnMute.getStyleClass().add("btn-action");
        btnMute.setOnAction(e -> {
            SoundManager sm = SoundManager.getInstance();
            sm.setMuted(!sm.isMuted());
            btnMute.setText(sm.isMuted() ? "🔇 Unmute" : "🔊 Mute");
        });

        ToggleButton btnRain = new ToggleButton("🌤 Tạnh");
        btnRain.getStyleClass().add("btn-action");
        btnRain.setOnAction(e -> {
            boolean r = btnRain.isSelected();
            basicRenderer.setRaining(r);
            graphicRenderer.setRaining(r);
            btnRain.setText(r ? "🌧 Mưa" : "🌤 Tạnh");
        });

        // Vehicle count
        lblVehicles = new Label("🚗 0");
        lblVehicles.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
        lblVehicles.setTextFill(Color.rgb(180, 220, 180));

        // Spacers
        Region sp1 = new Region(); HBox.setHgrow(sp1, Priority.SOMETIMES);
        Region sp2 = new Region(); sp2.setPrefWidth(8);
        Region sp3 = new Region(); sp3.setPrefWidth(8);

        toolbar.getChildren().addAll(
            lblMap, cmbMap, btnLoad,
            sp2,
            new Label("⚡") {{ setFont(Font.font(14)); }},
            speedSlider, lblSpeed,
            sp3,
            btnPause, btnMode, btnMute, btnRain,
            sp1,
            lblVehicles
        );
        return toolbar;
    }

    // ── Map Loading ──────────────────────────────────────────────────────

    private void loadMap(MapConfig newMap) {
        engine.clearVehicles();
        engine.getLights().clear();
        engine.getIntersections().clear();
        vehicleSpawner.clearState();

        currentMap = newMap;
        registerMap(engine, newMap);
        basicRenderer.setLanes(newMap.getLanes());
        graphicRenderer.setLanes(newMap.getLanes());

        // Rebuild sidebar
        BorderPane root = (BorderPane) canvas.getScene().getRoot();
        spawnContainer = buildSidebar(newMap);
        root.setRight(spawnContainer);

        appendSpawnLog("[✓] Map: " + newMap.getName() + "\n");
    }

    private static void registerMap(TrafficEngine engine, MapConfig map) {
        RoadNetwork network = new RoadNetwork();
        for (Intersection intersection : map.getIntersections()) {
            network.addIntersection(intersection);
        }
        network.registerTo(engine);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static VehicleSpawner.SpawnPosition toSpawnPosition(int idx) {
        return switch (idx) {
            case 1 -> VehicleSpawner.SpawnPosition.MIDDLE;
            case 2 -> VehicleSpawner.SpawnPosition.END;
            default -> VehicleSpawner.SpawnPosition.START;
        };
    }

    private static VehicleSpawner.SpawnLateralMode toSpawnLateralMode(int idx) {
        return switch (idx) {
            case 1 -> VehicleSpawner.SpawnLateralMode.LEFT;
            case 2 -> VehicleSpawner.SpawnLateralMode.CENTER;
            case 3 -> VehicleSpawner.SpawnLateralMode.RIGHT;
            default -> VehicleSpawner.SpawnLateralMode.AUTO;
        };
    }

    private static List<Lane> getSpawnableLanes(MapConfig map) {
        List<Lane> result = new ArrayList<>();
        for (Lane lane : map.getLanes()) {
            if (lane != null && lane.isUsableForSpawn()) {
                result.add(lane);
            }
        }
        return result;
    }

    private static String[] getSpawnLaneNames(MapConfig map, List<Lane> spawnableLanes) {
        String[] names = map.getLaneNames();
        if (names.length == spawnableLanes.size()) {
            return names;
        }

        String[] fallback = new String[spawnableLanes.size()];
        for (int i = 0; i < fallback.length; i++) {
            fallback[i] = "lane " + (i + 1);
        }
        return fallback;
    }

    private void updateAutoSpawn(double deltaTime) {
        if (!autoSpawnEnabled || vehicleSpawner == null || currentMap == null) {
            autoSpawnTimerSeconds = 0.0;
            return;
        }

        SpawnProfile profile = getSpawnProfile(currentMap);
        if (engine.getVehicles().size() >= profile.maxTotalVehicles()) {
            return;
        }

        autoSpawnTimerSeconds += Math.max(0.0, deltaTime);
        if (autoSpawnTimerSeconds < autoSpawnIntervalSeconds) {
            return;
        }

        List<Lane> lanes = getSpawnableLanes(currentMap);
        if (lanes.isEmpty()) {
            autoSpawnTimerSeconds = 0.0;
            return;
        }

        int bursts = 0;
        while (autoSpawnTimerSeconds >= autoSpawnIntervalSeconds
                && bursts < MAX_AUTO_SPAWN_BURSTS_PER_FRAME
                && engine.getVehicles().size() < profile.maxTotalVehicles()) {
            autoSpawnTimerSeconds -= autoSpawnIntervalSeconds;
            bursts++;
            if (tryAutoSpawnOnce(profile, lanes)) {
                // Spawned one batch for this simulation interval.
            } else {
                // Lanes are temporarily full. Keep at most half an interval so
                // a long lag frame does not spam attempts as soon as one slot opens.
                autoSpawnTimerSeconds = Math.min(autoSpawnTimerSeconds, autoSpawnIntervalSeconds * 0.5);
                break;
            }
        }

        if (bursts >= MAX_AUTO_SPAWN_BURSTS_PER_FRAME) {
            autoSpawnTimerSeconds = Math.min(autoSpawnTimerSeconds, autoSpawnIntervalSeconds);
        }
    }

    private boolean tryAutoSpawnOnce(SpawnProfile profile, List<Lane> lanes) {
        if (profile == null || lanes == null || lanes.isEmpty()) return false;

        String type = randomAutoType(profile);
        int attempts = Math.min(lanes.size(), 8);
        int start = random.nextInt(lanes.size());
        for (int i = 0; i < attempts; i++) {
            Lane lane = lanes.get((start + i) % lanes.size());
            if (countVehiclesOnLane(lane) >= profile.maxVehiclesPerLane()) {
                continue;
            }
            int spawned = vehicleSpawner.spawn(
                    type,
                    lane,
                    VehicleSpawner.SpawnPosition.START,
                    VehicleSpawner.SpawnLateralMode.AUTO,
                    1
            );
            if (spawned > 0) {
                appendSpawnLog(String.format("[A] 1x %s → lane %d\n",
                        type, lanes.indexOf(lane) + 1));
                return true;
            }
        }
        return false;
    }

    private record SpawnProfile(
            int maxTotalVehicles,
            int maxVehiclesPerLane,
            int emergencyRatePercent
    ) {}

    private SpawnProfile getSpawnProfile(MapConfig map) {
        if (map instanceof CrossroadsMap) return new SpawnProfile(20, 5, 7);
        if (map instanceof TJunctionMap) return new SpawnProfile(14, 4, 6);
        if (map instanceof FiveWayMap) return new SpawnProfile(20, 4, 5);
        if (map instanceof NetworkMap) return new SpawnProfile(28, 5, 7);
        if (map instanceof HighwayMap) return new SpawnProfile(54, 14, 5);
        return new SpawnProfile(22, 5, 6);
    }

    private int countVehiclesOnLane(Lane lane) {
        int count = 0;
        for (Vehicle vehicle : engine.getVehicles()) {
            if (vehicle.getLane() == lane) {
                count++;
            }
        }
        return count;
    }

    private String randomAutoType(SpawnProfile profile) {
        // Uu tien xe thuong de quan sat dong giao thong; xe uu tien xuat hien it hon.
        int r = random.nextInt(100);
        int emergencyRate = profile != null ? profile.emergencyRatePercent() : 8;
        if (r >= 100 - emergencyRate) {
            return random.nextBoolean() ? "ambulance" : "firetruck";
        }
        if (r < 36) return "car";
        if (r < 64) return "motorcycle";
        return "bicycle";
    }

    private void appendSpawnLog(String text) {
        if (spawnLog == null || text == null) {
            return;
        }
        spawnLog.appendText(text);
        int maxChars = 5000;
        int overflow = spawnLog.getLength() - maxChars;
        if (overflow > 0) {
            spawnLog.deleteText(0, overflow);
        }
    }

    private Label makeLabel(String text) {
        Label lbl = new Label(text);
        lbl.setFont(Font.font("SansSerif", 12));
        lbl.setTextFill(Color.rgb(170, 185, 210));
        return lbl;
    }
}