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

    // Two-stage turn handling:
    // 1) far enough from the junction, LEFT/RIGHT vehicles prepare their
    //    required virtual slot while still rolling;
    // 2) only inside the commit band may they reserve and start the Bezier turn.
    private static final double TURN_PREPARE_DISTANCE = 190.0;
    private static final double TURN_COMMIT_DISTANCE = 62.0;
    private static final double STRAIGHT_CROSSING_DISTANCE = 54.0;
    private static final double CLEAR_RESET_DISTANCE = 128.0;
    private static final double CONFLICT_RADIUS = 46.0;
    private static final double EXIT_DISTANCE_MIN = 58.0;
    private static final double EXIT_DISTANCE_MAX = 108.0;
    private static final double ENTRY_FRONT_GAP = 76.0;
    private static final double ENTRY_REAR_GAP = 44.0;
    private static final double PREFERRED_OFFSET_TOLERANCE = 6.0;
    private static final double WAITING_DISTANCE = 96.0;
    private static final double STRAIGHT_CONTINUATION_MIN = 92.0;
    private static final double TURN_SLOT_TOLERANCE = 5.0;
    private static final double TURN_SLOT_TARGET_TOLERANCE = 7.0;

    private final IntersectionOccupancy occupancy = new IntersectionOccupancy();
    private PriorityRouteAnalyzer priorityRoutes = PriorityRouteAnalyzer.empty();

    public void updateBeforeDrivers(List<Vehicle> vehicles, List<Intersection> intersections) {
        updateBeforeDrivers(vehicles, intersections, PriorityRouteAnalyzer.getCurrent());
    }

    public void updateBeforeDrivers(List<Vehicle> vehicles, List<Intersection> intersections,
                                    PriorityRouteAnalyzer priorityRoutes) {
        this.priorityRoutes = priorityRoutes == null ? PriorityRouteAnalyzer.empty() : priorityRoutes;
        if (vehicles == null || intersections == null || intersections.isEmpty()) {
            return;
        }

        // Reset old markers first. Do NOT mark a vehicle as CROSSING_STRAIGHT
        // before its requested turn has been evaluated; doing so makes LEFT/RIGHT
        // vehicles become "committed" too early and they simply drive straight.
        for (Vehicle vehicle : new ArrayList<>(vehicles)) {
            if (vehicle == null || vehicle.getLane() == null) {
                continue;
            }
            resetOldIntersectionMarker(vehicle);
        }

        // Start/queue intersection movements before the generic zone update.
        for (Vehicle vehicle : new ArrayList<>(vehicles)) {
            if (vehicle == null || vehicle.getLane() == null || vehicle.isTurning()) {
                continue;
            }
            tryStartTurn(vehicle, vehicles, intersections);
        }

        // Finally refresh visual/driver state for vehicles that did not just start
        // an active turn.
        for (Vehicle vehicle : new ArrayList<>(vehicles)) {
            if (vehicle == null || vehicle.getLane() == null) {
                continue;
            }
            updateIntersectionState(vehicle, intersections);
        }
    }

    private void resetOldIntersectionMarker(Vehicle vehicle) {
        Intersection last = vehicle.getLastIntersectionTurned();
        if (last == null) {
            return;
        }
        if (MathUtils.distance(vehicle.getPosition(), last.getCenter()) > CLEAR_RESET_DISTANCE) {
            vehicle.setLastIntersectionTurned(null);
            vehicle.setTurnWaitReason(null);
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
                    || vehicle.getIntersectionManeuverState() == Vehicle.IntersectionManeuverState.PREPARING_TURN_SLOT
                    || vehicle.getIntersectionManeuverState() == Vehicle.IntersectionManeuverState.WAITING_BEFORE_INTERSECTION
                    || vehicle.getIntersectionManeuverState() == Vehicle.IntersectionManeuverState.CROSSING_STRAIGHT) {
                vehicle.setIntersectionManeuverState(Vehicle.IntersectionManeuverState.NONE);
                vehicle.setCurrentIntersection(null);
                vehicle.setTurnWaitReason(null);
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
            vehicle.setTurnWaitReason(null);
        } else if (front >= start) {
            if (vehicle.getIntersectionManeuverState() == Vehicle.IntersectionManeuverState.WAITING_BEFORE_INTERSECTION) {
                vehicle.setCurrentIntersection(best);
                return;
            }
            if (shouldTreatAsStraightCrossing(vehicle, lane, best)) {
                vehicle.setIntersectionManeuverState(Vehicle.IntersectionManeuverState.CROSSING_STRAIGHT);
                vehicle.setCurrentIntersection(best);
                vehicle.setTurnWaitReason(null);
            } else if (!vehicle.isTurning()) {
                // A LEFT/RIGHT vehicle reached the intersection edge before it could
                // obtain a reservation. Stop before entering instead of silently
                // converting it into a straight crossing.
                waitBeforeIntersection(vehicle, best, "WAIT_TURN_SLOT");
            }
        } else if (front >= conflict - TURN_PREPARE_DISTANCE) {
            if (vehicle.getIntersectionManeuverState() != Vehicle.IntersectionManeuverState.WAITING_BEFORE_INTERSECTION
                    && vehicle.getIntersectionManeuverState() != Vehicle.IntersectionManeuverState.PREPARING_TURN_SLOT) {
                vehicle.setIntersectionManeuverState(Vehicle.IntersectionManeuverState.APPROACHING);
            }
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

        // Scan a longer distance for all vehicles so T-junction STRAIGHT intents
        // can be normalized into LEFT/RIGHT early, and turning vehicles can move
        // into the correct virtual slot without stopping at the last frame.
        Intersection intersection = nearestRelevantIntersection(
                vehicle, intersections, TURN_PREPARE_DISTANCE);
        if (intersection == null) {
            return;
        }

        double conflict = sourceLane.getProgressOf(intersection.getCenter());
        double distanceToConflict = conflict - vehicle.getFrontProgress();
        if (distanceToConflict < -CONFLICT_RADIUS || distanceToConflict > TURN_PREPARE_DISTANCE) {
            return;
        }

        Vehicle.TurnDecision requested = normalizeDecisionByMapRule(
                vehicle, sourceLane, intersection, vehicle.getTurnDecision());
        if (vehicle.getTurnDecision() != requested) {
            vehicle.setTurnDecision(requested);
        }

        boolean isPhysicalTurn = requested == Vehicle.TurnDecision.LEFT
                || requested == Vehicle.TurnDecision.RIGHT;

        // Priority yielding always wins over turn-slot preparation. Without this
        // guard a car that was pulled right for an ambulance could immediately
        // rewrite its target back to the left-turn slot in the same tick.
        if (handlePriorityYieldBeforeTurn(vehicle, sourceLane, intersection, requested, distanceToConflict)) {
            return;
        }

        // Stage 1: prepare the correct side slot while still rolling. This is
        // the key fix for the one-frame stutter: preparation must not be treated
        // as WAITING_BEFORE_INTERSECTION.
        if (isPhysicalTurn && distanceToConflict > TURN_COMMIT_DISTANCE) {
            if (!isInRequiredTurnSlot(vehicle, sourceLane, requested)) {
                prepareVehicleForTightTurnSlot(vehicle, sourceLane, intersection, requested, false);
            } else if (vehicle.getIntersectionManeuverState()
                    == Vehicle.IntersectionManeuverState.PREPARING_TURN_SLOT) {
                vehicle.setIntersectionManeuverState(Vehicle.IntersectionManeuverState.APPROACHING);
                vehicle.setTurnWaitReason(null);
            }
            return;
        }

        // Straight vehicles should only be committed when they are close enough
        // to the conflict band. Farther away, the normal driver keeps control.
        if (!isPhysicalTurn && distanceToConflict > STRAIGHT_CROSSING_DISTANCE) {
            return;
        }

        // Stage 2: inside the commit band. A turn from the wrong slot would be a
        // wide looping turn across the intersection, so it must now stop and wait
        // instead of starting a bad curve.
        if (!isInRequiredTurnSlot(vehicle, sourceLane, requested)) {
            prepareVehicleForTightTurnSlot(vehicle, sourceLane, intersection, requested, true);
            return;
        }

        if (!isLateralStableForIntersection(vehicle)) {
            vehicle.abortLateralManeuverSafely();
            waitBeforeIntersection(vehicle, intersection, "STABILIZE_SLOT");
            return;
        }
        if (isBlockedByYieldMode(vehicle)) {
            if (distanceToConflict <= WAITING_DISTANCE) {
                waitBeforeIntersection(vehicle, intersection, "YIELDING");
            }
            return;
        }

        if (!canPassTrafficLight(vehicle, sourceLane, conflict)) {
            waitBeforeIntersection(vehicle, intersection, "TRAFFIC_LIGHT");
            return;
        }

        if (!vehicle.isPriority()
                && priorityRoutes.hasBlockingPriorityFor(vehicle, intersection)
                && !vehicle.isCommittedToIntersection()) {
            vehicle.setYieldMode(Vehicle.YieldMode.STOP_BEFORE_CONFLICT);
            waitBeforeIntersection(vehicle, intersection, "PRIORITY_ROUTE_CONFLICT");
            return;
        }

        TurnCandidate candidate = chooseTurnCandidate(vehicle, vehicles, sourceLane, intersection, requested);
        if (candidate == null) {
            String reason = vehicle.getTurnWaitReason();
            waitBeforeIntersection(vehicle, intersection,
                    reason != null && !reason.isBlank() ? reason : "NO_AVAILABLE_TURN");
            return;
        }

        Lane targetLane = candidate.targetLane();
        Vehicle.TurnDecision effectiveDecision = candidate.decision();

        // When a lane has no straight continuation, a STRAIGHT request is
        // normalized into the available turn. Updating the stored decision makes
        // the on-car badge immediately show L/R instead of a misleading S.
        if (vehicle.getTurnDecision() != effectiveDecision) {
            vehicle.setTurnDecision(effectiveDecision);
        }

        if (targetLane == sourceLane && effectiveDecision == Vehicle.TurnDecision.STRAIGHT) {
            vehicle.setIntersectionManeuverState(Vehicle.IntersectionManeuverState.CROSSING_STRAIGHT);
            vehicle.setCurrentIntersection(intersection);
            vehicle.setTurnWaitReason(null);
            return;
        }

        double targetProgress = candidate.targetProgress();
        double targetOffset = candidate.targetOffset();

        TurnManeuver maneuver = buildCubicTurn(
                vehicle,
                sourceLane,
                targetLane,
                intersection,
                effectiveDecision,
                targetProgress,
                targetOffset
        );
        vehicle.startTurn(maneuver);
        vehicle.setTurnWaitReason(null);
    }

    private Vehicle.TurnDecision normalizeDecisionByMapRule(Vehicle vehicle,
                                                            Lane lane,
                                                            Intersection intersection,
                                                            Vehicle.TurnDecision requested) {
        Vehicle.TurnDecision safe = requested == null
                ? Vehicle.TurnDecision.STRAIGHT
                : requested;
        if (lane == null) return safe;
        if (lane.isStraightOnly()) return Vehicle.TurnDecision.STRAIGHT;
        if (!lane.hasAnyTurnRule(intersection)) return safe;

        if (lane.hasTurnRule(intersection, safe)
                && lane.getTurnTarget(intersection, safe) != null) {
            return safe;
        }

        boolean canStraight = lane.hasTurnRule(intersection, Vehicle.TurnDecision.STRAIGHT)
                && lane.getTurnTarget(intersection, Vehicle.TurnDecision.STRAIGHT) != null;
        boolean canLeft = lane.hasTurnRule(intersection, Vehicle.TurnDecision.LEFT)
                && lane.getTurnTarget(intersection, Vehicle.TurnDecision.LEFT) != null;
        boolean canRight = lane.hasTurnRule(intersection, Vehicle.TurnDecision.RIGHT)
                && lane.getTurnTarget(intersection, Vehicle.TurnDecision.RIGHT) != null;

        if (safe == Vehicle.TurnDecision.STRAIGHT || safe == null) {
            if (canStraight) return Vehicle.TurnDecision.STRAIGHT;
            if (vehicle != null) {
                double left = lane.getLeftmostOffset(vehicle);
                double right = lane.getRightmostOffset(vehicle);
                boolean closerLeft = Math.abs(vehicle.getLateralOffset() - left)
                        <= Math.abs(vehicle.getLateralOffset() - right);
                if (closerLeft && canLeft) return Vehicle.TurnDecision.LEFT;
                if (!closerLeft && canRight) return Vehicle.TurnDecision.RIGHT;
            }
            if (canLeft) return Vehicle.TurnDecision.LEFT;
            if (canRight) return Vehicle.TurnDecision.RIGHT;
        }

        if (safe == Vehicle.TurnDecision.LEFT) {
            if (canStraight) return Vehicle.TurnDecision.STRAIGHT;
            if (canRight) return Vehicle.TurnDecision.RIGHT;
        }
        if (safe == Vehicle.TurnDecision.RIGHT) {
            if (canStraight) return Vehicle.TurnDecision.STRAIGHT;
            if (canLeft) return Vehicle.TurnDecision.LEFT;
        }

        if (canStraight) return Vehicle.TurnDecision.STRAIGHT;
        if (canLeft) return Vehicle.TurnDecision.LEFT;
        if (canRight) return Vehicle.TurnDecision.RIGHT;
        return Vehicle.TurnDecision.STRAIGHT;
    }

    private boolean isInRequiredTurnSlot(Vehicle vehicle,
                                         Lane lane,
                                         Vehicle.TurnDecision decision) {
        if (vehicle == null || lane == null || decision == null) return true;
        if (decision == Vehicle.TurnDecision.STRAIGHT) return true;
        double required = requiredSourceOffsetForTurn(vehicle, lane, decision);
        return Math.abs(vehicle.getLateralOffset() - required) <= TURN_SLOT_TOLERANCE
                && Math.abs(vehicle.getTargetLateralOffset() - required) <= TURN_SLOT_TARGET_TOLERANCE;
    }

    private double requiredSourceOffsetForTurn(Vehicle vehicle,
                                               Lane lane,
                                               Vehicle.TurnDecision decision) {
        if (decision == Vehicle.TurnDecision.LEFT) {
            return lane.getLeftmostOffset(vehicle);
        }
        if (decision == Vehicle.TurnDecision.RIGHT) {
            return lane.getRightmostOffset(vehicle);
        }
        return lane.clampOffset(vehicle, vehicle.getPreferredLateralOffset());
    }

    private void prepareVehicleForTightTurnSlot(Vehicle vehicle,
                                                Lane lane,
                                                Intersection intersection,
                                                Vehicle.TurnDecision decision,
                                                boolean hardWait) {
        if (vehicle == null || lane == null || decision == null
                || decision == Vehicle.TurnDecision.STRAIGHT) {
            return;
        }
        double required = requiredSourceOffsetForTurn(vehicle, lane, decision);
        vehicle.abortLateralManeuverSafely();
        vehicle.setPreferredLateralOffset(required);
        vehicle.setTargetLateralOffset(required);
        vehicle.setCurrentIntersection(intersection);

        if (hardWait) {
            vehicle.setIntersectionManeuverState(
                    Vehicle.IntersectionManeuverState.WAITING_BEFORE_INTERSECTION);
            vehicle.setTurnWaitReason("TURN_SLOT_BLOCKED_" + decision);
            vehicle.setSpeed(0.0);
            return;
        }

        vehicle.setIntersectionManeuverState(
                Vehicle.IntersectionManeuverState.PREPARING_TURN_SLOT);
        vehicle.setTurnWaitReason("PREPARE_" + decision + "_SLOT");
        double rollingSpeed = MathUtils.clamp(vehicle.getSpeed(), 10.0, 24.0);
        vehicle.setSpeed(rollingSpeed);
    }

    private boolean shouldTreatAsStraightCrossing(Vehicle vehicle, Lane lane, Intersection intersection) {
        if (vehicle == null || lane == null || intersection == null) return false;
        Vehicle.TurnDecision decision = vehicle.getTurnDecision();
        if (decision != Vehicle.TurnDecision.STRAIGHT) {
            return false;
        }
        if (lane.hasAnyTurnRule(intersection)) {
            return lane.hasTurnRule(intersection, Vehicle.TurnDecision.STRAIGHT)
                    && lane.getTurnTarget(intersection, Vehicle.TurnDecision.STRAIGHT) == lane;
        }
        double conflict = lane.getProgressOf(intersection.getCenter());
        return lane.getLength() - conflict >= STRAIGHT_CONTINUATION_MIN;
    }

    private void waitBeforeIntersection(Vehicle vehicle, Intersection intersection, String reason) {
        if (vehicle == null) return;
        if (!vehicle.isCommittedToIntersection()) {
            vehicle.setIntersectionManeuverState(Vehicle.IntersectionManeuverState.WAITING_BEFORE_INTERSECTION);
            vehicle.setCurrentIntersection(intersection);
            vehicle.setTurnWaitReason(reason);
        }
    }

    private TurnManeuver buildCubicTurn(Vehicle vehicle,
                                        Lane sourceLane,
                                        Lane targetLane,
                                        Intersection intersection,
                                        Vehicle.TurnDecision decision,
                                        double targetProgress,
                                        double targetOffset) {
        // Start from the current lane-derived position. By the time this is
        // called, the car is inside the commit band and already aligned to the
        // correct virtual slot, so p0 is stable and does not produce a visible
        // snap.
        Vector2D p0 = new Vector2D(vehicle.getPosition().getX(), vehicle.getPosition().getY());
        Vector2D p3 = targetLane.getPositionAt(targetProgress, targetOffset);

        double sourceProgress = sourceLane.getProgressOf(p0);
        Vector2D dirIn = sourceLane.getDirectionAt(sourceProgress);
        Vector2D dirOut = targetLane.getDirectionAt(targetProgress);

        double chord = Math.max(10.0, MathUtils.distance(p0, p3));
        double angleDelta = Math.abs(normalizeAngle(targetLane.getAngleAt(targetProgress)
                - sourceLane.getAngleAt(sourceProgress)));
        double inHandle = handleInFor(vehicle, decision, chord, angleDelta);
        double outHandle = handleOutFor(vehicle, decision, chord, angleDelta);

        Vector2D p1 = new Vector2D(
                p0.getX() + dirIn.getX() * inHandle,
                p0.getY() + dirIn.getY() * inHandle
        );
        Vector2D p2 = new Vector2D(
                p3.getX() - dirOut.getX() * outHandle,
                p3.getY() - dirOut.getY() * outHandle
        );

        return new TurnManeuver(
                sourceLane,
                targetLane,
                intersection,
                decision,
                p0,
                p1,
                p2,
                p3,
                targetOffset,
                targetProgress
        );
    }

    private double exitDistanceFor(Vehicle vehicle,
                                   Vehicle.TurnDecision decision,
                                   double currentDistanceToCenter) {
        double vehicleBonus = vehicle != null ? Math.max(0.0, vehicle.getWidth() - 34.0) * 0.55 : 0.0;
        double base = switch (decision) {
            case RIGHT -> 66.0;
            case LEFT -> 88.0;
            case STRAIGHT -> 64.0;
        };
        if (vehicle != null && vehicle.isPriority()) {
            base += 8.0;
        }
        double desired = Math.max(base, currentDistanceToCenter * 0.70 + base * 0.35 + vehicleBonus);
        return MathUtils.clamp(desired, EXIT_DISTANCE_MIN, EXIT_DISTANCE_MAX + vehicleBonus);
    }

    private double handleInFor(Vehicle vehicle,
                               Vehicle.TurnDecision decision,
                               double chord,
                               double angleDelta) {
        double vehicleBonus = vehicle != null ? Math.max(0.0, vehicle.getWidth() - 34.0) * 0.10 : 0.0;
        double sharpFactor = MathUtils.clamp(angleDelta / 90.0, 0.78, 1.08);
        double factor = switch (decision) {
            case RIGHT -> 0.24;
            case LEFT -> 0.30;
            case STRAIGHT -> 0.16;
        };
        double min = decision == Vehicle.TurnDecision.STRAIGHT ? 14.0 : 24.0;
        double max = switch (decision) {
            case RIGHT -> 48.0;
            case LEFT -> 62.0;
            case STRAIGHT -> 34.0;
        };
        return MathUtils.clamp(chord * factor * sharpFactor + vehicleBonus, min, max + vehicleBonus);
    }

    private double handleOutFor(Vehicle vehicle,
                                Vehicle.TurnDecision decision,
                                double chord,
                                double angleDelta) {
        double vehicleBonus = vehicle != null ? Math.max(0.0, vehicle.getWidth() - 34.0) * 0.12 : 0.0;
        double sharpFactor = MathUtils.clamp(angleDelta / 90.0, 0.78, 1.12);
        double factor = switch (decision) {
            case RIGHT -> 0.31;
            case LEFT -> 0.35;
            case STRAIGHT -> 0.16;
        };
        double min = decision == Vehicle.TurnDecision.STRAIGHT ? 14.0 : 28.0;
        double max = switch (decision) {
            case RIGHT -> 58.0;
            case LEFT -> 74.0;
            case STRAIGHT -> 34.0;
        };
        return MathUtils.clamp(chord * factor * sharpFactor + vehicleBonus, min, max + vehicleBonus);
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

    private boolean handlePriorityYieldBeforeTurn(Vehicle vehicle,
                                                  Lane lane,
                                                  Intersection intersection,
                                                  Vehicle.TurnDecision requested,
                                                  double distanceToConflict) {
        if (vehicle == null || lane == null || intersection == null) {
            return false;
        }

        Vehicle.YieldMode mode = vehicle.getYieldMode();
        if (mode == null || mode == Vehicle.YieldMode.NONE) {
            return false;
        }

        // If the vehicle has already been told to clear the conflict area, never
        // convert that into a left/right turn reservation. It should simply leave
        // the intersection first.
        if (mode == Vehicle.YieldMode.CLEAR_CONFLICT
                || mode == Vehicle.YieldMode.CLEAR_INTERSECTION) {
            vehicle.cancelOvertake();
            vehicle.setIntersectionManeuverState(Vehicle.IntersectionManeuverState.CLEARING_FOR_PRIORITY);
            vehicle.setCurrentIntersection(intersection);
            vehicle.setTurnWaitReason("CLEAR_PRIORITY_FIRST");
            return true;
        }

        // Hard priority stops keep the original turn intent but prevent any new
        // turn preparation/reservation while the priority vehicle controls the
        // junction.
        if (mode == Vehicle.YieldMode.STOP_BEFORE_CONFLICT
                || mode == Vehicle.YieldMode.STOP
                || mode == Vehicle.YieldMode.HOLD_POSITION
                || mode == Vehicle.YieldMode.BLOCKED_YIELD) {
            vehicle.cancelOvertake();
            if (distanceToConflict <= WAITING_DISTANCE) {
                waitBeforeIntersection(vehicle, intersection, "PRIORITY_WAIT_" + mode);
            } else if (vehicle.getIntersectionManeuverState()
                    == Vehicle.IntersectionManeuverState.PREPARING_TURN_SLOT) {
                vehicle.setIntersectionManeuverState(Vehicle.IntersectionManeuverState.APPROACHING);
                vehicle.setTurnWaitReason("PRIORITY_SUSPEND_TURN");
            }
            return true;
        }

        // Side-yield modes are the inconsistent case reported in the demo:
        // a vehicle moves right to open the path, then the turn planner pulls it
        // left for a left turn. While side-yield is active, do not rewrite the
        // turn intent and do not start any turn reservation. The vehicle keeps
        // yielding first; after the priority lock expires, the normal turn logic
        // may prepare the original L/R/S movement again.
        if (isSideYieldMode(mode)) {
            vehicle.cancelOvertake();
            if (distanceToConflict <= WAITING_DISTANCE) {
                waitBeforeIntersection(vehicle, intersection, "YIELD_PRIORITY_BEFORE_TURN");
            } else if (vehicle.getIntersectionManeuverState()
                    == Vehicle.IntersectionManeuverState.PREPARING_TURN_SLOT
                    || vehicle.getIntersectionManeuverState()
                    == Vehicle.IntersectionManeuverState.WAITING_BEFORE_INTERSECTION) {
                vehicle.setIntersectionManeuverState(Vehicle.IntersectionManeuverState.APPROACHING);
                vehicle.setTurnWaitReason("YIELD_PRIORITY_BEFORE_TURN");
            } else {
                vehicle.setTurnWaitReason("YIELD_PRIORITY_BEFORE_TURN");
            }
            return true;
        }

        return false;
    }

    private boolean isSideYieldMode(Vehicle.YieldMode mode) {
        return mode == Vehicle.YieldMode.YIELD_RIGHT
                || mode == Vehicle.YieldMode.PULL_RIGHT
                || mode == Vehicle.YieldMode.CLEAR_PATH
                || mode == Vehicle.YieldMode.URGENT_CLEAR_PATH;
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

    private record TurnCandidate(Vehicle.TurnDecision decision,
                                 Lane targetLane,
                                 double targetProgress,
                                 double targetOffset) {}

    private TurnCandidate chooseTurnCandidate(Vehicle vehicle,
                                             List<Vehicle> vehicles,
                                             Lane sourceLane,
                                             Intersection intersection,
                                             Vehicle.TurnDecision requested) {
        String lastReject = null;
        for (Vehicle.TurnDecision decision : orderedTurnAttempts(sourceLane, intersection, requested)) {
            Lane targetLane = findTargetLane(sourceLane, decision, intersection);
            if (targetLane == null) {
                lastReject = "NO_" + decision + "_TARGET";
                continue;
            }

            if (targetLane == sourceLane && decision == Vehicle.TurnDecision.STRAIGHT) {
                return new TurnCandidate(decision, targetLane, Double.NaN, Double.NaN);
            }

            double targetConflict = targetLane.getProgressOf(intersection.getCenter());
            double exitDistance = exitDistanceFor(vehicle, decision,
                    MathUtils.distance(vehicle.getPosition(), intersection.getCenter()));
            double targetProgress = MathUtils.clamp(targetConflict + exitDistance, 0.0, targetLane.getLength());
            double targetOffset = chooseEntryOffset(vehicle, targetLane, targetProgress, decision);
            if (Double.isNaN(targetOffset)) {
                lastReject = "TARGET_ENTRY_FULL_" + decision;
                continue;
            }

            if (!occupancy.canEnterIntersection(vehicle, intersection, targetLane,
                    targetProgress, targetOffset, vehicles)) {
                lastReject = "INTERSECTION_BUSY_" + decision;
                continue;
            }

            return new TurnCandidate(decision, targetLane, targetProgress, targetOffset);
        }
        vehicle.setTurnWaitReason(lastReject != null ? lastReject : "NO_AVAILABLE_TURN");
        return null;
    }

    private List<Vehicle.TurnDecision> orderedTurnAttempts(Lane sourceLane,
                                                           Intersection intersection,
                                                           Vehicle.TurnDecision requested) {
        List<Vehicle.TurnDecision> result = new ArrayList<>();
        Vehicle.TurnDecision safeRequested = requested == null
                ? Vehicle.TurnDecision.STRAIGHT
                : requested;

        // If a map provides explicit rules, obey them. If the requested movement
        // is forbidden on this map (for example RIGHT from the stem-less side of a
        // T junction), normalize it to a real available movement instead of making
        // the vehicle wait forever.
        if (sourceLane != null && sourceLane.isStraightOnly()) {
            result.add(Vehicle.TurnDecision.STRAIGHT);
            return result;
        }

        if (sourceLane != null && sourceLane.hasAnyTurnRule(intersection)) {
            boolean requestedAllowed = sourceLane.hasTurnRule(intersection, safeRequested)
                    && sourceLane.getTurnTarget(intersection, safeRequested) != null;
            if (requestedAllowed) {
                result.add(safeRequested);
                return result;
            }

            // Requested movement is impossible. Prefer straight if it exists,
            // otherwise use left/right in a stable order. This is a map correction,
            // not a fallback after a blocked but legal turn.
            addIfAllowed(result, sourceLane, intersection, Vehicle.TurnDecision.STRAIGHT);
            addIfAllowed(result, sourceLane, intersection, Vehicle.TurnDecision.LEFT);
            addIfAllowed(result, sourceLane, intersection, Vehicle.TurnDecision.RIGHT);
            return result;
        }

        result.add(safeRequested);
        if (safeRequested == Vehicle.TurnDecision.STRAIGHT) {
            result.add(Vehicle.TurnDecision.LEFT);
            result.add(Vehicle.TurnDecision.RIGHT);
        }
        return result;
    }

    private void addIfAllowed(List<Vehicle.TurnDecision> result,
                              Lane sourceLane,
                              Intersection intersection,
                              Vehicle.TurnDecision decision) {
        if (!result.contains(decision)
                && sourceLane.hasTurnRule(intersection, decision)
                && sourceLane.getTurnTarget(intersection, decision) != null) {
            result.add(decision);
        }
    }

    private Lane findTargetLane(Lane currentLane,
                                Vehicle.TurnDecision decision,
                                Intersection intersection) {
        if (currentLane == null || intersection == null || decision == null) {
            return null;
        }

        if (currentLane.isStraightOnly()) {
            return decision == Vehicle.TurnDecision.STRAIGHT ? currentLane : null;
        }

        if (currentLane.hasAnyTurnRule(intersection)) {
            // Explicit map rules take precedence. A null explicit target means
            // the movement is forbidden; do not fall back to angle guessing.
            return currentLane.hasTurnRule(intersection, decision)
                    ? currentLane.getTurnTarget(intersection, decision)
                    : null;
        }

        double currentConflict = currentLane.getProgressOf(intersection.getCenter());
        if (decision == Vehicle.TurnDecision.STRAIGHT) {
            double continuation = currentLane.getLength() - currentConflict;
            if (continuation >= STRAIGHT_CONTINUATION_MIN) {
                return currentLane;
            }
            return bestOutgoingLaneByAngle(currentLane, intersection, 0.0, 42.0);
        }

        double desired = decision == Vehicle.TurnDecision.LEFT ? -90.0 : 90.0;
        return bestOutgoingLaneByAngle(currentLane, intersection, desired, 60.0);
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
            if (candidate == null || candidate == currentLane || candidate.getLength() <= 5.0) {
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
        return progress < lane.getLength() * 0.45;
    }

    private double chooseEntryOffset(Vehicle vehicle, Lane targetLane, double targetProgress,
                                     Vehicle.TurnDecision decision) {
        if (vehicle == null || targetLane == null) {
            return Double.NaN;
        }
        List<Double> candidates = new ArrayList<>();

        if (decision == Vehicle.TurnDecision.STRAIGHT) {
            // Going straight should preserve the current virtual slot.
            double preferred = targetLane.clampOffset(vehicle, vehicle.getPreferredLateralOffset());
            candidates.add(preferred);
            candidates.add(Math.abs(preferred - Vehicle.LEFT_OFFSET) < Math.abs(preferred - Vehicle.RIGHT_OFFSET)
                    ? Vehicle.LEFT_OFFSET : Vehicle.RIGHT_OFFSET);
            candidates.add(Math.abs(preferred - Vehicle.LEFT_OFFSET) < Math.abs(preferred - Vehicle.RIGHT_OFFSET)
                    ? Vehicle.RIGHT_OFFSET : Vehicle.LEFT_OFFSET);
        } else if (decision == Vehicle.TurnDecision.LEFT) {
            // Narrow left turns must enter the near/left slot. Do not silently
            // fall back to the far/right slot because that creates a wide loop
            // and can overlap a priority vehicle using the same exit.
            candidates.add(targetLane.getLeftmostOffset(vehicle));
        } else {
            // Narrow right turns must enter the near/right slot. If that entry is
            // blocked, wait before the intersection instead of cutting across the
            // target lane.
            candidates.add(targetLane.getRightmostOffset(vehicle));
        }
        // Center is not a normal turn-entry slot. Priority vehicles may use it
        // only when they are going straight and are already in an explicit
        // emergency-corridor maneuver; never use it as a fallback for L/R turns.
        if (vehicle.isPriority()
                && decision == Vehicle.TurnDecision.STRAIGHT
                && vehicle.getManeuverState() == Vehicle.ManeuverState.EMERGENCY_CORRIDOR) {
            candidates.add(Vehicle.CENTER_OFFSET);
        }

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
