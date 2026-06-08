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
        
        panel.getChildren().addAll(secMap, secCtrl, secZoom, secLightMode, secDisplay);
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