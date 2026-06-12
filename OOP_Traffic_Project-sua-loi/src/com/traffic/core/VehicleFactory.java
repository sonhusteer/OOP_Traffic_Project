package com.traffic.core;

import com.traffic.drivers.*;
import com.traffic.vehicles.*;

/**
 * Factory Pattern: tạo xe mà không để code bên ngoài
 * phụ thuộc vào class cụ thể (Car, Ambulance...).
 *
 * Muốn thêm xe mới → chỉ thêm 1 case ở đây.
 * Mọi chỗ khác gọi VehicleFactory.create() không cần đổi.
 */
public class VehicleFactory {

    /**
     * Tạo xe theo loại.
     * @param type  "car" | "motorcycle" | "bicycle" | "ambulance" | "firetruck"
     * @param x     tọa độ x ban đầu
     * @param y     tọa độ y ban đầu
     * @param angle hướng ban đầu (độ, 0 = sang phải)
     */
    public static Vehicle create(String type, double x, double y, double angle) {
        Vehicle v = switch (type.toLowerCase()) {
            case "car"       -> new Car(x, y, new NormalDriver());
            case "motorcycle"-> new Motorcycle(x, y, new AggressiveDriver());
            case "bicycle"   -> new Bicycle(x, y, new NormalDriver());
            case "ambulance" -> new Ambulance(x, y, new EmergencyDriver());
            case "firetruck" -> new FireTruck(x, y, new EmergencyDriver());
            default -> throw new IllegalArgumentException("Loại xe không hợp lệ: " + type);
        };
        v.setAngle(angle);
        return v;
    }

    /** Tạo xe với góc mặc định 0 */
    public static Vehicle create(String type, double x, double y) {
        return create(type, x, y, 0);
    }
}