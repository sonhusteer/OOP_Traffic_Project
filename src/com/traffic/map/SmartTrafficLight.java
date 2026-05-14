package com.traffic.map;

import java.util.List;

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

    public SmartTrafficLight(int greenTime, int yellowTime, int redTime, double x, double y) {
        super(greenTime, yellowTime, redTime, x);
    }

    public SmartTrafficLight(List<Lane> allLanes) {
        super(allLanes);
    }

    @Override
    public String getDisplay() {
        String stateName = state.name(); // "RED", "YELLOW", "GREEN"

        if (timeLeft <= SHOW_THRESHOLD) {
            return stateName + " - " + timeLeft; // vd: "GREEN - 8"
        }
        return stateName; // vd: "GREEN"
    }

    @Override
    public void tick() {
        if (timeLeft > 0) {
            timeLeft--;
        } else {
            String currentColor = state.name();
            // Logic đổi màu theo vòng lặp: Đỏ -> Xanh -> Vàng -> Đỏ
            if (currentColor.equalsIgnoreCase("RED")) {
                state = Enum.valueOf(state.getDeclaringClass(), "GREEN");
                timeLeft = 15; // Thời gian đèn xanh
            } else if (currentColor.equalsIgnoreCase("GREEN")) {
                state = Enum.valueOf(state.getDeclaringClass(), "YELLOW");
                timeLeft = 3;  // Thời gian đèn vàng
            } else {
                state = Enum.valueOf(state.getDeclaringClass(), "RED");
                timeLeft = 15; // Quay lại đèn đỏ
            }
        }
    }

    @Override
    public boolean isRed() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isRed'");
    }

    @Override
    public boolean isYellow() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isYellow'");
    }
}