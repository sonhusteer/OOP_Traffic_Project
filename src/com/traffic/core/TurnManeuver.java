package com.traffic.core;

import com.traffic.map.Intersection;
import com.traffic.map.Lane;

/**
 * Geometry + progress for a vehicle committed to an intersection movement.
 *
 * A normal vehicle is still driven by laneProgress + lateralOffset. It only
 * enters this maneuver after TurnCoordinator has decided that it is allowed to
 * enter the intersection. While active, movement follows a cubic Bezier curve
 * and uses approximate arc-length traversal so speed remains visually stable
 * through tight corners.
 */
public class TurnManeuver {

    private static final int LENGTH_SAMPLES = 48;

    private static final double ROUNDABOUT_ROAD_HALF_OFFSET = 40.0;
    private static final double ROUNDABOUT_LANE_RADIUS = 65.0;
    private static final double ROUNDABOUT_RADIAL_JOIN = Math.sqrt(
            ROUNDABOUT_LANE_RADIUS * ROUNDABOUT_LANE_RADIUS
                    - ROUNDABOUT_ROAD_HALF_OFFSET * ROUNDABOUT_ROAD_HALF_OFFSET);


    private final Lane sourceLane;
    private final Lane targetLane;
    private final Intersection intersection;
    private final Vehicle.TurnDecision decision;

    private final Vector2D p0;
    private final Vector2D p1;
    private final Vector2D p2;
    private final Vector2D p3;

    private final double targetEntryOffset;
    private final double targetEntryProgress;
    private final double[] sampleT;
    private final double[] sampleLength;
    private final double length;

    private double traveledDistance;
    private double t;

    @SuppressWarnings("this-escape")
    public TurnManeuver(Lane sourceLane,
                        Lane targetLane,
                        Intersection intersection,
                        Vehicle.TurnDecision decision,
                        Vector2D p0,
                        Vector2D p1,
                        Vector2D p2,
                        Vector2D p3,
                        double targetEntryOffset,
                        double targetEntryProgress) {
        this.sourceLane = sourceLane;
        this.targetLane = targetLane;
        this.intersection = intersection;
        this.decision = decision == null ? Vehicle.TurnDecision.STRAIGHT : decision;
        this.p0 = copy(p0);
        this.p1 = copy(p1);
        this.p2 = copy(p2);
        this.p3 = copy(p3);
        this.targetEntryOffset = targetEntryOffset;
        this.targetEntryProgress = targetEntryProgress;
        this.sampleT = new double[LENGTH_SAMPLES + 1];
        this.sampleLength = new double[LENGTH_SAMPLES + 1];
        this.length = Math.max(5.0, buildArcLengthTable());
        this.traveledDistance = 0.0;
        this.t = 0.0;
    }

    /**
     * Advance by physical distance, not by raw Bezier t. This avoids the common
     * effect where a car accelerates at the start/end of the curve and crawls in
     * the middle even though its displayed speed is constant.
     */
    public boolean advance(double speed, double deltaTime) {
        traveledDistance += Math.max(0.0, speed) * Math.max(0.0, deltaTime);
        if (traveledDistance >= length) {
            traveledDistance = length;
            t = 1.0;
            return true;
        }
        t = tForDistance(traveledDistance);
        return false;
    }

    public Vector2D pointAtCurrentT() { return pointAt(t); }

    public Vector2D tangentAtCurrentT() { return tangentAt(t); }

    public Vector2D pointAt(double value) {
        double u = MathUtils.clamp(value, 0.0, 1.0);
        if (usesRoundaboutPath()) {
            return roundaboutPointAt(u);
        }
        double omt = 1.0 - u;
        double x = omt * omt * omt * p0.getX()
                + 3.0 * omt * omt * u * p1.getX()
                + 3.0 * omt * u * u * p2.getX()
                + u * u * u * p3.getX();
        double y = omt * omt * omt * p0.getY()
                + 3.0 * omt * omt * u * p1.getY()
                + 3.0 * omt * u * u * p2.getY()
                + u * u * u * p3.getY();
        return new Vector2D(x, y);
    }

    public Vector2D tangentAt(double value) {
        double u = MathUtils.clamp(value, 0.0, 1.0);
        if (usesRoundaboutPath()) {
            return roundaboutTangentAt(u);
        }
        double omt = 1.0 - u;
        double dx = 3.0 * omt * omt * (p1.getX() - p0.getX())
                + 6.0 * omt * u * (p2.getX() - p1.getX())
                + 3.0 * u * u * (p3.getX() - p2.getX());
        double dy = 3.0 * omt * omt * (p1.getY() - p0.getY())
                + 6.0 * omt * u * (p2.getY() - p1.getY())
                + 3.0 * u * u * (p3.getY() - p2.getY());
        double len = Math.hypot(dx, dy);
        if (len < 1e-6) {
            return new Vector2D(1.0, 0.0);
        }
        return new Vector2D(dx / len, dy / len);
    }

    private boolean usesRoundaboutPath() {
        return intersection != null
                && intersection.getType() == Intersection.Type.FIVE_WAY
                && sourceLane != null
                && targetLane != null;
    }

    /**
     * Five-way movements follow the circulatory lane around the central island
     * instead of a generic Bezier chord through the center. This keeps vehicles
     * tangent to the roundabout and prevents them from cutting across the island.
     */
    private Vector2D roundaboutPointAt(double u) {
        double cx = intersection.getCenter().getX();
        double cy = intersection.getCenter().getY();

        double theta0 = Math.atan2(p0.getY() - cy, p0.getX() - cx);
        double theta3 = Math.atan2(p3.getY() - cy, p3.getX() - cx);
        double deltaTheta = roundaboutDeltaTheta(theta0, theta3);
        double theta = theta0 + u * deltaTheta;

        double r0 = Math.hypot(p0.getX() - cx, p0.getY() - cy);
        double r3 = Math.hypot(p3.getX() - cx, p3.getY() - cy);
        double baseRadius = MathUtils.lerp(r0, r3, u);

        // Ease onto the actual circulatory-lane radius in the middle and blend
        // back to the exact lane entry/exit points at both ends.
        double r = baseRadius + Math.sin(Math.PI * u) * (ROUNDABOUT_LANE_RADIUS - baseRadius);
        return new Vector2D(cx + r * Math.cos(theta), cy + r * Math.sin(theta));
    }

    private Vector2D roundaboutTangentAt(double u) {
        double eps = 0.006;
        double a = MathUtils.clamp(u - eps, 0.0, 1.0);
        double b = MathUtils.clamp(u + eps, 0.0, 1.0);
        if (Math.abs(b - a) < 1e-6) {
            b = MathUtils.clamp(u + eps * 2.0, 0.0, 1.0);
        }
        Vector2D pA = roundaboutPointAt(a);
        Vector2D pB = roundaboutPointAt(b);
        double dx = pB.getX() - pA.getX();
        double dy = pB.getY() - pA.getY();
        double len = Math.hypot(dx, dy);
        if (len < 1e-6) {
            return new Vector2D(1.0, 0.0);
        }
        return new Vector2D(dx / len, dy / len);
    }

    private double roundaboutDeltaTheta(double theta0, double theta3) {
        // The tangent points are offset from the road radial by this angle.
        // Subtracting it before normalization makes exit selection stable for
        // 5-way spacing, then adding it back keeps the actual endpoints exact.
        double tangentOffset = Math.atan2(ROUNDABOUT_ROAD_HALF_OFFSET, ROUNDABOUT_RADIAL_JOIN);
        double roadDiff = theta3 - theta0 - 2.0 * tangentOffset;
        roadDiff = normalizeAngleRadians(roadDiff);

        // Current map geometry uses one consistent circulatory direction. Force
        // that direction so left/straight/right all travel around the ring rather
        // than taking the short chord across the island.
        if (roadDiff > 0.0) {
            roadDiff -= 2.0 * Math.PI;
        }
        return roadDiff + 2.0 * tangentOffset;
    }

    private static double normalizeAngleRadians(double angle) {
        double result = angle;
        while (result <= -Math.PI) result += 2.0 * Math.PI;
        while (result > Math.PI) result -= 2.0 * Math.PI;
        return result;
    }

    private double buildArcLengthTable() {
        double sum = 0.0;
        Vector2D prev = pointAt(0.0);
        sampleT[0] = 0.0;
        sampleLength[0] = 0.0;
        for (int i = 1; i <= LENGTH_SAMPLES; i++) {
            double currentT = (double) i / LENGTH_SAMPLES;
            Vector2D current = pointAt(currentT);
            sum += MathUtils.distance(prev, current);
            sampleT[i] = currentT;
            sampleLength[i] = sum;
            prev = current;
        }
        return sum;
    }

    private double tForDistance(double distance) {
        double clamped = MathUtils.clamp(distance, 0.0, length);
        for (int i = 1; i < sampleLength.length; i++) {
            if (sampleLength[i] >= clamped) {
                double prevL = sampleLength[i - 1];
                double nextL = sampleLength[i];
                double span = Math.max(1e-6, nextL - prevL);
                double local = (clamped - prevL) / span;
                return MathUtils.lerp(sampleT[i - 1], sampleT[i], local);
            }
        }
        return 1.0;
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
    public double getTraveledDistance() { return traveledDistance; }
    public Vector2D getStartPoint() { return copy(p0); }
    public Vector2D getEndPoint() { return copy(p3); }
}
