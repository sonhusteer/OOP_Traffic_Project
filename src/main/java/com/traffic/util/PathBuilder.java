package com.traffic.util;

public class PathBuilder {
    
    // Tính tọa độ X trên đường cong Bezier bậc 2 tại thời điểm t (từ 0.0 đến 1.0)
    public static double getBezierX(double p0x, double p1x, double p2x, double t) {
        return Math.pow(1 - t, 2) * p0x + 2 * (1 - t) * t * p1x + Math.pow(t, 2) * p2x;
    }

    // Tính tọa độ Y trên đường cong Bezier bậc 2 tại thời điểm t
    public static double getBezierY(double p0y, double p1y, double p2y, double t) {
        return Math.pow(1 - t, 2) * p0y + 2 * (1 - t) * t * p1y + Math.pow(t, 2) * p2y;
    }

    // Tính GÓC XOAY của vô lăng (Tiếp tuyến của đường cong) để đầu xe luôn hướng theo đường đi
    public static double getBezierAngle(double p0x, double p0y, double p1x, double p1y, double p2x, double p2y, double t) {
        double dx = 2 * (1 - t) * (p1x - p0x) + 2 * t * (p2x - p1x);
        double dy = 2 * (1 - t) * (p1y - p0y) + 2 * t * (p2y - p1y);
        return Math.toDegrees(Math.atan2(dy, dx));
    }
}