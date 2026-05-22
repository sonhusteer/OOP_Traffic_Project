package com.traffic.map;

/** Loại 2: Không hiện số giây — chỉ hiện màu đèn */
public class NoCountdownLight extends TrafficLight {

    public NoCountdownLight(int greenTime, int redTime, double x, double y) {
        super(greenTime, redTime, x, y);
    }

    @Override
    public String getDisplay() {
        return ""; // không hiện số nào cả
    }

}
