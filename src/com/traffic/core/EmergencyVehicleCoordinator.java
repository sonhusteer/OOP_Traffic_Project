package com.traffic.core;

import com.traffic.map.Intersection;
import com.traffic.map.Lane;
import java.util.List;

/**
 * Dieu phoi luat uu tien cho xe khan cap.
 *
 * Lop nay chi lam 2 viec:
 * 1. Phat hien tinh huong co xe uu tien.
 * 2. Gan YieldMode cho xe thuong.
 *
 * Lop nay KHONG:
 * - Goi startLaneChange().
 * - Goi requestManeuver().
 * - Tu set position/speed cua xe.
 *
 * Driver se doc YieldMode va quyet dinh cach thuc thi:
 * - PULL_RIGHT -> thu dich lateralOffset sang phai.
 * - CLEAR_PATH -> tang toc nhe de thoat khoi duong xe uu tien.
 * - STOP -> dung lai truoc vung xung dot.
 * - CLEAR_INTERSECTION -> di tiep de thoat khoi giao lo, khong dung giua nga tu.
 */
public final class EmergencyVehicleCoordinator {

    private static final double SAME_LANE_YIELD_DIST = 150.0;
    private static final double SAME_LANE_REAR_IGNORE_DIST = 10.0;

    private static final double INTERSECTION_DANGER_RADIUS = 200.0;
    private static final double INTERSECTION_CLEAR_RADIUS = 65.0;

    private static final double YIELD_FRONT_GAP = 55.0;
    private static final double YIELD_REAR_GAP = 85.0;

    /** Ap dung lai YieldMode moi frame dua tren trang thai hien tai. */
    public void apply(List<Vehicle> vehicles, List<Intersection> intersections) {
        if (vehicles == null) {
            return;
        }

        resetNormalVehicles(vehicles);

        for (Vehicle priority : vehicles) {
            if (priority == null || !priority.isPriority()) {
                continue;
            }
            applySameLaneRules(priority, vehicles);
            applyIntersectionRules(priority, vehicles, intersections);
        }
    }

    private void resetNormalVehicles(List<Vehicle> vehicles) {
        for (Vehicle vehicle : vehicles) {
            if (vehicle != null && !vehicle.isPriority()) {
                vehicle.setYieldMode(Vehicle.YieldMode.NONE);
            }
        }
    }

    private void applySameLaneRules(Vehicle priority, List<Vehicle> vehicles) {
        Lane priorityLane = priority.getLane();
        if (priorityLane == null) {
            return;
        }

        double priorityProgress = priority.getProgress();
        for (Vehicle normal : vehicles) {
            if (normal == null || normal.isPriority()) {
                continue;
            }
            if (normal.getLane() != priorityLane) {
                continue;
            }

            double gap = normal.getProgress() - priorityProgress;
            if (gap <= SAME_LANE_REAR_IGNORE_DIST || gap >= SAME_LANE_YIELD_DIST) {
                continue;
            }

            // Coordinator chi kiem tra co kha nang nhich phai hay khong.
            // Viec that su nhich phai do SideShiftPlanner/Driver thuc hien.
            double rightOffset = priorityLane.occupancy().findYieldRightOffset(normal, priority);
            boolean canPullRight = priorityLane.occupancy().isSideSpaceFree(
                    normal,
                    rightOffset,
                    YIELD_FRONT_GAP,
                    YIELD_REAR_GAP
            );

            applyMode(
                    normal,
                    canPullRight
                            ? Vehicle.YieldMode.PULL_RIGHT
                            : Vehicle.YieldMode.CLEAR_PATH
            );
        }
    }

    private void applyIntersectionRules(
            Vehicle priority,
            List<Vehicle> vehicles,
            List<Intersection> intersections
    ) {
        if (intersections == null || intersections.isEmpty()) {
            return;
        }

        Lane priorityLane = priority.getLane();
        for (Intersection intersection : intersections) {
            if (intersection == null) {
                continue;
            }
            if (priorityLane != null && !intersection.getLanes().contains(priorityLane)) {
                continue;
            }

            double priorityDistance = MathUtils.distance(
                    priority.getPosition(),
                    intersection.getCenter()
            );
            if (priorityDistance > INTERSECTION_DANGER_RADIUS) {
                continue;
            }

            for (Vehicle normal : vehicles) {
                if (normal == null || normal.isPriority()) {
                    continue;
                }
                if (normal.getLane() == null) {
                    continue;
                }
                if (normal.getLane() == priorityLane) {
                    continue;
                }
                if (!intersection.getLanes().contains(normal.getLane())) {
                    continue;
                }

                double normalDistance = MathUtils.distance(
                        normal.getPosition(),
                        intersection.getCenter()
                );
                if (normalDistance > INTERSECTION_DANGER_RADIUS) {
                    continue;
                }

                applyMode(
                        normal,
                        normalDistance <= INTERSECTION_CLEAR_RADIUS
                                ? Vehicle.YieldMode.CLEAR_INTERSECTION
                                : Vehicle.YieldMode.STOP
                );
            }
        }
    }

    private void applyMode(Vehicle vehicle, Vehicle.YieldMode candidate) {
        if (vehicle == null || candidate == null) {
            return;
        }
        if (priorityOf(candidate) >= priorityOf(vehicle.getYieldMode())) {
            vehicle.setYieldMode(candidate);
        }
    }

    private int priorityOf(Vehicle.YieldMode mode) {
        if (mode == null) {
            return 0;
        }
        return switch (mode) {
            case NONE -> 0;
            case CLEAR_PATH -> 1;
            case PULL_RIGHT -> 2;
            case STOP -> 3;
            case CLEAR_INTERSECTION -> 4;
        };
    }
}
