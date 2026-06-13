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

    // ── Màu đường ────────────────────────────────────────────────────────
    private static final Color ASPHALT       = Color.rgb(38, 41, 50);
    private static final Color ASPHALT_EDGE  = Color.rgb(52, 56, 66);
    private static final Color LANE_EDGE     = Color.rgb(255, 210, 50, 0.90);  // vạch mép vàng liền
    private static final Color LANE_CENTER   = Color.rgb(255, 255, 255, 0.55); // vạch giữa trắng đứt
    private static final Color BG_DARK       = Color.rgb(22, 34, 20);
    private static final Color BG_GRASS      = Color.rgb(38, 58, 32);

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
        drawBuildings(gc, w, h);      // nhà dân cư (dưới cùng)
        drawLanes(gc);                // đường xe chạy (kẻ vạch qua ngã tư)
        drawIntersections(gc);        // ngã giao (xóa vạch cũ, vẽ vạch mới chuẩn)
        drawLights(gc);              // đèn giao thông
        drawVehicles(gc);            // xe
        drawRain(gc, w, h);          // hiệu ứng thời tiết (mưa trơn trượt)
        drawHUD(gc, w);              // HUD
    }

    // ── Nền ──────────────────────────────────────────────────────────────

    private void drawBackground(GraphicsContext gc, double w, double h) {
        // Gradient cỏ tự nhiên hơn
        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, BG_DARK), new Stop(1, BG_GRASS)));
        gc.fillRect(0, 0, w, h);

        // Texture cỏ — lưới mờ nhẹ
        gc.setStroke(Color.rgb(0, 0, 0, 0.08));
        gc.setLineWidth(1);
        gc.setLineDashes();
        for (int x = 0; x < (int)w; x += 32) gc.strokeLine(x, 0, x, h);
        for (int y = 0; y < (int)h; y += 32) gc.strokeLine(0, y, w, y);
    }

    // ── Đường ────────────────────────────────────────────────────────────

    private void drawLanes(GraphicsContext gc) {
        if (lanes == null) return;
        for (Lane lane : lanes) {
            List<Vector2D> pts = lane.getwaypoints();
            if (pts == null || pts.size() < 2) continue;

            gc.setLineCap(StrokeLineCap.ROUND);
            gc.setLineJoin(StrokeLineJoin.ROUND);
            gc.setLineDashes();

            // 1. Shadow mềm bên dưới
            gc.setLineWidth(88);
            gc.setStroke(Color.rgb(0, 0, 0, 0.30));
            strokePath(gc, pts, 3, 4);

            // 2. Mặt đường asphalt chính
            gc.setLineWidth(80);
            gc.setStroke(ASPHALT);
            strokePath(gc, pts, 0, 0);

            // 3. Viền ngoài đường (1 lớp nhạt hơn một chút)
            gc.setLineWidth(80);
            gc.setStroke(ASPHALT_EDGE);
            // chỉ vẽ viền — dùng stroke rộng hơn rồi đè lên không hiệu quả,
            // dùng offset mép thay thế bên dưới

            // 4. Highlight mờ giữa đường (ánh sáng ban ngày)
            gc.setLineWidth(20);
            gc.setStroke(Color.rgb(255, 255, 255, 0.04));
            strokePath(gc, pts, 0, 0);

            // 5. Vạch mép vàng LIỀN nét — rõ ràng 2 bên
            gc.setLineCap(StrokeLineCap.BUTT);
            gc.setLineJoin(StrokeLineJoin.MITER);
            gc.setLineWidth(2.5);
            gc.setStroke(LANE_EDGE);
            gc.setLineDashes();
            for (int side : new int[]{-1, 1}) {
                strokePathOffset(gc, pts, 38.0 * side);
            }

            // 6. Vạch trung tâm TRẮNG ĐỨT ĐOẠN — phân tách chiều đi
            gc.setLineCap(StrokeLineCap.BUTT);
            gc.setLineWidth(1.8);
            gc.setStroke(LANE_CENTER);
            gc.setLineDashes(14, 10);
            strokePath(gc, pts, 0, 0);
            gc.setLineDashes();
        }
    }

    /** Vẽ path offset theo chiều perpendicular */
    private void strokePathOffset(GraphicsContext gc, List<Vector2D> pts, double offset) {
        for (int i = 0; i < pts.size() - 1; i++) {
            double[] o1 = perp(pts.get(i), pts.get(i + 1),  offset);
            double[] o2 = perp(pts.get(i + 1), pts.get(i), -offset);
            gc.strokeLine(
                pts.get(i).getX()     + o1[0], pts.get(i).getY()     + o1[1],
                pts.get(i + 1).getX() + o2[0], pts.get(i + 1).getY() + o2[1]);
        }
    }

    /** Vẽ path với offset dịch chuyển x/y (cho shadow) */
    private void strokePath(GraphicsContext gc, List<Vector2D> pts, double dx, double dy) {
        for (int i = 0; i < pts.size() - 1; i++) {
            gc.strokeLine(
                pts.get(i).getX()     + dx, pts.get(i).getY()     + dy,
                pts.get(i + 1).getX() + dx, pts.get(i + 1).getY() + dy);
        }
    }

    private double[] perp(Vector2D p1, Vector2D p2, double offset) {
        double dx = p2.getX() - p1.getX();
        double dy = p2.getY() - p1.getY();
        double len = Math.hypot(dx, dy);
        if (len == 0) return new double[]{0, 0};
        return new double[]{-dy / len * offset, dx / len * offset};
    }

    // ── Đèn giao thông — MINIMALIST ──────────────────────────────────────

    private void drawLights(GraphicsContext gc) {
        for (TrafficLight light : lights) {
            if (light == null) continue;
            double cx = light.getPosition().getX();
            double cy = light.getPosition().getY();
            drawTrafficLight(gc, cx, cy, light);
        }
    }

    /**
     * Thiết kế tối giản:
     *  - Pill nhỏ dọc 10×28px, nền #111
     *  - 3 chấm đường kính 6px
     *  - Số đếm bên cạnh nhỏ gọn
     */
    private void drawTrafficLight(GraphicsContext gc, double cx, double cy, TrafficLight light) {
        // Pill tối giản: 8×24px
        double pw = 8, ph = 24;
        double px = cx - pw / 2, py = cy - ph / 2;

        // Nền pill — rất tối
        gc.setFill(Color.rgb(14, 14, 18));
        gc.fillRoundRect(px, py, pw, ph, pw, pw);

        // Viền mờ
        gc.setStroke(Color.rgb(255, 255, 255, 0.10));
        gc.setLineWidth(0.8);
        gc.setLineDashes();
        gc.strokeRoundRect(px, py, pw, ph, pw, pw);

        // Manual mode — viền vàng mỏng
        if (light.isManualMode()) {
            gc.setStroke(Color.rgb(255, 200, 0, 0.70));
            gc.setLineWidth(1.2);
            gc.strokeRoundRect(px - 2, py - 2, pw + 4, ph + 4, pw + 2, pw + 2);
        }

        String color = light.getColor();

        // 3 chấm nhỏ (cách đều)
        drawMiniBulb(gc, cx, py + 4,  "RED",    color);
        drawMiniBulb(gc, cx, py + 12, "YELLOW", color);
        drawMiniBulb(gc, cx, py + 20, "GREEN",  color);

        // Số đếm ngược — nhỏ, bên phải pill
        String display = light.getDisplay();
        if (!display.isEmpty()) {
            Color tc = switch (color) {
                case "GREEN"  -> Color.rgb(72, 220, 100);
                case "YELLOW" -> Color.rgb(255, 210, 50);
                default       -> Color.rgb(240, 80, 80);
            };
            gc.setFont(Font.font("SansSerif", FontWeight.BOLD, 8));
            gc.setFill(tc);
            gc.fillText(display, cx + pw / 2 + 2, cy + 3);
        }
    }

    private void drawMiniBulb(GraphicsContext gc, double cx, double cy, String bulbColor, String active) {
        boolean on = bulbColor.equals(active);
        int r = 3;

        Color onC = switch (bulbColor) {
            case "RED"    -> Color.rgb(255, 65, 65);
            case "YELLOW" -> Color.rgb(255, 210, 40);
            default       -> Color.rgb(60, 220, 80);
        };
        Color offC = switch (bulbColor) {
            case "RED"    -> Color.rgb(55, 18, 18);
            case "YELLOW" -> Color.rgb(55, 48, 10);
            default       -> Color.rgb(10, 55, 18);
        };

        if (on) {
            // Glow mờ nhỏ
            gc.setFill(Color.color(onC.getRed(), onC.getGreen(), onC.getBlue(), 0.22));
            gc.fillOval(cx - r - 3, cy - r - 3, (r + 3) * 2, (r + 3) * 2);
        }
        gc.setFill(on ? onC : offC);
        gc.fillOval(cx - r, cy - r, r * 2, r * 2);

        // Highlight nhỏ
        if (on) {
            gc.setFill(Color.rgb(255, 255, 255, 0.28));
            gc.fillOval(cx - r + 1, cy - r + 1, r - 1, r - 1);
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
        if (v.getYieldMode() == Vehicle.YieldMode.STOP_BEFORE_CONFLICT
                || v.getYieldMode() == Vehicle.YieldMode.STOP
                || v.getYieldMode() == Vehicle.YieldMode.CLEAR_CONFLICT
                || v.getYieldMode() == Vehicle.YieldMode.CLEAR_INTERSECTION
                || v.getYieldMode() == Vehicle.YieldMode.CLEAR_PATH
                || v.getYieldMode() == Vehicle.YieldMode.URGENT_CLEAR_PATH) {
            gc.setStroke(Color.rgb(255, 140, 0, 0.8));
            gc.setLineWidth(2.5);
            gc.setLineDashes();
            gc.strokeRect(-w/2 - 3, -h/2 - 3, w + 6, h + 6);
        }

        if (isUrgentYield(v)) {
            gc.setStroke(Color.rgb(255, 230, 70, 0.95));
            gc.setLineWidth(1.4);
            gc.setLineDashes(4, 4);
            gc.strokeRect(-w/2 - 6, -h/2 - 6, w + 12, h + 12);
            gc.setLineDashes();
        }

        // Body
        Color base = VEHICLE_COLORS.getOrDefault(v.getTypeName(), Color.GRAY);
        gc.setFill(new LinearGradient(-w/2, 0, w/2, 0, false, CycleMethod.NO_CYCLE,
            new Stop(0, base), new Stop(1, base.deriveColor(0, 1, 0.65, 1))));
        gc.fillRoundRect(-w/2, -h/2, w, h, 4, 4);

        // Roof highlight
        gc.setFill(Color.rgb(255, 255, 255, 0.15));
        gc.fillRoundRect(-w/2 + 3, -h/2 + 2, w - 6, h * 0.35, 3, 3);

        // Headlights
        gc.setFill(Color.rgb(255, 240, 150, 0.9));
        gc.fillRect(w/2 - 3, -h/2 + 2, 3, 4);
        gc.fillRect(w/2 - 3,  h/2 - 6, 3, 4);

        // Taillights
        gc.setFill(Color.rgb(255, 30, 30, 0.8));
        gc.fillRect(-w/2, -h/2 + 2, 3, 4);
        gc.fillRect(-w/2,  h/2 - 6, 3, 4);

        // Wheels
        gc.setFill(Color.rgb(25, 25, 25));
        gc.fillOval(w/2 - 6, -h/2 - 3, 5, 4);
        gc.fillOval(w/2 - 6,  h/2 - 1, 5, 4);
        gc.fillOval(-w/2 + 1, -h/2 - 3, 5, 4);
        gc.fillOval(-w/2 + 1,  h/2 - 1, 5, 4);

        // Name label
        gc.setFill(Color.rgb(255, 255, 255, 0.85));
        gc.setFont(Font.font("SansSerif", FontWeight.BOLD, 7));
        String label = v.getName().substring(0, Math.min(3, v.getName().length()));
        gc.fillText(label, -w/2 + 2, 3);

        gc.restore();
        drawTurnIntentBadge(gc, v, vx, vy, w, h);
    }

    private void drawTurnIntentBadge(GraphicsContext gc, Vehicle v, double vx, double vy, double w, double h) {
        if (v == null) return;
        String turn = v.getTurnIntentLabel();
        if (turn == null || turn.isBlank()) return;

        double badge = 20.0;
        double bx = vx - badge / 2.0;
        double by = vy - Math.max(w, h) / 2.0 - badge - 6.0;
        boolean waiting = v.getIntersectionManeuverState() == Vehicle.IntersectionManeuverState.WAITING_BEFORE_INTERSECTION;
        Color bg = waiting ? Color.rgb(255, 170, 35, 0.88) : Color.rgb(20, 20, 25, 0.70);
        Color fg = waiting ? Color.rgb(30, 25, 10) : Color.rgb(255, 255, 255, 0.92);

        gc.save();
        gc.setFill(bg);
        gc.fillRoundRect(bx, by, badge, badge, 5, 5);
        gc.setStroke(Color.rgb(255, 255, 255, 0.35));
        gc.setLineWidth(0.8);
        gc.strokeRoundRect(bx, by, badge, badge, 5, 5);
        gc.setFill(fg);
        gc.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
        gc.fillText(turn, bx + 6.0, by + 14.5);
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
    private boolean isUrgentYield(Vehicle v) {
        return v != null && (v.getYieldMode() == Vehicle.YieldMode.URGENT_CLEAR_PATH
                || v.getYieldMode() == Vehicle.YieldMode.CLEAR_PATH
                || v.getManeuverState() == Vehicle.ManeuverState.URGENT_CLEARING);
    }

}