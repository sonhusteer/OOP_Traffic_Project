package com.traffic.core;

import com.traffic.map.Intersection;
import java.util.List;

/**
 * Legacy adapter kept so older UI/tests that instantiate
 * EmergencyVehicleCoordinator still compile. New code should use
 * {@link EmergencyManager} directly.
 */
public final class EmergencyVehicleCoordinator {

    private final EmergencyManager delegate = new EmergencyManager();

    /** Delegates to {@link EmergencyManager#update(List, List)}. */
    public void apply(List<Vehicle> vehicles, List<Intersection> intersections) {
        delegate.update(vehicles, intersections);
    }
}
