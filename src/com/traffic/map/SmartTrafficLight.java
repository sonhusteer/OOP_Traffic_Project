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
    @Override
public void tick() {
    if (timeLeft > 0) {
        timeLeft--;
    } else {
        // Logic đổi màu theo vòng lặp: Đỏ -> Xanh -> Vàng -> Đỏ
        if (color.equalsIgnoreCase("RED")) {
            color = "GREEN";
            timeLeft = 15; // Thời gian đèn xanh
        } else if (color.equalsIgnoreCase("GREEN")) {
            color = "YELLOW";
            timeLeft = 3;  // Thời gian đèn vàng
        } else {
            color = "RED";
            timeLeft = 15; // Quay lại đèn đỏ
        }
    }
}
}