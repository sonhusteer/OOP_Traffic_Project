package com.traffic.core;

// Tien ich tinh toan toa do, khoang cach, huong di chuyen
public class MathUtils {

    // Tinh khoang cach Euclid giua 2 vector
    public static double distance(Vector2D a, Vector2D b) {
        double dx = b.getX() - a.getX();
        double dy = b.getY() - a.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    // Tinh goc (degree) tu diem 'from' huong den diem 'to'
    // 0 do = huong phai, tang theo chieu toa do man hinh cua JavaFX
    public static double angleTo(Vector2D from, Vector2D to) {
        double dx = to.getX() - from.getX();
        return Math.toDegrees(Math.atan2(to.getY() - from.getY(), dx));
    }

    public static double dot(Vector2D a, Vector2D b) {
        return a.getX() * b.getX() + a.getY() * b.getY();
    }

    public static Vector2D direction(Vector2D from, Vector2D to) {
        return to.subtract(from).normalized();
    }

    // Noi suy tuyen tinh giua 2 gia tri
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // Gioi han gia tri trong khoang [min, max]
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // Kiem tra 2 vi tri co gan nhau khong (trong nguong threshold)
    public static boolean isNear(Vector2D a, Vector2D b, double threshold) {
        return distance(a, b) <= threshold;
    }
}
