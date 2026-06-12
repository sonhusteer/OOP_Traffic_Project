package com.traffic.map;

/** Loai den luon hien so giay dem nguoc. */
public class CountdownLight extends TrafficLight {

    public CountdownLight(double greenTime, double redTime, double x, double y) {
        super(greenTime, redTime, x, y);
    }

    @Override
    public String getDisplay() {
        return String.valueOf(getDisplayTimeSeconds());
    }
}
