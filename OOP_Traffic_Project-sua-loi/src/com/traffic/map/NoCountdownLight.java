package com.traffic.map;

public class NoCountdownLight extends TrafficLight {

    public NoCountdownLight(double greenTime, double redTime, double x, double y) {
        super(greenTime, redTime, x, y);
    }

    @Override
    public String getDisplay() {
        return ""; 
    }
}