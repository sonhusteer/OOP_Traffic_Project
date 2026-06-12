package com.traffic.ui;

import com.traffic.maps.MapConfig;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Thanh công cụ phía dưới — chọn map, tốc độ, pause, mode, mute.
 */
public class ToolbarPanel {

    private final SimulationController controller;
    private final MapConfig[] allMaps;
    private final Label lblVehicles;
    private final Label lblSpeed;

    private Runnable onMapLoaded; // Callback khi load map mới

    public ToolbarPanel(SimulationController controller, MapConfig[] allMaps) {
        this.controller = controller;
        this.allMaps = allMaps;
        this.lblVehicles = new Label("🚗 0");
        this.lblSpeed = new Label("1.0×");

        lblVehicles.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
        lblVehicles.setTextFill(Color.rgb(180, 220, 180));
        lblSpeed.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
        lblSpeed.setTextFill(Color.rgb(180, 200, 255));
        lblSpeed.setPrefWidth(50);
    }

    public HBox build() {
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
        for (MapConfig m : allMaps) cmbMap.getItems().add(m.getName());
        cmbMap.getSelectionModel().selectFirst();
        cmbMap.getStyleClass().add("dark-combo");

        Button btnLoad = new Button("Load");
        btnLoad.getStyleClass().add("btn-action");
        btnLoad.setOnAction(e -> {
            int idx = cmbMap.getSelectionModel().getSelectedIndex();
            if (idx < 0) return;
            controller.loadMap(allMaps[idx]);
            if (onMapLoaded != null) onMapLoaded.run();
        });

        // Speed slider
        Slider speedSlider = new Slider(0.1, 3.0, 1.0);
        speedSlider.setPrefWidth(160);
        speedSlider.setShowTickLabels(true);
        speedSlider.setMajorTickUnit(1.0);
        speedSlider.valueProperty().addListener((obs, oldV, newV) -> {
            controller.setSimSpeed(newV.doubleValue());
            lblSpeed.setText(String.format("%.1f×", newV.doubleValue()));
        });

        // Buttons
        Button btnPause = new Button("⏸ Pause");
        btnPause.getStyleClass().add("btn-action");
        btnPause.setOnAction(e -> {
            controller.togglePause();
            btnPause.setText(controller.isPaused() ? "▶ Resume" : "⏸ Pause");
        });

        Button btnMode = new Button("🎨 Graphic");
        btnMode.getStyleClass().add("btn-action");
        btnMode.setOnAction(e -> {
            String newLabel = controller.toggleMode();
            btnMode.setText(newLabel);
        });

        Button btnMute = new Button("🔊 Mute");
        btnMute.getStyleClass().add("btn-action");
        btnMute.setOnAction(e -> {
            SoundManager sm = SoundManager.getInstance();
            sm.setMuted(!sm.isMuted());
            btnMute.setText(sm.isMuted() ? "🔇 Unmute" : "🔊 Mute");
        });

        // Spacers
        Region sp1 = new Region(); HBox.setHgrow(sp1, Priority.SOMETIMES);
        Region sp2 = new Region(); sp2.setPrefWidth(8);
        Region sp3 = new Region(); sp3.setPrefWidth(8);

        Label lblBolt = new Label("⚡");
        lblBolt.setFont(Font.font(14));

        toolbar.getChildren().addAll(
            lblMap, cmbMap, btnLoad,
            sp2,
            lblBolt, speedSlider, lblSpeed,
            sp3,
            btnPause, btnMode, btnMute,
            sp1,
            lblVehicles
        );
        return toolbar;
    }

    /** Cập nhật số xe hiển thị */
    public void updateVehicleCount(int count) {
        lblVehicles.setText("🚗 " + count);
    }

    /** Callback khi load map mới (để MainApp rebuild sidebar) */
    public void setOnMapLoaded(Runnable r) { this.onMapLoaded = r; }

    /** Lấy index map đang chọn */
    public MapConfig[] getAllMaps() { return allMaps; }
}
