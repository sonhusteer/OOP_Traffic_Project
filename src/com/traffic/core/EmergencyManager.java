package com.traffic.core;

import com.traffic.map.Intersection;
import com.traffic.map.Lane;
import java.util.List;

/** Applies priority-vehicle yielding rules without stopping cars that already cleared conflict. */
public class EmergencyManager {

    private static final double SAME_LANE_YIELD_DISTANCE = 205.0;
    private static final double EMERGENCY_LOOKAHEAD = 185.0;
    private static final double NORMAL_LOOKAHEAD = 145.0;
    private static final double STOP_ASSIGN_DISTANCE = 112.0;
    private static final double CONFLICT_RADIUS = 42.0;
    private static final double CLEAR_MARGIN = 28.0;
    private static final double COMFORTABLE_BRAKE = 120.0;
    private static final double STOP_BUFFER = 10.0;
    private static final double PRIORITY_YIELD_LOCK_SECONDS = 1.15;

    public void update(List<Vehicle> vehicles, List<Intersection> intersections) {
        update(vehicles, intersections, PriorityRouteAnalyzer.analyze(vehicles, intersections));
    }

    public void update(List<Vehicle> vehicles, List<Intersection> intersections,
                       PriorityRouteAnalyzer priorityRoutes) {
        PriorityRouteAnalyzer routes = priorityRoutes == null
                ? PriorityRouteAnalyzer.analyze(vehicles, intersections)
                : priorityRoutes;

        for (Vehicle v : vehicles) {
            if (v == null || v.isPriority()) {
                continue;
            }
            if (v.hasActivePriorityYieldLock()) {
                v.setYieldMode(v.getYieldMode() == Vehicle.YieldMode.URGENT_CLEAR_PATH
                        ? Vehicle.YieldMode.URGENT_CLEAR_PATH
                        : Vehicle.YieldMode.YIELD_RIGHT);
            } else {
                v.setYieldMode(Vehicle.YieldMode.NONE);
            }
        }

        for (Vehicle priority : vehicles) {
            if (!priority.isPriority() || priority.getLane() == null) continue;
            applySameLaneYield(priority, vehicles, routes);
            applyIntersectionYield(priority, vehicles, intersections, routes);
        }
    }

    private void applySameLaneYield(Vehicle priority, List<Vehicle> vehicles,
                                    PriorityRouteAnalyzer routes) {
        Lane lane = priority.getLane();
        for (Vehicle normal : vehicles) {
            if (normal.isPriority() || normal.getLane() != lane) continue;
            double gap = normal.getRearProgress() - priority.getFrontProgress();
            boolean priorityBehind = gap > 0.0;
            boolean closeEnough = gap < SAME_LANE_YIELD_DISTANCE;
            if (!priorityBehind || !closeEnough) {
                continue;
            }

            if (normal.isCommittedToIntersection()) {
                applyHigherPriorityMode(normal, Vehicle.YieldMode.CLEAR_CONFLICT);
                continue;
            }

            PriorityRouteContext ctx = routes.get(priority, normal);
            YieldDirective directive = chooseSameLaneYieldDirective(priority, normal, lane, ctx);

            switch (directive.getType()) {
                case NONE -> {
                    normal.clearPriorityYieldLock();
                    normal.setYieldMode(Vehicle.YieldMode.NONE);
                }
                case FOLLOW_QUEUE -> {
                    // This is a route-order relationship, not a side-yield.
                    // Do not write a YieldMode signal; the priority driver will read
                    // PriorityRouteAnalyzer and follow/wait.
                    normal.clearPriorityYieldLock();
                    normal.setYieldMode(Vehicle.YieldMode.NONE);
                }
                case CLEAR_INTERSECTION -> applyHigherPriorityMode(normal, Vehicle.YieldMode.CLEAR_CONFLICT);
                case HOLD_POSITION -> applyHigherPriorityMode(normal, Vehicle.YieldMode.HOLD_POSITION);
                case SIDE_SHIFT -> {
                    double yieldOffset = directive.getTargetOffset();
                    normal.lockPriorityYield(priority, yieldOffset, PRIORITY_YIELD_LOCK_SECONDS);
                    boolean sideGap = lane.occupancy().isSideSpaceFree(
                            normal, yieldOffset, 82.0, 48.0);
                    applyHigherPriorityMode(normal, sideGap
                            ? Vehicle.YieldMode.YIELD_RIGHT
                            : Vehicle.YieldMode.URGENT_CLEAR_PATH);
                }
            }
        }
    }

    private YieldDirective chooseSameLaneYieldDirective(Vehicle priority,
                                                        Vehicle normal,
                                                        Lane lane,
                                                        PriorityRouteContext ctx) {
        if (priority == null || normal == null || lane == null) {
            return YieldDirective.none();
        }

        PriorityRouteRelation relation = ctx != null
                ? ctx.getRelation()
                : PriorityRouteRelation.UNRELATED;

        if (relation == PriorityRouteRelation.SAME_QUEUE
                || relation == PriorityRouteRelation.PRIORITY_STRAIGHT_NORMAL_TURNING_AHEAD) {
            return YieldDirective.followQueue();
        }

        if (relation == PriorityRouteRelation.PRIORITY_STRAIGHT_NORMAL_STRAIGHT_AHEAD) {
            double left = lane.getLeftmostOffset(normal);
            double right = lane.getRightmostOffset(normal);
            double offset = Math.abs(normal.getLateralOffset() - left)
                    <= Math.abs(normal.getLateralOffset() - right) ? left : right;
            return YieldDirective.sideShift(offset, "SPLIT_TO_EDGE_FOR_PRIORITY_STRAIGHT");
        }

        // If the priority vehicle is turning, avoid the near turn slot unless this
        // pair is a same-route queue (handled above). That keeps the priority turn
        // path open without forcing all unrelated cars to move.
        if (priority.getTurnDecision() == Vehicle.TurnDecision.RIGHT) {
            return YieldDirective.sideShift(lane.getLeftmostOffset(normal), "KEEP_RIGHT_TURN_EXIT_CLEAR");
        }
        if (priority.getTurnDecision() == Vehicle.TurnDecision.LEFT) {
            return YieldDirective.sideShift(lane.getRightmostOffset(normal), "KEEP_LEFT_TURN_ARC_CLEAR");
        }

        // Conventional fallback for straight priority vehicle when the relation is
        // not specifically classified but the normal vehicle is physically ahead.
        return YieldDirective.sideShift(lane.getRightmostOffset(normal), "DEFAULT_PRIORITY_YIELD");
    }

    private void applyIntersectionYield(Vehicle priority, List<Vehicle> vehicles,
                                        List<Intersection> intersections,
                                        PriorityRouteAnalyzer routes) {
        Lane priorityLane = priority.getLane();
        for (Intersection intersection : intersections) {
            if (!intersection.getLanes().contains(priorityLane)) continue;
            if (!isPriorityRelevantForIntersection(priority, intersection)) continue;

            for (Vehicle normal : vehicles) {
                if (normal.isPriority() || normal.getLane() == null) continue;
                if (normal.getLane() == priorityLane) continue;
                if (!intersection.getLanes().contains(normal.getLane())) continue;

                PriorityRouteContext ctx = routes.get(priority, normal);
                if (ctx == null || ctx.getIntersection() != intersection || !ctx.requiresIntersectionBlock()) {
                    continue;
                }

                Vehicle.YieldMode mode = decideIntersectionMode(normal, intersection);
                applyHigherPriorityMode(normal, mode);
            }
        }
    }

    private boolean isPriorityRelevantForIntersection(Vehicle priority, Intersection intersection) {
        double conflictProgress = priority.getLane().getProgressOf(intersection.getCenter());
        double distanceToConflict = conflictProgress - priority.getFrontProgress();
        boolean approachingOrInside = distanceToConflict <= EMERGENCY_LOOKAHEAD;
        boolean notCleared = priority.getRearProgress() <= conflictProgress + CONFLICT_RADIUS + CLEAR_MARGIN;
        return approachingOrInside && notCleared;
    }

    private Vehicle.YieldMode decideIntersectionMode(Vehicle normal, Intersection intersection) {
        Lane normalLane = normal.getLane();
        double conflictProgress = normalLane.getProgressOf(intersection.getCenter());
        double conflictStart = conflictProgress - CONFLICT_RADIUS;
        double conflictEnd = conflictProgress + CONFLICT_RADIUS;

        if (normal.isCommittedToIntersection()) {
            return Vehicle.YieldMode.CLEAR_CONFLICT;
        }

        if (normal.getRearProgress() > conflictEnd + CLEAR_MARGIN) {
            return Vehicle.YieldMode.NONE;
        }

        if (normal.getFrontProgress() >= conflictStart) {
            return Vehicle.YieldMode.CLEAR_CONFLICT;
        }

        double distanceToConflict = conflictStart - normal.getFrontProgress();
        if (distanceToConflict > NORMAL_LOOKAHEAD) {
            return Vehicle.YieldMode.NONE;
        }

        if (!isLeadVehicleBeforeConflict(normal, conflictStart)) {
            return Vehicle.YieldMode.NONE;
        }

        double stopProgress = normalLane.getStopProgressBefore(conflictProgress);
        double distanceToStop = stopProgress - normal.getFrontProgress();
        if (distanceToStop > STOP_ASSIGN_DISTANCE) {
            return Vehicle.YieldMode.NONE;
        }

        if (distanceToStop <= 4.0) {
            return Vehicle.YieldMode.STOP_BEFORE_CONFLICT;
        }

        if (canStopBeforeConflict(normal, distanceToStop)) {
            return Vehicle.YieldMode.STOP_BEFORE_CONFLICT;
        }

        // If the lead car is already too close/fast to stop comfortably before
        // the conflict, do not force a late hard stop. Let it clear the conflict
        // area instead of freezing at or inside the intersection.
        return Vehicle.YieldMode.CLEAR_CONFLICT;
    }

    private boolean isLeadVehicleBeforeConflict(Vehicle candidate, double conflictStart) {
        Lane lane = candidate.getLane();
        if (lane == null) {
            return false;
        }
        double bestFront = -Double.MAX_VALUE;
        Vehicle lead = null;
        for (Vehicle other : lane.getVehicles()) {
            if (other == null || other.isPriority()) {
                continue;
            }
            if (other.getFrontProgress() >= conflictStart) {
                continue;
            }
            double front = other.getFrontProgress();
            if (front > bestFront) {
                bestFront = front;
                lead = other;
            }
        }
        return lead == candidate;
    }

    private boolean canStopBeforeConflict(Vehicle normal, double distanceToConflict) {
        double speed = Math.max(0.0, normal.getSpeed());
        double stoppingDistance = speed * speed / (2.0 * COMFORTABLE_BRAKE) + STOP_BUFFER;
        return distanceToConflict > stoppingDistance;
    }

    private void applyHigherPriorityMode(Vehicle vehicle, Vehicle.YieldMode proposed) {
        if (proposed == Vehicle.YieldMode.NONE) return;
        if (priorityOf(proposed) >= priorityOf(vehicle.getYieldMode())) {
            vehicle.setYieldMode(proposed);
        }
    }

    private int priorityOf(Vehicle.YieldMode mode) {
        return switch (mode) {
            case NONE -> 0;
            case YIELD_RIGHT, PULL_RIGHT -> 1;
            case HOLD_POSITION, BLOCKED_YIELD -> 2;
            case CLEAR_PATH, URGENT_CLEAR_PATH -> 3;
            case STOP_BEFORE_CONFLICT, STOP -> 4;
            case CLEAR_CONFLICT, CLEAR_INTERSECTION -> 5;
        };
    }
}
