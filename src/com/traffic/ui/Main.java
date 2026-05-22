package com.traffic.ui;

import com.traffic.core.TrafficEngine;
import com.traffic.core.Vehicle;
import com.traffic.core.VehicleFactory;
import com.traffic.map.Lane;
import com.traffic.map.TrafficLight;
import com.traffic.map.CountdownLight;
import com.traffic.map.NoCountdownLight;
import com.traffic.map.SmartTrafficLight;
import com.traffic.map.Last10SecondsLight;

import javax.swing.Timer;
import javax.swing.JFrame;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    public static void main(String[] args) {
        
        // 1. Tạo danh sách các Làn (Lanes) phục vụ Ngã Tư lớn
        List<Lane> allLanes = new ArrayList<>();
        
        // ──── THIẾT LẬP HỆ THỐNG ĐÈN GIAO THÔNG ĐỒNG BỘ ────
        // Pha ngang: Đèn Xanh (10s) -> Đèn Vàng (3s) -> Đèn Đỏ (13s). Ban đầu cho chạy Xanh ngay.
        TrafficLight lightHoriz1 = new CountdownLight(10, 13, 370, 280); 
        lightHoriz1.setInitialState(TrafficLight.State.GREEN, 10);
        
        TrafficLight lightHoriz2 = new NoCountdownLight(10, 13, 430, 320);
        lightHoriz2.setInitialState(TrafficLight.State.GREEN, 10);
        
        // Pha dọc: Đỏ (13s) -> Xanh (10s) -> Vàng (3s). Ban đầu cho đỗ Đỏ để chờ pha ngang đi xong.
        TrafficLight lightVert1 = new SmartTrafficLight(10, 3, 13, 380, 270);
        lightVert1.setInitialState(TrafficLight.State.RED, 13);
        
        TrafficLight lightVert2 = new Last10SecondsLight(10, 13, 420, 330);
        lightVert2.setInitialState(TrafficLight.State.RED, 13);

        // ──── THIẾT LẬP 4 LÀN ĐƯỜNG NGÃ TƯ ────
        // Làn 1: Ngang từ Trái sang Phải (y = 280)
        Lane road1 = new Lane(50, 280, 750, 280, lightHoriz1);
        road1.addwaypoint(50, 280);
        road1.addwaypoint(370, 280); // Vạch chờ đèn
        road1.addwaypoint(750, 280);
        allLanes.add(road1);

        // Làn 2: Ngang từ Phải sang Trái (y = 320)
        Lane road2 = new Lane(750, 320, 50, 320, lightHoriz2);
        road2.addwaypoint(750, 320);
        road2.addwaypoint(430, 320); // Vạch chờ đèn
        road2.addwaypoint(50, 320);
        allLanes.add(road2);

        // Làn 3: Dọc từ Trên xuống Dưới (x = 380)
        Lane road3 = new Lane(380, 50, 380, 550, lightVert1);
        road3.addwaypoint(380, 50);
        road3.addwaypoint(380, 270); // Vạch chờ đèn
        road3.addwaypoint(380, 550);
        allLanes.add(road3);

        // Làn 4: Dọc từ Dưới lên Trên (x = 420)
        Lane road4 = new Lane(420, 550, 420, 50, lightVert2);
        road4.addwaypoint(420, 550);
        road4.addwaypoint(420, 330); // Vạch chờ đèn
        road4.addwaypoint(420, 50);
        allLanes.add(road4);

        // 2. Thiết lập cửa sổ hiển thị JFrame
        JFrame frame = new JFrame("Bustling Crossroads - OOP Multi-Agent Traffic Simulator");
        MapRenderer renderer = new MapRenderer(allLanes, null); // lights sẽ được nạp qua engine
        // Gán nhãn debug để biết đèn nào đang GREEN/RED tương ứng làn nào
        renderer.setLightLabel(lightHoriz1, "Road1 →");
        renderer.setLightLabel(lightHoriz2, "Road2 ←");
        renderer.setLightLabel(lightVert1,  "Road3 ↓");
        renderer.setLightLabel(lightVert2,  "Road4 ↑");

        frame.add(renderer);
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);

        // 3. Khởi tạo Engine điều phối
        TrafficEngine engine = new TrafficEngine(renderer);
        engine.addTrafficLight(lightHoriz1);
        engine.addTrafficLight(lightHoriz2);
        engine.addTrafficLight(lightVert1);
        engine.addTrafficLight(lightVert2);

        // 4. Tạo nhiều loại phương tiện di chuyển 4 hướng
        // LÀN 1 (Từ trái sang phải, offset âm = lùi về bên trái so với waypoint đầu)
        Vehicle car1 = VehicleFactory.create("car", 0, 0, 0, new com.traffic.drivers.NormalDriver());
        car1.setLane(road1);
        engine.addVehicle(car1);

        Vehicle ambulance = VehicleFactory.create("ambulance", 0, 0, 0, new com.traffic.drivers.EmergencyDriver());
        ambulance.setLaneStartOffset(-80, 0); // 80px sau car1 (road1 đi sang phải → lùi về trái)
        ambulance.setLane(road1);
        engine.addVehicle(ambulance);

        // LÀN 2 (Từ phải sang trái, offset dương = lùi về bên phải so với waypoint đầu)
        Vehicle car2 = VehicleFactory.create("car", 0, 0, 0, new com.traffic.drivers.NormalDriver());
        car2.setLane(road2);
        engine.addVehicle(car2);

        Vehicle moto1 = VehicleFactory.create("motorcycle", 0, 0, 0, new com.traffic.drivers.AggressiveDriver());
        moto1.setLaneStartOffset(60, 0); // 60px sau car2 (road2 đi sang trái → lùi về phải)
        moto1.setLane(road2);
        engine.addVehicle(moto1);

        // LÀN 3 (Từ trên xuống dưới, offset âm = lùi về phía trên so với waypoint đầu)
        Vehicle car3 = VehicleFactory.create("car", 0, 0, 0, new com.traffic.drivers.NormalDriver());
        car3.setLane(road3);
        engine.addVehicle(car3);

        Vehicle bicycle = VehicleFactory.create("bicycle", 0, 0, 0, new com.traffic.drivers.NormalDriver());
        bicycle.setLaneStartOffset(0, -40); // 40px sau car3 (road3 đi xuống → lùi về phía trên)
        bicycle.setLane(road3);
        engine.addVehicle(bicycle);

        // LÀN 4 (Từ dưới lên trên, offset dương = lùi về phía dưới so với waypoint đầu)
        Vehicle firetruck = VehicleFactory.create("firetruck", 0, 0, 0, new com.traffic.drivers.EmergencyDriver());
        firetruck.setLane(road4);
        engine.addVehicle(firetruck);

        Vehicle moto2 = VehicleFactory.create("motorcycle", 0, 0, 0, new com.traffic.drivers.AggressiveDriver());
        moto2.setLaneStartOffset(0, 70); // 70px sau firetruck (road4 đi lên → lùi về phía dưới)
        moto2.setLane(road4);
        engine.addVehicle(moto2);

        // 5. Chạy mô phỏng mượt mà ở tần số 30ms (~33 FPS)
        Timer timer = new Timer(30, e -> {
            engine.tick(0.03); 
            engine.render();
            renderer.repaint();
        });
        
        timer.start();
        frame.setVisible(true);
    }
}