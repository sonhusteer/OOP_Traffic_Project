package com.traffic.ui;

import com.traffic.maps.MapConfig;
import com.traffic.maps.*;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Entry-point JavaFX — chỉ dựng giao diện và ghép các panel.
 * Toàn bộ logic simulation, spawn, toolbar đã được tách ra.
 */
public class MainApp extends Application {

    private static final MapConfig[] ALL_MAPS = {
        new CrossroadsMap(),
        new TJunctionMap(),
        new FiveWayMap(),
        new NetworkMap(),
        new HighwayMap()
    };

    private SimulationController controller;
    private SidebarPanel sidebar;
    private ToolbarPanel toolbar;
    private BorderPane root;
    private int currentMapIndex = 0;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        // Canvas
        Canvas canvas = new Canvas(800, 600);

        // Controller
        controller = new SimulationController(canvas, ALL_MAPS[0]);

        // Mouse click → đèn giao thông
        canvas.setOnMouseClicked(e -> {
            boolean left = e.getButton() == MouseButton.PRIMARY;
            controller.getActiveRenderer().handleClick(e.getX(), e.getY(), left);
        });

        // Sidebar
        sidebar = new SidebarPanel(controller);
        VBox sidebarNode = sidebar.build(ALL_MAPS[0]);

        // Toolbar
        toolbar = new ToolbarPanel(controller, ALL_MAPS);
        toolbar.setOnMapLoaded(() -> rebuildSidebar());

        // Cập nhật số xe mỗi frame
        controller.setOnTick(() ->
            toolbar.updateVehicleCount(controller.getEngine().getVehicles().size())
        );

        // Layout
        root = new BorderPane();
        root.setStyle("-fx-background-color: #1a1a2e;");
        root.setCenter(new javafx.scene.layout.StackPane(canvas));
        root.setRight(sidebarNode);
        root.setBottom(toolbar.build());

        // Scene + CSS
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

        // Start
        controller.start();
    }

    /** Rebuild sidebar khi đổi map */
    private void rebuildSidebar() {
        // Tìm map đang được load qua controller
        for (int i = 0; i < ALL_MAPS.length; i++) {
            if (controller.getEngine().getVehicles().isEmpty()) {
                currentMapIndex = i;
                break;
            }
        }
        VBox newSidebar = sidebar.build(ALL_MAPS[currentMapIndex]);
        root.setRight(newSidebar);

        sidebar.getSpawnLog().appendText("[✓] Map: " + ALL_MAPS[currentMapIndex].getName() + "\n");
    }
}