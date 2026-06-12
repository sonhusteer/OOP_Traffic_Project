package com.traffic.map;

import com.traffic.core.MathUtils;
import com.traffic.core.Vector2D;
import java.util.List;

/**
 * Xu ly hinh hoc duong di cua Lane.
 *
 * LanePath coi Lane la mot polyline gom nhieu waypoint. Moi vi tri cua xe
 * tren lane duoc mo ta bang:
 * - progress: quang duong da di doc theo tim lane.
 * - lateralOffset: do lech ngang so voi tim lane.
 *
 * Quy uoc lateralOffset:
 * - 0.0  = nam tren tim lane.
 * - > 0  = lech ve ben phai theo huong chay cua lane.
 * - < 0  = lech ve ben trai theo huong chay cua lane.
 *
 * Lop nay khong copy danh sach waypoint. No giu reference toi list cua Lane
 * de moi lan map them waypoint thi LanePath tu thay doi theo.
 */
public final class LanePath {

    private static final double EPSILON = 0.000001;

    private final List<Vector2D> waypoints;

    public LanePath(List<Vector2D> waypoints) {
        if (waypoints == null) {
            throw new IllegalArgumentException("waypoints cannot be null");
        }
        this.waypoints = waypoints;
    }

    /** Tong chieu dai cua lane tinh theo tat ca segment waypoint. */
    public double length() {
        double total = 0.0;
        for (int i = 0; i < waypoints.size() - 1; i++) {
            Vector2D a = waypoints.get(i);
            Vector2D b = waypoints.get(i + 1);
            total += MathUtils.distance(a, b);
        }
        return total;
    }

    /**
     * Tim progress gan nhat cua mot vi tri man hinh tren polyline.
     *
     * Method nay duyet qua tung segment, chieu diem position len segment do,
     * lay diem co khoang cach gan nhat va tra ve progress tu dau lane den diem
     * chieu gan nhat.
     */
    public double progressOf(Vector2D position) {
        if (position == null || waypoints.size() < 2) {
            return 0.0;
        }

        double bestProgress = 0.0;
        double bestDistanceSq = Double.MAX_VALUE;
        double accumulated = 0.0;

        for (int i = 0; i < waypoints.size() - 1; i++) {
            Vector2D a = waypoints.get(i);
            Vector2D b = waypoints.get(i + 1);

            double dx = b.getX() - a.getX();
            double dy = b.getY() - a.getY();
            double lenSq = dx * dx + dy * dy;
            if (lenSq < EPSILON) {
                continue;
            }

            double t = ((position.getX() - a.getX()) * dx
                    + (position.getY() - a.getY()) * dy) / lenSq;
            t = MathUtils.clamp(t, 0.0, 1.0);

            double px = a.getX() + t * dx;
            double py = a.getY() + t * dy;

            double diffX = position.getX() - px;
            double diffY = position.getY() - py;
            double distanceSq = diffX * diffX + diffY * diffY;

            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                bestProgress = accumulated + Math.sqrt(lenSq) * t;
            }

            accumulated += Math.sqrt(lenSq);
        }

        return bestProgress;
    }

    /**
     * Lay diem tren tim lane tai progress.
     *
     * Khac voi ban cu trong Lane, method nay cho phep progress am hoac lon hon
     * chieu dai lane. Khi do diem duoc noi dai theo segment dau/cuoi. Dieu nay
     * giup spawn nhieu xe phia truoc dau lane ma khong de len nhau.
     */
    public Vector2D centerAt(double progress) {
        if (waypoints.isEmpty()) {
            return new Vector2D(0.0, 0.0);
        }
        if (waypoints.size() == 1) {
            Vector2D only = waypoints.get(0);
            return new Vector2D(only.getX(), only.getY());
        }

        Segment first = firstValidSegment();
        if (first == null) {
            Vector2D p = waypoints.get(0);
            return new Vector2D(p.getX(), p.getY());
        }

        if (progress <= 0.0) {
            return extrapolate(first.a, first.b, progress);
        }

        double accumulated = 0.0;
        Segment lastValid = first;

        for (int i = 0; i < waypoints.size() - 1; i++) {
            Vector2D a = waypoints.get(i);
            Vector2D b = waypoints.get(i + 1);
            double segmentLength = MathUtils.distance(a, b);
            if (segmentLength < EPSILON) {
                continue;
            }

            lastValid = new Segment(a, b, segmentLength);
            if (accumulated + segmentLength >= progress) {
                double t = (progress - accumulated) / segmentLength;
                return new Vector2D(
                        MathUtils.lerp(a.getX(), b.getX(), t),
                        MathUtils.lerp(a.getY(), b.getY(), t)
                );
            }
            accumulated += segmentLength;
        }

        double extra = progress - accumulated;
        return extrapolate(lastValid.a, lastValid.b, lastValid.length + extra);
    }

    /** Lay goc xe tai progress, tinh theo segment ma xe dang di tren do. */
    public double angleAt(double progress) {
        Segment segment = segmentAt(progress);
        if (segment == null) {
            return 0.0;
        }
        return MathUtils.angleTo(segment.a, segment.b);
    }

    /**
     * Lay vi tri that cua xe tai progress + lateralOffset.
     *
     * positive lateralOffset = ben phai theo huong chay.
     */
    public Vector2D pointAt(double progress, double lateralOffset) {
        Vector2D center = centerAt(progress);
        double angle = Math.toRadians(angleAt(progress));

        // Tangent = (cos, sin). Right-normal tren he toa do man hinh Y-down la
        // (-sin, cos). Vi du xe di sang phai thi offset duong se lech xuong duoi.
        double rightNormalX = -Math.sin(angle);
        double rightNormalY = Math.cos(angle);

        return new Vector2D(
                center.getX() + rightNormalX * lateralOffset,
                center.getY() + rightNormalY * lateralOffset
        );
    }

    private Segment segmentAt(double progress) {
        if (waypoints.size() < 2) {
            return null;
        }

        if (progress <= 0.0) {
            return firstValidSegment();
        }

        double accumulated = 0.0;
        Segment lastValid = null;

        for (int i = 0; i < waypoints.size() - 1; i++) {
            Vector2D a = waypoints.get(i);
            Vector2D b = waypoints.get(i + 1);
            double segmentLength = MathUtils.distance(a, b);
            if (segmentLength < EPSILON) {
                continue;
            }

            Segment current = new Segment(a, b, segmentLength);
            lastValid = current;
            if (accumulated + segmentLength >= progress) {
                return current;
            }
            accumulated += segmentLength;
        }

        return lastValid != null ? lastValid : firstValidSegment();
    }

    private Segment firstValidSegment() {
        for (int i = 0; i < waypoints.size() - 1; i++) {
            Vector2D a = waypoints.get(i);
            Vector2D b = waypoints.get(i + 1);
            double segmentLength = MathUtils.distance(a, b);
            if (segmentLength >= EPSILON) {
                return new Segment(a, b, segmentLength);
            }
        }
        return null;
    }

    /**
     * Noi dai theo vector tu a den b.
     * distanceFromA co the am de lay diem nam truoc diem a.
     */
    private Vector2D extrapolate(Vector2D a, Vector2D b, double distanceFromA) {
        double dx = b.getX() - a.getX();
        double dy = b.getY() - a.getY();
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < EPSILON) {
            return new Vector2D(a.getX(), a.getY());
        }

        double ux = dx / len;
        double uy = dy / len;
        return new Vector2D(
                a.getX() + ux * distanceFromA,
                a.getY() + uy * distanceFromA
        );
    }

    private static final class Segment {
        private final Vector2D a;
        private final Vector2D b;
        private final double length;

        private Segment(Vector2D a, Vector2D b, double length) {
            this.a = a;
            this.b = b;
            this.length = length;
        }
    }
}
