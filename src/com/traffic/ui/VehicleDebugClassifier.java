package com.traffic.ui;

import com.traffic.core.PriorityRouteAnalyzer;
import com.traffic.core.PriorityRouteContext;
import com.traffic.core.Vehicle;

/** Classifies a vehicle by the reason for its current behavior, not by renderer color. */
public final class VehicleDebugClassifier {
    private VehicleDebugClassifier() {}

    public static DebugVisualState classify(Vehicle v) {
        if (v == null) return DebugVisualState.ERROR;

        Vehicle.YieldMode yield = v.getYieldMode();
        Vehicle.ManeuverState maneuver = v.getManeuverState();
        Vehicle.IntersectionManeuverState ix = v.getIntersectionManeuverState();

        if (yield == Vehicle.YieldMode.URGENT_CLEAR_PATH
                || yield == Vehicle.YieldMode.CLEAR_PATH
                || maneuver == Vehicle.ManeuverState.URGENT_CLEARING
                || (v.hasActivePriorityYieldLock()
                    && (maneuver == Vehicle.ManeuverState.YIELDING_RIGHT
                        || maneuver == Vehicle.ManeuverState.YIELD_RETURNING))) {
            return DebugVisualState.EMERGENCY_YIELD;
        }

        if (v.isPriority() && isPriorityQueueFollow(v)) {
            return DebugVisualState.PRIORITY_QUEUE;
        }

        if (ix == Vehicle.IntersectionManeuverState.APPROACHING
                || ix == Vehicle.IntersectionManeuverState.PREPARING_TURN_SLOT
                || ix == Vehicle.IntersectionManeuverState.PREPARING_TURN_SLOT_PAUSED
                || ix == Vehicle.IntersectionManeuverState.WAITING_BEFORE_INTERSECTION
                || ix == Vehicle.IntersectionManeuverState.CROSSING_STRAIGHT
                || ix == Vehicle.IntersectionManeuverState.TURNING_LEFT
                || ix == Vehicle.IntersectionManeuverState.TURNING_RIGHT
                || ix == Vehicle.IntersectionManeuverState.EXITING
                || ix == Vehicle.IntersectionManeuverState.CLEARING_FOR_PRIORITY
                || maneuver == Vehicle.ManeuverState.CLEARING_CONFLICT) {
            return DebugVisualState.TURNING_OR_INTERSECTION;
        }

        if (yield == Vehicle.YieldMode.STOP_BEFORE_CONFLICT
                || yield == Vehicle.YieldMode.STOP
                || yield == Vehicle.YieldMode.HOLD_POSITION
                || yield == Vehicle.YieldMode.BLOCKED_YIELD
                || maneuver == Vehicle.ManeuverState.HOLDING_POSITION
                || maneuver == Vehicle.ManeuverState.STOPPED_FOR_CONFLICT) {
            return DebugVisualState.ORDINARY_WAIT;
        }

        if (maneuver == Vehicle.ManeuverState.GAP_FILLING
                || maneuver == Vehicle.ManeuverState.GAP_FILL_RETURNING) {
            return DebugVisualState.GAP_FILL;
        }

        if (maneuver == Vehicle.ManeuverState.OVERTAKE_SHIFT_LEFT
                || maneuver == Vehicle.ManeuverState.OVERTAKE_PASSING
                || maneuver == Vehicle.ManeuverState.OVERTAKE_RETURNING
                || maneuver == Vehicle.ManeuverState.EMERGENCY_CORRIDOR) {
            return DebugVisualState.OVERTAKE;
        }

        return DebugVisualState.NORMAL;
    }

    private static boolean isPriorityQueueFollow(Vehicle v) {
        if (v == null || !v.isPriority() || v.getLane() == null) return false;
        Vehicle ahead = v.getLane().occupancy().vehicleAheadOf(v);
        if (ahead == null) return false;
        PriorityRouteContext ctx = PriorityRouteAnalyzer.getCurrent().get(v, ahead);
        if (ctx == null || !ctx.isQueueLike()) return false;
        double gap = ahead.getRearProgress() - v.getFrontProgress();
        return gap > 0.0 && gap <= 96.0;
    }
}
