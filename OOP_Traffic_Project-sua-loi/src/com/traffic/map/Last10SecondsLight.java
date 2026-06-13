package com.traffic.map;

public class Last10SecondsLight extends TrafficLight {

    private static final int SHOW_THRESHOLD = 10;

    public Last10SecondsLight(double greenTime, double redTime, double x, double y) {
        super(greenTime, redTime, x, y);
    }

    @Override
    public String getDisplay() {
        return (timeLeft <= SHOW_THRESHOLD) ? String.valueOf((int) Math.ceil(timeLeft)) : "";
    }
}