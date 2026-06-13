package com.traffic.map;

import com.traffic.core.Vector2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Một ngã rẽ — chứa nhiều Lane và đèn tương ứng.
 *
 * Hỗ trợ ngã 3 (3 làn), ngã 4 (4 làn), ngã 5 (5 làn).
 * Dễ mở rộng: thêm loại ngã rẽ mới chỉ cần thêm Lane.
 */
public class Intersection {

    public enum Type { T_JUNCTION, CROSSROADS, FIVE_WAY }

    private final Type         type;
    private final Vector2D     center;
    private final List<Lane>   lanes = new ArrayList<>();

    public Intersection(Type type, double centerX, double centerY) {
        this.type   = type;
        this.center = new Vector2D(centerX, centerY);
    }

    public void addLane(Lane lane) { lanes.add(lane); }

    public Type         getType()   { return type;   }
    public Vector2D     getCenter() { return center; }
    public List<Lane>   getLanes()  { return lanes;  }

    public void clearLaneReservations() {
        for (Lane lane : lanes) {
            if (lane != null) lane.clearReservations();
        }
    }

    /** Tất cả đèn trong ngã rẽ này */
    public List<TrafficLight> getAllLights() {
        List<TrafficLight> result = new ArrayList<>();
        for (Lane lane : lanes) {
            if (lane != null) result.addAll(lane.getAllTrafficLights());
        }
        return result;
    }
}
