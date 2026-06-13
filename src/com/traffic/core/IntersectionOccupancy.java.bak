package com.traffic.core;

import com.traffic.map.Intersection;
import com.traffic.map.Lane;
import java.util.List;

/**
 * Lightweight intersection gatekeeper. LaneOccupancy is excellent while cars
 * are on a lane; this class covers the short period where a car has committed
 * to the intersection and may be crossing/turning between lanes.
 */
public class IntersectionOccupancy {

    private static final double CENTER_CONFLICT_RADIUS = 58.0;
    private static final double APPROACH_HORIZON = 170.0;
    private static final double PRIORITY_HORIZON = 230.0;
    private static final double ENTRY_FRONT_GAP = 72.0;
    private static final double ENTRY_REAR_GAP = 42.0;

    public boolean canEnterIntersection(Vehicle vehicle,
                                        Intersection intersection,
                                        Lane targetLane,
                                        double targetProgress,
                                        double targetOffset,
                                        List<Vehicle> vehicles) {
        if (vehicle == null || intersection == null || vehicles == null) {
            return false;
        }

        if (targetLane != null && !targetLane.occupancy().isSpaceFreeAt(
                targetProgress, targetOffset, vehicle, ENTRY_FRONT_GAP, ENTRY_REAR_GAP)) {
            return false;
        }

        for (Vehicle other : vehicles) {
            if (other == null || other == vehicle || other.getLane() == null) {
                continue;
            }

            if (vehicle.isPriority() && !other.isPriority()) {
                // Priority vehicles are still blocked by physical overlap, but
                // not by normal vehicles that are merely approaching and can stop.
                if (other.isCommittedToIntersection()
                        && isVehicleInIntersection(other, intersection, CENTER_CONFLICT_RADIUS * 1.25)) {
                    return false;
                }
                continue;
            }

            if (!vehicle.isPriority() && other.isPriority()
                    && priorityVehicleControlsIntersection(other, intersection)) {
                return false;
            }

            if (other.isCommittedToIntersection()
                    && isVehicleInIntersection(other, intersection, CENTER_CONFLICT_RADIUS)) {
                return false;
            }

            if (isLikelyConflictingApproach(vehicle, other, intersection)) {
                return false;
            }
        }
        return true;
    }

    public boolean hasPriorityVehicleApproaching(Intersection intersection,
                                                 List<Vehicle> vehicles) {
        if (intersection == null || vehicles == null) {
            return false;
        }
        for (Vehicle vehicle : vehicles) {
            if (vehicle != null && vehicle.isPriority()
                    && priorityVehicleControlsIntersection(vehicle, intersection)) {
                return true;
            }
        }
        return false;
    }

    public boolean isVehicleInIntersection(Vehicle vehicle,
                                           Intersection intersection,
                                           double radius) {
        if (vehicle == null || intersection == null) {
            return false;
        }
        if (vehicle.getActiveTurn() != null
                && vehicle.getActiveTurn().getIntersection() == intersection) {
            return true;
        }
        if (vehicle.getCurrentIntersection() == intersection
                && vehicle.isCommittedToIntersection()) {
            return true;
        }
        return MathUtils.distance(vehicle.getPosition(), intersection.getCenter()) <= radius;
    }

    public boolean priorityVehicleControlsIntersection(Vehicle priority,
                                                       Intersection intersection) {
        if (priority == null || !priority.isPriority() || priority.getLane() == null
                || intersection == null || !intersection.getLanes().contains(priority.getLane())) {
            return false;
        }
        if (priority.getActiveTurn() != null
                && priority.getActiveTurn().getIntersection() == intersection) {
            return true;
        }
        Lane lane = priority.getLane();
        double conflict = lane.getProgressOf(intersection.getCenter());
        double distanceToConflict = conflict - priority.getFrontProgress();
        boolean approaching = distanceToConflict <= PRIORITY_HORIZON;
        boolean notCleared = priority.getRearProgress() <= conflict + CENTER_CONFLICT_RADIUS + 32.0;
        return approaching && notCleared;
    }

    private boolean isLikelyConflictingApproach(Vehicle vehicle,
                                                Vehicle other,
                                                Intersection intersection) {
        if (vehicle == null || other == null || intersection == null
                || vehicle.getLane() == null || other.getLane() == null) {
            return false;
        }
        if (!intersection.getLanes().contains(vehicle.getLane())
                || !intersection.getLanes().contains(other.getLane())) {
            return false;
        }
        if (vehicle.getLane() == other.getLane()) {
            return false;
        }

        double myConflict = vehicle.getLane().getProgressOf(intersection.getCenter());
        double otherConflict = other.getLane().getProgressOf(intersection.getCenter());
        double myDistance = myConflict - vehicle.getFrontProgress();
        double otherDistance = otherConflict - other.getFrontProgress();
        if (myDistance < -CENTER_CONFLICT_RADIUS || otherDistance < -CENTER_CONFLICT_RADIUS) {
            return false;
        }
        if (myDistance > APPROACH_HORIZON || otherDistance > APPROACH_HORIZON) {
            return false;
        }

        double myAngle = vehicle.getLane().getAngleAt(myConflict);
        double otherAngle = other.getLane().getAngleAt(otherConflict);
        double diff = Math.abs(myAngle - otherAngle);
        if (diff > 180.0) diff = 360.0 - diff;

        // Parallel/opposite directions are less likely to cross in the same center
        // point unless one vehicle is turning left. Perpendicular/diagonal flows are
        // treated conservatively.
        boolean crossing = Math.abs(Math.cos(Math.toRadians(diff))) < 0.70;
        if (crossing) return true;

        return vehicle.getTurnDecision() == Vehicle.TurnDecision.LEFT
                && other.getTurnDecision() == Vehicle.TurnDecision.STRAIGHT
                && otherDistance < APPROACH_HORIZON * 0.75;
    }
}
