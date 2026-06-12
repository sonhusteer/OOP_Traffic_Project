package com.traffic.core;

import com.traffic.map.Intersection;
import com.traffic.map.TrafficLight;
import java.util.ArrayList;
import java.util.List;

public class TrafficEngine {

    private final List<Vehicle>      vehicles      = new ArrayList<>();
    private final List<TrafficLight> lights        = new ArrayList<>();
    private final List<Intersection> intersections = new ArrayList<>();
    private IRenderer renderer;

    // ── Ngưỡng khoảng cách phát hiện ─────────────────────────────────────

    /** Xe ưu tiên cách xe thường < 150px trong cùng làn → nhường */
    private static final double SAME_LANE_YIELD_DIST   = 150.0;

    /** Xe ưu tiên hoặc xe thường cách tâm ngã tư < 200px → vùng nguy hiểm */
    private static final double INTERSECTION_DANGER    = 200.0;

    public TrafficEngine(IRenderer renderer) {
        this.renderer = renderer;
    }

    public void addVehicle(Vehicle v)            { vehicles.add(v); }
    public void addTrafficLight(TrafficLight l)  { lights.add(l); }
    public void addIntersection(Intersection i)  { intersections.add(i); }
    public void removeVehicle(Vehicle v)         { vehicles.remove(v); }
    public void clearVehicles()                  { vehicles.clear(); }
    public void setRenderer(IRenderer renderer)  { this.renderer = renderer; }

    public void tick(double deltaTime) {
        updateLights(deltaTime);
        detectEmergencyProximity();   // ← phát hiện trước khi xe ra quyết định
        updateVehicles(deltaTime);
    }

    public void render() {
        if (renderer == null) return;
        renderer.clear();
        renderer.renderLights(lights);
        renderer.renderVehicles(vehicles);
    }

    // ── Cập nhật đèn ─────────────────────────────────────────────────────

    private void updateLights(double deltaTime) {
        for (TrafficLight light : lights) {
            light.tick(deltaTime);
        }
    }

    // ── Cập nhật xe ──────────────────────────────────────────────────────

    private void updateVehicles(double deltaTime) {
        List<Vehicle> toRemove = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            TrafficLight targetLight = null;
            if (vehicle.getLane() != null) {
                targetLight = vehicle.getLane().getLight();
            }
            vehicle.makeDecision(targetLight);
            vehicle.update(deltaTime);

            // Kiểm tra ra khỏi bản đồ (Màn hình 800x600, xóa khi ra quá xa)
            double x = vehicle.getPosition().getX();
            double y = vehicle.getPosition().getY();
            if (x < -200 || x > 1000 || y < -200 || y > 800) {
                toRemove.add(vehicle);
            }
        }
        
        for (Vehicle v : toRemove) {
            vehicles.remove(v);
            if (v.getLane() != null) {
                v.getLane().removeVehicle(v);
            }
        }
    }

    // ── Phát hiện xe ưu tiên và đánh dấu nhường đường ───────────────────

    /**
     * Chạy mỗi tick, xử lý 2 tình huống:
     *
     * Tình huống 1 — Cùng làn:
     *   Xe ưu tiên đang ở phía sau xe thường trong cùng làn,
     *   khoảng cách < SAME_LANE_YIELD_DIST → xe thường nhường (dừng hẳn).
     *
     * Tình huống 2 — Xung đột ngã tư:
     *   Xe ưu tiên đang tiến vào ngã tư (dist đến tâm < INTERSECTION_DANGER),
     *   xe thường ở làn khác cùng ngã tư cũng trong vùng nguy hiểm
     *   → xe thường dừng hẳn dù đèn xanh.
     */
    private void detectEmergencyProximity() {
        // Reset toàn bộ về NONE — chỉ set lại nếu vẫn còn nguy hiểm
        for (Vehicle v : vehicles) {
            if (!v.isPriority()) v.setYieldMode(Vehicle.YieldMode.NONE);
        }

        for (Vehicle priority : vehicles) {
            if (!priority.isPriority()) continue;

            // ── Tình huống 1: Cùng làn ───────────────────────────────────
            if (priority.getLane() != null) {
                double prioProgress = priority.getLane()
                        .getProgress(priority.getPosition());

                for (Vehicle normal : vehicles) {
                    if (normal.isPriority()) continue;
                    if (normal.getLane() != priority.getLane()) continue;

                    double normalProgress = normal.getLane()
                            .getProgress(normal.getPosition());
                    double distBetween = MathUtils.distance(
                            priority.getPosition(), normal.getPosition());

                    // Xe ưu tiên ở phía sau (progress nhỏ hơn) và đủ gần
                    if (prioProgress < normalProgress
                            && distBetween < SAME_LANE_YIELD_DIST) {
                        normal.setYieldMode(Vehicle.YieldMode.RUSH);
                    }
                }
            }

            // ── Tình huống 2: Xung đột ngã tư ────────────────────────────
            for (Intersection intersection : intersections) {
                double distPrioToCenter = MathUtils.distance(
                        priority.getPosition(), intersection.getCenter());

                // Xe ưu tiên đang trong vùng nguy hiểm của ngã tư này
                if (distPrioToCenter < INTERSECTION_DANGER) {
                    for (Vehicle normal : vehicles) {
                        if (normal.isPriority()) continue;

                        // Bỏ qua nếu cùng làn (đã xử lý ở tình huống 1)
                        if (normal.getLane() == priority.getLane()) continue;

                        // Xe thường phải thuộc cùng ngã tư này
                        if (!intersection.getLanes().contains(normal.getLane())) continue;

                        double distNormalToCenter = MathUtils.distance(
                                normal.getPosition(), intersection.getCenter());

                        // Xe thường cũng đang trong vùng nguy hiểm
                        // → STOP: dừng hẳn dù đèn xanh
                        if (distNormalToCenter < INTERSECTION_DANGER) {
                            normal.setYieldMode(Vehicle.YieldMode.STOP);
                        }
                    }
                }
            }
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public List<Vehicle>      getVehicles()      { return vehicles; }
    public List<TrafficLight> getLights()        { return lights; }
    public List<Intersection> getIntersections() { return intersections; }
}