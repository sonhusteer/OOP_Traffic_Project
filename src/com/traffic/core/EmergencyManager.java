package com.traffic.core;

import com.traffic.map.Intersection;
import com.traffic.map.Lane;
import java.util.List;

/** Applies priority-vehicle yielding rules without stopping cars that already cleared conflict. */
public class EmergencyManager {

    private static final double SAME_LANE_YIELD_DISTANCE = 155.0;
    private static final double EMERGENCY_LOOKAHEAD = 220.0;
    private static final double NORMAL_LOOKAHEAD = 185.0;
    private static final double CONFLICT_RADIUS = 42.0;
    private static final double CLEAR_MARGIN = 28.0;
    private static final double COMFORTABLE_BRAKE = 120.0;
    private static final double STOP_BUFFER = 10.0;

    public void update(List<Vehicle> vehicles, List<Intersection> intersections) {
        for (Vehicle v : vehicles) {
            if (!v.isPriority()) v.setYieldMode(Vehicle.YieldMode.NONE);
        }

        for (Vehicle priority : vehicles) {
            if (!priority.isPriority() || priority.getLane() == null) continue;
            applySameLaneYield(priority, vehicles);
            applyIntersectionYield(priority, vehicles, intersections);
        }
    }

    private void applySameLaneYield(Vehicle priority, List<Vehicle> vehicles) {
        Lane lane = priority.getLane();
        for (Vehicle normal : vehicles) {
            if (normal.isPriority() || normal.getLane() != lane) continue;
            double gap = normal.getRearProgress() - priority.getFrontProgress();
            boolean priorityBehind = gap > 0.0;
            boolean closeEnough = gap < SAME_LANE_YIELD_DISTANCE;
            boolean sameSideOrBlocking = Math.abs(normal.getLateralOffset() - priority.getLateralOffset()) < 34.0;
            if (priorityBehind && closeEnough && sameSideOrBlocking) {
                applyHigherPriorityMode(normal, Vehicle.YieldMode.YIELD_RIGHT);
            }
        }
    }

    private void applyIntersectionYield(Vehicle priority, List<Vehicle> vehicles,
                                        List<Intersection> intersections) {
        Lane priorityLane = priority.getLane();
        for (Intersection intersection : intersections) {
            if (!intersection.getLanes().contains(priorityLane)) continue;
            if (!isPriorityRelevantForIntersection(priority, intersection)) continue;

            for (Vehicle normal : vehicles) {
                if (normal.isPriority() || normal.getLane() == null) continue;
                if (normal.getLane() == priorityLane) continue;
                if (!intersection.getLanes().contains(normal.getLane())) continue;

                Vehicle.YieldMode mode = decideIntersectionMode(normal, intersection);
                applyHigherPriorityMode(normal, mode);
            }
        }
    }

    private boolean isPriorityRelevantForIntersection(Vehicle priority, Intersection intersection) {
        double conflictProgress = priority.getLane().getProgressOf(intersection.getCenter());
        double distanceToConflict = conflictProgress - priority.getFrontProgress();
        boolean approachingOrInside = distanceToConflict <= EMERGENCY_LOOKAHEAD;
        boolean notCleared = priority.getRearProgress() <= conflictProgress + CONFLICT_RADIUS + CLEAR_MARGIN;
        return approachingOrInside && notCleared;
    }

    private Vehicle.YieldMode decideIntersectionMode(Vehicle normal, Intersection intersection) {
        Lane normalLane = normal.getLane();
        double conflictProgress = normalLane.getProgressOf(intersection.getCenter());
        double conflictStart = conflictProgress - CONFLICT_RADIUS;
        double conflictEnd = conflictProgress + CONFLICT_RADIUS;

        // Đuôi xe đã qua vùng xung đột: thả ngay, không đứng khựng sau ngã tư.
        if (normal.getRearProgress() > conflictEnd + CLEAR_MARGIN) {
            return Vehicle.YieldMode.NONE;
        }

        // Đầu xe đã chạm vùng xung đột: phải đi tiếp để thoát, không phanh giữa ngã tư.
        if (normal.getFrontProgress() >= conflictStart) {
            return Vehicle.YieldMode.CLEAR_CONFLICT;
        }

        double distanceToConflict = conflictStart - normal.getFrontProgress();
        if (distanceToConflict > NORMAL_LOOKAHEAD) {
            return Vehicle.YieldMode.NONE;
        }

        double stopProgress = normalLane.getStopProgressBefore(conflictProgress);
        double distanceToStop = stopProgress - normal.getFrontProgress();

        // Đã qua hoặc quá sát vạch dừng thì không ép dừng, cho xe thoát khỏi conflict.
        if (distanceToStop <= 4.0) {
            return Vehicle.YieldMode.CLEAR_CONFLICT;
        }

        return canStopBeforeConflict(normal, distanceToStop)
                ? Vehicle.YieldMode.STOP_BEFORE_CONFLICT
                : Vehicle.YieldMode.CLEAR_CONFLICT;
    }

    private boolean canStopBeforeConflict(Vehicle normal, double distanceToConflict) {
        double speed = Math.max(0.0, normal.getSpeed());
        double stoppingDistance = speed * speed / (2.0 * COMFORTABLE_BRAKE) + STOP_BUFFER;
        return distanceToConflict > stoppingDistance;
    }

    private void applyHigherPriorityMode(Vehicle vehicle, Vehicle.YieldMode proposed) {
        if (proposed == Vehicle.YieldMode.NONE) return;
        if (priorityOf(proposed) >= priorityOf(vehicle.getYieldMode())) {
            vehicle.setYieldMode(proposed);
        }
    }

    private int priorityOf(Vehicle.YieldMode mode) {
        return switch (mode) {
            case NONE -> 0;
            case YIELD_RIGHT -> 1;
            case STOP_BEFORE_CONFLICT -> 2;
            case CLEAR_CONFLICT -> 3;
        };
    }
}
