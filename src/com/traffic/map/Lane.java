package com.traffic.map;

import com.traffic.core.MathUtils;
import com.traffic.core.Vector2D;
import com.traffic.core.Vehicle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mot lane gom polyline waypoints, xe dang nam tren lane va cac diem den.
 *
 * Backward compatibility:
 * - addwaypoint() van duoc giu de cac map cu khong phai sua het.
 * - Neu lane co light va chua khai bao control point ro rang, waypoint dau
 *   tien duoc xem la vach dung cua light cu.
 */
public class Lane {

    /** Mot diem dieu khien giao thong: vach dung + den tuong ung. */
    public static final class TrafficControlPoint {
        private final Vector2D stopLine;
        private final TrafficLight light;

        private TrafficControlPoint(Vector2D stopLine, TrafficLight light) {
            this.stopLine = stopLine;
            this.light = light;
        }

        public Vector2D getStopLine() { return stopLine; }
        public TrafficLight getLight() { return light; }
    }

    private static final double POINT_EPSILON = 0.0001;
    private static final double PASSED_STOP_TOLERANCE = 8.0;

    private final List<Vector2D> waypoints = new ArrayList<>();

    // Tang 1 clean-code: tach hinh hoc duong di sang LanePath.
    // LanePath giu reference toi waypoints, nen khi map add waypoint thi path tu cap nhat.
    private final LanePath path = new LanePath(waypoints);

    // Lane bay gio duoc xem la mot dai duong rong, khong chi la mot duong tam mong.
    // Cac field nay se duoc Vehicle/LaneOccupancy dung manh hon o cac tang sau.
    private double width = 80.0;
    private int trackCount = 2;

    private final TrafficLight light;
    private final List<TrafficControlPoint> trafficControls = new ArrayList<>();
    private boolean explicitTrafficControls = false;

    private final List<Vehicle> vehicles = new ArrayList<>();

    private Lane leftNeighbor;
    private Lane rightNeighbor;

    // Xe dang dat cho de chuyen vao lane nay.
    private final List<Vehicle> reservedBy = new ArrayList<>();

    public Lane(double startX, double startY,
                double endX,   double endY,
                TrafficLight light) {
        this.light = light;
        waypoints.add(new Vector2D(startX, startY));
        waypoints.add(new Vector2D(endX, endY));
    }

    // ------------------------------------------------------------------
    // Waypoints va traffic controls.
    // ------------------------------------------------------------------

    /** Ten dung chuan Java. Nen dung method nay cho code moi. */
    public void addWaypoint(double x, double y) {
        Vector2D point = insertWaypointBeforeEnd(x, y);

        // Tuong thich code cu: waypoint dau tien la vach dung cho light chinh.
        if (!explicitTrafficControls && light != null && trafficControls.isEmpty()) {
            trafficControls.add(new TrafficControlPoint(point, light));
        }
    }

    /** Ten cu trong project. Giu lai de khong phai sua toan bo map. */
    public void addwaypoint(double x, double y) {
        addWaypoint(x, y);
    }

    /** Ten getter moi, ro nghia hon. */
    public List<Vector2D> getWaypoints() {
        return waypoints;
    }

    /** Ten getter cu, giu de renderer cu van compile. */
    public List<Vector2D> getwaypoints() {
        return waypoints;
    }

    public Vector2D getStart() {
        return waypoints.get(0);
    }

    public Vector2D getEnd() {
        return waypoints.get(waypoints.size() - 1);
    }

    // ------------------------------------------------------------------
    // Lane geometry.
    // ------------------------------------------------------------------

    /**
     * Duong hinh hoc cua lane. Code moi nen dung lane.path() de tinh
     * progress, goc, diem trung tam va diem lech ngang.
     */
    public LanePath path() {
        return path;
    }

    /** Chieu rong vat ly cua lane tinh bang pixel trong simulation. */
    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        if (width <= 0.0) {
            throw new IllegalArgumentException("Lane width must be positive");
        }
        this.width = width;
    }

    /** So vet ngang co the spawn/chay song song trong cung lane. */
    public int getTrackCount() {
        return trackCount;
    }

    public void setTrackCount(int trackCount) {
        if (trackCount <= 0) {
            throw new IllegalArgumentException("trackCount must be positive");
        }
        this.trackCount = trackCount;
    }

    /**
     * Tinh offset ngang cho trackIndex.
     *
     * Voi width = 80 va trackCount = 2, hai track se nam xap xi:
     * - track 0: -17px, ben trai tim lane.
     * - track 1: +17px, ben phai tim lane.
     */
    public double getTrackOffset(int trackIndex) {
        if (trackCount <= 1) {
            return 0.0;
        }

        trackIndex = (int) MathUtils.clamp(trackIndex, 0, trackCount - 1);

        double edgeMargin = 6.0;
        double usableWidth = Math.max(0.0, width - 2.0 * edgeMargin);
        double cellWidth = usableWidth / trackCount;
        return -usableWidth / 2.0 + cellWidth * (trackIndex + 0.5);
    }

    /** Offset trai nhat ma xe van con nam trong lane. */
    public double getLeftmostOffset(Vehicle v) {
        double vehicleHalfWidth = v != null ? v.getHeight() / 2.0 : 0.0;
        return -width / 2.0 + 6.0 + vehicleHalfWidth;
    }

    /** Offset phai nhat ma xe van con nam trong lane. */
    public double getRightmostOffset(Vehicle v) {
        double vehicleHalfWidth = v != null ? v.getHeight() / 2.0 : 0.0;
        return width / 2.0 - 6.0 - vehicleHalfWidth;
    }

    /** Gioi han offset ngang de than xe khong vuot ra ngoai lane. */
    public double clampOffset(Vehicle v, double offset) {
        return MathUtils.clamp(offset, getLeftmostOffset(v), getRightmostOffset(v));
    }

    /**
     * Khai bao ro mot den va vach dung tren lane.
     * Can cho NetworkMap vi road1/road2 di qua 2 nga tu, moi nga tu co 1 den.
     */
    public void addTrafficControlPoint(double x, double y, TrafficLight light) {
        if (light == null) return;

        // Khi dung explicit controls, bo implicit control sinh tu addWaypoint().
        if (!explicitTrafficControls) {
            trafficControls.clear();
            explicitTrafficControls = true;
        }

        Vector2D stopLine = findOrInsertWaypoint(x, y);
        if (!containsControl(stopLine, light)) {
            trafficControls.add(new TrafficControlPoint(stopLine, light));
        }
    }

    public List<TrafficControlPoint> getTrafficControls() {
        return Collections.unmodifiableList(trafficControls);
    }

    /** Lay tat ca den cua lane, co the nhieu hon 1 den. */
    public List<TrafficLight> getLights() {
        List<TrafficLight> result = new ArrayList<>();
        for (TrafficControlPoint control : trafficControls) {
            TrafficLight controlLight = control.getLight();
            if (controlLight != null && !result.contains(controlLight)) {
                result.add(controlLight);
            }
        }
        if (result.isEmpty() && light != null) {
            result.add(light);
        }
        return result;
    }

    /** Alias ro nghia hon cho Intersection/RoadNetwork khi gom den. */
    public List<TrafficLight> getAllTrafficLights() {
        return getLights();
    }

    /**
     * API cu: tra ve den dau tien cua lane. Driver moi nen dung
     * getNextTrafficControl() de chon den sap toi.
     */
    public TrafficLight getLight() {
        if (!trafficControls.isEmpty()) {
            return trafficControls.get(0).getLight();
        }
        return light;
    }

    /** API cu: tra ve vach dung dau tien. */
    public Vector2D getStopLine() {
        if (!trafficControls.isEmpty()) {
            return trafficControls.get(0).getStopLine();
        }
        if (waypoints.size() >= 3) {
            return waypoints.get(1);
        }
        return light != null ? light.getPosition() : getEnd();
    }

    /**
     * Tim vach dung gan mot diem, dung cho renderer khi 1 lane thuoc nhieu
     * Intersection. Vi du NetworkMap: road1 co vach dung o ca nga tu trai
     * va nga tu phai.
     */
    public Vector2D getStopLineNear(Vector2D point) {
        if (point == null || trafficControls.isEmpty()) {
            return getStopLine();
        }

        TrafficControlPoint best = null;
        double bestDistance = Double.MAX_VALUE;
        for (TrafficControlPoint control : trafficControls) {
            double distance = MathUtils.distance(point, control.getStopLine());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = control;
            }
        }
        return best != null ? best.getStopLine() : getStopLine();
    }

    /**
     * Tim den/vach dung tiep theo theo progress hien tai cua xe.
     * Neu xe da qua den thu nhat tren NetworkMap, method se tra ve den thu hai.
     */
    public TrafficControlPoint getNextTrafficControl(Vector2D pos) {
        if (trafficControls.isEmpty()) return null;

        double myProgress = getProgress(pos);
        TrafficControlPoint best = null;
        double bestProgress = Double.MAX_VALUE;

        for (TrafficControlPoint control : trafficControls) {
            double controlProgress = getProgress(control.getStopLine());

            // Cho dung sai nho de xe dung ngay vach van con nhin thay den hien tai.
            if (controlProgress + PASSED_STOP_TOLERANCE >= myProgress
                    && controlProgress < bestProgress) {
                bestProgress = controlProgress;
                best = control;
            }
        }

        return best;
    }

    private Vector2D insertWaypointBeforeEnd(double x, double y) {
        Vector2D point = new Vector2D(x, y);
        waypoints.add(waypoints.size() - 1, point);
        return point;
    }

    private Vector2D findOrInsertWaypoint(double x, double y) {
        Vector2D target = new Vector2D(x, y);
        for (Vector2D point : waypoints) {
            if (MathUtils.distance(point, target) <= POINT_EPSILON) {
                return point;
            }
        }
        return insertWaypointBeforeEnd(x, y);
    }

    private boolean containsControl(Vector2D stopLine, TrafficLight controlLight) {
        for (TrafficControlPoint control : trafficControls) {
            if (control.getLight() == controlLight
                    && MathUtils.distance(control.getStopLine(), stopLine) <= POINT_EPSILON) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Progress tren polyline.
    // ------------------------------------------------------------------

    public double getLength() {
        return path.length();
    }

    public double getProgress(Vector2D pos) {
        return path.progressOf(pos);
    }

    public Vector2D getPointAtProgress(double progress) {
        return path.centerAt(progress);
    }

    public Vector2D getPointAtProgress(double progress, double lateralOffset) {
        return path.pointAt(progress, lateralOffset);
    }

    public double getAngleAtProgress(double progress) {
        return path.angleAt(progress);
    }

    // ------------------------------------------------------------------
    // Tim xe phia truoc.
    // ------------------------------------------------------------------

    public Vehicle getVehicleAhead(Vehicle me) {
        double myProgress = getProgress(me.getPosition());
        return getVehicleAheadAt(myProgress, me);
    }

    public Vehicle getVehicleAheadAt(double fromProgress, Vehicle exclude) {
        Vehicle inFront = null;
        double minDiff = Double.MAX_VALUE;

        for (Vehicle other : vehicles) {
            if (other == exclude) continue;
            double otherProgress = getProgress(other.getPosition());
            double diff = otherProgress - fromProgress;

            if (diff > 0 && diff < minDiff) {
                minDiff = diff;
                inFront = other;
            }
        }
        return inFront;
    }

    // ------------------------------------------------------------------
    // Vehicles, neighbors va safety.
    // ------------------------------------------------------------------

    public List<Vehicle> getVehicles() {
        return vehicles;
    }

    public void addVehicle(Vehicle v) {
        if (v != null && !vehicles.contains(v)) {
            vehicles.add(v);
        }
    }

    public void removeVehicle(Vehicle v) {
        vehicles.remove(v);
        reservedBy.remove(v);
    }

    public Lane getLeftNeighbor() {
        return leftNeighbor;
    }

    public void setLeftNeighbor(Lane left) {
        this.leftNeighbor = left;
    }

    public Lane getRightNeighbor() {
        return rightNeighbor;
    }

    public void setRightNeighbor(Lane right) {
        this.rightNeighbor = right;
    }

    public void reserve(Vehicle v) {
        if (v != null && !reservedBy.contains(v)) {
            reservedBy.add(v);
        }
    }

    public void release(Vehicle v) {
        reservedBy.remove(v);
    }

    /**
     * Kiem tra an toan de nhap lane theo progress, tot hon so voi chi do
     * khoang cach Euclid tren cac lane gap khuc.
     */
    public boolean isSafeToEnter(Vector2D pos, double safeGap) {
        double posProgress = getProgress(pos);

        for (Vehicle v : vehicles) {
            if (Math.abs(getProgress(v.getPosition()) - posProgress) < safeGap) {
                return false;
            }
        }
        for (Vehicle v : reservedBy) {
            if (Math.abs(getProgress(v.getPosition()) - posProgress) < safeGap) {
                return false;
            }
        }
        return true;
    }
}
