package com.traffic.map;

/** Loại 1: Luôn hiện số giây đếm ngược */
public class CountdownLight extends TrafficLight {

    public CountdownLight(double greenTime, double redTime, double x, double y) {
        super(greenTime, redTime, x, y);
    }

    @Override
    public String getDisplay() {
        // (int) timeLeft cắt phần thập phân — luôn đúng
        // Math.ceil có thể bị sai do floating point: 9.0000001 → ceil = 10
        return String.valueOf((int) timeLeft);
    }
}
