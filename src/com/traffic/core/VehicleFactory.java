package com.traffic.core;

import com.traffic.drivers.*;

/**
 * Factory Pattern: tạo xe mà không để code bên ngoài
 * phụ thuộc vào class cụ thể (Car, Ambulance...).
 *
 * Muốn thêm xe mới → chỉ thêm 1 case ở đây.
 * Mọi chỗ khác gọi VehicleFactory.create() không cần đổi.
 */
public class VehicleFactory {

    /**
     * Tạo xe theo loại với driver cụ thể.
     */
    public static Vehicle create(String type, double x, double y, double angle, IDriver driver) {
        Vehicle v = switch (type.toLowerCase()) {
            case "car"       -> new Car(x, y, driver);
            case "motorcycle"-> new Motorcycle(x, y, driver);
            case "bicycle"   -> new Bicycle(x, y, driver);
            case "ambulance" -> new Ambulance(x, y, driver);
            case "firetruck" -> new FireTruck(x, y, driver);
            default -> throw new IllegalArgumentException("Loại xe không hợp lệ: " + type);
        };
        v.setAngle(angle);
        return v;
    }

    /**
     * Tạo xe theo loại với driver mặc định.
     */
    public static Vehicle create(String type, double x, double y, double angle) {
        IDriver defaultDriver = switch (type.toLowerCase()) {
            case "ambulance", "firetruck" -> new EmergencyDriver();
            default -> new NormalDriver();
        };
        return create(type, x, y, angle, defaultDriver);
    }

    /** Tạo xe với góc mặc định 0 */
    public static Vehicle create(String type, double x, double y) {
        return create(type, x, y, 0);
    }
}