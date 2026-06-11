package com.traffic.ui;

import com.traffic.core.IRenderer;
import com.traffic.core.Vehicle;
import com.traffic.map.Lane;
import com.traffic.map.TrafficLight;
import javafx.scene.canvas.GraphicsContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Lớp cha trừu tượng chứa logic dùng chung cho mọi chế độ vẽ.
 * Dùng JavaFX Canvas thay vì Swing JPanel.
 */
public abstract class AbstractBaseRenderer implements IRenderer {

    protected List<Lane> lanes;
    protected final List<Vehicle> vehicles = new ArrayList<>();
    protected final List<TrafficLight> lights = new ArrayList<>();

    private static final int LIGHT_HIT_RADIUS = 45;

    public AbstractBaseRenderer(List<Lane> lanes) {
        this.lanes = lanes;
    }

    /** Đổi danh sách làn khi load map mới */
    public void setLanes(List<Lane> newLanes) {
        this.lanes = newLanes;
    }

    /** Xử lý click chuột trên đèn giao thông */
    public void handleClick(double x, double y, boolean leftButton) {
        for (TrafficLight light : lights) {
            if (light == null) continue;
            double lx = light.getPosition().getX();
            double ly = light.getPosition().getY();
            double dist = Math.hypot(x - lx, y - ly);
            if (dist <= LIGHT_HIT_RADIUS) {
                if (leftButton) {
                    light.setManualMode(true);
                    light.manualSwitch();
                } else {
                    light.setManualMode(false);
                }
                break;
            }
        }
    }

    @Override
    public void clear() {
        vehicles.clear();
        lights.clear();
    }

    @Override
    public void renderVehicles(List<Vehicle> list) {
        vehicles.addAll(list);
    }

    @Override
    public void renderLights(List<TrafficLight> list) {
        lights.addAll(list);
    }

    /** Vẽ toàn bộ scene lên JavaFX Canvas */
    public abstract void draw(GraphicsContext gc, double width, double height);
}