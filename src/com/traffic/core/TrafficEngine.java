package com.traffic.core;

import com.traffic.map.TrafficLight;
import java.util.ArrayList;
import java.util.List;

public class TrafficEngine {

    private final List<Vehicle>      vehicles = new ArrayList<>();
    private final List<TrafficLight> lights   = new ArrayList<>();
    private IRenderer renderer; 

    public TrafficEngine(IRenderer renderer) {
        this.renderer = renderer;
    }

    public void addVehicle(Vehicle v)        { vehicles.add(v); }
    public void addTrafficLight(TrafficLight l) { lights.add(l); }
    public void removeVehicle(Vehicle v)     { vehicles.remove(v); }

    public void setRenderer(IRenderer renderer) { this.renderer = renderer; }

    public void tick(double deltaTime) {
        updateLights(deltaTime);
        updateVehicles(deltaTime);
    }

    public void render() {
        if (renderer == null) return; 
        renderer.clear();
        renderer.renderLights(lights);
        renderer.renderVehicles(vehicles);
    }

    private void updateLights(double deltaTime) {
        for (TrafficLight light : lights) {
            light.tick(deltaTime); 
        }
    }

    private void updateVehicles(double deltaTime) {
        for (Vehicle vehicle : vehicles) {
            
            /* Lấy trực tiếp đèn giao thông quản lý làn đường hiện tại của xe */
            TrafficLight targetLight = null;
            if (vehicle.getLane() != null) {
                targetLight = vehicle.getLane().getLight();
            }

            /* Truyền đúng đèn của làn vào cho tài xế ra quyết định */
            vehicle.makeDecision(targetLight);
            vehicle.update(deltaTime); 
        }
    }

    public List<Vehicle>      getVehicles() { return vehicles; }
    public List<TrafficLight> getLights()   { return lights; }
}