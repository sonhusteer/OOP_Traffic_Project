package com.traffic.core;

import com.traffic.map.Lane;
import com.traffic.map.TrafficLight;

/**
 * Lop truong tuong cho moi phuong tien.
 */
public abstract class Vehicle {

    public enum YieldMode { NONE, RUSH, STOP }

    protected Vector2D position;
    protected double   speed;
    protected final double maxSpeed; // Toc do gioi han rieng cua tung loai xe.
    protected double   angle;
    protected IDriver  driver;

    protected String  name;
    protected double  width;
    protected double  height;
    protected boolean isPriority;

    protected YieldMode yieldMode = YieldMode.NONE;

    // Lan vat ly hien tai, lan nha va lan cu khi dang chuyen lan.
    protected Lane lane;
    protected Lane homeLane;
    protected Lane originalLane;

    // State chuyen lan.
    protected boolean isChangingLane = false;
    protected Lane targetLane = null;
    protected double laneChangeElapsed = 0.0;
    protected static final double LANE_CHANGE_DURATION = 0.8;

    protected double laneChangeCooldown = 0.0;
    protected static final double LANE_CHANGE_COOLDOWN = 1.0;

    public Vehicle(double x, double y, double speed, IDriver driver) {
        this.position   = new Vector2D(x, y);
        this.speed      = speed;
        this.maxSpeed   = speed;
        this.angle      = 0;
        this.driver     = driver;
        this.isPriority = false;
    }

    // ------------------------------------------------------------------
    // Gan xe vao lane.
    // ------------------------------------------------------------------

    public void setLane(Lane lane) {
        if (lane == null) return;

        // Neu xe da nam trong lane khac, go bo truoc de khong tao duplicate.
        if (this.lane != null && this.lane != lane) {
            this.lane.removeVehicle(this);
        }

        this.lane = lane;
        this.homeLane = lane;
        this.originalLane = lane;
        this.targetLane = null;
        this.isChangingLane = false;
        this.laneChangeElapsed = 0.0;
        lane.addVehicle(this);

        Vector2D start = lane.getStart();
        this.position.setX(start.getX());
        this.position.setY(start.getY());

        this.angle = lane.getAngleAtProgress(0.0);
    }

    /**
     * Goi khi engine xoa xe. Neu khong reset, Vehicle object cu van giu lane cu.
     */
    public void detachFromLane() {
        this.lane = null;
        this.homeLane = null;
        this.originalLane = null;
        this.targetLane = null;
        this.isChangingLane = false;
        this.laneChangeElapsed = 0.0;
        this.laneChangeCooldown = 0.0;
    }

    public void setLaneStartOffset(double offsetX, double offsetY) {
        position.setX(position.getX() + offsetX);
        position.setY(position.getY() + offsetY);
    }

    /**
     * Gỡ xe khỏi tất cả Lane và reservation liên quan.
     * TrafficEngine gọi khi remove/clear xe để tránh xe ma còn nằm trong Lane.
     */
    public void detachFromLanes() {
        if (lane != null) {
            lane.removeVehicle(this);
        }
        if (originalLane != null && originalLane != lane) {
            originalLane.removeVehicle(this);
        }
        if (homeLane != null && homeLane != lane && homeLane != originalLane) {
            homeLane.removeVehicle(this);
        }
        if (targetLane != null) {
            targetLane.release(this);
        }

        lane = null;
        homeLane = null;
        originalLane = null;
        targetLane = null;
        isChangingLane = false;
        laneChangeElapsed = 0.0;
        laneChangeCooldown = 0.0;
        yieldMode = YieldMode.NONE;
    }

    // ------------------------------------------------------------------
    // Cap nhat vat ly.
    // ------------------------------------------------------------------

    public final void update(double deltaTime) {
        if (laneChangeCooldown > 0) {
            laneChangeCooldown = Math.max(0.0, laneChangeCooldown - deltaTime);
        }

        if (isChangingLane && originalLane != null && targetLane != null) {
            double oldProgress = originalLane.getProgress(position) + speed * deltaTime;
            double newProgress = targetLane.getProgress(position) + speed * deltaTime;

            Vector2D oldPoint = originalLane.getPointAtProgress(oldProgress);
            Vector2D newPoint = targetLane.getPointAtProgress(newProgress);

            laneChangeElapsed += deltaTime;
            double t = MathUtils.clamp(
                laneChangeElapsed / LANE_CHANGE_DURATION,
                0.0,
                1.0
            );

            position.setX(MathUtils.lerp(oldPoint.getX(), newPoint.getX(), t));
            position.setY(MathUtils.lerp(oldPoint.getY(), newPoint.getY(), t));

            double oldAngle = originalLane.getAngleAtProgress(oldProgress);
            double newAngle = targetLane.getAngleAtProgress(newProgress);
            this.angle = lerpAngle(oldAngle, newAngle, t);

            if (t >= 1.0) {
                finishLaneChange();
            }

            return;
        }

        double radians = Math.toRadians(this.angle);
        position.setX(position.getX() + Math.cos(radians) * speed * deltaTime);
        position.setY(position.getY() + Math.sin(radians) * speed * deltaTime);

        if (lane != null && !isChangingLane) {
            this.angle = lane.getAngleAtProgress(lane.getProgress(position));
        }
    }

    private void finishLaneChange() {
        Lane completedLane = targetLane;

        if (originalLane != null) {
            originalLane.removeVehicle(this);
        }

        if (completedLane != null) {
            completedLane.release(this);
            completedLane.addVehicle(this);
        }

        lane = completedLane;
        originalLane = lane;
        targetLane = null;
        isChangingLane = false;
        laneChangeElapsed = 0.0;
        laneChangeCooldown = LANE_CHANGE_COOLDOWN;
    }

    private double angleDiff(double a, double b) {
        double diff = Math.abs(a - b) % 360.0;
        return diff > 180.0 ? 360.0 - diff : diff;
    }

    private double lerpAngle(double from, double to, double t) {
        double diff = ((to - from + 540.0) % 360.0) - 180.0;
        return from + diff * t;
    }

    // ------------------------------------------------------------------
    // Chuyen lan.
    // ------------------------------------------------------------------

    public boolean startLaneChange(Lane newLane) {
        if (newLane == null || this.lane == null) return false;
        if (this.lane == newLane) return false;
        if (isChangingLane) return false;
        if (laneChangeCooldown > 0) return false;

        double currentProgress = lane.getProgress(position);
        double newProgress = newLane.getProgress(position);

        double currentAngle = lane.getAngleAtProgress(currentProgress);
        double newAngle = newLane.getAngleAtProgress(newProgress);

        // Chi cho phep chuyen giua cac lane gan song song.
        if (angleDiff(currentAngle, newAngle) > 45.0) {
            return false;
        }

        this.originalLane = this.lane;
        this.targetLane = newLane;
        newLane.reserve(this);

        this.isChangingLane = true;
        this.laneChangeElapsed = 0.0;

        return true;
    }

    /** Xe dang o ngoai lane nha? */
    public boolean isAwayFromHome() {
        return homeLane != null && lane != homeLane && !isChangingLane;
    }

    public void makeDecision(TrafficLight nearestLight) {
        if (driver != null) {
            driver.makeDecision(this, nearestLight);
        }
    }

    // ------------------------------------------------------------------
    // Getters / setters.
    // ------------------------------------------------------------------

    public Vector2D getPosition()        { return position;    }
    public double   getSpeed()           { return speed;       }
    public void     setSpeed(double s)   { this.speed = MathUtils.clamp(s, 0.0, maxSpeed); }
    public double   getMaxSpeed()        { return maxSpeed;    }
    public double   getAngle()           { return angle;       }
    public void     setAngle(double a)   { this.angle = a;     }
    public String   getName()            { return name;        }
    public double   getWidth()           { return width;       }
    public double   getHeight()          { return height;      }
    public boolean  isPriority()         { return isPriority;          }
    public Lane     getOriginalLane()    { return originalLane;        }
    public Lane     getLane()            { return lane;                }
    public Lane     getHomeLane()        { return homeLane;            }
    public Lane     getTargetLane()      { return targetLane;          }
    public YieldMode getYieldMode()      { return yieldMode;           }
    public void     setYieldMode(YieldMode m) { this.yieldMode = m;    }
    public boolean  isChangingLane()     { return isChangingLane;      }
    public double   getLaneChangeCooldown()   { return laneChangeCooldown;  }

    public abstract String getTypeName();
}
