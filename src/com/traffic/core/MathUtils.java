package com.traffic.core;

// Tien ich tinh toan toa do, khoang cach, huong di chuyen.
public class MathUtils {

    // Tinh khoang cach Euclid giua 2 vector.
    public static double distance(Vector2D a, Vector2D b) {
        double dx = b.getX() - a.getX();
        double dy = b.getY() - a.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    // Tinh goc (degree) tu diem 'from' huong den diem 'to'.
    // 0 do = huong phai, goc duong quay theo chieu tang cua truc Y man hinh.
    public static double angleTo(Vector2D from, Vector2D to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        return Math.toDegrees(Math.atan2(dy, dx));
    }

    // Noi suy tuyen tinh giua 2 gia tri.
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // Gioi han gia tri trong khoang [min, max].
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // Kiem tra 2 vi tri co gan nhau khong trong nguong threshold.
    public static boolean isNear(Vector2D a, Vector2D b, double threshold) {
        return distance(a, b) <= threshold;
    }

    /**
     * Di chuyen current tien ve target nhung moi lan khong vuot qua maxDelta.
     *
     * Ham nay chua duoc dung nhieu o Tang 1, nhung Tang 2 se dung de lam
     * lateralOffset di chuyen mem dan ve targetLateralOffset.
     */
    public static double moveTowards(double current, double target, double maxDelta) {
        if (maxDelta < 0.0) {
            throw new IllegalArgumentException("maxDelta must be non-negative");
        }
        if (Math.abs(target - current) <= maxDelta) {
            return target;
        }
        return current + Math.signum(target - current) * maxDelta;
    }
}
