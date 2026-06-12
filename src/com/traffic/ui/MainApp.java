package com.traffic.ui;

import com.traffic.core.TrafficEngine;
import com.traffic.core.Vehicle;
import com.traffic.core.VehicleFactory;
import com.traffic.map.Lane;
import com.traffic.maps.CrossroadsMap;
import com.traffic.maps.HighwayMap;
import com.traffic.maps.MapConfig;
import com.traffic.maps.NetworkMap;
import com.traffic.maps.TJunctionMap;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

/**
 * Entry-point JavaFX.
 *
 * Sửa quan trọng:
 *   - Mỗi lần load map tạo object map mới, không dùng lại map cũ đã bị đổi state.
 *   - Game loop dùng deltaTime thật từ AnimationTimer thay vì cố định 0.03.
 *   - Spawn offset theo progress dọc lane nên đúng cho lane ngang/dọc/chéo.
 */
public class MainApp extends Application {

    private static final String[] MAP_NAMES = {
        "Ngã Tư",
        "Ngã Ba",
        "Mạng lưới giao 2 ngã 4",
        "Đại lộ cao tốc"
    };

    /**
     * Tạo map mới hoàn toàn để tránh state cũ: xe trong lane, trạng thái đèn,
     * manual mode... bị giữ lại sau khi load lại map.
     */
    private static MapConfig createMap(int index) {
        return switch (index) {
            case 1 -> new TJunctionMap();
            case 2 -> new NetworkMap();
            case 3 -> new HighwayMap();
            default -> new CrossroadsMap();
        };
    }

    private MapConfig currentMap;
    private TrafficEngine engine;
    private AbstractBaseRenderer basicRenderer;
    private AbstractBaseRenderer graphicRenderer;
    private AbstractBaseRenderer activeRenderer;
    private boolean isBasicMode = true;
    private boolean paused = false;
    private double simSpeed = 1.0;

    private Canvas canvas;
    private Label lblVehicles;
    private Label lblSpeed;
    private VBox spawnContainer;
    private TextArea spawnLog;

    private String selectedSpawnType = "car";
    private boolean isSimulationMode = false;
    private boolean isAutoSpawn = false;
    private BorderPane mainRoot;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        currentMap = createMap(0);
        basicRenderer = new BasicRenderer(currentMap.getLanes());
        graphicRenderer = new JavaFXRenderer(currentMap.getLanes());
        activeRenderer = basicRenderer;

        engine = new TrafficEngine(activeRenderer);
        MapLoader.registerMap(engine, currentMap);

        BorderPane root = new BorderPane();
        this.mainRoot = root;
        root.setStyle("-fx-background-color: #1a1a2e;");

        canvas = new Canvas(800, 600);
        StackPane canvasHolder = new StackPane(canvas);
        canvasHolder.setStyle("-fx-background-color: #1a1a2e;");
        root.setCenter(canvasHolder);

        canvas.setOnMouseMoved(e -> {
            Lane closest = null;
            double minDist = 30.0;
            for (Lane lane : currentMap.getLanes()) {
                java.util.List<com.traffic.core.Vector2D> pts = lane.getwaypoints();
                for (int i = 0; i < pts.size() - 1; i++) {
                    double ax = pts.get(i).getX(), ay = pts.get(i).getY();
                    double bx = pts.get(i+1).getX(), by = pts.get(i+1).getY();
                    double px = e.getX(), py = e.getY();
                    double dx = bx - ax, dy = by - ay;
                    double t = 0;
                    if (dx != 0 || dy != 0) {
                        t = Math.max(0, Math.min(1, ((px-ax)*dx + (py-ay)*dy) / (dx*dx + dy*dy)));
                    }
                    double d = Math.hypot(px - (ax + t*dx), py - (ay + t*dy));
                    if (d < minDist) {
                        minDist = d;
                        closest = lane;
                    }
                }
            }
            basicRenderer.setHoveredLane(closest);
            graphicRenderer.setHoveredLane(closest);
        });

        canvas.setOnMouseClicked(e -> {
            boolean left = e.getButton() == MouseButton.PRIMARY;
            activeRenderer.handleClick(e.getX(), e.getY(), left);
            
            Lane hovered = activeRenderer.getHoveredLane();
            if (hovered != null && left && !isSimulationMode) {
                Vehicle v = VehicleFactory.create(selectedSpawnType, 0, 0);
                SpawnPlanner.place(v, hovered, 1, 0);
                engine.addVehicle(v);
                if (spawnLog != null) spawnLog.appendText("[+] " + selectedSpawnType + " (click)\n");
            }
        });

        spawnContainer = buildSidebar(currentMap);
        root.setRight(spawnContainer);

        HBox toolbar = buildToolbar();
        root.setBottom(toolbar);

        Scene scene = new Scene(root, 1080, 680);
        try {
            String css = getClass().getResource("/style.css") != null
                       ? getClass().getResource("/style.css").toExternalForm()
                       : null;
            if (css != null) scene.getStylesheets().add(css);
        } catch (Exception ignored) {
            // CSS chỉ là phần trang trí, thiếu CSS thì app vẫn chạy được.
        }

        stage.setTitle("Traffic Simulation");
        stage.setScene(scene);
        stage.show();

        startGameLoop();
    }

    private void startGameLoop() {
        AnimationTimer timer = new AnimationTimer() {
            private long lastNow = -1L;
            private double spawnTimer = 0.0;

            @Override
            public void handle(long now) {
                if (lastNow < 0) {
                    lastNow = now;
                }

                double realDt = (now - lastNow) / 1_000_000_000.0;
                lastNow = now;

                if (!paused) {
                    double dt = Math.min(realDt, 0.1) * simSpeed;
                    engine.tick(dt);
                    engine.render();
                    updateSiren();
                    
                    if (isAutoSpawn) {
                        spawnTimer += dt;
                        if (spawnTimer >= 1.5) { // Spawn every 1.5 real seconds
                            spawnTimer = 0.0;
                            java.util.List<Lane> spawnLanes = currentMap.getSpawnLanes();
                            if (!spawnLanes.isEmpty()) {
                                Lane randomLane = spawnLanes.get(new java.util.Random().nextInt(spawnLanes.size()));
                                String[] types = {"car", "car", "motorcycle", "car", "bicycle", "ambulance"};
                                String randType = types[new java.util.Random().nextInt(types.length)];
                                Vehicle v = VehicleFactory.create(randType, 0, 0);
                                SpawnPlanner.place(v, randomLane, 0, 0);
                                engine.addVehicle(v);
                                if (spawnLog != null) spawnLog.appendText("[Auto] " + randType + " spawned\n");
                            }
                        }
                    }
                } else {
                    SoundManager.getInstance().stop("siren.wav");
                }

                drawCurrentFrame();
                lblVehicles.setText("🚗 " + engine.getVehicles().size());
            }
        };
        timer.start();
    }

    private void drawCurrentFrame() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        activeRenderer.draw(gc, canvas.getWidth(), canvas.getHeight());
    }

    private void updateSiren() {
        boolean hasEmergency = false;
        for (Vehicle v : engine.getVehicles()) {
            if (v.isPriority() && v.getSpeed() > 0) {
                hasEmergency = true;
                break;
            }
        }

        SoundManager sound = SoundManager.getInstance();
        if (hasEmergency) {
            sound.loop("siren.wav");
        } else {
            sound.stop("siren.wav");
        }
    }

    private VBox buildSidebar(MapConfig map) {
        spawnLog = new TextArea();
        VBox sidebar = new VBox(12);
        sidebar.setPadding(new Insets(16, 16, 16, 16));
        sidebar.setPrefWidth(260);
        sidebar.setStyle("-fx-background-color: linear-gradient(to bottom, #1e1e30, #16162a);-fx-border-color: #333355; -fx-border-width: 0 0 0 1;");

        Label title = new Label("🚘 Spawn Vehicle");
        title.setFont(Font.font("SansSerif", FontWeight.BOLD, 15));
        title.setTextFill(Color.rgb(180, 200, 255));

        Label lblType = makeLabel("Loại xe:");
        ComboBox<String> cmbType = new ComboBox<>();
        cmbType.getItems().addAll("Car", "Motorcycle", "Bicycle", "Ambulance", "Firetruck");
        cmbType.getSelectionModel().selectFirst();
        cmbType.setMaxWidth(Double.MAX_VALUE);
        cmbType.getStyleClass().add("dark-combo");

        Label lblLane = makeLabel("Làn đường:");
        ComboBox<String> cmbLane = new ComboBox<>();
        cmbLane.getItems().addAll(map.getLaneNames());
        cmbLane.getSelectionModel().selectFirst();
        cmbLane.setMaxWidth(Double.MAX_VALUE);
        cmbLane.getStyleClass().add("dark-combo");

        Label lblDir = makeLabel("Hướng di chuyển:");
        ComboBox<String> cmbDir = new ComboBox<>();
        cmbDir.getItems().addAll("Đi thẳng", "Rẽ trái", "Rẽ phải");
        cmbDir.getSelectionModel().selectFirst();
        cmbDir.setMaxWidth(Double.MAX_VALUE);
        cmbDir.getStyleClass().add("dark-combo");

        Label lblOffset = makeLabel("Vị trí:");
        ComboBox<String> cmbOffset = new ComboBox<>();
        cmbOffset.getItems().addAll("Đầu làn", "Giữa làn", "Cuối làn");
        cmbOffset.getSelectionModel().selectFirst();
        cmbOffset.setMaxWidth(Double.MAX_VALUE);
        cmbOffset.getStyleClass().add("dark-combo");

        Label lblCount = makeLabel("Số lượng:");
        Spinner<Integer> spinner = new Spinner<>(1, 10, 1);
        spinner.setMaxWidth(Double.MAX_VALUE);

        Label lblSpeedTitle = makeLabel("Tốc độ xe: 1.0x");
        Slider speedSlider = new Slider(0.1, 3.0, 1.0);
        speedSlider.valueProperty().addListener((obs, oldV, newV) -> {
            simSpeed = newV.doubleValue();
            lblSpeedTitle.setText(String.format("Tốc độ xe: %.1fx", simSpeed));
        });

        Button btnSpawn = new Button("✦ Spawn");
        btnSpawn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5;");
        btnSpawn.setMaxWidth(Double.MAX_VALUE);
        Lane[] laneArray = map.getSpawnLanes().toArray(new Lane[0]);
        String[] typesRaw = {"car", "motorcycle", "bicycle", "ambulance", "firetruck"};
        
        btnSpawn.setOnAction(e -> {
            int laneIdx = cmbLane.getSelectionModel().getSelectedIndex();
            if (laneIdx < 0 || laneIdx >= laneArray.length) return;
            Lane lane = laneArray[laneIdx];
            int offIdx = cmbOffset.getSelectionModel().getSelectedIndex();
            int count = spinner.getValue();
            String t = typesRaw[cmbType.getSelectionModel().getSelectedIndex()];
            for(int c=0; c<count; c++) {
                Vehicle v = VehicleFactory.create(t, 0, 0);
                SpawnPlanner.place(v, lane, offIdx, c);
                engine.addVehicle(v);
            }
            spawnLog.appendText(String.format("[+] %dx %s -> %s\n", count, t, map.getLaneNames()[laneIdx]));
        });

        ToggleButton btnAuto = new ToggleButton("Tự động: Tắt");
        btnAuto.setStyle("-fx-background-color: #555; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5;");
        btnAuto.setMaxWidth(Double.MAX_VALUE);
        btnAuto.setOnAction(e -> {
            boolean isOn = btnAuto.isSelected();
            btnAuto.setText(isOn ? "Tự động: Bật" : "Tự động: Tắt");
            btnAuto.setStyle(isOn 
                ? "-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5;"
                : "-fx-background-color: #555; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5;"
            );
            isAutoSpawn = isOn;
        });

        Button btnClear = new Button("✕ Clear All");
        btnClear.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5;");
        btnClear.setMaxWidth(Double.MAX_VALUE);
        btnClear.setOnAction(e -> {
            engine.clearVehicles();
            spawnLog.appendText("[!] Đã xóa tất cả xe\n");
        });

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #444466;");
        Label lblLog = makeLabel("📋 Lịch sử:");
        spawnLog.setEditable(false);
        spawnLog.setPrefRowCount(8);
        spawnLog.setWrapText(true);
        spawnLog.setStyle("-fx-control-inner-background: #111120;-fx-text-fill: #88cc88;-fx-font-family: 'Consolas';-fx-font-size: 11px;");

        sidebar.getChildren().addAll(
            title, 
            lblType, cmbType, 
            lblLane, cmbLane, 
            lblDir, cmbDir,
            lblOffset, cmbOffset, 
            lblCount, spinner,
            lblSpeedTitle, speedSlider,
            btnSpawn, btnAuto, btnClear
        );

        // --- TRAFFIC LIGHT CONFIG ---
        Separator sepLight = new Separator();
        sepLight.setStyle("-fx-background-color: #444466;");
        
        Label lblLightTitle = new Label("🚦 Cấu hình Đèn (s)");
        lblLightTitle.setFont(Font.font("SansSerif", FontWeight.BOLD, 15));
        lblLightTitle.setTextFill(Color.rgb(180, 200, 255));

        Label lblGreenTime = makeLabel("Đèn Xanh: 10s");
        Slider greenSlider = new Slider(2, 30, 10);
        greenSlider.valueProperty().addListener((obs, oldV, newV) -> {
            int val = newV.intValue();
            lblGreenTime.setText("Đèn Xanh: " + val + "s");
            if (currentMap != null) {
                for (Lane lane : currentMap.getLanes()) {
                    for (com.traffic.map.TrafficLight light : lane.getAllTrafficLights()) {
                        light.setGreenTime(val);
                    }
                }
            }
        });

        Label lblRedTime = makeLabel("Đèn Đỏ: 10s");
        Slider redSlider = new Slider(2, 30, 10);
        redSlider.valueProperty().addListener((obs, oldV, newV) -> {
            int val = newV.intValue();
            lblRedTime.setText("Đèn Đỏ: " + val + "s");
            if (currentMap != null) {
                for (Lane lane : currentMap.getLanes()) {
                    for (com.traffic.map.TrafficLight light : lane.getAllTrafficLights()) {
                        light.setRedTime(val);
                    }
                }
            }
        });

        sidebar.getChildren().addAll(
            sepLight, lblLightTitle,
            lblGreenTime, greenSlider,
            lblRedTime, redSlider
        );
        // -----------------------------

        sep = new Separator();
        sep.setStyle("-fx-background-color: #444466;");
        lblLog = makeLabel("📋 Lịch sử:");
        spawnLog.setEditable(false);
        spawnLog.setPrefRowCount(8);
        spawnLog.setWrapText(true);
        spawnLog.setStyle("-fx-control-inner-background: #111120;-fx-text-fill: #88cc88;-fx-font-family: 'Consolas';-fx-font-size: 11px;");

        sidebar.getChildren().addAll(
            sep, lblLog, spawnLog
        );
        VBox.setVgrow(spawnLog, Priority.ALWAYS);
        return sidebar;
    }

    private HBox buildToolbar() {
        HBox toolbar = new HBox(12);
        toolbar.setPadding(new Insets(8, 16, 8, 16));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle("-fx-background-color: linear-gradient(to right, #1a1a2e, #16213e);-fx-border-color: #333355; -fx-border-width: 1 0 0 0;");

        Label lblMap = new Label("🗺");
        lblMap.setFont(Font.font(16));
        ComboBox<String> cmbMap = new ComboBox<>();
        cmbMap.getItems().addAll(MAP_NAMES);
        cmbMap.getSelectionModel().selectFirst();
        cmbMap.getStyleClass().add("dark-combo");

        Button btnLoad = new Button("Load");
        btnLoad.getStyleClass().add("btn-action");
        btnLoad.setOnAction(e -> {
            int idx = cmbMap.getSelectionModel().getSelectedIndex();
            if (idx < 0) return;
            loadMap(createMap(idx));
        });

        Button btnReset = new Button("↺ Reset");
        btnReset.getStyleClass().add("btn-clear");
        btnReset.setOnAction(e -> {
            engine.clearVehicles();
            int idx = cmbMap.getSelectionModel().getSelectedIndex();
            if (idx >= 0) loadMap(createMap(idx));
        });

        Button btnPause = new Button("⏸ Pause");
        btnPause.getStyleClass().add("btn-action");
        btnPause.setOnAction(e -> {
            paused = !paused;
            btnPause.setText(paused ? "▶ Resume" : "⏸ Pause");
            if (paused) SoundManager.getInstance().stop("siren.wav");
        });

        Button btnStep = new Button("⏭ Step");
        btnStep.getStyleClass().add("btn-action");
        btnStep.setOnAction(e -> {
            if (paused) {
                engine.tick(0.03 * simSpeed);
                engine.render();
                drawCurrentFrame();
            }
        });

        Button btnMode = new Button("🎨 Graphic");
        btnMode.getStyleClass().add("btn-action");
        btnMode.setOnAction(e -> {
            isBasicMode = !isBasicMode;
            if (!isBasicMode) {
                activeRenderer = graphicRenderer;
                engine.setRenderer(graphicRenderer);
                btnMode.setText("📐 Basic");
            } else {
                activeRenderer = basicRenderer;
                engine.setRenderer(basicRenderer);
                btnMode.setText("🎨 Graphic");
            }
            drawCurrentFrame();
        });

        ToggleButton btnHeatmap = new ToggleButton("🔥 Heatmap");
        btnHeatmap.getStyleClass().add("btn-action");
        btnHeatmap.setOnAction(e -> {
            boolean show = btnHeatmap.isSelected();
            basicRenderer.setShowHeatmap(show);
            graphicRenderer.setShowHeatmap(show);
            drawCurrentFrame();
        });

        ToggleButton btnSimMode = new ToggleButton("💻 Fullscreen");
        btnSimMode.getStyleClass().add("btn-action");
        btnSimMode.setOnAction(e -> {
            isSimulationMode = btnSimMode.isSelected();
            if (isSimulationMode) {
                mainRoot.setRight(null);
            } else {
                mainRoot.setRight(spawnContainer);
            }
        });

        lblVehicles = new Label("🚗 0");
        lblVehicles.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
        lblVehicles.setTextFill(Color.rgb(180, 220, 180));

        Region sp1 = new Region(); HBox.setHgrow(sp1, Priority.SOMETIMES);
        
        toolbar.getChildren().addAll(
            lblMap, cmbMap, btnLoad, btnReset,
            btnPause, btnStep, btnMode, btnHeatmap, btnSimMode,
            sp1, lblVehicles
        );
        return toolbar;
    }

    private void loadMap(MapConfig newMap) {
        currentMap = newMap;
        MapLoader.loadMap(engine, newMap);
        basicRenderer.setLanes(newMap.getLanes());
        graphicRenderer.setLanes(newMap.getLanes());

        BorderPane root = (BorderPane) canvas.getScene().getRoot();
        spawnContainer = buildSidebar(newMap);
        root.setRight(spawnContainer);

        spawnLog.appendText("[✓] Map: " + newMap.getName() + "\n");
    }

    private Label makeLabel(String text) {
        Label lbl = new Label(text);
        lbl.setFont(Font.font("SansSerif", 12));
        lbl.setTextFill(Color.rgb(170, 185, 210));
        return lbl;
    }
}
