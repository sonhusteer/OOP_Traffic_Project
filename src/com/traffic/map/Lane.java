
package com.traffic.map;
 
import com.traffic.core.MathUtils;
import com.traffic.core.Vector2D;
import com.traffic.core.Vehicle;
import java.util.ArrayList;
import java.util.List;
 
/**
 * Một làn đường: danh sách waypoints và đèn kiểm soát.
 * Waypoints: index 0 = điểm đầu, index 1 = vạch dừng, index cuối = điểm cuối.
 */
public class Lane {
 
    private final List<Vector2D> waypoints = new ArrayList<>();
    private final TrafficLight   light;
    private final List<Vehicle>  vehicles  = new ArrayList<>();

    // ── Neighbor Lanes (Láng giềng để chuyển làn) ────────────────────────
    private Lane leftNeighbor;
    private Lane rightNeighbor;

    // Các xe đang có ý định lấn vào làn này (Chống deadlock)
    private final List<Vehicle> reservedBy = new ArrayList<>();
 
    public Lane(double startX, double startY,
                double endX,   double endY,
                TrafficLight light) {
        this.light = light;
        waypoints.add(new Vector2D(startX, startY)); // index 0: điểm đầu
        waypoints.add(new Vector2D(endX, endY));     // index cuối: điểm cuối
    }
 
    // ── Waypoints ────────────────────────────────────────────────────────
 
    /** Chèn waypoint vào trước điểm cuối — gọi theo thứ tự từ đầu đến cuối */
    public void addwaypoint(double x, double y) {
        waypoints.add(waypoints.size() - 1, new Vector2D(x, y));
    }
 
    public List<Vector2D> getwaypoints() { return waypoints; }
    public Vector2D       getStart()     { return waypoints.get(0); }
    public Vector2D       getEnd()       { return waypoints.get(waypoints.size() - 1); }
 
    /**
     * Vạch dừng = waypoint thứ 2 (index 1), được thêm qua addwaypoint().
     * Nếu chưa có waypoint trung gian → fallback về vị trí đèn.
     */
    public Vector2D getStopLine() {
        if (waypoints.size() >= 3) {
            return waypoints.get(1); // index 1 = vạch dừng
        }
        return light != null ? light.getPosition() : getEnd();
    }

    // ── Tiến độ dọc theo lane (polyline) ─────────────────────────────────

    /** Tổng chiều dài lane dọc theo waypoints */
    public double getLength() {
        double total = 0.0;
        for (int i = 0; i < waypoints.size() - 1; i++) {
            total += MathUtils.distance(waypoints.get(i), waypoints.get(i + 1));
        }
        return total;
    }

    /** Tính tiến độ (quãng đường từ đầu lane) của một vị trí pos trên lane */
    public double getProgress(Vector2D pos) {
        double bestProgress = 0.0;
        double bestDistanceSq = Double.MAX_VALUE;
        double accumulated = 0.0;

        for (int i = 0; i < waypoints.size() - 1; i++) {
            Vector2D a = waypoints.get(i);
            Vector2D b = waypoints.get(i + 1);

            double dx = b.getX() - a.getX();
            double dy = b.getY() - a.getY();
            double lenSq = dx * dx + dy * dy;

            if (lenSq < 0.000001) continue;

            double t = ((pos.getX() - a.getX()) * dx + (pos.getY() - a.getY()) * dy) / lenSq;
            t = MathUtils.clamp(t, 0.0, 1.0);

            double px = a.getX() + t * dx;
            double py = a.getY() + t * dy;

            double diffX = pos.getX() - px;
            double diffY = pos.getY() - py;
            double distanceSq = diffX * diffX + diffY * diffY;

            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                bestProgress = accumulated + Math.sqrt(lenSq) * t;
            }
            accumulated += Math.sqrt(lenSq);
        }
        return bestProgress;
    }

    /** Lấy tọa độ điểm tại vị trí progress trên lane */
    public Vector2D getPointAtProgress(double progress) {
        progress = MathUtils.clamp(progress, 0.0, getLength());
        double accumulated = 0.0;

        for (int i = 0; i < waypoints.size() - 1; i++) {
            Vector2D a = waypoints.get(i);
            Vector2D b = waypoints.get(i + 1);
            double segmentLength = MathUtils.distance(a, b);

            if (segmentLength < 0.000001) continue;

            if (accumulated + segmentLength >= progress) {
                double t = (progress - accumulated) / segmentLength;
                return new Vector2D(
                    MathUtils.lerp(a.getX(), b.getX(), t),
                    MathUtils.lerp(a.getY(), b.getY(), t)
                );
            }
            accumulated += segmentLength;
        }
        return getEnd();
    }

    /** Lấy góc (degrees) của lane tại vị trí progress */
    public double getAngleAtProgress(double progress) {
        if (waypoints.size() < 2) return 0.0;
        progress = MathUtils.clamp(progress, 0.0, getLength());
        double accumulated = 0.0;

        for (int i = 0; i < waypoints.size() - 1; i++) {
            Vector2D a = waypoints.get(i);
            Vector2D b = waypoints.get(i + 1);
            double segmentLength = MathUtils.distance(a, b);

            if (segmentLength < 0.000001) continue;

            if (accumulated + segmentLength >= progress) {
                return MathUtils.angleTo(a, b);
            }
            accumulated += segmentLength;
        }
        return MathUtils.angleTo(waypoints.get(waypoints.size() - 2), getEnd());
    }
 
    // ── Xe phía trước ────────────────────────────────────────────────────

    /** Tìm xe gần nhất phía trước, dùng tiến độ dọc theo lane */
    public Vehicle getVehicleAhead(Vehicle me) {
        double myProgress = getProgress(me.getPosition());
        return getVehicleAheadAt(myProgress, me);
    }

    /** Tìm xe gần nhất phía trước tại vị trí progress trên lane */
    public Vehicle getVehicleAheadAt(double fromProgress, Vehicle exclude) {
        Vehicle inFront = null;
        double  minDiff = Double.MAX_VALUE;

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
 
    // ── Getters ──────────────────────────────────────────────────────────
 
    public TrafficLight  getLight()              { return light;       }
    public List<Vehicle> getVehicles()           { return vehicles;    }
    public void          addVehicle(Vehicle v)   { vehicles.add(v);    }
    public void          removeVehicle(Vehicle v){ vehicles.remove(v); }

    // ── Neighbors & Safety ───────────────────────────────────────────────

    public Lane getLeftNeighbor()                { return leftNeighbor;  }
    public void setLeftNeighbor(Lane left)       { this.leftNeighbor = left; }
    public Lane getRightNeighbor()               { return rightNeighbor; }
    public void setRightNeighbor(Lane right)     { this.rightNeighbor = right; }

    public void reserve(Vehicle v)               { reservedBy.add(v);    }
    public void release(Vehicle v)               { reservedBy.remove(v); }

    /** Kiểm tra xem một vị trí pos trên làn này có an toàn để xe lấn vào không */
    public boolean isSafeToEnter(Vector2D pos, double safeGap) {
        for (Vehicle v : vehicles) {
            if (MathUtils.distance(v.getPosition(), pos) < safeGap) return false;
        }
        for (Vehicle v : reservedBy) {
            if (MathUtils.distance(v.getPosition(), pos) < safeGap) return false;
        }
        return true;
    }
}