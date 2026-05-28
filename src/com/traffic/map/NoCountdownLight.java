package com.traffic.map;

/** Loại 2: Không hiện số đếm ngược — chỉ đổi màu */
public class NoCountdownLight extends TrafficLight {

    public NoCountdownLight(double greenTime, double redTime, double x, double y) {
        super(greenTime, redTime, x, y);
    }

    @Override
    public String getDisplay() {
        return "";
    }
}
