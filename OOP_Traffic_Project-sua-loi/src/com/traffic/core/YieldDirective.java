package com.traffic.core;

/** A typed yielding directive. Empty/none means: do not side-shift; use another policy. */
public final class YieldDirective {
    public enum Type {
        NONE,
        FOLLOW_QUEUE,
        SIDE_SHIFT,
        HOLD_POSITION,
        CLEAR_INTERSECTION
    }

    private static final YieldDirective NONE = new YieldDirective(Type.NONE, Double.NaN, "NONE");
    private static final YieldDirective FOLLOW_QUEUE = new YieldDirective(Type.FOLLOW_QUEUE, Double.NaN, "FOLLOW_QUEUE");
    private static final YieldDirective HOLD = new YieldDirective(Type.HOLD_POSITION, Double.NaN, "HOLD_POSITION");
    private static final YieldDirective CLEAR = new YieldDirective(Type.CLEAR_INTERSECTION, Double.NaN, "CLEAR_INTERSECTION");

    private final Type type;
    private final double targetOffset;
    private final String reason;

    private YieldDirective(Type type, double targetOffset, String reason) {
        this.type = type == null ? Type.NONE : type;
        this.targetOffset = targetOffset;
        this.reason = reason == null ? this.type.name() : reason;
    }

    public static YieldDirective none() { return NONE; }
    public static YieldDirective followQueue() { return FOLLOW_QUEUE; }
    public static YieldDirective hold() { return HOLD; }
    public static YieldDirective clearIntersection() { return CLEAR; }
    public static YieldDirective sideShift(double offset, String reason) {
        return new YieldDirective(Type.SIDE_SHIFT, offset, reason);
    }

    public Type getType() { return type; }
    public double getTargetOffset() { return targetOffset; }
    public String getReason() { return reason; }

    public boolean hasTargetOffset() { return type == Type.SIDE_SHIFT && !Double.isNaN(targetOffset); }
    public boolean isNone() { return type == Type.NONE; }
}
