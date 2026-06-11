
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
 
    /** Tìm xe gần nhất đang ở PHÍA TRƯỚC một vị trí trong làn này.
     *  Dùng được cho cả xe đang ở làn khác muốn check trước khi lấn vào. */
    public Vehicle getVehicleAhead(Vehicle me) {
        double myDist = MathUtils.distance(getStart(), me.getPosition());
        return getVehicleAheadAt(myDist, me);
    }

    /** Tìm xe gần nhất phía trước tại khoảng cách fromStart trong làn này */
    public Vehicle getVehicleAheadAt(double fromStart, Vehicle exclude) {
        Vehicle inFront = null;
        double  minDiff = Double.MAX_VALUE;

        for (Vehicle other : vehicles) {
            if (other == exclude) continue;
            double otherDist = MathUtils.distance(getStart(), other.getPosition());
            double diff      = otherDist - fromStart;

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
        // Kiểm tra xe đang thực sự chạy trong làn
        for (Vehicle v : vehicles) {
            if (MathUtils.distance(v.getPosition(), pos) < safeGap) return false;
        }
        // Kiểm tra xe từ làn khác đã đặt chỗ lấn vào
        for (Vehicle v : reservedBy) {
            if (MathUtils.distance(v.getPosition(), pos) < safeGap) return false;
        }
        return true;
    }
}