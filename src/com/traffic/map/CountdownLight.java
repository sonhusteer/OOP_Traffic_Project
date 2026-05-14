package com.traffic.map;

/** Loại 1: Luôn hiện số giây đếm ngược */
public class CountdownLight extends TrafficLight {

    public CountdownLight(int greenTime, int redTime, double x, double y) {
        super(greenTime, redTime, x, y);
    }

    @Override
    public String getDisplay() {
        return String.valueOf(timeLeft); // luôn hiện: "30", "15", "3"...
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