package com.traffic.core;

import com.traffic.map.TrafficLight;
import java.util.ArrayList;
import java.util.List;

/**
 * Bộ máy điều phối trung tâm.
 *
 * Nguyên tắc thiết kế:
 *  - KHÔNG biết cách vẽ (ủy quyền cho IRenderer)
 *  - KHÔNG biết loại xe cụ thể (chỉ biết Vehicle)
 *  - KHÔNG biết loại driver (chỉ gọi vehicle.makeDecision)
 *  → Thêm xe mới / đèn mới / renderer mới: KHÔNG cần sửa class này
 */
public class TrafficEngine {

    private final List<Vehicle>      vehicles = new ArrayList<>();
    private final List<TrafficLight> lights   = new ArrayList<>();
    private IRenderer renderer; // có thể đổi lúc runtime

    public TrafficEngine(IRenderer renderer) {
        this.renderer = renderer;
    }

    // ── Quản lý đối tượng ─────────────────────────────────────────────────

    public void addVehicle(Vehicle v)        { vehicles.add(v); }
    public void addTrafficLight(TrafficLight l) { lights.add(l); }
    public void removeVehicle(Vehicle v)     { vehicles.remove(v); }

    /** Đổi renderer lúc runtime: Basic ↔ Graphic không cần restart */
    public void setRenderer(IRenderer renderer) { this.renderer = renderer; }

    // ── Vòng lặp chính ────────────────────────────────────────────────────

    /** Cập nhật toàn bộ logic — gọi mỗi frame */
    public void tick(double deltaTime) {
        updateLights();
        updateVehicles(deltaTime);
    }

    /** Vẽ toàn bộ lên màn hình — gọi sau tick() */
    public void render() {
        if (renderer == null) return; 
        renderer.clear();
        renderer.renderLights(lights);
        renderer.renderVehicles(vehicles);
    }

    // ── Logic nội bộ ──────────────────────────────────────────────────────

    private void updateLights() {
        for (TrafficLight light : lights) {
            light.tick();
        }
    }

    private void updateVehicles(double deltaTime) {
        for (Vehicle vehicle : vehicles) {
            // Tìm đèn gần nhất với xe — ĐÚNG hơn luôn lấy lights.get(0)
            TrafficLight nearest = findNearestLight(vehicle);

            // Gọi qua method — KHÔNG truy cập field driver trực tiếp
            vehicle.makeDecision(nearest);

            vehicle.update(deltaTime); // Cập nhật tg thực tế
        }
    }

    /**
     * Tìm đèn gần xe nhất trong danh sách.
     * Dùng MathUtils.distance() để tính khoảng cách.
     */
    private TrafficLight findNearestLight(Vehicle vehicle) {
        if (lights.isEmpty()) return null;

        TrafficLight nearest = null;
        double minDist = Double.MAX_VALUE;

        for (TrafficLight light : lights) {
            double dist = MathUtils.distance(vehicle.getPosition(), light.getPosition());
            if (dist < minDist) {
                minDist = dist;
                nearest = light;
            }
        }
        return nearest;
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public List<Vehicle>      getVehicles() { return vehicles; }
    public List<TrafficLight> getLights()   { return lights; }
}