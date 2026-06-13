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
    private static final double CLEARING_MIN_SPEED = 15.0;
    private static final double CLEARING_SPEED_CAP = 46.0;
    private static final double PRIORITY_CLEARING_SPEED_CAP = 58.0;
    private static final double ACTIVE_TURN_MIN_SPEED = 18.0;
    private static final double ACTIVE_TURN_SPEED_CAP = 62.0;
    private static final double PRIORITY_ACTIVE_TURN_SPEED_CAP = 72.0;
    private static final double PRIORITY_QUEUE_FOLLOW_FACTOR = 0.86;
    private static final double PRIORITY_QUEUE_FOLLOW_DISTANCE = 82.0;
    private static final double PRIORITY_QUEUE_FOLLOW_EXIT_DISTANCE = 96.0;
    private static final double PRIORITY_QUEUE_HARD_FOLLOW_DISTANCE = 34.0;
    private static final double PRIORITY_QUEUE_CANCEL_LATERAL_DISTANCE = 96.0;
    private static final double CENTER_CONFLICT_RADIUS = 88.0;
    private static final double RED_LIGHT_LATERAL_GATE = 150.0;
    private static final double INTERSECTION_ABORT_SPEED_CAP = 26.0;
    private static final double RED_LIGHT_ABORT_SPEED_CAP = 18.0;

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
            double cap = vehicle.isPriority()
                    ? PRIORITY_ACTIVE_TURN_SPEED_CAP
                    : ACTIVE_TURN_SPEED_CAP;
            decision.cancelOvertake()
                    .yieldMode(Vehicle.YieldMode.CLEAR_CONFLICT)
                    .maneuverState(Vehicle.ManeuverState.CLEARING_CONFLICT)
                    .targetLateralOffset(vehicle.getLateralOffset())
                    .targetSpeed(boundedSpeed(vehicle, ACTIVE_TURN_MIN_SPEED, cap))
                    .reason("ACTIVE_TURN_CLEAR");
        } else if (vehicle.isCommittedToIntersection()) {
            mergeCommittedClear(vehicle, decision);
        } else {
            applyPriorityRoutePolicy(vehicle, decision, routes);
            applyCenterCorridorGuard(vehicle, decision, routes, vehicles, intersections);
            applyIntersectionLateralGate(vehicle, decision);
            applyRedLightLateralGate(vehicle, nextLight, decision);
        }
        applyCollisionAvoidance(vehicle, decision, vehicles);
        return decision;
    }

    private static VehicleDecision mergeCommittedClear(Vehicle vehicle, VehicleDecision decision) {
        double cap = vehicle != null && vehicle.isPriority()
                ? PRIORITY_CLEARING_SPEED_CAP
                : CLEARING_SPEED_CAP;
        if (vehicle != null) {
            cap = Math.max(cap, vehicle.getSpeed());
        }
        return decision.cancelOvertake()
                .yieldMode(Vehicle.YieldMode.CLEAR_CONFLICT)
                .maneuverState(Vehicle.ManeuverState.CLEARING_CONFLICT)
                .targetLateralOffset(vehicle.getLateralOffset())
                .targetSpeed(boundedSpeed(vehicle, CLEARING_MIN_SPEED, cap))
                .reason("COMMITTED_CLEAR");
    }

    private static double boundedSpeed(Vehicle vehicle, double minSpeed, double maxSpeed) {
        double current = vehicle == null ? 0.0 : vehicle.getSpeed();
        double lo = Math.max(0.0, minSpeed);
        double hi = Math.max(lo, maxSpeed);
        return Math.min(hi, Math.max(lo, current));
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
                    double gap = inFront.getRearProgress() - vehicle.getFrontProgress();
                    boolean shouldFollowNow = shouldPriorityQueueFollowNow(ctx, gap);
                    if ((vehicle.isOvertaking()
                            || vehicle.getManeuverState() == Vehicle.ManeuverState.EMERGENCY_CORRIDOR)
                            && gap <= PRIORITY_QUEUE_CANCEL_LATERAL_DISTANCE) {
                        decision.abortLateralManeuver().reason("PRIORITY_QUEUE_CANCEL_LATERAL");
                    }
                    if (shouldFollowNow) {
                        double followSpeed = Math.max(0.0, inFront.getSpeed() * PRIORITY_QUEUE_FOLLOW_FACTOR);
                        if (gap < PRIORITY_QUEUE_HARD_FOLLOW_DISTANCE) {
                            followSpeed = Math.min(followSpeed, Math.max(0.0, inFront.getSpeed() * 0.62));
                        }
                        decision.capSpeed(followSpeed).reason("PRIORITY_QUEUE_FOLLOW");
                    }
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
        if (ahead == null) return false;
        PriorityRouteContext ctx = routes.get(priority, ahead);
        double gap = ahead.getRearProgress() - priority.getFrontProgress();
        return ctx.isQueueLike() && shouldPriorityQueueFollowNow(ctx, gap);
    }

    private static boolean shouldPriorityQueueFollowNow(PriorityRouteContext ctx, double gap) {
        if (ctx == null || !ctx.isQueueLike() || gap <= 0.0) return false;
        Vehicle normal = ctx.getNormal();
        if (normal != null) {
            Vehicle.IntersectionManeuverState state = normal.getIntersectionManeuverState();
            if (normal.isCommittedToIntersection()
                    || state == Vehicle.IntersectionManeuverState.WAITING_BEFORE_INTERSECTION
                    || state == Vehicle.IntersectionManeuverState.CROSSING_STRAIGHT
                    || state == Vehicle.IntersectionManeuverState.TURNING_LEFT
                    || state == Vehicle.IntersectionManeuverState.TURNING_RIGHT
                    || state == Vehicle.IntersectionManeuverState.EXITING
                    || state == Vehicle.IntersectionManeuverState.CLEARING_FOR_PRIORITY) {
                return true;
            }
        }
        Vehicle priority = ctx.getPriority();
        double followLimit = priority != null && priority.getPriorityWaitTarget() == normal
                ? PRIORITY_QUEUE_FOLLOW_EXIT_DISTANCE
                : PRIORITY_QUEUE_FOLLOW_DISTANCE;
        return gap <= followLimit;
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

        if (Vehicle.isActiveLateralManeuverState(vehicle.getManeuverState())) {
            decision.abortLateralManeuver()
                    .capSpeed(Math.min(vehicle.getSpeed(), INTERSECTION_ABORT_SPEED_CAP))
                    .reason("INTERSECTION_LATERAL_GATE");
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
        if (dist < 0.0 || dist > RED_LIGHT_LATERAL_GATE) return;

        if (Vehicle.isActiveLateralManeuverState(vehicle.getManeuverState())) {
            double cap = dist <= 24.0 ? 0.0 : RED_LIGHT_ABORT_SPEED_CAP;
            decision.abortLateralManeuver()
                    .capSpeed(Math.min(vehicle.getSpeed(), cap))
                    .reason("RED_LIGHT_LATERAL_GATE");
        }
    }

    private static double normalizeAngle(double angle) {
        double result = angle;
        while (result <= -180.0) result += 360.0;
        while (result > 180.0) result -= 360.0;
        return result;
    }

    private static void applyCollisionAvoidance(Vehicle vehicle,
                                                VehicleDecision decision,
                                                List<Vehicle> vehicles) {
        if (vehicle == null || vehicles == null) return;

        double baseSpeed = decision.targetSpeed != null ? decision.targetSpeed : vehicle.getSpeed();
        double mySpeed = baseSpeed;
        
        double myX = vehicle.getPosition().getX();
        double myY = vehicle.getPosition().getY();
        double angleRad = Math.toRadians(vehicle.getAngle());
        double dirX = Math.cos(angleRad);
        double dirY = Math.sin(angleRad);
        
        double lenMe = vehicle.getLongitudinalLength();
        double widthMe = vehicle.getHeight();
        
        double minBumperDist = 8.0; // Keep a clear gap of 8 pixels at stop

        for (Vehicle other : vehicles) {
            if (other == null || other == vehicle) {
                continue;
            }

            boolean amIInside = vehicle.isTurning() || vehicle.isCommittedToIntersection();
            boolean isOtherInside = other.isTurning() || other.isCommittedToIntersection();
            if (amIInside && !isOtherInside) {
                continue;
            }

            double dx = other.getPosition().getX() - myX;
            double dy = other.getPosition().getY() - myY;
            double dist = Math.hypot(dx, dy);

            // Lookahead limit
            if (dist > 120.0) {
                continue;
            }

            // Project relative position
            double longGap = dx * dirX + dy * dirY;
            double latGap = dx * (-dirY) + dy * dirX;

            double halfLenMe = lenMe / 2.0;
            double halfLenOther = other.getLongitudinalLength() / 2.0;
            double bumperDist = longGap - (halfLenMe + halfLenOther);

            // Check if other is in front (with a tolerance for overlapping/merging)
            if (longGap > 0 && bumperDist > -lenMe * 0.8) {
                // Calculate projected width of the other vehicle based on relative heading
                double relAngleRad = Math.toRadians(other.getAngle() - vehicle.getAngle());
                double otherProjectedWidth = other.getLongitudinalLength() * Math.abs(Math.sin(relAngleRad))
                        + other.getHeight() * Math.abs(Math.cos(relAngleRad));
                
                double combinedWidth = (widthMe + otherProjectedWidth) / 2.0 + 3.0; // 3px buffer

                if (Math.abs(latGap) < combinedWidth) {
                    // Other is blocking our path!
                    
                    // Deadlock/Mutual blocking check
                    double dirXOther = Math.cos(Math.toRadians(other.getAngle()));
                    double dirYOther = Math.sin(Math.toRadians(other.getAngle()));
                    double longGapOther = -dx * dirXOther - dy * dirYOther;
                    
                    boolean mutualBlocking = longGapOther > 0;
                    if (mutualBlocking) {
                        boolean amIEmergency = vehicle.isPriority();
                        boolean isOtherEmergency = other.isPriority();
                        boolean amICommitted = vehicle.isCommittedToIntersection();
                        boolean isOtherCommitted = other.isCommittedToIntersection();
                        
                        boolean hasMePriority = false;
                        if (amIEmergency && !isOtherEmergency) {
                            hasMePriority = true;
                        } else if (!amIEmergency && isOtherEmergency) {
                            hasMePriority = false;
                        } else if (amICommitted && !isOtherCommitted) {
                            hasMePriority = true;
                        } else if (!amICommitted && isOtherCommitted) {
                            hasMePriority = false;
                        } else {
                            hasMePriority = vehicle.hashCode() < other.hashCode();
                        }
                        
                        if (hasMePriority) {
                            continue;
                        }
                    }

                    double angleDiff = Math.abs(normalizeAngle(other.getAngle() - vehicle.getAngle()));
                    boolean isFollowing = angleDiff < 45.0;

                    double safeDistance = isFollowing 
                            ? (22.0 + mySpeed * 0.65) // Dynamic safe distance for following
                            : 45.0;                   // Fixed safe distance for crossing vehicles

                    if (bumperDist < safeDistance) {
                        double neededSpeed;
                        if (bumperDist <= minBumperDist) {
                            neededSpeed = 0.0;
                        } else {
                            double ratio = (bumperDist - minBumperDist) / Math.max(1.0, safeDistance - minBumperDist);
                            if (isFollowing) {
                                // Follow the vehicle's speed, capped/scaled down
                                if (other.getSpeed() > 1.0) {
                                    neededSpeed = Math.min(other.getSpeed(), mySpeed * ratio);
                                } else {
                                    neededSpeed = mySpeed * ratio;
                                }
                            } else {
                                // Decelerate to stop for crossing vehicles
                                neededSpeed = mySpeed * ratio;
                            }
                        }
                        mySpeed = Math.min(mySpeed, neededSpeed);
                    }
                }
            }
        }

        if (mySpeed < baseSpeed) {
            decision.capSpeed(mySpeed).reason("COLLISION_AVOIDANCE");
        }
    }
}
