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
            // Khong co den -> chay binh thuong
            vehicle.setSpeed(MAX_SPEED);
            return;
        }

        double dist = MathUtils.distance(vehicle.getPosition(), nextLight.getPosition());

        if (nextLight.isRed()) {
            if (dist <= STOP_DISTANCE) {
                // Dung han truoc den
                vehicle.setSpeed(MIN_SPEED);
            } else if (dist <= BRAKE_DISTANCE) {
                // Giam toc dan khi gan den do
                double ratio = (dist - STOP_DISTANCE) / (BRAKE_DISTANCE - STOP_DISTANCE);
                vehicle.setSpeed(MathUtils.clamp(MAX_SPEED * ratio, MIN_SPEED, MAX_SPEED));
            } else {
                // Con xa -> chay binh thuong
                vehicle.setSpeed(MAX_SPEED);
            }
        } else {
            // Den xanh hoac vang -> chay binh thuong
            vehicle.setSpeed(MAX_SPEED);
        }
    }
}