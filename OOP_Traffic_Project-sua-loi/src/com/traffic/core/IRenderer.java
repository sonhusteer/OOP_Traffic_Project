package com.traffic.core;

import com.traffic.map.Intersection;
import com.traffic.map.TrafficLight;
import java.util.List;

/**
 * Hợp đồng cho mọi chế độ hiển thị.
 *
 * Đây là trung tâm của tính ĐA HÌNH trong phần vẽ:
 *   - BasicRenderer    → vẽ hình chữ nhật + tên
 *   - JavaFXRenderer   → vẽ ảnh, xoay, hiệu ứng
 *
 * TrafficEngine chỉ gọi renderer.render(...) mà không cần
 * biết đang dùng chế độ nào. Đổi renderer = đổi toàn bộ
 * giao diện mà không sửa một dòng logic nào.
 */
public interface IRenderer {

    /** Xóa canvas trước mỗi frame */
    void clear();

    /** Vẽ toàn bộ xe */
    void renderVehicles(List<Vehicle> vehicles);

    /** Vẽ toàn bộ đèn giao thông */
    void renderLights(List<TrafficLight> lights);

    /** Vẽ ngã giao — hộp asphalt, vạch dừng, zebra */
    void renderIntersections(List<Intersection> intersections);
}