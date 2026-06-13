package com.traffic.core;

/**
 * Route-level relationship between a priority vehicle and a normal vehicle.
 * This is deliberately separate from Vehicle.YieldMode. YieldMode describes an
 * immediate physical response; PriorityRouteRelation describes why two routes
 * matter in this frame.
 */
public enum PriorityRouteRelation {
    UNRELATED,

    /** Same source lane / same planned movement / same target lane: handle as a queue. */
    SAME_QUEUE,

    /** Priority goes straight and a normal straight vehicle ahead can split to an edge. */
    PRIORITY_STRAIGHT_NORMAL_STRAIGHT_AHEAD,

    /** Priority goes straight but the normal ahead is preparing L/R; do not side-yield blindly. */
    PRIORITY_STRAIGHT_NORMAL_TURNING_AHEAD,

    /** Same lane but the movements diverge. Use a cautious side-yield only if it helps. */
    SAME_LANE_DIVERGING,

    /** Routes cross at the same intersection and their ETA windows overlap. */
    CROSSING_CONFLICT,

    /** Different approaches want the same exit/entry space in the same ETA window. */
    SHARED_EXIT_CONFLICT;

    public boolean isQueueLike() {
        return this == SAME_QUEUE
                || this == PRIORITY_STRAIGHT_NORMAL_TURNING_AHEAD;
    }

    public boolean requiresIntersectionBlock() {
        return this == CROSSING_CONFLICT || this == SHARED_EXIT_CONFLICT;
    }

    public boolean allowsSideYield() {
        return this == PRIORITY_STRAIGHT_NORMAL_STRAIGHT_AHEAD
                || this == SAME_LANE_DIVERGING;
    }
}
