package com.traffic.engine;

import com.traffic.config.Constants;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import com.traffic.model.map.CityMap;
import com.traffic.model.map.IntersectionNode;
import java.util.ArrayList;
import java.util.List;
import com.traffic.model.traffic.TrafficLight;
import com.traffic.view.MapRenderer;
import com.traffic.model.map.RoadEdge;
import com.traffic.model.vehicle.*;

public class SimulationEngine extends AnimationTimer {
    
    private Canvas canvas;
    private GraphicsContext gc;
    private CityMap cityMap;
    private javafx.scene.control.Label vehicleCountLabel;

    public void setVehicleCountLabel(javafx.scene.control.Label label) {
        this.vehicleCountLabel = label;
    }
    
    // --- CAMERA & VIEW ---
    private double cameraX = 0;
    private double cameraY = 0;
    private double zoomScale = 1.0;

    // --- ENVIRONMENT ---
    private boolean isPaused = false;
    private boolean isDebugMode = false;
    private double timeOfDay = 12.0;
    private String currentMapType = "Ô Cờ (Grid)";
    private double[] rainX = new double[300];
    private double[] rainY = new double[300];

    // --- VEHICLES ---
    private final List<Vehicle> vehicles = new ArrayList<>();
    private boolean autoSpawnEnabled = true;
    private double spawnTimer = 0.0;

    // --- GRAPHIC ASSETS (generated once per map load) ---
    private List<MapRenderer.Decoration>  decorations  = new ArrayList<>();
    private List<MapRenderer.StreetLight> streetLights = new ArrayList<>();

    // --- RÀO CHẮN TÀU HỎA (chỉ dùng cho map Bách Khoa) ---
    private boolean railBarrierDown = false;  // rào đang hạ xuống?
    private double  railBarrierTimer = 0;     // giây đếm ngược
    private static final double BARRIER_OPEN_TIME   = 8.0;  // 8s rào mở
    private static final double BARRIER_CLOSED_TIME = 4.0;  // 4s rào đóng (tàu qua)
   
    public SimulationEngine(Canvas canvas) {
        this.canvas = canvas;
        this.gc = canvas.getGraphicsContext2D();
        this.cityMap = new CityMap(); 
        
        // Khởi tạo hạt mưa
        for (int i = 0; i < 300; i++) {
            rainX[i] = Math.random() * Constants.WINDOW_WIDTH;
            rainY[i] = Math.random() * Constants.WINDOW_HEIGHT;
        }

        // ---> QUAN TRỌNG: Gọi hàm đổi Map ngay lúc bật app để Camera nhảy đúng vào giữa đường!
        changeMap("Ô Cờ (Grid)");
    }

    // --- CÁC HÀM GETTER/SETTER CHO GIAO DIỆN ---
    public void togglePause() {
        this.isPaused = !this.isPaused;
    }
    public void setDebugMode(boolean debug) { this.isDebugMode = debug; }
    public double getZoomScale() { return zoomScale; }

    /** Chế độ cảnh sát: đèn tất cả đỏ, sau đó cho từng hướng xanh lần lượt */
    public void togglePoliceMode() {
        com.traffic.config.Constants.AUTO_LIGHTS = false;
        for (com.traffic.model.map.IntersectionNode node : cityMap.getNodes()) {
            node.manualToggle();
        }
    }
    
    public void zoomCamera(double factor) {
        this.zoomScale *= factor;
        if(this.zoomScale < 0.3) this.zoomScale = 0.3; 
        if(this.zoomScale > 3.0) this.zoomScale = 3.0; 
    }
    
    public void moveCamera(double dx, double dy) {
        this.cameraX -= dx;
        this.cameraY -= dy;
    }

    // --- HÀM ĐỔI BẢN ĐỒ VÀ CĂN GIỮA CAMERA ---
    public void changeMap(String mapType) {
        this.currentMapType = mapType;
        cityMap.loadMap(mapType);
        zoomScale = 1.0;
        railBarrierDown = false;
        railBarrierTimer = BARRIER_OPEN_TIME;
        vehicles.clear();
        spawnTimer = 0.0;

        if ("Bách Khoa".equals(mapType)) {
            // Không dùng decoration ngẫu nhiên trong campus
            decorations  = new ArrayList<>();
            streetLights = MapRenderer.generateStreetLights(cityMap);
        } else {
            decorations  = MapRenderer.generateDecorations(cityMap);
            streetLights = MapRenderer.generateStreetLights(cityMap);
        }

        if (!cityMap.getNodes().isEmpty()) {
            IntersectionNode centerNode = cityMap.getNodes().get(0);
            cameraX = centerNode.getX() - (canvas.getWidth() / 2);
            cameraY = centerNode.getY() - (canvas.getHeight() / 2);
        }
    }

    // --- XỬ LÝ CLICK CHUỘT TƯƠNG TÁC ---
    public void handleMouseClick(double mouseX, double mouseY, boolean isRightClick) {
        double worldX = (mouseX - canvas.getWidth()/2) / zoomScale + canvas.getWidth()/2 + cameraX;
        double worldY = (mouseY - canvas.getHeight()/2) / zoomScale + canvas.getHeight()/2 + cameraY;
        
        // 1. Check click vào Đèn Giao Thông (Đổi màu thủ công)
        if (!com.traffic.config.Constants.AUTO_LIGHTS) {
            for (IntersectionNode node : cityMap.getNodes()) {
                if (node.getType() != IntersectionNode.NodeType.FIVE_WAY) {
                    if (Math.abs(node.getX() - worldX) < 100 && Math.abs(node.getY() - worldY) < 100) {
                        node.manualToggle(); 
                        return; 
                    }
                }
            }
        }
    }

    @Override
    public void handle(long now) {
        update();
        render();
    }

    private void update() {
        if (isPaused) return;

        // 1. Logic thời gian
        if (com.traffic.config.Constants.TIME_MODE == 0) {
            timeOfDay += 0.005;
            if (timeOfDay >= 24) timeOfDay = 0;
        } else if (com.traffic.config.Constants.TIME_MODE == 1) {
            timeOfDay = 12.0;
        } else {
            timeOfDay = 0.0;
        }

        // 2. Cập nhật rào chắn tàu hỏa (chỉ khi ở map Bách Khoa)
        if ("Bách Khoa".equals(currentMapType)) {
            railBarrierTimer -= 1.0 / 60.0; // giảm theo 60fps
            if (railBarrierTimer <= 0) {
                railBarrierDown = !railBarrierDown;
                railBarrierTimer = railBarrierDown ? BARRIER_CLOSED_TIME : BARRIER_OPEN_TIME;
            }
        }

        // 3. Cập nhật đèn đỏ
        for (IntersectionNode node : cityMap.getNodes()) {
            node.updateLights();
        }

        // 4. Cập nhật xe cộ
        List<Vehicle> toRemove = new ArrayList<>();
        for (Vehicle v : new ArrayList<>(vehicles)) {
            v.update(vehicles, railBarrierDown);
            if (v.isAtEndOfRoad()) {
                boolean success = transitionVehicleToNextRoad(v);
                if (!success) {
                    toRemove.add(v);
                }
            }
        }
        vehicles.removeAll(toRemove);

        // 5. Tự động sinh xe
        if (autoSpawnEnabled) {
            spawnTimer += 1.0 / 60.0;
            if (spawnTimer >= 1.5) {
                spawnTimer = 0.0;
                spawnRandomVehicle();
            }
        }

        if (vehicleCountLabel != null) {
            vehicleCountLabel.setText("Số lượng xe: " + vehicles.size());
        }
    }

    private Color getColorForPhase(TrafficLight.Phase phase) {
        if (phase == TrafficLight.Phase.RED)    return Color.RED;
        if (phase == TrafficLight.Phase.YELLOW) return Color.YELLOW;
        return Color.LIMEGREEN;
    }

    // ---- 3D traffic light (basic: 1 dot; graphic: 3-bulb pole) ----
    private void drawTrafficLight(GraphicsContext gc, TrafficLight light, double x, double y) {
        // Shadow + pole
        gc.setFill(Color.rgb(0,0,0,0.18)); gc.fillOval(x-1, y+43, 18, 6);
        gc.setStroke(Color.web("#4a5568")); gc.setLineWidth(2.5);
        gc.strokeLine(x+8, y+48, x+8, y+18);
        // Housing
        gc.setFill(Color.web("#111820")); gc.fillRoundRect(x+1, y+1, 16, 40, 4, 4);
        gc.setFill(Color.web("#1a252f")); gc.fillRoundRect(x, y, 16, 40, 4, 4);
        gc.setFill(Color.rgb(80,100,120,0.3)); gc.fillRoundRect(x, y, 3, 40, 4, 4);
        // 3 bulbs
        TrafficLight.Phase ph = light.getPhase();
        drawBulb(gc, x+3, y+3,  10, Color.RED,      ph == TrafficLight.Phase.RED);
        drawBulb(gc, x+3, y+15, 10, Color.YELLOW,   ph == TrafficLight.Phase.YELLOW);
        drawBulb(gc, x+3, y+27, 10, Color.LIMEGREEN, ph == TrafficLight.Phase.GREEN);
    }

    private void drawBulb(GraphicsContext gc, double x, double y, double sz, Color c, boolean lit) {
        if (lit) {
            gc.setFill(c.deriveColor(0,1,1,0.3)); gc.fillOval(x-3, y-3, sz+6, sz+6);
            gc.setFill(c); gc.fillOval(x, y, sz, sz);
            gc.setFill(Color.rgb(255,255,255,0.5)); gc.fillOval(x+2, y+1, sz*0.35, sz*0.35);
        } else {
            gc.setFill(c.deriveColor(0, 0.3, 0.25, 1)); gc.fillOval(x, y, sz, sz);
        }
    }

    private void render() {
        double cW = canvas.getWidth(), cH = canvas.getHeight();

        // Tính độ tối
        double darkness = 0;
        if (com.traffic.config.Constants.TIME_MODE == 2) {
            darkness = 0.75;
        } else if (com.traffic.config.Constants.TIME_MODE == 0) {
            if      (timeOfDay >= 18 || timeOfDay <= 6)  darkness = 0.75;
            else if (timeOfDay > 16)  darkness = 0.75 * ((timeOfDay - 16) / 2.0);
            else if (timeOfDay < 8)   darkness = 0.75 * (1 - ((timeOfDay - 6) / 2.0));
        }

        // NỀN
        MapRenderer.drawBackground(gc, cameraX, cameraY);

        gc.save();
        gc.translate(cW/2, cH/2);
        gc.scale(zoomScale, zoomScale);
        gc.translate(-cW/2, -cH/2);
        gc.translate(-cameraX, -cameraY);

        // === TẦNG 1a: Vỉa hè ===
        MapRenderer.drawSidewalks(gc, cityMap);

        // === TẦNG 1b: Đường + vạch kẻ ===
        MapRenderer.drawRoads(gc, cityMap);

        // === TẦNG 1c: Chi tiết ngã tư / bùng binh ===
        MapRenderer.drawIntersectionDetails(gc, cityMap);

        // === TẦNG 1d: Trang trí mặt đất (công viên, bãi đỗ) ===
        MapRenderer.drawDecorationsGround(gc, decorations);

        // === TẦNG 1e: Trang trí cao (nhà, cây, cửa hàng) ===
        MapRenderer.drawDecorationsAbove(gc, decorations, darkness);

        // === TẦNG 1f: Cảnh đặc biệt Bách Khoa ===
        if ("Bách Khoa".equals(currentMapType)) {
            MapRenderer.drawBachKhoaLandmarks(gc);
            // Rào chắn tàu tại 3 vị trí giao cắt
            MapRenderer.drawRailBarrier(gc, 275, 163, railBarrierDown, railBarrierTimer,
                    railBarrierDown ? BARRIER_CLOSED_TIME : BARRIER_OPEN_TIME);
            MapRenderer.drawRailBarrier(gc, 275, 403, railBarrierDown, railBarrierTimer,
                    railBarrierDown ? BARRIER_CLOSED_TIME : BARRIER_OPEN_TIME);
            MapRenderer.drawRailBarrier(gc, 275, 633, railBarrierDown, railBarrierTimer,
                    railBarrierDown ? BARRIER_CLOSED_TIME : BARRIER_OPEN_TIME);
        }

        // === TẦNG 2: Xe cộ ===
        for (Vehicle v : vehicles) {
            v.render(gc, darkness);
        }

        // === TẦNG 3: Phủ màn đêm ===
        if (darkness > 0) {
            gc.setFill(Color.rgb(10, 15, 30, darkness));
            gc.fillRect(cameraX - 5000, cameraY - 5000, 15000, 15000);
        }

        // === TẦNG 4: quầng đèn đường (ADD blend) ===
        MapRenderer.drawStreetLightGlow(gc, streetLights, darkness);

        // === TẦNG 5: Cột đèn đường + Đèn giao thông ===
        MapRenderer.drawStreetLightPoles(gc, streetLights);

        for (IntersectionNode node : cityMap.getNodes()) {
            if (node.isSpawnNode()) continue;
            // Bách Khoa: chỉ vẽ đèn tại ngã tư Giải Phóng (x ≈ 370), bỏ đèn campus nội bộ
            if ("Bách Khoa".equals(currentMapType) && node.getX() > 420) continue;
            double nX = node.getX(), nY = node.getY();
            double off = Constants.ROAD_WIDTH / 2 + 8;

            if (node.getType() == IntersectionNode.NodeType.FIVE_WAY) {
                double off5 = com.traffic.config.Constants.ROUNDABOUT_RADIUS + 83;
                double d45  = Math.cos(Math.toRadians(45)) * off5;
                drawTrafficLight(gc, node.getLightNorth(), nX - 8,          nY - off5 - 22);
                drawTrafficLight(gc, node.getLightSouth(), nX - 8,          nY + off5);
                drawTrafficLight(gc, node.getLightEast(),  nX + off5,       nY - 11);
                drawTrafficLight(gc, node.getLightWest(),  nX - off5 - 16,  nY - 11);
                if (node.isHasNW()) drawTrafficLight(gc, node.getLightNW(), nX-d45-16, nY-d45-22);
            } else {
                if (node.isHasNorth()) drawTrafficLight(gc, node.getLightNorth(), nX - 78,     nY - 118);
                if (node.isHasSouth()) drawTrafficLight(gc, node.getLightSouth(), nX + 62,     nY + 22);
                if (node.isHasEast())  drawTrafficLight(gc, node.getLightEast(),  nX + 62,    nY - 118);
                if (node.isHasWest())  drawTrafficLight(gc, node.getLightWest(),  nX - 78, nY + 22);
            }

            // Đồng hồ đếm ngược
            int remain = (int) Math.ceil(node.getRemainingTime());
            boolean showTimer = node.getLightMode() == IntersectionNode.LightMode.COUNTDOWN
                    || (node.getLightMode() == IntersectionNode.LightMode.SMART_COUNTDOWN && remain <= 10);
            if (showTimer) {
                gc.setFill(Color.web("#1a252f")); gc.fillRoundRect(nX-12, nY-12, 24, 18, 4, 4);
                gc.setFill(Color.WHITE);
                gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 12));
                gc.fillText(String.valueOf(remain), nX - 8, nY + 3);
            }
        }

        gc.restore();

        // === TẦNG 8: Mưa (bám màn hình) ===
        if (com.traffic.config.Constants.IS_RAINING) {
            gc.setStroke(Color.rgb(200, 220, 255, 0.6)); gc.setLineWidth(1.5);
            for (int i = 0; i < 300; i++) {
                gc.strokeLine(rainX[i], rainY[i], rainX[i]-3, rainY[i]+15);
                rainY[i] += 25; rainX[i] -= 5;
                if (rainY[i] > cH) { rainY[i] = -20; rainX[i] = Math.random()*cW+100; }
            }
        }
    }

    public javafx.scene.canvas.Canvas getCanvas() { return canvas; }
    
    public void resetCamera() { 
        zoomScale = 1.0; 
        if (!cityMap.getNodes().isEmpty()) {
            IntersectionNode centerNode = cityMap.getNodes().get(0);
            cameraX = centerNode.getX() - (canvas.getWidth() / 2);
            cameraY = centerNode.getY() - (canvas.getHeight() / 2);
        }
    }
    
    //đổi chế độ đèn
    public void setTrafficLightMode(int modeIndex) {
        IntersectionNode.LightMode mode = IntersectionNode.LightMode.NORMAL; // 0: Không đếm
        if (modeIndex == 1) mode = IntersectionNode.LightMode.COUNTDOWN;     // 1: Đếm toàn thời gian
        else if (modeIndex == 2) mode = IntersectionNode.LightMode.SMART_COUNTDOWN; // 2: Đếm khi <=10s

        for (IntersectionNode node : cityMap.getNodes()) {
            node.setLightMode(mode);
        }
    }

    // --- VEHICLE CONTROL API ---
    private int getCorrectLaneForType(String type, RoadEdge road) {
        int lanesCount = road.getLanesPerDirection();
        if (lanesCount <= 1) return 0;
        
        String lowerType = type.toLowerCase();
        boolean isFourWheeler = lowerType.equals("car") || lowerType.equals("ambulance") || lowerType.equals("firetruck");
        return isFourWheeler ? 0 : 1; // 4-wheeled -> lane 0 (left), 2-wheeled -> lane 1 (right)
    }

    private boolean transitionVehicleToNextRoad(Vehicle v) {
        RoadEdge current = v.getCurrentRoad();
        IntersectionNode targetNode = v.isMovingForward() ? current.getEndNode() : current.getStartNode();
        IntersectionNode fromNode = v.isMovingForward() ? current.getStartNode() : current.getEndNode();
        
        if (targetNode.isSpawnNode()) {
            return false; // Exit point, despawn!
        }
        
        List<RoadEdge> candidates = new ArrayList<>();
        for (RoadEdge road : cityMap.getRoads()) {
            if (road == current) continue;
            
            // Prevent U-turns (do not turn onto the reverse road connecting the same two nodes)
            boolean isReverse = (road.getStartNode() == targetNode && road.getEndNode() == fromNode)
                    || (road.getStartNode() == fromNode && road.getEndNode() == targetNode);
            if (isReverse) continue;
            
            if (road.getStartNode() == targetNode || road.getEndNode() == targetNode) {
                candidates.add(road);
            }
        }
        
        if (candidates.isEmpty()) {
            return false;
        }
        
        RoadEdge nextRoad = candidates.get((int)(Math.random() * candidates.size()));
        boolean movingForward = (nextRoad.getStartNode() == targetNode);
        
        int lane = getCorrectLaneForType(v.getVehicleType(), nextRoad);
        
        v.setCurrentRoad(nextRoad);
        v.setMovingForward(movingForward);
        v.setLaneIndex(lane);
        v.resetDistance();
        return true;
    }

    public void spawnVehicle(String type) {
        List<RoadEdge> spawnRoads = new ArrayList<>();
        for (RoadEdge road : cityMap.getRoads()) {
            if (road.getStartNode().isSpawnNode()) {
                spawnRoads.add(road);
            }
        }
        
        if (spawnRoads.isEmpty()) return;
        
        // Try to find an unoccupied lane on a spawn road
        java.util.Collections.shuffle(spawnRoads);
        for (RoadEdge road : spawnRoads) {
            int lane = getCorrectLaneForType(type, road);
            
            boolean occupied = false;
            for (Vehicle v : vehicles) {
                if (v.getCurrentRoad() == road && v.getLaneIndex() == lane && v.getDistance() < 55) {
                    occupied = true;
                    break;
                }
            }
            
            if (!occupied) {
                Vehicle v = createVehicleInstance(type, road, lane);
                if (v != null) {
                    vehicles.add(v);
                    return;
                }
            }
        }
        
        // Force spawn if necessary
        RoadEdge road = spawnRoads.get(0);
        int lane = getCorrectLaneForType(type, road);
        Vehicle v = createVehicleInstance(type, road, lane);
        if (v != null) {
            vehicles.add(v);
        }
    }

    private Vehicle createVehicleInstance(String type, RoadEdge road, int lane) {
        String id = type + "-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000);
        return switch (type.toLowerCase()) {
            case "car" -> new Car(id, road, lane);
            case "motorcycle" -> new Motorcycle(id, road, lane);
            case "bicycle" -> new Bicycle(id, road, lane);
            case "ambulance" -> new Ambulance(id, road, lane);
            case "firetruck" -> new FireTruck(id, road, lane);
            default -> new Car(id, road, lane);
        };
    }

    public void spawnRandomVehicle() {
        String[] types = {"car", "motorcycle", "bicycle", "ambulance", "firetruck"};
        double rand = Math.random();
        String type;
        if (rand < 0.40) type = "car";
        else if (rand < 0.75) type = "motorcycle";
        else if (rand < 0.90) type = "bicycle";
        else if (rand < 0.95) type = "ambulance";
        else type = "firetruck";
        
        spawnVehicle(type);
    }

    public void clearAllVehicles() {
        vehicles.clear();
    }

    public int getVehicleCount() {
        return vehicles.size();
    }

    public boolean isAutoSpawnEnabled() {
        return autoSpawnEnabled;
    }

    public void setAutoSpawnEnabled(boolean enabled) {
        this.autoSpawnEnabled = enabled;
    }
}