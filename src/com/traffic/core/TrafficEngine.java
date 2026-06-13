package com.traffic.core;

import com.traffic.map.Intersection;
import com.traffic.map.TrafficLight;
import java.util.ArrayList;
import java.util.List;

public class TrafficEngine {

    private final List<Vehicle>      vehicles      = new ArrayList<>();
    private final List<TrafficLight> lights        = new ArrayList<>();
    private final List<Intersection> intersections = new ArrayList<>();
    private final EmergencyManager   emergencyManager = new EmergencyManager();
    private final TurnCoordinator    turnCoordinator = new TurnCoordinator();
    private IRenderer renderer;

    public TrafficEngine(IRenderer renderer) {
        this.renderer = renderer;
    }

    public void addVehicle(Vehicle v) {
        if (v != null && !vehicles.contains(v)) vehicles.add(v);
    }

    public void addTrafficLight(TrafficLight l) {
        if (l != null && !lights.contains(l)) lights.add(l);
    }

    public void addIntersection(Intersection i) {
        if (i != null && !intersections.contains(i)) intersections.add(i);
    }

    public void clearTrafficLights() { lights.clear(); }

    public void clearIntersections() { intersections.clear(); }

    public void removeVehicle(Vehicle v) {
        if (v == null) return;
        vehicles.remove(v);
        if (v.getLane() != null) {
            v.getLane().removeVehicle(v);
        }
    }

    /** Xóa cả danh sách engine lẫn danh sách xe đang nằm trong từng Lane. */
    public void clearVehicles() {
        for (Vehicle v : new ArrayList<>(vehicles)) {
            if (v.getLane() != null) {
                v.getLane().removeVehicle(v);
            }
        }
        vehicles.clear();
        for (Intersection intersection : intersections) {
            for (var lane : intersection.getLanes()) {
                lane.clearReservations();
            }
        }
    }

    public void setRenderer(IRenderer renderer) { this.renderer = renderer; }

    public void tick(double deltaTime) {
        updateLights(deltaTime);
        PriorityRouteAnalyzer priorityRoutes = PriorityRouteAnalyzer.analyze(vehicles, intersections);
        emergencyManager.update(vehicles, intersections, priorityRoutes);
        turnCoordinator.updateBeforeDrivers(vehicles, intersections, priorityRoutes);
        updateVehicles(deltaTime, priorityRoutes);
    }

    public void render() {
        if (renderer == null) return;
        renderer.clear();
        renderer.renderIntersections(intersections);
        renderer.renderLights(lights);
        renderer.renderVehicles(vehicles);
    }

    private void updateLights(double deltaTime) {
        for (TrafficLight light : lights) {
            light.tick(deltaTime);
        }
    }

    private void updateVehicles(double deltaTime, PriorityRouteAnalyzer priorityRoutes) {
        List<Vehicle> toRemove = new ArrayList<>();
        for (Vehicle vehicle : new ArrayList<>(vehicles)) {
            TrafficLight targetLight = vehicle.getLane() != null
                    ? vehicle.getLane().getNextLight(vehicle.getFrontProgress())
                    : null;

            vehicle.makeDecision(targetLight);
            VehicleDecisionMerger.resolve(vehicle, targetLight, priorityRoutes, vehicles, intersections)
                    .applyTo(vehicle);
            vehicle.update(deltaTime);

            double x = vehicle.getPosition().getX();
            double y = vehicle.getPosition().getY();
            if (x < -220 || x > 1020 || y < -220 || y > 820) {
                toRemove.add(vehicle);
            }
        }

        for (Vehicle v : toRemove) {
            removeVehicle(v);
        }
    }

    public List<Vehicle>      getVehicles()      { return vehicles; }
    public List<TrafficLight> getLights()        { return lights; }
    public List<Intersection> getIntersections() { return intersections; }
}
