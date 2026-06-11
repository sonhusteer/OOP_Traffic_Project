package com.traffic.ui;

import com.traffic.core.Vector2D;
import com.traffic.core.Vehicle;
import com.traffic.map.Lane;
import com.traffic.map.TrafficLight;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.*;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.util.List;
import java.util.Map;

/**
 * Chế độ vẽ cơ bản — hình khối 2D, gradient, glow.
 * Sử dụng JavaFX GraphicsContext.
 */
public class BasicRenderer extends AbstractBaseRenderer {

    private static final Color ASPHALT   = Color.rgb(45, 48, 55);
    private static final Color EDGE_MARK = Color.rgb(255, 220, 60, 0.78);
    private static final Color BG_DARK   = Color.rgb(30, 38, 28);
    private static final Color BG_GRASS  = Color.rgb(44, 62, 38);

    private static final Map<String, Color> VEHICLE_COLORS = Map.of(
        "car",        Color.rgb(66, 133, 244),
        "motorcycle", Color.rgb(255, 152, 0),
        "bicycle",    Color.rgb(76, 175, 80),
        "ambulance",  Color.rgb(240, 240, 240),
        "firetruck",  Color.rgb(211, 47, 47)
    );

    public BasicRenderer(List<Lane> lanes) {
        super(lanes);
    }

    @Override
    public void draw(GraphicsContext gc, double w, double h) {
        drawBackground(gc, w, h);
        drawLanes(gc);
        drawLights(gc);
        drawVehicles(gc);
        drawHUD(gc, w);
    }

    // ── Nền ──────────────────────────────────────────────────────────────

    private void drawBackground(GraphicsContext gc, double w, double h) {
        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, BG_DARK), new Stop(1, BG_GRASS)));
        gc.fillRect(0, 0, w, h);
    }

    // ── Đường ────────────────────────────────────────────────────────────

    private void drawLanes(GraphicsContext gc) {
        if (lanes == null) return;
        for (Lane lane : lanes) {
            List<Vector2D> pts = lane.getwaypoints();
            if (pts == null || pts.size() < 2) continue;

            // Shadow
            gc.setLineCap(StrokeLineCap.ROUND);
            gc.setLineJoin(StrokeLineJoin.ROUND);
            gc.setLineWidth(84);
            gc.setStroke(Color.rgb(0, 0, 0, 0.24));
            gc.setLineDashes();
            for (int i = 0; i < pts.size() - 1; i++) {
                gc.strokeLine(pts.get(i).getX() + 2, pts.get(i).getY() + 3,
                              pts.get(i+1).getX() + 2, pts.get(i+1).getY() + 3);
            }

            // Asphalt 80px
            gc.setLineWidth(80);
            gc.setStroke(ASPHALT);
            for (int i = 0; i < pts.size() - 1; i++) {
                gc.strokeLine(pts.get(i).getX(), pts.get(i).getY(),
                              pts.get(i+1).getX(), pts.get(i+1).getY());
            }

            // Highlight ánh sáng
            gc.setLineWidth(30);
            gc.setStroke(Color.rgb(255, 255, 255, 0.03));
            for (int i = 0; i < pts.size() - 1; i++) {
                gc.strokeLine(pts.get(i).getX(), pts.get(i).getY(),
                              pts.get(i+1).getX(), pts.get(i+1).getY());
            }

            // Vạch mép vàng đứt đoạn (2 bên)
            gc.setLineCap(StrokeLineCap.BUTT);
            gc.setLineJoin(StrokeLineJoin.BEVEL);
            gc.setLineWidth(1.5);
            gc.setStroke(EDGE_MARK);
            gc.setLineDashes(18, 8);
            for (int side : new int[]{-1, 1}) {
                for (int i = 0; i < pts.size() - 1; i++) {
                    double[] off1 = perp(pts.get(i), pts.get(i+1), 40.0 * side);
                    double[] off2 = perp(pts.get(i+1), pts.get(i), -40.0 * side);
                    gc.strokeLine(
                        pts.get(i).getX()   + off1[0], pts.get(i).getY()   + off1[1],
                        pts.get(i+1).getX() + off2[0], pts.get(i+1).getY() + off2[1]);
                }
            }
            gc.setLineDashes();
        }
    }

    private double[] perp(Vector2D p1, Vector2D p2, double offset) {
        double dx = p2.getX() - p1.getX();
        double dy = p2.getY() - p1.getY();
        double len = Math.hypot(dx, dy);
        if (len == 0) return new double[]{0, 0};
        return new double[]{-dy / len * offset, dx / len * offset};
    }

    // ── Đèn giao thông ──────────────────────────────────────────────────

    private void drawLights(GraphicsContext gc) {
        for (TrafficLight light : lights) {
            if (light == null) continue;
            double x = light.getPosition().getX() - 12;
            double y = light.getPosition().getY() - 35;
            drawTrafficLight(gc, x, y, light);
        }
    }

    private void drawTrafficLight(GraphicsContext gc, double x, double y, TrafficLight light) {
        // Manual mode border
        if (light.isManualMode()) {
            gc.setStroke(Color.rgb(255, 220, 0, 0.8));
            gc.setLineWidth(2.5);
            gc.setLineDashes();
            gc.strokeRoundRect(x - 3, y - 3, 30, 70, 8, 8);
        }

        // Body
        gc.setFill(Color.rgb(35, 35, 40));
        gc.fillRoundRect(x, y, 24, 64, 6, 6);

        // Glow
        String color = light.getColor();
        Color glow = switch (color) {
            case "GREEN"  -> Color.rgb(0, 255, 0, 0.2);
            case "YELLOW" -> Color.rgb(255, 255, 0, 0.2);
            default       -> Color.rgb(255, 0, 0, 0.2);
        };
        gc.setFill(glow);
        gc.fillRoundRect(x - 3, y - 3, 30, 70, 8, 8);

        // 3 bulbs
        drawBulb(gc, x + 4, y + 4,  color.equals("RED"),    Color.RED,    Color.rgb(80, 0, 0));
        drawBulb(gc, x + 4, y + 24, color.equals("YELLOW"), Color.YELLOW, Color.rgb(80, 80, 0));
        drawBulb(gc, x + 4, y + 44, color.equals("GREEN"),  Color.GREEN,  Color.rgb(0, 80, 0));

        // Countdown
        String display = light.getDisplay();
        if (!display.isEmpty()) {
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("SansSerif", FontWeight.BOLD, 11));
            gc.fillText(display, x + 28, y + 38);
        }

        // Manual indicator
        if (light.isManualMode()) {
            gc.setFill(Color.rgb(255, 220, 0));
            gc.setFont(Font.font("SansSerif", FontWeight.BOLD, 10));
            gc.fillText("M", x + 28, y + 52);
        }
    }

    private void drawBulb(GraphicsContext gc, double x, double y, boolean on, Color onColor, Color offColor) {
        gc.setFill(on ? onColor : offColor);
        gc.fillOval(x, y, 16, 16);
        if (on) {
            gc.setFill(Color.color(onColor.getRed(), onColor.getGreen(), onColor.getBlue(), 0.25));
            gc.fillOval(x - 3, y - 3, 22, 22);
        }
    }

    // ── Xe ───────────────────────────────────────────────────────────────

    private void drawVehicles(GraphicsContext gc) {
        for (Vehicle v : vehicles) {
            if (v == null || v.getPosition() == null) continue;
            if (v.isPriority()) drawBlink(gc, v);
            drawVehicle(gc, v);
        }
    }

    private void drawVehicle(GraphicsContext gc, Vehicle v) {
        double vx = v.getPosition().getX();
        double vy = v.getPosition().getY();
        double w  = v.getWidth();
        double h  = v.getHeight();

        gc.save();
        gc.translate(vx, vy);
        gc.rotate(v.getAngle());

        // Yield warning
        if (v.getYieldMode() == Vehicle.YieldMode.STOP) {
            gc.setStroke(Color.rgb(255, 140, 0, 0.8));
            gc.setLineWidth(2.5);
            gc.setLineDashes();
            gc.strokeRect(-w/2 - 3, -h/2 - 3, w + 6, h + 6);
        }

        // Body
        Color base = VEHICLE_COLORS.getOrDefault(v.getTypeName(), Color.GRAY);
        Color dark = base.deriveColor(0, 1, 0.6, 1);
        gc.setFill(base);
        gc.fillRoundRect(-w/2, -h/2, w, h, 4, 4);

        // Roof highlight
        gc.setFill(Color.rgb(255, 255, 255, 0.15));
        gc.fillRoundRect(-w/2 + 3, -h/2 + 2, w - 6, h * 0.35, 3, 3);

        // Headlights (front = right side in local space)
        gc.setFill(Color.rgb(255, 240, 150, 0.9));
        gc.fillRect(w/2 - 3, -h/2 + 2, 3, 4);
        gc.fillRect(w/2 - 3,  h/2 - 6, 3, 4);

        // Taillights (back = left side)
        gc.setFill(Color.rgb(255, 30, 30, 0.8));
        gc.fillRect(-w/2, -h/2 + 2, 3, 4);
        gc.fillRect(-w/2,  h/2 - 6, 3, 4);

        // Wheels
        gc.setFill(Color.rgb(25, 25, 25));
        gc.fillOval(w/2 - 6, -h/2 - 3, 5, 4);
        gc.fillOval(w/2 - 6,  h/2 - 1, 5, 4);
        gc.fillOval(-w/2 + 1, -h/2 - 3, 5, 4);
        gc.fillOval(-w/2 + 1,  h/2 - 1, 5, 4);

        // Name
        gc.setFill(Color.rgb(255, 255, 255, 0.8));
        gc.setFont(Font.font("SansSerif", FontWeight.BOLD, 7));
        String label = v.getName().substring(0, Math.min(3, v.getName().length()));
        gc.fillText(label, -w/2 + 2, 3);

        gc.restore();
    }

    private void drawBlink(GraphicsContext gc, Vehicle v) {
        long t = System.currentTimeMillis() + v.hashCode();
        boolean on = (t % 500) < 250;
        Color c = on ? Color.rgb(255, 0, 0, 0.35) : Color.rgb(0, 100, 255, 0.35);
        double w = v.getWidth(), h = v.getHeight();

        gc.save();
        gc.translate(v.getPosition().getX(), v.getPosition().getY());
        gc.rotate(v.getAngle());
        gc.setFill(c);
        gc.fillOval(-w/2 - 6, -h/2 - 6, w + 12, h + 12);
        gc.restore();
    }

    // ── HUD ──────────────────────────────────────────────────────────────

    private void drawHUD(GraphicsContext gc, double w) {
        gc.setFill(Color.rgb(0, 0, 0, 0.45));
        gc.fillRoundRect(10, 10, 190, 46, 10, 10);
        gc.setStroke(Color.rgb(255, 255, 255, 0.1));
        gc.setLineWidth(1);
        gc.setLineDashes();
        gc.strokeRoundRect(10, 10, 190, 46, 10, 10);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
        gc.fillText("🚦 Traffic Sim", 22, 32);

        gc.setFill(Color.rgb(180, 220, 180));
        gc.setFont(Font.font("SansSerif", 11));
        gc.fillText(vehicles.size() + " vehicles", 22, 48);
    }
}