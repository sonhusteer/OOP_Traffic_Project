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
        Double passOffset = lane.occupancy().findPassingOffset(vehicle, front, preferLeft);
        if (passOffset == null) {
            passOffset = lane.occupancy().findPassingOffset(vehicle, front, !preferLeft);
        }
        if (passOffset == null) {
            return false;
        }

        boolean safe = lane.occupancy().isSideSpaceFree(vehicle, passOffset, frontGap, rearGap);
        if (!safe) {
            return false;
        }

        return vehicle.requestManeuver(LateralManeuver.overtake(passOffset, front));
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
        boolean safe = lane.occupancy().isSideSpaceFree(vehicle, rightOffset, frontGap, rearGap);
        if (!safe) {
            return false;
        }

        return vehicle.requestManeuver(LateralManeuver.yieldRight(rightOffset, priorityVehicle));
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
