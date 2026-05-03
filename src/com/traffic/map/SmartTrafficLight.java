package com.traffic.map;

public class SmartTrafficLight extends TrafficLight {
    public SmartTrafficLight(int duration) {
        super(duration);
    }

    @Override
    public String getDisplay() {
        // Nếu còn từ 10 giây trở xuống thì hiện số, ngược lại chỉ hiện màu 
        if (timeLeft <= 10) {
            return color + " - " + timeLeft;
        }
        return color; 
    }
}