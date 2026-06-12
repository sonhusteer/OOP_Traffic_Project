package com.traffic.ui;

import com.traffic.core.Vector2D;
import com.traffic.core.Vehicle;
import com.traffic.core.VehicleFactory;
import com.traffic.map.Lane;
import com.traffic.maps.MapConfig;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Panel bên phải — spawn xe, chọn loại/làn/vị trí, log lịch sử.
 */
public class SidebarPanel {

    private final SimulationController controller;
    private final TextArea spawnLog = new TextArea();

    public SidebarPanel(SimulationController controller) {
        this.controller = controller;
    }

    /** Dựng sidebar cho map cụ thể. */
    public VBox build(MapConfig map) {
        VBox sidebar = new VBox(10);
        sidebar.setPadding(new Insets(16, 12, 16, 12));
        sidebar.setPrefWidth(230);
        sidebar.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #1e1e30, #16162a);"
          + "-fx-border-color: #333355; -fx-border-width: 0 0 0 1;"
        );

        Label title = new Label("🚘 Spawn Vehicle");
        title.setFont(Font.font("SansSerif", FontWeight.BOLD, 15));
        title.setTextFill(Color.rgb(180, 200, 255));

        String[] types = {"car", "motorcycle", "bicycle", "ambulance", "firetruck"};
        String[] typeLabels = {"Car", "Motorcycle", "Bicycle", "Ambulance", "Firetruck"};
        ComboBox<String> cmbType = new ComboBox<>();
        cmbType.getItems().addAll(typeLabels);
        cmbType.getSelectionModel().selectFirst();
        cmbType.setMaxWidth(Double.MAX_VALUE);
        cmbType.getStyleClass().add("dark-combo");

        String[] laneNames = map.getLaneNames();
        ComboBox<String> cmbLane = new ComboBox<>();
        cmbLane.getItems().addAll(laneNames);
        cmbLane.getSelectionModel().selectFirst();
        cmbLane.setMaxWidth(Double.MAX_VALUE);
        cmbLane.getStyleClass().add("dark-combo");

        ComboBox<String> cmbOffset = new ComboBox<>();
        cmbOffset.getItems().addAll("Đầu làn", "Giữa làn", "Cuối làn");
        cmbOffset.getSelectionModel().selectFirst();
        cmbOffset.setMaxWidth(Double.MAX_VALUE);
        cmbOffset.getStyleClass().add("dark-combo");

        Spinner<Integer> spinner = new Spinner<>(1, 10, 1);
        spinner.setMaxWidth(Double.MAX_VALUE);

        Lane[] laneArray = map.getSpawnLanes().toArray(new Lane[0]);
        Button btnSpawn = new Button("✦ Spawn");
        btnSpawn.getStyleClass().add("btn-spawn");
        btnSpawn.setMaxWidth(Double.MAX_VALUE);
        btnSpawn.setOnAction(e -> {
            int laneIdx = cmbLane.getSelectionModel().getSelectedIndex();
            if (laneIdx < 0 || laneIdx >= laneArray.length) return;

            String type = types[cmbType.getSelectionModel().getSelectedIndex()];
            Lane lane = laneArray[laneIdx];
            int count = spinner.getValue();
            int offIdx = cmbOffset.getSelectionModel().getSelectedIndex();

            for (int i = 0; i < count; i++) {
                Vehicle v = VehicleFactory.create(type, 0, 0);
                v.setLane(lane);
                applyOffset(v, lane, offIdx, i);
                controller.getEngine().addVehicle(v);
            }

            spawnLog.appendText(String.format("[+] %dx %s → %s%n",
                count, type, laneNames[laneIdx]));
        });

        Button btnClear = new Button("✕ Clear All");
        btnClear.getStyleClass().add("btn-clear");
        btnClear.setMaxWidth(Double.MAX_VALUE);
        btnClear.setOnAction(e -> {
            controller.getEngine().clearVehicles();
            spawnLog.appendText("[!] Đã xóa tất cả xe\n");
        });

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #444466;");

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
            makeLabel("Loại xe:"), cmbType,
            makeLabel("Làn đường:"), cmbLane,
            makeLabel("Vị trí:"), cmbOffset,
            makeLabel("Số lượng:"), spinner,
            btnSpawn, btnClear,
            sep, lblLog, spawnLog
        );
        VBox.setVgrow(spawnLog, Priority.ALWAYS);
        return sidebar;
    }

    public TextArea getSpawnLog() { return spawnLog; }

    /** Offset theo progress dọc lane, đúng cho cả lane dọc và lane xiên. */
    private static void applyOffset(Vehicle v, Lane lane, int offIdx, int i) {
        final double gap = 55.0;
        double length = lane.getLength();
        double baseProgress = switch (offIdx) {
            case 1 -> length * 0.45;
            case 2 -> length * 0.75;
            default -> 0.0;
        };
        double progress = (offIdx == 0)
            ? i * gap
            : Math.max(0.0, baseProgress - i * gap);

        Vector2D p = lane.getPointAtProgress(progress);
        v.getPosition().setX(p.getX());
        v.getPosition().setY(p.getY());
        v.setAngle(lane.getAngleAtProgress(progress));
    }

    private Label makeLabel(String text) {
        Label lbl = new Label(text);
        lbl.setFont(Font.font("SansSerif", 12));
        lbl.setTextFill(Color.rgb(170, 185, 210));
        return lbl;
    }
}
