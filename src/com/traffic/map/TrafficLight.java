package com.traffic.map;

public abstract class TrafficLight {
    protected String color; // RED, YELLOW, GREEN 
    protected int duration; // Tổng thời gian của màu hiện tại
    protected int timeLeft; // Số giây còn lại

    public TrafficLight(int duration) {
        this.duration = duration;
        this.timeLeft = duration;
        this.color = "RED"; // Mặc định bắt đầu bằng đèn đỏ
    }

    // Hàm cập nhật thời gian mỗi giây [cite: 29]
    public void tick() {
        if (timeLeft > 0) {
            timeLeft--;
        }
    }

    public abstract String getDisplay(); // Mỗi loại đèn sẽ có cách hiển thị riêng 
}