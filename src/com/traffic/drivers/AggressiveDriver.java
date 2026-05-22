package com.traffic.drivers;

import com.traffic.core.IDriver;
import com.traffic.core.MathUtils;
import com.traffic.core.Vehicle;
import com.traffic.map.TrafficLight;

// Tai xe hung hang: phong nhanh, giu khoang cach ngan hon,
// dung gon vao den do, co the "luot" qua den vang
public class AggressiveDriver implements IDriver {

    // Giam toc muon hon NormalDriver — nhưng đủ xa để phanh kịp ở tốc độ cao
    private static final double BRAKE_DISTANCE = 120.0;

    private static final double STOP_DISTANCE = 10.0;

    // Toc do vừa phải để dễ quan sát và kiểm tra
    private static final double MAX_SPEED = 80.0;

    private static final double MIN_SPEED = 0.0;

    @Override
    public void makeDecision(Vehicle vehicle, TrafficLight nextLight) {
        if (nextLight == null) {
            vehicle.setSpeed(MAX_SPEED);
            return;
        }

        // Nếu xe đã vượt qua ngã tư có đèn -> tiếp tục chạy
        if (vehicle.getCurrentWaypointIndex() >= 2) {
            vehicle.setSpeed(MAX_SPEED);
            return;
        }

        double dist = MathUtils.distance(vehicle.getPosition(), nextLight.getPosition());

        if (nextLight.isRed()) {
            if (dist <= STOP_DISTANCE) {
                vehicle.setSpeed(MIN_SPEED);
            } else if (dist <= BRAKE_DISTANCE) {
                // Giam toc gap gap, khong on nhung van dung
                double ratio = (dist - STOP_DISTANCE) / (BRAKE_DISTANCE - STOP_DISTANCE);
                vehicle.setSpeed(MathUtils.clamp(MAX_SPEED * ratio, MIN_SPEED, MAX_SPEED));
            } else {
                vehicle.setSpeed(MAX_SPEED);
            }
        } else if (nextLight.isYellow()) {
            // Den vang: tang toc co gang vuot qua
            vehicle.setSpeed(MAX_SPEED * 1.1);
        } else {
            // Den xanh: chay toc do toi da
            vehicle.setSpeed(MAX_SPEED);
        }
    }
}
