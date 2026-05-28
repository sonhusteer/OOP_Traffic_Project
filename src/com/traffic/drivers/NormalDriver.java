package com.traffic.drivers;

import com.traffic.core.IDriver;
import com.traffic.core.MathUtils;
import com.traffic.core.Vehicle;
import com.traffic.map.TrafficLight;

// Tài xế bình thường: tuân thủ đèn đỏ, giữ khoảng cách an toàn
public class NormalDriver implements IDriver {

    // Khoảng cách bắt đầu giảm tốc khi thấy đèn đỏ (pixel)
    private static final double BRAKE_DISTANCE = 80.0;

    // Khoảng cách dừng hẳn trước vạch đèn (pixel)
    private static final double STOP_DISTANCE  = 10.0;

    // Tốc độ tối đa bình thường
    private static final double MAX_SPEED      = 80.0;

    // Tốc độ tối thiểu khi giảm tốc gần đèn
    private static final double MIN_SPEED      = 0.0;

    // Khoảng cách an toàn với xe phía trước
    private static final double SAFE_DISTANCE  = 45.0;

    @Override
    public void makeDecision(Vehicle vehicle, TrafficLight nextLight) {
        double targetSpeed = MAX_SPEED;

        // ── Kiểm tra đèn giao thông ──────────────────────────────────────
        if (nextLight != null && vehicle.getLane() != null) {

            // Dùng vạch dừng thay vì vị trí cột đèn (chính xác hơn)
            var stopLine       = vehicle.getLane().getStopLine();
            var laneStart      = vehicle.getLane().getStart();

            double distToStop      = MathUtils.distance(vehicle.getPosition(), stopLine);
            double myDistToStart   = MathUtils.distance(laneStart, vehicle.getPosition());
            double stopDistToStart = MathUtils.distance(laneStart, stopLine);

            // Xe chưa qua vạch dừng
            boolean isPastStop = myDistToStart > stopDistToStart;

            if (!isPastStop && nextLight.isRed()) {
                if (distToStop <= STOP_DISTANCE) {
                    targetSpeed = MIN_SPEED;                   // dừng hẳn
                } else if (distToStop <= BRAKE_DISTANCE) {
                    double ratio = (distToStop - STOP_DISTANCE)
                                 / (BRAKE_DISTANCE - STOP_DISTANCE);
                    targetSpeed = MathUtils.clamp(
                            MAX_SPEED * ratio, MIN_SPEED, MAX_SPEED);
                }
            }
            // Đèn vàng hoặc xanh: chạy bình thường
        }

        // ── Giữ khoảng cách xe phía trước ────────────────────────────────
        if (vehicle.getLane() != null) {
            Vehicle inFront = vehicle.getLane().getVehicleAhead(vehicle);
            if (inFront != null) {
                double distToCar = MathUtils.distance(
                        vehicle.getPosition(), inFront.getPosition());

                if (distToCar <= SAFE_DISTANCE) {
                    targetSpeed = Math.min(targetSpeed, inFront.getSpeed());
                    if (distToCar <= SAFE_DISTANCE * 0.5) {
                        targetSpeed = MIN_SPEED;
                    }
                }
            }
        }

        vehicle.setSpeed(targetSpeed);
    }
}
