package com.traffic.core;

import com.traffic.map.Lane;
import com.traffic.map.TrafficLight;

/** Vehicle physical state. Behavior is delegated to IDriver. */
public abstract class Vehicle {

    public enum YieldMode {
        NONE,
        YIELD_RIGHT,
        STOP_BEFORE_CONFLICT,
        CLEAR_CONFLICT
    }

    public enum ManeuverState {
        NORMAL,
        OVERTAKE_SHIFT_LEFT,
        OVERTAKE_PASSING,
        OVERTAKE_RETURNING,
        YIELDING_RIGHT,
        STOPPED_FOR_CONFLICT,
        CLEARING_CONFLICT
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

    public void setLanePosition(Lane lane, double progress, double lateralOffset) {
        if (this.lane != null && this.lane != lane) {
            this.lane.removeVehicle(this);
        }
        this.lane = lane;
        this.originalLane = lane;
        this.isChangingLane = false;
        this.targetPosition = null;
        this.laneProgress = progress;
        this.lateralOffset = clampLateral(lateralOffset);
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
            lateralOffset = clampLateral(lane.getSignedLateralOffset(position));
            targetLateralOffset = lateralOffset;
            preferredLateralOffset = lateralOffset;
            syncPositionFromLane();
        }
    }

    public final void update(double deltaTime) {
        if (laneChangeCooldown > 0) laneChangeCooldown = Math.max(0.0, laneChangeCooldown - deltaTime);
        if (maneuverCooldown > 0) maneuverCooldown = Math.max(0.0, maneuverCooldown - deltaTime);

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

    private void updateLateralOffset(double deltaTime) {
        double diff = targetLateralOffset - lateralOffset;
        if (Math.abs(diff) <= 0.25) {
            lateralOffset = targetLateralOffset;
            return;
        }
        double smoothness = switch (maneuverState) {
            case OVERTAKE_SHIFT_LEFT, YIELDING_RIGHT -> 7.0;
            case OVERTAKE_RETURNING -> 3.4;
            case CLEARING_CONFLICT -> 4.0;
            default -> 4.8;
        };
        double alpha = 1.0 - Math.exp(-smoothness * Math.max(0.0, deltaTime));
        lateralOffset += diff * alpha;
        if (Math.abs(targetLateralOffset - lateralOffset) < 0.35) {
            lateralOffset = targetLateralOffset;
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
        overtakingTarget = target;
        maneuverState = ManeuverState.OVERTAKE_SHIFT_LEFT;
        setTargetLateralOffset(LEFT_OFFSET);
    }

    public void cancelOvertake() {
        if (isOvertaking()) {
            overtakingTarget = null;
            maneuverState = ManeuverState.NORMAL;
            returnToPreferredSlot();
            maneuverCooldown = Math.max(maneuverCooldown, 0.6);
        }
    }

    public boolean isOvertaking() {
        return maneuverState == ManeuverState.OVERTAKE_SHIFT_LEFT
            || maneuverState == ManeuverState.OVERTAKE_PASSING
            || maneuverState == ManeuverState.OVERTAKE_RETURNING;
    }

    public boolean isOvertakingInLane() { return isOvertaking(); }

    public void returnToPreferredSlot() {
        setTargetLateralOffset(preferredLateralOffset);
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
        yieldMode = m == null ? YieldMode.NONE : m;
        if (!isOvertaking()) {
            maneuverState = switch (yieldMode) {
                case YIELD_RIGHT -> ManeuverState.YIELDING_RIGHT;
                case STOP_BEFORE_CONFLICT -> ManeuverState.STOPPED_FOR_CONFLICT;
                case CLEAR_CONFLICT -> ManeuverState.CLEARING_CONFLICT;
                default -> ManeuverState.NORMAL;
            };
        }
    }
    public boolean isChangingLane() { return isChangingLane; }
    public double getLaneChangeCooldown() { return laneChangeCooldown; }
    public boolean hasOvertaken() { return hasOvertaken; }
    public double getLaneProgress() { return laneProgress; }
    public void setLaneProgress(double p) { laneProgress = p; }

    public double getLateralOffset() { return lateralOffset; }
    public void setLateralOffset(double o) { lateralOffset = clampLateral(o); }
    public double getTargetLateralOffset() { return targetLateralOffset; }
    public void setTargetLateralOffset(double o) { targetLateralOffset = clampLateral(o); }
    public double getPreferredLateralOffset() { return preferredLateralOffset; }
    public void setPreferredLateralOffset(double o) { preferredLateralOffset = clampLateral(o); }

    public ManeuverState getManeuverState() { return maneuverState; }
    public void setManeuverState(ManeuverState state) { maneuverState = state == null ? ManeuverState.NORMAL : state; }
    public Vehicle getOvertakingTarget() { return overtakingTarget; }
    public void setOvertakingTarget(Vehicle target) { overtakingTarget = target; }
    public double getManeuverCooldown() { return maneuverCooldown; }
    public void setManeuverCooldown(double seconds) { maneuverCooldown = Math.max(0.0, seconds); }

    public abstract String getTypeName();
}
