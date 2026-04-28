package com.traffic.map;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class Lane {
    // Danh sách các điểm tạo nên làn đường
    private List<Point2D> waypoints;

    public Lane() {
        this.waypoints = new ArrayList<>();
    }

    // Hàm để Sơn thêm điểm khi thiết kế bản đồ
    public void addWaypoint(double x, double y) {
        waypoints.add(new Point2D.Double(x, y));
    }

    // Minh (xe) sẽ gọi hàm này để biết đường mà đi
    public List<Point2D> getWaypoints() {
        return waypoints;
    }

    // Lấy điểm bắt đầu của con đường
    public Point2D getStartPoint() {
        return waypoints.isEmpty() ? null : waypoints.get(0);
    }

    // Lấy điểm kết thúc của con đường
    public Point2D getEndPoint() {
        return waypoints.isEmpty() ? null : waypoints.get(waypoints.size() - 1);
    }
}