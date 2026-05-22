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
    private double timeAccumulator = 0.0;

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
        timeAccumulator += deltaTime;
        if (timeAccumulator >= 1.0) {
            updateLights();
            timeAccumulator -= 1.0;
        }
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
            // ── Bám làn tự động ──────────────────────────────────────────
            if (vehicle.getCurrentLane() != null) {
                List<com.traffic.core.Vector2D> waypoints = vehicle.getCurrentLane().getwaypoints();
                if (waypoints != null && !waypoints.isEmpty()) {
                    int targetIdx = vehicle.getCurrentWaypointIndex();

                    if (targetIdx >= waypoints.size()) {
                        vehicle.setLane(vehicle.getCurrentLane());
                        targetIdx = vehicle.getCurrentWaypointIndex();
                    }

                    com.traffic.core.Vector2D target = waypoints.get(targetIdx);
                    double dist = MathUtils.distance(vehicle.getPosition(), target);

                    if (dist < 8.0) {
                        targetIdx++;
                        vehicle.setCurrentWaypointIndex(targetIdx);
                        if (targetIdx < waypoints.size()) {
                            target = waypoints.get(targetIdx);
                        } else {
                            vehicle.setLane(vehicle.getCurrentLane());
                            targetIdx = vehicle.getCurrentWaypointIndex();
                            target = waypoints.get(targetIdx);
                        }
                    }

                    double dx = target.getX() - vehicle.getPosition().getX();
                    double dy = target.getY() - vehicle.getPosition().getY();
                    double angle = Math.toDegrees(Math.atan2(dy, dx));
                    vehicle.setAngle(angle);
                }
            }

            // ── Giữ khoảng cách xe phía trước trên cùng làn ─────────────
            double followSpeedLimit = applyFollowDistance(vehicle);

            // ── Quyết định dựa trên đèn của làn đang chạy ────────────────
            TrafficLight laneLight = findLaneLight(vehicle);
            vehicle.makeDecision(laneLight);

            // Áp dụng giới hạn tốc độ do xe phía trước (không để vượt qua)
            if (vehicle.getSpeed() > followSpeedLimit) {
                vehicle.setSpeed(followSpeedLimit);
            }

            vehicle.update(deltaTime);
        }
    }

    /**
     * Tính tốc độ tối đa cho phép dựa trên khoảng cách xe phía trước CÙNG LÀN.
     *
     * - Xe ưu tiên (ambulance, firetruck): luôn có quyền ưu tiên, bỏ qua follow distance.
     * - Chỉ xét xe cùng làn, dùng dot product xác định "phía trước".
     * - Công thức sqrt: khi khoảng trống vừa mở ra, xe tăng tốc nhanh hơn tuyến tính.
     *
     * LÝ DO BỎ CROSS-LANE CHECK:
     *   Xe từ làn vuông góc gặp nhau tại ngã tư, dot product THƯỜNG > 0 cho CẢ HAI phía
     *   → cả hai thấy nhau là "xe phía trước" → cả hai dừng → deadlock vĩnh viễn.
     */
    private double applyFollowDistance(Vehicle self) {
        // Xe ưu tiên luôn chạy tốc độ tối đa — bỏ qua mọi xe phía trước
        if (self.isPriority()) return Double.MAX_VALUE;

        final double MIN_GAP         = 15.0; // Dừng hoàn toàn khi gần hơn mức này
        final double FOLLOW_DISTANCE = 80.0; // Bắt đầu giảm tốc khi gần hơn mức này
        final double MAX_FOLLOW_SPEED = 80.0;

        double closestFront = Double.MAX_VALUE;

        for (Vehicle other : vehicles) {
            if (other == self) continue;
            // Chỉ xét xe CÙNG LÀN để tránh deadlock xuyên làn
            if (other.getCurrentLane() != self.getCurrentLane()) continue;

            double dist = MathUtils.distance(self.getPosition(), other.getPosition());
            if (dist >= FOLLOW_DISTANCE) continue;

            // Dùng dot product xác định xe "ở phía trước" theo hướng di chuyển
            if (calcDotToTarget(self, other) > 0 && dist < closestFront) {
                closestFront = dist;
            }
        }

        if (closestFront > FOLLOW_DISTANCE) return Double.MAX_VALUE;
        if (closestFront <= MIN_GAP)        return 0.0;

        // sqrt giúp xe tăng tốc nhanh hơn tuyến tính khi gap vừa mở ra
        double ratio = (closestFront - MIN_GAP) / (FOLLOW_DISTANCE - MIN_GAP);
        return Math.sqrt(ratio) * MAX_FOLLOW_SPEED;
    }

    /**
     * Tính tích vô hướng giữa hướng di chuyển của xe self và vector trỏ tới other.
     * > 0 nghĩa là other ở phía trước self.
     */
    private double calcDotToTarget(Vehicle self, Vehicle other) {
        double selfAngleRad = Math.toRadians(self.getAngle());
        double selfDirX = Math.cos(selfAngleRad);
        double selfDirY = Math.sin(selfAngleRad);

        double toOtherX = other.getPosition().getX() - self.getPosition().getX();
        double toOtherY = other.getPosition().getY() - self.getPosition().getY();

        return selfDirX * toOtherX + selfDirY * toOtherY;
    }

    /**
     * Trả về đèn giao thông mà xe cần tuân theo, hoặc null nếu không cần check đèn.
     *
     * Logic:
     *   waypointIndex == 0 → xe đang di chuyển đến điểm ĐẦU LÀN (chưa đến gần đèn) → null
     *   waypointIndex == 1 → xe đang TIẾN ĐẾN vạch dừng → cần check đèn → trả về light
     *   waypointIndex >= 2 → xe đã QUA vạch dừng, đang thoát ra → null
     *
     * Điều này đảm bảo:
     *   - Xe không bị đèn đỏ "chặn" khi còn ở đầu làn (xa ngã tư 300px+)
     *   - Xe đúng lúc mới thấy đèn khi tiến vào vùng ngã tư (waypointIndex = 1)
     */
    private TrafficLight findLaneLight(Vehicle vehicle) {
        if (vehicle.getCurrentLane() == null) return null;
        // CHỈ check đèn khi xe đang ở waypoint 1 (tiến đến vạch dừng)
        if (vehicle.getCurrentWaypointIndex() != 1) return null;
        return vehicle.getCurrentLane().getLight();
    }

    /**
     * Tìm đèn gần xe nhất trong danh sách (dự phòng).
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