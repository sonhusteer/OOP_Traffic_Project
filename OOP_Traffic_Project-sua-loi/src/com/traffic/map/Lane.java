
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
     * NormalDriver và AggressiveDriver dùng method này để tính điểm dừng
     * chính xác — thay vì dùng vị trí cột đèn.
     *
     * Nếu chưa có waypoint trung gian → fallback về vị trí đèn.
     */
    public Vector2D getStopLine() {
        if (waypoints.size() >= 3) {
            return waypoints.get(1); // index 1 = vạch dừng
        }
        // fallback: chưa addwaypoint → dùng vị trí đèn
        return light != null ? light.getPosition() : getEnd();
    }
 
    // ── Xe phía trước ────────────────────────────────────────────────────
 
    /** Tính khoảng cách có dấu từ đầu làn dọc theo hướng của làn */
    public double getSignedDistance(Vector2D pos) {
        Vector2D start = getStart();
        Vector2D end = getEnd();
        double lx = end.getX() - start.getX();
        double ly = end.getY() - start.getY();
        double len = Math.sqrt(lx * lx + ly * ly);
        if (len < 1e-5) return 0.0;
        
        double dx = pos.getX() - start.getX();
        double dy = pos.getY() - start.getY();
        return (dx * lx + dy * ly) / len;
    }

    /** Tìm xe gần nhất đang ở PHÍA TRƯỚC một vị trí trong làn này.
     *  Dùng được cho cả xe đang ở làn khác muốn check trước khi lấn vào. */
    public Vehicle getVehicleAhead(Vehicle me) {
        double myDist = getSignedDistance(me.getPosition());
        return getVehicleAheadAt(myDist, me);
    }

    /** Tìm xe gần nhất phía trước tại khoảng cách fromStart trong làn này */
    public Vehicle getVehicleAheadAt(double fromStart, Vehicle exclude) {
        Vehicle inFront = null;
        double  minDist = Double.MAX_VALUE;
        boolean meIsFour = exclude.isFourWheeler();

        // Lane direction unit vector
        Vector2D start = getStart();
        Vector2D end   = getEnd();
        double lx = end.getX() - start.getX();
        double ly = end.getY() - start.getY();
        double len = Math.sqrt(lx * lx + ly * ly);

        for (Vehicle other : vehicles) {
            if (other == exclude) continue;
            if (meIsFour != other.isFourWheeler()) continue;

            double dx = other.getPosition().getX() - exclude.getPosition().getX();
            double dy = other.getPosition().getY() - exclude.getPosition().getY();
            double eucDist = Math.sqrt(dx * dx + dy * dy);

            // Other vehicle must be clearly ahead: dot product > half its own width
            // This prevents vehicles beside or slightly behind from being counted as "ahead"
            if (len > 0.01) {
                double nx = lx / len, ny = ly / len;
                double dot = nx * dx + ny * dy;
                double minAhead = other.getWidth() / 2.0; // must project at least half vehicle width ahead
                if (dot > minAhead && eucDist < minDist) {
                    minDist = eucDist;
                    inFront = other;
                }
            }
        }
        return inFront;
    }
 
    // ── Getters ──────────────────────────────────────────────────────────
 
    public TrafficLight  getLight()              { return light;       }
    public List<Vehicle> getVehicles()           { return vehicles;    }
    public void          addVehicle(Vehicle v)   { vehicles.add(v);    }
    public void          removeVehicle(Vehicle v){ vehicles.remove(v); }
    public void          clear() {
        vehicles.clear();
        reservedBy.clear();
    }

    // ── Neighbors & Safety ───────────────────────────────────────────────

    public Lane getLeftNeighbor()                { return leftNeighbor;  }
    public void setLeftNeighbor(Lane left)       { this.leftNeighbor = left; }
    public Lane getRightNeighbor()               { return rightNeighbor; }
    public void setRightNeighbor(Lane right)     { this.rightNeighbor = right; }

    public void reserve(Vehicle v)               { reservedBy.add(v);    }
    public void release(Vehicle v)               { reservedBy.remove(v); }

    /** Kiểm tra xem một vị trí pos trên làn này có an toàn để xe lấn vào không */
    public boolean isSafeToEnter(Vehicle me, double safeGap) {
        Vector2D pos = me.getPosition();
        // Kiểm tra xe đang thực sự chạy trong làn
        for (Vehicle v : vehicles) {
            if (me.isFourWheeler() != v.isFourWheeler()) continue;
            if (MathUtils.distance(v.getPosition(), pos) < safeGap) return false;
        }
        // Kiểm tra xe từ làn khác đã đặt chỗ lấn vào
        for (Vehicle v : reservedBy) {
            if (me.isFourWheeler() != v.isFourWheeler()) continue;
            if (MathUtils.distance(v.getPosition(), pos) < safeGap) return false;
        }
        return true;
    }
}