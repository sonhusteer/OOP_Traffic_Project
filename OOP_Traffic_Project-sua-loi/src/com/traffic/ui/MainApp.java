package com.traffic.ui;

import com.traffic.core.TrafficEngine;
import com.traffic.core.Vehicle;
import com.traffic.core.VehicleFactory;
import com.traffic.core.MathUtils;
import com.traffic.core.Vector2D;
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
import java.util.List;
import java.util.ArrayList;
import java.util.Random;

/**
 * Entry‑point JavaFX — thay thế hoàn toàn Swing.
 */
public class MainApp extends Application {

    private static final MapConfig[] ALL_MAPS = {
        new CrossroadsMap(),
        new TJunctionMap(),
        new NetworkMap(),
        new HighwayMap()
    };

    // ── State ────────────────────────────────────────────────────────────
    private MapConfig currentMap;
    private TrafficEngine engine;
    private AbstractBaseRenderer basicRenderer;
    private AbstractBaseRenderer graphicRenderer;
    private AbstractBaseRenderer activeRenderer;
    private boolean isBasicMode = true;
    private boolean paused = false;
    private double simSpeed = 1.0;
    private boolean autoSpawnEnabled = false;
    private double autoSpawnCooldown = 0.0;
    public static double vehicleSpeedMultiplier = 1.0;

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
            private long lastTime = 0;

            @Override
            public void handle(long now) {
                if (lastTime == 0) {
                    lastTime = now;
                    return;
                }
                double realDt = (now - lastTime) / 1_000_000_000.0;
                lastTime = now;

                if (!paused) {
                    // Limit dt to 0.05 seconds max to prevent physics instability during lag
                    double dt = Math.min(realDt, 0.05) * simSpeed;

                    if (autoSpawnEnabled) {
                        autoSpawnCooldown -= dt;
                        if (autoSpawnCooldown <= 0) {
                            triggerAutoSpawn();
                            autoSpawnCooldown = 1.5;
                        }
                    }

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
        String[] laneNames = map.getLaneNames();
        ComboBox<String> cmbLane = new ComboBox<>();
        cmbLane.getItems().addAll(laneNames);
        cmbLane.getSelectionModel().selectFirst();
        cmbLane.setMaxWidth(Double.MAX_VALUE);
        cmbLane.getStyleClass().add("dark-combo");

        // Hướng rẽ
        Label lblTurn = makeLabel("Hướng di chuyển:");
        ComboBox<String> cmbTurn = new ComboBox<>();
        cmbTurn.getItems().addAll("Đi thẳng", "Rẽ trái", "Rẽ phải");
        cmbTurn.getSelectionModel().selectFirst();
        cmbTurn.setMaxWidth(Double.MAX_VALUE);
        cmbTurn.getStyleClass().add("dark-combo");

        // Offset
        Label lblOffset = makeLabel("Vị trí:");
        ComboBox<String> cmbOffset = new ComboBox<>();
        cmbOffset.getItems().addAll("Đầu làn", "Giữa làn", "Cuối làn");
        cmbOffset.getSelectionModel().selectFirst();
        cmbOffset.setMaxWidth(Double.MAX_VALUE);
        cmbOffset.getStyleClass().add("dark-combo");

        // Count
        Label lblCount = makeLabel("Số lượng:");
        Spinner<Integer> spinner = new Spinner<>(1, 10, 1);
        spinner.setMaxWidth(Double.MAX_VALUE);

        // Vehicle speed scale slider
        Label lblSpeedScale = makeLabel("Tốc độ xe: 1.0×");
        Slider speedScaleSlider = new Slider(0.5, 2.0, 1.0);
        speedScaleSlider.setMaxWidth(Double.MAX_VALUE);
        speedScaleSlider.setShowTickLabels(true);
        speedScaleSlider.setMajorTickUnit(0.5);
        speedScaleSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            vehicleSpeedMultiplier = newVal.doubleValue();
            lblSpeedScale.setText(String.format("Tốc độ xe: %.1f×", vehicleSpeedMultiplier));
        });

        // Spawn button
        Button btnSpawn = new Button("✦ Spawn");
        btnSpawn.getStyleClass().add("btn-spawn");
        btnSpawn.setMaxWidth(Double.MAX_VALUE);
        List<Lane> spawnableLanes = getSpawnableLanes(map);
        btnSpawn.setOnAction(e -> {
            int laneIdx = cmbLane.getSelectionModel().getSelectedIndex();
            if (laneIdx < 0 || laneIdx >= spawnableLanes.size()) return;

            String type = types[cmbType.getSelectionModel().getSelectedIndex()];
            Lane lane = spawnableLanes.get(laneIdx);
            int count = spinner.getValue();
            int offIdx = cmbOffset.getSelectionModel().getSelectedIndex();

            int turnIdx = cmbTurn.getSelectionModel().getSelectedIndex();
            Vehicle.TurnDecision decision = Vehicle.TurnDecision.values()[turnIdx];


            // Spawn vehicles one by one — set real position first, then check safety
            boolean allSafe = true;
            List<Vehicle> spawned = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Vehicle v = VehicleFactory.create(type, 0, 0);
                v.setTurnDecision(decision);
                v.setLane(lane);          // sets real position + lateral offset
                applyOffset(v, lane, offIdx, i); // applies along-lane spacing

                // Now check against already-spawned vehicles AND existing vehicles
                boolean safe = true;
                for (Vehicle existing : engine.getVehicles()) {
                    if (v.isFourWheeler() != existing.isFourWheeler()) continue;
                    double minDist = (v.getWidth() + existing.getWidth()) / 2.0 + 15.0;
                    if (MathUtils.distance(v.getPosition(), existing.getPosition()) < minDist) {
                        safe = false; break;
                    }
                }
                if (safe) {
                    for (Vehicle prev : spawned) {
                        double minDist = (v.getWidth() + prev.getWidth()) / 2.0 + 15.0;
                        if (MathUtils.distance(v.getPosition(), prev.getPosition()) < minDist) {
                            safe = false; break;
                        }
                    }
                }
                if (!safe) { allSafe = false; lane.removeVehicle(v); break; }
                spawned.add(v);
            }

            if (!allSafe) {
                // Remove any partially-spawned vehicles from the lane list
                for (Vehicle v : spawned) lane.removeVehicle(v);
                spawnLog.appendText("[Error] Làn đường bị nghẽn ở vị trí spawn!\n");
                return;
            }

            for (Vehicle v : spawned) {
                engine.addVehicle(v);
            }

            spawnLog.appendText(String.format("[+] %dx %s (%s) → %s\n",
                count, type, cmbTurn.getSelectionModel().getSelectedItem(), laneNames[laneIdx]));
        });

        // Auto Spawn button
        ToggleButton btnAutoSpawn = new ToggleButton(autoSpawnEnabled ? "Tự động: Bật 🟢" : "Tự động: Tắt 🔴");
        btnAutoSpawn.setMaxWidth(Double.MAX_VALUE);
        btnAutoSpawn.getStyleClass().add("btn-action");
        if (autoSpawnEnabled) {
            btnAutoSpawn.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white;");
        }
        btnAutoSpawn.setOnAction(e -> {
            autoSpawnEnabled = btnAutoSpawn.isSelected();
            btnAutoSpawn.setText(autoSpawnEnabled ? "Tự động: Bật 🟢" : "Tự động: Tắt 🔴");
            if (autoSpawnEnabled) {
                btnAutoSpawn.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white;");
            } else {
                btnAutoSpawn.setStyle("");
            }
        });

        // Clear button
        Button btnClear = new Button("✕ Clear All");
        btnClear.getStyleClass().add("btn-clear");
        btnClear.setMaxWidth(Double.MAX_VALUE);
        btnClear.setOnAction(e -> {
            engine.clearVehicles();
            if (currentMap != null) {
                for (Lane lane : currentMap.getLanes()) {
                    lane.clear();
                }
            }
            spawnLog.appendText("[!] Đã xóa tất cả xe\n");
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
            lblTurn, cmbTurn,
            lblOffset, cmbOffset,
            lblCount, spinner,
            lblSpeedScale, speedScaleSlider,
            btnSpawn, btnAutoSpawn, btnClear,
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

        currentMap = newMap;
        registerMap(engine, newMap);
        basicRenderer.setLanes(newMap.getLanes());
        graphicRenderer.setLanes(newMap.getLanes());

        // Rebuild sidebar
        BorderPane root = (BorderPane) canvas.getScene().getRoot();
        spawnContainer = buildSidebar(newMap);
        root.setRight(spawnContainer);

        spawnLog.appendText("[✓] Map: " + newMap.getName() + "\n");
    }

    private static void registerMap(TrafficEngine engine, MapConfig map) {
        RoadNetwork network = new RoadNetwork();
        for (Intersection intersection : map.getIntersections()) {
            network.addIntersection(intersection);
        }
        network.registerTo(engine);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static void applyOffset(Vehicle v, Lane lane, int offIdx, int i) {
        int gap = 55;
        double offsetDist = 0;
        switch (offIdx) {
            case 0 -> offsetDist = i * gap;
            case 1 -> offsetDist = i * gap - 200;
            case 2 -> offsetDist = i * gap - 400;
        }

        double angle = MathUtils.angleTo(lane.getStart(), lane.getEnd());
        double rad = Math.toRadians(angle);
        double dx = Math.cos(rad) * offsetDist;
        double dy = Math.sin(rad) * offsetDist;

        v.setLaneStartOffset(dx, dy);
    }

    private Label makeLabel(String text) {
        Label lbl = new Label(text);
        lbl.setFont(Font.font("SansSerif", 12));
        lbl.setTextFill(Color.rgb(170, 185, 210));
        return lbl;
    }

    private void triggerAutoSpawn() {
        if (currentMap == null) return;

        if (engine.getVehicles().size() >= 4) return;

        List<Lane> spawnableLanes = getSpawnableLanes(currentMap);
        Random rand = new Random();

        List<Lane> validLanes = new ArrayList<>();
        for (Lane l : spawnableLanes) {
            if (l.getVehicles().size() < 2) {
                validLanes.add(l);
            }
        }
        if (validLanes.isEmpty()) return;

        Lane lane = validLanes.get(rand.nextInt(validLanes.size()));

        String[] types = {"car", "motorcycle", "bicycle", "ambulance", "firetruck"};
        double p = rand.nextDouble();
        String type;
        if (p < 0.60) type = "car";
        else if (p < 0.75) type = "motorcycle";
        else if (p < 0.90) type = "bicycle";
        else if (p < 0.95) type = "ambulance";
        else type = "firetruck";

        Vehicle.TurnDecision decision;
        double turnP = rand.nextDouble();
        if (turnP < 0.50) decision = Vehicle.TurnDecision.STRAIGHT;
        else if (turnP < 0.75) decision = Vehicle.TurnDecision.LEFT;
        else decision = Vehicle.TurnDecision.RIGHT;

        Vehicle v = VehicleFactory.create(type, 0, 0);
        v.setTurnDecision(decision);
        v.setLane(lane);   // sets real position + lateral offset

        // Check safety using the actual spawned position
        boolean safe = true;
        for (Vehicle existing : engine.getVehicles()) {
            if (v.isFourWheeler() != existing.isFourWheeler()) continue;
            double minDist = (v.getWidth() + existing.getWidth()) / 2.0 + 25.0;
            if (MathUtils.distance(v.getPosition(), existing.getPosition()) < minDist) {
                safe = false;
                break;
            }
        }
        if (!safe) {
            lane.removeVehicle(v); // clean up from lane list
            return;
        }

        engine.addVehicle(v);

        String typeStr = v.getTypeName();
        String turnStr = decision == Vehicle.TurnDecision.STRAIGHT ? "Thẳng" : (decision == Vehicle.TurnDecision.LEFT ? "Trái" : "Phải");
        spawnLog.appendText(String.format("[Auto] %s (%s) → Làn: %s\n", typeStr, turnStr, lane.getLight() != null ? "road" : "highway"));
    }

    private List<Lane> getSpawnableLanes(MapConfig map) {
        List<Lane> spawnable = new ArrayList<>();
        if (map == null) return spawnable;
        for (Lane lane : map.getLanes()) {
            double length = MathUtils.distance(lane.getStart(), lane.getEnd());
            if (length <= 50.0) {
                continue;
            }
            double startX = lane.getStart().getX();
            double startY = lane.getStart().getY();
            boolean nearEdge = (startX <= 100) || (startX >= 700) || (startY <= 100) || (startY >= 500);
            if (!nearEdge) {
                continue;
            }
            spawnable.add(lane);
        }
        return spawnable;
    }
}