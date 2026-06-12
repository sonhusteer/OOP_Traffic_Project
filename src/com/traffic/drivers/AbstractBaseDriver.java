package com.traffic.drivers;

import com.traffic.core.IDriver;
import com.traffic.core.MathUtils;
import com.traffic.core.Vector2D;
import com.traffic.core.Vehicle;
import com.traffic.map.Lane;
import com.traffic.map.TrafficLight;

/**
 * Bo nao lai xe co ban.
 * Thu tu uu tien:
 * 1. STOP do xe uu tien o nga tu.
 * 2. RUSH / nhuong duong xe uu tien.
 * 3. Den giao thong sap toi.
 * 4. Quay ve lane nha neu dang muon lane.
 * 5. Giu khoang cach va vuot xe.
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

    /** Toc do hanh trinh bi gioi han boi ca driver va loai xe. */
    protected double getCruiseSpeed(Vehicle vehicle) {
        return Math.min(getMaxSpeed(), vehicle.getMaxSpeed());
    }

    /** Toc do khi RUSH, van khong de xe dap/xe cuu hoa vuot qua qua phi ly. */
    protected double getRushSpeed(Vehicle vehicle) {
        return Math.min(getMaxSpeed() * 1.5, vehicle.getMaxSpeed() * 1.3);
    }

    @Override
    public void makeDecision(Vehicle vehicle, TrafficLight nextLight) {

        // Dang chuyen lane: chi can giu toc do an toan, khong ra lenh moi.
        if (vehicle.isChangingLane()) {
            double targetSpeed = getCruiseSpeed(vehicle);

            Lane targetLane = vehicle.getTargetLane();
            if (targetLane != null) {
                Vehicle inFrontNew = targetLane.getVehicleAhead(vehicle);
                if (inFrontNew != null) {
                    double distNew = MathUtils.distance(
                        vehicle.getPosition(), inFrontNew.getPosition());

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
                        vehicle.getPosition(), inFrontOld.getPosition());

                    if (distOld <= getSafeDistance() * 0.3) {
                        targetSpeed = Math.min(targetSpeed, inFrontOld.getSpeed());
                    }
                }
            }

            vehicle.setSpeed(targetSpeed);
            return;
        }

        Vehicle.YieldMode mode = vehicle.getYieldMode();

        if (mode == Vehicle.YieldMode.STOP) {
            vehicle.setSpeed(0);
            return;
        }

        double targetSpeed = getCruiseSpeed(vehicle);
        if (mode == Vehicle.YieldMode.RUSH) {
            targetSpeed = getRushSpeed(vehicle);
        }

        Lane currentLane = vehicle.getLane();

        // RUSH: thu sang lane ben phai neu co. Neu khong co, xe se tang toc.
        if (mode == Vehicle.YieldMode.RUSH && currentLane != null
                && currentLane.getRightNeighbor() != null) {
            Lane right = currentLane.getRightNeighbor();
            if (right.isSafeToEnter(vehicle.getPosition(), getSafeDistance())
                    && vehicle.startLaneChange(right)) {
                return;
            }
        }

        // Den giao thong: chon control point sap toi tren homeLane.
        boolean stoppingForLight = false;
        Lane homeLane = vehicle.getHomeLane();
        Lane lightLane = (homeLane != null) ? homeLane : currentLane;
        TrafficLight logicalLight = nextLight;
        Vector2D stopLine = null;

        if (lightLane != null) {
            Lane.TrafficControlPoint nextControl =
                lightLane.getNextTrafficControl(vehicle.getPosition());
            if (nextControl != null) {
                logicalLight = nextControl.getLight();
                stopLine = nextControl.getStopLine();
            } else if (logicalLight != null) {
                stopLine = lightLane.getStopLine();
            }
        }

        if (obeyTrafficLight() && logicalLight != null && lightLane != null && stopLine != null) {
            double myProgress = lightLane.getProgress(vehicle.getPosition());
            double stopProgress = lightLane.getProgress(stopLine);
            double distToStop = stopProgress - myProgress;
            boolean isPastStop = distToStop < -3.0;

            if (!isPastStop) {
                if (logicalLight.isRed()) {
                    stoppingForLight = true;

                    if (distToStop <= getStopDistance()) {
                        targetSpeed = getMinSpeed();
                    } else if (distToStop <= getBrakeDistance()) {
                        double ratio = (distToStop - getStopDistance())
                                     / (getBrakeDistance() - getStopDistance());
                        targetSpeed = MathUtils.clamp(
                            getCruiseSpeed(vehicle) * ratio,
                            getMinSpeed(),
                            getCruiseSpeed(vehicle));
                    }
                } else if (logicalLight.isYellow() && rushYellowLight()) {
                    targetSpeed = Math.min(getRushSpeed(vehicle), getCruiseSpeed(vehicle) * 1.1);
                }
            }
        }

        // Neu dang muon lane de vuot, co gang ve homeLane truoc khi vuot tiep.
        if (vehicle.isAwayFromHome() && vehicle.getLaneChangeCooldown() <= 0) {
            Lane home = vehicle.getHomeLane();
            if (home != null && home != currentLane) {
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

        // Giu khoang cach va vuot xe neu co the.
        currentLane = vehicle.getLane();
        if (currentLane != null) {
            Vehicle inFront = currentLane.getVehicleAhead(vehicle);
            if (inFront != null) {
                double distToCar = MathUtils.distance(
                    vehicle.getPosition(), inFront.getPosition());

                if (distToCar <= getSafeDistance()) {
                    if (canOvertake()
                            && !stoppingForLight
                            && currentLane.getLeftNeighbor() != null) {
                        Lane left = currentLane.getLeftNeighbor();
                        if (left.isSafeToEnter(vehicle.getPosition(), getOvertakeGap())
                                && vehicle.startLaneChange(left)) {
                            return;
                        }
                    }

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
