package com.traffic.drivers;

import com.traffic.core.IDriver;
import com.traffic.core.LateralManeuver;
import com.traffic.core.MathUtils;
import com.traffic.core.Vector2D;
import com.traffic.core.Vehicle;
import com.traffic.map.Lane;
import com.traffic.map.TrafficLight;

/**
 * Bo nao lai xe co ban.
 *
 * Final clean-code layer:
 * - Driver khong dung leftNeighbor/rightNeighbor de vuot/nhuong nua.
 * - Vươt/ne/nhuong duoc giao cho SideShiftPlanner trong cung Lane.
 * - YieldMode moi phan biet ro PULL_RIGHT, CLEAR_PATH, STOP, CLEAR_INTERSECTION.
 * - startLaneChange() chi con de tuong thich cho doi lane vat ly that su.
 */
public abstract class AbstractBaseDriver implements IDriver {

    private final SideShiftPlanner sideShiftPlanner = new SideShiftPlanner();

    protected abstract double getBrakeDistance();
    protected abstract double getStopDistance();
    protected abstract double getMaxSpeed();
    protected abstract double getMinSpeed();
    protected abstract double getSafeDistance();
    protected abstract double getOvertakeGap();
    protected abstract boolean canOvertake();

    protected boolean obeyTrafficLight() { return true; }
    protected boolean rushYellowLight()  { return false; }

    protected double getCruiseSpeed(Vehicle vehicle) {
        return Math.min(getMaxSpeed(), vehicle.getMaxSpeed());
    }

    protected double getRushSpeed(Vehicle vehicle) {
        return Math.min(getMaxSpeed() * 1.5, vehicle.getMaxSpeed() * 1.3);
    }

    protected double getClearPathSpeed(Vehicle vehicle) {
        return Math.min(getRushSpeed(vehicle), vehicle.getMaxSpeed());
    }

    @Override
    public void makeDecision(Vehicle vehicle, TrafficLight nextLight) {
        if (vehicle == null) {
            return;
        }

        if (vehicle.isPhysicalLaneChanging()) {
            keepSafeSpeedDuringPhysicalLaneChange(vehicle);
            return;
        }

        Vehicle.YieldMode mode = vehicle.getYieldMode();
        Lane currentLane = vehicle.getLane();

        // 1. STOP: dung truoc vung xung dot.
        if (mode == Vehicle.YieldMode.STOP) {
            vehicle.setSpeed(0);
            return;
        }

        // 2. CLEAR_INTERSECTION: da o trong giao lo thi khong dung giua duong.
        if (mode == Vehicle.YieldMode.CLEAR_INTERSECTION) {
            vehicle.setSpeed(getClearPathSpeed(vehicle));
            return;
        }

        double targetSpeed = getCruiseSpeed(vehicle);

        // 3. Xu ly den do/den vang.
        TrafficLightContext lightContext = findTrafficLightContext(vehicle, currentLane, nextLight);
        boolean stoppingForLight = false;
        if (obeyTrafficLight() && lightContext.isValid()) {
            LightDecision lightDecision = decideTrafficLightSpeed(vehicle, lightContext, targetSpeed);
            targetSpeed = lightDecision.targetSpeed;
            stoppingForLight = lightDecision.stopping;
        }

        // 4. Xu ly yeu cau nhuong xe uu tien.
        if (mode == Vehicle.YieldMode.PULL_RIGHT && currentLane != null) {
            boolean alreadyPullingRight = vehicle.getCurrentManeuver().getType()
                    == LateralManeuver.Type.YIELD_RIGHT;
            boolean pulledRight = alreadyPullingRight || sideShiftPlanner.tryYieldRight(
                    vehicle,
                    null,
                    getSafeDistance(),
                    getSafeDistance() * 1.5
            );

            if (pulledRight) {
                targetSpeed = Math.min(targetSpeed, getCruiseSpeed(vehicle) * 0.8);
            } else {
                // Neu khong co cho ne phai, fallback thanh CLEAR_PATH de tranh
                // chan dau xe uu tien qua lau.
                targetSpeed = Math.max(targetSpeed, getClearPathSpeed(vehicle));
            }
        } else if (mode == Vehicle.YieldMode.CLEAR_PATH) {
            targetSpeed = Math.max(targetSpeed, getClearPathSpeed(vehicle));
        }

        // 5. Khi het tinh huong uu tien, neu xe dang lech track spawn thi quay ve.
        if (mode == Vehicle.YieldMode.NONE && currentLane != null) {
            sideShiftPlanner.tryReturnToPreferredOffset(
                    vehicle,
                    getSafeDistance(),
                    getSafeDistance() * 1.5
            );
        }

        // 6-8. Kiem tra xe phia truoc, thu vuot trong cung lane, neu khong thi bam duoi.
        currentLane = vehicle.getLane();
        if (currentLane != null) {
            Vehicle inFront = currentLane.occupancy().vehicleAheadOf(vehicle);
            if (inFront != null) {
                double gap = inFront.getProgress() - vehicle.getProgress();
                boolean frontIsSlow = inFront.getSpeed() < getCruiseSpeed(vehicle) * 0.85;
                boolean closeEnough = gap < getSafeDistance() * 2.0;

                if (canOvertake()
                        && mode == Vehicle.YieldMode.NONE
                        && !stoppingForLight
                        && frontIsSlow
                        && closeEnough) {
                    boolean started = sideShiftPlanner.tryOvertakeInsideLane(
                            vehicle,
                            inFront,
                            getOvertakeGap(),
                            getOvertakeGap() * 1.5,
                            true
                    );
                    if (started) {
                        vehicle.setSpeed(targetSpeed);
                        return;
                    }
                }

                if (gap <= getSafeDistance()) {
                    targetSpeed = Math.min(targetSpeed, inFront.getSpeed());
                    if (gap <= getSafeDistance() * 0.5) {
                        targetSpeed = getMinSpeed();
                    }
                }
            }
        }

        vehicle.setSpeed(targetSpeed);
    }

    private void keepSafeSpeedDuringPhysicalLaneChange(Vehicle vehicle) {
        double targetSpeed = getCruiseSpeed(vehicle);

        Lane targetLane = vehicle.getTargetLane();
        if (targetLane != null) {
            Vehicle inFrontNew = targetLane.getVehicleAhead(vehicle);
            if (inFrontNew != null) {
                double gap = inFrontNew.getProgress() - vehicle.getProgress();
                if (gap <= getSafeDistance()) {
                    targetSpeed = Math.min(targetSpeed, inFrontNew.getSpeed());
                }
            }
        }

        Lane originalLane = vehicle.getOriginalLane();
        if (originalLane != null) {
            Vehicle inFrontOld = originalLane.getVehicleAhead(vehicle);
            if (inFrontOld != null) {
                double gap = inFrontOld.getProgress() - vehicle.getProgress();
                if (gap <= getSafeDistance() * 0.3) {
                    targetSpeed = Math.min(targetSpeed, inFrontOld.getSpeed());
                }
            }
        }

        vehicle.setSpeed(targetSpeed);
    }

    private LightDecision decideTrafficLightSpeed(Vehicle vehicle, TrafficLightContext context, double targetSpeed) {
        double myProgress = (vehicle.getLane() == context.lane)
                ? vehicle.getProgress()
                : context.lane.getProgress(vehicle.getPosition());
        double stopProgress = context.lane.getProgress(context.stopLine);
        double distToStop = stopProgress - myProgress;
        boolean isPastStop = distToStop < -3.0;
        if (isPastStop) {
            return new LightDecision(targetSpeed, false);
        }

        if (context.light.isRed()) {
            if (distToStop <= getStopDistance()) {
                return new LightDecision(getMinSpeed(), true);
            }
            if (distToStop <= getBrakeDistance()) {
                double ratio = (distToStop - getStopDistance()) / (getBrakeDistance() - getStopDistance());
                double speed = MathUtils.clamp(getCruiseSpeed(vehicle) * ratio, getMinSpeed(), getCruiseSpeed(vehicle));
                return new LightDecision(speed, true);
            }
        } else if (context.light.isYellow() && rushYellowLight()) {
            double speed = Math.min(getRushSpeed(vehicle), getCruiseSpeed(vehicle) * 1.1);
            return new LightDecision(speed, false);
        }
        return new LightDecision(targetSpeed, false);
    }

    private TrafficLightContext findTrafficLightContext(Vehicle vehicle, Lane currentLane, TrafficLight fallbackLight) {
        Lane homeLane = vehicle.getHomeLane();
        Lane lightLane = homeLane != null ? homeLane : currentLane;
        TrafficLight logicalLight = fallbackLight;
        Vector2D stopLine = null;

        if (lightLane != null) {
            Lane.TrafficControlPoint nextControl = lightLane.getNextTrafficControl(vehicle.getPosition());
            if (nextControl != null) {
                logicalLight = nextControl.getLight();
                stopLine = nextControl.getStopLine();
            } else if (logicalLight != null) {
                stopLine = lightLane.getStopLine();
            }
        }
        return new TrafficLightContext(lightLane, logicalLight, stopLine);
    }

    private static final class LightDecision {
        private final double targetSpeed;
        private final boolean stopping;
        private LightDecision(double targetSpeed, boolean stopping) {
            this.targetSpeed = targetSpeed;
            this.stopping = stopping;
        }
    }

    private static final class TrafficLightContext {
        private final Lane lane;
        private final TrafficLight light;
        private final Vector2D stopLine;
        private TrafficLightContext(Lane lane, TrafficLight light, Vector2D stopLine) {
            this.lane = lane;
            this.light = light;
            this.stopLine = stopLine;
        }
        private boolean isValid() {
            return lane != null && light != null && stopLine != null;
        }
    }
}
