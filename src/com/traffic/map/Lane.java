package com.traffic.map;

import com.traffic.core.MathUtils;
import com.traffic.core.Vector2D;
import com.traffic.core.Vehicle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Lane geometry plus occupancy helper. */
public class Lane {

    private static final double EPS = 1e-6;
    private static final double ROAD_HALF_WIDTH = 40.0;
    private static final double NORMAL_EDGE_MARGIN = 5.0;
    private static final double PRIORITY_CORRIDOR_MARGIN = 0.0;

    private final List<Vector2D> waypoints = new ArrayList<>();
    private final TrafficLight light;
    private final List<Vehicle> vehicles = new ArrayList<>();
    private final Set<Vehicle> reservedBy = new HashSet<>();
    private final LaneOccupancy occupancy = new LaneOccupancy(this);

    private Lane leftNeighbor;
    private Lane rightNeighbor;
    private boolean formalLaneChangeAllowed = false;
    private boolean inLaneOvertakeAllowed = true;
    private boolean spawnAllowed = true;

    public Lane(double startX, double startY,
                double endX, double endY,
                TrafficLight light) {
        this.light = light;
        waypoints.add(new Vector2D(startX, startY));
        waypoints.add(new Vector2D(endX, endY));
    }

    public void addwaypoint(double x, double y) {
        waypoints.add(waypoints.size() - 1, new Vector2D(x, y));
    }

    /** Compatibility alias; older map files use camel-case addWaypoint. */
    public void addWaypoint(double x, double y) {
        addwaypoint(x, y);
    }

    public List<Vector2D> getwaypoints() { return waypoints; }
    public Vector2D getStart() { return waypoints.get(0); }
    public Vector2D getEnd() { return waypoints.get(waypoints.size() - 1); }

    public double getLength() {
        double total = 0.0;
        for (int i = 0; i < waypoints.size() - 1; i++) {
            total += MathUtils.distance(waypoints.get(i), waypoints.get(i + 1));
        }
        return total;
    }

    public boolean isUsableForSpawn() { return spawnAllowed && getLength() > 5.0; }
    public boolean isSpawnAllowed() { return spawnAllowed; }
    public void setSpawnAllowed(boolean allowed) { this.spawnAllowed = allowed; }
    public boolean isDummy() { return !isUsableForSpawn(); }

    public Vector2D getPointAt(double progress) {
        double length = getLength();
        if (waypoints.size() < 2 || length <= EPS) return getStart();

        if (progress <= 0.0) {
            Vector2D start = getStart();
            Vector2D dir = getDirectionAt(0.0);
            return new Vector2D(start.getX() + dir.getX() * progress,
                                start.getY() + dir.getY() * progress);
        }
        if (progress >= length) {
            Vector2D end = getEnd();
            Vector2D dir = getDirectionAt(length);
            double extra = progress - length;
            return new Vector2D(end.getX() + dir.getX() * extra,
                                end.getY() + dir.getY() * extra);
        }

        double remaining = progress;
        for (int i = 0; i < waypoints.size() - 1; i++) {
            Vector2D a = waypoints.get(i);
            Vector2D b = waypoints.get(i + 1);
            double seg = MathUtils.distance(a, b);
            if (seg <= EPS) continue;
            if (remaining <= seg) {
                double t = remaining / seg;
                return new Vector2D(MathUtils.lerp(a.getX(), b.getX(), t),
                                    MathUtils.lerp(a.getY(), b.getY(), t));
            }
            remaining -= seg;
        }
        return getEnd();
    }

    public Vector2D getDirectionAt(double progress) {
        double length = getLength();
        if (waypoints.size() < 2 || length <= EPS) return new Vector2D(1, 0);

        double p = MathUtils.clamp(progress, 0.0, length);
        double accumulated = 0.0;
        for (int i = 0; i < waypoints.size() - 1; i++) {
            Vector2D a = waypoints.get(i);
            Vector2D b = waypoints.get(i + 1);
            double dx = b.getX() - a.getX();
            double dy = b.getY() - a.getY();
            double seg = Math.hypot(dx, dy);
            if (seg <= EPS) continue;
            if (p <= accumulated + seg || i == waypoints.size() - 2) {
                return new Vector2D(dx / seg, dy / seg);
            }
            accumulated += seg;
        }
        return new Vector2D(1, 0);
    }

    public double getAngleAt(double progress) {
        Vector2D dir = getDirectionAt(progress);
        return Math.toDegrees(Math.atan2(dir.getY(), dir.getX()));
    }

    /** Positive lateral offset means the vehicle's right side. */
    public Vector2D getRightNormalAt(double progress) {
        Vector2D dir = getDirectionAt(progress);
        return new Vector2D(-dir.getY(), dir.getX());
    }

    public Vector2D getPositionAt(double progress, double lateralOffset) {
        Vector2D center = getPointAt(progress);
        Vector2D normal = getRightNormalAt(progress);
        return new Vector2D(center.getX() + normal.getX() * lateralOffset,
                            center.getY() + normal.getY() * lateralOffset);
    }

    /** Compatibility alias for older LaneOccupancy code. */
    public Vector2D getPointAtProgress(double progress) { return getPointAt(progress); }

    /** Compatibility alias for older LaneOccupancy code. */
    public double getAngleAtProgress(double progress) { return getAngleAt(progress); }

    public double getProgressOf(Vector2D pos) {
        if (pos == null || waypoints.size() < 2) return 0.0;
        double bestProgress = 0.0;
        double bestDistSq = Double.MAX_VALUE;
        double accumulated = 0.0;

        for (int i = 0; i < waypoints.size() - 1; i++) {
            Vector2D a = waypoints.get(i);
            Vector2D b = waypoints.get(i + 1);
            double dx = b.getX() - a.getX();
            double dy = b.getY() - a.getY();
            double segLenSq = dx * dx + dy * dy;
            if (segLenSq <= EPS) continue;
            double t = ((pos.getX() - a.getX()) * dx + (pos.getY() - a.getY()) * dy) / segLenSq;
            t = MathUtils.clamp(t, 0.0, 1.0);
            double px = a.getX() + dx * t;
            double py = a.getY() + dy * t;
            double ex = pos.getX() - px;
            double ey = pos.getY() - py;
            double distSq = ex * ex + ey * ey;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestProgress = accumulated + Math.sqrt(segLenSq) * t;
            }
            accumulated += Math.sqrt(segLenSq);
        }
        return bestProgress;
    }

    /** Compatibility alias for older LaneOccupancy code. */
    public double getProgress(Vector2D pos) { return getProgressOf(pos); }

    public double getSignedLateralOffset(Vector2D pos) {
        double progress = getProgressOf(pos);
        Vector2D center = getPointAt(progress);
        Vector2D normal = getRightNormalAt(progress);
        return (pos.getX() - center.getX()) * normal.getX()
             + (pos.getY() - center.getY()) * normal.getY();
    }

    public Vector2D getStopLine() {
        if (waypoints.size() >= 3) return waypoints.get(1);
        return light != null ? light.getPosition() : getEnd();
    }

    public double getStopProgress() { return getProgressOf(getStopLine()); }

    /**
     * Lấy vạch dừng gần nhất nằm trước conflictProgress.
     * Hữu ích cho lane đi qua nhiều ngã tư liên tiếp.
     */
    public double getStopProgressBefore(double conflictProgress) {
        double best = Double.NaN;
        for (int i = 1; i < waypoints.size() - 1; i++) {
            double p = getProgressOf(waypoints.get(i));
            if (p < conflictProgress - 2.0 && (Double.isNaN(best) || p > best)) {
                best = p;
            }
        }
        if (!Double.isNaN(best)) return best;

        double fallback = getStopProgress();
        if (fallback < conflictProgress - 2.0) return fallback;
        return Math.max(0.0, conflictProgress - 72.0);
    }

    public Vehicle getVehicleAhead(Vehicle me) { return getVehicleAhead(me, 30.0); }

    public Vehicle getVehicleAhead(Vehicle me, double lateralThreshold) {
        if (me == null) return null;
        double myProgress = me.getLane() == this ? me.getLaneProgress() : getProgressOf(me.getPosition());
        return getVehicleAheadAt(myProgress, me, me.getLateralOffset(), lateralThreshold);
    }

    public Vehicle getVehicleAheadAt(double fromStart, Vehicle exclude) {
        return getVehicleAheadAt(fromStart, exclude, 0.0, Double.MAX_VALUE);
    }

    public Vehicle getVehicleAheadAt(double fromStart, Vehicle exclude,
                                     double lateralOffset, double lateralThreshold) {
        Vehicle inFront = null;
        double minDiff = Double.MAX_VALUE;
        for (Vehicle other : vehicles) {
            if (other == exclude) continue;
            double otherProgress = other.getLane() == this ? other.getLaneProgress() : getProgressOf(other.getPosition());
            double diff = otherProgress - fromStart;
            double lateralGap = Math.abs(other.getLateralOffset() - lateralOffset);
            if (diff > 0 && diff < minDiff && lateralGap <= lateralThreshold) {
                minDiff = diff;
                inFront = other;
            }
        }
        return inFront;
    }

    public boolean isSpaceFree(double progress, double lateralOffset,
                               double frontGap, double backGap,
                               double lateralGap, Vehicle exclude) {
        for (Vehicle v : vehicles) {
            if (v == exclude) continue;
            double otherProgress = v.getLane() == this ? v.getLaneProgress() : getProgressOf(v.getPosition());
            double longitudinal = otherProgress - progress;
            double lateral = Math.abs(v.getLateralOffset() - lateralOffset);
            if (longitudinal < frontGap && longitudinal > -backGap && lateral < lateralGap) return false;
        }
        for (Vehicle v : reservedBy) {
            if (v == exclude) continue;
            double otherProgress = getProgressOf(v.getPosition());
            double longitudinal = otherProgress - progress;
            double lateral = Math.abs(v.getLateralOffset() - lateralOffset);
            if (longitudinal < frontGap && longitudinal > -backGap && lateral < lateralGap) return false;
        }
        return true;
    }

    public boolean isLateralSpaceFree(Vehicle requester, double targetOffset,
                                      double frontGap, double backGap) {
        if (requester == null) return false;
        double progress = requester.getLane() == this ? requester.getLaneProgress() : getProgressOf(requester.getPosition());
        double lateralGap = Math.max(22.0, requester.getHeight() + 8.0);
        return isSpaceFree(progress, targetOffset, frontGap, backGap, lateralGap, requester);
    }

    public boolean isSpawnSpaceFree(double progress, double offset, double width, double height) {
        double lateralGap = Math.max(22.0, height + 8.0);
        return isSpaceFree(progress, offset, 62.0, 36.0, lateralGap, null);
    }

    public boolean isSafeToEnter(Vector2D pos, double safeGap) {
        for (Vehicle v : vehicles) if (MathUtils.distance(v.getPosition(), pos) < safeGap) return false;
        for (Vehicle v : reservedBy) if (MathUtils.distance(v.getPosition(), pos) < safeGap) return false;
        return true;
    }

    public LaneOccupancy occupancy() { return occupancy; }

    public Set<Vehicle> getReservedVehicles() { return reservedBy; }

    public double getLeftmostOffset(Vehicle vehicle) {
        double halfVehicle = vehicle != null ? vehicle.getHeight() / 2.0 : 10.0;
        double margin = NORMAL_EDGE_MARGIN;
        if (vehicle != null && vehicle.isUsingEmergencyCorridor()) {
            // Emergency corridor: xe uu tien duoc ap sat vach vang ben trai
            // de vuot vat can, nhung khong duoc vuot hẳn ra khoi mat duong.
            margin = PRIORITY_CORRIDOR_MARGIN;
        }
        return -ROAD_HALF_WIDTH + halfVehicle + margin;
    }

    public double getRightmostOffset(Vehicle vehicle) {
        double halfVehicle = vehicle != null ? vehicle.getHeight() / 2.0 : 10.0;
        return ROAD_HALF_WIDTH - halfVehicle - NORMAL_EDGE_MARGIN;
    }

    public double clampOffset(Vehicle vehicle, double offset) {
        return MathUtils.clamp(offset, getLeftmostOffset(vehicle), getRightmostOffset(vehicle));
    }

    /** Three virtual tracks inside one wide lane: left, center, right. */
    public int getTrackCount() { return 3; }

    public double getOffsetForTrack(int trackIndex) {
        return switch (trackIndex) {
            case 0 -> Vehicle.LEFT_OFFSET;
            case 2 -> Vehicle.RIGHT_OFFSET;
            default -> Vehicle.CENTER_OFFSET;
        };
    }

    public TrafficLight getLight() { return light; }
    public List<Vehicle> getVehicles() { return vehicles; }
    public void addVehicle(Vehicle v) { if (v != null && !vehicles.contains(v)) vehicles.add(v); }
    public void removeVehicle(Vehicle v) { vehicles.remove(v); reservedBy.remove(v); }

    public Lane getLeftNeighbor() { return leftNeighbor; }
    public void setLeftNeighbor(Lane left) { this.leftNeighbor = left; }
    public Lane getRightNeighbor() { return rightNeighbor; }
    public void setRightNeighbor(Lane right) { this.rightNeighbor = right; }

    public void reserve(Vehicle v) { if (v != null) reservedBy.add(v); }
    public void release(Vehicle v) { reservedBy.remove(v); }
    public void clearReservations() { reservedBy.clear(); }

    public boolean isFormalLaneChangeAllowed() { return formalLaneChangeAllowed; }
    public void setFormalLaneChangeAllowed(boolean allowed) { this.formalLaneChangeAllowed = allowed; }
    public boolean isAllowFormalLaneChange() { return formalLaneChangeAllowed; }
    public void setAllowFormalLaneChange(boolean allowed) { this.formalLaneChangeAllowed = allowed; }

    public boolean isInLaneOvertakeAllowed() { return inLaneOvertakeAllowed; }
    public void setInLaneOvertakeAllowed(boolean allowed) { this.inLaneOvertakeAllowed = allowed; }
    public boolean isAllowInLaneOvertake() { return inLaneOvertakeAllowed; }
    public void setAllowInLaneOvertake(boolean allowed) { this.inLaneOvertakeAllowed = allowed; }
}
