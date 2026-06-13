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
    private static final Color LANE_EDGE    = Color.rgb(255, 210, 50, 0.90);  // vạch mép vàng
    private static final Color LANE_CENTER  = Color.rgb(255, 255, 255, 0.55); // vạch giữa trắng
    private static final Color BG_DARK      = Color.rgb(18, 28, 16);
    private static final Color BG_GRASS     = Color.rgb(28, 45, 24);

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
        drawBuildings(gc, w, h);      // nhà dân cư (dưới cùng)
        drawLanes(gc);                // đường xe chạy (kẻ vạch qua ngã tư)
        drawIntersections(gc);        // ngã giao (xóa vạch cũ, vẽ chuẩn)
        drawPedestrians(gc);         // người đi bộ
        drawTrees(gc, w, h);         // cây sồi (có bóng đổ xuống đường)
        drawLights(gc);              // đèn giao thông
        drawVehicles(gc);            // xe
        drawRain(gc, w, h);          // hiệu ứng mưa trơn trượt
        drawHUD(gc, w);              // HUD
    }

    // =====================================================================
    //  1. NỀN CỎ — gradient tối hơn BasicRenderer
    // =====================================================================
    private void drawBackground(GraphicsContext gc, double w, double h) {
        LinearGradient bg = new LinearGradient(
            0, 0, w, h, false, CycleMethod.NO_CYCLE,
            new Stop(0, BG_DARK),
            new Stop(1, BG_GRASS)
        );
        gc.setFill(bg);
        gc.fillRect(0, 0, w, h);

        // Lưới cỏ mờ nhạt
        gc.setStroke(Color.rgb(0, 0, 0, 0.08));
        gc.setLineWidth(1);
        gc.setLineDashes();
        for (int x = 0; x < (int) w; x += 32) gc.strokeLine(x, 0, x, h);
        for (int y = 0; y < (int) h; y += 32) gc.strokeLine(0, y, w, y);
    }

    // =====================================================================
    //  1.5. TÒA NHÀ (Glass Skyscrapers & Brick Buildings)
    // =====================================================================
    @Override
    protected void drawBuildings(GraphicsContext gc, double canvasW, double canvasH) {
        // Cố định seed để nhà không bị giật khi render lại
        java.util.Random rnd = new java.util.Random(42);
        
        int bSize = 60;
        int padding = 20;
        int streetClearance = 80;

        for (int y = padding; y < canvasH - padding; y += bSize + padding) {
            for (int x = padding; x < canvasW - padding; x += bSize + padding) {
                // Kiểm tra xem tòa nhà có đè lên đường không
                boolean isClear = true;
                for (Lane lane : lanes) {
                    if (lane == null) continue;
                    // Kiểm tra khoảng cách từ tâm tòa nhà đến đường (rất cơ bản)
                    double cx = x + bSize / 2.0;
                    double cy = y + bSize / 2.0;
                    double lx1 = lane.getStart().getX(), ly1 = lane.getStart().getY();
                    double lx2 = lane.getEnd().getX(), ly2 = lane.getEnd().getY();
                    
                    // Khoảng cách từ điểm đến đoạn thẳng
                    double dist = pointToLineDistance(cx, cy, lx1, ly1, lx2, ly2);
                    if (dist < streetClearance) {
                        isClear = false;
                        break;
                    }
                }

                if (isClear && rnd.nextDouble() > 0.2) { // 80% có nhà
                    boolean isGlass = rnd.nextDouble() > 0.4; // 60% nhà kính
                    double bw = bSize + rnd.nextInt(20);
                    double bh = bSize + rnd.nextInt(30);
                    
                    // Vẽ bóng đổ (Soft shadow)
                    gc.setFill(Color.rgb(0, 0, 0, 0.4));
                    gc.fillRoundRect(x + 10, y + 10, bw, bh, 8, 8);

                    if (isGlass) {
                        // Tòa nhà kính (Glass Skyscraper)
                        LinearGradient glassGrad = new LinearGradient(
                            x, y, x + bw, y + bh, false, CycleMethod.NO_CYCLE,
                            new Stop(0, Color.rgb(180, 220, 255, 0.9)), // Phản chiếu bầu trời
                            new Stop(1, Color.rgb(40, 80, 140, 0.9))
                        );
                        gc.setFill(glassGrad);
                        gc.fillRoundRect(x, y, bw, bh, 4, 4);
                        
                        // Lưới vân kính
                        gc.setStroke(Color.rgb(255, 255, 255, 0.3));
                        gc.setLineWidth(1.0);
                        for(double i = 5; i < bw; i += 10) gc.strokeLine(x+i, y, x+i, y+bh);
                        for(double i = 5; i < bh; i += 10) gc.strokeLine(x, y+i, x+bw, y+i);
                        
                    } else {
                        // Nhà gạch (Low-poly Brick)
                        gc.setFill(Color.rgb(140, 60, 50));
                        gc.fillRoundRect(x, y, bw, bh, 4, 4);
                        
                        // Mái bằng phẳng màu xám sậm
                        gc.setFill(Color.rgb(80, 80, 80));
                        gc.fillRoundRect(x+4, y+4, bw-8, bh-8, 2, 2);
                        
                        // Mặt tiền (storefront giả lập)
                        gc.setFill(Color.rgb(255, 240, 180, 0.8));
                        gc.fillRect(x + bw/2 - 10, y + bh - 6, 20, 6);
                    }
                    
                    // Viền nóc nhà
                    gc.setStroke(Color.rgb(30, 30, 30, 0.8));
                    gc.setLineWidth(1.5);
                    gc.strokeRoundRect(x, y, bw, bh, 4, 4);
                }
            }
        }
    }

    private double pointToLineDistance(double px, double py, double x1, double y1, double x2, double y2) {
        double A = px - x1;
        double B = py - y1;
        double C = x2 - x1;
        double D = y2 - y1;
        double dot = A * C + B * D;
        double lenSq = C * C + D * D;
        double param = -1;
        if (lenSq != 0) param = dot / lenSq;
        double xx, yy;
        if (param < 0) {
            xx = x1; yy = y1;
        } else if (param > 1) {
            xx = x2; yy = y2;
        } else {
            xx = x1 + param * C; yy = y1 + param * D;
        }
        double dx = px - xx;
        double dy = py - yy;
        return Math.sqrt(dx * dx + dy * dy);
    }

    // =====================================================================
    //  1.6. CÂY XANH & NGƯỜI ĐI BỘ
    // =====================================================================
    private void drawTrees(GraphicsContext gc, double canvasW, double canvasH) {
        java.util.Random rnd = new java.util.Random(123);
        
        for (int i = 0; i < 40; i++) { // Vẽ 40 cụm cây
            double tx = rnd.nextDouble() * canvasW;
            double ty = rnd.nextDouble() * canvasH;
            
            // Chỉ vẽ cây nếu gần đường (vỉa hè) nhưng không đè lên giữa đường
            boolean nearRoad = false;
            boolean onRoad = false;
            for (Lane lane : lanes) {
                if (lane == null) continue;
                double dist = pointToLineDistance(tx, ty, lane.getStart().getX(), lane.getStart().getY(), lane.getEnd().getX(), lane.getEnd().getY());
                if (dist > 45 && dist < 120) nearRoad = true;
                if (dist <= 45) onRoad = true; // Đường rộng 80 (bán kính 40)
            }
            
            if (nearRoad && !onRoad) {
                // Tán cây sồi lớn: bóng đổ trước
                double treeR = 25 + rnd.nextDouble() * 15;
                gc.setFill(Color.rgb(0, 0, 0, 0.35));
                gc.fillOval(tx - treeR + 8, ty - treeR + 8, treeR * 2, treeR * 2);
                
                // Các lớp lá đè lên nhau
                for (int j = 0; j < 3; j++) {
                    double leafR = treeR * (1.0 - j*0.2);
                    double ox = (rnd.nextDouble() - 0.5) * 10;
                    double oy = (rnd.nextDouble() - 0.5) * 10;
                    
                    // Màu xanh lá đậm -> nhạt (với chút vàng của sồi già)
                    gc.setFill(Color.rgb(30 + j*15, 80 + j*25 + rnd.nextInt(20), 20 + j*10));
                    gc.fillOval(tx - leafR + ox, ty - leafR + oy, leafR * 2, leafR * 2);
                }
            }
        }
    }

    private void drawPedestrians(GraphicsContext gc) {
        java.util.Random rnd = new java.util.Random(999);
        // Tìm các vị trí ngã tư (Intersection) để vẽ người đứng chờ
        for (com.traffic.map.Intersection inter : intersections) {
            if (inter == null) continue;
            double cx = inter.getCenter().getX();
            double cy = inter.getCenter().getY();
            
            // Vẽ 3-5 nhóm người ở 4 góc
            int numGroups = 3 + rnd.nextInt(3);
            for (int g = 0; g < numGroups; g++) {
                double angle = rnd.nextDouble() * Math.PI * 2;
                double dist = 90 + rnd.nextDouble() * 30; // Đứng dạt ra ngoài rìa ngã tư
                double px = cx + Math.cos(angle) * dist;
                double py = cy + Math.sin(angle) * dist;
                
                // 1-3 người mỗi nhóm
                int people = 1 + rnd.nextInt(3);
                for (int p = 0; p < people; p++) {
                    double ox = px + (rnd.nextDouble() - 0.5) * 12;
                    double oy = py + (rnd.nextDouble() - 0.5) * 12;
                    
                    // Bóng đổ
                    gc.setFill(Color.rgb(0, 0, 0, 0.4));
                    gc.fillOval(ox - 3, oy - 3, 6, 6);
                    
                    // Vai áo (Màu sắc đa dạng)
                    int r = 50 + rnd.nextInt(200);
                    int gC = 50 + rnd.nextInt(200);
                    int b = 50 + rnd.nextInt(200);
                    gc.setFill(Color.rgb(r, gC, b));
                    gc.fillOval(ox - 3.5, oy - 2.5, 7, 5); // Vai áo ngang
                    
                    // Đầu
                    gc.setFill(Color.rgb(240, 200, 180));
                    gc.fillOval(ox - 2, oy - 2, 4, 4);
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

            // 1. Shadow mềm
            gc.setStroke(Color.rgb(0, 0, 0, 0.30));
            gc.setLineWidth(88);
            strokePath(gc, pts, 3, 4);

            // 2. Mặt đường asphalt
            gc.setStroke(ASPHALT);
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
        }
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
        DebugVisualState debugState = VehicleDebugClassifier.classify(v);

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

        drawDebugStateBorder(gc, debugState, w, h);

        // Kiểm tra sprite
        if (sprites.containsKey(v.getTypeName())) {
            // ── Vẽ sprite ───────────────────────────────────────────────
            Image sprite = sprites.get(v.getTypeName());
            gc.drawImage(sprite, -w / 2, -h / 2, w, h);
        } else {
            // ── Fallback: vẽ 2D shapes giống BasicRenderer ──────────────

            Color base = VEHICLE_COLORS.getOrDefault(v.getTypeName(), Color.GRAY);
            Color dark = VEHICLE_COLORS_DARK.getOrDefault(v.getTypeName(), Color.DARKGRAY);

            boolean isTaxi = false;
            boolean isTruck = false;

            if (v.getTypeName().equals("car")) {
                int hash = Math.abs(v.getName().hashCode());
                if (hash % 4 == 0) {
                    base = Color.rgb(240, 200, 20); // Yellow Taxi
                    dark = Color.rgb(180, 150, 10);
                    isTaxi = true;
                } else if (hash % 4 == 1) {
                    base = Color.rgb(210, 30, 40); // Red Sports Car
                    dark = Color.rgb(150, 10, 20);
                } else if (hash % 4 == 2) {
                    base = Color.rgb(235, 235, 240); // White Delivery Truck
                    dark = Color.rgb(170, 170, 180);
                    isTruck = true;
                }
            }

            // Shadow dưới xe
            gc.setFill(Color.rgb(0, 0, 0, 0.5));
            gc.fillRoundRect(-w / 2 + 3, -h / 2 + 3, w, h, 4, 4);

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

            if (isTaxi) {
                // Vẽ biển TAXI trên nóc
                gc.setFill(Color.BLACK);
                gc.fillRect(-4, -h / 2 + h / 2 - 2, 8, 4);
                gc.setFill(Color.YELLOW);
                gc.fillRect(-3, -h / 2 + h / 2 - 1, 6, 2);
            } else if (isTruck) {
                // Thùng xe tải chở hàng (phía sau)
                gc.setFill(Color.rgb(200, 200, 210));
                gc.fillRoundRect(-w / 2, -h / 2, w * 0.7, h, 2, 2);
                // Viền thùng xe
                gc.setStroke(Color.rgb(150, 150, 160));
                gc.setLineWidth(1.0);
                gc.strokeRoundRect(-w / 2, -h / 2, w * 0.7, h, 2, 2);
            }

            // Đèn hậu đỏ (phía sau xe)
            gc.setFill(Color.rgb(255, 50, 50, 200.0 / 255));
            gc.fillRect(-w / 2, -h / 2, 3, h);

            // Đèn pha trắng (phía trước)
            gc.setFill(Color.rgb(255, 255, 200, 180.0 / 255));
            gc.fillRect(w / 2 - 3, -h / 2, 3, h);
        }

        gc.restore();
        drawTurnIntentBadge(gc, v, px, py, w, h);
        drawDebugStateCue(gc, debugState, px, py, w, h);
    }

    private void drawDebugStateBorder(GraphicsContext gc, DebugVisualState state, double w, double h) {
        if (state == null || state == DebugVisualState.NORMAL) return;
        Color color = switch (state) {
            case ERROR -> Color.rgb(255, 45, 180, 240.0 / 255);
            case EMERGENCY_YIELD -> Color.rgb(235, 45, 45, 235.0 / 255);
            case PRIORITY_QUEUE -> Color.rgb(255, 115, 95, 225.0 / 255);
            case TURNING_OR_INTERSECTION -> Color.rgb(165, 95, 255, 220.0 / 255);
            case ORDINARY_WAIT -> Color.rgb(255, 180, 45, 220.0 / 255);
            case GAP_FILL -> Color.rgb(40, 210, 190, 220.0 / 255);
            case OVERTAKE -> Color.rgb(70, 150, 255, 220.0 / 255);
            default -> Color.TRANSPARENT;
        };
        gc.setStroke(color);
        gc.setLineWidth(state == DebugVisualState.EMERGENCY_YIELD ? 3.0 : 2.2);
        if (state == DebugVisualState.PRIORITY_QUEUE) {
            gc.setLineDashes(5, 4);
        } else {
            gc.setLineDashes();
        }
        gc.strokeRoundRect(-w / 2 - 4, -h / 2 - 4, w + 8, h + 8, 5, 5);
        gc.setLineDashes();
    }

    private void drawDebugStateCue(GraphicsContext gc, DebugVisualState state, double px, double py, double w, double h) {
        if (state == null || state == DebugVisualState.NORMAL) return;
        String cue = switch (state) {
            case EMERGENCY_YIELD -> "E";
            case PRIORITY_QUEUE -> "Q";
            case TURNING_OR_INTERSECTION -> "T";
            case ORDINARY_WAIT -> "W";
            case GAP_FILL -> "G";
            case OVERTAKE -> "O";
            case ERROR -> "!";
            default -> "";
        };
        if (cue.isEmpty()) return;
        Color bg = switch (state) {
            case ERROR -> Color.rgb(255, 45, 180, 230.0 / 255);
            case EMERGENCY_YIELD -> Color.rgb(235, 45, 45, 230.0 / 255);
            case PRIORITY_QUEUE -> Color.rgb(255, 115, 95, 230.0 / 255);
            case TURNING_OR_INTERSECTION -> Color.rgb(165, 95, 255, 225.0 / 255);
            case ORDINARY_WAIT -> Color.rgb(255, 180, 45, 225.0 / 255);
            case GAP_FILL -> Color.rgb(40, 210, 190, 225.0 / 255);
            case OVERTAKE -> Color.rgb(70, 150, 255, 225.0 / 255);
            default -> Color.TRANSPARENT;
        };
        double size = 14.0;
        double x = px + w / 2.0 + 5.0;
        double y = py + h / 2.0 + 2.0;
        gc.save();
        gc.setFill(bg);
        gc.fillRoundRect(x, y, size, size, 4, 4);
        gc.setStroke(Color.rgb(10, 10, 12, 140.0 / 255));
        gc.strokeRoundRect(x, y, size, size, 4, 4);
        gc.setFill(Color.rgb(255, 255, 255, 245.0 / 255));
        gc.setFont(Font.font("SansSerif", FontWeight.BOLD, 9));
        gc.fillText(cue, x + 4.0, y + 10.5);
        gc.restore();
    }

    private void drawTurnIntentBadge(GraphicsContext gc, Vehicle v, double px, double py, double w, double h) {
        if (v == null) return;
        String turn = v.getTurnIntentLabel();
        if (turn == null || turn.isBlank()) return;

        double badge = 20.0;
        double bx = px - badge / 2.0;
        double by = py - Math.max(w, h) / 2.0 - badge - 6.0;
        boolean preparing = v.getIntersectionManeuverState() == Vehicle.IntersectionManeuverState.PREPARING_TURN_SLOT;
        boolean waiting = v.getIntersectionManeuverState() == Vehicle.IntersectionManeuverState.WAITING_BEFORE_INTERSECTION;
        Color bg = waiting ? Color.rgb(255, 170, 35, 225.0 / 255)
                : preparing ? Color.rgb(80, 170, 255, 210.0 / 255)
                : Color.rgb(20, 20, 25, 185.0 / 255);
        Color fg = waiting ? Color.rgb(30, 25, 10) : Color.rgb(255, 255, 255, 235.0 / 255);

        gc.save();
        gc.setFill(bg);
        gc.fillRoundRect(bx, by, badge, badge, 5, 5);
        gc.setStroke(Color.rgb(255, 255, 255, 95.0 / 255));
        gc.setLineWidth(0.8);
        gc.strokeRoundRect(bx, by, badge, badge, 5, 5);
        gc.setFill(fg);
        gc.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
        gc.fillText(turn, bx + 6.0, by + 14.5);
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