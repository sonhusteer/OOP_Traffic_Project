package com.traffic.map;

/**
 * A traffic-control point attached to a lane at a precise progress value.
 *
 * This lets a long lane pass through more than one intersection/light without
 * pretending that the whole lane has only one stop line.
 */
public final class LaneControlPoint {

    private final double progress;
    private final TrafficLight light;

    public LaneControlPoint(double progress, TrafficLight light) {
        this.progress = Math.max(0.0, progress);
        this.light = light;
    }

    public double getProgress() {
        return progress;
    }

    public TrafficLight getLight() {
        return light;
    }
}
