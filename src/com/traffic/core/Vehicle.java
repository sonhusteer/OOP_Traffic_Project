package com.traffic.core;

/**
 * Lớp trừu tượng cho mọi phương tiện.
 *
 * Nguyên tắc:
 *  - Chỉ chứa DỮ LIỆU VẬT LÝ (vị trí, tốc độ, góc)
 *  - KHÔNG chứa bất kỳ logic vẽ nào (tách biệt hoàn toàn)
 *  - Hành vi lái xe được ủy quyền cho IDriver (Strategy Pattern)
 */
import com.traffic.map.TrafficLight;
public abstract class Vehicle {

    protected Vector2D position;
    protected double speed;
    protected double angle;
    protected IDriver driver;

    // Thông tin nhận dạng — mỗi loại xe tự định nghĩa
    protected String name;
    protected double width;
    protected double height;
    protected boolean isPriority;

    public Vehicle(double x, double y, double speed, IDriver driver) {
        this.position  = new Vector2D(x, y);
        this.speed     = speed;
        this.angle     = 0;
        this.driver    = driver;
        this.isPriority = false;
    }

    // ── Logic cập nhật vật lý ──────────────────────────────────────────────

    /** Cập nhật tọa độ theo deltaTime — không ai được override */
    public final void update(double deltaTime) {
        double radians = Math.toRadians(this.angle);
        position.setX(position.getX() + Math.cos(radians) * speed * deltaTime);
        position.setY(position.getY() + Math.sin(radians) * speed * deltaTime);
    }

    /**
     * Gọi driver ra quyết định. TrafficEngine gọi method này —
     * không truy cập field driver trực tiếp (bảo vệ encapsulation).
     */
    public void makeDecision(TrafficLight nearestLight) {
        if (driver != null) {
            driver.makeDecision(this, nearestLight);
        }
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Vector2D getPosition()         { return position; }
    public double   getSpeed()            { return speed; }
    public void     setSpeed(double s)    { this.speed = s; }
    public double   getAngle()            { return angle; }
    public void     setAngle(double a)    { this.angle = a; }
    public String   getName()             { return name; }
    public double   getWidth()            { return width; }
    public double   getHeight()           { return height; }
    public boolean  isPriority()          { return isPriority; }

    /** Mỗi loại xe trả về tên kiểu để Renderer chọn ảnh đúng */
    public abstract String getTypeName();
}