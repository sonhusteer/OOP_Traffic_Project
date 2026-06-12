package com.traffic.map;

public class SmartTrafficLight extends TrafficLight {

    private static final int SHOW_THRESHOLD = 10;

    public SmartTrafficLight(double greenTime, double redTime, double x, double y) {
        super(greenTime, redTime, x, y); 
    }

    @Override
    public String getDisplay() {
        String stateName = state.name(); 

        if (timeLeft <= SHOW_THRESHOLD) {
            return stateName + " - " + (int) Math.ceil(timeLeft); 
        }
        return stateName; 
    }
}