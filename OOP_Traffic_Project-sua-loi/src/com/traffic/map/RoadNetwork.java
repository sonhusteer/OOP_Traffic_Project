package com.traffic.map;

import com.traffic.core.TrafficEngine;
import java.util.ArrayList;
import java.util.List;

/**
 * Mạng lưới giao thông — quản lý nhiều Intersection.
 *
 * Nhiệm vụ:
 *  - Lưu danh sách tất cả ngã rẽ
 *  - Đăng ký toàn bộ đèn vào TrafficEngine để engine cập nhật
 *  - Dễ mở rộng: thêm ngã rẽ mới chỉ cần gọi addIntersection()
 */
public class RoadNetwork {

    private final List<Intersection> intersections = new ArrayList<>();

    // ── Quản lý ngã rẽ ───────────────────────────────────────────────────

    public void addIntersection(Intersection intersection) {
        intersections.add(intersection);
    }

    public List<Intersection> getIntersections() {
        return intersections;
    }

    /**
     * Lấy tất cả đèn trong toàn bộ mạng lưới.
     * TrafficEngine dùng method này để đăng ký đèn.
     */
    public List<TrafficLight> getAllLights() {
        List<TrafficLight> all = new ArrayList<>();
        for (Intersection intersection : intersections) {
            all.addAll(intersection.getAllLights());
        }
        return all;
    }

    /**
     * Đăng ký toàn bộ đèn VÀ ngã rẽ của mạng lưới vào engine.
     * Gọi 1 lần duy nhất khi khởi tạo simulation.
     *
     * [Cách B] RoadNetwork tự động truyền Intersection vào engine
     * → MainApp không cần gọi thêm engine.addIntersection().
     */
    public void registerTo(TrafficEngine engine) {
        for (Intersection intersection : intersections) {
            // Đăng ký đèn
            for (TrafficLight light : intersection.getAllLights()) {
                engine.addTrafficLight(light);
            }
            // Đăng ký ngã rẽ để engine phát hiện xung đột
            engine.addIntersection(intersection);
        }
    }
}