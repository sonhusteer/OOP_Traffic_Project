package com.traffic.map;

/** Loại đèn không hiển thị số đếm, chỉ hiển thị màu. */
public class NoCountdownLight extends TrafficLight {

    public NoCountdownLight(double greenTime, double redTime, double x, double y) {
        super(greenTime, redTime, x, y);
    }

    @Override
    public String getDisplay() {
        return "";
    }
}
