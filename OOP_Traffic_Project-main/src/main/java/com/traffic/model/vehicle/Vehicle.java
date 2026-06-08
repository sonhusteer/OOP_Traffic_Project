package com.traffic.model.vehicle;

import com.traffic.model.map.IntersectionNode;
import com.traffic.model.map.RoadEdge;
import com.traffic.model.traffic.TrafficLight;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class Vehicle {
    private String id;
    private RoadEdge currentRoad;
    private boolean movingForward;
    private double distance;
    private double speed;
    private int laneIndex;

    // Smoothed visual coordinates and offsets
    protected double x;
    protected double y;
    protected double angle;
    protected double currentOffsetX;
    protected double currentOffsetY;
    private boolean initializedOffsets = false;

    // Dimensions
    private double length;
    private double width;
    private Color color;

    private static final Map<String, Image> imageCache = new HashMap<>();

    public Vehicle(String id, RoadEdge road, int laneIndex, double length, double width, Color color) {
        this.id = id;
        this.currentRoad = road;
        this.laneIndex = laneIndex;
        this.length = length;
        this.width = width;
        this.color = color;
        
        // Start facing correct direction based on starting road
        IntersectionNode start = road.getStartNode();
        IntersectionNode end = road.getEndNode();
        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();
        double roadAngle = Math.toDegrees(Math.atan2(dy, dx));
        
        // Check if starting direction is forward or backward.
        // Conceptually, when spawned, we assume moving forward (start -> end).
        this.movingForward = true;
        this.angle = roadAngle;
        this.distance = 0.0;
        this.speed = 0.0;
    }

    // Static helper to cache and load images
    public static Image getVehicleImage(String typeName) {
        String key = typeName.toLowerCase();
        if (!imageCache.containsKey(key)) {
            try {
                String path = "/images/" + key + ".png";
                java.io.InputStream is = Vehicle.class.getResourceAsStream(path);
                if (is != null) {
                    Image img = new Image(is);
                    imageCache.put(key, img);
                } else {
                    System.err.println("Could not find image asset: " + path);
                    imageCache.put(key, null);
                }
            } catch (Exception e) {
                System.err.println("Error loading image for " + key + ": " + e.getMessage());
                imageCache.put(key, null);
            }
        }
        return imageCache.get(key);
    }

    // Abstract methods to define behavior
    public abstract double getMaxSpeed();
    public abstract double getSafeDistance();
    public abstract boolean isPriorityVehicle();
    public abstract String getVehicleType();

    public double getLaneOffset(RoadEdge road, boolean movingForward, int laneIndex) {
        double rw = road.getWidth();
        int lanes = road.getLanesPerDirection();
        if (lanes <= 0) return 0;
        double laneWidth = (rw / 2) / lanes;
        double offset = (laneIndex + 0.5) * laneWidth;
        return movingForward ? offset : -offset;
    }

    protected TrafficLight getLightForIncomingRoad(IntersectionNode node, IntersectionNode neighbor) {
        double dx = neighbor.getX() - node.getX();
        double dy = neighbor.getY() - node.getY();
        double a = Math.toDegrees(Math.atan2(dy, dx));
        if (a < 0) a += 360;

        if (a >= 337.5 || a < 22.5) return node.getLightEast();
        if (a >= 67.5 && a < 112.5) return node.getLightSouth();
        if (a >= 157.5 && a < 202.5) return node.getLightWest();
        if (a >= 202.5 && a < 247.5) {
            if (node.getType() == IntersectionNode.NodeType.FIVE_WAY && node.isHasNW()) return node.getLightNW();
            return node.getLightWest();
        }
        if (a >= 247.5 && a < 292.5) return node.getLightNorth();

        // Diagonal fallback mappings:
        if (a >= 22.5 && a < 67.5) { // SE
            return node.isHasEast() ? node.getLightEast() : node.getLightSouth();
        }
        if (a >= 112.5 && a < 157.5) { // SW
            return node.isHasWest() ? node.getLightWest() : node.getLightSouth();
        }
        // NE (a >= 292.5 && a < 337.5)
        return node.isHasEast() ? node.getLightEast() : node.getLightNorth();
    }

    public Vehicle findVehicleAhead(List<Vehicle> allVehicles) {
        Vehicle closest = null;
        double minDist = Double.MAX_VALUE;
        for (Vehicle other : allVehicles) {
            if (other == this) continue;
            if (other.getCurrentRoad() == this.currentRoad 
                && other.isMovingForward() == this.movingForward 
                && other.getLaneIndex() == this.laneIndex) {
                double diff = other.getDistance() - this.distance;
                if (diff > 0 && diff < minDist) {
                    minDist = diff;
                    closest = other;
                }
            }
        }
        return closest;
    }

    private double lerp(double startVal, double endVal, double t) {
        return startVal + (endVal - startVal) * Math.max(0, Math.min(1, t));
    }

    public void update(List<Vehicle> allVehicles, boolean railBarrierDown) {
        IntersectionNode start = currentRoad.getStartNode();
        IntersectionNode end = currentRoad.getEndNode();
        double sx = start.getX(), sy = start.getY();
        double ex = end.getX(), ey = end.getY();
        double dx = ex - sx, dy = ey - sy;
        double roadLength = Math.hypot(dx, dy);

        double ux = dx / roadLength;
        double uy = dy / roadLength;
        double px = -uy;
        double py = ux;

        // Target offset calculation
        double targetOffsetVal = getLaneOffset(currentRoad, movingForward, laneIndex);
        double targetOffsetX = px * targetOffsetVal;
        double targetOffsetY = py * targetOffsetVal;

        if (!initializedOffsets) {
            currentOffsetX = targetOffsetX;
            currentOffsetY = targetOffsetY;
            double roadAngle = Math.toDegrees(Math.atan2(dy, dx));
            angle = movingForward ? roadAngle : (roadAngle + 180);
            while (angle < 0) angle += 360;
            while (angle >= 360) angle -= 360;
            initializedOffsets = true;
        }

        // Adjust speed based on rules
        double maxSpeed = getMaxSpeed() * com.traffic.config.Constants.VEHICLE_SPEED_MULTIPLIER;
        double targetSpeed = maxSpeed;
        double distLeft = roadLength - distance;

        // 1. Traffic Light check
        if (distLeft < 150 && !isPriorityVehicle()) {
            IntersectionNode nextNode = movingForward ? end : start;
            if (!nextNode.isSpawnNode()) {
                IntersectionNode neighborNode = movingForward ? start : end;
                TrafficLight light = getLightForIncomingRoad(nextNode, neighborNode);
                if (light != null && (light.getPhase() == TrafficLight.Phase.RED || light.getPhase() == TrafficLight.Phase.YELLOW)) {
                    double stopThreshold = 75.0 + getLength() / 2.0 + 8.0; // Stop behind stop line (75px from center)
                    double stopDist = distLeft - stopThreshold;
                    if (stopDist <= 0) {
                        targetSpeed = 0;
                    } else {
                        targetSpeed = Math.min(targetSpeed, maxSpeed * (stopDist / 60.0));
                    }
                }
            }
        }

        // 2. Railway Barrier check (For HUST Map crossing railroad at X = 180)
        if (railBarrierDown && !isPriorityVehicle()) {
            boolean crossesRail = (start.getX() < 180 && end.getX() > 180) || (start.getX() > 180 && end.getX() < 180);
            if (crossesRail) {
                double distToRail = Math.abs(x - 180);
                boolean approachingRail = false;
                if (movingForward) {
                    approachingRail = (start.getX() < 180 && x < 180) || (start.getX() > 180 && x > 180);
                } else {
                    approachingRail = (end.getX() < 180 && x < 180) || (end.getX() > 180 && x > 180);
                }

                if (approachingRail && distToRail < 120) {
                    double stopThreshold = 55.0 + getLength() / 2.0; // safe distance before rail
                    double stopDist = distToRail - stopThreshold;
                    if (stopDist <= 0) {
                        targetSpeed = 0;
                    } else {
                        targetSpeed = Math.min(targetSpeed, maxSpeed * (stopDist / 60.0));
                    }
                }
            }
        }

        // 3. Collision avoidance (Vehicle ahead check)
        Vehicle ahead = findVehicleAhead(allVehicles);
        if (ahead != null) {
            double distToAhead = ahead.getDistance() - distance;
            double minSafe = getSafeDistance() + ahead.getLength() / 2 + getLength() / 2;
            if (distToAhead < minSafe + 35) {
                if (distToAhead <= minSafe) {
                    targetSpeed = Math.min(targetSpeed, ahead.getSpeed() * 0.4);
                    if (distToAhead <= minSafe - 5) {
                        targetSpeed = 0;
                    }
                } else {
                    double ratio = (distToAhead - minSafe) / 35.0;
                    targetSpeed = Math.min(targetSpeed, lerp(ahead.getSpeed(), maxSpeed, ratio));
                }
            }
        }

        // Smooth speed transition
        if (speed < targetSpeed) {
            speed += 0.1;
            if (speed > targetSpeed) speed = targetSpeed;
        } else if (speed > targetSpeed) {
            speed -= 0.2; // break harder than accelerate
            if (speed < targetSpeed) speed = targetSpeed;
        }

        distance += speed;

        // Position interpolation
        double progress = distance / roadLength;
        if (progress > 1.0) progress = 1.0;

        double baseTargetX = movingForward ? (sx + dx * progress) : (ex - dx * progress);
        double baseTargetY = movingForward ? (sy + dy * progress) : (ey - dy * progress);

        // Smooth offsets
        currentOffsetX += (targetOffsetX - currentOffsetX) * 0.15;
        currentOffsetY += (targetOffsetY - currentOffsetY) * 0.15;

        x = baseTargetX + currentOffsetX;
        y = baseTargetY + currentOffsetY;

        // Smooth angle transition
        double roadAngle = Math.toDegrees(Math.atan2(dy, dx));
        double targetAngle = movingForward ? roadAngle : (roadAngle + 180);
        while (targetAngle < 0) targetAngle += 360;
        while (targetAngle >= 360) targetAngle -= 360;

        double diff = targetAngle - angle;
        while (diff < -180) diff += 360;
        while (diff > 180) diff -= 360;
        angle += diff * 0.15;
    }

    public boolean isAtEndOfRoad() {
        IntersectionNode start = currentRoad.getStartNode();
        IntersectionNode end = currentRoad.getEndNode();
        double length = Math.hypot(end.getX() - start.getX(), end.getY() - start.getY());
        return distance >= length - 2.0;
    }

    public void render(GraphicsContext gc, double darkness) {
        gc.save();
        gc.translate(x, y);
        gc.rotate(angle);

        if (com.traffic.config.Constants.BASIC_MODE) {
            gc.setFill(color);
            gc.fillRect(-length / 2, -width / 2, length, width);
            // Windshield
            gc.setFill(Color.BLACK);
            gc.fillRect(length / 4, -width / 2 + 2, length / 4, width - 4);
        } else {
            Image img = getVehicleImage(getVehicleType());
            if (img != null) {
                gc.drawImage(img, -length / 2, -width / 2, length, width);
            } else {
                gc.setFill(color);
                gc.fillRect(-length / 2, -width / 2, length, width);
                gc.setFill(Color.BLACK);
                gc.fillRect(length / 4, -width / 2 + 2, length / 4, width - 4);
            }
        }

        // Night Headlights
        if (darkness > 0.35) {
            gc.setFill(Color.rgb(255, 255, 170, 0.8));
            gc.fillOval(length / 2 - 2, -width / 3 - 2, 5, 5);
            gc.fillOval(length / 2 - 2, width / 3 - 2, 5, 5);
        }

        gc.restore();
    }

    // Getters and Setters
    public String getId() { return id; }
    public RoadEdge getCurrentRoad() { return currentRoad; }
    public void setCurrentRoad(RoadEdge road) { this.currentRoad = road; }
    public boolean isMovingForward() { return movingForward; }
    public void setMovingForward(boolean forward) { this.movingForward = forward; }
    public double getDistance() { return distance; }
    public void resetDistance() { this.distance = 0.0; }
    public double getSpeed() { return speed; }
    public int getLaneIndex() { return laneIndex; }
    public void setLaneIndex(int laneIndex) { this.laneIndex = laneIndex; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getLength() { return length; }
    public double getWidth() { return width; }
    public Color getColor() { return color; }
}
