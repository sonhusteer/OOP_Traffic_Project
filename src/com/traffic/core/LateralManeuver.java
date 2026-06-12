package com.traffic.core;

/**
 * Tang 5 clean-code: mo ta mot thao tac dich ngang trong cung Lane.
 *
 * LateralManeuver khong doi Vehicle.lane. No chi noi xe nen tien toi
 * targetOffset nao va thao tac do co muc uu tien bao nhieu.
 */
public final class LateralManeuver {

    public enum Type {
        NONE,
        OVERTAKE,
        RETURN_TO_TRACK,
        YIELD_RIGHT,
        EMERGENCY_PASS
    }

    private final Type type;
    private final double targetOffset;
    private final Vehicle targetVehicle;
    private final int priority;

    private LateralManeuver(Type type, double targetOffset, Vehicle targetVehicle, int priority) {
        this.type = type;
        this.targetOffset = targetOffset;
        this.targetVehicle = targetVehicle;
        this.priority = priority;
    }

    public static LateralManeuver none() {
        return new LateralManeuver(Type.NONE, 0.0, null, 0);
    }

    public static LateralManeuver overtake(double offset, Vehicle target) {
        return new LateralManeuver(Type.OVERTAKE, offset, target, 2);
    }

    public static LateralManeuver returnToTrack(double offset) {
        return new LateralManeuver(Type.RETURN_TO_TRACK, offset, null, 1);
    }

    public static LateralManeuver yieldRight(double offset, Vehicle priorityVehicle) {
        return new LateralManeuver(Type.YIELD_RIGHT, offset, priorityVehicle, 3);
    }

    public static LateralManeuver emergencyPass(double offset, Vehicle target) {
        return new LateralManeuver(Type.EMERGENCY_PASS, offset, target, 4);
    }

    public boolean isActive() { return type != Type.NONE; }
    public Type getType() { return type; }
    public double getTargetOffset() { return targetOffset; }
    public Vehicle getTargetVehicle() { return targetVehicle; }
    public int getPriority() { return priority; }
}
