package com.traffic.core;

import com.traffic.map.Intersection;
import com.traffic.map.Lane;
import com.traffic.map.TrafficLight;

/** Vehicle physical state. Behavior is delegated to IDriver. */
public abstract class Vehicle {

    public enum YieldMode {
        NONE,
        YIELD_RIGHT,
        STOP_BEFORE_CONFLICT,
        CLEAR_CONFLICT,
        HOLD_POSITION,
        URGENT_CLEAR_PATH,

        // Legacy names kept so older modules can compile.
        PULL_RIGHT,
        CLEAR_PATH,
        STOP,
        CLEAR_INTERSECTION,
        BLOCKED_YIELD
    }

    public enum ManeuverState {
        NORMAL,
        OVERTAKE_SHIFT_LEFT,
        OVERTAKE_PASSING,
        OVERTAKE_RETURNING,
        YIELDING_RIGHT,
        GAP_FILLING,
        URGENT_CLEARING,
        HOLDING_POSITION,
        STOPPED_FOR_CONFLICT,
        CLEARING_CONFLICT,
        EMERGENCY_CORRIDOR
    }

    public enum TurnDecision {
        STRAIGHT,
        LEFT,
        RIGHT
    }

    public enum IntersectionManeuverState {
        NONE,
        APPROACHING,
        WAITING_BEFORE_INTERSECTION,
        CROSSING_STRAIGHT,
        TURNING_LEFT,
        TURNING_RIGHT,
        EXITING,
        CLEARING_FOR_PRIORITY
    }

    public static final double LEFT_OFFSET = -18.0;
    public static final double CENTER_OFFSET = 0.0;
    public static final double RIGHT_OFFSET = 18.0;
    public static final double MAX_LATERAL_OFFSET = 26.0;

    // Backward-compatible constant aliases.
    public static final double LEFT_LATERAL_OFFSET = LEFT_OFFSET;
    public static final double CENTER_LATERAL_OFFSET = CENTER_OFFSET;
    public static final double RIGHT_LATERAL_OFFSET = RIGHT_OFFSET;

    protected Vector2D position;
    protected double speed;
    protected double angle;
    protected IDriver driver;

    protected String name;
    protected double width;
    protected double height;
    protected boolean isPriority;

    protected YieldMode yieldMode = YieldMode.NONE;
    protected ManeuverState maneuverState = ManeuverState.NORMAL;

    protected Lane lane;
    protected double laneProgress = 0.0;

    protected Lane originalLane;
    protected boolean isChangingLane = false;
    protected Vector2D targetPosition = null;
    protected static final double LANE_CHANGE_SPEED = 120.0;

    protected double laneChangeCooldown = 0.0;
    protected static final double LANE_CHANGE_COOLDOWN = 1.5;
    protected boolean hasOvertaken = false;

    protected double lateralOffset = CENTER_OFFSET;
    protected double targetLateralOffset = CENTER_OFFSET;
    protected double preferredLateralOffset = CENTER_OFFSET;

    protected Vehicle overtakingTarget = null;
    protected double maneuverCooldown = 0.0;

    protected TurnDecision turnDecision = TurnDecision.STRAIGHT;
    protected TurnManeuver activeTurn = null;
    protected Intersection lastIntersectionTurned = null;
    protected Intersection currentIntersection = null;
    protected IntersectionManeuverState intersectionManeuverState = IntersectionManeuverState.NONE;

    // Priority vehicles should give normal vehicles a short chance to yield
    // before committing to aggressive passing or emergency corridor.
    protected Vehicle priorityWaitTarget = null;
    protected double priorityWaitSeconds = 0.0;

    public Vehicle(double x, double y, double speed, IDriver driver) {
        this.position = new Vector2D(x, y);
        this.speed = speed;
        this.angle = 0;
        this.driver = driver;
        this.isPriority = false;
    }

    public void setLane(Lane lane) {
        setLanePosition(lane, 0.0, CENTER_OFFSET);
    }

    /** Compatibility overload for older SpawnPlanner code. */
    public void setLane(Lane lane, double progress, int trackIndex) {
        double offset = lane != null ? lane.getOffsetForTrack(trackIndex) : CENTER_OFFSET;
        setLanePosition(lane, progress, offset);
    }

    /** Compatibility overload: third argument is a track index, not raw pixels. */
    public void setLanePosition(Lane lane, double progress, int trackIndex) {
        double offset = lane != null ? lane.getOffsetForTrack(trackIndex) : CENTER_OFFSET;
        setLanePosition(lane, progress, offset);
    }

    public void setLanePosition(Lane lane, double progress, double lateralOffset) {
        if (this.lane != null && this.lane != lane) {
            this.lane.removeVehicle(this);
        }
        this.lane = lane;
        this.originalLane = lane;
        this.isChangingLane = false;
        this.targetPosition = null;
        this.laneProgress = progress;
        this.lateralOffset = clampLateralForLane(lateralOffset);
        this.targetLateralOffset = this.lateralOffset;
        this.preferredLateralOffset = this.lateralOffset;
        if (lane != null) {
            lane.addVehicle(this);
            syncPositionFromLane();
        }
    }

    /** Legacy API retained for older UI code. Prefer setLanePosition. */
    public void setLaneStartOffset(double offsetX, double offsetY) {
        position.setX(position.getX() + offsetX);
        position.setY(position.getY() + offsetY);
        if (lane != null) {
            laneProgress = lane.getProgressOf(position);
            lateralOffset = clampLateralForLane(lane.getSignedLateralOffset(position));
            targetLateralOffset = lateralOffset;
            preferredLateralOffset = lateralOffset;
            syncPositionFromLane();
        }
    }

    public final void update(double deltaTime) {
        if (laneChangeCooldown > 0) laneChangeCooldown = Math.max(0.0, laneChangeCooldown - deltaTime);
        if (maneuverCooldown > 0) maneuverCooldown = Math.max(0.0, maneuverCooldown - deltaTime);

        if (activeTurn != null) {
            updateTurnManeuver(deltaTime);
            return;
        }

        updateLateralOffset(deltaTime);

        if (isChangingLane && targetPosition != null) {
            updateFormalLaneChange(deltaTime);
            return;
        }

        if (lane != null) {
            laneProgress += speed * deltaTime;
            syncPositionFromLane();
        } else {
            double radians = Math.toRadians(angle);
            position.setX(position.getX() + Math.cos(radians) * speed * deltaTime);
            position.setY(position.getY() + Math.sin(radians) * speed * deltaTime);
        }
    }

    private void updateTurnManeuver(double deltaTime) {
        if (activeTurn == null) return;
        boolean done = activeTurn.advance(speed, deltaTime);
        Vector2D p = activeTurn.pointAtCurrentT();
        Vector2D tangent = activeTurn.tangentAtCurrentT();
        position.setX(p.getX());
        position.setY(p.getY());
        angle = Math.toDegrees(Math.atan2(tangent.getY(), tangent.getX()));
        if (done) {
            completeActiveTurn();
        }
    }

    private void updateLateralOffset(double deltaTime) {
        double diff = targetLateralOffset - lateralOffset;
        if (Math.abs(diff) <= 0.25) {
            lateralOffset = targetLateralOffset;
            if (maneuverState == ManeuverState.GAP_FILLING) {
                preferredLateralOffset = isPriority ? CENTER_OFFSET : targetLateralOffset;
                maneuverState = ManeuverState.NORMAL;
                maneuverCooldown = Math.max(maneuverCooldown, 0.8);
            }
            return;
        }
        double smoothness = switch (maneuverState) {
            case OVERTAKE_SHIFT_LEFT, YIELDING_RIGHT -> 7.0;
            case GAP_FILLING -> 5.2;
            case URGENT_CLEARING -> 4.4;
            case OVERTAKE_RETURNING -> 3.4;
            case EMERGENCY_CORRIDOR -> 6.2;
            case CLEARING_CONFLICT, HOLDING_POSITION -> 4.0;
            default -> 4.8;
        };
        double alpha = 1.0 - Math.exp(-smoothness * Math.max(0.0, deltaTime));
        lateralOffset += diff * alpha;
        if (Math.abs(targetLateralOffset - lateralOffset) < 0.35) {
            lateralOffset = targetLateralOffset;
        }

        if (maneuverState == ManeuverState.GAP_FILLING && isNearTargetLateralOffset(1.2)) {
            // Gap filling is not an overtake. The new slot becomes the temporary
            // natural slot so the car does not immediately drift back.
            preferredLateralOffset = isPriority ? CENTER_OFFSET : targetLateralOffset;
            maneuverState = ManeuverState.NORMAL;
            maneuverCooldown = Math.max(maneuverCooldown, 0.8);
        }
    }

    private void updateFormalLaneChange(double deltaTime) {
        if (lane == null || targetPosition == null) return;

        double radians = Math.toRadians(angle);
        position.setX(position.getX() + Math.cos(radians) * speed * deltaTime);
        position.setY(position.getY() + Math.sin(radians) * speed * deltaTime);

        double projectedProgress = lane.getProgressOf(position);
        Vector2D centerTarget = lane.getPositionAt(projectedProgress, 0.0);
        double dist = MathUtils.distance(position, centerTarget);

        if (dist < 3.0) {
            laneProgress = projectedProgress;
            lateralOffset = CENTER_OFFSET;
            targetLateralOffset = CENTER_OFFSET;
            preferredLateralOffset = CENTER_OFFSET;
            isChangingLane = false;
            targetPosition = null;
            lane.release(this);
            originalLane = lane;
            laneChangeCooldown = LANE_CHANGE_COOLDOWN;
            maneuverCooldown = Math.max(maneuverCooldown, 0.8);
            syncPositionFromLane();
            return;
        }

        double changeAngle = MathUtils.angleTo(position, centerTarget);
        double step = Math.min(LANE_CHANGE_SPEED * deltaTime, dist);
        position.setX(position.getX() + Math.cos(Math.toRadians(changeAngle)) * step);
        position.setY(position.getY() + Math.sin(Math.toRadians(changeAngle)) * step);
        laneProgress = lane.getProgressOf(position);
        angle = lane.getAngleAt(laneProgress);
    }

    private void syncPositionFromLane() {
        if (lane == null) return;
        angle = lane.getAngleAt(laneProgress);
        Vector2D p = lane.getPositionAt(laneProgress, lateralOffset);
        position.setX(p.getX());
        position.setY(p.getY());
    }

    public void startLaneChange(Lane newLane) {
        if (isCommittedToIntersection()) return;
        if (newLane == null || lane == null || lane == newLane) return;
        if (isChangingLane || laneChangeCooldown > 0) return;
        if (!lane.isFormalLaneChangeAllowed()) return;

        boolean isOvertaking = lane.getLeftNeighbor() == newLane;
        originalLane = lane;
        lane.removeVehicle(this);
        lane = newLane;
        newLane.addVehicle(this);
        newLane.reserve(this);

        double targetProgress = newLane.getProgressOf(position);
        targetPosition = newLane.getPositionAt(targetProgress, CENTER_OFFSET);
        isChangingLane = true;
        hasOvertaken = isOvertaking;

        double newAngle = newLane.getAngleAt(targetProgress);
        double diff = Math.abs(angle - newAngle);
        if (diff > 180) diff = 360 - diff;
        if (diff <= 90) angle = newAngle;
    }

    public void makeDecision(TrafficLight nearestLight) {
        if (driver != null) driver.makeDecision(this, nearestLight);
    }

    public boolean isSameDirection(Lane otherLane) {
        if (otherLane == null || lane == null) return false;
        double myAngle = lane.getAngleAt(laneProgress);
        double otherAngle = otherLane.getAngleAt(otherLane.getProgressOf(position));
        double diff = Math.abs(myAngle - otherAngle);
        if (diff > 180) diff = 360 - diff;
        return diff < 90;
    }

    public void beginInLaneOvertake(Vehicle target) {
        if (isCommittedToIntersection()) return;
        overtakingTarget = target;
        maneuverState = ManeuverState.OVERTAKE_SHIFT_LEFT;
        setTargetLateralOffset(LEFT_OFFSET);
    }

    /** Compatibility entry point for SideShiftPlanner/LateralManeuver. */
    public boolean requestManeuver(LateralManeuver maneuver) {
        if (maneuver == null || maneuverCooldown > 0.0 || isCommittedToIntersection()) {
            return false;
        }
        switch (maneuver.getType()) {
            case OVERTAKE -> {
                overtakingTarget = maneuver.getTarget();
                maneuverState = ManeuverState.OVERTAKE_SHIFT_LEFT;
                setTargetLateralOffset(maneuver.getTargetOffset());
                return true;
            }
            case YIELD_RIGHT -> {
                overtakingTarget = null;
                maneuverState = ManeuverState.YIELDING_RIGHT;
                setTargetLateralOffset(maneuver.getTargetOffset());
                return true;
            }
            case RETURN_TO_PREFERRED -> {
                return returnToPreferredOffset();
            }
            case GAP_FILL -> {
                overtakingTarget = null;
                maneuverState = ManeuverState.GAP_FILLING;
                setTargetLateralOffset(maneuver.getTargetOffset());
                return true;
            }
            case EMERGENCY_CORRIDOR -> {
                if (!isPriority) return false;
                overtakingTarget = maneuver.getTarget();
                maneuverState = ManeuverState.EMERGENCY_CORRIDOR;
                setTargetLateralOffset(maneuver.getTargetOffset());
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public void cancelOvertake() {
        if (isOvertaking()) {
            overtakingTarget = null;
            maneuverState = ManeuverState.NORMAL;
            returnToPreferredSlot();
            maneuverCooldown = Math.max(maneuverCooldown, 0.6);
        }
    }

    public void abortLateralManeuverSafely() {
        if (isOvertaking()) {
            overtakingTarget = null;
        }
        if (maneuverState == ManeuverState.OVERTAKE_SHIFT_LEFT
                || maneuverState == ManeuverState.OVERTAKE_PASSING
                || maneuverState == ManeuverState.EMERGENCY_CORRIDOR
                || maneuverState == ManeuverState.GAP_FILLING
                || maneuverState == ManeuverState.YIELDING_RIGHT
                || maneuverState == ManeuverState.URGENT_CLEARING) {
            maneuverState = ManeuverState.NORMAL;
            returnToPreferredSlot();
            maneuverCooldown = Math.max(maneuverCooldown, 0.45);
        }
    }

    public boolean isOvertaking() {
        return maneuverState == ManeuverState.OVERTAKE_SHIFT_LEFT
            || maneuverState == ManeuverState.OVERTAKE_PASSING
            || maneuverState == ManeuverState.OVERTAKE_RETURNING
            || maneuverState == ManeuverState.EMERGENCY_CORRIDOR;
    }

    public boolean isOvertakingInLane() { return isOvertaking(); }

    public void returnToPreferredSlot() {
        setTargetLateralOffset(preferredLateralOffset);
    }

    /** Compatibility wrapper for older SideShiftPlanner code. */
    public boolean returnToPreferredOffset() {
        returnToPreferredSlot();
        maneuverState = ManeuverState.OVERTAKE_RETURNING;
        return true;
    }

    /** True when the vehicle is not already close to its preferred lateral slot. */
    public boolean isAwayFromPreferredOffset() {
        return !isNearPreferredLateralOffset(1.5);
    }

    public double getFrontProgress() { return laneProgress + getLongitudinalLength() / 2.0; }
    public double getRearProgress() { return laneProgress - getLongitudinalLength() / 2.0; }
    public double getLongitudinalLength() { return Math.max(width, height); }

    public boolean isNearTargetLateralOffset(double tolerance) {
        return Math.abs(lateralOffset - targetLateralOffset) <= tolerance;
    }

    public boolean isNearPreferredLateralOffset(double tolerance) {
        return Math.abs(lateralOffset - preferredLateralOffset) <= tolerance;
    }

    private double clampLateral(double offset) {
        return MathUtils.clamp(offset, -MAX_LATERAL_OFFSET, MAX_LATERAL_OFFSET);
    }

    private double clampLateralForLane(double offset) {
        return lane != null ? lane.clampOffset(this, offset) : clampLateral(offset);
    }

    public boolean isUsingEmergencyCorridor() {
        return isPriority && maneuverState == ManeuverState.EMERGENCY_CORRIDOR;
    }

    public boolean isTurning() { return activeTurn != null; }
    public TurnManeuver getActiveTurn() { return activeTurn; }

    public void startTurn(TurnManeuver maneuver) {
        if (maneuver == null || maneuver.getTargetLane() == null) return;
        abortLateralManeuverSafely();
        activeTurn = maneuver;
        maneuver.getTargetLane().reserve(this);
        currentIntersection = maneuver.getIntersection();
        lastIntersectionTurned = maneuver.getIntersection();
        yieldMode = YieldMode.CLEAR_CONFLICT;
        maneuverState = ManeuverState.CLEARING_CONFLICT;
        targetLateralOffset = lateralOffset;
        preferredLateralOffset = lateralOffset;
        intersectionManeuverState = switch (maneuver.getDecision()) {
            case LEFT -> IntersectionManeuverState.TURNING_LEFT;
            case RIGHT -> IntersectionManeuverState.TURNING_RIGHT;
            default -> IntersectionManeuverState.CROSSING_STRAIGHT;
        };
    }

    private void completeActiveTurn() {
        if (activeTurn == null) return;
        TurnManeuver finished = activeTurn;
        Lane targetLane = finished.getTargetLane();
        double entryProgress = targetLane.getProgressOf(position);
        double entryOffset = targetLane.clampOffset(this, targetLane.getSignedLateralOffset(position));

        activeTurn = null;
        setLanePosition(targetLane, entryProgress, entryOffset);
        targetLane.release(this);
        preferredLateralOffset = entryOffset;
        targetLateralOffset = entryOffset;
        currentIntersection = finished.getIntersection();
        lastIntersectionTurned = finished.getIntersection();
        intersectionManeuverState = IntersectionManeuverState.EXITING;
        yieldMode = YieldMode.NONE;
        maneuverState = ManeuverState.NORMAL;
        turnDecision = TurnDecision.STRAIGHT;
    }

    public boolean isCommittedToIntersection() {
        return activeTurn != null
                || intersectionManeuverState == IntersectionManeuverState.CROSSING_STRAIGHT
                || intersectionManeuverState == IntersectionManeuverState.TURNING_LEFT
                || intersectionManeuverState == IntersectionManeuverState.TURNING_RIGHT
                || intersectionManeuverState == IntersectionManeuverState.EXITING
                || intersectionManeuverState == IntersectionManeuverState.CLEARING_FOR_PRIORITY;
    }

    public void returnPriorityToCenterIfIdle() {
        if (isCommittedToIntersection()) return;
        if (isPriority && !isOvertaking()
                && maneuverState != ManeuverState.YIELDING_RIGHT
                && maneuverState != ManeuverState.CLEARING_CONFLICT
                && maneuverState != ManeuverState.HOLDING_POSITION
                && maneuverState != ManeuverState.STOPPED_FOR_CONFLICT) {
            preferredLateralOffset = CENTER_OFFSET;
            setTargetLateralOffset(CENTER_OFFSET);
        }
    }

    public Vector2D getPosition() { return position; }
    public double getSpeed() { return speed; }
    public void setSpeed(double s) { speed = Math.max(0.0, s); }
    public double getAngle() { return angle; }
    public void setAngle(double a) { angle = a; }
    public String getName() { return name; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
    public boolean isPriority() { return isPriority; }
    public Lane getOriginalLane() { return originalLane; }
    public Lane getLane() { return lane; }
    public YieldMode getYieldMode() { return yieldMode; }
    public void setYieldMode(YieldMode m) {
        YieldMode requested = m == null ? YieldMode.NONE : m;

        // Once the vehicle has committed to the intersection, no external rule
        // may make it stop or shift sideways in the conflict area. It must clear.
        if (requested != YieldMode.NONE && isCommittedToIntersection()) {
            yieldMode = YieldMode.CLEAR_CONFLICT;
            maneuverState = ManeuverState.CLEARING_CONFLICT;
            if (intersectionManeuverState == IntersectionManeuverState.CROSSING_STRAIGHT) {
                intersectionManeuverState = IntersectionManeuverState.CLEARING_FOR_PRIORITY;
            }
            return;
        }

        yieldMode = requested;

        // NONE means no new external yield command this frame. It must not
        // cancel an active in-lane maneuver such as GAP_FILLING; the driver
        // decides when old yield states should return to normal.
        if (yieldMode == YieldMode.NONE) {
            return;
        }

        if (isOvertaking() || maneuverState == ManeuverState.GAP_FILLING) {
            return;
        }

        maneuverState = switch (yieldMode) {
            case YIELD_RIGHT, PULL_RIGHT -> ManeuverState.YIELDING_RIGHT;
            case HOLD_POSITION, BLOCKED_YIELD -> ManeuverState.HOLDING_POSITION;
            case STOP_BEFORE_CONFLICT, STOP -> ManeuverState.STOPPED_FOR_CONFLICT;
            case CLEAR_CONFLICT, CLEAR_INTERSECTION -> ManeuverState.CLEARING_CONFLICT;
            case CLEAR_PATH, URGENT_CLEAR_PATH -> ManeuverState.URGENT_CLEARING;
            default -> maneuverState;
        };
    }
    public boolean isChangingLane() { return isChangingLane; }
    public double getLaneChangeCooldown() { return laneChangeCooldown; }
    public boolean hasOvertaken() { return hasOvertaken; }
    public double getLaneProgress() { return laneProgress; }
    /** Compatibility alias for older occupancy/coordinator code. */
    public double getProgress() { return getLaneProgress(); }
    public void setLaneProgress(double p) { laneProgress = p; }

    public double getLateralOffset() { return lateralOffset; }
    public void setLateralOffset(double o) { lateralOffset = clampLateralForLane(o); }
    public double getTargetLateralOffset() { return targetLateralOffset; }
    public void setTargetLateralOffset(double o) { targetLateralOffset = clampLateralForLane(o); }
    public double getPreferredLateralOffset() { return preferredLateralOffset; }
    public void setPreferredLateralOffset(double o) { preferredLateralOffset = clampLateralForLane(o); }

    public ManeuverState getManeuverState() { return maneuverState; }
    public void setManeuverState(ManeuverState state) { maneuverState = state == null ? ManeuverState.NORMAL : state; }
    public Vehicle getOvertakingTarget() { return overtakingTarget; }
    public void setOvertakingTarget(Vehicle target) { overtakingTarget = target; }

    public double addPriorityWaitFor(Vehicle target, double seconds) {
        if (!isPriority || target == null) {
            resetPriorityWait();
            return 0.0;
        }
        if (priorityWaitTarget != target) {
            priorityWaitTarget = target;
            priorityWaitSeconds = 0.0;
        }
        priorityWaitSeconds = Math.min(3.0, priorityWaitSeconds + Math.max(0.0, seconds));
        return priorityWaitSeconds;
    }

    public void resetPriorityWait() {
        priorityWaitTarget = null;
        priorityWaitSeconds = 0.0;
    }

    public double getPriorityWaitSeconds() { return priorityWaitSeconds; }
    public Vehicle getPriorityWaitTarget() { return priorityWaitTarget; }

    public double getManeuverCooldown() { return maneuverCooldown; }
    public void setManeuverCooldown(double seconds) { maneuverCooldown = Math.max(0.0, seconds); }

    public TurnDecision getTurnDecision() { return turnDecision; }
    public void setTurnDecision(TurnDecision decision) {
        turnDecision = decision == null ? TurnDecision.STRAIGHT : decision;
    }

    public Intersection getLastIntersectionTurned() { return lastIntersectionTurned; }
    public void setLastIntersectionTurned(Intersection intersection) { lastIntersectionTurned = intersection; }

    public Intersection getCurrentIntersection() { return currentIntersection; }
    public void setCurrentIntersection(Intersection intersection) { currentIntersection = intersection; }

    public IntersectionManeuverState getIntersectionManeuverState() { return intersectionManeuverState; }
    public void setIntersectionManeuverState(IntersectionManeuverState state) {
        intersectionManeuverState = state == null ? IntersectionManeuverState.NONE : state;
    }

    public abstract String getTypeName();
}
