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
    private static final Color ASPHALT   = Color.rgb(45, 48, 55);
    private static final Color EDGE_MARK = Color.rgb(255, 220, 60, 200.0 / 255);
    private static final Color BG_DARK   = Color.rgb(25, 30, 35);
    private static final Color BG_GRASS  = Color.rgb(35, 50, 32);

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
    public void draw(GraphicsContext gc, double w, double h) {
        drawBackground(gc, w, h);
        drawLanes(gc);
        drawLights(gc);
        drawVehicles(gc);
        drawHUD(gc, w);
    }

    // =====================================================================
    //  1. NỀN CỎ — gradient tối hơn BasicRenderer
    // =====================================================================
    private void drawBackground(GraphicsContext gc, double w, double h) {
        // Gradient nền tối hơn BasicRenderer
        LinearGradient bg = new LinearGradient(
            0, 0, w, h, false, CycleMethod.NO_CYCLE,
            new Stop(0, BG_DARK),
            new Stop(1, BG_GRASS)
        );
        gc.setFill(bg);
        gc.fillRect(0, 0, w, h);

        // Lưới mờ nhạt giúp có chiều sâu
        gc.setStroke(Color.rgb(255, 255, 255, 6.0 / 255));
        gc.setLineWidth(1);
        for (int x = 0; x < (int) w; x += 40) gc.strokeLine(x, 0, x, h);
        for (int y = 0; y < (int) h; y += 40) gc.strokeLine(0, y, w, y);
    }

    // =====================================================================
    //  2. VẼ ĐƯỜNG — asphalt 80px, vạch mép vàng
    // =====================================================================
    private void drawLanes(GraphicsContext gc) {
        if (lanes == null) return;

        for (Lane lane : lanes) {
            List<Vector2D> pts = lane.getwaypoints();
            if (pts == null || pts.size() < 2) continue;

            // Shadow nhẹ bên dưới đường
            gc.setStroke(Color.rgb(0, 0, 0, 60.0 / 255));
            gc.setLineWidth(84);
            gc.setLineCap(StrokeLineCap.ROUND);
            gc.setLineJoin(StrokeLineJoin.ROUND);
            for (int i = 0; i < pts.size() - 1; i++) {
                gc.strokeLine(
                    pts.get(i).getX() + 2, pts.get(i).getY() + 3,
                    pts.get(i + 1).getX() + 2, pts.get(i + 1).getY() + 3
                );
            }

            // Mặt đường asphalt (rộng 80px)
            gc.setStroke(ASPHALT);
            gc.setLineWidth(80);
            gc.setLineCap(StrokeLineCap.ROUND);
            gc.setLineJoin(StrokeLineJoin.ROUND);
            for (int i = 0; i < pts.size() - 1; i++) {
                gc.strokeLine(
                    pts.get(i).getX(), pts.get(i).getY(),
                    pts.get(i + 1).getX(), pts.get(i + 1).getY()
                );
            }

            // Highlight nhẹ ở giữa mặt đường (ánh đèn phản chiếu)
            gc.setStroke(Color.rgb(255, 255, 255, 8.0 / 255));
            gc.setLineWidth(30);
            gc.setLineCap(StrokeLineCap.ROUND);
            gc.setLineJoin(StrokeLineJoin.ROUND);
            for (int i = 0; i < pts.size() - 1; i++) {
                gc.strokeLine(
                    pts.get(i).getX(), pts.get(i).getY(),
                    pts.get(i + 1).getX(), pts.get(i + 1).getY()
                );
            }

            // Vạch mép đường vàng (2 bên, offset=40)
            gc.setStroke(EDGE_MARK);
            gc.setLineDashes(18, 8);
            gc.setLineWidth(1.5);
            gc.setLineCap(StrokeLineCap.BUTT);
            gc.setLineJoin(StrokeLineJoin.BEVEL);
            for (int side : new int[]{-1, 1}) {
                for (int i = 0; i < pts.size() - 1; i++) {
                    double[] off  = perp(pts.get(i), pts.get(i + 1), 40.0 * side);
                    double[] off2 = perp(pts.get(i + 1), pts.get(i), -40.0 * side);
                    gc.strokeLine(
                        pts.get(i).getX()     + off[0],  pts.get(i).getY()     + off[1],
                        pts.get(i + 1).getX() + off2[0], pts.get(i + 1).getY() + off2[1]
                    );
                }
            }
            // Reset dashes
            gc.setLineDashes((double[]) null);
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
        // Kích thước nhỏ gọn: 14×38
        int bw = 14, bh = 38;
        double bx = cx - bw / 2.0, by = cy - bh / 2.0;

        // Khung đèn — pill shape tối
        gc.setFill(Color.rgb(20, 22, 26));
        gc.fillRoundRect(bx - 1, by - 1, bw + 2, bh + 2, 8, 8);
        gc.setStroke(Color.rgb(50, 54, 62));
        gc.setLineWidth(1);
        gc.strokeRoundRect(bx, by, bw, bh, 8, 8);

        String color = light.getColor();

        // Vẽ 3 bóng đèn — glow lớn hơn BasicRenderer (r+5 thay vì r+3)
        drawBulb(gc, cx, by + 6,  "RED",    color, 9);
        drawBulb(gc, cx, by + 20, "YELLOW", color, 9);
        drawBulb(gc, cx, by + 34, "GREEN",  color, 9);

        // Số đếm ngược nhỏ bên cạnh
        String display = light.getDisplay();
        if (!display.isEmpty()) {
            gc.setFont(Font.font("SansSerif", FontWeight.BOLD, 10));
            Color textColor = switch (color) {
                case "GREEN"  -> Color.rgb(80, 220, 80);
                case "YELLOW" -> Color.rgb(255, 220, 50);
                default       -> Color.rgb(255, 90, 90);
            };
            gc.setFill(Color.rgb(0, 0, 0, 130.0 / 255));
            gc.fillRoundRect(cx + bw / 2.0 + 3, cy - 8, 20, 14, 4, 4);
            gc.setFill(textColor);
            gc.fillText(display, cx + bw / 2.0 + 5, cy + 3);
        }

        // Huy hiệu vàng nếu ở chế độ thủ công
        if (light.isManualMode()) {
            gc.setStroke(Color.rgb(255, 200, 0));
            gc.setLineWidth(1.5);
            gc.strokeRoundRect(bx - 3, by - 3, bw + 6, bh + 6, 10, 10);
        }
    }

    private void drawBulb(GraphicsContext gc, double cx, double cy,
                           String bulbColor, String activeColor, int r) {
        boolean on = bulbColor.equals(activeColor);

        Color onColor = switch (bulbColor) {
            case "RED"    -> Color.rgb(255, 60, 60);
            case "YELLOW" -> Color.rgb(255, 215, 0);
            default       -> Color.rgb(60, 220, 60);
        };
        Color offColor = switch (bulbColor) {
            case "RED"    -> Color.rgb(80, 20, 20);
            case "YELLOW" -> Color.rgb(80, 70, 0);
            default       -> Color.rgb(10, 65, 10);
        };

        if (on) {
            // Quầng sáng lớn hơn BasicRenderer (r+5 thay vì r+3)
            Color glow = Color.rgb(
                (int) (onColor.getRed() * 255),
                (int) (onColor.getGreen() * 255),
                (int) (onColor.getBlue() * 255),
                50.0 / 255
            );
            gc.setFill(glow);
            gc.fillOval(cx - r - 5, cy - r - 5, (r + 5) * 2, (r + 5) * 2);
        }

        // Bóng đèn
        gc.setFill(on ? onColor : offColor);
        gc.fillOval(cx - r, cy - r, r * 2, r * 2);

        if (on) {
            // Highlight nhỏ ở góc trái trên
            gc.setFill(Color.rgb(255, 255, 255, 80.0 / 255));
            gc.fillOval(cx - r + 2, cy - r + 2, r - 2, r - 2);
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
    private void drawHUD(GraphicsContext gc, double canvasW) {
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