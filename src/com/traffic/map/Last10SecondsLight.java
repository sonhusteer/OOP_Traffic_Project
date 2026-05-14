package com.traffic.map;

/** Loại 3: Chỉ hiện số đếm ngược khi còn ≤ 10 giây */
public class Last10SecondsLight extends TrafficLight {

    private static final int SHOW_THRESHOLD = 10;

    public Last10SecondsLight(int greenTime, int redTime, double x, double y) {
        super(greenTime, redTime, x, y);
    }

    @Override
    public String getDisplay() {
        // Đa hình: cùng method getDisplay(), hành vi khác CountdownLight
        return (timeLeft <= SHOW_THRESHOLD) ? String.valueOf(timeLeft) : "";
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
