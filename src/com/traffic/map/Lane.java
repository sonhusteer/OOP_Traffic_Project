package com.traffic.map;

import com.traffic.core.Vector2D; // Import class của Huy
import java.util.ArrayList;
import java.util.List;

public class Lane {
    // Danh sách các điểm tạo nên làn đường sử dụng Vector2D
    private List<Vector2D> waypoints;

    public Lane() {
        this.waypoints = new ArrayList<>();
    }

    // Hàm để Sơn thêm điểm khi thiết kế bản đồ
    public void addwaypoint(double x, double y) {
        waypoints.add(new Vector2D(x, y));
    }

    // Minh (xe) sẽ gọi hàm này để biết đường mà đi[cite: 1]
    // Lưu ý: Viết thường chữ 'w' để khớp với MapRenderer bạn đã viết
    public List<Vector2D> getwaypoints() {
        return waypoints;
    }

    // Lấy điểm bắt đầu của con đường[cite: 1]
    public Vector2D getStartPoint() {
        return waypoints.isEmpty() ? null : waypoints.get(0);
    }

    // Lấy điểm kết thúc của con đường[cite: 1]
    public Vector2D getEndPoint() {
        return waypoints.isEmpty() ? null : waypoints.get(waypoints.size() - 1);
    }

    /**
     * Tính toán góc hướng (Heading) giữa hai điểm waypoint
     * Giúp Minh (xe) xoay hình ảnh xe theo đúng chiều đường[cite: 1]
     */
    public double getAngleAt(int index) {
        if (index >= waypoints.size() - 1) return 0;
        Vector2D p1 = waypoints.get(index);
        Vector2D p2 = waypoints.get(index + 1);
        
        // Vận dụng Trigonometry (Lượng giác) từ đặc tả[cite: 1]
        return Math.atan2(p2.getY() - p1.getY(), p2.getX() - p1.getX());
    }
}