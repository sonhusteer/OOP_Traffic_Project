package com.traffic.ui;

import com.traffic.core.IRenderer;
import com.traffic.core.Vector2D;
import com.traffic.core.Vehicle;
import com.traffic.map.Lane;
import com.traffic.map.TrafficLight;

import javax.swing.JPanel;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

public class MapRenderer extends JPanel implements IRenderer {
    private final List<Lane> lanes;
    private final List<Vehicle> vehicles = new ArrayList<>();
    private final List<TrafficLight> lights = new ArrayList<>();
    // Tên để hiển thị debug — gán từ ngoài bằng setLightLabels()
    private final java.util.Map<TrafficLight, String> lightLabels = new java.util.LinkedHashMap<>();

    /** Gán nhãn tên cho từng đèn (dùng để vẽ debug overlay) */
    public void setLightLabel(TrafficLight light, String label) {
        lightLabels.put(light, label);
    }
    
    public MapRenderer(List<Lane> lanes, TrafficLight smartLight) {
        this.lanes = lanes;
        if (smartLight != null) {
            this.lights.add(smartLight);
        }
        this.setBackground(new Color(34, 139, 34)); 
    }

    @Override
    public void clear() {
        this.vehicles.clear();
        this.lights.clear();
    }

    @Override
    public void renderVehicles(List<Vehicle> vehicles) {
        this.vehicles.addAll(vehicles);
    }

    @Override
    public void renderLights(List<TrafficLight> lights) {
        this.lights.addAll(lights);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 1. Vẽ tất cả các làn đường
        if (lanes != null) {
            for (Lane lane : lanes) {
                drawLane(g2d, lane);
            }
        }

        // 2. Vẽ đèn giao thông với vị trí lệch lề đường để không đè lên xe
        for (TrafficLight light : lights) {
            if (light != null) {
                Vector2D pos = light.getPosition();
                if (pos != null) {
                    int lx = (int) pos.getX();
                    int ly = (int) pos.getY();
                    
                    if (lx == 370 && ly == 280) { // Đèn làn ngang đi phải (Góc trên trái)
                        drawTrafficLight(g2d, lx - 20, ly - 90, light);
                    } else if (lx == 430 && ly == 320) { // Đèn làn ngang đi trái (Góc dưới phải)
                        drawTrafficLight(g2d, lx - 10, ly + 20, light);
                    } else if (lx == 380 && ly == 270) { // Đèn làn dọc đi xuống (Góc dưới trái)
                        drawTrafficLight(g2d, lx - 75, ly - 10, light);
                    } else if (lx == 420 && ly == 330) { // Đèn làn dọc đi lên (Góc trên phải)
                        drawTrafficLight(g2d, lx + 45, ly - 70, light);
                    } else {
                        drawTrafficLight(g2d, lx, ly, light);
                    }
                } else {
                    drawTrafficLight(g2d, 310, 210, light);
                }
            }
        }

        // 3. Vẽ tất cả phương tiện
        for (Vehicle vehicle : vehicles) {
            if (vehicle != null) {
                drawVehicle(g2d, vehicle);
            }
        }

        // 4. Debug overlay — trạng thái từng đèn
        drawDebugOverlay(g2d);
        drawLegend(g2d);
    }

    private void drawDebugOverlay(Graphics2D g2d) {
        if (lights.isEmpty()) return;
        int panelX = 8, panelY = 8;
        int lineH = 20;
        // Nền mờ
        g2d.setColor(new Color(0, 0, 0, 160));
        g2d.fillRoundRect(panelX - 4, panelY - 4, 250, lineH * lights.size() + 8, 8, 8);

        g2d.setFont(new Font("Monospaced", Font.BOLD, 13));
        int y = panelY + lineH - 4;
        for (TrafficLight light : lights) {
            String label = lightLabels.getOrDefault(light, "Light");
            String state = light.getColor();           // "RED" / "GREEN" / "YELLOW"
            String time  = String.valueOf(light.getTimeLeft());

            Color stateColor = switch (state.toUpperCase()) {
                case "GREEN"  -> new Color(50, 220, 50);
                case "YELLOW" -> Color.YELLOW;
                default       -> new Color(220, 50, 50);
            };
            // Vẽ hình tròn màu
            g2d.setColor(stateColor);
            g2d.fillOval(panelX, y - 13, 14, 14);
            // Vẽ tên + thời gian
            g2d.setColor(Color.WHITE);
            g2d.drawString(String.format("%-10s %-6s %2ss", label, state, time), panelX + 20, y);
            y += lineH;
        }
    }

    // Vẽ chú thích phía dưới
    private void drawLegend(Graphics2D g2d) {
        String[] notes = {
            "● Đỏ/Cam = xe ưu tiên (luôn đi)",
            "● Xanh lam = ô tô thường",
            "● Cam nhạt = xe máy (hung hăng)",
            "● Xanh lá = xe đạp"
        };
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g2d.setColor(new Color(0, 0, 0, 130));
        g2d.fillRoundRect(6, getHeight() - 90, 240, 84, 6, 6);
        g2d.setColor(Color.WHITE);
        int ly = getHeight() - 72;
        for (String note : notes) {
            g2d.drawString(note, 12, ly);
            ly += 18;
        }
    }

    private void drawLane(Graphics2D g2d, Lane lane) {
        List<Vector2D> points = lane.getwaypoints();
        if (points == null || points.size() < 2) return;

        // Vẽ mặt đường
        g2d.setColor(new Color(60, 60, 60));
        g2d.setStroke(new BasicStroke(40, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < points.size() - 1; i++) {
            Vector2D p1 = points.get(i);
            Vector2D p2 = points.get(i + 1);
            g2d.drawLine((int)p1.getX(), (int)p1.getY(), (int)p2.getX(), (int)p2.getY());
        }

        // Vẽ vạch kẻ đường
        g2d.setColor(Color.WHITE);
        float[] dash = {10f, 10f};
        g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, dash, 0));
        for (int i = 0; i < points.size() - 1; i++) {
            Vector2D p1 = points.get(i);
            Vector2D p2 = points.get(i + 1);
            g2d.drawLine((int)p1.getX(), (int)p1.getY(), (int)p2.getX(), (int)p2.getY());
        }
    }

    private void drawTrafficLight(Graphics2D g2d, int x, int y, TrafficLight light) {
        g2d.setColor(Color.BLACK);
        g2d.fillRect(x, y, 30, 80);

        // Đèn Đỏ
        g2d.setColor(light.getColor().equalsIgnoreCase("Red") ? Color.RED : Color.DARK_GRAY);
        g2d.fillOval(x + 5, y + 5, 20, 20);
        // Đèn Vàng
        g2d.setColor(light.getColor().equalsIgnoreCase("Yellow") ? Color.YELLOW : Color.DARK_GRAY);
        g2d.fillOval(x + 5, y + 30, 20, 20);
        // Đèn Xanh
        g2d.setColor(light.getColor().equalsIgnoreCase("Green") ? Color.GREEN : Color.DARK_GRAY);
        g2d.fillOval(x + 5, y + 55, 20, 20);

        // Hiển thị số giây
        String displayValue = light.getDisplay(); 
        if (displayValue != null && !displayValue.isEmpty() && !displayValue.equalsIgnoreCase(light.getColor())) {
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 14));
            g2d.drawString(displayValue, x + 35, y + 45);
        }
    }

    private void drawVehicle(Graphics2D g2d, Vehicle vehicle) {
        Vector2D pos = vehicle.getPosition();
        if (pos == null) return;

        double angle = vehicle.getAngle();
        double w = vehicle.getWidth();
        double h = vehicle.getHeight();

        // Lưu trạng thái vẽ cũ
        AffineTransform old = g2d.getTransform();

        // Di chuyển và xoay canvas theo hướng của xe
        g2d.translate(pos.getX(), pos.getY());
        g2d.rotate(Math.toRadians(angle));

        // Xác định màu sắc theo loại xe
        if (vehicle.isPriority()) {
            // Xe ưu tiên (Cứu thương, cứu hỏa): Vẽ màu đỏ nổi bật
            g2d.setColor(new Color(220, 20, 60)); // Crimson Red
            g2d.fillRect((int)(-w/2), (int)(-h/2), (int)w, (int)h);
            
            // Vẽ đèn còi nhấp nháy màu xanh dương
            g2d.setColor(new Color(30, 144, 255)); // Dodger Blue
            g2d.fillOval(-4, -4, 8, 8);
        } else {
            // Xe thường: Phân biệt màu sắc
            Color carColor = switch(vehicle.getTypeName().toLowerCase()) {
                case "car" -> new Color(0, 128, 255);       // Royal Blue
                case "motorcycle" -> new Color(255, 140, 0); // Dark Orange
                case "bicycle" -> new Color(50, 205, 50);    // Lime Green
                default -> Color.LIGHT_GRAY;
            };
            g2d.setColor(carColor);
            g2d.fillRect((int)(-w/2), (int)(-h/2), (int)w, (int)h);
        }

        // Vẽ bánh xe (kính chắn gió / bánh xe đen nhỏ chỉ hướng)
        g2d.setColor(Color.BLACK);
        g2d.fillRect((int)(w/2 - 5), (int)(-h/2 - 2), 4, 2);
        g2d.fillRect((int)(w/2 - 5), (int)(h/2), 4, 2);
        g2d.fillRect((int)(-w/2 + 1), (int)(-h/2 - 2), 4, 2);
        g2d.fillRect((int)(-w/2 + 1), (int)(h/2), 4, 2);

        // Vẽ tên + tốc độ phương tiện
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 9));
        g2d.drawString(vehicle.getName().substring(0, Math.min(3, vehicle.getName().length())), (int)(-w/2 + 3), 3);
        // Hiển thị tốc độ nhỏ bên phải (0 = đố đỏ → sàng đỏ ở tốc độ > 0)
        int spd = (int) vehicle.getSpeed();
        g2d.setColor(spd == 0 ? Color.RED : new Color(200, 255, 200));
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 8));
        g2d.drawString(String.valueOf(spd), (int)(w/2 - 14), 3);

        // Khôi phục trạng thái vẽ
        g2d.setTransform(old);
    }
}