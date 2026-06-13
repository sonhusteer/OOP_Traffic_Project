package com.traffic.core;

import com.traffic.map.Intersection;
import com.traffic.map.Lane;
import com.traffic.map.LaneControlPoint;
import com.traffic.map.TrafficLight;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Owns intersection entry/turn decisions. This keeps TrafficEngine small and
 * prevents turn logic from fighting SideShiftPlanner/EmergencyManager.
 */
public class TurnCoordinator {

    private static final double TURN_TRIGGER_DISTANCE = 66.0;
    private static final double RIGHT_TURN_TRIGGER_DISTANCE = 88.0;
    private static final double STRAIGHT_CROSSING_DISTANCE = 48.0;
    private static final double CLEAR_RESET_DISTANCE = 128.0;
    private static final double CONFLICT_RADIUS = 52.0;
    private static final double EXIT_DISTANCE_MIN = 48.0;
    private static final double EXIT_DISTANCE_MAX = 92.0;
    private static final double ENTRY_FRONT_GAP = 76.0;
    private static final double ENTRY_REAR_GAP = 44.0;
    private static final double PREFERRED_OFFSET_TOLERANCE = 6.0;

    private final IntersectionOccupancy occupancy = new IntersectionOccupancy();

    public void updateBeforeDrivers(List<Vehicle> vehicles, List<Intersection> intersections) {
        if (vehicles == null || intersections == null || intersections.isEmpty()) {
            return;
        }

        for (Vehicle vehicle : new ArrayList<>(vehicles)) {
            if (vehicle == null || vehicle.getLane() == null) {
                continue;
            }
            resetOldIntersectionMarker(vehicle);
            updateIntersectionState(vehicle, intersections);
        }

        for (Vehicle vehicle : new ArrayList<>(vehicles)) {
            if (vehicle == null || vehicle.getLane() == null || vehicle.isTurning()) {
                continue;
            }
            tryStartTurn(vehicle, vehicles, intersections);
        }
    }

    private void resetOldIntersectionMarker(Vehicle vehicle) {
        Intersection last = vehicle.getLastIntersectionTurned();
        if (last == null) {
            return;
        }
        if (MathUtils.distance(vehicle.getPosition(), last.getCenter()) > CLEAR_RESET_DISTANCE) {
            vehicle.setLastIntersectionTurned(null);
            if (vehicle.getIntersectionManeuverState() == Vehicle.IntersectionManeuverState.EXITING) {
                vehicle.setIntersectionManeuverState(Vehicle.IntersectionManeuverState.NONE);
                vehicle.setCurrentIntersection(null);
            }
        }
    }

    private void updateIntersectionState(Vehicle vehicle, List<Intersection> intersections) {
        if (vehicle.isTurning()) {
            return;
        }

        Intersection best = nearestRelevantIntersection(vehicle, intersections, STRAIGHT_CROSSING_DISTANCE + 44.0);
        if (best == null) {
            if (vehicle.getIntersectionManeuverState() == Vehicle.IntersectionManeuverState.APPROACHING
                    || vehicle.getIntersectionManeuverState() == Vehicle.IntersectionManeuverState.WAITING_BEFORE_INTERSECTION
                    || vehicle.getIntersectionManeuverState() == Vehicle.IntersectionManeuverState.CROSSING_STRAIGHT) {
                vehicle.setIntersectionManeuverState(Vehicle.IntersectionManeuverState.NONE);
                vehicle.setCurrentIntersection(null);
            }
            return;
        }

        Lane lane = vehicle.getLane();
        double conflict = lane.getProgressOf(best.getCenter());
        double front = vehicle.getFrontProgress();
        double rear = vehicle.getRearProgress();
        double start = conflict - CONFLICT_RADIUS;
        double end = conflict + CONFLICT_RADIUS;

        if (rear > end + 18.0) {
            vehicle.setIntersectionManeuverState(Vehicle.IntersectionManeuverState.EXITING);
            vehicle.setCurrentIntersection(best);
            vehicle.setLastIntersectionTurned(best);
        } else if (front >= start) {
            vehicle.setIntersectionManeuverState(Vehicle.IntersectionManeuverState.CROSSING_STRAIGHT);
            vehicle.setCurrentIntersection(best);
        } else if (front >= conflict - TURN_TRIGGER_DISTANCE - 24.0) {
            vehicle.setIntersectionManeuverState(Vehicle.IntersectionManeuverState.APPROACHING);
            vehicle.setCurrentIntersection(best);
        }
    }

    private void tryStartTurn(Vehicle vehicle,
                              List<Vehicle> vehicles,
                              List<Intersection> intersections) {
        Lane sourceLane = vehicle.getLane();
        if (sourceLane == null || vehicle.getLastIntersectionTurned() != null) {
            return;
        }
        if (!isLateralStableForIntersection(vehicle)) {
            vehicle.abortLateralManeuverSafely();
            return;
        }
        if (isBlockedByYieldMode(vehicle)) {
            return;
        }

        Intersection intersection = nearestRelevantIntersection(vehicle, intersections,
                vehicle.getTurnDecision() == Vehicle.TurnDecision.RIGHT
                        ? RIGHT_TURN_TRIGGER_DISTANCE
                        : TURN_TRIGGER_DISTANCE);
        if (intersection == null) {
            return;
        }

        double conflict = sourceLane.getProgressOf(intersection.getCenter());
        double distanceToConflict = conflict - vehicle.getFrontProgress();
        if (distanceToConflict < -CONFLICT_RADIUS || distanceToConflict > RIGHT_TURN_TRIGGER_DISTANCE) {
            return;
        }

        if (!canPassTrafficLight(vehicle, sourceLane, conflict)) {
            vehicle.setIntersectionManeuverState(Vehicle.IntersectionManeuverState.WAITING_BEFORE_INTERSECTION);
            vehicle.setCurrentIntersection(intersection);
            return;
        }

        if (!vehicle.isPriority()
                && occupancy.hasPriorityVehicleApproaching(intersection, vehicles)
                && !vehicle.isCommittedToIntersection()) {
            vehicle.setYieldMode(Vehicle.YieldMode.STOP_BEFORE_CONFLICT);
            vehicle.setIntersectionManeuverState(Vehicle.IntersectionManeuverState.WAITING_BEFORE_INTERSECTION);
            vehicle.setCurrentIntersection(intersection);
            return;
        }

        Vehicle.TurnDecision requested = vehicle.getTurnDecision();
        Lane targetLane = findTargetLane(sourceLane, requested, intersection);
        Vehicle.TurnDecision effectiveDecision = requested;

        if (targetLane == null && requested != Vehicle.TurnDecision.STRAIGHT) {
            targetLane = findTargetLane(sourceLane, Vehicle.TurnDecision.STRAIGHT, intersection);
            effectiveDecision = Vehicle.TurnDecision.STRAIGHT;
        }
        if (targetLane == null) {
            for (Vehicle.TurnDecision fallback : Vehicle.TurnDecision.values()) {
                targetLane = findTargetLane(sourceLane, fallback, intersection);
                if (targetLane != null) {
                    effectiveDecision = fallback;
                    break;
                }
            }
        }
        if (targetLane == null) {
            vehicle.setLastIntersectionTurned(intersection);
            return;
        }

        if (targetLane == sourceLane && effectiveDecision == Vehicle.TurnDecision.STRAIGHT) {
            // No Bezier needed. The straight crossing is still marked so emergency
            // rules do not stop the car in the middle of the intersection.
            vehicle.setIntersectionManeuverState(Vehicle.IntersectionManeuverState.CROSSING_STRAIGHT);
            vehicle.setCurrentIntersection(intersection);
            return;
        }

        double targetConflict = targetLane.getProgressOf(intersection.getCenter());
        double exitDistance = MathUtils.clamp(
                MathUtils.distance(vehicle.getPosition(), intersection.getCenter()),
                EXIT_DISTANCE_MIN,
                EXIT_DISTANCE_MAX);
        double targetProgress = MathUtils.clamp(targetConflict + exitDistance, 0.0, targetLane.getLength());
        double targetOffset = chooseEntryOffset(vehicle, targetLane, targetProgress);
        if (Double.isNaN(targetOffset)) {
            return;
        }

        if (!occupancy.canEnterIntersection(vehicle, intersection, targetLane,
                targetProgress, targetOffset, vehicles)) {
            vehicle.setIntersectionManeuverState(Vehicle.IntersectionManeuverState.WAITING_BEFORE_INTERSECTION);
            vehicle.setCurrentIntersection(intersection);
            return;
        }

        Vector2D p0 = new Vector2D(vehicle.getPosition().getX(), vehicle.getPosition().getY());
        Vector2D p1 = new Vector2D(intersection.getCenter().getX(), intersection.getCenter().getY());
        Vector2D p2 = targetLane.getPositionAt(targetProgress, targetOffset);

        vehicle.startTurn(new TurnManeuver(
                sourceLane,
                targetLane,
                intersection,
                effectiveDecision,
                p0,
                p1,
                p2,
                targetOffset,
                targetProgress
        ));
    }

    private boolean isLateralStableForIntersection(Vehicle vehicle) {
        if (vehicle == null) return false;
        if (vehicle.isOvertaking()
                || vehicle.getManeuverState() == Vehicle.ManeuverState.GAP_FILLING
                || vehicle.getManeuverState() == Vehicle.ManeuverState.YIELDING_RIGHT
                || vehicle.getManeuverState() == Vehicle.ManeuverState.URGENT_CLEARING) {
            return false;
        }
        return vehicle.isNearPreferredLateralOffset(PREFERRED_OFFSET_TOLERANCE);
    }

    private boolean isBlockedByYieldMode(Vehicle vehicle) {
        return switch (vehicle.getYieldMode()) {
            case STOP_BEFORE_CONFLICT, STOP,
                 HOLD_POSITION, BLOCKED_YIELD,
                 YIELD_RIGHT, PULL_RIGHT,
                 CLEAR_PATH, URGENT_CLEAR_PATH -> true;
            default -> false;
        };
    }

    private boolean canPassTrafficLight(Vehicle vehicle, Lane lane, double conflictProgress) {
        if (vehicle == null || lane == null || vehicle.isPriority()) {
            return true;
        }
        LaneControlPoint control = lane.getNextControlPoint(vehicle.getFrontProgress());
        if (control == null || control.getLight() == null) {
            return true;
        }
        TrafficLight light = control.getLight();
        double distanceToStop = control.getProgress() - vehicle.getFrontProgress();
        if (distanceToStop < -3.0) {
            return true;
        }
        if (control.getProgress() > conflictProgress + 10.0) {
            return true;
        }
        return !(light.isRed() || light.isYellow());
    }

    private Intersection nearestRelevantIntersection(Vehicle vehicle,
                                                     List<Intersection> intersections,
                                                     double lookAhead) {
        if (vehicle == null || vehicle.getLane() == null) {
            return null;
        }
        Lane lane = vehicle.getLane();
        return intersections.stream()
                .filter(ix -> ix != null && ix.getLanes().contains(lane))
                .map(ix -> new IntersectionDistance(ix, lane.getProgressOf(ix.getCenter()) - vehicle.getFrontProgress()))
                .filter(d -> d.distance >= -CONFLICT_RADIUS && d.distance <= lookAhead)
                .min(Comparator.comparingDouble(d -> Math.abs(d.distance)))
                .map(d -> d.intersection)
                .orElse(null);
    }

    private record IntersectionDistance(Intersection intersection, double distance) {}

    private Lane findTargetLane(Lane currentLane,
                                Vehicle.TurnDecision decision,
                                Intersection intersection) {
        if (currentLane == null || intersection == null || decision == null) {
            return null;
        }

        Lane explicit = currentLane.getTurnTarget(intersection, decision);
        if (explicit != null) {
            return explicit;
        }

        double currentConflict = currentLane.getProgressOf(intersection.getCenter());
        if (decision == Vehicle.TurnDecision.STRAIGHT) {
            if (currentConflict < currentLane.getLength() - 35.0) {
                return currentLane;
            }
            return bestOutgoingLaneByAngle(currentLane, intersection, 0.0, 42.0);
        }

        double desired = decision == Vehicle.TurnDecision.LEFT ? -90.0 : 90.0;
        return bestOutgoingLaneByAngle(currentLane, intersection, desired, 55.0);
    }

    private Lane bestOutgoingLaneByAngle(Lane currentLane,
                                         Intersection intersection,
                                         double desiredDelta,
                                         double tolerance) {
        double currentProgress = currentLane.getProgressOf(intersection.getCenter());
        double currentAngle = currentLane.getAngleAt(currentProgress);
        Lane best = null;
        double bestScore = Double.MAX_VALUE;
        for (Lane candidate : intersection.getLanes()) {
            if (candidate == null || candidate == currentLane || !candidate.isUsableForSpawn()) {
                continue;
            }
            if (!isOutgoingFromIntersection(candidate, intersection)) {
                continue;
            }
            double candidateProgress = candidate.getProgressOf(intersection.getCenter());
            double candidateAngle = candidate.getAngleAt(candidateProgress);
            double delta = normalizeAngle(candidateAngle - currentAngle);
            double score = Math.abs(normalizeAngle(delta - desiredDelta));
            if (score <= tolerance && score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private boolean isOutgoingFromIntersection(Lane lane, Intersection intersection) {
        if (lane == null || intersection == null) return false;
        double startDist = MathUtils.distance(lane.getStart(), intersection.getCenter());
        double endDist = MathUtils.distance(lane.getEnd(), intersection.getCenter());
        if (startDist <= endDist + 12.0) {
            return true;
        }
        double progress = lane.getProgressOf(intersection.getCenter());
        return progress < lane.getLength() * 0.35;
    }

    private double chooseEntryOffset(Vehicle vehicle, Lane targetLane, double targetProgress) {
        if (vehicle == null || targetLane == null) {
            return Double.NaN;
        }
        List<Double> candidates = new ArrayList<>();
        double preferred = targetLane.clampOffset(vehicle, vehicle.getPreferredLateralOffset());
        candidates.add(preferred);
        if (Math.abs(preferred - Vehicle.LEFT_OFFSET) < Math.abs(preferred - Vehicle.RIGHT_OFFSET)) {
            candidates.add(Vehicle.LEFT_OFFSET);
            candidates.add(Vehicle.RIGHT_OFFSET);
        } else {
            candidates.add(Vehicle.RIGHT_OFFSET);
            candidates.add(Vehicle.LEFT_OFFSET);
        }
        candidates.add(Vehicle.CENTER_OFFSET);

        for (double raw : candidates) {
            double offset = targetLane.clampOffset(vehicle, raw);
            if (targetLane.occupancy().isSpaceFreeAt(
                    targetProgress, offset, vehicle, ENTRY_FRONT_GAP, ENTRY_REAR_GAP)) {
                return offset;
            }
        }
        return Double.NaN;
    }

    private static double normalizeAngle(double angle) {
        double result = angle;
        while (result <= -180.0) result += 360.0;
        while (result > 180.0) result -= 360.0;
        return result;
    }
}
