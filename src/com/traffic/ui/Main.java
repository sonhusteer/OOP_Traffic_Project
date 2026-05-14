package com.traffic.ui;

import com.traffic.map.Lane;
import com.traffic.map.SmartTrafficLight;
import com.traffic.map.TrafficLight;
import javax.swing.Timer; // BẮT BUỘC phải là javax.swing để chạy được với renderer.repaint()
import javax.swing.JFrame;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    public static void main(String[] args) {
        
        // 1. Tạo dữ liệu cho con đường (Lane)
        Lane road1 = new Lane(0, 0, 0, 0, null);
        road1.addwaypoint(50, 250);   // Điểm bắt đầu
        road1.addwaypoint(300, 250);  // Điểm giữa
        road1.addwaypoint(500, 100);  // Đường chéo lên

        List<Lane> allLanes = new ArrayList<>();
        allLanes.add(road1);

        // 2. Thiết lập cửa sổ hiển thị
        JFrame frame = new JFrame("Traffic Simulation - Sơn Project");
        TrafficLight smartLight = new SmartTrafficLight(allLanes); // Ví dụ tạo đèn giao thông thông minh
        MapRenderer renderer = new MapRenderer(allLanes, smartLight);

        frame.add(renderer);
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        // Tạo Timer để cập nhật trạng thái đèn mỗi giây (1000ms)
Timer timer = new Timer(1000, e -> {
    smartLight.tick(); // Giảm số giây còn lại
    
    // Nếu hết thời gian, bạn có thể thêm logic đổi màu ở đây
    // Ví dụ: if(smartLight.getTimeLeft() == 0) { ... }

    renderer.repaint(); // Yêu cầu vẽ lại bản đồ với số giây mới
});
timer.start(); // Bắt đầu chạy bộ đếm
        frame.setVisible(true);
    }
}