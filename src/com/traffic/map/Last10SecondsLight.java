package com.traffic.map;

/** Chi hien countdown khi con tu 10 giay tro xuong. */
public class Last10SecondsLight extends TrafficLight {

    private static final int SHOW_THRESHOLD = 10;

    public Last10SecondsLight(double greenTime, double redTime, double x, double y) {
        super(greenTime, redTime, x, y);
    }

    @Override
    public String getDisplay() {
        int seconds = getDisplayTimeSeconds();
        return (seconds <= SHOW_THRESHOLD) ? String.valueOf(seconds) : "";
    }
}
