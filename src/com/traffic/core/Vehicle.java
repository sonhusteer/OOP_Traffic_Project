package com.traffic.core;

import com.traffic.map.Lane;
import com.traffic.map.TrafficLight;

/**
 * Lop truong tuong cho moi phuong tien.
 *
 * Tang 2 clean-code:
 * - Khi xe co Lane, nguon su that cua vi tri la lane + progress + lateralOffset.
 * - position x/y chi la ket qua dong bo de renderer ve xe va de code cu doc.
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

    // Lane vat ly hien tai, lane nha va lane cu khi dang chuyen lane vat ly.
    protected Lane lane;
    protected Lane homeLane;
    protected Lane originalLane;

    /** Vi tri doc theo LanePath, tinh bang pixel tu dau lane. */
    protected double progress = 0.0;

    /** Do lech ngang so voi tim lane: am = trai, duong = phai theo huong chay. */
    protected double lateralOffset = 0.0;

    /** Offset ngang ma xe dang muon tien toi. Tang sau se dung cho ne/vuot/nhuong. */
    protected double targetLateralOffset = 0.0;

    /** Offset mac dinh khi spawn. Sau khi ne/vuot, xe se quay ve offset nay. */
    protected double preferredLateralOffset = 0.0;

    /** Tang 5: thao tac dich ngang hien tai trong cung Lane. */
    protected LateralManeuver currentManeuver = LateralManeuver.none();

    protected static final double LATERAL_SHIFT_SPEED = 45.0;

    // State chuyen lane vat ly. Giu tam de driver cu chua bi vo.
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

    /** API cu: gan xe vao dau lane, nam tren tim lane. */
    public void setLane(Lane lane) {
        setLane(lane, 0.0, 0.0);
    }

    /** Gan xe vao lane tai progress nhat dinh, van nam tren tim lane. */
    public void setLane(Lane lane, double progress) {
        setLane(lane, progress, 0.0);
    }

    /**
     * Gan xe vao lane tai progress va track ngang.
     * Tang 3 se dung method nay de spawn 2 xe ngang hang trong cung lane.
     */
    public void setLane(Lane lane, double progress, int trackIndex) {
        if (lane == null) {
            throw new IllegalArgumentException("Lane cannot be null");
        }
        setLane(lane, progress, lane.getTrackOffset(trackIndex));
    }

    /** Gan xe vao lane tai progress va lateralOffset cu the. */
    public void setLane(Lane lane, double progress, double lateralOffset) {
        if (lane == null) {
            throw new IllegalArgumentException("Lane cannot be null");
        }

        detachFromCurrentLaneReferences();

        this.lane = lane;
        this.homeLane = lane;
        this.originalLane = lane;
        this.targetLane = null;
        this.currentManeuver = LateralManeuver.none();
        this.isChangingLane = false;
        this.laneChangeElapsed = 0.0;
        this.laneChangeCooldown = 0.0;

        this.progress = progress;
        this.preferredLateralOffset = lane.clampOffset(this, lateralOffset);
        this.lateralOffset = this.preferredLateralOffset;
        this.targetLateralOffset = this.preferredLateralOffset;

        lane.addVehicle(this);
        syncPositionFromLane();
    }

    /** Goi khi engine xoa xe. Neu khong reset, Vehicle object cu van giu lane cu. */
    public void detachFromLane() {
        this.lane = null;
        this.homeLane = null;
        this.originalLane = null;
        this.targetLane = null;
        this.currentManeuver = LateralManeuver.none();
        this.isChangingLane = false;
        this.laneChangeElapsed = 0.0;
        this.laneChangeCooldown = 0.0;
        this.progress = 0.0;
        this.lateralOffset = 0.0;
        this.targetLateralOffset = 0.0;
        this.preferredLateralOffset = 0.0;
    }

    /**
     * API cu. Tu tang 2 tro di khong nen dung de spawn xe tren lane nua.
     * Method van duoc giu de code cu khong vo; sau khi dich position, progress
     * se duoc do lai tu vi tri moi.
     */
    @Deprecated
    public void setLaneStartOffset(double offsetX, double offsetY) {
        position.setX(position.getX() + offsetX);
        position.setY(position.getY() + offsetY);
        syncLaneStateFromCurrentPosition();
    }

    /**
     * Go xe khoi tat ca Lane va reservation lien quan.
     * TrafficEngine goi khi remove/clear xe de tranh xe ma con nam trong Lane.
     */
    public void detachFromLanes() {
        detachFromCurrentLaneReferences();

        lane = null;
        homeLane = null;
        originalLane = null;
        targetLane = null;
        currentManeuver = LateralManeuver.none();
        isChangingLane = false;
        laneChangeElapsed = 0.0;
        laneChangeCooldown = 0.0;
        progress = 0.0;
        lateralOffset = 0.0;
        targetLateralOffset = 0.0;
        preferredLateralOffset = 0.0;
        yieldMode = YieldMode.NONE;
    }

    private void detachFromCurrentLaneReferences() {
        if (lane != null) {
            lane.release(this);
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
    }

    // ------------------------------------------------------------------
    // Cap nhat vat ly.
    // ------------------------------------------------------------------

    public final void update(double deltaTime) {
        if (laneChangeCooldown > 0) {
            laneChangeCooldown = Math.max(0.0, laneChangeCooldown - deltaTime);
        }

        // Xe khong gan lane thi van chay tu do theo x/y nhu code cu.
        if (lane == null) {
            double radians = Math.toRadians(this.angle);
            position.setX(position.getX() + Math.cos(radians) * speed * deltaTime);
            position.setY(position.getY() + Math.sin(radians) * speed * deltaTime);
            return;
        }

        // Tam thoi giu lane-change vat ly cu de driver hien tai van chay.
        // Cac tang sau se khong dung startLaneChange() cho vuot/nhuong nua.
        if (isChangingLane && originalLane != null && targetLane != null) {
            updatePhysicalLaneChange(deltaTime);
            return;
        }

        // Tang 2: xe co lane thi di tien bang progress doc theo LanePath.
        progress += speed * deltaTime;

        // lateralOffset di chuyen mem ve target, chuan bi cho Tang 5 LateralManeuver.
        lateralOffset = MathUtils.moveTowards(
                lateralOffset,
                targetLateralOffset,
                LATERAL_SHIFT_SPEED * deltaTime
        );
        if (Math.abs(lateralOffset - targetLateralOffset) < 0.5) {
            lateralOffset = targetLateralOffset;
            if (currentManeuver.isActive()) {
                currentManeuver = LateralManeuver.none();
                lane.release(this);
            }
            isChangingLane = false;
        } else {
            isChangingLane = true;
        }

        syncPositionFromLane();
    }

    /** Chuyen lane vat ly cu, chi de tuong thich truoc khi refactor driver. */
    private void updatePhysicalLaneChange(double deltaTime) {
        double oldProgress = progress + speed * deltaTime;
        double newProgress = targetLane.getProgress(position) + speed * deltaTime;

        Vector2D oldPoint = originalLane.getPointAtProgress(oldProgress, lateralOffset);
        Vector2D newPoint = targetLane.getPointAtProgress(newProgress, 0.0);

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

        progress = oldProgress;

        if (t >= 1.0) {
            finishLaneChange();
        }
    }

    /** Dong bo position/angle tu lane + progress + lateralOffset. */
    private void syncPositionFromLane() {
        if (lane == null) {
            return;
        }

        lateralOffset = lane.clampOffset(this, lateralOffset);
        targetLateralOffset = lane.clampOffset(this, targetLateralOffset);
        preferredLateralOffset = lane.clampOffset(this, preferredLateralOffset);

        Vector2D p = lane.getPointAtProgress(progress, lateralOffset);
        position.setX(p.getX());
        position.setY(p.getY());
        angle = lane.getAngleAtProgress(progress);
    }

    /**
     * Tinh nguoc progress + lateralOffset tu position hien tai.
     * Dung de tuong thich voi code cu con sua position truc tiep.
     */
    public void syncLaneStateFromCurrentPosition() {
        if (lane == null) {
            return;
        }

        progress = lane.getProgress(position);
        Vector2D center = lane.getPointAtProgress(progress);
        double laneAngle = Math.toRadians(lane.getAngleAtProgress(progress));

        double rightNormalX = -Math.sin(laneAngle);
        double rightNormalY = Math.cos(laneAngle);
        double dx = position.getX() - center.getX();
        double dy = position.getY() - center.getY();
        double signedOffset = dx * rightNormalX + dy * rightNormalY;

        lateralOffset = lane.clampOffset(this, signedOffset);
        targetLateralOffset = lateralOffset;
        preferredLateralOffset = lateralOffset;
        syncPositionFromLane();
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
        currentManeuver = LateralManeuver.none();
        isChangingLane = false;
        laneChangeElapsed = 0.0;
        laneChangeCooldown = LANE_CHANGE_COOLDOWN;

        if (lane != null) {
            progress = lane.getProgress(position);
            lateralOffset = 0.0;
            targetLateralOffset = 0.0;
            preferredLateralOffset = 0.0;
            currentManeuver = LateralManeuver.none();
            syncPositionFromLane();
        }
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
    // Chuyen lane vat ly.
    // ------------------------------------------------------------------

    /**
     * Chuyen sang Lane khac ve mat vat ly.
     *
     * @deprecated Tang clean-code moi khong dung method nay cho vuot xe/nhuong
     * xe uu tien nua. No chi nen dung cho routing that su trong tuong lai.
     */
    @Deprecated
    public boolean startLaneChange(Lane newLane) {
        if (newLane == null || this.lane == null) return false;
        if (this.lane == newLane) return false;
        if (isChangingLane) return false;
        if (laneChangeCooldown > 0) return false;

        double currentProgress = progress;
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

    /** Xe dang o ngoai lane nha? Giu de code driver cu van chay trong giai do chuyen tiep. */
    public boolean isAwayFromHome() {
        return homeLane != null && lane != homeLane && !isChangingLane;
    }

    /** Xe dang lech khoi offset spawn ban dau trong cung lane. */
    public boolean isAwayFromPreferredOffset() {
        return Math.abs(lateralOffset - preferredLateralOffset) > 2.0
            || Math.abs(targetLateralOffset - preferredLateralOffset) > 2.0;
    }

    /** Dat muc tieu lech ngang trong cung lane ma khong gan loai maneuver. */
    public void setTargetLateralOffset(double offset) {
        if (lane == null) {
            targetLateralOffset = offset;
            lateralOffset = offset;
            return;
        }
        targetLateralOffset = lane.clampOffset(this, offset);
        if (Math.abs(targetLateralOffset - lateralOffset) > 0.5) {
            isChangingLane = true;
        }
    }

    /**
     * Tang 5: xin thuc hien mot thao tac dich ngang trong cung Lane.
     * Vehicle chi nhan lenh; viec kiem tra an toan thuoc SideShiftPlanner
     * va LaneOccupancy.
     */
    public boolean requestManeuver(LateralManeuver maneuver) {
        if (lane == null || maneuver == null || !maneuver.isActive()) {
            return false;
        }
        if (currentManeuver.isActive()
                && currentManeuver.getPriority() > maneuver.getPriority()) {
            return false;
        }

        double offset = lane.clampOffset(this, maneuver.getTargetOffset());
        if (Math.abs(offset - targetLateralOffset) < 0.5
                && currentManeuver.getType() == maneuver.getType()) {
            // Da dang thuc hien dung maneuver nay roi; coi nhu request thanh cong
            // de driver khong hieu nham la khong the ne/vuot.
            return true;
        }

        if (Math.abs(offset - targetLateralOffset) < 0.5
                && Math.abs(offset - lateralOffset) < 0.5) {
            return false;
        }

        lane.release(this);
        currentManeuver = maneuver;
        targetLateralOffset = offset;
        isChangingLane = Math.abs(targetLateralOffset - lateralOffset) > 0.5;
        lane.reserve(this);
        return true;
    }

    /** Huy maneuver hien tai va giai phong reservation ngang. */
    public void clearCurrentManeuver() {
        if (lane != null) {
            lane.release(this);
        }
        currentManeuver = LateralManeuver.none();
        if (targetLane == null) {
            isChangingLane = false;
        }
    }

    /** Quay ve offset ban dau khi spawn. */
    public boolean returnToPreferredOffset() {
        return requestManeuver(
                LateralManeuver.returnToTrack(preferredLateralOffset)
        );
    }

    public boolean isPhysicalLaneChanging() {
        return isChangingLane && originalLane != null && targetLane != null;
    }

    public boolean isLateralManeuverActive() {
        return currentManeuver != null && currentManeuver.isActive();
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

    public double getProgress() { return progress; }

    public void setProgress(double progress) {
        this.progress = progress;
        syncPositionFromLane();
    }

    public double getLateralOffset() { return lateralOffset; }
    public double getTargetLateralOffset() { return targetLateralOffset; }
    public double getPreferredLateralOffset() { return preferredLateralOffset; }
    public LateralManeuver getCurrentManeuver() { return currentManeuver; }
    public boolean hasActiveManeuver() { return currentManeuver.isActive(); }

    public abstract String getTypeName();
}
