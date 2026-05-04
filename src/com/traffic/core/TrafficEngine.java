package com.traffic.core;

import com.traffic.map.TrafficLight;
import java.util.ArrayList;
import java.util.List;
// Lop chinh dieu khien toan bo he thong giao thong
public class TrafficEngine {
    private List<Vehicle> vehicles; // Danh sach xe do tminh quan ly
    private List<TrafficLight> lights; // Danh sach den do bson quan ly

    public TrafficEngine() {
        this.vehicles = new ArrayList<>();
        this.lights = new ArrayList<>();
    }

    public void addVehicle(Vehicle v) {
        vehicles.add(v);
    }

    public void addTrafficLight(TrafficLight light) {
        lights.add(light);
    }

    // Vong lap xu ly logic thoi gian thuc
    public void tick(double deltaTime) {
        // Cap nhat trang thai den cua bson
        for (TrafficLight light : lights) {
            light.tick();
        }

        // Cap nhat vi tri va logic xe cua tminh
        for (Vehicle vehicle : vehicles) {
            TrafficLight nearestLight = lights.isEmpty() ? null : lights.get(0);
            
            if (vehicle.driver != null) {
                vehicle.driver.makeDecision(vehicle, nearestLight);
            }
            
            vehicle.update(deltaTime);
        }
    }

    // Tra ve danh sach xe de tminh dung ve len giao dien
    public List<Vehicle> getVehicles() {
        return vehicles;
    }
}