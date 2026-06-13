package com.traffic.core;

import com.traffic.map.Intersection;
import com.traffic.map.Lane;

/**
 * Immutable geometry + mutable progress for a vehicle committed to an
 * intersection turn. The vehicle temporarily follows this Bezier path; after
 * completion it is reattached to the target lane by progress + lateralOffset.
 */
public class TurnManeuver {

    private static final int LENGTH_SAMPLES = 18;

    private final Lane sourceLane;
    private final Lane targetLane;
    private final Intersection intersection;
    private final Vehicle.TurnDecision decision;
    private final Vector2D p0;
    private final Vector2D p1;
    private final Vector2D p2;
    private final double targetEntryOffset;
    private final double targetEntryProgress;
    private final double length;
    private double t;

    public TurnManeuver(Lane sourceLane,
                        Lane targetLane,
                        Intersection intersection,
                        Vehicle.TurnDecision decision,
                        Vector2D p0,
                        Vector2D p1,
                        Vector2D p2,
                        double targetEntryOffset,
                        double targetEntryProgress) {
        this.sourceLane = sourceLane;
        this.targetLane = targetLane;
        this.intersection = intersection;
        this.decision = decision == null ? Vehicle.TurnDecision.STRAIGHT : decision;
        this.p0 = copy(p0);
        this.p1 = copy(p1);
        this.p2 = copy(p2);
        this.targetEntryOffset = targetEntryOffset;
        this.targetEntryProgress = targetEntryProgress;
        this.length = Math.max(5.0, sampleLength());
        this.t = 0.0;
    }

    public boolean advance(double speed, double deltaTime) {
        t += Math.max(0.0, speed) * Math.max(0.0, deltaTime) / length;
        if (t >= 1.0) {
            t = 1.0;
            return true;
        }
        return false;
    }

    public Vector2D pointAtCurrentT() { return pointAt(t); }

    public Vector2D tangentAtCurrentT() { return tangentAt(t); }

    public Vector2D pointAt(double value) {
        double clamped = MathUtils.clamp(value, 0.0, 1.0);
        double omt = 1.0 - clamped;
        double x = omt * omt * p0.getX()
                + 2.0 * omt * clamped * p1.getX()
                + clamped * clamped * p2.getX();
        double y = omt * omt * p0.getY()
                + 2.0 * omt * clamped * p1.getY()
                + clamped * clamped * p2.getY();
        return new Vector2D(x, y);
    }

    public Vector2D tangentAt(double value) {
        double clamped = MathUtils.clamp(value, 0.0, 1.0);
        double omt = 1.0 - clamped;
        double dx = 2.0 * omt * (p1.getX() - p0.getX())
                + 2.0 * clamped * (p2.getX() - p1.getX());
        double dy = 2.0 * omt * (p1.getY() - p0.getY())
                + 2.0 * clamped * (p2.getY() - p1.getY());
        double len = Math.hypot(dx, dy);
        if (len < 1e-6) {
            return new Vector2D(1.0, 0.0);
        }
        return new Vector2D(dx / len, dy / len);
    }

    private double sampleLength() {
        double sum = 0.0;
        Vector2D prev = pointAt(0.0);
        for (int i = 1; i <= LENGTH_SAMPLES; i++) {
            double sampleT = (double) i / LENGTH_SAMPLES;
            Vector2D current = pointAt(sampleT);
            sum += MathUtils.distance(prev, current);
            prev = current;
        }
        return sum;
    }

    private static Vector2D copy(Vector2D source) {
        if (source == null) {
            return new Vector2D(0.0, 0.0);
        }
        return new Vector2D(source.getX(), source.getY());
    }

    public Lane getSourceLane() { return sourceLane; }
    public Lane getTargetLane() { return targetLane; }
    public Intersection getIntersection() { return intersection; }
    public Vehicle.TurnDecision getDecision() { return decision; }
    public double getTargetEntryOffset() { return targetEntryOffset; }
    public double getTargetEntryProgress() { return targetEntryProgress; }
    public double getT() { return t; }
    public double getLength() { return length; }
}
