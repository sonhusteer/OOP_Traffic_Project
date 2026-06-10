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

    // Pre-selected routing path
    private RoadEdge nextRoad;
    private boolean nextMovingForward;
    private int nextLaneIndex;

    // Smoothed visual coordinates and offsets
    protected double x;
    protected double y;
    protected double angle;
    protected double currentOffsetX;
    protected double currentOffsetY;
    private boolean initializedOffsets = false;
    private double stoppedTimer = 0.0;

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
        this.stoppedTimer = 0.0;
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
                
                // If this is a priority vehicle, and the other vehicle is yielding to emergency vehicles, we can ignore it
                if (this.isPriorityVehicle() && other.isEmergencyVehicleNearby(allVehicles, 450.0)) {
                    continue;
                }

                // If this vehicle wants to turn right and the vehicle ahead has shifted to the left to let it pass
                if (this.isRightTurnPlanned() && !this.isPriorityVehicle() && !other.isPriorityVehicle()) {
                    double diffX = other.getX() - x;
                    double diffY = other.getY() - y;
                    double headingRad = Math.toRadians(angle);
                    double sin = Math.sin(headingRad);
                    double cos = Math.cos(headingRad);
                    double distLat = Math.abs(-diffX * sin + diffY * cos);
                    
                    // If they are laterally separated by more than 10 px, we can bypass
                    if (distLat > 10.0) {
                        continue;
                    }
                }
                
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
        // Update stopped timer
        if (speed < 0.05) {
            stoppedTimer += 1.0 / 60.0;
        } else {
            stoppedTimer = 0.0;
        }

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

        // Emergency vehicle nearby logic (Level 1 rule)
        boolean emergencyNearby = isEmergencyVehicleNearby(allVehicles, 220.0);

        // Oncoming emergency vehicle nearby logic (Only vehicles in lane 0 need to shift for oncoming emergency vehicles)
        boolean oncomingEmergencyNearby = false;
        if (!this.isPriorityVehicle() && this.laneIndex == 0) {
            for (Vehicle other : allVehicles) {
                if (other.isPriorityVehicle() && other.getCurrentRoad() == this.currentRoad && other.isMovingForward() != this.movingForward) {
                    double dist = Math.hypot(other.getX() - x, other.getY() - y);
                    if (dist < 250.0) {
                        oncomingEmergencyNearby = true;
                        break;
                    }
                }
            }
        }

        // Scan if there is a right-turning vehicle behind us (disabled if we are stopped by a red/yellow light ahead)
        boolean rightTurnerBehind = false;
        if (!this.isPriorityVehicle()) {
            boolean lightIsRedOrYellow = false;
            double distLeft = roadLength - distance;
            if (distLeft < 150) {
                IntersectionNode nextNode = movingForward ? end : start;
                if (!nextNode.isSpawnNode()) {
                    IntersectionNode neighborNode = movingForward ? start : end;
                    TrafficLight light = getLightForIncomingRoad(nextNode, neighborNode);
                    if (light != null && (light.getPhase() == TrafficLight.Phase.RED || light.getPhase() == TrafficLight.Phase.YELLOW)) {
                        lightIsRedOrYellow = true;
                    }
                }
            }

            if (!lightIsRedOrYellow) {
                for (Vehicle other : allVehicles) {
                    if (other != this 
                        && other.getCurrentRoad() == currentRoad 
                        && other.isMovingForward() == movingForward 
                        && other.getLaneIndex() == laneIndex) {
                        if (other.getDistance() < this.distance && (this.distance - other.getDistance()) < 150.0) {
                            if (other.isRightTurnPlanned()) {
                                rightTurnerBehind = true;
                                break;
                            }
                        }
                    }
                }
            }
        }

        double rw = currentRoad.getWidth();
        int lanes = currentRoad.getLanesPerDirection();
        if (lanes > 0) {
            double laneWidth = (rw / 2) / lanes;
            if (emergencyNearby || oncomingEmergencyNearby) {
                // Priority 1: Shift right for emergency vehicles
                targetOffsetVal += (movingForward ? 1.0 : -1.0) * (laneWidth * 0.45);
            } else if (rightTurnerBehind) {
                // Priority 2: Shift left to let right-turners behind pass
                targetOffsetVal -= (movingForward ? 1.0 : -1.0) * (laneWidth * 0.45);
            } else if (this.isRightTurnPlanned() && !this.isPriorityVehicle()) {
                // Priority 3: Shift right slightly if we want to turn right (helping us squeeze past)
                targetOffsetVal += (movingForward ? 1.0 : -1.0) * (laneWidth * 0.25);
            }
        }

        // If this is an emergency vehicle, and there is a yielding vehicle ahead, shift to the left to bypass
        if (this.isPriorityVehicle()) {
            boolean hasYieldingAhead = false;
            for (Vehicle other : allVehicles) {
                if (other != this && other.getCurrentRoad() == currentRoad 
                    && other.isMovingForward() == movingForward 
                    && other.getLaneIndex() == laneIndex) {
                    if (other.getDistance() > this.distance && (other.getDistance() - this.distance) < 150.0) {
                        hasYieldingAhead = true;
                        break;
                    }
                }
            }
            if (hasYieldingAhead) {
                if (lanes > 0) {
                    double laneWidth = (rw / 2) / lanes;
                    // Shift to the left of the lane (opposite direction of pulling over)
                    targetOffsetVal -= (movingForward ? 1.0 : -1.0) * (laneWidth * 0.35);
                }
            }
        }

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

        // Emergency vehicle speed reduction (Level 1 rule)
        if (emergencyNearby) {
            targetSpeed = 0;
        }

        // Non-priority road approach check (Level 3 priority rule)
        if (distLeft < 120.0 && !isPriorityVehicle()) {
            IntersectionNode nextNode = movingForward ? end : start;
            if (!nextNode.isSpawnNode() && currentRoad.getType() != RoadEdge.RoadType.AVENUE) {
                targetSpeed = Math.min(targetSpeed, maxSpeed * 0.6);
            }
        }

        // 1. Traffic Light and Next Road Full check
        if (distLeft < 150 && !isPriorityVehicle()) {
            IntersectionNode nextNode = movingForward ? end : start;
            if (!nextNode.isSpawnNode()) {
                IntersectionNode neighborNode = movingForward ? start : end;
                TrafficLight light = getLightForIncomingRoad(nextNode, neighborNode);
                
                // Ignore light if we already crossed the pedestrian crossing (distLeft <= 70.0)
                boolean alreadyCrossedPedestrian = (distLeft <= 70.0);
                boolean allowedToGo = alreadyCrossedPedestrian;

                boolean nextRoadFull = false;
                if (nextRoad != null) {
                    long count = 0;
                    for (Vehicle other : allVehicles) {
                        if (other.getCurrentRoad() == nextRoad) {
                            count++;
                        }
                    }
                    if (count >= 8) {
                        nextRoadFull = true;
                    }
                }

                if ((light != null && !allowedToGo && (light.getPhase() == TrafficLight.Phase.RED || light.getPhase() == TrafficLight.Phase.YELLOW))
                    || (nextRoadFull && !alreadyCrossedPedestrian)) {
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

        // 2b. Intersection yielding check (Priority to the right)
        if (shouldYieldAtIntersection(allVehicles)) {
            double stopThreshold = 75.0 + getLength() / 2.0 + 8.0; // Stop behind stop line (75px from center)
            double stopDist = distLeft - stopThreshold;
            if (stopDist <= 0) {
                targetSpeed = 0;
            } else {
                targetSpeed = Math.min(targetSpeed, maxSpeed * (stopDist / 60.0));
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

        // 3b. Visual collision avoidance (for vehicles on different roads/lanes, e.g. at intersections or turning)
        for (Vehicle other : allVehicles) {
            if (other == this) continue;

            boolean sameRoadAndLane = (other.getCurrentRoad() == currentRoad 
                && other.isMovingForward() == movingForward 
                && other.getLaneIndex() == laneIndex);
            if (sameRoadAndLane) continue;

            double diffX = other.getX() - x;
            double diffY = other.getY() - y;
            double distSq = diffX * diffX + diffY * diffY;
            if (distSq < 120 * 120) { // only check nearby vehicles
                double headingRad = Math.toRadians(angle);
                double cos = Math.cos(headingRad);
                double sin = Math.sin(headingRad);
                double distLong = diffX * cos + diffY * sin;
                double distLat = Math.abs(-diffX * sin + diffY * cos);

                if (distLong > 0) {
                    double overlapThreshold = (width + other.getWidth()) / 2.0 + 6.0;

                    // If one is priority and they are oncoming on the same road, reduce overlap threshold to avoid deadlock
                    if ((this.isPriorityVehicle() || other.isPriorityVehicle()) 
                        && other.getCurrentRoad() == currentRoad 
                        && other.isMovingForward() != movingForward) {
                        overlapThreshold = (width + other.getWidth()) / 2.0 - 2.0;
                    }

                    if (distLat < overlapThreshold) {
                        double minSafe = (length + other.getLength()) / 2.0 + getSafeDistance() * 0.8;
                        if (distLong < minSafe + 25) {
                            if (distLong <= minSafe) {
                                targetSpeed = Math.min(targetSpeed, other.getSpeed() * 0.4);
                                if (distLong <= minSafe - 5) {
                                    targetSpeed = 0;
                                }
                            } else {
                                double ratio = (distLong - minSafe) / 25.0;
                                targetSpeed = Math.min(targetSpeed, lerp(other.getSpeed(), maxSpeed, ratio));
                            }
                        }
                    }
                }
            }
        }

        // Turn speed reduction rule (optimized so turning speed is slightly faster to clear intersections, but still slower than straight speed)
        if (isTurningPlanned() && distLeft < 80.0) {
            targetSpeed = Math.min(targetSpeed, maxSpeed * 0.5);
        }
        if (isCurrentlyTurning()) {
            targetSpeed = Math.min(targetSpeed, maxSpeed * 0.55);
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
        
        double threshold = 2.0;
        if (isRightTurnPlanned()) {
            threshold = currentRoad.getWidth() / 2.0 + 10.0;
        } else if (isLeftTurnPlanned() || isTurningPlanned()) {
            threshold = 15.0;
        }
        
        return distance >= length - threshold;
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
    public double getAngle() { return angle; }
    public double getLength() { return length; }
    public double getWidth() { return width; }
    public Color getColor() { return color; }

    public RoadEdge getNextRoad() { return nextRoad; }
    public void setNextRoad(RoadEdge nextRoad) { this.nextRoad = nextRoad; }
    public boolean isNextMovingForward() { return nextMovingForward; }
    public void setNextMovingForward(boolean forward) { this.nextMovingForward = forward; }
    public int getNextLaneIndex() { return nextLaneIndex; }
    public void setNextLaneIndex(int lane) { this.nextLaneIndex = lane; }

    public double getRoadAngle(RoadEdge road, boolean forward) {
        IntersectionNode start = road.getStartNode();
        IntersectionNode end = road.getEndNode();
        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();
        double a = Math.toDegrees(Math.atan2(dy, dx));
        double roadAngle = forward ? a : (a + 180);
        while (roadAngle < 0) roadAngle += 360;
        while (roadAngle >= 360) roadAngle -= 360;
        return roadAngle;
    }

    public boolean isTurningPlanned() {
        if (nextRoad == null) return false;
        double currentA = getRoadAngle(currentRoad, movingForward);
        double nextA = getRoadAngle(nextRoad, nextMovingForward);
        double diff = Math.abs(nextA - currentA);
        while (diff < -180) diff += 360;
        while (diff > 180) diff -= 360;
        diff = Math.abs(diff);
        return diff > 20.0;
    }

    public boolean isCurrentlyTurning() {
        double currentRoadAngle = getRoadAngle(currentRoad, movingForward);
        double diff = currentRoadAngle - angle;
        while (diff < -180) diff += 360;
        while (diff > 180) diff -= 360;
        return Math.abs(diff) > 10.0 && distance < 60.0;
    }

    public boolean isRightTurnPlanned() {
        if (nextRoad == null) return false;
        
        // Calculate heading vector of current road
        IntersectionNode currentStart = currentRoad.getStartNode();
        IntersectionNode currentEnd = currentRoad.getEndNode();
        double dx1 = currentEnd.getX() - currentStart.getX();
        double dy1 = currentEnd.getY() - currentStart.getY();
        if (!movingForward) {
            dx1 = -dx1;
            dy1 = -dy1;
        }
        
        // Calculate heading vector of next road
        IntersectionNode nextStart = nextRoad.getStartNode();
        IntersectionNode nextEnd = nextRoad.getEndNode();
        double dx2 = nextEnd.getX() - nextStart.getX();
        double dy2 = nextEnd.getY() - nextStart.getY();
        if (!nextMovingForward) {
            dx2 = -dx2;
            dy2 = -dy2;
        }
        
        double angle1 = Math.toDegrees(Math.atan2(dy1, dx1));
        double angle2 = Math.toDegrees(Math.atan2(dy2, dx2));
        
        double diff = angle2 - angle1;
        while (diff < -180) diff += 360;
        while (diff > 180) diff -= 360;
        
        return diff >= 45.0 && diff <= 135.0;
    }

    public IntersectionNode getTargetNode() {
        if (currentRoad == null) return null;
        return movingForward ? currentRoad.getEndNode() : currentRoad.getStartNode();
    }

    public boolean isLeftTurnPlanned() {
        if (nextRoad == null) return false;
        
        IntersectionNode currentStart = currentRoad.getStartNode();
        IntersectionNode currentEnd = currentRoad.getEndNode();
        double dx1 = currentEnd.getX() - currentStart.getX();
        double dy1 = currentEnd.getY() - currentStart.getY();
        if (!movingForward) {
            dx1 = -dx1;
            dy1 = -dy1;
        }
        
        IntersectionNode nextStart = nextRoad.getStartNode();
        IntersectionNode nextEnd = nextRoad.getEndNode();
        double dx2 = nextEnd.getX() - nextStart.getX();
        double dy2 = nextEnd.getY() - nextStart.getY();
        if (!nextMovingForward) {
            dx2 = -dx2;
            dy2 = -dy2;
        }
        
        double angle1 = Math.toDegrees(Math.atan2(dy1, dx1));
        double angle2 = Math.toDegrees(Math.atan2(dy2, dx2));
        
        double diff = angle2 - angle1;
        while (diff < -180) diff += 360;
        while (diff > 180) diff -= 360;
        
        return diff >= -135.0 && diff <= -45.0;
    }

    public void setOffsets(double ox, double oy) {
        this.currentOffsetX = ox;
        this.currentOffsetY = oy;
    }

    public boolean isEmergencyVehicleNearby(List<Vehicle> allVehicles, double radius) {
        if (this.isPriorityVehicle()) {
            return false;
        }
        double activeRadius = 450.0;
        // 1. Direct check: emergency vehicle behind us
        for (Vehicle other : allVehicles) {
            if (other != this && other.isPriorityVehicle()) {
                // Yield only if the emergency vehicle is on the same road, same direction, and behind us
                if (other.getCurrentRoad() == this.currentRoad && other.isMovingForward() == this.movingForward) {
                    if (other.getDistance() < this.distance) {
                        double dist = Math.hypot(other.getX() - x, other.getY() - y);
                        if (dist < activeRadius) {
                            return true;
                        }
                    }
                }
            }
        }

        // 2. Propagation check: is there a yielding vehicle ahead of us on the same road and lane?
        for (Vehicle other : allVehicles) {
            if (other != this 
                && other.getCurrentRoad() == this.currentRoad 
                && other.isMovingForward() == this.movingForward 
                && other.getLaneIndex() == this.laneIndex) {
                if (other.getDistance() > this.distance) {
                    double dist = other.getDistance() - this.distance;
                    if (dist < 250.0) {
                        if (other.isEmergencyVehicleNearby(allVehicles, radius)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    public boolean shouldYieldAtIntersection(List<Vehicle> allVehicles) {
        if (isPriorityVehicle()) {
            return false;
        }

        // Deadlock resolver: if we are stuck for more than 3.5 seconds, ignore yielding rules and go
        if (stoppedTimer > 3.5) {
            return false;
        }

        IntersectionNode targetNode = getTargetNode();
        if (targetNode == null || targetNode.isSpawnNode()) {
            return false;
        }

        double distToNode = Math.hypot(x - targetNode.getX(), y - targetNode.getY());
        if (distToNode > 120.0) {
            return false;
        }

        IntersectionNode start = currentRoad.getStartNode();
        IntersectionNode end = currentRoad.getEndNode();
        double roadLength = Math.hypot(end.getX() - start.getX(), end.getY() - start.getY());
        double distLeft = roadLength - distance;
        if (distLeft <= 70.0) {
            return false;
        }

        IntersectionNode neighborNode = movingForward ? start : end;
        TrafficLight light = getLightForIncomingRoad(targetNode, neighborNode);



        for (Vehicle other : allVehicles) {
            if (other == this) continue;

            if (other.getTargetNode() != targetNode) {
                continue;
            }

            // If other is an emergency vehicle approaching the same intersection, we must yield to it immediately
            if (other.isPriorityVehicle()) {
                return true;
            }

            double otherDistToNode = Math.hypot(other.getX() - targetNode.getX(), other.getY() - targetNode.getY());
            if (otherDistToNode > 120.0) {
                continue;
            }

            IntersectionNode otherStart = other.getCurrentRoad().getStartNode();
            IntersectionNode otherEnd = other.getCurrentRoad().getEndNode();
            double otherRoadLength = Math.hypot(otherEnd.getX() - otherStart.getX(), otherEnd.getY() - otherStart.getY());
            double otherDistLeft = otherRoadLength - other.getDistance();
            if (otherDistLeft <= 70.0) {
                continue;
            }

            // 1. Traffic Light status check first!
            // If other is stopped by its red/yellow light, we don't yield to it (even if other is on a priority road).
            IntersectionNode otherNeighbor = other.isMovingForward() ? otherStart : otherEnd;
            TrafficLight otherLight = other.getLightForIncomingRoad(targetNode, otherNeighbor);
            boolean otherStoppedByLight = (otherLight != null && 
                (otherLight.getPhase() == TrafficLight.Phase.RED || otherLight.getPhase() == TrafficLight.Phase.YELLOW));
            if (otherStoppedByLight) {
                continue;
            }

            // 2. Priority road check (Level 2 priority rule)
            boolean thisOnPriority = (this.currentRoad.getType() == RoadEdge.RoadType.AVENUE);
            boolean otherOnPriority = (other.getCurrentRoad().getType() == RoadEdge.RoadType.AVENUE);
            if (thisOnPriority && !otherOnPriority) {
                continue; // We have priority, don't yield to side road vehicles
            }
            if (!thisOnPriority && otherOnPriority) {
                return true; // Other is on priority road, we must yield!
            }



            // 4. Xe rẽ nhường đường cho xe đi thẳng ở ngã tư (Xe đi thẳng được ưu tiên đi trước)
            boolean thisIsTurning = this.isTurningPlanned() || this.isCurrentlyTurning() || this.isLeftTurnPlanned() || this.isRightTurnPlanned();
            boolean otherIsTurning = other.isTurningPlanned() || other.isCurrentlyTurning() || other.isLeftTurnPlanned() || other.isRightTurnPlanned();
            if (thisIsTurning && !otherIsTurning) {
                return true; // We are turning, other is going straight -> yield!
            }

            // 5. Standard Right-Hand Priority
            double dx = other.getX() - x;
            double dy = other.getY() - y;

            double headingRad = Math.toRadians(angle);
            double rx = -Math.sin(headingRad);
            double ry = Math.cos(headingRad);
            double dotRight = dx * rx + dy * ry;

            double hx = Math.cos(headingRad);
            double hy = Math.sin(headingRad);
            double dotForward = dx * hx + dy * hy;

            if (dotRight > 15.0 && dotForward > -20.0) {
                return true;
            }
        }

        return false;
    }
}
