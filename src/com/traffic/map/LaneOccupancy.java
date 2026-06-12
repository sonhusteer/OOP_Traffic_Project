package com.traffic.map;

import com.traffic.core.MathUtils;
import com.traffic.core.Vector2D;
import com.traffic.core.Vehicle;
import java.util.ArrayList;
import java.util.List;

/**
 * Tang 4 clean-code: gom logic kiem tra xe trong mot Lane.
 *
 * LaneOccupancy lam viec theo hai truc:
 * - progress: vi tri doc theo LanePath.
 * - lateralOffset: vi tri ngang trong dai lane.
 *
 * Hai xe chi can nhau khi vua gan nhau theo progress, vua chong nhau theo
 * lateralOffset. Nho vay mot Lane rong co the chua hai xe ngang hang.
 */
public final class LaneOccupancy {

    private static final double SIDE_MARGIN = 4.0;
    private static final double LONGITUDINAL_MARGIN = 4.0;
    private static final int OFFSET_SEARCH_STEPS = 9;

    private final Lane lane;

    public LaneOccupancy(Lane lane) {
        if (lane == null) {
            throw new IllegalArgumentException("lane cannot be null");
        }
        this.lane = lane;
    }

    /** Tim xe phia truoc va con chong vung ngang voi xe me. */
    public Vehicle vehicleAheadOf(Vehicle me) {
        if (me == null) {
            return null;
        }
        double progress = progressOf(me);
        double offset = offsetOf(me);
        return vehicleAheadAt(progress, offset, me);
    }

    /** Tim xe phia truoc tai progress + lateralOffset cho truoc. */
    public Vehicle vehicleAheadAt(double progress, double lateralOffset, Vehicle exclude) {
        Vehicle best = null;
        double bestDiff = Double.MAX_VALUE;

        for (Vehicle other : lane.getVehicles()) {
            if (other == null || other == exclude) {
                continue;
            }

            double diff = progressOf(other) - progress;
            if (diff <= 0.0) {
                continue;
            }
            if (!hasLateralConflictAt(lateralOffset, exclude, other)) {
                continue;
            }
            if (diff < bestDiff) {
                bestDiff = diff;
                best = other;
            }
        }
        return best;
    }

    /** Tim xe phia sau tai progress + lateralOffset cho truoc. */
    public Vehicle vehicleBehindAt(double progress, double lateralOffset, Vehicle exclude) {
        Vehicle best = null;
        double bestDiff = Double.MAX_VALUE;

        for (Vehicle other : lane.getVehicles()) {
            if (other == null || other == exclude) {
                continue;
            }

            double diff = progress - progressOf(other);
            if (diff <= 0.0) {
                continue;
            }
            if (!hasLateralConflictAt(lateralOffset, exclude, other)) {
                continue;
            }
            if (diff < bestDiff) {
                bestDiff = diff;
                best = other;
            }
        }
        return best;
    }

    /** Kiem tra targetOffset co du khoang trong de xe me dich ngang sang khong. */
    public boolean isSideSpaceFree(
            Vehicle me,
            double targetOffset,
            double frontGap,
            double rearGap
    ) {
        if (me == null) {
            return false;
        }

        double progress = progressOf(me);
        targetOffset = lane.clampOffset(me, targetOffset);
        return isSpaceFreeAt(progress, targetOffset, me, frontGap, rearGap);
    }

    /** Kiem tra mot diem progress + lateralOffset co trong khong. */
    public boolean isSpaceFreeAt(
            double progress,
            double lateralOffset,
            Vehicle exclude,
            double frontGap,
            double rearGap
    ) {
        for (Vehicle other : lane.getVehicles()) {
            if (other == null || other == exclude) {
                continue;
            }
            if (hasLongitudinalConflict(progress, exclude, other, frontGap, rearGap)
                    && hasLateralConflictAt(lateralOffset, exclude, other)) {
                return false;
            }
        }

        for (Vehicle other : lane.getReservedVehicles()) {
            if (other == null || other == exclude) {
                continue;
            }
            if (hasLongitudinalConflict(progress, exclude, other, frontGap, rearGap)
                    && hasLateralConflictAt(lateralOffset, exclude, other)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Tim offset de vuot xe obstacle trong cung lane.
     * passLeft = true -> offset am; passLeft = false -> offset duong.
     */
    public Double findPassingOffset(
            Vehicle passingVehicle,
            Vehicle obstacle,
            boolean passLeft
    ) {
        if (passingVehicle == null || obstacle == null) {
            return null;
        }

        double direction = passLeft ? -1.0 : 1.0;
        double required = requiredLateralSeparation(passingVehicle, obstacle) + 2.0;

        List<Double> candidates = new ArrayList<>();
        candidates.add(offsetOf(obstacle) + direction * required);
        candidates.add(offsetOf(passingVehicle) + direction * required);
        candidates.add(passLeft
                ? lane.getLeftmostOffset(passingVehicle)
                : lane.getRightmostOffset(passingVehicle));

        double left = lane.getLeftmostOffset(passingVehicle);
        double right = lane.getRightmostOffset(passingVehicle);
        for (int i = 0; i <= OFFSET_SEARCH_STEPS; i++) {
            double t = (double) i / OFFSET_SEARCH_STEPS;
            candidates.add(passLeft
                    ? MathUtils.lerp(offsetOf(passingVehicle), left, t)
                    : MathUtils.lerp(offsetOf(passingVehicle), right, t));
        }

        for (double candidate : candidates) {
            double clamped = lane.clampOffset(passingVehicle, candidate);
            if (!hasLateralConflict(
                    passingVehicle,
                    clamped,
                    obstacle,
                    offsetOf(obstacle)
            )) {
                return clamped;
            }
        }
        return null;
    }

    /** Tim offset phai nhat de xe thuong nhuong xe uu tien trong cung lane. */
    public double findYieldRightOffset(Vehicle normal, Vehicle priority) {
        if (normal == null) {
            return 0.0;
        }
        return lane.getRightmostOffset(normal);
    }

    /** Kiem tra hai xe co chong vung ngang neu nam o hai offset nay khong. */
    public boolean hasLateralConflict(
            Vehicle a,
            double offsetA,
            Vehicle b,
            double offsetB
    ) {
        if (a == null || b == null) {
            return false;
        }
        return Math.abs(offsetA - offsetB) < requiredLateralSeparation(a, b);
    }

    /** Tinh progress cua xe theo Lane hien tai. */
    public double progressOf(Vehicle vehicle) {
        if (vehicle == null) {
            return 0.0;
        }
        if (vehicle.getLane() == lane) {
            return vehicle.getProgress();
        }
        return lane.getProgress(vehicle.getPosition());
    }

    /** Tinh lateralOffset cua mot vi tri man hinh so voi tim lane. */
    public double offsetOf(Vector2D position) {
        if (position == null) {
            return 0.0;
        }

        double progress = lane.getProgress(position);
        Vector2D center = lane.getPointAtProgress(progress);
        double angle = Math.toRadians(lane.getAngleAtProgress(progress));

        double rightNormalX = -Math.sin(angle);
        double rightNormalY = Math.cos(angle);
        double dx = position.getX() - center.getX();
        double dy = position.getY() - center.getY();
        return dx * rightNormalX + dy * rightNormalY;
    }

    private double offsetOf(Vehicle vehicle) {
        if (vehicle == null) {
            return 0.0;
        }
        if (vehicle.getLane() == lane) {
            return vehicle.getTargetLateralOffset();
        }
        return offsetOf(vehicle.getPosition());
    }

    private boolean hasLateralConflictAt(double lateralOffset, Vehicle me, Vehicle other) {
        if (other == null) {
            return false;
        }

        double otherCurrentOffset = other.getLane() == lane
                ? other.getLateralOffset()
                : offsetOf(other.getPosition());
        double otherTargetOffset = other.getLane() == lane
                ? other.getTargetLateralOffset()
                : otherCurrentOffset;

        if (me == null) {
            double sideMargin = other.getHeight() / 2.0 + SIDE_MARGIN;
            return Math.abs(lateralOffset - otherCurrentOffset) < sideMargin
                    || Math.abs(lateralOffset - otherTargetOffset) < sideMargin;
        }

        return hasLateralConflict(me, lateralOffset, other, otherCurrentOffset)
                || hasLateralConflict(me, lateralOffset, other, otherTargetOffset);
    }

    private boolean hasLongitudinalConflict(
            double baseProgress,
            Vehicle baseVehicle,
            Vehicle other,
            double frontGap,
            double rearGap
    ) {
        double diff = progressOf(other) - baseProgress;
        double padding = longitudinalPadding(baseVehicle, other);

        if (diff >= 0.0) {
            return diff < frontGap + padding;
        }
        return -diff < rearGap + padding;
    }

    private double requiredLateralSeparation(Vehicle a, Vehicle b) {
        return a.getHeight() / 2.0 + b.getHeight() / 2.0 + SIDE_MARGIN;
    }

    private double longitudinalPadding(Vehicle a, Vehicle b) {
        double aHalf = a != null ? a.getWidth() / 2.0 : 0.0;
        double bHalf = b != null ? b.getWidth() / 2.0 : 0.0;
        return aHalf + bHalf + LONGITUDINAL_MARGIN;
    }
}
