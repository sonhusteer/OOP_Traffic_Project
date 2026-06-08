package com.traffic.view;

import com.traffic.engine.SimulationEngine;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class MainView {
    private BorderPane root;
    private SimulationEngine engine;
    private double lastMouseX, lastMouseY;

    public MainView() {
        root = new BorderPane();
        engine = new SimulationEngine(new javafx.scene.canvas.Canvas(com.traffic.config.Constants.WINDOW_WIDTH - 300, com.traffic.config.Constants.WINDOW_HEIGHT));
        
        // Cắm Canvas vào giữa
        root.setCenter(engine.getCanvas());
        
        // Tạo Bảng điều khiển siêu cấp bên phải
        root.setRight(createControlPanel());
        
        setupCameraControls();
    }

    public BorderPane getRoot() { return root; }
    public void startSimulation() { engine.start(); }

    private ScrollPane createControlPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(15));
        panel.setStyle("-fx-background-color: #1e272e;"); // Nền Dark Mode
        panel.setPrefWidth(320);

        // Khối 1: CẢNH MÔ PHỎNG
        VBox secMap = createSection("🗺 CẢNH MÔ PHỎNG");
        ToggleGroup mapGroup = new ToggleGroup();
        RadioButton rbGrid  = createRadio("Ô Cờ (Grid)", mapGroup);
        RadioButton rbNga4 = createRadio("Ngã Tư", mapGroup);
        RadioButton rbCloverleaf = createRadio("Cloverleaf", mapGroup);
        RadioButton rbBachKhoa   = createRadio("Bách Khoa", mapGroup);
        RadioButton rbNga3       = createRadio("Ngã Ba", mapGroup);
        rbGrid.setSelected(true);
        mapGroup.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) engine.changeMap(((RadioButton)newV).getText());
        });
        HBox mapRow1 = new HBox(20, rbGrid, rbNga4);
        HBox mapRow2 = new HBox(20, rbCloverleaf, rbBachKhoa);
        HBox mapRow3 = new HBox(20, rbNga3);
        secMap.getChildren().addAll(mapRow1, mapRow2, mapRow3);

        // Khối ĐIỀU KHIỂN
        VBox secCtrl = createSection("🎛 ĐIỀU KHIỂN");
        Button btnPause = new Button("⏸ Tạm dừng / Tiếp tục");
        btnPause.setMaxWidth(Double.MAX_VALUE);
        btnPause.setStyle("-fx-background-color: #ecf0f1; -fx-text-fill: #2c3e50; -fx-font-weight: bold;");
        btnPause.setOnAction(e -> engine.togglePause());

        ToggleGroup modeGroup = new ToggleGroup();
        RadioButton rbAuto   = createRadio("Tự động", modeGroup);
        RadioButton rbManual = createRadio("Thủ công (click đèn)", modeGroup);
        rbAuto.setSelected(true);
        modeGroup.selectedToggleProperty().addListener((obs, old, newVal) -> {
            com.traffic.config.Constants.AUTO_LIGHTS = rbAuto.isSelected();
        });

        secCtrl.getChildren().addAll(btnPause, new HBox(20, rbAuto, rbManual));

        // Khối 3: CAMERA ZOOM
        VBox secZoom = createSection("🔍 ZOOM");
        HBox zoomControls = new HBox(10);
        Button btnZoomOut = new Button("Q -");
        Button btnZoomIn = new Button("Q +");
        Button btnResetZoom = new Button("Reset");
        btnZoomOut.setOnAction(e -> engine.zoomCamera(0.8));
        btnZoomIn.setOnAction(e -> engine.zoomCamera(1.2));
        btnResetZoom.setOnAction(e -> engine.resetCamera()); 
        zoomControls.getChildren().addAll(btnZoomOut, btnZoomIn, btnResetZoom);
        secZoom.getChildren().add(zoomControls);


        // Khối 8: LOẠI ĐÈN
        VBox secLightMode = createSection("🚥 LOẠI ĐÈN");
        ComboBox<String> cbLight = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(
            "Không đếm số", "Đếm toàn thời gian", "Đếm khi <= 10s"
        ));
        cbLight.setValue("Đếm khi <= 10s");
        cbLight.setMaxWidth(Double.MAX_VALUE);
        cbLight.setStyle("-fx-font-size: 13px; -fx-cursor: hand;");
        cbLight.setOnAction(e -> engine.setTrafficLightMode(cbLight.getSelectionModel().getSelectedIndex()));
        secLightMode.getChildren().add(cbLight);

        // Khối 6: HIỂN THỊ
        VBox secDisplay = createSection("📺 HIỂN THỊ");
        ToggleGroup dispGroup = new ToggleGroup();
        RadioButton rbBasic = createRadio("Basic", dispGroup);
        RadioButton rbGraphic = createRadio("Đồ họa", dispGroup);
        rbGraphic.setSelected(true);
        dispGroup.selectedToggleProperty().addListener((obs, old, newVal) -> {
            com.traffic.config.Constants.BASIC_MODE = ((RadioButton)newVal).getText().equals("Basic");
        });
        HBox dispRow = new HBox(20, rbBasic, rbGraphic);
        secDisplay.getChildren().add(dispRow);

        // Khối 7: VEHICLES
        VBox secVehicle = createSection("🚗 SINH XE & ĐIỀU KHIỂN");
        
        // Dynamic vehicle count label
        Label lblCount = new Label("Số lượng xe: 0");
        lblCount.setTextFill(Color.web("#00f0ff"));
        lblCount.setFont(Font.font("System", FontWeight.BOLD, 13));
        engine.setVehicleCountLabel(lblCount);

        // Buttons for spawn
        Button btnSpawnCar = new Button("Ô tô");
        Button btnSpawnMoto = new Button("Xe máy");
        Button btnSpawnBike = new Button("Xe đạp");
        Button btnSpawnAmbulance = new Button("Cứu thương");
        Button btnSpawnFireTruck = new Button("Cứu hỏa");
        Button btnSpawnRandom = new Button("Sinh ngẫu nhiên");
        
        // Style buttons
        String btnStyle = "-fx-background-color: #34495e; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 4;";
        btnSpawnCar.setStyle(btnStyle);
        btnSpawnMoto.setStyle(btnStyle);
        btnSpawnBike.setStyle(btnStyle);
        btnSpawnAmbulance.setStyle("-fx-background-color: #ff003c; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 4;");
        btnSpawnFireTruck.setStyle("-fx-background-color: #ff3b00; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 4;");
        btnSpawnRandom.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 4;");
        
        btnSpawnCar.setMaxWidth(Double.MAX_VALUE);
        btnSpawnMoto.setMaxWidth(Double.MAX_VALUE);
        btnSpawnBike.setMaxWidth(Double.MAX_VALUE);
        btnSpawnAmbulance.setMaxWidth(Double.MAX_VALUE);
        btnSpawnFireTruck.setMaxWidth(Double.MAX_VALUE);
        btnSpawnRandom.setMaxWidth(Double.MAX_VALUE);

        // Actions
        btnSpawnCar.setOnAction(e -> engine.spawnVehicle("car"));
        btnSpawnMoto.setOnAction(e -> engine.spawnVehicle("motorcycle"));
        btnSpawnBike.setOnAction(e -> engine.spawnVehicle("bicycle"));
        btnSpawnAmbulance.setOnAction(e -> engine.spawnVehicle("ambulance"));
        btnSpawnFireTruck.setOnAction(e -> engine.spawnVehicle("firetruck"));
        btnSpawnRandom.setOnAction(e -> engine.spawnRandomVehicle());

        // Grid for individual vehicle spawn
        GridPane gridSpawn = new GridPane();
        gridSpawn.setHgap(8);
        gridSpawn.setVgap(8);
        gridSpawn.addColumn(0, btnSpawnCar, btnSpawnMoto, btnSpawnBike);
        gridSpawn.addColumn(1, btnSpawnAmbulance, btnSpawnFireTruck, btnSpawnRandom);
        
        // Make columns equal width
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);
        gridSpawn.getColumnConstraints().addAll(col1, col2);

        // Auto spawn and clear controls
        CheckBox chkAutoSpawn = createCheck("Tự động sinh xe", engine.isAutoSpawnEnabled());
        chkAutoSpawn.setOnAction(e -> engine.setAutoSpawnEnabled(chkAutoSpawn.isSelected()));

        // Tốc độ xe (Slider)
        VBox boxSpeed = new VBox(4);
        Label lblSpeed = new Label("Tốc độ xe: 100%");
        lblSpeed.setTextFill(Color.web("#bdc3c7"));
        Slider sliderSpeed = new Slider(0.2, 2.5, 1.0);
        sliderSpeed.setShowTickMarks(true);
        sliderSpeed.setShowTickLabels(true);
        sliderSpeed.setCursor(Cursor.HAND);
        sliderSpeed.valueProperty().addListener((obs, oldVal, newVal) -> {
            double val = newVal.doubleValue();
            com.traffic.config.Constants.VEHICLE_SPEED_MULTIPLIER = val;
            lblSpeed.setText(String.format("Tốc độ xe: %d%%", (int)(val * 100)));
        });
        boxSpeed.getChildren().addAll(lblSpeed, sliderSpeed);

        Button btnClear = new Button("🗑 Xóa tất cả xe");
        btnClear.setMaxWidth(Double.MAX_VALUE);
        btnClear.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 4;");
        btnClear.setOnAction(e -> engine.clearAllVehicles());

        secVehicle.getChildren().addAll(lblCount, gridSpawn, chkAutoSpawn, boxSpeed, btnClear);
        
        panel.getChildren().addAll(secMap, secCtrl, secZoom, secLightMode, secDisplay, secVehicle);
        ScrollPane scroll = new ScrollPane(panel);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #1e272e; -fx-border-color: #1e272e;");
        return scroll;
    }

    private VBox createSection(String titleStr) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-border-color: #34495e; -fx-border-width: 1; -fx-border-radius: 5;");
        Label title = new Label(titleStr);
        title.setFont(Font.font("System", FontWeight.BOLD, 14));
        title.setTextFill(Color.WHITE);
        box.getChildren().add(title);
        return box;
    }

    private RadioButton createRadio(String text, ToggleGroup group) {
        RadioButton rb = new RadioButton(text);
        rb.setToggleGroup(group);
        rb.setTextFill(Color.web("#bdc3c7"));
        return rb;
    }

    private CheckBox createCheck(String text, boolean selected) {
        CheckBox chk = new CheckBox(text);
        chk.setSelected(selected);
        chk.setTextFill(Color.web("#bdc3c7"));
        return chk;
    }

    private void setupCameraControls() {
        javafx.scene.canvas.Canvas canvas = engine.getCanvas();
        canvas.setCursor(Cursor.HAND);
        canvas.setOnScroll(event -> {
            if (event.getDeltaY() > 0) engine.zoomCamera(1.1);
            else engine.zoomCamera(0.9);
        });
        canvas.setOnMousePressed(event -> {
            lastMouseX = event.getX(); lastMouseY = event.getY();
            canvas.setCursor(Cursor.CLOSED_HAND);
        });
        canvas.setOnMouseDragged(event -> {
            engine.moveCamera((event.getX() - lastMouseX) / engine.getZoomScale(), (event.getY() - lastMouseY) / engine.getZoomScale());
            lastMouseX = event.getX(); lastMouseY = event.getY();
        });
        canvas.setOnMouseReleased(event -> canvas.setCursor(Cursor.HAND));
        canvas.setOnMouseClicked(event -> {
            boolean isRightClick = event.getButton() == javafx.scene.input.MouseButton.SECONDARY;
            engine.handleMouseClick(event.getX(), event.getY(), isRightClick);
        });
    }
}