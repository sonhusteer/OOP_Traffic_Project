package com.traffic.drivers;

import com.traffic.core.IDriver;
import com.traffic.core.MathUtils;
import com.traffic.core.Vehicle;
import com.traffic.map.TrafficLight;

// Tai xe binh thuong: tuan thu den do, giu khoang cach an toan
public class NormalDriver implements IDriver {

    // Khoang cach bat dau giam toc khi thay den do (pixel)
    private static final double BRAKE_DISTANCE = 80.0;

    // Khoang cach dung han truoc vach den (pixel)
    private static final double STOP_DISTANCE = 10.0;

    // Toc do toi da binh thuong
    private static final double MAX_SPEED = 80.0;

    // Toc do toi thieu khi giam toc gan den
    private static final double MIN_SPEED = 0.0;

    @Override
    public void makeDecision(Vehicle vehicle, TrafficLight nextLight) {
        if (nextLight == null) {
            // Không có đèn → chạy bình thường
            vehicle.setSpeed(MAX_SPEED);
            return;
        }

        // Nếu xe đã vượt qua vạch dừng (đang đi tới waypoint cuối) → tiếp tục chạy
        if (vehicle.getCurrentWaypointIndex() >= 2) {
            vehicle.setSpeed(MAX_SPEED);
            return;
        }

        double dist = com.traffic.core.MathUtils.distance(vehicle.getPosition(), nextLight.getPosition());

        if (nextLight.isRed()) {
            if (dist <= STOP_DISTANCE) {
                vehicle.setSpeed(MIN_SPEED);                        // Dừng hẳn trước vạch
            } else if (dist <= BRAKE_DISTANCE) {
                // Giảm tốc dần khi gần đèn đỏ
                double ratio = (dist - STOP_DISTANCE) / (BRAKE_DISTANCE - STOP_DISTANCE);
                vehicle.setSpeed(com.traffic.core.MathUtils.clamp(MAX_SPEED * ratio, MIN_SPEED, MAX_SPEED));
            } else {
                vehicle.setSpeed(MAX_SPEED);                        // Còn xa → chạy bình thường
            }
        } else {
            // Đèn xanh hoặc vàng → chạy bình thường
            vehicle.setSpeed(MAX_SPEED);
        }
    }
}