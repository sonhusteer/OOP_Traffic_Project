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
    private IRenderer renderer;

    // Nguong phat hien xe uu tien.
    private static final double SAME_LANE_YIELD_DIST = 150.0;
    private static final double INTERSECTION_DANGER  = 200.0;

    public TrafficEngine(IRenderer renderer) {
        this.renderer = renderer;
    }

    // ---------------------------------------------------------------------
    // Dang ky / huy dang ky object trong simulation.
    // ---------------------------------------------------------------------

    public void addVehicle(Vehicle v) {
        // Tranh add trung mot xe neu UI bam nham hoac code goi lap.
        if (v != null && !vehicles.contains(v)) {
            vehicles.add(v);
        }
    }

    public void addTrafficLight(TrafficLight l) {
        // Fix loi den bi tick nhanh: cung mot TrafficLight co the nam trong
        // nhieu Intersection, nen engine chi duoc luu moi object dung 1 lan.
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
        // Fix "xe ma": truoc day chi vehicles.clear(), nhung xe van con trong
        // Lane. Khi spawn xe moi, Lane.getVehicleAhead() van thay xe cu.
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
        Lane lane = v.getLane();
        Lane originalLane = v.getOriginalLane();
        Lane targetLane = v.getTargetLane();

        if (lane != null) {
            lane.removeVehicle(v);
        }
        if (originalLane != null && originalLane != lane) {
            originalLane.removeVehicle(v);
        }
        if (targetLane != null) {
            targetLane.removeVehicle(v);
            targetLane.release(v);
        }

        // Xoa tham chieu lane trong Vehicle de object khong con state cu.
        v.detachFromLane();
    }

    // ---------------------------------------------------------------------
    // Main tick / render.
    // ---------------------------------------------------------------------

    public void tick(double deltaTime) {
        updateLights(deltaTime);
        detectEmergencyProximity();
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

    private void updateVehicles(double deltaTime) {
        List<Vehicle> toRemove = new ArrayList<>();

        // Duyet tren ban copy de an toan neu co xe bi remove trong luc update.
        for (Vehicle vehicle : new ArrayList<>(vehicles)) {
            if (!vehicles.contains(vehicle)) continue;

            TrafficLight targetLight = null;
            if (vehicle.getLane() != null) {
                targetLight = vehicle.getLane().getLight();
            }

            vehicle.makeDecision(targetLight);
            vehicle.update(deltaTime);

            // Vung ngoai ban do 800x600. Cho lech them de xe di het man hinh.
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
    // Phat hien xe uu tien va danh dau YieldMode cho xe thuong.
    // ---------------------------------------------------------------------

    private void detectEmergencyProximity() {
        // Moi frame tinh lai tu dau. Neu het nguy hiem thi ve NONE.
        for (Vehicle v : vehicles) {
            if (!v.isPriority()) {
                v.setYieldMode(Vehicle.YieldMode.NONE);
            }
        }

        for (Vehicle priority : vehicles) {
            if (!priority.isPriority()) continue;

            Lane priorityLane = priority.getLane();

            // Tinh "phia truoc/phia sau" bang progress tren lane thay vi
            // khoang cach Euclid tu start. Cach nay dung hon voi lane gap khuc.
            if (priorityLane != null) {
                double priorityProgress = priorityLane.getProgress(priority.getPosition());

                for (Vehicle normal : vehicles) {
                    if (normal.isPriority()) continue;
                    if (normal.getLane() != priorityLane) continue;

                    double normalProgress = priorityLane.getProgress(normal.getPosition());
                    double progressGap = normalProgress - priorityProgress;

                    // Xe uu tien dang o sau va sap bat kip xe thuong.
                    if (progressGap > 0 && progressGap < SAME_LANE_YIELD_DIST) {
                        normal.setYieldMode(Vehicle.YieldMode.RUSH);
                    }
                }
            }

            // Xu ly xung dot o nga tu: xe thuong trong vung nguy hiem phai dung.
            for (Intersection intersection : intersections) {
                double distPrioToCenter = MathUtils.distance(
                    priority.getPosition(), intersection.getCenter());

                if (distPrioToCenter < INTERSECTION_DANGER) {
                    for (Vehicle normal : vehicles) {
                        if (normal.isPriority()) continue;
                        if (normal.getLane() == null) continue;
                        if (normal.getLane() == priorityLane) continue;
                        if (!intersection.getLanes().contains(normal.getLane())) continue;

                        double distNormalToCenter = MathUtils.distance(
                            normal.getPosition(), intersection.getCenter());

                        if (distNormalToCenter < INTERSECTION_DANGER) {
                            normal.setYieldMode(Vehicle.YieldMode.STOP);
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Getters. Van tra ve List goc de giu tuong thich voi UI hien co.
    // Nen uu tien dung clearLights()/clearIntersections() khi xoa map.
    // ---------------------------------------------------------------------

    public List<Vehicle>      getVehicles()      { return vehicles;      }
    public List<TrafficLight> getLights()        { return lights;        }
    public List<Intersection> getIntersections() { return intersections; }
}
