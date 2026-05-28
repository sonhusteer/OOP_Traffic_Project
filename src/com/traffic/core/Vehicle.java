package com.traffic.core;

import com.traffic.map.Lane;
import com.traffic.map.TrafficLight;

/**
 * Lớp trừu tượng cho mọi phương tiện.
 *
 * Nguyên tắc:
 *  - Chỉ chứa DỮ LIỆU VẬT LÝ (vị trí, tốc độ, góc)
 *  - KHÔNG chứa bất kỳ logic vẽ nào (tách biệt hoàn toàn)
 *  - Hành vi lái xe được ủy quyền cho IDriver (Strategy Pattern)
 */
public abstract class Vehicle {

    protected Vector2D position;
    protected double   speed;
    protected double   angle;
    protected IDriver  driver;

    protected String  name;
    protected double  width;
    protected double  height;
    protected boolean isPriority;

    // Làn đường hiện tại
    protected Lane lane;

    public Vehicle(double x, double y, double speed, IDriver driver) {
        this.position   = new Vector2D(x, y);
        this.speed      = speed;
        this.angle      = 0;
        this.driver     = driver;
        this.isPriority = false;
    }

    // ── Gắn xe vào làn đường ─────────────────────────────────────────────

    /**
     * Gắn xe vào làn — tự đặt vị trí ban đầu tại điểm đầu của làn.
     * Theo pattern MainApp: gọi setLane() trước, rồi setLaneStartOffset() sau.
     */
    public void setLane(Lane lane) {
        this.lane = lane;
        if (lane != null) {
            lane.addVehicle(this);
            // Đặt vị trí tại điểm đầu của làn
            Vector2D start = lane.getStart();
            this.position.setX(start.getX());
            this.position.setY(start.getY());
            // Tự tính góc di chuyển theo hướng làn
            Vector2D end = lane.getEnd();
            this.angle = MathUtils.angleTo(start, end);
        }
    }

    /**
     * Dịch chuyển vị trí hiện tại thêm offset — gọi SAU setLane().
     * Dùng để tạo khoảng cách ban đầu giữa các xe cùng làn.
     *
     * Ví dụ: setLaneStartOffset(-80, 0) → lùi 80px về phía sau xe trước (làn ngang)
     */
    public void setLaneStartOffset(double dx, double dy) {
        this.position.setX(this.position.getX() + dx);
        this.position.setY(this.position.getY() + dy);
    }

    // ── Logic cập nhật vật lý ─────────────────────────────────────────────

    /** final: không ai được override — đảm bảo vật lý nhất quán */
    public final void update(double deltaTime) {
        double radians = Math.toRadians(this.angle);
        position.setX(position.getX() + Math.cos(radians) * speed * deltaTime);
        position.setY(position.getY() + Math.sin(radians) * speed * deltaTime);
    }

    /** Ủy quyền quyết định lái cho driver */
    public void makeDecision(TrafficLight nearestLight) {
        if (driver != null) {
            driver.makeDecision(this, nearestLight);
        }
    }

    // ── Getters / Setters ─────────────────────────────────────────────────

    public Vector2D getPosition()        { return position;    }
    public double   getSpeed()           { return speed;       }
    public void     setSpeed(double s)   { this.speed = s;     }
    public double   getAngle()           { return angle;       }
    public void     setAngle(double a)   { this.angle = a;     }
    public String   getName()            { return name;        }
    public double   getWidth()           { return width;       }
    public double   getHeight()          { return height;      }
    public boolean  isPriority()         { return isPriority;  }

    public Lane getLane() { return lane; }

    /** Mỗi loại xe trả về tên kiểu để Renderer chọn ảnh đúng */
    public abstract String getTypeName();

    public abstract void setX(double x);

    public abstract void setY(double y);
}