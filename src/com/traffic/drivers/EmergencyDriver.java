package com.traffic.drivers;

import com.traffic.core.IDriver;
import com.traffic.core.Vehicle;
import com.traffic.map.TrafficLight;

// Tai xe xe uu tien (Ambulance, FireTruck):
// Khong tuan thu den do, chay toc do toi da moi luc
// Cac xe khac phai nhuong duong (xu ly o TrafficEngine)
public class EmergencyDriver implements IDriver {

    // Toc do cao nhat trong tat ca cac loai driver
    private static final double EMERGENCY_SPEED = 160.0;

    @Override
    public void makeDecision(Vehicle vehicle, TrafficLight nextLight) {
        // Hoan toan bo qua trang thai den giao thong
        // Ke ca den do cung vuot qua
        vehicle.setSpeed(EMERGENCY_SPEED);

        // Khong can kiem tra nextLight
        // Logic nhuong duong cua xe khac duoc xu ly o TrafficEngine:
        //   khi phat hien EmergencyDriver gan ke -> goi vehicle.setSpeed(giam) voi xe thuong
    }
}
