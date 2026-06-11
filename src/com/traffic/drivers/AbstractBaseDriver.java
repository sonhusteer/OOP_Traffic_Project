package com.traffic.drivers;

import com.traffic.core.IDriver;
import com.traffic.core.MathUtils;
import com.traffic.core.Vehicle;
import com.traffic.map.Lane;
import com.traffic.map.TrafficLight;

/**
 * Bộ não lái xe trừu tượng — logic chung cho Normal, Aggressive, Emergency.
 * 
 * Hỗ trợ 2 kiểu lách:
 *   1. Chuyển làn chính thức: Khi có làn CÙNG CHIỀU bên cạnh (HighwayMap)
 *   2. Dịch ngang trong làn: Lách trái/phải TRONG chính làn rộng 80px (Giao thông VN)
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

    @Override
    public void makeDecision(Vehicle vehicle, TrafficLight nextLight) {
        // ── Đang trượt ngang chuyển làn → chờ xong ──────────────────────
        if (vehicle.isChangingLane()) {
            return;
        }

        Vehicle.YieldMode mode = vehicle.getYieldMode();

        // ── 1. Dừng hẳn ở ngã tư nếu gặp xe ưu tiên ────────────────────
        if (mode == Vehicle.YieldMode.STOP) {
            vehicle.setSpeed(0);
            return;
        }

        double targetSpeed = getMaxSpeed();

        // ── 2. Nhường đường: Dạt PHẢI trong làn rộng ─────────────────────
        if (mode == Vehicle.YieldMode.RUSH) {
            vehicle.setTargetLateralOffset(18.0);  // Dạt phải 18px
            targetSpeed = getMaxSpeed() * 0.6;     // Giảm tốc nhường
        } else if (vehicle.getLateralOffset() > 0.5) {
            // Hết tình trạng khẩn cấp → về giữa làn
            vehicle.setTargetLateralOffset(0);
        }

        // ── 3. Đèn giao thông ────────────────────────────────────────────
        boolean stoppingForLight = false;

        Lane logicalLane = vehicle.getOriginalLane() != null
                         ? vehicle.getOriginalLane() : vehicle.getLane();
        TrafficLight logicalLight = logicalLane != null
                                  ? logicalLane.getLight() : nextLight;

        if (obeyTrafficLight() && logicalLight != null && logicalLane != null) {
            var stopLine       = logicalLane.getStopLine();
            var laneStart      = logicalLane.getStart();
            double distToStop  = MathUtils.distance(vehicle.getPosition(), stopLine);
            double myDist      = MathUtils.distance(laneStart, vehicle.getPosition());
            double stopDist    = MathUtils.distance(laneStart, stopLine);
            boolean isPastStop = myDist > stopDist;

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
                } else if (logicalLight.isYellow() && this instanceof AggressiveDriver) {
                    targetSpeed = getMaxSpeed() * 1.1;
                }
            }
        }

        // ── 4. Giữ khoảng cách & Vượt xe ─────────────────────────────────
        if (vehicle.getLane() != null) {
            Vehicle inFront = vehicle.getLane().getVehicleAhead(vehicle);

            // Nếu xe đang lách trái (offset < -5), bỏ qua xe phía trước vì đang chạy bên cạnh
            boolean isShiftedLeft = vehicle.getLateralOffset() < -5;

            if (inFront != null && !isShiftedLeft) {
                double distToCar = MathUtils.distance(
                    vehicle.getPosition(), inFront.getPosition());

                if (distToCar <= getSafeDistance()) {
                    // *** KHÔNG vượt khi đang dừng đèn đỏ ***
                    if (canOvertake() && !stoppingForLight
                            && vehicle.getLaneChangeCooldown() <= 0) {

                        Lane left = vehicle.getLane().getLeftNeighbor();

                        // Ưu tiên 1: Chuyển làn chính thức nếu có làn CÙNG CHIỀU
                        if (left != null && vehicle.isSameDirection(left)
                                && left.isSafeToEnter(
                                    vehicle.getPosition(), getOvertakeGap())) {
                            vehicle.startLaneChange(left);
                            return;
                        }

                        // Ưu tiên 2: Lách trái trong làn rộng (Giao thông VN)
                        vehicle.setTargetLateralOffset(-18.0);
                        // Giữ tốc độ cao để vượt qua
                        targetSpeed = getMaxSpeed();
                        vehicle.setSpeed(targetSpeed);
                        return;
                    }

                    // Không vượt được → bám đuôi
                    targetSpeed = Math.min(targetSpeed, inFront.getSpeed());
                    if (distToCar <= getSafeDistance() * 0.4) {
                        targetSpeed = getMinSpeed();
                    }
                }
            }

            // Nếu xe đã lách trái và vượt qua rồi (không còn xe chậm phía trước)
            // → về giữa làn
            if (isShiftedLeft && inFront == null
                    && mode != Vehicle.YieldMode.RUSH) {
                vehicle.setTargetLateralOffset(0);
            }
            // Cũng về giữa nếu xe phía trước đã xa
            if (isShiftedLeft && inFront != null) {
                double distAhead = MathUtils.distance(
                    vehicle.getPosition(), inFront.getPosition());
                if (distAhead > getSafeDistance() * 2.0) {
                    vehicle.setTargetLateralOffset(0);
                }
            }

            // ── 5. Keep Right: Chỉ cho chuyển làn chính thức (HighwayMap) ─
            if (vehicle.hasOvertaken()
                    && vehicle.getLaneChangeCooldown() <= 0
                    && vehicle.getLane().getRightNeighbor() != null) {
                Lane right = vehicle.getLane().getRightNeighbor();
                if (vehicle.isSameDirection(right)) {
                    Vehicle frontRight = right.getVehicleAhead(vehicle);
                    double distRight = (frontRight != null)
                            ? MathUtils.distance(
                                vehicle.getPosition(), frontRight.getPosition())
                            : Double.MAX_VALUE;
                    if (right.isSafeToEnter(vehicle.getPosition(), getSafeDistance())
                            && distRight > getSafeDistance() * 2.0) {
                        vehicle.startLaneChange(right);
                        return;
                    }
                }
            }
        }

        vehicle.setSpeed(targetSpeed);
    }
}
