package com.traffic.core;

/**
 * Small immutable command object used by SideShiftPlanner.
 * It is intentionally minimal so older planner code can work with the newer
 * Vehicle lateral-offset state machine.
 */
public final class LateralManeuver {
    public enum Type { OVERTAKE, YIELD_RIGHT, RETURN_TO_PREFERRED, GAP_FILL, EMERGENCY_CORRIDOR }

    private final Type type;
    private final double targetOffset;
    private final Vehicle target;

    private LateralManeuver(Type type, double targetOffset, Vehicle target) {
        this.type = type;
        this.targetOffset = targetOffset;
        this.target = target;
    }

    public static LateralManeuver overtake(double targetOffset, Vehicle target) {
        return new LateralManeuver(Type.OVERTAKE, targetOffset, target);
    }

    public static LateralManeuver yieldRight(double targetOffset, Vehicle priorityVehicle) {
        return new LateralManeuver(Type.YIELD_RIGHT, targetOffset, priorityVehicle);
    }

    public static LateralManeuver returnToPreferred(double targetOffset) {
        return new LateralManeuver(Type.RETURN_TO_PREFERRED, targetOffset, null);
    }

    public static LateralManeuver gapFill(double targetOffset) {
        return new LateralManeuver(Type.GAP_FILL, targetOffset, null);
    }

    public static LateralManeuver emergencyCorridor(double targetOffset, Vehicle obstacle) {
        return new LateralManeuver(Type.EMERGENCY_CORRIDOR, targetOffset, obstacle);
    }

    public Type getType() { return type; }
    public double getTargetOffset() { return targetOffset; }
    public Vehicle getTarget() { return target; }
}
