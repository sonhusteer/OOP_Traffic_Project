package com.traffic.core;

import com.traffic.map.Intersection;
import com.traffic.map.Lane;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes priority-route relationships once per simulation tick. Managers and
 * drivers read this cache instead of recalculating route conflict differently.
 */
public final class PriorityRouteAnalyzer {
    private static final double SAME_LANE_HORIZON = 235.0;
    private static final double INTERSECTION_HORIZON = 210.0;
    private static final double ETA_WINDOW_SECONDS = 1.85;
    private static final double MIN_SPEED_FOR_ETA = 22.0;

    private static PriorityRouteAnalyzer current = empty();

    private final Map<Vehicle, Map<Vehicle, PriorityRouteContext>> byPriority = new IdentityHashMap<>();
    private final Map<Vehicle, PriorityRouteContext> strongestForNormal = new IdentityHashMap<>();

    public static PriorityRouteAnalyzer empty() {
        return new PriorityRouteAnalyzer();
    }

    public static PriorityRouteAnalyzer getCurrent() {
        return current;
    }

    public static void setCurrent(PriorityRouteAnalyzer analyzer) {
        current = analyzer == null ? empty() : analyzer;
    }

    public static PriorityRouteAnalyzer analyze(List<Vehicle> vehicles, List<Intersection> intersections) {
        PriorityRouteAnalyzer analyzer = new PriorityRouteAnalyzer();
        if (vehicles == null || intersections == null) {
            setCurrent(analyzer);
            return analyzer;
        }

        List<Vehicle> priorityVehicles = new ArrayList<>();
        List<Vehicle> normalVehicles = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            if (vehicle == null || vehicle.getLane() == null) continue;
            if (vehicle.isPriority()) priorityVehicles.add(vehicle);
            else normalVehicles.add(vehicle);
        }

        for (Vehicle priority : priorityVehicles) {
            for (Vehicle normal : normalVehicles) {
                PriorityRouteContext ctx = analyzer.compute(priority, normal, intersections);
                if (ctx.isRelated()) {
                    analyzer.put(ctx);
                }
            }
        }
        setCurrent(analyzer);
        return analyzer;
    }

    public PriorityRouteContext get(Vehicle priority, Vehicle normal) {
        if (priority == null || normal == null) return PriorityRouteContext.unrelated(priority, normal);
        Map<Vehicle, PriorityRouteContext> inner = byPriority.get(priority);
        if (inner == null) return PriorityRouteContext.unrelated(priority, normal);
        return inner.getOrDefault(normal, PriorityRouteContext.unrelated(priority, normal));
    }

    public PriorityRouteContext strongestForNormal(Vehicle normal) {
        if (normal == null) return null;
        return strongestForNormal.get(normal);
    }

    public boolean hasBlockingPriorityFor(Vehicle normal, Intersection intersection) {
        if (normal == null) return false;
        PriorityRouteContext ctx = strongestForNormal(normal);
        if (ctx == null || !ctx.requiresIntersectionBlock()) return false;
        return intersection == null || ctx.getIntersection() == intersection;
    }

    public boolean isQueueLike(Vehicle priority, Vehicle normal) {
        return get(priority, normal).isQueueLike();
    }

    private void put(PriorityRouteContext ctx) {
        byPriority.computeIfAbsent(ctx.getPriority(), k -> new IdentityHashMap<>())
                .put(ctx.getNormal(), ctx);
        PriorityRouteContext previous = strongestForNormal.get(ctx.getNormal());
        if (previous == null || severity(ctx.getRelation()) > severity(previous.getRelation())) {
            strongestForNormal.put(ctx.getNormal(), ctx);
        }
    }

    private int severity(PriorityRouteRelation relation) {
        return switch (relation) {
            case UNRELATED -> 0;
            case SAME_QUEUE -> 1;
            case PRIORITY_STRAIGHT_NORMAL_STRAIGHT_AHEAD -> 2;
            case PRIORITY_STRAIGHT_NORMAL_TURNING_AHEAD -> 3;
            case SAME_LANE_DIVERGING -> 4;
            case SHARED_EXIT_CONFLICT -> 5;
            case CROSSING_CONFLICT -> 6;
        };
    }

    private PriorityRouteContext compute(Vehicle priority, Vehicle normal, List<Intersection> intersections) {
        if (priority == null || normal == null || priority.getLane() == null || normal.getLane() == null) {
            return PriorityRouteContext.unrelated(priority, normal);
        }

        if (priority.getLane() == normal.getLane()) {
            double gap = normal.getRearProgress() - priority.getFrontProgress();
            if (gap > 0.0 && gap <= SAME_LANE_HORIZON) {
                Intersection ix = nearestSharedIntersectionAhead(priority, normal, intersections, INTERSECTION_HORIZON);
                Lane pTarget = resolveTargetLane(priority.getLane(), ix, priority.getTurnDecision());
                Lane nTarget = resolveTargetLane(normal.getLane(), ix, normal.getTurnDecision());
                PriorityRouteRelation relation = sameLaneRelation(priority, normal, ix, pTarget, nTarget);
                double pEta = etaToIntersection(priority, ix);
                double nEta = etaToIntersection(normal, ix);
                return new PriorityRouteContext(priority, normal, ix, relation, pTarget, nTarget, pEta, nEta, gap);
            }
            return PriorityRouteContext.unrelated(priority, normal);
        }

        Intersection ix = firstCommonRelevantIntersection(priority, normal, intersections);
        if (ix == null) return PriorityRouteContext.unrelated(priority, normal);

        double pEta = etaToIntersection(priority, ix);
        double nEta = etaToIntersection(normal, ix);
        if (Double.isInfinite(pEta) || Double.isInfinite(nEta)) {
            return PriorityRouteContext.unrelated(priority, normal);
        }
        if (Math.abs(pEta - nEta) > ETA_WINDOW_SECONDS && !normal.isCommittedToIntersection()) {
            return PriorityRouteContext.unrelated(priority, normal);
        }

        Lane pTarget = resolveTargetLane(priority.getLane(), ix, priority.getTurnDecision());
        Lane nTarget = resolveTargetLane(normal.getLane(), ix, normal.getTurnDecision());
        PriorityRouteRelation relation = crossingRelation(priority, normal, ix, pTarget, nTarget);
        return new PriorityRouteContext(priority, normal, ix, relation, pTarget, nTarget, pEta, nEta, Double.POSITIVE_INFINITY);
    }

    private PriorityRouteRelation sameLaneRelation(Vehicle priority, Vehicle normal,
                                                   Intersection ix, Lane pTarget, Lane nTarget) {
        Vehicle.TurnDecision p = priority.getTurnDecision();
        Vehicle.TurnDecision n = normal.getTurnDecision();
        if (p == null) p = Vehicle.TurnDecision.STRAIGHT;
        if (n == null) n = Vehicle.TurnDecision.STRAIGHT;

        boolean orderedQueue = isOrderedQueueSituation(priority, normal, ix, p, n, pTarget, nTarget);

        // Same-lane straight/straight on an open road is not a queue. The normal
        // vehicle is a physical blocker ahead and may split to an edge; the
        // priority vehicle should not be forced into early follow from far away.
        if (p == Vehicle.TurnDecision.STRAIGHT && n == Vehicle.TurnDecision.STRAIGHT) {
            return orderedQueue
                    ? PriorityRouteRelation.SAME_QUEUE
                    : PriorityRouteRelation.PRIORITY_STRAIGHT_NORMAL_STRAIGHT_AHEAD;
        }

        if (p == Vehicle.TurnDecision.STRAIGHT && n != Vehicle.TurnDecision.STRAIGHT) {
            return PriorityRouteRelation.PRIORITY_STRAIGHT_NORMAL_TURNING_AHEAD;
        }

        boolean sameDecision = p == n;
        boolean sameTarget = pTarget != null && nTarget != null && pTarget == nTarget;
        if (sameDecision && sameTarget && orderedQueue) {
            return PriorityRouteRelation.SAME_QUEUE;
        }
        return PriorityRouteRelation.SAME_LANE_DIVERGING;
    }

    private boolean isOrderedQueueSituation(Vehicle priority, Vehicle normal, Intersection ix,
                                            Vehicle.TurnDecision p, Vehicle.TurnDecision n,
                                            Lane pTarget, Lane nTarget) {
        if (priority == null || normal == null || normal.getLane() == null) return false;
        if (normal.isCommittedToIntersection() || priority.isCommittedToIntersection()) return true;

        Vehicle.IntersectionManeuverState state = normal.getIntersectionManeuverState();
        if (state == Vehicle.IntersectionManeuverState.WAITING_BEFORE_INTERSECTION
                || state == Vehicle.IntersectionManeuverState.CROSSING_STRAIGHT
                || state == Vehicle.IntersectionManeuverState.TURNING_LEFT
                || state == Vehicle.IntersectionManeuverState.TURNING_RIGHT
                || state == Vehicle.IntersectionManeuverState.EXITING
                || state == Vehicle.IntersectionManeuverState.CLEARING_FOR_PRIORITY) {
            return true;
        }

        // PREPARING_TURN_SLOT is already owned by the turn/intersection planner.
        // Do not classify it as an open-road blocker and pull it away from its
        // turn slot for side-yield. Speed follow is still distance-gated later
        // by Driver/Merger, so this does not bring back early priority braking.
        if (state == Vehicle.IntersectionManeuverState.PREPARING_TURN_SLOT) {
            return true;
        }

        if (ix != null) {
            double pDist = priority.getLane().getProgressOf(ix.getCenter()) - priority.getFrontProgress();
            double nDist = normal.getLane().getProgressOf(ix.getCenter()) - normal.getFrontProgress();
            if (nDist <= 70.0 && pDist <= 150.0) return true;
        }

        return p != Vehicle.TurnDecision.STRAIGHT
                && p == n
                && pTarget != null
                && nTarget != null
                && pTarget == nTarget;
    }

    private PriorityRouteRelation crossingRelation(Vehicle priority, Vehicle normal,
                                                   Intersection ix, Lane pTarget, Lane nTarget) {
        if (priority == null || normal == null || ix == null) return PriorityRouteRelation.UNRELATED;
        if (pTarget != null && nTarget != null && pTarget == nTarget) {
            return PriorityRouteRelation.SHARED_EXIT_CONFLICT;
        }

        Lane pLane = priority.getLane();
        Lane nLane = normal.getLane();
        if (pLane == null || nLane == null) return PriorityRouteRelation.UNRELATED;
        double pConflict = pLane.getProgressOf(ix.getCenter());
        double nConflict = nLane.getProgressOf(ix.getCenter());
        Vector2D pDir = pLane.getDirectionAt(pConflict);
        Vector2D nDir = nLane.getDirectionAt(nConflict);
        double dot = Math.abs(pDir.getX() * nDir.getX() + pDir.getY() * nDir.getY());
        return dot < 0.72 ? PriorityRouteRelation.CROSSING_CONFLICT : PriorityRouteRelation.UNRELATED;
    }

    private Intersection nearestSharedIntersectionAhead(Vehicle priority, Vehicle normal,
                                                        List<Intersection> intersections, double horizon) {
        Intersection best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        if (intersections == null) return null;
        for (Intersection ix : intersections) {
            if (ix == null || !ix.getLanes().contains(priority.getLane())) continue;
            double pDist = priority.getLane().getProgressOf(ix.getCenter()) - priority.getFrontProgress();
            double nDist = normal.getLane().getProgressOf(ix.getCenter()) - normal.getFrontProgress();
            if (pDist < -45.0 || pDist > horizon || nDist < -80.0 || nDist > horizon + 65.0) continue;
            if (pDist < bestDist) {
                bestDist = pDist;
                best = ix;
            }
        }
        return best;
    }

    private Intersection firstCommonRelevantIntersection(Vehicle priority, Vehicle normal, List<Intersection> intersections) {
        Intersection best = null;
        double bestEta = Double.POSITIVE_INFINITY;
        if (intersections == null) return null;
        for (Intersection ix : intersections) {
            if (ix == null || !ix.getLanes().contains(priority.getLane()) || !ix.getLanes().contains(normal.getLane())) {
                continue;
            }
            double pDist = priority.getLane().getProgressOf(ix.getCenter()) - priority.getFrontProgress();
            double nDist = normal.getLane().getProgressOf(ix.getCenter()) - normal.getFrontProgress();
            if (pDist < -55.0 || pDist > INTERSECTION_HORIZON || nDist < -70.0 || nDist > INTERSECTION_HORIZON) {
                continue;
            }
            double pEta = eta(priority, pDist);
            if (pEta < bestEta) {
                bestEta = pEta;
                best = ix;
            }
        }
        return best;
    }

    private double etaToIntersection(Vehicle vehicle, Intersection ix) {
        if (vehicle == null || ix == null || vehicle.getLane() == null) return Double.POSITIVE_INFINITY;
        double dist = vehicle.getLane().getProgressOf(ix.getCenter()) - vehicle.getFrontProgress();
        return eta(vehicle, dist);
    }

    private double eta(Vehicle vehicle, double distance) {
        if (distance < -45.0) return Double.POSITIVE_INFINITY;
        if (distance <= 0.0) return 0.0;
        double speed = Math.max(MIN_SPEED_FOR_ETA, vehicle != null ? vehicle.getSpeed() : MIN_SPEED_FOR_ETA);
        return distance / speed;
    }

    private Lane resolveTargetLane(Lane lane, Intersection intersection, Vehicle.TurnDecision decision) {
        if (lane == null) return null;
        Vehicle.TurnDecision safe = decision == null ? Vehicle.TurnDecision.STRAIGHT : decision;
        if (lane.isStraightOnly()) return safe == Vehicle.TurnDecision.STRAIGHT ? lane : null;
        if (intersection != null && lane.hasAnyTurnRule(intersection)) {
            return lane.hasTurnRule(intersection, safe) ? lane.getTurnTarget(intersection, safe) : null;
        }
        return safe == Vehicle.TurnDecision.STRAIGHT ? lane : null;
    }
}
