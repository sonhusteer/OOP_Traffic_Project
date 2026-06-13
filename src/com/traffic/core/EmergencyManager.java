package com.traffic.core;

import com.traffic.map.Intersection;
import com.traffic.map.Lane;
import com.traffic.core.Vector2D;
import java.util.List;

/** Applies priority-vehicle yielding rules without stopping cars that already cleared conflict. */
public class EmergencyManager {

    private static final double SAME_LANE_YIELD_DISTANCE = 155.0;
    private static final double EMERGENCY_LOOKAHEAD = 220.0;
    private static final double NORMAL_LOOKAHEAD = 160.0;
    private static final double STOP_ASSIGN_DISTANCE = 120.0;
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
                if (normal.isCommittedToIntersection()) {
                    applyHigherPriorityMode(normal, Vehicle.YieldMode.CLEAR_CONFLICT);
                    continue;
                }
                boolean canPullRight = lane.occupancy().hasYieldRightMergeGap(normal, priority);
                applyHigherPriorityMode(normal, canPullRight
                        ? Vehicle.YieldMode.YIELD_RIGHT
                        : Vehicle.YieldMode.URGENT_CLEAR_PATH);
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
                if (!lanesCanConflict(priorityLane, normal.getLane(), intersection)) continue;

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

    /**
     * Chi nhung lane cat nhau that su moi can dung de nhuong xe uu tien.
     * Cac lane song song/nguoc chieu tren cung mot mat duong khong bi dung,
     * vi chung khong cat duong di cua xe uu tien trong mo phong di thang nay.
     */
    private boolean lanesCanConflict(Lane priorityLane, Lane normalLane, Intersection intersection) {
        if (priorityLane == null || normalLane == null || priorityLane == normalLane) {
            return false;
        }

        double priorityConflict = priorityLane.getProgressOf(intersection.getCenter());
        double normalConflict = normalLane.getProgressOf(intersection.getCenter());

        Vector2D pDir = priorityLane.getDirectionAt(priorityConflict);
        Vector2D nDir = normalLane.getDirectionAt(normalConflict);
        double dot = Math.abs(pDir.getX() * nDir.getX() + pDir.getY() * nDir.getY());

        // dot gan 1: cung huong/nguoc huong -> khong cat nhau.
        // dot nho: gan vuong goc/cheo -> co kha nang xung dot tai giao lo.
        return dot < 0.70;
    }

    private Vehicle.YieldMode decideIntersectionMode(Vehicle normal, Intersection intersection) {
        Lane normalLane = normal.getLane();
        double conflictProgress = normalLane.getProgressOf(intersection.getCenter());
        double conflictStart = conflictProgress - CONFLICT_RADIUS;
        double conflictEnd = conflictProgress + CONFLICT_RADIUS;

        // Xe đã commit vào giao lộ, kể cả đi thẳng hoặc đang cua Bezier, phải
        // thoát giao lộ. Không dừng/không né ngang trong vùng xung đột.
        if (normal.isCommittedToIntersection()) {
            return Vehicle.YieldMode.CLEAR_CONFLICT;
        }

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

        // Chỉ xe đầu hàng trước vùng xung đột mới bị đánh dấu STOP.
        // Các xe phía sau sẽ tự follow xe trước, tránh viền vàng/STOP chồng lên nhau.
        if (!isLeadVehicleBeforeConflict(normal, conflictStart)) {
            return Vehicle.YieldMode.NONE;
        }

        double stopProgress = normalLane.getStopProgressBefore(conflictProgress);
        double distanceToStop = stopProgress - normal.getFrontProgress();

        // Xe còn quá xa vạch dừng thì chưa cần bị gán STOP vì xe ưu tiên.
        // Nếu gán quá sớm, xe sẽ dừng giữa đường và làm nghẽn đoàn phía sau.
        if (distanceToStop > STOP_ASSIGN_DISTANCE) {
            return Vehicle.YieldMode.NONE;
        }

        // Nếu xe vẫn chưa vào vùng xung đột thì không được thúc nó vượt đèn đỏ.
        // Dù xe đang sát vạch dừng, nó vẫn phải dừng trước conflict thay vì bị
        // CLEAR_CONFLICT đẩy qua ngã tư. Chỉ khi đầu xe đã vào conflictStart
        // mới xử lý CLEAR_CONFLICT ở nhánh phía trên.
        if (distanceToStop <= 4.0) {
            return Vehicle.YieldMode.STOP_BEFORE_CONFLICT;
        }

        return canStopBeforeConflict(normal, distanceToStop)
                ? Vehicle.YieldMode.STOP_BEFORE_CONFLICT
                : Vehicle.YieldMode.STOP_BEFORE_CONFLICT;
    }

    private boolean isLeadVehicleBeforeConflict(Vehicle candidate, double conflictStart) {
        Lane lane = candidate.getLane();
        if (lane == null) {
            return false;
        }
        double bestFront = -Double.MAX_VALUE;
        Vehicle lead = null;
        for (Vehicle other : lane.getVehicles()) {
            if (other == null || other.isPriority()) {
                continue;
            }
            if (other.getFrontProgress() >= conflictStart) {
                continue;
            }
            double front = other.getFrontProgress();
            if (front > bestFront) {
                bestFront = front;
                lead = other;
            }
        }
        return lead == candidate;
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
            case YIELD_RIGHT, PULL_RIGHT -> 1;
            case HOLD_POSITION, BLOCKED_YIELD -> 2;
            case CLEAR_PATH, URGENT_CLEAR_PATH -> 3;
            case STOP_BEFORE_CONFLICT, STOP -> 4;
            case CLEAR_CONFLICT, CLEAR_INTERSECTION -> 5;
        };
    }
}
