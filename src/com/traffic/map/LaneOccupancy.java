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
    private static final double PASS_CORRIDOR_FORWARD_FACTOR = 0.85;
    private static final double PASS_CORRIDOR_REAR_FACTOR = 0.55;

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
     *
     * Wrapper cu: chi dam bao khong chong ngang voi obstacle.
     * Driver nen goi overload co frontGap/rearGap de kiem tra hanh lang vuot.
     */
    public Double findPassingOffset(
            Vehicle passingVehicle,
            Vehicle obstacle,
            boolean passLeft
    ) {
        return findPassingOffset(passingVehicle, obstacle, passLeft, 0.0, 0.0);
    }

    /**
     * Tim offset vuot on dinh hon: khong chi lech khoi obstacle, ma con phai
     * co hanh lang an toan phia truoc/sau de xe khong do du giua chung.
     */
    public Double findPassingOffset(
            Vehicle passingVehicle,
            Vehicle obstacle,
            boolean passLeft,
            double frontGap,
            double rearGap
    ) {
        if (passingVehicle == null || obstacle == null) {
            return null;
        }

        double direction = passLeft ? -1.0 : 1.0;
        double required = requiredLateralSeparation(passingVehicle, obstacle) + 3.0;
        double current = offsetOf(passingVehicle);
        double obstacleOffset = offsetOf(obstacle);

        List<Double> candidates = new ArrayList<>();
        candidates.add(obstacleOffset + direction * required);
        candidates.add(current + direction * required);
        candidates.add(passLeft
                ? lane.getLeftmostOffset(passingVehicle)
                : lane.getRightmostOffset(passingVehicle));

        double edge = passLeft
                ? lane.getLeftmostOffset(passingVehicle)
                : lane.getRightmostOffset(passingVehicle);
        for (int i = 1; i <= OFFSET_SEARCH_STEPS; i++) {
            double t = (double) i / OFFSET_SEARCH_STEPS;
            candidates.add(MathUtils.lerp(current, edge, t));
        }

        Double best = null;
        double bestScore = Double.MAX_VALUE;
        for (double candidate : candidates) {
            double clamped = lane.clampOffset(passingVehicle, candidate);

            if (Math.abs(clamped - current) < 3.0) {
                continue;
            }
            if (hasLateralConflict(passingVehicle, clamped, obstacle, obstacleOffset)) {
                continue;
            }
            if (frontGap > 0.0 || rearGap > 0.0) {
                if (!isPassCorridorFree(passingVehicle, obstacle, clamped, frontGap, rearGap)) {
                    continue;
                }
            }

            double score = Math.abs(clamped - current)
                    + Math.abs(clamped - obstacleOffset) * 0.15
                    + (passLeft ? 0.0 : 8.0); // uu tien trai neu hai ben deu tot
            if (score < bestScore) {
                bestScore = score;
                best = clamped;
            }
        }
        return best;
    }

    /**
     * Kiem tra hanh lang vuot. Ham nay chat hon isSideSpaceFree vi no probe
     * them mot diem phia truoc; nhờ vậy xe it bi nhap nhang/doi y giua chung.
     */
    public boolean isPassCorridorFree(
            Vehicle vehicle,
            Vehicle obstacle,
            double targetOffset,
            double frontGap,
            double rearGap
    ) {
        if (vehicle == null) {
            return false;
        }
        targetOffset = lane.clampOffset(vehicle, targetOffset);
        double progress = progressOf(vehicle);

        if (!isSpaceFreeAt(progress, targetOffset, vehicle, frontGap, rearGap)) {
            return false;
        }

        double probeForward = Math.max(24.0, frontGap * PASS_CORRIDOR_FORWARD_FACTOR);
        double probeRear = Math.max(18.0, rearGap * PASS_CORRIDOR_REAR_FACTOR);
        if (!isSpaceFreeAt(progress + probeForward, targetOffset, vehicle, frontGap * 0.55, probeRear)) {
            return false;
        }

        // Nếu obstacle vẫn chồng ngang ở offset đích thì không phải là vượt hợp lệ.
        if (obstacle != null && obstacle.getLane() == lane
                && hasLateralConflict(vehicle, targetOffset, obstacle, offsetOf(obstacle))) {
            return false;
        }
        return true;
    }

    /** Offset hanh lang uu tien sat vach vang ben trai cua lane. */
    public double findEmergencyCorridorOffset(Vehicle priorityVehicle) {
        if (priorityVehicle == null) {
            return lane.getLeftmostOffset(null);
        }
        Vehicle.ManeuverState oldState = priorityVehicle.getManeuverState();
        priorityVehicle.setManeuverState(Vehicle.ManeuverState.EMERGENCY_CORRIDOR);
        double offset = lane.getLeftmostOffset(priorityVehicle);
        priorityVehicle.setManeuverState(oldState);
        return offset;
    }

    /**
     * Kiem tra hanh lang uu tien sat vach vang co du an toan khong.
     * Ham nay kiem tra lane hien tai va lane doi dien/gan ben trai neu co,
     * de tranh xe uu tien lấn vach vang khi co xe nguoc chieu dang toi.
     */
    public boolean isEmergencyCorridorFree(
            Vehicle priorityVehicle,
            Vehicle obstacle,
            double frontGap,
            double rearGap
    ) {
        if (priorityVehicle == null || !priorityVehicle.isPriority()) {
            return false;
        }

        double corridorOffset = findEmergencyCorridorOffset(priorityVehicle);
        double progress = progressOf(priorityVehicle);

        if (!isSpaceFreeAt(progress, corridorOffset, priorityVehicle, frontGap, rearGap)) {
            return false;
        }
        if (!isSpaceFreeAt(progress + Math.max(45.0, frontGap * 0.75),
                corridorOffset, priorityVehicle, frontGap * 0.65, rearGap * 0.65)) {
            return false;
        }

        Lane opposite = lane.getLeftNeighbor();
        if (opposite != null) {
            Vector2D p0 = lane.getPositionAt(progress, corridorOffset);
            Vector2D p1 = lane.getPositionAt(progress + Math.max(80.0, frontGap), corridorOffset);
            for (Vehicle other : opposite.getVehicles()) {
                if (other == null || other == priorityVehicle) {
                    continue;
                }
                double d0 = MathUtils.distance(other.getPosition(), p0);
                double d1 = MathUtils.distance(other.getPosition(), p1);
                if (Math.min(d0, d1) < Math.max(62.0, priorityVehicle.getWidth() + other.getWidth())) {
                    return false;
                }
            }
        }

        return true;
    }


    /**
     * Tim offset vuot o khoang giua lane/thoa hiep giua cac slot.
     * Dung cho aggressive driver va priority driver truoc khi priority duoc phep
     * dung emergency corridor. Khac voi vuot trai/phai co dinh, ham nay quet
     * lien tuc trong bien an toan cua lane va uu tien offset gan center.
     */
    public Double findMiddlePassingOffset(
            Vehicle passingVehicle,
            Vehicle obstacle,
            double frontGap,
            double rearGap
    ) {
        if (passingVehicle == null || obstacle == null) {
            return null;
        }

        double left = lane.getLeftmostOffset(passingVehicle);
        double right = lane.getRightmostOffset(passingVehicle);
        double current = offsetOf(passingVehicle);
        double obstacleOffset = offsetOf(obstacle);

        List<Double> candidates = new ArrayList<>();
        candidates.add(Vehicle.CENTER_OFFSET);
        candidates.add((current + obstacleOffset) / 2.0);
        candidates.add(current * 0.65);
        candidates.add(obstacleOffset * 0.35);

        int steps = 14;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            candidates.add(MathUtils.lerp(left, right, t));
        }

        Double best = null;
        double bestScore = Double.MAX_VALUE;
        for (double candidate : candidates) {
            double clamped = lane.clampOffset(passingVehicle, candidate);
            if (Math.abs(clamped - current) < 2.5) {
                continue;
            }
            if (hasLateralConflict(passingVehicle, clamped, obstacle, obstacleOffset)) {
                continue;
            }
            if (!isPassCorridorFree(passingVehicle, obstacle, clamped, frontGap, rearGap)) {
                continue;
            }

            double score = Math.abs(clamped) * 0.55
                    + Math.abs(clamped - current) * 0.32
                    + Math.abs(clamped - obstacleOffset) * 0.08;
            if (score < bestScore) {
                bestScore = score;
                best = clamped;
            }
        }
        return best;
    }

    /** Tim offset phai nhat de xe thuong nhuong xe uu tien trong cung lane. */
    public double findYieldRightOffset(Vehicle normal, Vehicle priority) {
        if (normal == null) {
            return 0.0;
        }
        return lane.getRightmostOffset(normal);
    }


    /**
     * Tim offset gan nhat de xe chen vao khoang trong trong hang cho.
     * Khac voi overtake: gap-fill khong nham vuot qua xe truoc, chi doi sang
     * mot slot trong hon va sau do slot moi tro thanh preferred offset.
     */
    public Double findGapFillOffset(Vehicle vehicle) {
        if (vehicle == null) {
            return null;
        }

        double current = offsetOf(vehicle);
        double preferred = vehicle.getPreferredLateralOffset();
        double[] candidates = new double[] {
                preferred,
                Vehicle.CENTER_OFFSET,
                Vehicle.RIGHT_OFFSET,
                Vehicle.LEFT_OFFSET,
                lane.getRightmostOffset(vehicle),
                lane.getLeftmostOffset(vehicle)
        };

        Double best = null;
        double bestScore = Double.MAX_VALUE;
        for (double candidate : candidates) {
            double clamped = lane.clampOffset(vehicle, candidate);
            if (Math.abs(clamped - current) < 3.0) {
                continue;
            }
            if (!isGapFillSpaceFree(vehicle, clamped)) {
                continue;
            }
            double score = Math.abs(clamped - current) + Math.abs(clamped - preferred) * 0.25;
            if (score < bestScore) {
                bestScore = score;
                best = clamped;
            }
        }
        return best;
    }

    /** Kiem tra khoang trong cho thao tac gap-fill: can ca ngang lan phia truoc. */
    public boolean isGapFillSpaceFree(Vehicle vehicle, double targetOffset) {
        if (vehicle == null) {
            return false;
        }
        double progress = progressOf(vehicle);
        double frontGap = Math.max(42.0, vehicle.getWidth() + 24.0);
        double rearGap = Math.max(26.0, vehicle.getWidth() * 0.55);
        if (!isSideSpaceFree(vehicle, targetOffset, frontGap, rearGap)) {
            return false;
        }

        // Kiem tra them mot diem hoi tien len de tranh chen ngang vao dau xe khac.
        double probeProgress = progress + Math.max(16.0, vehicle.getWidth() * 0.4);
        return isSpaceFreeAt(probeProgress, targetOffset, vehicle, frontGap * 0.65, rearGap);
    }

    /**
     * Pull-right cho xe uu tien can khe nhap that su. Ham nay chat hon
     * isSideSpaceFree de tranh xe thuong co chen phai khi ben phai dang co hang xe.
     */
    public boolean hasYieldRightMergeGap(Vehicle normal, Vehicle priority) {
        if (normal == null) {
            return false;
        }
        double rightOffset = findYieldRightOffset(normal, priority);
        double frontGap = Math.max(70.0, normal.getWidth() + 40.0);
        double rearGap = Math.max(95.0, normal.getWidth() + 60.0);
        return isSideSpaceFree(normal, rightOffset, frontGap, rearGap)
                && isSpaceFreeAt(progressOf(normal) + normal.getWidth() * 0.35,
                        rightOffset, normal, frontGap * 0.75, rearGap);
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
