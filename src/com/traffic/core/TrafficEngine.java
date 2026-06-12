package com.traffic.core;

import com.traffic.map.Intersection;
import com.traffic.map.Lane;
import com.traffic.map.TrafficLight;
import java.util.ArrayList;
import java.util.List;

public class TrafficEngine {

    private final List<Vehicle>      vehicles      = new ArrayList<>();
    private final List<TrafficLight> lights        = new ArrayList<>();
    private final List<Intersection> intersections = new ArrayList<>();

    /** Luat uu tien duoc tach khoi engine de engine chi con dieu phoi tick. */
    private final EmergencyVehicleCoordinator emergencyCoordinator = new EmergencyVehicleCoordinator();

    private IRenderer renderer;

    public TrafficEngine(IRenderer renderer) {
        this.renderer = renderer;
    }

    // ---------------------------------------------------------------------
    // Dang ky / huy dang ky object trong simulation.
    // ---------------------------------------------------------------------

    public void addVehicle(Vehicle v) {
        if (v != null && !vehicles.contains(v)) {
            vehicles.add(v);
        }
    }

    public void addTrafficLight(TrafficLight l) {
        // Cung mot TrafficLight co the nam trong nhieu Intersection, nen engine
        // chi luu moi object dung 1 lan de den khong bi tick nhanh gap doi.
        if (l != null && !lights.contains(l)) {
            lights.add(l);
        }
    }

    public void addIntersection(Intersection i) {
        if (i != null && !intersections.contains(i)) {
            intersections.add(i);
        }
    }

    public void removeVehicle(Vehicle v) {
        if (v == null) return;
        vehicles.remove(v);
        cleanupVehicleFromLanes(v);
    }

    public void clearVehicles() {
        // Fix "xe ma": xoa xe khoi Lane/reservation truoc khi clear list engine.
        for (Vehicle v : new ArrayList<>(vehicles)) {
            cleanupVehicleFromLanes(v);
        }
        vehicles.clear();
    }

    public void clearLights() {
        lights.clear();
    }

    /** Alias giu tuong thich voi UI cu. */
    public void clearTrafficLights() {
        clearLights();
    }

    public void clearIntersections() {
        intersections.clear();
    }

    public void setRenderer(IRenderer renderer) {
        this.renderer = renderer;
    }

    private void cleanupVehicleFromLanes(Vehicle v) {
        // Vehicle tu biet go minh khoi lane hien tai, lane goc, target lane va reservation.
        v.detachFromLanes();
    }

    // ---------------------------------------------------------------------
    // Main tick / render.
    // ---------------------------------------------------------------------

    public void tick(double deltaTime) {
        updateLights(deltaTime);
        emergencyCoordinator.apply(vehicles, intersections);
        updateVehicles(deltaTime);
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

    /**
     * Update xe theo 2 pha:
     * 1. Tat ca xe doc snapshot vi tri hien tai va ra quyet dinh.
     * 2. Tat ca xe moi cap nhat vat ly.
     *
     * Cach nay tranh viec xe sau doc vi tri moi cua xe truoc trong khi cac xe
     * khac van chua update xong.
     */
    private void updateVehicles(double deltaTime) {
        List<Vehicle> snapshot = new ArrayList<>(vehicles);

        // Phase 1: decision.
        for (Vehicle vehicle : snapshot) {
            if (!vehicles.contains(vehicle)) continue;

            TrafficLight targetLight = null;
            Lane lane = vehicle.getLane();
            if (lane != null) {
                targetLight = lane.getLight();
            }
            vehicle.makeDecision(targetLight);
        }

        // Phase 2: physics update + collect out-of-map vehicles.
        List<Vehicle> toRemove = new ArrayList<>();
        for (Vehicle vehicle : snapshot) {
            if (!vehicles.contains(vehicle)) continue;

            vehicle.update(deltaTime);

            double x = vehicle.getPosition().getX();
            double y = vehicle.getPosition().getY();
            if (x < -200 || x > 1000 || y < -200 || y > 800) {
                toRemove.add(vehicle);
            }
        }

        for (Vehicle v : toRemove) {
            removeVehicle(v);
        }
    }

    // ---------------------------------------------------------------------
    // Getters. Van tra ve List goc de giu tuong thich voi UI hien co.
    // ---------------------------------------------------------------------

    public List<Vehicle>      getVehicles()      { return vehicles;      }
    public List<TrafficLight> getLights()        { return lights;        }
    public List<Intersection> getIntersections() { return intersections; }
}
