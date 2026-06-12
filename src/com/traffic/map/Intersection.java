package com.traffic.map;

import com.traffic.core.Vector2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Mot nga re gom nhieu Lane. Lane co the co 1 hoac nhieu den.
 */
public class Intersection {

    public enum Type { T_JUNCTION, CROSSROADS, FIVE_WAY }

    private final Type type;
    private final Vector2D center;
    private final List<Lane> lanes = new ArrayList<>();

    public Intersection(Type type, double centerX, double centerY) {
        this.type = type;
        this.center = new Vector2D(centerX, centerY);
    }

    public void addLane(Lane lane) {
        if (lane != null && !lanes.contains(lane)) {
            lanes.add(lane);
        }
    }

    public Type getType() { return type; }
    public Vector2D getCenter() { return center; }
    public List<Lane> getLanes() { return lanes; }

    /** Lay tat ca den trong cac lane, chong trung object den. */
    public List<TrafficLight> getAllLights() {
        List<TrafficLight> result = new ArrayList<>();
        for (Lane lane : lanes) {
            for (TrafficLight light : lane.getLights()) {
                if (light != null && !result.contains(light)) {
                    result.add(light);
                }
            }
        }
        return result;
    }
}
