package com.traffic.core;

import com.traffic.map.Intersection;
import com.traffic.map.Lane;

/** Immutable per-tick cached relation between one priority vehicle and one normal vehicle. */
public final class PriorityRouteContext {
    private final Vehicle priority;
    private final Vehicle normal;
    private final Intersection intersection;
    private final PriorityRouteRelation relation;
    private final Lane priorityTargetLane;
    private final Lane normalTargetLane;
    private final double priorityEta;
    private final double normalEta;
    private final double longitudinalGap;

    public PriorityRouteContext(Vehicle priority,
                                Vehicle normal,
                                Intersection intersection,
                                PriorityRouteRelation relation,
                                Lane priorityTargetLane,
                                Lane normalTargetLane,
                                double priorityEta,
                                double normalEta,
                                double longitudinalGap) {
        this.priority = priority;
        this.normal = normal;
        this.intersection = intersection;
        this.relation = relation == null ? PriorityRouteRelation.UNRELATED : relation;
        this.priorityTargetLane = priorityTargetLane;
        this.normalTargetLane = normalTargetLane;
        this.priorityEta = priorityEta;
        this.normalEta = normalEta;
        this.longitudinalGap = longitudinalGap;
    }

    public static PriorityRouteContext unrelated(Vehicle priority, Vehicle normal) {
        return new PriorityRouteContext(priority, normal, null,
                PriorityRouteRelation.UNRELATED, null, null,
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    public Vehicle getPriority() { return priority; }
    public Vehicle getNormal() { return normal; }
    public Intersection getIntersection() { return intersection; }
    public PriorityRouteRelation getRelation() { return relation; }
    public Lane getPriorityTargetLane() { return priorityTargetLane; }
    public Lane getNormalTargetLane() { return normalTargetLane; }
    public double getPriorityEta() { return priorityEta; }
    public double getNormalEta() { return normalEta; }
    public double getLongitudinalGap() { return longitudinalGap; }

    public boolean isRelated() { return relation != PriorityRouteRelation.UNRELATED; }
    public boolean isQueueLike() { return relation.isQueueLike(); }
    public boolean requiresIntersectionBlock() { return relation.requiresIntersectionBlock(); }
    public boolean allowsSideYield() { return relation.allowsSideYield(); }
}
