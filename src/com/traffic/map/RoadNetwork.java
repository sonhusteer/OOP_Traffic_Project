package com.traffic.map;

import com.traffic.core.TrafficEngine;
import java.util.ArrayList;
import java.util.List;

/**
 * RoadNetwork dang ky cac Intersection va TrafficLight vao TrafficEngine.
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

    public List<TrafficLight> getAllLights() {
        List<TrafficLight> all = new ArrayList<>();
        for (Intersection intersection : intersections) {
            for (TrafficLight light : intersection.getAllLights()) {
                if (light != null && !all.contains(light)) {
                    all.add(light);
                }
            }
        }
        return all;
    }

    /** Dang ky moi den/ngatu dung 1 lan. Engine cung co guard de tranh trung. */
    public void registerTo(TrafficEngine engine) {
        for (Intersection intersection : intersections) {
            for (TrafficLight light : intersection.getAllLights()) {
                engine.addTrafficLight(light);
            }
            engine.addIntersection(intersection);
        }
    }
}
