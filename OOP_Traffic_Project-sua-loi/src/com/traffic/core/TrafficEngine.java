package com.traffic.core;

import com.traffic.map.Intersection;
import com.traffic.map.TrafficLight;
import com.traffic.map.Lane;
import java.util.ArrayList;
import java.util.List;

public class TrafficEngine {

    private final List<Vehicle> vehicles = new ArrayList<>();
    private final List<TrafficLight> lights = new ArrayList<>();
    private final List<Intersection> intersections = new ArrayList<>();
    private IRenderer renderer;

    // ── Ngưỡng khoảng cách phát hiện ─────────────────────────────────────

    /** Xe ưu tiên cách xe thường < 150px trong cùng làn → nhường */
    private static final double SAME_LANE_YIELD_DIST = 150.0;

    /** Xe ưu tiên hoặc xe thường cách tâm ngã tư < 200px → vùng nguy hiểm */
    private static final double INTERSECTION_DANGER = 200.0;

    public TrafficEngine(IRenderer renderer) {
        this.renderer = renderer;
    }

    public void addVehicle(Vehicle v) {
        vehicles.add(v);
    }

    public void addTrafficLight(TrafficLight l) {
        if (!lights.contains(l)) {
            lights.add(l);
        }
    }

    public void addIntersection(Intersection i) {
        intersections.add(i);
    }

    public void removeVehicle(Vehicle v) {
        vehicles.remove(v);
    }

    public void clearVehicles() {
        for (Vehicle v : vehicles) {
            if (v.getLane() != null) {
                v.getLane().removeVehicle(v);
            }
        }
        vehicles.clear();
        for (Intersection inter : intersections) {
            for (Lane lane : inter.getLanes()) {
                lane.clear();
            }
        }
    }

    public void setRenderer(IRenderer renderer) {
        this.renderer = renderer;
    }

    public void tick(double deltaTime) {
        updateLights(deltaTime);
        detectEmergencyProximity(); // ← phát hiện trước khi xe ra quyết định

        for (Vehicle v : vehicles) {
            if (!v.isTurning() && v.getLane() != null) {
                checkAndStartTurn(v);
            }
        }

        updateVehicles(deltaTime);
    }

    public void render() {
        if (renderer == null)
            return;
        renderer.clear();
        renderer.renderIntersections(intersections);
        renderer.renderLights(lights);
        renderer.renderVehicles(vehicles);
    }

    // ── Cập nhật đèn ─────────────────────────────────────────────────────

    private void updateLights(double deltaTime) {
        for (TrafficLight light : lights) {
            light.tick(deltaTime);
        }
    }

    // ── Cập nhật xe ──────────────────────────────────────────────────────

    private void updateVehicles(double deltaTime) {
        List<Vehicle> toRemove = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            TrafficLight targetLight = null;
            if (vehicle.getLane() != null) {
                targetLight = vehicle.getLane().getLight();
            }
            vehicle.makeDecision(targetLight);

            // Turn yielding logic: left-turning vehicles (or those planning to turn left)
            // must yield to straight-going vehicles and opposing left-turn vehicles with higher progress at intersections
            boolean mustYieldAtIntersection = false;
            if (vehicle.getTurnDecision() == Vehicle.TurnDecision.LEFT) {
                // Find current intersection
                Intersection intersection = vehicle.getLastIntersectionTurned();
                if (intersection == null && vehicle.getLane() != null) {
                    for (Intersection inter : intersections) {
                        if (inter.getLanes().contains(vehicle.getLane())) {
                            double distToCenter = MathUtils.distance(vehicle.getPosition(), inter.getCenter());
                            if (distToCenter < 110.0) { // approaching area
                                intersection = inter;
                                break;
                            }
                        }
                    }
                }

                if (intersection != null) {
                    for (Vehicle other : vehicles) {
                        if (other == vehicle)
                            continue;
                        
                        boolean inSameIntersection = false;
                        if (other.getLane() != null && intersection.getLanes().contains(other.getLane())) {
                            inSameIntersection = true;
                        }
                        if (other.getOriginalLane() != null
                                && intersection.getLanes().contains(other.getOriginalLane())) {
                            inSameIntersection = true;
                        }

                        if (inSameIntersection) {
                            double dist = MathUtils.distance(vehicle.getPosition(), other.getPosition());
                            if (other.getTurnDecision() == Vehicle.TurnDecision.STRAIGHT) {
                                double distOtherToCenter = MathUtils.distance(other.getPosition(),
                                        intersection.getCenter());

                                // Yield if the straight-going vehicle is close to the intersection and is
                                // moving or past its stop line
                                if (distOtherToCenter < 120.0 && dist < 120.0 && (other.getSpeed() > 2.0 || isVehiclePastStopLine(other))) {
                                    mustYieldAtIntersection = true;
                                    break;
                                }
                            } else if (other.getTurnDecision() == Vehicle.TurnDecision.LEFT) {
                                // Yield if the opposing left-turning vehicle is turning and has progressed further
                                if (dist < 120.0 && other.isTurning() && other.getTurnT() > vehicle.getTurnT()) {
                                    mustYieldAtIntersection = true;
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            if (mustYieldAtIntersection) {
                vehicle.setSpeed(0.0);
            }

            // Anti-overlap Proximity Safety Check — smooth repulsion instead of hard freeze
            for (Vehicle other : vehicles) {
                if (other == vehicle)
                    continue;

                // Skip cross-category check when driving parallel (same lane or angle diff <
                // 30°)
                // Do NOT skip if either vehicle is turning or changing lanes (maneuvering in
                // intersections/lane changes)
                if (vehicle.isFourWheeler() != other.isFourWheeler()) {
                    if (!vehicle.isTurning() && !other.isTurning() && !vehicle.isChangingLane()
                            && !other.isChangingLane()) {
                        boolean sameLane = (vehicle.getLane() != null && vehicle.getLane() == other.getLane());
                        double angleDiff = Math.abs(vehicle.getAngle() - other.getAngle());
                        while (angleDiff > 180)
                            angleDiff = 360 - angleDiff;
                        if (sameLane || angleDiff < 30.0)
                            continue;
                    }
                }

                double safetyDist = (vehicle.getWidth() + other.getWidth()) / 2.0 + 4.0;
                double dist = MathUtils.distance(vehicle.getPosition(), other.getPosition());
                if (dist < safetyDist && dist > 0.01) {
                    double headingRad = Math.toRadians(vehicle.getAngle());
                    double dx = other.getPosition().getX() - vehicle.getPosition().getX();
                    double dy = other.getPosition().getY() - vehicle.getPosition().getY();
                    double dot = dx * Math.cos(headingRad) + dy * Math.sin(headingRad);

                    if (dot > 0) { // other is ahead
                        double angleToOther = Math.toDegrees(Math.atan2(dy, dx));
                        double diff = angleToOther - vehicle.getAngle();
                        while (diff < -180)
                            diff += 360;
                        while (diff > 180)
                            diff -= 360;

                        if (Math.abs(diff) < 50.0) {
                            // Scale speed down proportionally to overlap depth
                            double overlap = safetyDist - dist;
                            double penalty = Math.min(1.0, overlap / safetyDist);
                            double newSpeed = vehicle.getSpeed() * (1.0 - penalty);
                            vehicle.setSpeed(newSpeed);

                            // Physical push-apart: move vehicle backwards by overlap/2
                            double pushDist = overlap / 2.0;
                            vehicle.pushBack(pushDist);
                            break;
                        }
                    }
                }
            }

            vehicle.update(deltaTime);

            // Kiểm tra ra khỏi bản đồ (Màn hình 800x600, xóa khi ra quá xa)
            double x = vehicle.getPosition().getX();
            double y = vehicle.getPosition().getY();
            if (x < -80 || x > 880 || y < -80 || y > 680) {
                toRemove.add(vehicle);
            }
        }

        for (Vehicle v : toRemove) {
            vehicles.remove(v);
            if (v.getLane() != null) {
                v.getLane().removeVehicle(v);
            }
        }
    }

    // ── Phát hiện xe ưu tiên và đánh dấu nhường đường ───────────────────

    /**
     * Chạy mỗi tick, xử lý 2 tình huống:
     *
     * Tình huống 1 — Cùng làn:
     * Xe ưu tiên đang ở phía sau xe thường trong cùng làn,
     * khoảng cách < SAME_LANE_YIELD_DIST → xe thường nhường (dừng hẳn).
     *
     * Tình huống 2 — Xung đột ngã tư:
     * Xe ưu tiên đang tiến vào ngã tư (dist đến tâm < INTERSECTION_DANGER),
     * xe thường ở làn khác cùng ngã tư cũng trong vùng nguy hiểm
     * → xe thường dừng hẳn dù đèn xanh.
     */
    private void detectEmergencyProximity() {
        // Reset toàn bộ về NONE — chỉ set lại nếu vẫn còn nguy hiểm
        for (Vehicle v : vehicles) {
            if (!v.isPriority())
                v.setYieldMode(Vehicle.YieldMode.NONE);
        }

        for (Vehicle priority : vehicles) {
            if (!priority.isPriority())
                continue;

            // ── Tình huống 1: Cùng làn ───────────────────────────────────
            if (priority.getLane() != null) {
                double prioFromStart = priority.getLane().getSignedDistance(priority.getPosition());

                for (Vehicle normal : vehicles) {
                    if (normal.isPriority())
                        continue;
                    if (normal.getLane() != priority.getLane())
                        continue;
                    if (normal.isFourWheeler() != priority.isFourWheeler())
                        continue;
                    // Xe đang rẽ hoặc đổi làn không thể dừng — xe ưu tiên phải nhường
                    if (normal.isTurning() || normal.isChangingLane())
                        continue;

                    double normalFromStart = normal.getLane().getSignedDistance(normal.getPosition());
                    double distBetween = normalFromStart - prioFromStart;

                    // Xe ưu tiên ở phía sau (gần start hơn) và đủ gần
                    // → RUSH: tăng tốc tạo hiệu ứng căng thẳng
                    if (prioFromStart < normalFromStart
                            && distBetween < SAME_LANE_YIELD_DIST) {
                        normal.setYieldMode(Vehicle.YieldMode.RUSH);
                    }
                }
            }

            // ── Tình huống 2: Xung đột ngã tư ────────────────────────────
            for (Intersection intersection : intersections) {
                double distPrioToCenter = MathUtils.distance(
                        priority.getPosition(), intersection.getCenter());

                // Xe ưu tiên đang trong vùng nguy hiểm của ngã tư này
                if (distPrioToCenter < INTERSECTION_DANGER) {
                    for (Vehicle normal : vehicles) {
                        if (normal.isPriority())
                            continue;

                        // Bỏ qua nếu cùng làn (đã xử lý ở tình huống 1)
                        if (normal.getLane() == priority.getLane())
                            continue;

                        // Xe thường phải thuộc cùng ngã tư này
                        if (!intersection.getLanes().contains(normal.getLane()))
                            continue;

                        // Xe đang rẽ hoặc đổi làn đã cam kết với hướng đi — không ép dừng
                        // Xe ưu tiên sẽ phải chạy chậm lại và nhường xe này
                        if (normal.isTurning() || normal.isChangingLane())
                            continue;

                        double distNormalToCenter = MathUtils.distance(
                                normal.getPosition(), intersection.getCenter());

                        // Xe thường cũng đang trong vùng nguy hiểm
                        // → STOP: dừng hẳn dù đèn xanh
                        if (distNormalToCenter < INTERSECTION_DANGER) {
                            normal.setYieldMode(Vehicle.YieldMode.STOP);
                        }
                    }
                }
            }
        }
    }

    private void checkAndStartTurn(Vehicle vehicle) {
        Intersection approachingIntersection = null;
        double triggerDist = (vehicle.getTurnDecision() == Vehicle.TurnDecision.RIGHT) ? 90.0 : 60.0;
        for (Intersection intersection : intersections) {
            if (intersection.getLanes().contains(vehicle.getLane())) {
                double distToCenter = MathUtils.distance(vehicle.getPosition(), intersection.getCenter());
                if (distToCenter < triggerDist) {
                    approachingIntersection = intersection;
                    break;
                }
            }
        }

        if (vehicle.getLastIntersectionTurned() != null) {
            double distToLast = MathUtils.distance(vehicle.getPosition(),
                    vehicle.getLastIntersectionTurned().getCenter());
            if (distToLast > 110.0) {
                vehicle.setLastIntersectionTurned(null);
            }
        }

        if (approachingIntersection == null || vehicle.getLastIntersectionTurned() == approachingIntersection) {
            return;
        }

        Lane targetLane = findTargetLane(vehicle.getLane(), vehicle.getTurnDecision(), approachingIntersection);

        if (targetLane == null) {
            for (Vehicle.TurnDecision fallbackDecision : Vehicle.TurnDecision.values()) {
                targetLane = findTargetLane(vehicle.getLane(), fallbackDecision, approachingIntersection);
                if (targetLane != null) {
                    vehicle.setTurnDecision(fallbackDecision);
                    break;
                }
            }
        }

        if (targetLane == null) {
            vehicle.setLastIntersectionTurned(approachingIntersection);
            return;
        }

        if (targetLane == vehicle.getLane()) {
            vehicle.setLastIntersectionTurned(approachingIntersection);
            return;
        }

        startVehicleTurn(vehicle, targetLane, approachingIntersection);
    }

    private Lane findTargetLane(Lane currentLane, Vehicle.TurnDecision decision, Intersection intersection) {
        if (decision == Vehicle.TurnDecision.STRAIGHT) {
            // Verify lane actually extends meaningfully past the intersection center.
            // If intersection is near the lane END (last 25%), there is no straight path —
            // e.g. road3↓ in T-junction ends exactly at the intersection.
            double totalLen = MathUtils.distance(currentLane.getStart(), currentLane.getEnd());
            double distToEnd = MathUtils.distance(intersection.getCenter(), currentLane.getEnd());
            if (totalLen > 1.0 && distToEnd < totalLen * 0.25) {
                return null; // Lane ends at intersection — must turn
            }
            return currentLane;
        }

        double currentAngle = MathUtils.angleTo(currentLane.getStart(), currentLane.getEnd());
        Lane bestLane = null;
        double bestDiff = Double.MAX_VALUE;

        for (Lane otherLane : intersection.getLanes()) {
            if (otherLane == currentLane)
                continue;

            if (MathUtils.distance(otherLane.getStart(), otherLane.getEnd()) < 5)
                continue;

            double distToStart = MathUtils.distance(intersection.getCenter(), otherLane.getStart());
            double distToEnd = MathUtils.distance(intersection.getCenter(), otherLane.getEnd());
            if (distToStart > distToEnd + 10) {
                continue;
            }

            double otherAngle = MathUtils.angleTo(otherLane.getStart(), otherLane.getEnd());
            double angleDiff = otherAngle - currentAngle;
            while (angleDiff < -180)
                angleDiff += 360;
            while (angleDiff > 180)
                angleDiff -= 360;

            if (decision == Vehicle.TurnDecision.LEFT) {
                double dev = Math.abs(angleDiff - (-90));
                if (dev < 45 && dev < bestDiff) {
                    bestDiff = dev;
                    bestLane = otherLane;
                }
            } else if (decision == Vehicle.TurnDecision.RIGHT) {
                double dev = Math.abs(angleDiff - 90);
                if (dev < 45 && dev < bestDiff) {
                    bestDiff = dev;
                    bestLane = otherLane;
                }
            }
        }
        return bestLane;
    }

    private void startVehicleTurn(Vehicle vehicle, Lane targetLane, Intersection intersection) {
        // Un-shift the current position by subtracting the lateral offset to get the
        // center line coordinates
        double perpRad = Math.toRadians(vehicle.getAngle() + 90);
        Vector2D p0 = new Vector2D(
                vehicle.getPosition().getX() - Math.cos(perpRad) * vehicle.getLateralOffset(),
                vehicle.getPosition().getY() - Math.sin(perpRad) * vehicle.getLateralOffset());

        Vector2D p1 = lineIntersection(
                vehicle.getLane().getStart(), vehicle.getLane().getEnd(),
                targetLane.getStart(), targetLane.getEnd());

        double radius = MathUtils.distance(p0, p1);

        double targetAngle = MathUtils.angleTo(targetLane.getStart(), targetLane.getEnd());
        double rad = Math.toRadians(targetAngle);
        Vector2D targetDir = new Vector2D(Math.cos(rad), Math.sin(rad));

        Vector2D p2 = new Vector2D(
                p1.getX() + targetDir.getX() * radius,
                p1.getY() + targetDir.getY() * radius);

        vehicle.startTurn(p0, p1, p2, targetLane, intersection);
    }

    private Vector2D lineIntersection(Vector2D A, Vector2D B, Vector2D C, Vector2D D) {
        double a1 = B.getY() - A.getY();
        double b1 = A.getX() - B.getX();
        double c1 = a1 * A.getX() + b1 * A.getY();

        double a2 = D.getY() - C.getY();
        double b2 = C.getX() - D.getX();
        double c2 = a2 * C.getX() + b2 * C.getY();

        double determinant = a1 * b2 - a2 * b1;

        if (Math.abs(determinant) < 1e-5) {
            return new Vector2D((A.getX() + C.getX()) / 2, (A.getY() + C.getY()) / 2);
        } else {
            double x = (b2 * c1 - b1 * c2) / determinant;
            double y = (a1 * c2 - a2 * c1) / determinant;
            return new Vector2D(x, y);
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public List<Vehicle> getVehicles() {
        return vehicles;
    }

    public List<TrafficLight> getLights() {
        return lights;
    }

    public List<Intersection> getIntersections() {
        return intersections;
    }

    private boolean isVehiclePastStopLine(Vehicle v) {
        if (v.isTurning()) {
            return true;
        }
        Lane lane = v.getOriginalLane() != null ? v.getOriginalLane() : v.getLane();
        if (lane == null) {
            return false;
        }
        Vector2D stopLine = lane.getStopLine();
        if (stopLine == null) {
            return false;
        }
        double myDist = lane.getSignedDistance(v.getPosition());
        double stopDist = lane.getSignedDistance(stopLine);
        return (myDist + v.getWidth() / 2.0) >= (stopDist - 5.0);
    }
}
