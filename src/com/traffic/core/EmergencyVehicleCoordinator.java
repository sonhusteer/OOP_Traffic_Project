package com.traffic.core;

import com.traffic.map.Intersection;
import java.util.List;

/**
 * @deprecated Use {@link EmergencyManager}. This wrapper is kept only so older
 * UI/tests that instantiate EmergencyVehicleCoordinator still compile.
 */
@Deprecated
public final class EmergencyVehicleCoordinator {

    private final EmergencyManager delegate = new EmergencyManager();

    /**
     * @deprecated Use {@link EmergencyManager#update(List, List)} instead.
     */
    @Deprecated
    public void apply(List<Vehicle> vehicles, List<Intersection> intersections) {
        delegate.update(vehicles, intersections);
    }
}
