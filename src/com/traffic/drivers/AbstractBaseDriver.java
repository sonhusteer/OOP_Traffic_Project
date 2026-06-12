package com.traffic.drivers;

import com.traffic.core.IDriver;
import com.traffic.core.MathUtils;
import com.traffic.core.Vehicle;
import com.traffic.map.Lane;
import com.traffic.map.TrafficLight;

/** Shared driver brain with a small in-lane overtaking state machine. */
public abstract class AbstractBaseDriver implements IDriver {

    private static final double RETURN_FRONT_GAP = 75.0;
    private static final double RETURN_BACK_GAP = 45.0;
    private static final double PASS_CLEAR_GAP = 28.0;

    protected abstract double getBrakeDistance();
    protected abstract double getStopDistance();
    protected abstract double getMaxSpeed();
    protected abstract double getMinSpeed();
    protected abstract double getSafeDistance();
    protected abstract double getOvertakeGap();
    protected abstract boolean canOvertake();
    protected boolean obeyTrafficLight() { return true; }

    @Override
    public void makeDecision(Vehicle vehicle, TrafficLight nextLight) {
        if (vehicle.isChangingLane()) return;

        Vehicle.YieldMode mode = vehicle.getYieldMode();
        if (mode == Vehicle.YieldMode.STOP_BEFORE_CONFLICT) {
            vehicle.cancelOvertake();
            vehicle.setManeuverState(Vehicle.ManeuverState.STOPPED_FOR_CONFLICT);
            vehicle.returnToPreferredSlot();
            vehicle.setSpeed(0.0);
            return;
        }

        if (mode == Vehicle.YieldMode.CLEAR_CONFLICT) {
            vehicle.cancelOvertake();
            vehicle.setManeuverState(Vehicle.ManeuverState.CLEARING_CONFLICT);
            vehicle.returnToPreferredSlot();
            vehicle.setSpeed(Math.max(vehicle.getSpeed(), getMaxSpeed() * 0.75));
            return;
        }

        if (mode == Vehicle.YieldMode.YIELD_RIGHT) {
            vehicle.cancelOvertake();
            vehicle.setManeuverState(Vehicle.ManeuverState.YIELDING_RIGHT);
            vehicle.setTargetLateralOffset(Vehicle.RIGHT_OFFSET);
            vehicle.setSpeed(Math.min(getMaxSpeed() * 0.55,
                    vehicle.getSpeed() > 0.0 ? vehicle.getSpeed() : getMaxSpeed() * 0.45));
            return;
        }

        if (vehicle.getManeuverState() == Vehicle.ManeuverState.YIELDING_RIGHT
                || vehicle.getManeuverState() == Vehicle.ManeuverState.STOPPED_FOR_CONFLICT
                || vehicle.getManeuverState() == Vehicle.ManeuverState.CLEARING_CONFLICT) {
            vehicle.setManeuverState(Vehicle.ManeuverState.NORMAL);
            vehicle.returnToPreferredSlot();
        }

        if (handleActiveOvertake(vehicle)) {
            vehicle.setSpeed(getMaxSpeed());
            return;
        }

        SpeedDecision speedDecision = applyTrafficLightRule(vehicle, nextLight);
        double targetSpeed = speedDecision.targetSpeed;
        boolean stoppingForLight = speedDecision.stoppingForLight;

        Lane lane = vehicle.getLane();
        if (lane != null) {
            Vehicle inFront = lane.getVehicleAhead(vehicle);
            if (inFront != null) {
                double longitudinalGap = inFront.getRearProgress() - vehicle.getFrontProgress();
                if (longitudinalGap <= getSafeDistance()) {
                    if (shouldStartOvertake(vehicle, inFront, stoppingForLight)) {
                        startOvertake(vehicle, inFront);
                        vehicle.setSpeed(getMaxSpeed());
                        return;
                    }

                    targetSpeed = Math.min(targetSpeed, inFront.getSpeed());
                    if (longitudinalGap <= getSafeDistance() * 0.35) {
                        targetSpeed = getMinSpeed();
                    }
                }
            }

            if (!vehicle.isOvertaking()
                    && Math.abs(vehicle.getLateralOffset() - vehicle.getPreferredLateralOffset()) > 0.5) {
                vehicle.returnToPreferredSlot();
            }

            if (tryKeepRightAfterFormalOvertake(vehicle, targetSpeed)) return;
        }

        vehicle.setSpeed(targetSpeed);
    }

    private record SpeedDecision(double targetSpeed, boolean stoppingForLight) {}

    private SpeedDecision applyTrafficLightRule(Vehicle vehicle, TrafficLight nextLight) {
        double targetSpeed = getMaxSpeed();
        boolean stoppingForLight = false;

        Lane logicalLane = vehicle.getOriginalLane() != null ? vehicle.getOriginalLane() : vehicle.getLane();
        TrafficLight logicalLight = logicalLane != null ? logicalLane.getLight() : nextLight;

        if (obeyTrafficLight() && logicalLight != null && logicalLane != null) {
            double stopProgress = logicalLane.getStopProgress();
            double frontProgress = vehicle.getLane() == logicalLane
                    ? vehicle.getFrontProgress()
                    : logicalLane.getProgressOf(vehicle.getPosition()) + vehicle.getLongitudinalLength() / 2.0;
            double distToStop = stopProgress - frontProgress;
            boolean isPastStop = distToStop < -3.0;

            if (!isPastStop) {
                if (logicalLight.isRed()) {
                    stoppingForLight = true;
                    if (distToStop <= getStopDistance()) {
                        targetSpeed = getMinSpeed();
                    } else if (distToStop <= getBrakeDistance()) {
                        double ratio = (distToStop - getStopDistance())
                                / Math.max(1.0, getBrakeDistance() - getStopDistance());
                        targetSpeed = MathUtils.clamp(getMaxSpeed() * ratio, getMinSpeed(), getMaxSpeed());
                    }
                } else if (logicalLight.isYellow() && this instanceof AggressiveDriver) {
                    targetSpeed = getMaxSpeed() * 1.1;
                }
            }
        }
        return new SpeedDecision(targetSpeed, stoppingForLight);
    }

    private boolean handleActiveOvertake(Vehicle vehicle) {
        if (!vehicle.isOvertaking()) return false;
        Lane lane = vehicle.getLane();
        Vehicle target = vehicle.getOvertakingTarget();

        if (lane == null || target == null || target.getLane() != lane) {
            vehicle.returnToPreferredSlot();
            vehicle.setManeuverState(Vehicle.ManeuverState.OVERTAKE_RETURNING);
        }

        switch (vehicle.getManeuverState()) {
            case OVERTAKE_SHIFT_LEFT -> {
                vehicle.setTargetLateralOffset(Vehicle.LEFT_OFFSET);
                if (Math.abs(vehicle.getLateralOffset() - Vehicle.LEFT_OFFSET) <= 3.0) {
                    vehicle.setManeuverState(Vehicle.ManeuverState.OVERTAKE_PASSING);
                }
                return true;
            }
            case OVERTAKE_PASSING -> {
                vehicle.setTargetLateralOffset(Vehicle.LEFT_OFFSET);
                target = vehicle.getOvertakingTarget();
                if (target == null || hasPassedTarget(vehicle, target)) {
                    if (lane == null || lane.isLateralSpaceFree(vehicle,
                            vehicle.getPreferredLateralOffset(), RETURN_FRONT_GAP, RETURN_BACK_GAP)) {
                        vehicle.returnToPreferredSlot();
                        vehicle.setManeuverState(Vehicle.ManeuverState.OVERTAKE_RETURNING);
                    }
                }
                return true;
            }
            case OVERTAKE_RETURNING -> {
                if (lane != null && !vehicle.isNearPreferredLateralOffset(5.0)
                        && !lane.isLateralSpaceFree(vehicle,
                            vehicle.getPreferredLateralOffset(), RETURN_FRONT_GAP, RETURN_BACK_GAP)) {
                    vehicle.setTargetLateralOffset(Vehicle.LEFT_OFFSET);
                    vehicle.setManeuverState(Vehicle.ManeuverState.OVERTAKE_PASSING);
                    return true;
                }

                vehicle.returnToPreferredSlot();
                if (vehicle.isNearPreferredLateralOffset(2.0)) {
                    vehicle.setOvertakingTarget(null);
                    vehicle.setManeuverState(Vehicle.ManeuverState.NORMAL);
                    vehicle.setManeuverCooldown(1.0);
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean shouldStartOvertake(Vehicle vehicle, Vehicle inFront, boolean stoppingForLight) {
        if (!canOvertake() || stoppingForLight) return false;
        if (vehicle.getManeuverCooldown() > 0.0 || vehicle.getLaneChangeCooldown() > 0.0) return false;
        if (vehicle.getLane() == null || inFront == null || inFront.isPriority()) return false;

        Lane lane = vehicle.getLane();
        if (lane.isFormalLaneChangeAllowed()) {
            Lane left = lane.getLeftNeighbor();
            if (left != null && vehicle.isSameDirection(left)
                    && left.isSafeToEnter(vehicle.getPosition(), getOvertakeGap())) {
                return true;
            }
        }

        return lane.isInLaneOvertakeAllowed()
                && Math.abs(vehicle.getLateralOffset() - Vehicle.LEFT_OFFSET) > 3.0
                && lane.isLateralSpaceFree(vehicle, Vehicle.LEFT_OFFSET, getOvertakeGap() + 45.0, 35.0);
    }

    private void startOvertake(Vehicle vehicle, Vehicle inFront) {
        Lane lane = vehicle.getLane();
        if (lane != null && lane.isFormalLaneChangeAllowed()) {
            Lane left = lane.getLeftNeighbor();
            if (left != null && vehicle.isSameDirection(left)
                    && left.isSafeToEnter(vehicle.getPosition(), getOvertakeGap())) {
                vehicle.startLaneChange(left);
                return;
            }
        }
        vehicle.beginInLaneOvertake(inFront);
    }

    private boolean hasPassedTarget(Vehicle vehicle, Vehicle target) {
        if (target == null || target.getLane() != vehicle.getLane()) return true;
        return vehicle.getRearProgress() > target.getFrontProgress() + PASS_CLEAR_GAP;
    }

    private boolean tryKeepRightAfterFormalOvertake(Vehicle vehicle, double targetSpeed) {
        Lane lane = vehicle.getLane();
        if (!vehicle.hasOvertaken()
                || vehicle.getLaneChangeCooldown() > 0.0
                || lane == null
                || !lane.isFormalLaneChangeAllowed()
                || lane.getRightNeighbor() == null) {
            return false;
        }

        Lane right = lane.getRightNeighbor();
        if (!vehicle.isSameDirection(right)) return false;

        Vehicle frontRight = right.getVehicleAhead(vehicle);
        double gapRight = frontRight != null
                ? frontRight.getRearProgress() - vehicle.getFrontProgress()
                : Double.MAX_VALUE;

        if (right.isSafeToEnter(vehicle.getPosition(), getSafeDistance())
                && gapRight > getSafeDistance() * 2.0) {
            vehicle.startLaneChange(right);
            vehicle.setSpeed(targetSpeed);
            return true;
        }
        return false;
    }
}
