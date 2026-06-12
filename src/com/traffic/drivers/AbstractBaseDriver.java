package com.traffic.drivers;

import com.traffic.core.IDriver;
import com.traffic.core.MathUtils;
import com.traffic.core.Vehicle;
import com.traffic.map.Lane;
import com.traffic.map.TrafficLight;

/**
 * Bộ não lái xe trừu tượng.
 *
 * Logic ưu tiên (từ cao đến thấp):
 *   1. Đèn đỏ → phanh/dừng  (TUYỆT ĐỐI không vượt khi đèn đỏ)
 *   2. Nhường xe ưu tiên → lấn phải
 *   3. Xe chậm phía trước → lấn trái vượt (NẾU đèn không đỏ)
 *   4. Đang mượn làn → quay về homeLane ngay khi có thể
 */
public abstract class AbstractBaseDriver implements IDriver {

    protected abstract double getBrakeDistance();
    protected abstract double getStopDistance();
    protected abstract double getMaxSpeed();
    protected abstract double getMinSpeed();
    protected abstract double getSafeDistance();
    protected abstract double getOvertakeGap();
    protected abstract boolean canOvertake();

    protected boolean obeyTrafficLight() { return true; }
    protected boolean rushYellowLight()  { return false; }

    @Override
    public void makeDecision(Vehicle vehicle, TrafficLight nextLight) {

        // ── Đang trượt ngang → chỉ canh xe, không ra quyết định mới ─────
        if (vehicle.isChangingLane()) {
            double targetSpeed = getMaxSpeed();

            Lane targetLane = vehicle.getTargetLane();
            if (targetLane != null) {
                Vehicle inFrontNew = targetLane.getVehicleAhead(vehicle);
                if (inFrontNew != null) {
                    double distNew = MathUtils.distance(
                        vehicle.getPosition(),
                        inFrontNew.getPosition()
                    );

                    if (distNew <= getSafeDistance()) {
                        targetSpeed = Math.min(targetSpeed, inFrontNew.getSpeed());
                    }
                }
            }

            Lane originalLane = vehicle.getOriginalLane();
            if (originalLane != null) {
                double myProgress = originalLane.getProgress(vehicle.getPosition());
                Vehicle inFrontOld = originalLane.getVehicleAheadAt(myProgress, vehicle);

                if (inFrontOld != null) {
                    double distOld = MathUtils.distance(
                        vehicle.getPosition(),
                        inFrontOld.getPosition()
                    );

                    if (distOld <= getSafeDistance() * 0.3) {
                        targetSpeed = Math.min(targetSpeed, inFrontOld.getSpeed());
                    }
                }
            }

            vehicle.setSpeed(targetSpeed);
            return;
        }

        Vehicle.YieldMode mode = vehicle.getYieldMode();

        // ── 1. Dừng hẳn ở ngã tư ────────────────────────────────────────
        if (mode == Vehicle.YieldMode.STOP) {
            vehicle.setSpeed(0);
            return;
        }

        double targetSpeed = getMaxSpeed();
        if (mode == Vehicle.YieldMode.RUSH) {
            targetSpeed = getMaxSpeed() * 1.5;
        }

        // ── 2. Nhường đường: lấn sang phải ──────────────────────────────
        if (mode == Vehicle.YieldMode.RUSH && vehicle.getLane().getRightNeighbor() != null) {
            Lane right = vehicle.getLane().getRightNeighbor();
            if (right.isSafeToEnter(vehicle.getPosition(), getSafeDistance())
                    && vehicle.startLaneChange(right)) {
                return;
            }
        }

        // ── 3. Đèn giao thông (LUÔN dùng đèn của homeLane) ─────────────
        //    Đây là fix chính: khi xe đang mượn làn khác để vượt,
        //    nó vẫn tuân theo đèn của LÀN NHÀ, không phải làn đang mượn.
        boolean stoppingForLight = false;

        Lane homeLane = vehicle.getHomeLane();
        Lane lightLane = (homeLane != null) ? homeLane : vehicle.getLane();
        TrafficLight logicalLight = (lightLane != null) ? lightLane.getLight() : nextLight;

        if (obeyTrafficLight() && logicalLight != null && lightLane != null) {
            var stopLine   = lightLane.getStopLine();
            var laneStart  = lightLane.getStart();

            double distToStop      = MathUtils.distance(vehicle.getPosition(), stopLine);
            double myDistToStart   = MathUtils.distance(laneStart, vehicle.getPosition());
            double stopDistToStart = MathUtils.distance(laneStart, stopLine);
            boolean isPastStop     = myDistToStart > stopDistToStart;

            if (!isPastStop) {
                if (logicalLight.isRed()) {
                    stoppingForLight = true;
                    if (distToStop <= getStopDistance()) {
                        targetSpeed = getMinSpeed();
                    } else if (distToStop <= getBrakeDistance()) {
                        double ratio = (distToStop - getStopDistance())
                                     / (getBrakeDistance() - getStopDistance());
                        targetSpeed = MathUtils.clamp(
                            getMaxSpeed() * ratio, getMinSpeed(), getMaxSpeed());
                    }
                } else if (logicalLight.isYellow() && rushYellowLight()) {
                    targetSpeed = getMaxSpeed() * 1.1;
                }
            }
        }

        // ── 4. Quay về homeLane nếu đang mượn làn ───────────────────────
        //    Ưu tiên cao hơn vượt xe: nếu đã vượt xong → về nhà ngay.
        if (vehicle.isAwayFromHome() && vehicle.getLaneChangeCooldown() <= 0) {
            Lane home = vehicle.getHomeLane();
            if (home != null && home != vehicle.getLane()) {
                Vehicle frontHome = home.getVehicleAhead(vehicle);
                double distHome = (frontHome != null)
                    ? MathUtils.distance(vehicle.getPosition(), frontHome.getPosition())
                    : Double.MAX_VALUE;

                if (home.isSafeToEnter(vehicle.getPosition(), getSafeDistance())
                        && distHome > getSafeDistance() * 1.5
                        && vehicle.startLaneChange(home)) {
                    vehicle.setSpeed(targetSpeed);
                    return;
                }
            }
        }

        // ── 5. Giữ khoảng cách & Vượt xe ────────────────────────────────
        if (vehicle.getLane() != null) {
            Vehicle inFront = vehicle.getLane().getVehicleAhead(vehicle);
            if (inFront != null) {
                double distToCar = MathUtils.distance(
                    vehicle.getPosition(), inFront.getPosition());

                if (distToCar <= getSafeDistance()) {
                    // *** KHÔNG vượt khi đèn đỏ ***
                    if (canOvertake()
                            && !stoppingForLight
                            && vehicle.getLane().getLeftNeighbor() != null) {
                        Lane left = vehicle.getLane().getLeftNeighbor();
                        if (left.isSafeToEnter(vehicle.getPosition(), getOvertakeGap())
                                && vehicle.startLaneChange(left)) {
                            return;
                        }
                    }

                    // Không vượt được → bám đuôi
                    targetSpeed = Math.min(targetSpeed, inFront.getSpeed());
                    if (distToCar <= getSafeDistance() * 0.5) {
                        targetSpeed = getMinSpeed();
                    }
                }
            }
        }

        vehicle.setSpeed(targetSpeed);
    }
}
