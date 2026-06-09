package com.traffic.view;

import com.traffic.config.Constants;
import com.traffic.engine.SimulationEngine;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
        RadioButton rbNga3 = createRadio("Ngã Ba", mapGroup);
        RadioButton rbNga5    = createRadio("Ngã 5", mapGroup);
        RadioButton rbMixed   = createRadio("Hỗn Hợp", mapGroup);
        rbGrid.setSelected(true);
        mapGroup.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) engine.changeMap(((RadioButton)newV).getText());
        });
        HBox mapRow1 = new HBox(20, rbGrid, rbNga4, rbNga3);
        HBox mapRow2 = new HBox(20, rbNga5, rbMixed);
        secMap.getChildren().addAll(mapRow1, mapRow2);

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
        
        // Khối THỜI GIAN (Day/Night Toggle)
        VBox secTime = createSection("🌙 THỜI GIAN");

        // Hàng nhãn: 🌞 ──────── 🌙
        Label lblSun  = new Label("🌞");
        Label lblMoon = new Label("🌙");
        lblSun .setStyle("-fx-font-size: 18px;");
        lblMoon.setStyle("-fx-font-size: 18px;");

        // Toggle track
        StackPane track = new StackPane();
        track.setPrefSize(64, 28);
        track.setMaxSize(64, 28);
        track.setStyle("-fx-background-color: #2c3e50; -fx-background-radius: 14;");

        // Toggle thumb
        StackPane thumb = new StackPane();
        thumb.setPrefSize(24, 24);
        thumb.setMaxSize(24, 24);
        thumb.setStyle("-fx-background-color: linear-gradient(to bottom, #f9ca24, #f0932b); "
                     + "-fx-background-radius: 12; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 4, 0, 0, 1);");
        StackPane.setAlignment(thumb, javafx.geometry.Pos.CENTER_LEFT);
        thumb.setTranslateX(2);
        track.getChildren().add(thumb);

        // Nhãn trạng thái
        Label lblTimeStatus = new Label("Tự động (Chu kỳ)");
        lblTimeStatus.setTextFill(Color.web("#f9ca24"));
        lblTimeStatus.setFont(Font.font("System", FontWeight.BOLD, 11));

        // 3 nút chế độ
        Button btnAuto  = createTimeBtn("🔄 Tự động");
        Button btnDay   = createTimeBtn("☀ Ban Ngày");
        Button btnNight = createTimeBtn("🌙 Ban Đêm");

        // Highlight nút đang chọn
        Runnable refreshTimeUI = () -> {
            String base = "-fx-background-radius: 8; -fx-font-size: 11px; -fx-cursor: hand; "
                        + "-fx-padding: 5 8 5 8; -fx-border-radius: 8; -fx-border-width: 1;";
            btnAuto .setStyle(base + (Constants.TIME_MODE == 0
                    ? "-fx-background-color: #6c5ce7; -fx-text-fill: white; -fx-border-color: #a29bfe;"
                    : "-fx-background-color: #2d3748; -fx-text-fill: #bdc3c7; -fx-border-color: #4a5568;"));
            btnDay  .setStyle(base + (Constants.TIME_MODE == 1
                    ? "-fx-background-color: #e67e22; -fx-text-fill: white; -fx-border-color: #f39c12;"
                    : "-fx-background-color: #2d3748; -fx-text-fill: #bdc3c7; -fx-border-color: #4a5568;"));
            btnNight.setStyle(base + (Constants.TIME_MODE == 2
                    ? "-fx-background-color: #2980b9; -fx-text-fill: white; -fx-border-color: #00cec9;"
                    : "-fx-background-color: #2d3748; -fx-text-fill: #bdc3c7; -fx-border-color: #4a5568;"));

            // Cập nhật thumb & màu track
            if (Constants.TIME_MODE == 1) {
                thumb.setTranslateX(2);
                thumb.setStyle("-fx-background-color: linear-gradient(to bottom, #f9ca24, #f0932b); "
                             + "-fx-background-radius: 12; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 4, 0, 0, 1);");
                track.setStyle("-fx-background-color: #e67e22; -fx-background-radius: 14;");
                lblTimeStatus.setText("Ban Ngày");
                lblTimeStatus.setTextFill(Color.web("#f9ca24"));
            } else if (Constants.TIME_MODE == 2) {
                thumb.setTranslateX(38);
                thumb.setStyle("-fx-background-color: linear-gradient(to bottom, #74b9ff, #0984e3); "
                             + "-fx-background-radius: 12; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 4, 0, 0, 1);");
                track.setStyle("-fx-background-color: #2c3e50; -fx-background-radius: 14;");
                lblTimeStatus.setText("Ban Đêm");
                lblTimeStatus.setTextFill(Color.web("#74b9ff"));
            } else {
                thumb.setTranslateX(20);
                thumb.setStyle("-fx-background-color: linear-gradient(to bottom, #a29bfe, #6c5ce7); "
                             + "-fx-background-radius: 12; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 4, 0, 0, 1);");
                track.setStyle("-fx-background-color: #6c5ce7; -fx-background-radius: 14;");
                lblTimeStatus.setText("Tự động (Chu kỳ)");
                lblTimeStatus.setTextFill(Color.web("#a29bfe"));
            }
        };

        btnAuto.setOnAction(e  -> { Constants.TIME_MODE = 0; refreshTimeUI.run(); });
        btnDay.setOnAction(e   -> { Constants.TIME_MODE = 1; refreshTimeUI.run(); });
        btnNight.setOnAction(e -> { Constants.TIME_MODE = 2; refreshTimeUI.run(); });

        // Khởi tạo style ban đầu
        refreshTimeUI.run();

        // Layout hàng toggle: icon + track + icon
        HBox toggleRow = new HBox(8, lblSun, track, lblMoon);
        toggleRow.setAlignment(Pos.CENTER);

        HBox btnRow = new HBox(6, btnAuto, btnDay, btnNight);
        btnRow.setAlignment(Pos.CENTER);

        secTime.getChildren().addAll(toggleRow, lblTimeStatus, btnRow);

        panel.getChildren().addAll(secMap, secCtrl, secZoom, secLightMode, secDisplay, secTime);
        ScrollPane scroll = new ScrollPane(panel);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #1e272e; -fx-border-color: #1e272e;");
        return scroll;
    }

    private Button createTimeBtn(String text) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(btn, Priority.ALWAYS);
        return btn;
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