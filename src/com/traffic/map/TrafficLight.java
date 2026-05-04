package com.traffic.map;

import com.traffic.core.Vector2D;

public abstract class TrafficLight {
    protected String color; // RED, YELLOW, GREEN 
    protected int duration; // Tổng thời gian của màu hiện tại
    protected int timeLeft; // Số giây còn lại
    protected Vector2D position;  // Vị trí của đèn giao thông trên bản đồ

    public TrafficLight(int duration) {
        this.duration = duration;
        this.timeLeft = duration;
        this.color = "RED"; // Mặc định bắt đầu bằng đèn đỏ
        this.position = new Vector2D(0, 0); // Vị trí mặc định, có thể được thiết lập sau
    }

    // Hàm cập nhật thời gian mỗi giây [cite: 29]
    public void tick() {
        if (timeLeft > 0) {
            timeLeft--;
        }
    }
    public boolean isRed()    { return "RED".equals(color); }
    public boolean isYellow() { return "YELLOW".equals(color); }
    public boolean isGreen()  { return "GREEN".equals(color); }

    public Vector2D getPosition() { return position; }  // THEM

    public abstract String getDisplay(); // Mỗi loại đèn sẽ có cách hiển thị riêng 
}