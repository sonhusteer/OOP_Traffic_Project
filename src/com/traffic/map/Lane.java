package com.traffic.map;

import com.traffic.core.MathUtils;
import com.traffic.core.Vector2D;
import com.traffic.core.Vehicle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lane geometry plus occupancy helper. */
public class Lane {

    private static final double ROAD_HALF_WIDTH = 40.0;
    private static final double NORMAL_EDGE_MARGIN = 5.0;
    private static final double PRIORITY_CORRIDOR_MARGIN = 0.0;

    private final List<Vector2D> waypoints = new ArrayList<>();
    private final LanePath path = new LanePath(waypoints);
    private final TrafficLight light;
    private final List<LaneControlPoint> controlPoints = new ArrayList<>();
    private final List<Vehicle> vehicles = new ArrayList<>();
    private final Set<Vehicle> reservedBy = new HashSet<>();
    private final LaneOccupancy occupancy = new LaneOccupancy(this);
    private final Map<Intersection, EnumMap<Vehicle.TurnDecision, Lane>> turnTargets = new HashMap<>();

    // Historical neighbor fields are kept for old map/UI code.
    private Lane leftNeighbor;
    private Lane rightNeighbor;

    // New explicit semantics. Opposing lane is used for emergency-corridor
    // safety checks. Adjacent lanes are only metadata/UI helpers.
    private Lane opposingLane;
    private Lane leftAdjacentLane;
    private Lane rightAdjacentLane;

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

    public double getLength() { return path.length(); }

    public boolean isUsableForSpawn() { return spawnAllowed && getLength() > 5.0; }
    public boolean isSpawnAllowed() { return spawnAllowed; }
    public void setSpawnAllowed(boolean allowed) { this.spawnAllowed = allowed; }
    public boolean isDummy() { return !isUsableForSpawn(); }

    public Vector2D getPointAt(double progress) { return path.centerAt(progress); }

    public Vector2D getDirectionAt(double progress) { return path.directionAt(progress); }

    public double getAngleAt(double progress) { return path.angleAt(progress); }

    /** Positive lateral offset means the vehicle's right side. */
    public Vector2D getRightNormalAt(double progress) {
        Vector2D dir = getDirectionAt(progress);
        return new Vector2D(-dir.getY(), dir.getX());
    }

    public Vector2D getPositionAt(double progress, double lateralOffset) {
        return path.pointAt(progress, lateralOffset);
    }

    /** Compatibility alias for older LaneOccupancy code. */
    public Vector2D getPointAtProgress(double progress) { return getPointAt(progress); }

    /** Compatibility alias for older LaneOccupancy code. */
    public double getAngleAtProgress(double progress) { return getAngleAt(progress); }

    public double getProgressOf(Vector2D pos) { return path.progressOf(pos); }

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

    public void addControlPoint(double progress, TrafficLight controlLight) {
        if (controlLight == null) return;
        controlPoints.add(new LaneControlPoint(MathUtils.clamp(progress, 0.0, getLength()), controlLight));
        controlPoints.sort((a, b) -> Double.compare(a.getProgress(), b.getProgress()));
    }

    public void addControlPoint(double x, double y, TrafficLight controlLight) {
        addControlPoint(getProgressOf(new Vector2D(x, y)), controlLight);
    }

    public void clearControlPoints() { controlPoints.clear(); }

    public List<LaneControlPoint> getControlPoints() {
        if (!controlPoints.isEmpty()) {
            return Collections.unmodifiableList(controlPoints);
        }
        if (light == null) {
            return Collections.emptyList();
        }
        return List.of(new LaneControlPoint(getStopProgress(), light));
    }

    public LaneControlPoint getNextControlPoint(double frontProgress) {
        LaneControlPoint firstAhead = null;
        for (LaneControlPoint controlPoint : getControlPoints()) {
            if (controlPoint == null || controlPoint.getLight() == null) continue;
            if (controlPoint.getProgress() >= frontProgress - 3.0) {
                firstAhead = controlPoint;
                break;
            }
        }
        return firstAhead;
    }

    public TrafficLight getNextLight(double frontProgress) {
        LaneControlPoint controlPoint = getNextControlPoint(frontProgress);
        return controlPoint != null ? controlPoint.getLight() : null;
    }

    public List<TrafficLight> getAllTrafficLights() {
        Set<TrafficLight> unique = new LinkedHashSet<>();
        if (light != null) unique.add(light);
        for (LaneControlPoint controlPoint : controlPoints) {
            if (controlPoint != null && controlPoint.getLight() != null) {
                unique.add(controlPoint.getLight());
            }
        }
        return new ArrayList<>(unique);
    }

    /**
     * Lấy vạch dừng gần nhất nằm trước conflictProgress.
     * Hữu ích cho lane đi qua nhiều ngã tư liên tiếp.
     */
    public double getStopProgressBefore(double conflictProgress) {
        double best = Double.NaN;
        for (LaneControlPoint controlPoint : getControlPoints()) {
            double p = controlPoint.getProgress();
            if (p < conflictProgress - 2.0 && (Double.isNaN(best) || p > best)) {
                best = p;
            }
        }
        if (!Double.isNaN(best)) return best;

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

    public Vehicle getVehicleAhead(Vehicle me) { return occupancy.vehicleAheadOf(me); }

    public Vehicle getVehicleAhead(Vehicle me, double lateralThreshold) {
        // lateralThreshold kept for source compatibility. Occupancy uses real vehicle
        // dimensions and current/target offsets, which is safer than a fixed threshold.
        return occupancy.vehicleAheadOf(me);
    }

    public Vehicle getVehicleAheadAt(double fromStart, Vehicle exclude) {
        return occupancy.vehicleAheadAt(fromStart, exclude != null ? exclude.getLateralOffset() : 0.0, exclude);
    }

    public Vehicle getVehicleAheadAt(double fromStart, Vehicle exclude,
                                     double lateralOffset, double lateralThreshold) {
        return occupancy.vehicleAheadAt(fromStart, lateralOffset, exclude);
    }

    public boolean isSpaceFree(double progress, double lateralOffset,
                               double frontGap, double backGap,
                               double lateralGap, Vehicle exclude) {
        // Legacy API: use the old caller's lateralGap as an additional minimum,
        // but still let LaneOccupancy account for vehicle dimensions/targets.
        return occupancy.isSpaceFreeAt(progress, lateralOffset, exclude, frontGap, backGap);
    }

    public boolean isLateralSpaceFree(Vehicle requester, double targetOffset,
                                      double frontGap, double backGap) {
        return occupancy.isSideSpaceFree(requester, targetOffset, frontGap, backGap);
    }

    public boolean isSpawnSpaceFree(double progress, double offset, double width, double height) {
        double frontGap = Math.max(62.0, width + 28.0);
        double backGap = Math.max(36.0, width * 0.65);

        for (Vehicle v : vehicles) {
            if (spawnConflicts(progress, offset, width, height, v, frontGap, backGap)) return false;
        }
        for (Vehicle v : reservedBy) {
            if (spawnConflicts(progress, offset, width, height, v, frontGap, backGap)) return false;
        }
        return true;
    }

    private boolean spawnConflicts(double progress, double offset, double width, double height,
                                   Vehicle other, double frontGap, double backGap) {
        if (other == null) return false;
        double otherProgress = other.getLane() == this ? other.getLaneProgress() : getProgressOf(other.getPosition());
        double longitudinal = otherProgress - progress;
        if (!(longitudinal < frontGap && longitudinal > -backGap)) return false;

        double otherOffset = other.getLane() == this ? other.getLateralOffset() : getSignedLateralOffset(other.getPosition());
        double otherTarget = other.getLane() == this ? other.getTargetLateralOffset() : otherOffset;
        double required = height / 2.0 + other.getHeight() / 2.0 + 5.0;
        return Math.abs(otherOffset - offset) < required || Math.abs(otherTarget - offset) < required;
    }

    public boolean isSafeToEnter(Vector2D pos, double safeGap) {
        for (Vehicle v : vehicles) if (MathUtils.distance(v.getPosition(), pos) < safeGap) return false;
        for (Vehicle v : reservedBy) if (MathUtils.distance(v.getPosition(), pos) < safeGap) return false;
        return true;
    }

    public LaneOccupancy occupancy() { return occupancy; }

    public void setTurnTarget(Intersection intersection, Vehicle.TurnDecision decision, Lane targetLane) {
        if (intersection == null || decision == null) return;
        turnTargets.computeIfAbsent(intersection, key -> new EnumMap<>(Vehicle.TurnDecision.class))
                .put(decision, targetLane);
    }

    public Lane getTurnTarget(Intersection intersection, Vehicle.TurnDecision decision) {
        if (intersection == null || decision == null) return null;
        EnumMap<Vehicle.TurnDecision, Lane> byDecision = turnTargets.get(intersection);
        return byDecision != null ? byDecision.get(decision) : null;
    }

    public Set<Vehicle> getReservedVehicles() { return reservedBy; }

    public double getLeftmostOffset(Vehicle vehicle) {
        double halfVehicle = vehicle != null ? vehicle.getHeight() / 2.0 : 10.0;
        double margin = NORMAL_EDGE_MARGIN;
        if (vehicle != null && vehicle.isUsingEmergencyCorridor()) {
            // Emergency corridor: xe uu tien duoc ap sat vach vang ben trai
            // de vuot vat can, nhung khong duoc vuot han ra khoi mat duong.
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

    /** Two normal virtual tracks inside one wide lane: left and right. */
    public int getTrackCount() { return 2; }

    public double getOffsetForTrack(int trackIndex) {
        return (Math.floorMod(trackIndex, 2) == 0)
                ? Vehicle.LEFT_OFFSET
                : Vehicle.RIGHT_OFFSET;
    }

    public TrafficLight getLight() { return light; }
    public List<Vehicle> getVehicles() { return vehicles; }
    public void addVehicle(Vehicle v) { if (v != null && !vehicles.contains(v)) vehicles.add(v); }
    public void removeVehicle(Vehicle v) { vehicles.remove(v); reservedBy.remove(v); }

    public Lane getLeftNeighbor() { return leftNeighbor; }
    public void setLeftNeighbor(Lane left) { this.leftNeighbor = left; this.leftAdjacentLane = left; }
    public Lane getRightNeighbor() { return rightNeighbor; }
    public void setRightNeighbor(Lane right) { this.rightNeighbor = right; this.rightAdjacentLane = right; }

    public Lane getOpposingLane() { return opposingLane; }
    public void setOpposingLane(Lane opposingLane) { this.opposingLane = opposingLane; }
    public Lane getLeftAdjacentLane() { return leftAdjacentLane; }
    public void setLeftAdjacentLane(Lane leftAdjacentLane) { this.leftAdjacentLane = leftAdjacentLane; this.leftNeighbor = leftAdjacentLane; }
    public Lane getRightAdjacentLane() { return rightAdjacentLane; }
    public void setRightAdjacentLane(Lane rightAdjacentLane) { this.rightAdjacentLane = rightAdjacentLane; this.rightNeighbor = rightAdjacentLane; }

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
