package com.traffic.core;

import com.traffic.map.Intersection;
import com.traffic.map.TrafficLight;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class TrafficEngine {

    private final List<Vehicle>      vehicles      = new ArrayList<>();
    private final List<TrafficLight> lights        = new ArrayList<>();
    private final List<Intersection> intersections = new ArrayList<>();
    private final EmergencyManager   emergencyManager = new EmergencyManager();
    private final TurnCoordinator    turnCoordinator = new TurnCoordinator();
    private final Map<Vehicle, Double> intersectionWaitSeconds = new IdentityHashMap<>();
    private IRenderer renderer;

    private static final double DEADLOCK_RELEASE_SECONDS = 2.2;
    private static final double DEADLOCK_RELEASE_SPEED = 24.0;

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
        intersectionWaitSeconds.remove(v);
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
        intersectionWaitSeconds.clear();
        for (Intersection intersection : intersections) {
            for (var lane : intersection.getLanes()) {
                lane.clearReservations();
            }
        }
    }

    public void setRenderer(IRenderer renderer) { this.renderer = renderer; }

    public void tick(double deltaTime) {
        TrafficDebug.beginTick();
        updateLights(deltaTime);
        PriorityRouteAnalyzer priorityRoutes = PriorityRouteAnalyzer.analyze(vehicles, intersections);
        emergencyManager.update(vehicles, intersections, priorityRoutes);
        turnCoordinator.updateBeforeDrivers(vehicles, intersections, priorityRoutes);
        logPreparingTurnSlotStalls();
        releaseStaleIntersectionDeadlock(deltaTime);
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


    private void logPreparingTurnSlotStalls() {
        for (Vehicle vehicle : new ArrayList<>(vehicles)) {
            if (vehicle == null) continue;
            Vehicle.IntersectionManeuverState state = vehicle.getIntersectionManeuverState();
            if ((state == Vehicle.IntersectionManeuverState.PREPARING_TURN_SLOT
                    || state == Vehicle.IntersectionManeuverState.PREPARING_TURN_SLOT_PAUSED)
                    && vehicle.getSpeed() < 2.0) {
                boolean stable = !Vehicle.isActiveLateralManeuverState(vehicle.getManeuverState())
                        && vehicle.isNearPreferredLateralOffset(6.0);
                TrafficDebug.logPreparingStuck(vehicle, stable);
            }
        }
    }

    private void updateVehicles(double deltaTime, PriorityRouteAnalyzer priorityRoutes) {
        List<Vehicle> toRemove = new ArrayList<>();
        for (Vehicle vehicle : new ArrayList<>(vehicles)) {
            TrafficLight targetLight = vehicle.getLane() != null
                    ? vehicle.getLane().getNextLight(vehicle.getFrontProgress())
                    : null;

            vehicle.makeDecision(targetLight, deltaTime);
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
        intersectionWaitSeconds.keySet().removeIf(v -> v == null || !vehicles.contains(v));
    }

    private void releaseStaleIntersectionDeadlock(double deltaTime) {
        Vehicle selected = null;
        double selectedWait = 0.0;

        for (Vehicle vehicle : new ArrayList<>(vehicles)) {
            if (vehicle == null) continue;
            if (vehicle.getIntersectionManeuverState() != Vehicle.IntersectionManeuverState.WAITING_BEFORE_INTERSECTION
                    || !isDeadlockReleaseReason(vehicle.getTurnWaitReason())) {
                intersectionWaitSeconds.remove(vehicle);
                continue;
            }

            double waited = intersectionWaitSeconds.getOrDefault(vehicle, 0.0) + Math.max(0.0, deltaTime);
            intersectionWaitSeconds.put(vehicle, waited);
            if (waited >= DEADLOCK_RELEASE_SECONDS && waited > selectedWait) {
                selected = vehicle;
                selectedWait = waited;
            }
        }

        if (selected == null) {
            return;
        }

        // This is a last-resort airbag for circular target-lane waits. It only
        // applies to vehicles already stuck at the intersection gate, never to
        // red-light or priority-conflict waits.
        selected.clearPriorityYieldLock("DEADLOCK_FORCED_RELEASE");
        selected.setYieldMode(Vehicle.YieldMode.CLEAR_CONFLICT);
        selected.setManeuverState(Vehicle.ManeuverState.CLEARING_CONFLICT);
        selected.setIntersectionManeuverState(Vehicle.IntersectionManeuverState.CLEARING_FOR_PRIORITY);
        selected.setTurnWaitReason("DEADLOCK_FORCED_RELEASE");
        selected.setSpeed(Math.max(selected.getSpeed(), DEADLOCK_RELEASE_SPEED));
        intersectionWaitSeconds.remove(selected);
    }

    private boolean isDeadlockReleaseReason(String reason) {
        if (reason == null || reason.isBlank()) return false;
        return reason.startsWith("TARGET_ENTRY_FULL")
                || reason.startsWith("INTERSECTION_BUSY")
                || reason.startsWith("NO_AVAILABLE_TURN")
                || reason.startsWith("WAIT_TURN_SLOT")
                || reason.startsWith("TURN_SLOT_BLOCKED")
                || reason.startsWith("STABILIZE_SLOT");
    }

    public List<Vehicle>      getVehicles()      { return vehicles; }
    public List<TrafficLight> getLights()        { return lights; }
    public List<Intersection> getIntersections() { return intersections; }
}
