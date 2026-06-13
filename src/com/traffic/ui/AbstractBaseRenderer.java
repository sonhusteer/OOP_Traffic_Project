package com.traffic.ui;

import com.traffic.core.IRenderer;
import com.traffic.core.Vehicle;
import com.traffic.map.Intersection;
import com.traffic.map.Lane;
import com.traffic.map.TrafficLight;
import com.traffic.core.Vector2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.*;
import javafx.scene.shape.StrokeLineCap;
import java.util.ArrayList;
import java.util.List;

/**
 * Lớp cha trừu tượng — logic vẽ dùng chung.
 */
public abstract class AbstractBaseRenderer implements IRenderer {

    protected List<Lane> lanes = new ArrayList<>();
    protected final List<Vehicle> vehicles = new ArrayList<>();
    protected final List<TrafficLight> lights = new ArrayList<>();
    protected final List<Intersection> intersections = new ArrayList<>();

    // ── Hiệu ứng Mưa (Rain Overlay) ──────────────────────────────────────
    private static final int NUM_DROPS = 200;
    private final double[] rainX = new double[NUM_DROPS];
    private final double[] rainY = new double[NUM_DROPS];
    private final double[] rainSpeed = new double[NUM_DROPS];
    private final double[] rainLength = new double[NUM_DROPS];
    private boolean rainInitialized = false;
    private boolean isRaining = false; // Mặc định tắt mưa

    public void setRaining(boolean raining) {
        this.isRaining = raining;
    }

    private void initRain(double w, double h) {
        for (int i = 0; i < NUM_DROPS; i++) {
            rainX[i] = Math.random() * w;
            rainY[i] = Math.random() * h;
            rainSpeed[i] = 15 + Math.random() * 15;
            rainLength[i] = 10 + Math.random() * 25;
        }
        rainInitialized = true;
    }

    protected void drawRain(GraphicsContext gc, double w, double h) {
        if (!isRaining)
            return; // Không vẽ mưa nếu đang tắt

        if (!rainInitialized)
            initRain(w, h);

        // Phủ lớp xanh đen mờ để tạo không khí ẩm ướt, trơn trượt
        gc.setFill(Color.rgb(10, 20, 30, 0.25));
        gc.fillRect(0, 0, w, h);

        // Vẽ hạt mưa rơi chéo
        gc.setStroke(Color.rgb(180, 200, 240, 0.5));
        for (int i = 0; i < NUM_DROPS; i++) {
            gc.setLineWidth(Math.max(0.5, rainLength[i] / 15.0));
            gc.strokeLine(rainX[i], rainY[i], rainX[i] + rainLength[i] / 4, rainY[i] + rainLength[i]);

            // Cập nhật tọa độ hạt mưa (mưa bay chéo xuống)
            rainX[i] += rainSpeed[i] / 4;
            rainY[i] += rainSpeed[i];

            // Nếu rơi quá màn hình thì reset lên trên
            if (rainY[i] > h) {
                rainY[i] = -rainLength[i];
                rainX[i] = Math.random() * w;
            }
        }
    }

    protected static final double ROAD_HALF = 40.0;
    private static final int LIGHT_HIT = 45;

    public AbstractBaseRenderer(List<Lane> lanes) {
        this.lanes = lanes;
    }

    public void setLanes(List<Lane> newLanes) {
        this.lanes = newLanes;
    }

    public void handleClick(double x, double y, boolean leftButton) {
        for (TrafficLight light : lights) {
            if (light == null)
                continue;
            double dist = Math.hypot(x - light.getPosition().getX(),
                    y - light.getPosition().getY());
            if (dist <= LIGHT_HIT) {
                if (leftButton) {
                    light.setManualMode(true);
                    light.manualSwitch();
                } else {
                    light.setManualMode(false);
                }
                break;
            }
        }
    }

    @Override
    public void clear() {
        vehicles.clear();
        lights.clear();
        intersections.clear();
    }

    @Override
    public void renderVehicles(List<Vehicle> list) {
        vehicles.addAll(list);
    }

    @Override
    public void renderLights(List<TrafficLight> list) {
        lights.addAll(list);
    }

    @Override
    public void renderIntersections(List<Intersection> list) {
        intersections.addAll(list);
    }

    // =========================================================================
    // NHÀ DÂN CƯ — vẽ TRƯỚC đường, DƯỚI mọi thứ khác
    // =========================================================================

    /** Palette mái nhà (top-down view) */
    private static final Color[] ROOF_COLORS = {
            Color.rgb(112, 88, 76), // nâu gỗ
            Color.rgb(88, 88, 92), // xám nguội
            Color.rgb(128, 96, 84), // gạch terra
            Color.rgb(76, 90, 96), // xanh đá
            Color.rgb(104, 82, 76), // đỏ gỉ
            Color.rgb(92, 90, 76), // ô liu
            Color.rgb(82, 82, 88), // than
            Color.rgb(118, 100, 92), // cát
            Color.rgb(96, 76, 72), // nâu đỏ
            Color.rgb(72, 96, 80), // xanh rêu (nhà vườn)
    };

    protected void drawBuildings(GraphicsContext gc, double canvasW, double canvasH) {
        int step = 58;

        for (int gx = 0; gx * step < canvasW; gx++) {
            for (int gy = 0; gy * step < canvasH; gy++) {

                double cx = gx * step + step / 2.0;
                double cy = gy * step + step / 2.0;

                // Bỏ qua ô quá gần đường hoặc ngã giao
                if (isTooCloseToRoad(cx, cy, 54))
                    continue;

                // Hash tất định theo ô — đảm bảo ổn định giữa các frame
                long h = hash2(gx, gy);

                // ~30% ô trống (vườn / không gian)
                if (Math.abs(h % 10) < 3)
                    continue;

                double bw = 18 + Math.abs(h % 24);
                double bh = 18 + Math.abs((h >> 8) % 24);
                int ci = (int) Math.abs(h % ROOF_COLORS.length);

                double bx = cx - bw / 2 + clamp((h % 7) - 3, -6, 6);
                double by = cy - bh / 2 + clamp(((h >> 4) % 7) - 3, -6, 6);

                Color roof = ROOF_COLORS[ci];
                Color wall = roof.deriveColor(0, 0.85, 0.62, 1.0);

                // Drop-shadow
                gc.setFill(Color.rgb(0, 0, 0, 0.28));
                gc.fillRoundRect(bx + 3, by + 3, bw, bh, 3, 3);

                // Tường (viền ngoài)
                gc.setFill(wall);
                gc.fillRoundRect(bx, by, bw, bh, 3, 3);

                // Mái nhà (bên trong)
                gc.setFill(roof);
                gc.fillRoundRect(bx + 2, by + 2, bw - 4, bh - 4, 2, 2);

                // Chi tiết mái
                boolean flatRoof = Math.abs(h % 3) != 0;
                if (flatRoof && bw > 22 && bh > 22) {
                    // Mái bằng: thiết bị hoặc cửa trời
                    gc.setFill(Color.rgb(180, 185, 190, 0.38));
                    gc.fillRoundRect(bx + bw * 0.3, by + bh * 0.3,
                            bw * 0.4, bh * 0.4, 2, 2);
                } else {
                    // Mái dốc: gờ giữa
                    Color ridge = wall.deriveColor(0, 1, 0.55, 1);
                    gc.setStroke(ridge);
                    gc.setLineWidth(1.0);
                    gc.setLineDashes((double[]) null);
                    if (bw >= bh) {
                        gc.strokeLine(bx + 3, by + bh / 2, bx + bw - 3, by + bh / 2);
                    } else {
                        gc.strokeLine(bx + bw / 2, by + 3, bx + bw / 2, by + bh - 3);
                    }
                }

                // Cửa sổ
                if (bw > 26 && bh > 26 && Math.abs(h % 2) == 0) {
                    gc.setFill(Color.rgb(200, 235, 255, 0.55));
                    gc.fillRect(bx + 4, by + 4, 4, 3);
                    gc.fillRect(bx + bw - 9, by + 4, 4, 3);
                    if (bh > 32) {
                        gc.fillRect(bx + 4, by + bh - 8, 4, 3);
                        gc.fillRect(bx + bw - 9, by + bh - 8, 4, 3);
                    }
                }

                // Vườn nhỏ (cây xanh) cạnh nhà xác suất thấp
                if (Math.abs(h % 7) == 0 && bx > 8 && by > 8
                        && bx + bw + 12 < canvasW && by + bh + 12 < canvasH) {
                    gc.setFill(Color.rgb(46, 80, 40, 0.60));
                    double tx = bx + bw + 5, ty = by + bh / 2.0;
                    gc.fillOval(tx - 6, ty - 6, 12, 12);
                    gc.setFill(Color.rgb(60, 110, 50, 0.50));
                    gc.fillOval(tx - 4, ty - 4, 8, 8);
                }
            }
        }
    }

    private boolean isTooCloseToRoad(double px, double py, double thr) {
        if (lanes == null)
            return false;
        for (Lane lane : lanes) {
            List<Vector2D> pts = lane.getwaypoints();
            for (int i = 0; i < pts.size() - 1; i++) {
                if (distSeg(px, py,
                        pts.get(i).getX(), pts.get(i).getY(),
                        pts.get(i + 1).getX(), pts.get(i + 1).getY()) < thr)
                    return true;
            }
        }
        // Kiểm tra thêm với intersections
        for (Intersection inter : intersections) {
            double[] box = calcIntersectionBox(inter);
            if (px > box[0] - 10 && px < box[2] + 10 &&
                    py > box[1] - 10 && py < box[3] + 10)
                return true;
        }
        return false;
    }

    private double distSeg(double px, double py,
            double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        if (dx == 0 && dy == 0)
            return Math.hypot(px - ax, py - ay);
        double t = Math.max(0, Math.min(1,
                ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)));
        return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }

    private long hash2(int x, int y) {
        long h = (long) x * 73856093L ^ (long) y * 19349663L;
        h ^= (h >>> 16);
        h *= 0x45d9f3bL;
        h ^= (h >>> 16);
        return h;
    }

    private double clamp(long v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // =========================================================================
    // NGÃ GIAO — vẽ SAU buildings, TRƯỚC đường
    // =========================================================================

    protected void drawIntersections(GraphicsContext gc) {
        for (Intersection inter : intersections) {
            if (inter.getType() == Intersection.Type.FIVE_WAY) {
                double cx = inter.getCenter().getX();
                double cy = inter.getCenter().getY();

                // Vẽ bùng binh như một vòng xuyến thật: nền phủ lớn che các nhánh,
                // một vòng lane rõ ràng, đảo xanh ở giữa, vạch vòng + mũi tên lưu thông.
                double coverR = 132.0;
                double outerR = 110.0;
                double innerR = 48.0;
                double islandR = 44.0;
                double laneGuideR = 66.0;

                gc.setFill(Color.rgb(0, 0, 0, 0.24));
                gc.fillOval(cx - coverR + 4, cy - coverR + 5, coverR * 2, coverR * 2);

                gc.setFill(Color.rgb(45, 48, 55));
                gc.fillOval(cx - coverR, cy - coverR, coverR * 2, coverR * 2);

                gc.setFill(Color.rgb(38, 41, 50));
                gc.fillOval(cx - outerR, cy - outerR, outerR * 2, outerR * 2);

                gc.setFill(Color.rgb(255, 255, 255, 0.045));
                gc.fillOval(cx - outerR * 0.86, cy - outerR * 0.86, outerR * 1.72, outerR * 1.72);

                // Viền ngoài và viền trong màu vàng nhạt để người xem nhận ra đây là vòng xuyến.
                gc.setLineDashes((double[]) null);
                gc.setStroke(Color.rgb(255, 210, 50, 0.82));
                gc.setLineWidth(2.4);
                gc.strokeOval(cx - outerR + 7, cy - outerR + 7, (outerR - 7) * 2, (outerR - 7) * 2);
                gc.setStroke(Color.rgb(255, 210, 50, 0.55));
                gc.setLineWidth(2.0);
                gc.strokeOval(cx - innerR, cy - innerR, innerR * 2, innerR * 2);

                gc.setStroke(Color.rgb(255, 255, 255, 0.62));
                gc.setLineWidth(2.0);
                gc.setLineDashes(14, 10);
                gc.strokeOval(cx - laneGuideR, cy - laneGuideR, laneGuideR * 2, laneGuideR * 2);
                gc.setLineDashes((double[]) null);

                for (int i = 0; i < 5; i++) {
                    drawRoundaboutArrow(gc, cx, cy, -Math.PI / 2 + i * 2.0 * Math.PI / 5.0 - 0.32, laneGuideR + 17.0);
                }

                gc.setFill(Color.rgb(46, 80, 40));
                gc.fillOval(cx - islandR, cy - islandR, islandR * 2, islandR * 2);
                gc.setFill(Color.rgb(65, 105, 52, 0.45));
                gc.fillOval(cx - islandR * 0.72, cy - islandR * 0.72, islandR * 1.44, islandR * 1.44);
                gc.setStroke(Color.rgb(24, 48, 24));
                gc.setLineWidth(2.2);
                gc.strokeOval(cx - islandR, cy - islandR, islandR * 2, islandR * 2);

                gc.setFill(Color.rgb(255, 255, 255, 0.70));
                gc.setFont(javafx.scene.text.Font.font("SansSerif", javafx.scene.text.FontWeight.BOLD, 16));
                gc.fillText("↺", cx - 7, cy + 6);

                drawRoundaboutApproachMarkings(gc, inter);

            } else {
                double[] b = calcIntersectionBox(inter);
                double x1 = b[0], y1 = b[1], x2 = b[2], y2 = b[3];
                double bw = x2 - x1, bh = y2 - y1;

                // 1. Nền asphalt (Xóa các vạch kẻ đường đè lên nhau)
                gc.setFill(Color.rgb(38, 41, 50));
                gc.fillRect(x1, y1, bw, bh);

                // Highlight ánh sáng giữa ngã tư
                gc.setFill(Color.rgb(255, 255, 255, 0.04));
                gc.fillOval(x1 + bw * 0.1, y1 + bh * 0.1, bw * 0.8, bh * 0.8);
            }
            
            if (inter.getType() != Intersection.Type.FIVE_WAY) {
                for (Lane lane : inter.getLanes()) {
                    Vector2D start = lane.getStart();
                    Vector2D end = lane.getEnd();
                    Vector2D stop = lane.getStopLine();
                    
                    double dx = end.getX() - start.getX();
                    double dy = end.getY() - start.getY();
                    double len = Math.hypot(dx, dy);
                    if (len == 0)
                        continue;
                        
                    // Fix: Tìm waypoint gần ngã tư nhất làm stop line cho những đường đi qua nhiều ngã tư
                    if (lane.getwaypoints().size() > 3) {
                        for (int i = 1; i < lane.getwaypoints().size() - 1; i++) {
                            Vector2D wp = lane.getwaypoints().get(i);
                            double dist = Math.hypot(wp.getX() - inter.getCenter().getX(), wp.getY() - inter.getCenter().getY());
                            if (dist < 150) {
                                double vx = inter.getCenter().getX() - wp.getX();
                                double vy = inter.getCenter().getY() - wp.getY();
                                if (vx * dx + vy * dy > 0) {
                                    stop = wp;
                                    break;
                                }
                            }
                        }
                    }
                    double nx = dx / len;
                    double ny = dy / len;
                    double px = -ny; // Vector vuông góc
                    double py = nx;

                    double hw = ROAD_HALF; // 40px

                    // --- Stop Line (Vạch dừng liền nét) ---
                    gc.setStroke(Color.rgb(240, 240, 240, 0.95));
                    gc.setLineWidth(5.0);
                    gc.setLineCap(StrokeLineCap.BUTT);
                    gc.setLineDashes((double[]) null);
                    gc.strokeLine(
                            stop.getX() + px * hw, stop.getY() + py * hw,
                            stop.getX() - px * hw, stop.getY() - py * hw);

                    // --- Zebra Crossing (Vạch người đi bộ) ---
                    double zebraDist = 18; // lùi về sau vạch dừng
                    double zcx = stop.getX() - nx * zebraDist;
                    double zcy = stop.getY() - ny * zebraDist;

                    double zebraLen = 20;
                    gc.setStroke(Color.rgb(250, 250, 250, 0.85)); // Sắc nét hơn
                    gc.setLineWidth(6.0);
                    // Vẽ từng sọc song song với hướng đi
                    for (double offset = -hw + 8; offset <= hw - 8; offset += 12) {
                        double cx = zcx + px * offset;
                        double cy = zcy + py * offset;
                        gc.strokeLine(
                                cx - nx * (zebraLen / 2), cy - ny * (zebraLen / 2),
                                cx + nx * (zebraLen / 2), cy + ny * (zebraLen / 2));
                    }

                    // --- Mũi tên chỉ hướng (Road Arrow) ---
                    double arrowDist = 55; // lùi xa hơn zebra
                    double ax = stop.getX() - nx * arrowDist;
                    double ay = stop.getY() - ny * arrowDist;
                    drawRoadArrow(gc, ax, ay, nx, ny);
                }
            }
        }
    }

    private void drawRoundaboutArrow(GraphicsContext gc, double cx, double cy, double theta, double radius) {
        double x = cx + Math.cos(theta) * radius;
        double y = cy + Math.sin(theta) * radius;
        double tangent = theta - Math.PI / 2.0; // cùng chiều với path vòng xuyến trong TurnManeuver

        gc.save();
        gc.translate(x, y);
        gc.rotate(Math.toDegrees(tangent));
        gc.setStroke(Color.rgb(255, 255, 255, 0.58));
        gc.setFill(Color.rgb(255, 255, 255, 0.58));
        gc.setLineWidth(2.2);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.strokeLine(-10, 0, 7, 0);
        gc.fillPolygon(new double[]{7, 7, 15}, new double[]{-5, 5, 0}, 3);
        gc.restore();
    }

    private void drawRoundaboutApproachMarkings(GraphicsContext gc, Intersection inter) {
        for (Lane lane : inter.getLanes()) {
            if (lane == null || lane.getLight() == null) {
                continue; // chỉ vẽ vạch dừng cho làn đi vào vòng xuyến
            }
            Vector2D stop = lane.getStopLine();
            double stopProgress = lane.getProgressOf(stop);
            Vector2D dir = lane.getDirectionAt(stopProgress);
            double nx = dir.getX();
            double ny = dir.getY();
            double len = Math.hypot(nx, ny);
            if (len < 1e-6) {
                continue;
            }
            nx /= len;
            ny /= len;
            double px = -ny;
            double py = nx;
            double hw = ROAD_HALF;

            gc.setStroke(Color.rgb(240, 240, 240, 0.92));
            gc.setLineWidth(4.4);
            gc.setLineCap(StrokeLineCap.BUTT);
            gc.setLineDashes((double[]) null);
            gc.strokeLine(stop.getX() + px * hw, stop.getY() + py * hw,
                    stop.getX() - px * hw, stop.getY() - py * hw);

            // Mũi tên approach nhỏ để phân biệt đường vào với vòng xuyến ở giữa.
            double ax = stop.getX() - nx * 42.0;
            double ay = stop.getY() - ny * 42.0;
            drawRoadArrow(gc, ax, ay, nx, ny);
        }
    }

    private void drawRoadArrow(GraphicsContext gc, double x, double y, double nx, double ny) {
        gc.save();
        gc.translate(x, y);
        gc.rotate(Math.toDegrees(Math.atan2(ny, nx)));

        gc.setFill(Color.rgb(255, 255, 255, 0.45));
        // Thân mũi tên
        gc.fillRect(-12, -2, 20, 4);
        // Đầu mũi tên (tam giác)
        gc.fillPolygon(new double[] { 8, 8, 18 }, new double[] { -7, 7, 0 }, 3);

        gc.restore();
    }

    // =========================================================================
    // Tính bounding box ngã giao từ các làn liên quan
    // =========================================================================

    protected double[] calcIntersectionBox(Intersection inter) {
        double cx = inter.getCenter().getX();
        double cy = inter.getCenter().getY();
        double minX = cx, maxX = cx, minY = cy, maxY = cy;

        for (Lane lane : inter.getLanes()) {
            Vector2D s = lane.getStart(), e = lane.getEnd();
            double dx = Math.abs(e.getX() - s.getX());
            double dy = Math.abs(e.getY() - s.getY());

            if (dx > dy) { // ngang
                double lY = (s.getY() + e.getY()) / 2.0;
                minY = Math.min(minY, lY - ROAD_HALF);
                maxY = Math.max(maxY, lY + ROAD_HALF);
                minX = Math.min(minX, cx - ROAD_HALF);
                maxX = Math.max(maxX, cx + ROAD_HALF);
            } else if (dy > dx) { // dọc
                double lX = (s.getX() + e.getX()) / 2.0;
                minX = Math.min(minX, lX - ROAD_HALF);
                maxX = Math.max(maxX, lX + ROAD_HALF);
                minY = Math.min(minY, cy - ROAD_HALF);
                maxY = Math.max(maxY, cy + ROAD_HALF);
            } else { // chéo
                minX = Math.min(minX, cx - ROAD_HALF);
                maxX = Math.max(maxX, cx + ROAD_HALF);
                minY = Math.min(minY, cy - ROAD_HALF);
                maxY = Math.max(maxY, cy + ROAD_HALF);
            }
        }
        return new double[] { minX, minY, maxX, maxY };
    }


    // =========================================================================
    // Hiệu ứng đèn xe dùng chung: xi nhan, đèn ưu tiên và strobe khẩn cấp
    // =========================================================================

    protected void drawVehicleLightEffects(GraphicsContext gc, Vehicle v, double w, double h) {
        if (v == null) return;
        drawTurnSignalLights(gc, v, w, h);
        if (v.isPriority()) {
            drawEmergencyVehicleStrobes(gc, v, w, h);
        }
    }

    protected Vehicle.TurnDecision getActiveTurnSignal(Vehicle v) {
        if (v == null) return null;
        Vehicle.TurnDecision decision = v.getDisplayedTurnDecision();
        if (decision == null || decision == Vehicle.TurnDecision.STRAIGHT) return null;

        Vehicle.IntersectionManeuverState state = v.getIntersectionManeuverState();
        if (v.isTurning()
                || state == Vehicle.IntersectionManeuverState.PREPARING_TURN_SLOT
                || state == Vehicle.IntersectionManeuverState.PREPARING_TURN_SLOT_PAUSED
                || state == Vehicle.IntersectionManeuverState.WAITING_BEFORE_INTERSECTION
                || state == Vehicle.IntersectionManeuverState.TURNING_LEFT
                || state == Vehicle.IntersectionManeuverState.TURNING_RIGHT) {
            return decision;
        }
        return null;
    }

    private void drawTurnSignalLights(GraphicsContext gc, Vehicle v, double w, double h) {
        Vehicle.TurnDecision signal = getActiveTurnSignal(v);
        if (signal == null) return;
        if (!isBlinkOn(v, 760, 0.52, 73)) return;

        double side = signal == Vehicle.TurnDecision.LEFT ? -1.0 : 1.0;
        double y = side * (h / 2.0 + 2.2);
        Color amber = Color.rgb(255, 185, 30, 0.96);
        Color glow = Color.rgb(255, 180, 30, 0.28);

        gc.save();
        gc.setFill(glow);
        gc.fillOval(w / 2.0 - 8.0, y - 6.0, 13.0, 12.0);
        gc.fillOval(-w / 2.0 - 5.0, y - 6.0, 13.0, 12.0);

        gc.setFill(amber);
        gc.fillOval(w / 2.0 - 5.5, y - 3.2, 7.0, 6.4);
        gc.fillOval(-w / 2.0 - 1.5, y - 3.2, 7.0, 6.4);

        // A small side marker makes the signal visible even on sprite vehicles.
        gc.fillRoundRect(-3.0, y - 2.0, 6.0, 4.0, 2.0, 2.0);
        gc.restore();
    }

    private void drawEmergencyVehicleStrobes(GraphicsContext gc, Vehicle v, double w, double h) {
        String type = v.getTypeName();
        if ("ambulance".equals(type)) {
            drawAmbulanceStrobes(gc, v, w, h);
        } else if ("firetruck".equals(type)) {
            drawFireTruckStrobes(gc, v, w, h);
        } else {
            drawGenericPriorityStrobes(gc, v, w, h);
        }
    }

    private void drawAmbulanceStrobes(GraphicsContext gc, Vehicle v, double w, double h) {
        long phase = flashPhase(v, 250, 0);
        boolean redOn = phase % 2 == 0;
        boolean blueOn = !redOn;
        drawLightBar(gc, w, h,
                redOn ? Color.rgb(255, 35, 35, 0.98) : Color.rgb(95, 15, 15, 0.45),
                blueOn ? Color.rgb(35, 105, 255, 0.98) : Color.rgb(15, 35, 95, 0.45),
                1.0);
        Color halo = redOn ? Color.rgb(255, 35, 35, 0.16) : Color.rgb(35, 105, 255, 0.16);
        drawEmergencyHalo(gc, w, h, halo, 1.0);
    }

    private void drawFireTruckStrobes(GraphicsContext gc, Vehicle v, double w, double h) {
        long phase = flashPhase(v, 165, 41) % 6;
        boolean redLeft = phase == 0 || phase == 2;
        boolean redRight = phase == 1 || phase == 3;
        boolean amberPulse = phase == 4;
        Color left = redLeft ? Color.rgb(255, 25, 25, 0.98)
                : amberPulse ? Color.rgb(255, 150, 20, 0.92)
                : Color.rgb(90, 12, 12, 0.45);
        Color right = redRight ? Color.rgb(255, 25, 25, 0.98)
                : amberPulse ? Color.rgb(255, 150, 20, 0.92)
                : Color.rgb(90, 12, 12, 0.45);
        drawLightBar(gc, w, h, left, right, 1.15);
        if (redLeft || redRight || amberPulse) {
            drawEmergencyHalo(gc, w, h,
                    amberPulse ? Color.rgb(255, 150, 20, 0.14) : Color.rgb(255, 35, 35, 0.14),
                    1.12);
        }
    }

    private void drawGenericPriorityStrobes(GraphicsContext gc, Vehicle v, double w, double h) {
        boolean on = isBlinkOn(v, 360, 0.5, 0);
        drawLightBar(gc, w, h,
                on ? Color.rgb(255, 30, 30, 0.95) : Color.rgb(80, 15, 15, 0.45),
                on ? Color.rgb(30, 110, 255, 0.95) : Color.rgb(15, 35, 95, 0.45),
                1.0);
    }

    private void drawLightBar(GraphicsContext gc, double w, double h, Color left, Color right, double scale) {
        double barW = Math.max(14.0, w * 0.52 * scale);
        double barH = Math.max(4.0, Math.min(7.0, h * 0.22 * scale));
        double y = -h / 2.0 - barH - 1.5;
        double half = barW / 2.0;

        gc.save();
        gc.setFill(Color.rgb(8, 8, 12, 0.74));
        gc.fillRoundRect(-half - 1.5, y - 1.0, barW + 3.0, barH + 2.0, 3.0, 3.0);
        gc.setFill(left);
        gc.fillRoundRect(-half, y, half, barH, 2.5, 2.5);
        gc.setFill(right);
        gc.fillRoundRect(0, y, half, barH, 2.5, 2.5);
        gc.setFill(Color.rgb(255, 255, 255, 0.22));
        gc.fillRoundRect(-half + 1.0, y + 0.8, barW - 2.0, Math.max(1.0, barH * 0.33), 2.0, 2.0);
        gc.restore();
    }

    private void drawEmergencyHalo(GraphicsContext gc, double w, double h, Color color, double scale) {
        gc.save();
        gc.setFill(color);
        gc.fillOval(-w / 2.0 - 9.0 * scale, -h / 2.0 - 9.0 * scale,
                w + 18.0 * scale, h + 18.0 * scale);
        gc.restore();
    }

    protected boolean isBlinkOn(Vehicle v, long periodMs, double duty, long phaseShiftMs) {
        if (periodMs <= 0) return true;
        long seed = v == null ? 0L : Math.abs(v.getName() == null ? v.hashCode() : v.getName().hashCode());
        long t = System.currentTimeMillis() + seed % periodMs + phaseShiftMs;
        long m = Math.floorMod(t, periodMs);
        return m < Math.max(1L, Math.round(periodMs * duty));
    }

    private long flashPhase(Vehicle v, long stepMs, long phaseShiftMs) {
        long seed = v == null ? 0L : Math.abs(v.getName() == null ? v.hashCode() : v.getName().hashCode());
        return Math.floorDiv(System.currentTimeMillis() + seed % Math.max(1L, stepMs) + phaseShiftMs,
                Math.max(1L, stepMs));
    }

    public abstract void draw(GraphicsContext gc, double width, double height);
}