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

    protected abstract double getStopLineGap();
    protected abstract double getStopCarGap();
    protected abstract double getBrakeGap();
    protected abstract double getSafeGap();
    protected abstract double getOvertakeGap();
    protected abstract double getBaseMaxSpeed();
    protected double getMaxSpeed() {
        return getBaseMaxSpeed() * com.traffic.ui.MainApp.vehicleSpeedMultiplier;
    }
    protected abstract double getMinSpeed();
    protected abstract boolean canOvertake();
    protected boolean obeyTrafficLight() { return true; }

    @Override
    public void makeDecision(Vehicle vehicle, TrafficLight nextLight) {
        // ── Đang trượt ngang chuyển làn → chờ xong ──────────────────────
        if (vehicle.isChangingLane()) {
            return;
        }

        // ── Đang rẽ ở ngã tư → giữ nguyên tốc độ rẽ ─────────────────────
        if (vehicle.isTurning()) {
            vehicle.setSpeed(getMaxSpeed());
            return;
        }

        Vehicle.YieldMode mode = vehicle.getYieldMode();

        double targetSpeed = getMaxSpeed();

        // ── 1. Nhường đường (RUSH: tăng tốc chạy thoát để dọn đường) ───────
        if (mode == Vehicle.YieldMode.RUSH) {
            targetSpeed = getMaxSpeed() * 1.2;     // Tăng tốc dọn đường
        }
        double defaultOffset = vehicle.isFourWheeler() ? -18.0 : 18.0;
        vehicle.setTargetLateralOffset(defaultOffset);

        // ── 2. Đèn giao thông và Xe ưu tiên ở ngã tư ───────────────────────
        boolean stoppingForLight = false;

        Lane logicalLane = vehicle.getOriginalLane() != null
                         ? vehicle.getOriginalLane() : vehicle.getLane();
        TrafficLight logicalLight = logicalLane != null
                                  ? logicalLane.getLight() : nextLight;

        boolean isPastStop = false;
        if (logicalLane != null) {
            var stopLine = logicalLane.getStopLine();
            double myDist = logicalLane.getSignedDistance(vehicle.getPosition());
            double stopDist = logicalLane.getSignedDistance(stopLine);
            isPastStop = (myDist + vehicle.getWidth() / 2.0) >= (stopDist - 5.0);
        }

        boolean stoppingForEmergency = (mode == Vehicle.YieldMode.STOP && !isPastStop);

        if (logicalLane != null && (stoppingForEmergency || (obeyTrafficLight() && logicalLight != null))) {
            var stopLine       = logicalLane.getStopLine();
            double myDist      = logicalLane.getSignedDistance(vehicle.getPosition());
            double stopDist    = logicalLane.getSignedDistance(stopLine);
            double distToStop  = stopDist - myDist;

            double stopLineGap = getStopLineGap();
            if (!vehicle.isFourWheeler()) {
                stopLineGap = 4.0; // 2-wheelers stop extremely close to stop line
            }
            double stopLineDist = (vehicle.getWidth() / 2.0) + stopLineGap;
            double brakeLineDist = stopLineDist + getBrakeGap();

            if (!isPastStop) {
                if (stoppingForEmergency || (logicalLight != null && logicalLight.isRed())) {
                    stoppingForLight = true;
                    if (distToStop <= stopLineDist) {
                        targetSpeed = getMinSpeed();
                    } else if (distToStop <= brakeLineDist) {
                        double ratio = (distToStop - stopLineDist)
                                     / (brakeLineDist - stopLineDist);
                        targetSpeed = MathUtils.clamp(
                            getMaxSpeed() * ratio, getMinSpeed(), getMaxSpeed());
                    }
                } else if (logicalLight != null && logicalLight.isYellow() && this instanceof AggressiveDriver) {
                    targetSpeed = getMaxSpeed() * 1.1;
                }
            }
        }

        // ── 4. Giữ khoảng cách & Vượt xe ─────────────────────────────────
        if (vehicle.getLane() != null) {
            Vehicle inFront = vehicle.getLane().getVehicleAhead(vehicle);

            // Xe đang lách sang trái khỏi vị trí thông thường để vượt xe?
            // 4-bánh: vị trí thường -18, lách thêm trái → offset < -23
            // 2-bánh: vị trí thường +18, lách sang trái → offset < 13
            double homeOffset = vehicle.isFourWheeler() ? -18.0 : 18.0;
            boolean isShiftedLeft = vehicle.getLateralOffset() < (homeOffset - 5.0);

            if (inFront != null && !isShiftedLeft) {
                double distToCar = MathUtils.distance(
                    vehicle.getPosition(), inFront.getPosition());

                double stopCarGap = getStopCarGap();
                if (!vehicle.isFourWheeler()) {
                    stopCarGap = 4.0; // 2-wheelers stop extremely close behind other vehicles
                }
                double contactDist = (vehicle.getWidth() + inFront.getWidth()) / 2.0;
                double stopDist = contactDist + stopCarGap;
                double safeDist = stopDist + getSafeGap();

                if (distToCar <= safeDist) {
                    // *** KHÔNG vượt khi đang dừng đèn đỏ ***
                    if (canOvertake() && !stoppingForLight
                            && vehicle.getLaneChangeCooldown() <= 0) {

                        Lane left = vehicle.getLane().getLeftNeighbor();

                        // Ưu tiên 1: Chuyển làn chính thức nếu có làn CÙNG CHIỀU
                        if (left != null && vehicle.isSameDirection(left)
                                && left.isSafeToEnter(
                                    vehicle, getOvertakeGap())) {
                            vehicle.startLaneChange(left);
                            return;
                        }

                        // Ưu tiên 2: Lách trái trong làn rộng (Giao thông VN)
                        // Lách sang trái so với vị trí thường của xe
                        double overtakeOffset = vehicle.isFourWheeler() ? -36.0 : 0.0;
                        vehicle.setTargetLateralOffset(overtakeOffset);
                        // Giữ tốc độ cao để vượt qua
                        targetSpeed = getMaxSpeed();
                        vehicle.setSpeed(targetSpeed);
                        return;
                    }

                    // Không vượt được → bám đuôi
                    targetSpeed = Math.min(targetSpeed, inFront.getSpeed());
                    if (distToCar <= stopDist) {
                        targetSpeed = getMinSpeed();
                    } else {
                        double ratio = (distToCar - stopDist) / (safeDist - stopDist);
                        double followSpeed = inFront.getSpeed() + (getMaxSpeed() - inFront.getSpeed()) * ratio;
                        targetSpeed = Math.min(targetSpeed, followSpeed);
                    }
                }
            }

            // Nếu xe đã lách trái và vượt qua rồi (không còn xe chậm phía trước)
            // → về giữa làn
            if (isShiftedLeft && inFront == null
                    && mode != Vehicle.YieldMode.RUSH) {
                vehicle.setTargetLateralOffset(defaultOffset);
            }
            // Cũng về giữa nếu xe phía trước đã xa
            if (isShiftedLeft && inFront != null) {
                double distAhead = MathUtils.distance(
                    vehicle.getPosition(), inFront.getPosition());
                double contactDist = (vehicle.getWidth() + inFront.getWidth()) / 2.0;
                double safeDist = contactDist + getStopCarGap() + getSafeGap();
                if (distAhead > safeDist * 2.0) {
                    vehicle.setTargetLateralOffset(defaultOffset);
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
                    double contactDist = (frontRight != null) ? (vehicle.getWidth() + frontRight.getWidth()) / 2.0 : 36.0;
                    double safeDist = contactDist + getStopCarGap() + getSafeGap();
                    if (right.isSafeToEnter(vehicle, safeDist)
                            && distRight > safeDist * 2.0) {
                        vehicle.startLaneChange(right);
                        return;
                    }
                }
            }
        }

        vehicle.setSpeed(targetSpeed);
    }
}
