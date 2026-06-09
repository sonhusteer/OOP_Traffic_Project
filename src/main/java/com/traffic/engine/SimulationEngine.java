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

public class SimulationEngine extends AnimationTimer {
    
    private Canvas canvas;
    private GraphicsContext gc;
    private CityMap cityMap;
    
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
    private long tick = 0; // dùng để animate sóng nước

    // --- GRAPHIC ASSETS (generated once per map load) ---
    private List<MapRenderer.Decoration>  decorations  = new ArrayList<>();
    private List<MapRenderer.StreetLight> streetLights = new ArrayList<>();

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
        decorations  = MapRenderer.generateDecorations(cityMap);
        streetLights = MapRenderer.generateStreetLights(cityMap);

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


        if (!isPaused) tick++;
        for (IntersectionNode node : cityMap.getNodes()) {
            node.updateLights();
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

        // === TẦNG 1d-extra: Cảnh đặc biệt Hỗn Hợp (sông + cầu) ===
        if ("Hỗn Hợp".equals(currentMapType)) {
            MapRenderer.drawMixedMapLandmarks(gc, tick);
        }

        // === TẦNG 1d: Trang trí mặt đất (công viên, bãi đỗ) ===
        MapRenderer.drawDecorationsGround(gc, decorations);

        // === TẦNG 1e: Trang trí cao (nhà, cây, cửa hàng) ===
        MapRenderer.drawDecorationsAbove(gc, decorations, darkness);


        if (darkness > 0) {
            gc.setFill(Color.rgb(10, 15, 30, darkness));
            gc.fillRect(cameraX - 5000, cameraY - 5000, 15000, 15000);
        }

        // === TẦNG 4: quầng đèn đường (ADD blend) ===
        MapRenderer.drawStreetLightGlow(gc, streetLights, darkness);

        // === TẦNG 5: Cột đèn đường + Đèn giao thông ===
        MapRenderer.drawStreetLightPoles(gc, streetLights);

        for (IntersectionNode node : cityMap.getNodes()) {
            // Bỏ qua spawn nodes
            if (node.isSpawnNode()) continue;
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
}