package com.traffic.core;

/**
 * Final per-frame command for one vehicle.
 *
 * Stage 2 keeps the existing managers/drivers compatible, but funnels the
 * final corrections through this object before Vehicle.update().  This makes
 * conflict resolution explicit: priority-route queueing, red-light/turn gates,
 * and lateral-cancel rules no longer rely only on ad-hoc writes to Vehicle.
 */
public final class VehicleDecision {
    Double targetSpeed;
    private Double targetLateralOffset;
    private Double preferredLateralOffset;
    private Vehicle.YieldMode yieldMode;
    private Vehicle.ManeuverState maneuverState;
    private Vehicle.IntersectionManeuverState intersectionState;

    private boolean cancelOvertake;
    private boolean abortLateralManeuver;
    private boolean returnToPreferredSlot;
    private boolean clearPriorityYieldLock;
    private boolean resetPriorityWait;

    private String reason;

    public static VehicleDecision empty() {
        return new VehicleDecision();
    }

    public VehicleDecision reason(String reason) {
        this.reason = reason;
        return this;
    }

    public String getReason() {
        return reason;
    }

    public VehicleDecision targetSpeed(double speed) {
        this.targetSpeed = Math.max(0.0, speed);
        return this;
    }

    /**
     * Raise the commanded speed floor. This keeps clearing/turning vehicles from
     * freezing when a previous module wrote a lower speed in the same frame.
     */
    public VehicleDecision floorSpeed(double speed) {
        double floor = Math.max(0.0, speed);
        if (targetSpeed == null || floor > targetSpeed) {
            targetSpeed(floor);
        }
        return this;
    }

    /**
     * Apply an upper speed cap. Use this when the merger aborts a lateral
     * maneuver near an intersection/red light so the current frame does not keep
     * the driver's previous high overtake speed.
     */
    public VehicleDecision capSpeed(double speed) {
        double cap = Math.max(0.0, speed);
        if (targetSpeed == null || cap < targetSpeed) {
            targetSpeed(cap);
        }
        return this;
    }

    /** @deprecated Use floorSpeed(double) for explicit semantics. */
    @Deprecated
    public VehicleDecision maxSpeed(double speed) {
        return floorSpeed(speed);
    }

    /** @deprecated Use capSpeed(double) for explicit semantics. */
    @Deprecated
    public VehicleDecision minSpeed(double speed) {
        return capSpeed(speed);
    }

    public VehicleDecision targetLateralOffset(double offset) {
        this.targetLateralOffset = offset;
        return this;
    }

    public VehicleDecision preferredLateralOffset(double offset) {
        this.preferredLateralOffset = offset;
        return this;
    }

    public VehicleDecision yieldMode(Vehicle.YieldMode mode) {
        this.yieldMode = mode == null ? Vehicle.YieldMode.NONE : mode;
        return this;
    }

    public VehicleDecision maneuverState(Vehicle.ManeuverState state) {
        this.maneuverState = state == null ? Vehicle.ManeuverState.NORMAL : state;
        return this;
    }

    public VehicleDecision intersectionState(Vehicle.IntersectionManeuverState state) {
        this.intersectionState = state == null ? Vehicle.IntersectionManeuverState.NONE : state;
        return this;
    }

    public VehicleDecision cancelOvertake() {
        this.cancelOvertake = true;
        return this;
    }

    public VehicleDecision abortLateralManeuver() {
        this.abortLateralManeuver = true;
        return this;
    }

    public VehicleDecision returnToPreferredSlot() {
        this.returnToPreferredSlot = true;
        return this;
    }

    public VehicleDecision clearPriorityYieldLock() {
        this.clearPriorityYieldLock = true;
        return this;
    }

    public VehicleDecision resetPriorityWait() {
        this.resetPriorityWait = true;
        return this;
    }

    public boolean hasAnyCommand() {
        return targetSpeed != null
                || targetLateralOffset != null
                || preferredLateralOffset != null
                || yieldMode != null
                || maneuverState != null
                || intersectionState != null
                || cancelOvertake
                || abortLateralManeuver
                || returnToPreferredSlot
                || clearPriorityYieldLock
                || resetPriorityWait;
    }

    public void applyTo(Vehicle vehicle) {
        if (vehicle == null || !hasAnyCommand()) return;

        if (clearPriorityYieldLock) {
            vehicle.clearPriorityYieldLock();
        }
        if (resetPriorityWait) {
            vehicle.resetPriorityWait();
        }

        // Abort/cancel first so later explicit target/preferred commands can
        // re-align the vehicle to the route slot selected by TurnCoordinator.
        if (abortLateralManeuver) {
            vehicle.abortLateralManeuverSafely();
        } else if (cancelOvertake) {
            vehicle.cancelOvertake();
        }

        if (yieldMode != null) {
            vehicle.setYieldMode(yieldMode);
        }
        if (maneuverState != null) {
            vehicle.setManeuverState(maneuverState);
        }
        if (intersectionState != null) {
            vehicle.setIntersectionManeuverState(intersectionState);
        }
        if (preferredLateralOffset != null) {
            vehicle.setPreferredLateralOffset(preferredLateralOffset);
        }
        if (returnToPreferredSlot) {
            vehicle.returnToPreferredSlot();
        }
        if (targetLateralOffset != null) {
            vehicle.setTargetLateralOffset(targetLateralOffset);
        }
        if (targetSpeed != null) {
            vehicle.setSpeed(targetSpeed);
        }
    }
}
