package com.traffic.drivers;

import com.traffic.core.IDriver;
import com.traffic.core.MathUtils;
import com.traffic.core.Vehicle;
import com.traffic.map.Lane;
import com.traffic.map.LaneControlPoint;
import com.traffic.map.TrafficLight;

/**
 * Shared driver brain.
 *
 * New behavior order:
 * 1. emergency / conflict modes
 * 2. active lateral maneuver
 * 3. traffic light speed rule
 * 4. low-speed gap filling near queues/intersections
 * 5. in-lane overtake/avoidance only; no formal lane jump
 * 6. follow front vehicle and return to preferred slot
 */
public abstract class AbstractBaseDriver implements IDriver {

    private static final double RETURN_FRONT_GAP = 75.0;
    private static final double RETURN_BACK_GAP = 45.0;
    private static final double PASS_CLEAR_GAP = 36.0;

    private static final double GAP_FILL_FRONT_GAP = 48.0;
    private static final double GAP_FILL_REAR_GAP = 34.0;
    private static final double GAP_FILL_QUEUE_GAP = 78.0;

    private static final double URGENT_FRONT_GAP = 76.0;
    private static final double URGENT_REAR_GAP = 40.0;
    private static final double URGENT_SPEED_FACTOR = 1.18;
    private static final double URGENT_MIN_FACTOR = 0.72;

    private static final double PRIORITY_PATIENCE_SECONDS = 0.75;
    private static final double PRIORITY_PATIENCE_TICK = 0.035;
    private static final double PRIORITY_WAIT_LOOKAHEAD = 95.0;
    private static final double PRIORITY_CRITICAL_GAP = 22.0;

    private final SideShiftPlanner sideShiftPlanner = new SideShiftPlanner();

    protected abstract double getBrakeDistance();
    protected abstract double getStopDistance();
    protected abstract double getMaxSpeed();
    protected abstract double getMinSpeed();
    protected abstract double getSafeDistance();
    protected abstract double getOvertakeGap();
    protected abstract boolean canOvertake();
    protected boolean obeyTrafficLight() { return true; }

    /** Aggressive and emergency drivers may use a center/middle gap before edge passing. */
    protected boolean canUseMiddleGap() { return false; }

    /** Only emergency drivers may use the yellow-line emergency corridor. */
    protected boolean canUseEmergencyCorridor() { return false; }

    @Override
    public void makeDecision(Vehicle vehicle, TrafficLight nextLight) {
        if (vehicle == null || vehicle.isChangingLane()) return;

        Vehicle.YieldMode mode = vehicle.getYieldMode();
        if (vehicle.isTurning()) {
            vehicle.cancelOvertake();
            vehicle.setManeuverState(Vehicle.ManeuverState.CLEARING_CONFLICT);
            vehicle.setSpeed(Math.max(vehicle.getSpeed(), turningSpeedLimit(vehicle)));
            return;
        }

        if (vehicle.isCommittedToIntersection()
                && mode != Vehicle.YieldMode.STOP_BEFORE_CONFLICT
                && mode != Vehicle.YieldMode.STOP
                && mode != Vehicle.YieldMode.HOLD_POSITION
                && mode != Vehicle.YieldMode.BLOCKED_YIELD) {
            vehicle.cancelOvertake();
            vehicle.setManeuverState(Vehicle.ManeuverState.CLEARING_CONFLICT);
            vehicle.setTargetLateralOffset(vehicle.getLateralOffset());
            vehicle.setSpeed(Math.max(vehicle.getSpeed(), getMaxSpeed() * 0.75));
            return;
        }

        if (mode == Vehicle.YieldMode.STOP_BEFORE_CONFLICT || mode == Vehicle.YieldMode.STOP) {
            vehicle.cancelOvertake();
            vehicle.setManeuverState(Vehicle.ManeuverState.STOPPED_FOR_CONFLICT);
            vehicle.returnToPreferredSlot();
            vehicle.setSpeed(0.0);
            return;
        }

        if (mode == Vehicle.YieldMode.HOLD_POSITION || mode == Vehicle.YieldMode.BLOCKED_YIELD) {
            vehicle.cancelOvertake();
            vehicle.setManeuverState(Vehicle.ManeuverState.HOLDING_POSITION);
            vehicle.setTargetLateralOffset(vehicle.getLateralOffset());
            vehicle.setSpeed(Math.min(vehicle.getSpeed(), getMaxSpeed() * 0.35));
            return;
        }

        if (mode == Vehicle.YieldMode.CLEAR_CONFLICT
                || mode == Vehicle.YieldMode.CLEAR_INTERSECTION) {
            vehicle.cancelOvertake();
            vehicle.setManeuverState(Vehicle.ManeuverState.CLEARING_CONFLICT);
            vehicle.returnToPreferredSlot();
            // CLEAR_CONFLICT chi ap dung khi xe da nam trong vung xung dot.
            // Khi do xe can thoat khoi giao lo, nhung van khong lien quan den
            // CLEAR_PATH do xe uu tien thuc tu phia sau.
            vehicle.setSpeed(Math.max(vehicle.getSpeed(), getMaxSpeed() * 0.75));
            return;
        }

        if (mode == Vehicle.YieldMode.CLEAR_PATH
                || mode == Vehicle.YieldMode.URGENT_CLEAR_PATH) {
            handleUrgentClearPath(vehicle, nextLight);
            return;
        }

        if (mode == Vehicle.YieldMode.YIELD_RIGHT || mode == Vehicle.YieldMode.PULL_RIGHT) {
            vehicle.cancelOvertake();
            Lane lane = vehicle.getLane();
            boolean canReallyPullRight = lane != null
                    && lane.occupancy().hasYieldRightMergeGap(vehicle, null);
            if (canReallyPullRight) {
                vehicle.setManeuverState(Vehicle.ManeuverState.YIELDING_RIGHT);
                vehicle.setTargetLateralOffset(lane.getRightmostOffset(vehicle));
                vehicle.setSpeed(Math.min(getMaxSpeed() * 0.55,
                        vehicle.getSpeed() > 0.0 ? vehicle.getSpeed() : getMaxSpeed() * 0.45));
            } else {
                // Ben phai da kin. Xe khong duoc ep chen phai, ma chuyen sang
                // trang thai "bi voi" de tu tien len tim khoang trong. Trang thai
                // nay van ton trong den do/vach dung, khong duoc thuc xe qua den.
                handleUrgentClearPath(vehicle, nextLight);
            }
            return;
        }

        if (vehicle.getManeuverState() == Vehicle.ManeuverState.YIELDING_RIGHT
                || vehicle.getManeuverState() == Vehicle.ManeuverState.STOPPED_FOR_CONFLICT
                || vehicle.getManeuverState() == Vehicle.ManeuverState.CLEARING_CONFLICT
                || vehicle.getManeuverState() == Vehicle.ManeuverState.URGENT_CLEARING
                || vehicle.getManeuverState() == Vehicle.ManeuverState.HOLDING_POSITION) {
            vehicle.setManeuverState(Vehicle.ManeuverState.NORMAL);
            vehicle.returnToPreferredSlot();
        }

        if (handleActiveLateralManeuver(vehicle)) {
            if (vehicle.isOvertaking()) {
                vehicle.setSpeed(getMaxSpeed());
            } else {
                vehicle.setSpeed(Math.max(vehicle.getSpeed(), getMaxSpeed() * 0.45));
            }
            return;
        }

        SpeedDecision speedDecision = applyTrafficLightRule(vehicle, nextLight);
        double targetSpeed = speedDecision.targetSpeed();
        boolean stoppingForLight = speedDecision.stoppingForLight();

        Lane lane = vehicle.getLane();
        if (lane != null) {
            Vehicle inFront = lane.occupancy().vehicleAheadOf(vehicle);
            double longitudinalGap = inFront != null
                    ? inFront.getRearProgress() - vehicle.getFrontProgress()
                    : Double.MAX_VALUE;

            if (vehicle.isPriority() && inFront != null
                    && longitudinalGap > 0.0
                    && longitudinalGap < PRIORITY_WAIT_LOOKAHEAD
                    && shouldPriorityWaitForYield(vehicle, inFront, longitudinalGap)) {
                double waitSpeed = Math.min(targetSpeed, Math.max(inFront.getSpeed(), getMaxSpeed() * 0.58));
                if (longitudinalGap < getSafeDistance() * 1.7) {
                    waitSpeed = Math.min(waitSpeed, Math.max(getMinSpeed(), inFront.getSpeed() * 0.75));
                }
                rampSpeed(vehicle, waitSpeed);
                return;
            }

            if (inFront != null && longitudinalGap <= getSafeDistance()) {
                if (shouldTryGapFill(vehicle, inFront, longitudinalGap, stoppingForLight)
                        && sideShiftPlanner.tryGapFill(vehicle, GAP_FILL_FRONT_GAP, GAP_FILL_REAR_GAP)) {
                    vehicle.setSpeed(Math.min(targetSpeed, Math.max(getMinSpeed(), getMaxSpeed() * 0.45)));
                    return;
                }

                if (shouldStartOvertake(vehicle, inFront, stoppingForLight)) {
                    startOvertake(vehicle, inFront);
                    vehicle.setSpeed(getMaxSpeed());
                    return;
                }

                targetSpeed = Math.min(targetSpeed, inFront.getSpeed());
                if (longitudinalGap <= getSafeDistance() * 0.35) {
                    targetSpeed = getMinSpeed();
                }
            } else if (shouldTryGapFill(vehicle, null, longitudinalGap, stoppingForLight)
                    && sideShiftPlanner.tryGapFill(vehicle, GAP_FILL_FRONT_GAP, GAP_FILL_REAR_GAP)) {
                vehicle.setSpeed(Math.min(targetSpeed, Math.max(getMinSpeed(), getMaxSpeed() * 0.45)));
                return;
            }

            if (!vehicle.isOvertaking()
                    && vehicle.getManeuverState() != Vehicle.ManeuverState.GAP_FILLING
                    && Math.abs(vehicle.getLateralOffset() - vehicle.getPreferredLateralOffset()) > 0.5) {
                sideShiftPlanner.tryReturnToPreferredOffset(vehicle, RETURN_FRONT_GAP, RETURN_BACK_GAP);
            }

            if (vehicle.isPriority() && inFront == null && !vehicle.isOvertaking()) {
                vehicle.resetPriorityWait();
                vehicle.returnPriorityToCenterIfIdle();
            }

            if (tryKeepRightAfterFormalOvertake(vehicle, targetSpeed)) return;
        }

        vehicle.setSpeed(targetSpeed);
    }

    private record SpeedDecision(
            double targetSpeed,
            boolean stoppingForLight,
            boolean redLightAhead,
            boolean pastStopLine,
            double distanceToStopLine
    ) {}

    private SpeedDecision applyTrafficLightRule(Vehicle vehicle, TrafficLight nextLight) {
        double targetSpeed = getMaxSpeed();
        boolean stoppingForLight = false;
        boolean redLightAhead = false;
        boolean pastStopLine = false;
        double distanceToStopLine = Double.POSITIVE_INFINITY;

        Lane logicalLane = vehicle.getOriginalLane() != null ? vehicle.getOriginalLane() : vehicle.getLane();
        double frontProgress = 0.0;
        LaneControlPoint nextControl = null;
        TrafficLight logicalLight = null;

        if (logicalLane != null) {
            frontProgress = vehicle.getLane() == logicalLane
                    ? vehicle.getFrontProgress()
                    : logicalLane.getProgressOf(vehicle.getPosition()) + vehicle.getLongitudinalLength() / 2.0;
            nextControl = logicalLane.getNextControlPoint(frontProgress);
            logicalLight = nextControl != null ? nextControl.getLight() : nextLight;
        } else {
            logicalLight = nextLight;
        }

        if (obeyTrafficLight() && logicalLight != null && logicalLane != null) {
            double stopProgress = nextControl != null ? nextControl.getProgress() : logicalLane.getStopProgress();
            double distToStop = stopProgress - frontProgress;
            distanceToStopLine = distToStop;
            boolean isPastStop = distToStop < -3.0;
            pastStopLine = isPastStop;

            if (!isPastStop) {
                if (logicalLight.isRed()) {
                    redLightAhead = true;
                    stoppingForLight = true;
                    if (distToStop <= getStopDistance()) {
                        targetSpeed = 0.0;
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
        return new SpeedDecision(targetSpeed, stoppingForLight, redLightAhead,
                pastStopLine, distanceToStopLine);
    }

    /**
     * Xe bi xe uu tien thuc tu phia sau co trang thai "bi voi".
     * Hanh vi moi:
     * - neu ben phai mo ra thi uu tien nhich phai;
     * - neu chua nhich duoc thi tang toc mem de tim khoang trong;
     * - van ton trong den do/vach dung, khong duoc bi thuc qua den.
     */
    private void handleUrgentClearPath(Vehicle vehicle, TrafficLight nextLight) {
        vehicle.cancelOvertake();
        boolean continuingGapFill = vehicle.getManeuverState() == Vehicle.ManeuverState.GAP_FILLING;
        if (!continuingGapFill) {
            vehicle.setManeuverState(Vehicle.ManeuverState.URGENT_CLEARING);
        }

        Lane lane = vehicle.getLane();
        if (lane != null) {
            boolean canPullRight = lane.occupancy().hasYieldRightMergeGap(vehicle, null)
                    && lane.occupancy().isSideSpaceFree(
                            vehicle,
                            lane.getRightmostOffset(vehicle),
                            URGENT_FRONT_GAP,
                            URGENT_REAR_GAP
                    );
            if (continuingGapFill) {
                // Let the committed gap-fill finish; do not rewrite its target every frame.
            } else if (canPullRight) {
                vehicle.setTargetLateralOffset(lane.getRightmostOffset(vehicle));
            } else if (!vehicle.isOvertaking()) {
                // Ben phai dang co xe: khong ep chen. Thu tim mot slot trong
                // ve phia phai/giua lane de xe tu dien vao cho trong.
                if (!sideShiftPlanner.tryYieldGapFill(vehicle, URGENT_FRONT_GAP, URGENT_REAR_GAP)
                        && Math.abs(vehicle.getTargetLateralOffset() - vehicle.getPreferredLateralOffset()) < 2.0) {
                    vehicle.returnToPreferredSlot();
                }
            }
        }

        SpeedDecision lightRule = applyTrafficLightRule(vehicle, nextLight);
        double desired;
        if (lightRule.redLightAhead() && !lightRule.pastStopLine()) {
            if (lightRule.distanceToStopLine() <= getStopDistance() + 4.0) {
                rampSpeed(vehicle, 0.0);
                return;
            }
            // Con xa vach dung thi co the "voi" hon, nhung toc do bi gioi han
            // boi lightRule nen khong lao qua den do.
            desired = Math.min(getMaxSpeed() * 1.05, lightRule.targetSpeed());
        } else {
            desired = Math.max(lightRule.targetSpeed(), getMaxSpeed() * URGENT_SPEED_FACTOR);
        }

        if (lane != null) {
            Vehicle inFront = lane.occupancy().vehicleAheadOf(vehicle);
            if (inFront != null) {
                double gap = inFront.getRearProgress() - vehicle.getFrontProgress();
                if (gap < getSafeDistance() * 0.45) {
                    desired = Math.min(desired, getMinSpeed());
                } else if (gap < getSafeDistance()) {
                    desired = Math.min(desired, Math.max(inFront.getSpeed(), getMaxSpeed() * URGENT_MIN_FACTOR));
                }
            }
        }

        rampSpeed(vehicle, desired);
    }

    private double turningSpeedLimit(Vehicle vehicle) {
        double cap = vehicle != null && vehicle.isPriority() ? 72.0 : 62.0;
        return Math.min(getMaxSpeed() * 0.88, cap);
    }

    private void rampSpeed(Vehicle vehicle, double desiredSpeed) {
        double current = vehicle.getSpeed();
        double step = Math.max(2.0, getMaxSpeed() * 0.07);
        double next;
        if (desiredSpeed > current) {
            next = Math.min(desiredSpeed, current + step);
        } else {
            next = Math.max(desiredSpeed, current - step * 1.35);
        }
        vehicle.setSpeed(next);
    }

    private boolean handleActiveLateralManeuver(Vehicle vehicle) {
        if (vehicle.getManeuverState() == Vehicle.ManeuverState.GAP_FILLING) {
            if (vehicle.isNearTargetLateralOffset(1.5)) {
                vehicle.setPreferredLateralOffset(vehicle.getTargetLateralOffset());
                vehicle.setManeuverState(Vehicle.ManeuverState.NORMAL);
                vehicle.setManeuverCooldown(0.8);
            }
            return true;
        }
        return handleActiveOvertake(vehicle);
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
            case OVERTAKE_SHIFT_LEFT, EMERGENCY_CORRIDOR -> {
                // Khoảng trống đã được kiểm tra trước khi commit vượt.
                // Không kiểm tra/hủy lại từng frame vì sẽ tạo cảm giác xe do dự/lắc.
                if (vehicle.isNearTargetLateralOffset(3.0)) {
                    vehicle.setManeuverState(Vehicle.ManeuverState.OVERTAKE_PASSING);
                }
                return true;
            }
            case OVERTAKE_PASSING -> {
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

    private boolean shouldPriorityWaitForYield(Vehicle priority, Vehicle inFront, double longitudinalGap) {
        if (priority == null || inFront == null || !priority.isPriority()) {
            return false;
        }
        if (!isMeaningfulYieldMode(inFront.getYieldMode())) {
            priority.resetPriorityWait();
            return false;
        }
        if (!isBlockingCurrentPath(priority, inFront)) {
            priority.resetPriorityWait();
            return false;
        }
        if (longitudinalGap <= PRIORITY_CRITICAL_GAP) {
            return false;
        }
        double waited = priority.addPriorityWaitFor(inFront, PRIORITY_PATIENCE_TICK);
        return waited < PRIORITY_PATIENCE_SECONDS;
    }

    private boolean hasPriorityWaitExpiredOrCritical(Vehicle priority, Vehicle inFront, double longitudinalGap) {
        if (priority == null || !priority.isPriority()) {
            return true;
        }
        if (longitudinalGap <= PRIORITY_CRITICAL_GAP) {
            return true;
        }
        if (!isMeaningfulYieldMode(inFront != null ? inFront.getYieldMode() : Vehicle.YieldMode.NONE)) {
            return true;
        }
        return priority.getPriorityWaitTarget() == inFront
                && priority.getPriorityWaitSeconds() >= PRIORITY_PATIENCE_SECONDS;
    }

    private boolean isMeaningfulYieldMode(Vehicle.YieldMode mode) {
        return mode != null && mode != Vehicle.YieldMode.NONE;
    }

    private boolean isBlockingCurrentPath(Vehicle vehicle, Vehicle inFront) {
        if (vehicle == null || inFront == null || vehicle.getLane() == null) {
            return false;
        }
        Lane lane = vehicle.getLane();
        return inFront.getLane() == lane
                && lane.occupancy().hasLateralConflict(
                        vehicle,
                        vehicle.getTargetLateralOffset(),
                        inFront,
                        inFront.getLateralOffset()
                );
    }

    private boolean shouldTryGapFill(Vehicle vehicle, Vehicle inFront,
                                     double longitudinalGap, boolean stoppingForLight) {
        if (vehicle.getLane() == null) return false;
        if (vehicle.getManeuverCooldown() > 0.0) return false;
        if (vehicle.isOvertaking()) return false;
        if (vehicle.getIntersectionManeuverState() == Vehicle.IntersectionManeuverState.APPROACHING
                || vehicle.getIntersectionManeuverState() == Vehicle.IntersectionManeuverState.WAITING_BEFORE_INTERSECTION) return false;

        // Gap-fill chi duoc kich hoat khi that su co xe/vat can phia truoc.
        // Khong dung "chay cham", "gan stop line" hoac "dang dung den do" lam ly do rieng,
        // vi nhu vay xe se tu dich vao giua lane/slot khac du khong co vat can.
        if (inFront == null) return false;

        boolean blockedByFront = longitudinalGap > 0.0 && longitudinalGap < GAP_FILL_QUEUE_GAP;
        if (!blockedByFront) return false;

        boolean frontIsSlow = inFront.getSpeed() < getMaxSpeed() * 0.72;
        boolean movingSlowly = vehicle.getSpeed() < getMaxSpeed() * 0.55;

        boolean nearStopLine = false;
        Lane lane = vehicle.getLane();
        if (lane != null) {
            double distToStop = lane.getStopProgress() - vehicle.getFrontProgress();
            nearStopLine = distToStop > -8.0 && distToStop < 145.0;
        }

        return frontIsSlow || movingSlowly || nearStopLine || stoppingForLight;
    }

    private boolean shouldStartOvertake(Vehicle vehicle, Vehicle inFront, boolean stoppingForLight) {
        if (!canOvertake() || stoppingForLight) return false;
        if (vehicle.getManeuverCooldown() > 0.0) return false;
        if (vehicle.getLane() == null || inFront == null) return false;
        if (vehicle.getIntersectionManeuverState() == Vehicle.IntersectionManeuverState.APPROACHING
                || vehicle.getIntersectionManeuverState() == Vehicle.IntersectionManeuverState.WAITING_BEFORE_INTERSECTION) return false;

        // Xe thường không cố vượt xe ưu tiên. Xe ưu tiên vẫn phải có khả năng
        // tránh/vượt cả xe thường lẫn xe ưu tiên khác nếu bị kẹt phía trước.
        if (inFront.isPriority() && !vehicle.isPriority()) return false;

        Lane lane = vehicle.getLane();
        if (!lane.isInLaneOvertakeAllowed()) return false;

        double actualGap = inFront.getRearProgress() - vehicle.getFrontProgress();
        if (actualGap <= 0.0 || actualGap > getOvertakeGap() + 38.0) {
            return false;
        }

        // Xe thuong chi vuot khi xe truoc thuc su cham hon. Neu xe truoc dang di binh thuong,
        // khong nen "do du" dich ngang roi quay lai.
        if (!vehicle.isPriority() && inFront.getSpeed() >= getMaxSpeed() * 0.82) {
            return false;
        }

        double frontGap = getOvertakeGap() + 62.0;
        double rearGap = Math.max(38.0, getOvertakeGap() * 0.65);

        Double passOffset = lane.occupancy().findPassingOffset(
                vehicle, inFront, true, frontGap, rearGap
        );

        if (passOffset == null && canUseMiddleGap()) {
            passOffset = lane.occupancy().findMiddlePassingOffset(vehicle, inFront, frontGap, rearGap);
        }

        // Xe thuong khong mac dinh vuot phai. Priority co the fallback sang khe phai,
        // nhung van truoc emergency corridor.
        if (passOffset == null && vehicle.isPriority()) {
            passOffset = lane.occupancy().findPassingOffset(
                    vehicle, inFront, false, frontGap, rearGap
            );
        }

        if (passOffset != null) {
            return lane.occupancy().isPassCorridorFree(vehicle, inFront, passOffset, frontGap, rearGap);
        }

        // Emergency corridor la phuong an cuoi: chi sau khi xe truoc da co thoi gian
        // nhich phai/clear path, hoac khi khoang cach da qua nguy cap.
        return vehicle.isPriority()
                && canUseEmergencyCorridor()
                && hasPriorityWaitExpiredOrCritical(vehicle, inFront, actualGap)
                && lane.occupancy().isEmergencyCorridorFree(vehicle, inFront, frontGap + 20.0, rearGap);
    }

    private void startOvertake(Vehicle vehicle, Vehicle inFront) {
        if (vehicle == null || inFront == null || vehicle.getLane() == null) {
            return;
        }

        // Tuyệt đối không startLaneChange ở đây. Dù Highway có 2 lane cùng chiều,
        // project này mô phỏng lane rộng 2 slot nên vượt/né đều phải nằm trong lane hiện tại.
        double frontGap = getOvertakeGap() + 62.0;
        double rearGap = Math.max(38.0, getOvertakeGap() * 0.65);

        if (sideShiftPlanner.tryOvertakeInsideLane(vehicle, inFront, frontGap, rearGap, true)) {
            vehicle.resetPriorityWait();
            return;
        }

        if (canUseMiddleGap() && sideShiftPlanner.tryMiddleGapOvertake(vehicle, inFront, frontGap, rearGap)) {
            vehicle.resetPriorityWait();
            return;
        }

        // Fallback phải chỉ dành cho xe ưu tiên để tránh bị kẹt sau xe khác/xe ưu tiên khác.
        if (vehicle.isPriority()) {
            if (sideShiftPlanner.tryOvertakeInsideLane(vehicle, inFront, frontGap, rearGap, false)) {
                vehicle.resetPriorityWait();
                return;
            }

            double actualGap = inFront.getRearProgress() - vehicle.getFrontProgress();
            if (canUseEmergencyCorridor()
                    && hasPriorityWaitExpiredOrCritical(vehicle, inFront, actualGap)) {
                if (sideShiftPlanner.tryEmergencyCorridor(vehicle, inFront, frontGap + 20.0, rearGap)) {
                    vehicle.resetPriorityWait();
                }
            }
        }
    }

    private boolean hasPassedTarget(Vehicle vehicle, Vehicle target) {
        if (target == null || target.getLane() != vehicle.getLane()) return true;
        return vehicle.getRearProgress() > target.getFrontProgress() + PASS_CLEAR_GAP;
    }

    private boolean tryKeepRightAfterFormalOvertake(Vehicle vehicle, double targetSpeed) {
        // Formal lane-change đã bị tắt theo thiết kế mới. Giữ method để tránh
        // đụng nhiều code cũ, nhưng không bao giờ cho xe bay sang lane khác.
        return false;
    }
}
