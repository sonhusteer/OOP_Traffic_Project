package com.traffic.core;

import com.traffic.map.Lane;
import com.traffic.map.TrafficLight;
import com.traffic.map.Intersection;

/**
 * Lớp trừu tượng cho mọi phương tiện.
 *
 * Nguyên tắc:
 * - Chỉ chứa DỮ LIỆU VẬT LÝ (vị trí, tốc độ, góc)
 * - KHÔNG chứa bất kỳ logic vẽ nào (tách biệt hoàn toàn)
 * - Hành vi lái xe được ủy quyền cho IDriver (Strategy Pattern)
 */
public abstract class Vehicle {

    /**
     * Trạng thái nhường đường cho xe ưu tiên:
     * NONE — bình thường, không bị ảnh hưởng
     * RUSH — Ambu cùng làn phía sau → tăng tốc (tạo hiệu ứng căng thẳng)
     * STOP — Ambu từ làn ngang vào ngã tư → dừng hẳn dù đèn xanh
     */
    public enum YieldMode {
        NONE, RUSH, STOP
    }

    public enum TurnDecision {
        STRAIGHT, LEFT, RIGHT
    }

    protected Vector2D position;
    protected double speed;
    protected double angle;
    protected IDriver driver;

    protected TurnDecision turnDecision = TurnDecision.STRAIGHT;

    // Turn transition state
    protected boolean isTurning = false;
    protected double turnT = 0.0;
    protected Vector2D turnP0;
    protected Vector2D turnP1;
    protected Vector2D turnP2;
    protected Lane turnTargetLane;
    protected Intersection lastIntersectionTurned;

    protected String name;
    protected double width;
    protected double height;
    protected boolean isPriority;

    // Engine đặt mode này trước mỗi tick để driver phản ứng phù hợp
    protected YieldMode yieldMode = YieldMode.NONE;

    // Làn đường hiện tại
    protected Lane lane;

    // ── Hỗ trợ chuyển làn chính thức (giữa 2 làn cùng chiều) ────────────
    protected Lane originalLane;
    protected boolean isChangingLane = false;
    protected Vector2D targetPosition = null;
    protected static final double LANE_CHANGE_SPEED = 120.0;

    protected double laneChangeCooldown = 0.0;
    protected static final double LANE_CHANGE_COOLDOWN = 1.5;
    protected boolean hasOvertaken = false;

    // ── Dịch ngang trong làn (lách xe kiểu Việt Nam) ─────────────────────
    // Dương = dạt phải, Âm = lách trái. Đơn vị: pixel.
    protected double lateralOffset = 0;
    protected double targetLateralOffset = 0;
    private static final double LATERAL_SHIFT_SPEED = 80.0; // px/s

    public Vehicle(double x, double y, double speed, IDriver driver) {
        this.position = new Vector2D(x, y);
        this.speed = speed;
        this.angle = 0;
        this.driver = driver;
        this.isPriority = false;
    }

    // ── Gắn xe vào làn đường ─────────────────────────────────────────────

    public void setLane(Lane lane) {
        this.lane = lane;
        this.originalLane = lane;
        lane.addVehicle(this);

        Vector2D start = lane.getStart();
        this.position.setX(start.getX());
        this.position.setY(start.getY());

        Vector2D end = lane.getEnd();
        this.angle = MathUtils.angleTo(start, end);

        // Shift laterally based on 2-lane layout (4-wheelers on left, 2-wheelers on
        // right)
        this.targetLateralOffset = isFourWheeler() ? -18.0 : 18.0;
        this.lateralOffset = this.targetLateralOffset;
        double perpRad = Math.toRadians(this.angle + 90);
        this.position.setX(this.position.getX() + Math.cos(perpRad) * lateralOffset);
        this.position.setY(this.position.getY() + Math.sin(perpRad) * lateralOffset);
    }

    public void setLaneStartOffset(double offsetX, double offsetY) {
        position.setX(position.getX() + offsetX);
        position.setY(position.getY() + offsetY);
    }

    // ── Logic cập nhật vật lý ─────────────────────────────────────────────

    /** final: không ai được override — đảm bảo vật lý nhất quán */
    public final void update(double deltaTime) {
        // 1. Đếm giờ hạ nhiệt chuyển làn
        if (laneChangeCooldown > 0) {
            laneChangeCooldown -= deltaTime;
        }

        if (isTurning) {
            double turnLength = MathUtils.distance(turnP0, turnP1) + MathUtils.distance(turnP1, turnP2);
            if (turnLength < 5.0)
                turnLength = 5.0;
            turnT += (speed * deltaTime) / turnLength;

            if (turnT >= 1.0) {
                position.setX(turnP2.getX());
                position.setY(turnP2.getY());
                completeTurn(turnTargetLane);
            } else {
                double omt = 1.0 - turnT;
                double x = omt * omt * turnP0.getX() + 2 * omt * turnT * turnP1.getX() + turnT * turnT * turnP2.getX();
                double y = omt * omt * turnP0.getY() + 2 * omt * turnT * turnP1.getY() + turnT * turnT * turnP2.getY();

                double dX = 2 * omt * (turnP1.getX() - turnP0.getX()) + 2 * turnT * (turnP2.getX() - turnP1.getX());
                double dY = 2 * omt * (turnP1.getY() - turnP0.getY()) + 2 * turnT * (turnP2.getY() - turnP1.getY());
                if (Math.abs(dX) > 0.01 || Math.abs(dY) > 0.01) {
                    this.angle = Math.toDegrees(Math.atan2(dY, dX));
                }

                // Shift position laterally based on current turning angle
                double perpRad = Math.toRadians(this.angle + 90);
                position.setX(x + Math.cos(perpRad) * lateralOffset);
                position.setY(y + Math.sin(perpRad) * lateralOffset);
            }
            return;
        }

        // 2. Dịch ngang trong làn (lách xe)
        double lateralDiff = targetLateralOffset - lateralOffset;
        if (Math.abs(lateralDiff) > 0.5) {
            double step = Math.min(Math.abs(lateralDiff), LATERAL_SHIFT_SPEED * deltaTime);
            double signedStep = Math.signum(lateralDiff) * step;
            lateralOffset += signedStep;

            // Di chuyển vuông góc với hướng mũi xe
            // Bên phải của xe = angle + 90°
            double perpRad = Math.toRadians(this.angle + 90);
            position.setX(position.getX() + Math.cos(perpRad) * signedStep);
            position.setY(position.getY() + Math.sin(perpRad) * signedStep);
        }

        // 3. Chuyển làn chính thức (trượt sang làn cùng chiều khác)
        if (isChangingLane && targetPosition != null) {
            double dist = MathUtils.distance(position, targetPosition);

            if (dist < 3.0) {
                position.setX(targetPosition.getX());
                position.setY(targetPosition.getY());
                isChangingLane = false;
                targetPosition = null;
                if (lane != null)
                    lane.release(this);
                originalLane = lane;
                laneChangeCooldown = LANE_CHANGE_COOLDOWN;
            } else {
                double changeAngle = MathUtils.angleTo(position, targetPosition);
                double radChange = Math.toRadians(changeAngle);
                double step = Math.min(LANE_CHANGE_SPEED * deltaTime, dist);
                position.setX(position.getX() + Math.cos(radChange) * step);
                position.setY(position.getY() + Math.sin(radChange) * step);
            }
        }

        // 4. Di chuyển thẳng theo hướng mũi xe
        double radians = Math.toRadians(this.angle);
        position.setX(position.getX() + Math.cos(radians) * speed * deltaTime);
        position.setY(position.getY() + Math.sin(radians) * speed * deltaTime);
    }

    // ── Chuyển làn chính thức (chỉ dùng cho làn cùng chiều) ──────────────

    public void startLaneChange(Lane newLane) {
        if (this.lane == newLane)
            return;
        if (isChangingLane)
            return;
        if (laneChangeCooldown > 0)
            return;

        boolean isOvertaking = (this.lane.getLeftNeighbor() == newLane);
        this.originalLane = this.lane;

        this.lane.removeVehicle(this);
        this.lane = newLane;
        newLane.addVehicle(this);
        newLane.reserve(this);

        // Tính target: chiếu vuông góc từ vị trí xe lên đường tâm làn mới
        Vector2D ns = newLane.getStart();
        Vector2D ne = newLane.getEnd();
        double lx = ne.getX() - ns.getX();
        double ly = ne.getY() - ns.getY();
        double lenSq = lx * lx + ly * ly;

        double t = 0.5;
        if (lenSq > 1) {
            t = ((position.getX() - ns.getX()) * lx
                    + (position.getY() - ns.getY()) * ly) / lenSq;
            t = MathUtils.clamp(t, 0.0, 1.0);
        }
        double targetX = ns.getX() + t * lx;
        double targetY = ns.getY() + t * ly;

        this.targetPosition = new Vector2D(targetX, targetY);
        this.isChangingLane = true;

        if (isOvertaking) {
            this.hasOvertaken = true;
        } else {
            this.hasOvertaken = false;
        }

        // Chống Car Flipping
        double newAngle = MathUtils.angleTo(ns, ne);
        double diff = Math.abs(this.angle - newAngle);
        if (diff > 180)
            diff = 360 - diff;
        if (diff <= 90) {
            this.angle = newAngle;
        }
    }

    /** Ủy quyền quyết định lái cho driver */
    public void makeDecision(TrafficLight nearestLight) {
        if (driver != null) {
            driver.makeDecision(this, nearestLight);
        }
    }

    // ── Kiểm tra làn bên cạnh có cùng chiều hay không ────────────────────
    /** Trả về true nếu otherLane đi cùng chiều với làn hiện tại */
    public boolean isSameDirection(Lane otherLane) {
        if (otherLane == null || lane == null)
            return false;
        double myAngle = MathUtils.angleTo(lane.getStart(), lane.getEnd());
        double otherAngle = MathUtils.angleTo(otherLane.getStart(), otherLane.getEnd());
        double diff = Math.abs(myAngle - otherAngle);
        if (diff > 180)
            diff = 360 - diff;
        return diff < 90;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────

    public Vector2D getPosition() {
        return position;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double s) {
        this.speed = s;
    }

    public double getAngle() {
        return angle;
    }

    public void setAngle(double a) {
        this.angle = a;
    }

    public String getName() {
        return name;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    public boolean isPriority() {
        return isPriority;
    }

    public Lane getOriginalLane() {
        return originalLane;
    }

    public Lane getLane() {
        return lane;
    }

    public YieldMode getYieldMode() {
        return yieldMode;
    }

    public void setYieldMode(YieldMode m) {
        this.yieldMode = m;
    }

    public boolean isChangingLane() {
        return isChangingLane;
    }

    public double getLaneChangeCooldown() {
        return laneChangeCooldown;
    }

    public boolean hasOvertaken() {
        return hasOvertaken;
    }

    // Lateral offset
    public double getLateralOffset() {
        return lateralOffset;
    }

    public void setTargetLateralOffset(double o) {
        this.targetLateralOffset = o;
    }

    // Turn control methods
    public TurnDecision getTurnDecision() {
        return turnDecision;
    }

    public void setTurnDecision(TurnDecision td) {
        this.turnDecision = td;
    }

    public boolean isTurning() {
        return isTurning;
    }

    public double getTurnT() {
        return turnT;
    }

    public Intersection getLastIntersectionTurned() {
        return lastIntersectionTurned;
    }

    public void setLastIntersectionTurned(Intersection intersection) {
        this.lastIntersectionTurned = intersection;
    }

    public void startTurn(Vector2D p0, Vector2D p1, Vector2D p2, Lane targetLane, Intersection intersection) {
        this.turnP0 = p0;
        this.turnP1 = p1;
        this.turnP2 = p2;
        this.turnTargetLane = targetLane;
        this.lastIntersectionTurned = intersection;
        this.turnT = 0.0;
        this.isTurning = true;
    }

    public void completeTurn(Lane targetLane) {
        if (this.lane != null) {
            this.lane.removeVehicle(this);
        }
        this.lane = targetLane;
        this.originalLane = targetLane;
        targetLane.addVehicle(this);

        Vector2D start = targetLane.getStart();
        Vector2D end = targetLane.getEnd();
        this.angle = MathUtils.angleTo(start, end);

        // Align to lane
        Vector2D ns = targetLane.getStart();
        Vector2D ne = targetLane.getEnd();
        double lx = ne.getX() - ns.getX();
        double ly = ne.getY() - ns.getY();
        double lenSq = lx * lx + ly * ly;

        double t = 0.5;
        if (lenSq > 1) {
            t = ((position.getX() - ns.getX()) * lx
                    + (position.getY() - ns.getY()) * ly) / lenSq;
            t = MathUtils.clamp(t, 0.0, 1.0);
        }
        position.setX(ns.getX() + t * lx);
        position.setY(ns.getY() + t * ly);

        // Align to the correct sub-lane side (left for 4-wheelers, right for
        // 2-wheelers)
        this.targetLateralOffset = isFourWheeler() ? -18.0 : 18.0;
        this.lateralOffset = this.targetLateralOffset;
        double perpRad = Math.toRadians(this.angle + 90);
        position.setX(position.getX() + Math.cos(perpRad) * lateralOffset);
        position.setY(position.getY() + Math.sin(perpRad) * lateralOffset);

        this.isTurning = false;
        this.turnP0 = null;
        this.turnP1 = null;
        this.turnP2 = null;
        this.turnDecision = TurnDecision.STRAIGHT;
    }

    public void pushBack(double dist) {
        if (isTurning) {
            if (turnP0 != null && turnP1 != null && turnP2 != null) {
                double turnLength = MathUtils.distance(turnP0, turnP1) + MathUtils.distance(turnP1, turnP2);
                if (turnLength < 5.0)
                    turnLength = 5.0;
                this.turnT = Math.max(0.0, this.turnT - dist / turnLength);
                // Recalculate position immediately so it takes effect
                double omt = 1.0 - turnT;
                double x = omt * omt * turnP0.getX() + 2 * omt * turnT * turnP1.getX() + turnT * turnT * turnP2.getX();
                double y = omt * omt * turnP0.getY() + 2 * omt * turnT * turnP1.getY() + turnT * turnT * turnP2.getY();
                double perpRad = Math.toRadians(this.angle + 90);
                position.setX(x + Math.cos(perpRad) * lateralOffset);
                position.setY(y + Math.sin(perpRad) * lateralOffset);
            }
        } else {
            double radians = Math.toRadians(this.angle);
            position.setX(position.getX() - Math.cos(radians) * dist);
            position.setY(position.getY() - Math.sin(radians) * dist);
        }
    }

    public boolean isFourWheeler() {
        String type = getTypeName();
        return "car".equals(type) || "ambulance".equals(type) || "firetruck".equals(type);
    }

    public abstract String getTypeName();
}