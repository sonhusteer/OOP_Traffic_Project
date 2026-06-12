package com.traffic.map;

import com.traffic.core.TrafficEngine;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Mạng lưới giao thông - quản lý nhiều Intersection.
 */
public class RoadNetwork {

    private final List<Intersection> intersections = new ArrayList<>();

    public void addIntersection(Intersection intersection) {
        if (intersection != null && !intersections.contains(intersection)) {
            intersections.add(intersection);
        }
    }

    public List<Intersection> getIntersections() {
        return intersections;
    }

    /** Lấy tất cả đèn, chống trùng khi một lane/light thuộc nhiều intersection. */
    public List<TrafficLight> getAllLights() {
        Set<TrafficLight> unique = new LinkedHashSet<>();
        for (Intersection intersection : intersections) {
            unique.addAll(intersection.getAllLights());
        }
        return new ArrayList<>(unique);
    }

    public void registerTo(TrafficEngine engine) {
        for (TrafficLight light : getAllLights()) {
            engine.addTrafficLight(light);
        }
        for (Intersection intersection : intersections) {
            engine.addIntersection(intersection);
        }
    }
}
