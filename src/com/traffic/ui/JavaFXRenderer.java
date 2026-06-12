package com.traffic.ui;

import com.traffic.core.Vector2D;
import com.traffic.core.Vehicle;
import com.traffic.map.Lane;
import com.traffic.map.TrafficLight;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.*;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chế độ Graphic — hỗ trợ sprite ảnh xe + hiệu ứng nâng cao.
 * Dùng JavaFX Canvas. Nếu không tìm thấy ảnh, fallback về vẽ 2D.
 */
public class JavaFXRenderer extends AbstractBaseRenderer {

    private final Map<String, Image> sprites = new HashMap<>();

    // ── Bảng màu đường & nền ─────────────────────────────────────────────
    private static final Color ASPHALT      = Color.rgb(38, 41, 50);
    private static final Color ASPHALT_EDGE = Color.rgb(52, 56, 66);
    private static final Color LANE_EDGE    = Color.rgb(255, 210, 50, 0.90);  // vạch mép vàng
    private static final Color LANE_CENTER  = Color.rgb(255, 255, 255, 0.55); // vạch giữa trắng
    private static final Color BG_DARK      = Color.rgb(22, 34, 20);
    private static final Color BG_GRASS     = Color.rgb(38, 58, 32);

    // ── Bảng màu xe ─────────────────────────────────────────────────────
    private static final Map<String, Color> VEHICLE_COLORS = Map.of(
        "car",        Color.rgb(66, 133, 244),
        "motorcycle", Color.rgb(255, 152, 0),
        "bicycle",    Color.rgb(76, 175, 80),
        "ambulance",  Color.rgb(240, 240, 240),
        "firetruck",  Color.rgb(211, 47, 47)
    );

    private static final Map<String, Color> VEHICLE_COLORS_DARK = Map.of(
        "car",        Color.rgb(25, 82, 180),
        "motorcycle", Color.rgb(200, 100, 0),
        "bicycle",    Color.rgb(40, 120, 45),
        "ambulance",  Color.rgb(160, 165, 170),
        "firetruck",  Color.rgb(150, 20, 20)
    );

    public JavaFXRenderer(List<Lane> lanes) {
        super(lanes);
        loadSprites();
    }

    /** Tải sprite PNG từ /images/. Nếu không có, fallback về 2D shapes. */
    private void loadSprites() {
        String[] types = {"car", "motorcycle", "bicycle", "ambulance", "firetruck"};
        for (String type : types) {
            try {
                InputStream is = getClass().getResourceAsStream("/images/" + type + ".png");
                if (is != null) {
                    sprites.put(type, new Image(is));
                }
            } catch (Exception ignored) {}
        }
    }

    // =====================================================================
    //  MAIN DRAW
    // =====================================================================
    @Override
    public void draw(GraphicsContext gc, double width, double height) {
        // Draw Grass background
        gc.setFill(Color.web("#1b261a")); 
        gc.fillRect(0, 0, width, height);

        // Draw simple dummy grid / buildings for flavor (like in the screenshot)
        drawSimpleBuildings(gc, width, height);

        // Grid nhẹ (optional, to match screenshots)
        gc.setStroke(Color.web("#2c3e2b", 0.5));
        gc.setLineWidth(1);
        for(int i = 0; i < width; i += 100) gc.strokeLine(i, 0, i, height);
        for(int i = 0; i < height; i += 100) gc.strokeLine(0, i, width, i);
        drawLanes(gc);                // đường xe chạy (kẻ vạch qua ngã tư)
        drawIntersections(gc);        // ngã giao (xóa vạch cũ, vẽ chuẩn)
        drawLights(gc);              // đèn giao thông
        drawVehicles(gc);            // xe
        drawHUD(gc, width);          // HUD
    }

    private void drawSimpleBuildings(GraphicsContext gc, double w, double h) {
        gc.setFill(Color.web("#314234"));
        // Random square blocks acting as simple top-down roofs
        // Since we don't have a stable RNG seed, they will re-roll every frame if we just use random().
        // For a quick fix without state, we can use a deterministic math function based on grid coords.
        for (int x = 50; x < w; x += 100) {
            for (int y = 50; y < h; y += 100) {
                // Pseudo random
                int hash = (x * 73856093 ^ y * 19349663);
                if ((hash % 100) > 40) {
                    double bw = 20 + Math.abs(hash % 30);
                    double bh = 20 + Math.abs((hash / 17) % 30);
                    gc.setFill(Color.web("#354638")); // shadow
                    gc.fillRect(x + 2, y + 2, bw, bh);
                    gc.setFill(Color.web(((hash % 2) == 0) ? "#4a5d4e" : "#515e52"));
                    gc.fillRect(x, y, bw, bh);
                }
            }
        }
    }

    // =====================================================================
    //  2. VẼ ĐƯỜNG — asphalt 80px, vạch mép vàng
    // =====================================================================
    private void drawLanes(GraphicsContext gc) {
        if (lanes == null) return;

        for (Lane lane : lanes) {
            List<Vector2D> pts = lane.getwaypoints();
            if (pts == null || pts.size() < 2) continue;

            gc.setLineCap(StrokeLineCap.ROUND);
            gc.setLineJoin(StrokeLineJoin.ROUND);
            gc.setLineDashes();

            // Hover effect
            if (lane == hoveredLane) {
                gc.setStroke(Color.rgb(255, 255, 0, 0.8));
                gc.setLineWidth(96);
                strokePath(gc, pts, 0, 0);
            }

            gc.setLineCap(StrokeLineCap.ROUND);
            gc.setLineJoin(StrokeLineJoin.ROUND);
            gc.setLineDashes();

            // 1. Shadow mềm
            gc.setStroke(Color.rgb(0, 0, 0, 0.30));
            gc.setLineWidth(88);
            strokePath(gc, pts, 3, 4);

            // 2. Mặt đường asphalt hoặc heatmap
            if (showHeatmap) {
                double density = Math.min(1.0, lane.getVehicles().size() / 15.0);
                gc.setStroke(Color.color(density, 1.0 - density, 0, 0.6));
            } else {
                gc.setStroke(ASPHALT);
            }
            gc.setLineWidth(80);
            strokePath(gc, pts, 0, 0);

            // 3. Highlight ánh sáng giữa đường
            gc.setStroke(Color.rgb(255, 255, 255, 0.04));
            gc.setLineWidth(22);
            strokePath(gc, pts, 0, 0);

            // 4. Vạch mép vàng LIỀN — rõ nét 2 bên
            gc.setLineCap(StrokeLineCap.BUTT);
            gc.setLineJoin(StrokeLineJoin.MITER);
            gc.setStroke(LANE_EDGE);
            gc.setLineWidth(2.5);
            gc.setLineDashes();
            for (int side : new int[]{-1, 1}) {
                strokePathOffset(gc, pts, 38.0 * side);
            }

            // 5. Vạch giữa TRẮNG ĐỨT ĐOẠN
            gc.setLineCap(StrokeLineCap.BUTT);
            gc.setStroke(LANE_CENTER);
            gc.setLineWidth(1.8);
            gc.setLineDashes(14, 10);
            strokePath(gc, pts, 0, 0);
            gc.setLineDashes((double[]) null);

            // 6. Mũi tên định hướng (Transparent Arrows)
            gc.setStroke(Color.rgb(255, 255, 255, 0.2));
            gc.setLineWidth(2.5);
            for (int i = 0; i < pts.size() - 1; i++) {
                drawArrow(gc, pts.get(i), pts.get(i + 1));
            }
        }
    }

    private void drawArrow(GraphicsContext gc, Vector2D p1, Vector2D p2) {
        double dx = p2.getX() - p1.getX();
        double dy = p2.getY() - p1.getY();
        double len = Math.hypot(dx, dy);
        if (len < 10) return;
        
        // Vẽ mũi tên ở giữa đoạn thẳng
        double mx = p1.getX() + dx / 2;
        double my = p1.getY() + dy / 2;
        double angle = Math.atan2(dy, dx);
        
        double arrowLen = 12;
        double arrowAngle = Math.PI / 6;
        
        double x1 = mx - arrowLen * Math.cos(angle - arrowAngle);
        double y1 = my - arrowLen * Math.sin(angle - arrowAngle);
        double x2 = mx - arrowLen * Math.cos(angle + arrowAngle);
        double y2 = my - arrowLen * Math.sin(angle + arrowAngle);
        
        gc.strokeLine(mx, my, x1, y1);
        gc.strokeLine(mx, my, x2, y2);
    }

    private void strokePath(GraphicsContext gc, List<Vector2D> pts, double dx, double dy) {
        for (int i = 0; i < pts.size() - 1; i++) {
            gc.strokeLine(
                pts.get(i).getX() + dx, pts.get(i).getY() + dy,
                pts.get(i + 1).getX() + dx, pts.get(i + 1).getY() + dy);
        }
    }

    private void strokePathOffset(GraphicsContext gc, List<Vector2D> pts, double offset) {
        for (int i = 0; i < pts.size() - 1; i++) {
            double[] o1 = perp(pts.get(i), pts.get(i + 1), offset);
            double[] o2 = perp(pts.get(i + 1), pts.get(i), -offset);
            gc.strokeLine(
                pts.get(i).getX() + o1[0], pts.get(i).getY() + o1[1],
                pts.get(i + 1).getX() + o2[0], pts.get(i + 1).getY() + o2[1]);
        }
    }

    /** Tính vector perpendicular offset (sang trái khi facing từ p1→p2) */
    private double[] perp(Vector2D p1, Vector2D p2, double offset) {
        double dx = p2.getX() - p1.getX();
        double dy = p2.getY() - p1.getY();
        double len = Math.hypot(dx, dy);
        if (len == 0) return new double[]{0, 0};
        double nx = -dy / len;
        double ny =  dx / len;
        return new double[]{nx * offset, ny * offset};
    }

    // =====================================================================
    //  3. ĐÈN GIAO THÔNG — glow lớn hơn BasicRenderer
    // =====================================================================
    private void drawLights(GraphicsContext gc) {
        for (TrafficLight light : lights) {
            if (light == null) continue;
            double x = light.getPosition().getX();
            double y = light.getPosition().getY();
            drawTrafficLight(gc, x, y, light);
        }
    }

    private void drawTrafficLight(GraphicsContext gc, double cx, double cy, TrafficLight light) {
        // Pill tối giản: 8×24px
        double pw = 8, ph = 24;
        double px = cx - pw / 2, py = cy - ph / 2;

        // Nền pill rất tối
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

        // 3 chấm nhỏ r=3
        drawBulb(gc, cx, py + 4,  "RED",    color, 3);
        drawBulb(gc, cx, py + 12, "YELLOW", color, 3);
        drawBulb(gc, cx, py + 20, "GREEN",  color, 3);

        // Số đếm — nhỏ, bên phải
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

    private void drawBulb(GraphicsContext gc, double cx, double cy,
                           String bulbColor, String activeColor, int r) {
        boolean on = bulbColor.equals(activeColor);

        Color onColor = switch (bulbColor) {
            case "RED"    -> Color.rgb(255, 65, 65);
            case "YELLOW" -> Color.rgb(255, 210, 40);
            default       -> Color.rgb(60, 220, 80);
        };
        Color offColor = switch (bulbColor) {
            case "RED"    -> Color.rgb(55, 18, 18);
            case "YELLOW" -> Color.rgb(55, 48, 10);
            default       -> Color.rgb(10, 55, 18);
        };

        if (on) {
            // Glow tối giản — nhỏ vừa đủ
            gc.setFill(Color.color(onColor.getRed(), onColor.getGreen(), onColor.getBlue(), 0.22));
            gc.fillOval(cx - r - 3, cy - r - 3, (r + 3) * 2, (r + 3) * 2);
        }

        gc.setFill(on ? onColor : offColor);
        gc.fillOval(cx - r, cy - r, r * 2, r * 2);

        if (on) {
            gc.setFill(Color.rgb(255, 255, 255, 0.28));
            gc.fillOval(cx - r + 1, cy - r + 1, r - 1, r - 1);
        }
    }

    // =====================================================================
    //  4. VẼ XE — sprite hoặc fallback 2D shapes
    // =====================================================================
    private void drawVehicles(GraphicsContext gc) {
        for (Vehicle v : vehicles) {
            if (v == null || v.getPosition() == null) continue;
            drawVehicle(gc, v);
        }
    }

    private void drawVehicle(GraphicsContext gc, Vehicle v) {
        double w = v.getWidth(), h = v.getHeight();
        double px = v.getPosition().getX();
        double py = v.getPosition().getY();

        // Đèn nhấp nháy đỏ/xanh cho xe ưu tiên (vẽ ngoài transform)
        if (v.isPriority()) {
            long t = System.currentTimeMillis() / 250;
            Color blinkColor = (t % 2 == 0)
                ? Color.rgb(255, 20, 20, 220.0 / 255)
                : Color.rgb(20, 100, 255, 220.0 / 255);
            gc.save();
            gc.translate(px, py);
            gc.rotate(v.getAngle());
            gc.setFill(blinkColor);
            gc.fillRoundRect(-w / 2, -h / 2 - 4, w, 4, 2, 2);
            gc.restore();
        }

        gc.save();
        gc.translate(px, py);
        gc.rotate(v.getAngle());   // JavaFX rotate() takes degrees

        // Viền cảnh báo STOP (cam)
        if (v.getYieldMode() == Vehicle.YieldMode.STOP) {
            gc.setStroke(Color.rgb(255, 140, 0, 160.0 / 255));
            gc.setLineWidth(2.5);
            gc.strokeRoundRect(-w / 2 - 4, -h / 2 - 4, w + 8, h + 8, 5, 5);
        }

        // Kiểm tra sprite
        if (sprites.containsKey(v.getTypeName())) {
            // ── Vẽ sprite ───────────────────────────────────────────────
            Image sprite = sprites.get(v.getTypeName());
            gc.drawImage(sprite, -w / 2, -h / 2, w, h);
        } else {
            // ── Fallback: vẽ 2D shapes giống BasicRenderer ──────────────

            Color base = VEHICLE_COLORS.getOrDefault(v.getTypeName(), Color.GRAY);
            Color dark = VEHICLE_COLORS_DARK.getOrDefault(v.getTypeName(), Color.DARKGRAY);

            // Shadow dưới xe
            gc.setFill(Color.rgb(0, 0, 0, 60.0 / 255));
            gc.fillRoundRect(-w / 2 + 2, -h / 2 + 2, w, h, 4, 4);

            // Thân xe — gradient dọc
            LinearGradient bodyGrad = new LinearGradient(
                -w / 2, 0, w / 2, 0, false, CycleMethod.NO_CYCLE,
                new Stop(0, base),
                new Stop(1, dark)
            );
            gc.setFill(bodyGrad);
            gc.fillRoundRect(-w / 2, -h / 2, w, h, 4, 4);

            // Highlight trên nóc
            gc.setFill(Color.rgb(255, 255, 255, 40.0 / 255));
            gc.fillRoundRect(-w / 2 + 2, -h / 2 + 1, w - 4, h / 2 - 2, 3, 3);

            // Viền ngoài xe
            gc.setStroke(Color.rgb(0, 0, 0, 120.0 / 255));
            gc.setLineWidth(1);
            gc.strokeRoundRect(-w / 2, -h / 2, w, h, 4, 4);

            // Bánh xe 4 góc
            drawWheels(gc, w, h);

            // Đèn hậu đỏ (phía sau xe)
            gc.setFill(Color.rgb(255, 50, 50, 200.0 / 255));
            gc.fillRect(-w / 2, -h / 2, 3, h);

            // Đèn pha trắng (phía trước)
            gc.setFill(Color.rgb(255, 255, 200, 180.0 / 255));
            gc.fillRect(w / 2 - 3, -h / 2, 3, h);
        }

        // Label tên & tốc độ (vẽ cho cả sprite lẫn 2D)
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("SansSerif", FontWeight.BOLD, 8));
        String label = v.getName().substring(0, Math.min(3, v.getName().length()));
        // Ước lượng chiều rộng text (JavaFX không có FontMetrics trực tiếp trên GC)
        double labelW = label.length() * 5.0;
        gc.fillText(label, -labelW / 2, 3);

        int spd = (int) v.getSpeed();
        gc.setFill(spd == 0 ? Color.rgb(255, 100, 100) : Color.rgb(180, 255, 180));
        gc.setFont(Font.font("SansSerif", FontWeight.NORMAL, 7));
        gc.fillText(spd + "", w / 2 - 12, h / 2 - 1);

        gc.restore();
    }

    private void drawWheels(GraphicsContext gc, double w, double h) {
        gc.setFill(Color.rgb(25, 25, 25));
        int wr = 4, wh = 3;
        // Bánh trước phải / trái
        gc.fillRoundRect(w / 2 - wr - 1, -h / 2 - wh, wr, wh, 2, 2);
        gc.fillRoundRect(-w / 2 + 1,      -h / 2 - wh, wr, wh, 2, 2);
        // Bánh sau phải / trái
        gc.fillRoundRect(w / 2 - wr - 1,  h / 2,       wr, wh, 2, 2);
        gc.fillRoundRect(-w / 2 + 1,       h / 2,       wr, wh, 2, 2);
    }

    // =====================================================================
    //  5. HUD ĐÈN GIAO THÔNG — góc trái trên
    // =====================================================================
    private void drawHUD(GraphicsContext gc, double w) {
        // Có thể thêm minimap, la bàn, v.v. nếu cần
        if (selectedVehicle != null) {
            double vx = selectedVehicle.getPosition().getX();
            double vy = selectedVehicle.getPosition().getY();
            
            gc.setFill(Color.rgb(20, 20, 30, 0.85));
            gc.fillRoundRect(vx + 15, vy - 40, 110, 45, 5, 5);
            gc.setStroke(Color.rgb(100, 150, 255, 0.5));
            gc.setLineWidth(1);
            gc.strokeRoundRect(vx + 15, vy - 40, 110, 45, 5, 5);
            
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Consolas", FontWeight.NORMAL, 11));
            String type = selectedVehicle.getClass().getSimpleName();
            String speedStr = String.format("Speed: %.1f", selectedVehicle.getSpeed());
            gc.fillText(type, vx + 22, vy - 23);
            gc.fillText(speedStr, vx + 22, vy - 8);
            
            // Draw highlight circle around selected vehicle
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(1.5);
            gc.setLineDashes(4, 4);
            gc.strokeOval(vx - 12, vy - 12, 24, 24);
            gc.setLineDashes((double[]) null);
        }

        if (lights.isEmpty()) return;

        int panelW = 180, rowH = 22;
        int panelH = lights.size() * rowH + 16;
        int px = 10, py = 10;

        // Nền HUD mờ (glassmorphism style)
        gc.setFill(Color.rgb(10, 12, 18, 200.0 / 255));
        gc.fillRoundRect(px, py, panelW, panelH, 10, 10);
        gc.setStroke(Color.rgb(255, 255, 255, 20.0 / 255));
        gc.setLineWidth(1);
        gc.strokeRoundRect(px, py, panelW, panelH, 10, 10);

        gc.setFont(Font.font("SansSerif", FontWeight.BOLD, 10));

        for (int i = 0; i < lights.size(); i++) {
            TrafficLight light = lights.get(i);
            int ry = py + 8 + i * rowH;

            // Chấm màu đèn
            Color dotColor = switch (light.getColor()) {
                case "GREEN"  -> Color.rgb(50, 220, 80);
                case "YELLOW" -> Color.rgb(255, 210, 40);
                default       -> Color.rgb(240, 70, 70);
            };

            // Glow nhỏ cho dot
            gc.setFill(Color.color(dotColor.getRed(), dotColor.getGreen(), dotColor.getBlue(), 60.0 / 255));
            gc.fillOval(px + 10 - 3, ry - 2, 14, 14);
            gc.setFill(dotColor);
            gc.fillOval(px + 10, ry, 9, 9);

            // Tên đèn
            gc.setFill(Color.rgb(200, 200, 200));
            gc.fillText(light.getColor(), px + 26, ry + 9);

            // Thời gian còn lại
            int tl = (int) light.getTimeLeft();
            gc.setFill(dotColor);
            String tStr = tl + "s";
            gc.fillText(tStr, px + panelW - 30, ry + 9);

            // Thanh tiến trình mini
            gc.setFill(Color.rgb(255, 255, 255, 20.0 / 255));
            gc.fillRoundRect(px + 26, ry + 11, panelW - 60, 3, 2, 2);
            double maxTime = 13.0;
            int barW = (int) Math.min((tl / maxTime) * (panelW - 60), panelW - 60);
            gc.setFill(dotColor);
            gc.fillRoundRect(px + 26, ry + 11, Math.max(barW, 2), 3, 2, 2);
        }
    }
}