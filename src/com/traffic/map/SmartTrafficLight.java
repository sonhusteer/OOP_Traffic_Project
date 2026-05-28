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

    /** Constructor cho Main.java: SmartTrafficLight(greenTime, yellowTime, redTime, x, y) */
    public SmartTrafficLight(int greenTime, int yellowTime, int redTime, double x, double y) {
        super(greenTime, yellowTime, redTime, x, y);
    }

    /** Constructor rút gọn (không yellowTime riêng — dùng mặc định 3s) */
    public SmartTrafficLight(double greenTime, double redTime, double x, double y) {
        super(greenTime, redTime, x, y);
    }

    @Override
    public String getDisplay() {
        String stateName = state.name();

        if (timeLeft <= SHOW_THRESHOLD) {
            return stateName + " - " + (int) Math.ceil(timeLeft);
        }
        return stateName;
    }
}