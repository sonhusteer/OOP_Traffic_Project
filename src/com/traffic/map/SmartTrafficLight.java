package com.traffic.map;

/**
 * Loại 4: Đèn thông minh — hiện màu + số giây khi còn ≤ 10 giây.
 * Khác Last10SecondsLight: hiện thêm tên trạng thái (RED/GREEN...).
 *
 * Ví dụ getDisplay():
 *   Khi còn 25 giây  → "GREEN"
 *   Khi còn 8 giây   → "GREEN - 8"
 */
public class SmartTrafficLight extends TrafficLight {

    private static final int SHOW_THRESHOLD = 10;

    public SmartTrafficLight(int greenTime, int redTime, double x, double y) {
        super(greenTime, redTime, x, y); 
    }

    @Override
    public String getDisplay() {
        String stateName = state.name(); // "RED", "YELLOW", "GREEN"

        if (timeLeft <= SHOW_THRESHOLD) {
            return stateName + " - " + timeLeft; // vd: "GREEN - 8"
        }
        return stateName; // vd: "GREEN"
    }
}