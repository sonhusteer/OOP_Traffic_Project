package com.traffic.drivers;

import com.traffic.core.LateralManeuver;
import com.traffic.core.Vehicle;
import com.traffic.map.Lane;

/**
 * Tang 6 clean-code: lap ke hoach dich ngang trong cung Lane.
 *
 * Driver khong tu tinh offset nua. Driver goi class nay de thu vuot,
 * nhường xe uu tien, hoac quay ve offset spawn ban dau.
 */
public final class SideShiftPlanner {

    public boolean tryOvertakeInsideLane(
            Vehicle vehicle,
            Vehicle front,
            double frontGap,
            double rearGap,
            boolean preferLeft
    ) {
        if (vehicle == null || front == null || vehicle.getLane() == null) {
            return false;
        }

        Lane lane = vehicle.getLane();

        Double passOffset = lane.occupancy().findPassingOffset(
                vehicle, front, preferLeft, frontGap, rearGap
        );
        if (passOffset == null) {
            return false;
        }

        boolean safe = lane.occupancy().isPassCorridorFree(
                vehicle, front, passOffset, frontGap, rearGap
        );
        if (!safe) {
            return false;
        }

        return vehicle.requestManeuver(LateralManeuver.overtake(passOffset, front));
    }


    public boolean tryMiddleGapOvertake(
            Vehicle vehicle,
            Vehicle front,
            double frontGap,
            double rearGap
    ) {
        if (vehicle == null || front == null || vehicle.getLane() == null) {
            return false;
        }

        Lane lane = vehicle.getLane();
        Double passOffset = lane.occupancy().findMiddlePassingOffset(
                vehicle, front, frontGap, rearGap
        );
        if (passOffset == null) {
            return false;
        }
        if (!lane.occupancy().isPassCorridorFree(vehicle, front, passOffset, frontGap, rearGap)) {
            return false;
        }
        return vehicle.requestManeuver(LateralManeuver.overtake(passOffset, front));
    }

    public boolean tryEmergencyCorridor(
            Vehicle vehicle,
            Vehicle front,
            double frontGap,
            double rearGap
    ) {
        if (vehicle == null || front == null || vehicle.getLane() == null || !vehicle.isPriority()) {
            return false;
        }

        Lane lane = vehicle.getLane();
        if (!lane.occupancy().isEmergencyCorridorFree(vehicle, front, frontGap, rearGap)) {
            return false;
        }

        double offset = lane.occupancy().findEmergencyCorridorOffset(vehicle);
        return vehicle.requestManeuver(LateralManeuver.emergencyCorridor(offset, front));
    }

    public boolean tryYieldRight(
            Vehicle vehicle,
            Vehicle priorityVehicle,
            double frontGap,
            double rearGap
    ) {
        if (vehicle == null || vehicle.getLane() == null) {
            return false;
        }

        Lane lane = vehicle.getLane();
        double rightOffset = lane.occupancy().findYieldRightOffset(vehicle, priorityVehicle);
        boolean safe = lane.occupancy().isSideSpaceFree(vehicle, rightOffset, frontGap, rearGap)
                && lane.occupancy().hasYieldRightMergeGap(vehicle, priorityVehicle);
        if (!safe) {
            return false;
        }

        return vehicle.requestManeuver(LateralManeuver.yieldRight(rightOffset, priorityVehicle));
    }

    public boolean tryGapFill(
            Vehicle vehicle,
            double frontGap,
            double rearGap
    ) {
        if (vehicle == null || vehicle.getLane() == null) {
            return false;
        }
        if (vehicle.getManeuverCooldown() > 0.0) {
            return false;
        }

        Lane lane = vehicle.getLane();
        Double offset = lane.occupancy().findGapFillOffset(vehicle);
        if (offset == null) {
            return false;
        }
        if (!lane.occupancy().isGapFillSpaceFree(vehicle, offset, frontGap, rearGap)) {
            return false;
        }
        return vehicle.requestManeuver(LateralManeuver.gapFill(offset));
    }

    public boolean tryYieldGapFill(
            Vehicle vehicle,
            double frontGap,
            double rearGap
    ) {
        if (vehicle == null || vehicle.getLane() == null) {
            return false;
        }
        if (vehicle.getManeuverCooldown() > 0.0) {
            return false;
        }

        Lane lane = vehicle.getLane();
        Double offset = lane.occupancy().findYieldGapFillOffset(vehicle);
        if (offset == null) {
            return false;
        }
        if (!lane.occupancy().isGapFillSpaceFree(vehicle, offset, frontGap, rearGap)) {
            return false;
        }
        return vehicle.requestManeuver(LateralManeuver.gapFill(offset));
    }

    public boolean tryReturnToPreferredOffset(
            Vehicle vehicle,
            double frontGap,
            double rearGap
    ) {
        if (vehicle == null || vehicle.getLane() == null) {
            return false;
        }
        if (!vehicle.isAwayFromPreferredOffset()) {
            return false;
        }

        Lane lane = vehicle.getLane();
        double preferred = vehicle.getPreferredLateralOffset();
        boolean safe = lane.occupancy().isSideSpaceFree(vehicle, preferred, frontGap, rearGap);
        if (!safe) {
            return false;
        }

        return vehicle.returnToPreferredOffset();
    }
}
