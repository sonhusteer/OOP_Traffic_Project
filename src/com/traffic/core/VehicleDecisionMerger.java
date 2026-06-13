package com.traffic.core;

import com.traffic.map.Intersection;
import com.traffic.map.Lane;
import com.traffic.map.LaneControlPoint;
import com.traffic.map.TrafficLight;
import java.util.List;

/**
 * Resolves contradictory commands produced by the legacy pipeline.
 *
 * EmergencyManager, TurnCoordinator and Driver still run for compatibility, but
 * before Vehicle.update() this merger applies one final policy pass using the
 * cached PriorityRouteAnalyzer. This is the stage-2 bridge toward a full command
 * pipeline: route relation is no longer encoded solely through YieldMode, and
 * lateral/turn/priority conflicts are resolved in one place.
 */
public final class VehicleDecisionMerger {
    private static final double COMMITTED_CLEAR_SPEED_FACTOR = 0.72;
    private static final double PRIORITY_QUEUE_FOLLOW_FACTOR = 0.86;
    private static final double CENTER_CONFLICT_RADIUS = 88.0;
    private static final double RED_LIGHT_LATERAL_GATE = 150.0;

    private VehicleDecisionMerger() {}

    public static VehicleDecision resolve(Vehicle vehicle,
                                          TrafficLight nextLight,
                                          PriorityRouteAnalyzer priorityRoutes,
                                          List<Vehicle> vehicles,
                                          List<Intersection> intersections) {
        VehicleDecision decision = VehicleDecision.empty();
        if (vehicle == null) return decision;

        PriorityRouteAnalyzer routes = priorityRoutes == null
                ? PriorityRouteAnalyzer.getCurrent()
                : priorityRoutes;

        // The driver may have set speed/offset already. The merger only writes
        // corrections when a higher-level policy demands it.
        if (vehicle.isTurning()) {
            return decision.cancelOvertake()
                    .yieldMode(Vehicle.YieldMode.CLEAR_CONFLICT)
                    .maneuverState(Vehicle.ManeuverState.CLEARING_CONFLICT)
                    .targetLateralOffset(vehicle.getLateralOffset())
                    .maxSpeed(Math.max(vehicle.getSpeed(), 28.0))
                    .reason("ACTIVE_TURN_CLEAR");
        }

        if (vehicle.isCommittedToIntersection()) {
            return mergeCommittedClear(vehicle, decision);
        }

        applyPriorityRoutePolicy(vehicle, decision, routes);
        applyCenterCorridorGuard(vehicle, decision, routes, vehicles, intersections);
        applyIntersectionLateralGate(vehicle, decision);
        applyRedLightLateralGate(vehicle, nextLight, decision);
        return decision;
    }

    private static VehicleDecision mergeCommittedClear(Vehicle vehicle, VehicleDecision decision) {
        return decision.cancelOvertake()
                .yieldMode(Vehicle.YieldMode.CLEAR_CONFLICT)
                .maneuverState(Vehicle.ManeuverState.CLEARING_CONFLICT)
                .targetLateralOffset(vehicle.getLateralOffset())
                .maxSpeed(Math.max(vehicle.getSpeed(), Math.max(24.0, vehicle.getSpeed() * COMMITTED_CLEAR_SPEED_FACTOR)))
                .reason("COMMITTED_CLEAR");
    }

    private static void applyPriorityRoutePolicy(Vehicle vehicle,
                                                 VehicleDecision decision,
                                                 PriorityRouteAnalyzer routes) {
        if (vehicle == null || routes == null) return;

        if (vehicle.isPriority()) {
            Vehicle inFront = vehicle.getLane() != null
                    ? vehicle.getLane().occupancy().vehicleAheadOf(vehicle)
                    : null;
            if (inFront != null) {
                PriorityRouteContext ctx = routes.get(vehicle, inFront);
                if (ctx.isQueueLike()) {
                    // Same-route or priority-straight/normal-turning means queue,
                    // not corridor. The priority vehicle follows and never escalates
                    // to middle/center passing for this target.
                    if (vehicle.isOvertaking()
                            || vehicle.getManeuverState() == Vehicle.ManeuverState.EMERGENCY_CORRIDOR) {
                        decision.abortLateralManeuver().reason("PRIORITY_QUEUE_CANCEL_LATERAL");
                    }
                    double followSpeed = Math.max(0.0, inFront.getSpeed() * PRIORITY_QUEUE_FOLLOW_FACTOR);
                    if (inFront.getRearProgress() - vehicle.getFrontProgress() < 34.0) {
                        followSpeed = Math.min(followSpeed, Math.max(0.0, inFront.getSpeed() * 0.62));
                    }
                    decision.minSpeed(followSpeed).reason("PRIORITY_QUEUE_FOLLOW");
                }
            }
            return;
        }

        PriorityRouteContext strongest = routes.strongestForNormal(vehicle);
        if (strongest == null) return;

        PriorityRouteRelation relation = strongest.getRelation();
        if (relation == PriorityRouteRelation.SAME_QUEUE
                || relation == PriorityRouteRelation.PRIORITY_STRAIGHT_NORMAL_TURNING_AHEAD) {
            // The vehicle is ordered ahead of the priority route. Do not let a
            // previous yield lock pull it away from its turn/route slot.
            if (vehicle.hasActivePriorityYieldLock()
                    || vehicle.getManeuverState() == Vehicle.ManeuverState.YIELDING_RIGHT
                    || vehicle.getManeuverState() == Vehicle.ManeuverState.URGENT_CLEARING) {
                decision.clearPriorityYieldLock()
                        .yieldMode(Vehicle.YieldMode.NONE)
                        .maneuverState(Vehicle.ManeuverState.NORMAL)
                        .returnToPreferredSlot()
                        .reason("QUEUE_LIKE_NORMAL_RESTORE_ROUTE");
            }
        }
    }

    private static void applyCenterCorridorGuard(Vehicle vehicle,
                                                 VehicleDecision decision,
                                                 PriorityRouteAnalyzer routes,
                                                 List<Vehicle> vehicles,
                                                 List<Intersection> intersections) {
        if (vehicle == null || !vehicle.isPriority()) return;
        if (vehicle.getManeuverState() != Vehicle.ManeuverState.EMERGENCY_CORRIDOR) return;

        boolean mayUseCenter = vehicle.getTurnDecision() == Vehicle.TurnDecision.STRAIGHT
                && !centerOffsetOccupiedByCommittedVehicle(vehicle, vehicles, intersections)
                && !centerConflictsWithQueueLikeTarget(vehicle, routes);

        if (!mayUseCenter) {
            decision.abortLateralManeuver().reason("CENTER_CORRIDOR_GUARD");
        }
    }

    private static boolean centerConflictsWithQueueLikeTarget(Vehicle priority, PriorityRouteAnalyzer routes) {
        if (priority == null || priority.getLane() == null || routes == null) return false;
        Vehicle ahead = priority.getLane().occupancy().vehicleAheadOf(priority);
        return ahead != null && routes.get(priority, ahead).isQueueLike();
    }

    private static boolean centerOffsetOccupiedByCommittedVehicle(Vehicle priority,
                                                                  List<Vehicle> vehicles,
                                                                  List<Intersection> intersections) {
        if (priority == null || priority.getLane() == null || vehicles == null || intersections == null) {
            return false;
        }
        Intersection ix = nearestRelevantIntersection(priority, intersections);
        if (ix == null) return false;

        for (Vehicle other : vehicles) {
            if (other == null || other == priority || !other.isCommittedToIntersection()) continue;
            if (other.getLane() == null || !ix.getLanes().contains(other.getLane())) continue;
            double progressAtCenter = other.getLane().getProgressOf(ix.getCenter());
            double distance = Math.abs(other.getLaneProgress() - progressAtCenter);
            if (distance <= CENTER_CONFLICT_RADIUS
                    && Math.abs(other.getLateralOffset() - Vehicle.CENTER_OFFSET) <= 13.0) {
                return true;
            }
        }
        return false;
    }

    private static Intersection nearestRelevantIntersection(Vehicle vehicle, List<Intersection> intersections) {
        Intersection best = null;
        double bestAbs = Double.POSITIVE_INFINITY;
        if (vehicle == null || vehicle.getLane() == null || intersections == null) return null;
        Lane lane = vehicle.getLane();
        for (Intersection ix : intersections) {
            if (ix == null || !ix.getLanes().contains(lane)) continue;
            double dist = lane.getProgressOf(ix.getCenter()) - vehicle.getFrontProgress();
            if (dist < -65.0 || dist > 145.0) continue;
            double abs = Math.abs(dist);
            if (abs < bestAbs) {
                bestAbs = abs;
                best = ix;
            }
        }
        return best;
    }

    private static void applyIntersectionLateralGate(Vehicle vehicle, VehicleDecision decision) {
        if (vehicle == null) return;
        Vehicle.IntersectionManeuverState state = vehicle.getIntersectionManeuverState();
        boolean nearOrWaiting = state == Vehicle.IntersectionManeuverState.APPROACHING
                || state == Vehicle.IntersectionManeuverState.PREPARING_TURN_SLOT
                || state == Vehicle.IntersectionManeuverState.WAITING_BEFORE_INTERSECTION;
        if (!nearOrWaiting) return;

        Vehicle.ManeuverState ms = vehicle.getManeuverState();
        boolean activeLateral = ms == Vehicle.ManeuverState.GAP_FILLING
                || ms == Vehicle.ManeuverState.OVERTAKE_SHIFT_LEFT
                || ms == Vehicle.ManeuverState.OVERTAKE_PASSING
                || ms == Vehicle.ManeuverState.OVERTAKE_RETURNING
                || ms == Vehicle.ManeuverState.EMERGENCY_CORRIDOR;
        if (activeLateral) {
            decision.abortLateralManeuver().reason("INTERSECTION_LATERAL_GATE");
        }
    }

    private static void applyRedLightLateralGate(Vehicle vehicle,
                                                 TrafficLight nextLight,
                                                 VehicleDecision decision) {
        if (vehicle == null || vehicle.getLane() == null) return;
        TrafficLight light = nextLight;
        Lane lane = vehicle.getLane();
        double frontProgress = vehicle.getFrontProgress();
        double stopProgress = lane.getStopProgress();

        LaneControlPoint cp = lane.getNextControlPoint(frontProgress);
        if (cp != null) {
            light = cp.getLight();
            stopProgress = cp.getProgress();
        }
        if (light == null || !light.isRed()) return;

        double dist = stopProgress - frontProgress;
        if (dist < -3.0 || dist > RED_LIGHT_LATERAL_GATE) return;

        Vehicle.ManeuverState ms = vehicle.getManeuverState();
        boolean activeLateral = ms == Vehicle.ManeuverState.GAP_FILLING
                || ms == Vehicle.ManeuverState.OVERTAKE_SHIFT_LEFT
                || ms == Vehicle.ManeuverState.OVERTAKE_PASSING
                || ms == Vehicle.ManeuverState.OVERTAKE_RETURNING
                || ms == Vehicle.ManeuverState.EMERGENCY_CORRIDOR;
        if (activeLateral) {
            decision.abortLateralManeuver().reason("RED_LIGHT_LATERAL_GATE");
        }
    }
}
