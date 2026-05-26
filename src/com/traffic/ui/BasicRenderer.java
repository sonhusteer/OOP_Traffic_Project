package com.traffic.ui;

import com.traffic.core.IRenderer;
import com.traffic.core.MathUtils;
import com.traffic.core.Vehicle;
import com.traffic.core.Vector2D;
import com.traffic.map.Lane;
import com.traffic.map.TrafficLight;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Chế độ Basic: vẽ xe = hình chữ nhật màu + tên.
 * Implements IRenderer → thể hiện tính đa hình với JavaFXRenderer.
 *
 * Cùng lời gọi engine.render() nhưng cho giao diện hoàn toàn khác.
 */
public class BasicRenderer extends JPanel implements IRenderer {

    private final List<Lane>         lanes;
    private final List<Vehicle>      vehicles = new ArrayList<>();
    private final List<TrafficLight> lights   = new ArrayList<>();

    // Màu xe theo loại
    private static final Map<String, Color> VEHICLE_COLORS = Map.of(
        "car",        new Color(30, 120, 255),
        "motorcycle", new Color(255, 140, 0),
        "bicycle",    new Color(50, 200, 50),
        "ambulance",  Color.WHITE,
        "firetruck",  new Color(220, 30, 30)
    );

    public BasicRenderer(List<Lane> lanes) {
        this.lanes = lanes;
        setBackground(new Color(34, 139, 34)); // nền cỏ xanh
        setPreferredSize(new Dimension(800, 600));
    }

    // ── IRenderer ────────────────────────────────────────────────────────

    @Override
    public void clear() {
        vehicles.clear();
        lights.clear();
    }

    @Override
    public void renderVehicles(List<Vehicle> list) {
        vehicles.addAll(list);
    }

    @Override
    public void renderLights(List<TrafficLight> list) {
        lights.addAll(list);
    }

    // ── Swing paint ──────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

        drawLanes(g2);
        drawLights(g2);
        drawVehicles(g2);
        drawDebugOverlay(g2);
    }

    // ── Vẽ đường ─────────────────────────────────────────────────────────

    private void drawLanes(Graphics2D g2) {
        if (lanes == null) return;
        for (Lane lane : lanes) {
            List<Vector2D> pts = lane.getwaypoints();
            if (pts == null || pts.size() < 2) continue;

            // Mặt đường xám
            g2.setColor(new Color(60, 60, 60));
            g2.setStroke(new BasicStroke(40, BasicStroke.CAP_ROUND,
                                         BasicStroke.JOIN_ROUND));
            for (int i = 0; i < pts.size() - 1; i++) {
                g2.drawLine((int) pts.get(i).getX(), (int) pts.get(i).getY(),
                            (int) pts.get(i+1).getX(), (int) pts.get(i+1).getY());
            }

            // Vạch kẻ trắng đứt
            float[] dash = {10f, 10f};
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_BEVEL, 0, dash, 0));
            for (int i = 0; i < pts.size() - 1; i++) {
                g2.drawLine((int) pts.get(i).getX(), (int) pts.get(i).getY(),
                            (int) pts.get(i+1).getX(), (int) pts.get(i+1).getY());
            }
        }
    }

    // ── Vẽ đèn ───────────────────────────────────────────────────────────

    private void drawLights(Graphics2D g2) {
        for (TrafficLight light : lights) {
            if (light == null) continue;
            int x = (int) light.getPosition().getX();
            int y = (int) light.getPosition().getY();
            drawTrafficLight(g2, x - 15, y - 40, light);
        }
    }

    private void drawTrafficLight(Graphics2D g2, int x, int y, TrafficLight light) {
        // Hộp đèn
        g2.setColor(new Color(30, 30, 30));
        g2.fillRoundRect(x, y, 30, 80, 6, 6);

        String color = light.getColor();

        g2.setColor(color.equals("RED")    ? Color.RED    : new Color(80, 0, 0));
        g2.fillOval(x + 5, y + 5,  20, 20);
        g2.setColor(color.equals("YELLOW") ? Color.YELLOW : new Color(80, 80, 0));
        g2.fillOval(x + 5, y + 30, 20, 20);
        g2.setColor(color.equals("GREEN")  ? Color.GREEN  : new Color(0, 80, 0));
        g2.fillOval(x + 5, y + 55, 20, 20);

        // Số giây — đa hình: mỗi loại đèn getDisplay() khác nhau
        String display = light.getDisplay();
        if (!display.isEmpty()) {
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, 13));
            g2.drawString(display, x + 35, y + 45);
        }
    }

    // ── Vẽ xe ────────────────────────────────────────────────────────────

    private void drawVehicles(Graphics2D g2) {
        for (Vehicle v : vehicles) {
            if (v == null || v.getPosition() == null) continue;
            drawVehicle(g2, v);
        }
    }

    private void drawVehicle(Graphics2D g2, Vehicle v) {
        double w = v.getWidth(), h = v.getHeight();
        AffineTransform old = g2.getTransform();

        g2.translate(v.getPosition().getX(), v.getPosition().getY());
        g2.rotate(Math.toRadians(v.getAngle()));

        // Thân xe
        Color c = VEHICLE_COLORS.getOrDefault(v.getTypeName(), Color.GRAY);
        g2.setColor(c);
        g2.fillRect((int)(-w/2), (int)(-h/2), (int)w, (int)h);

        // Viền
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1));
        g2.drawRect((int)(-w/2), (int)(-h/2), (int)w, (int)h);

        // Bánh xe
        g2.setColor(new Color(20, 20, 20));
        int wx = 4, wy = 3;
        g2.fillRect((int)( w/2 - wx - 1), (int)(-h/2 - wy), wx, wy);
        g2.fillRect((int)( w/2 - wx - 1), (int)( h/2),      wx, wy);
        g2.fillRect((int)(-w/2 + 1),      (int)(-h/2 - wy), wx, wy);
        g2.fillRect((int)(-w/2 + 1),      (int)( h/2),      wx, wy);

        // Tên xe
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 9));
        String label = v.getName().substring(0, Math.min(3, v.getName().length()));
        g2.drawString(label, (int)(-w/2 + 2), 3);

        // Tốc độ
        int spd = (int) v.getSpeed();
        g2.setColor(spd == 0 ? Color.RED : new Color(180, 255, 180));
        g2.setFont(new Font("SansSerif", Font.PLAIN, 8));
        g2.drawString(String.valueOf(spd), (int)(w/2 - 14), 3);

        g2.setTransform(old);
    }

    // ── Debug overlay ─────────────────────────────────────────────────────

    private void drawDebugOverlay(Graphics2D g2) {
        if (lights.isEmpty()) return;
        int x = 8, y = 8, lineH = 20;

        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRoundRect(x - 4, y - 4, 230, lineH * lights.size() + 8, 8, 8);
        g2.setFont(new Font("Monospaced", Font.BOLD, 12));

        int ty = y + lineH - 4;
        for (TrafficLight light : lights) {
            Color sc = switch (light.getColor()) {
                case "GREEN"  -> new Color(50, 220, 50);
                case "YELLOW" -> Color.YELLOW;
                default       -> new Color(220, 50, 50);
            };
            g2.setColor(sc);
            g2.fillOval(x, ty - 13, 13, 13);
            g2.setColor(Color.WHITE);
            g2.drawString(String.format("%-6s %2ds",
                    light.getColor(), (int) light.getTimeLeft()), x + 18, ty);
            ty += lineH;
        }
    }
}
