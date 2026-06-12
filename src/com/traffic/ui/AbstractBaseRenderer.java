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
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.util.ArrayList;
import java.util.List;

/**
 * Lớp cha trừu tượng — logic vẽ dùng chung.
 */
public abstract class AbstractBaseRenderer implements IRenderer {

    protected List<Lane>               lanes         = new ArrayList<>();
    protected final List<Vehicle>      vehicles      = new ArrayList<>();
    protected final List<TrafficLight> lights        = new ArrayList<>();
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
        if (!isRaining) return; // Không vẽ mưa nếu đang tắt

        if (!rainInitialized) initRain(w, h);

        // Phủ lớp xanh đen mờ để tạo không khí ẩm ướt, trơn trượt
        gc.setFill(Color.rgb(10, 20, 30, 0.25));
        gc.fillRect(0, 0, w, h);

        // Vẽ hạt mưa rơi chéo
        gc.setStroke(Color.rgb(180, 200, 240, 0.5));
        for (int i = 0; i < NUM_DROPS; i++) {
            gc.setLineWidth(Math.max(0.5, rainLength[i] / 15.0));
            gc.strokeLine(rainX[i], rainY[i], rainX[i] + rainLength[i]/4, rainY[i] + rainLength[i]);
            
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
    private   static final int   LIGHT_HIT  = 45;

    public AbstractBaseRenderer(List<Lane> lanes) { this.lanes = lanes; }

    public void setLanes(List<Lane> newLanes) { this.lanes = newLanes; }

    public void handleClick(double x, double y, boolean leftButton) {
        for (TrafficLight light : lights) {
            if (light == null) continue;
            double dist = Math.hypot(x - light.getPosition().getX(),
                                     y - light.getPosition().getY());
            if (dist <= LIGHT_HIT) {
                if (leftButton) { light.setManualMode(true);  light.manualSwitch(); }
                else            { light.setManualMode(false); }
                break;
            }
        }
    }

    @Override public void clear() { vehicles.clear(); lights.clear(); intersections.clear(); }
    @Override public void renderVehicles(List<Vehicle> list)           { vehicles.addAll(list); }
    @Override public void renderLights(List<TrafficLight> list)        { lights.addAll(list); }
    @Override public void renderIntersections(List<Intersection> list) { intersections.addAll(list); }

    // =========================================================================
    //  NHÀ DÂN CƯ — vẽ TRƯỚC đường, DƯỚI mọi thứ khác
    // =========================================================================

    /** Palette mái nhà (top-down view) */
    private static final Color[] ROOF_COLORS = {
        Color.rgb(112, 88, 76),   // nâu gỗ
        Color.rgb(88, 88, 92),    // xám nguội
        Color.rgb(128, 96, 84),   // gạch terra
        Color.rgb(76, 90, 96),    // xanh đá
        Color.rgb(104, 82, 76),   // đỏ gỉ
        Color.rgb(92, 90, 76),    // ô liu
        Color.rgb(82, 82, 88),    // than
        Color.rgb(118, 100, 92),  // cát
        Color.rgb(96, 76, 72),    // nâu đỏ
        Color.rgb(72, 96, 80),    // xanh rêu (nhà vườn)
    };

    protected void drawBuildings(GraphicsContext gc, double canvasW, double canvasH) {
        int step = 58;

        for (int gx = 0; gx * step < canvasW; gx++) {
            for (int gy = 0; gy * step < canvasH; gy++) {

                double cx = gx * step + step / 2.0;
                double cy = gy * step + step / 2.0;

                // Bỏ qua ô quá gần đường hoặc ngã giao
                if (isTooCloseToRoad(cx, cy, 54)) continue;

                // Hash tất định theo ô — đảm bảo ổn định giữa các frame
                long h = hash2(gx, gy);

                // ~30% ô trống (vườn / không gian)
                if (Math.abs(h % 10) < 3) continue;

                double bw = 18 + Math.abs(h % 24);
                double bh = 18 + Math.abs((h >> 8) % 24);
                int   ci  = (int) Math.abs(h % ROOF_COLORS.length);

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
                    gc.fillRect(bx + 4,       by + 4,       4, 3);
                    gc.fillRect(bx + bw - 9,  by + 4,       4, 3);
                    if (bh > 32) {
                        gc.fillRect(bx + 4,      by + bh - 8, 4, 3);
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
        if (lanes == null) return false;
        for (Lane lane : lanes) {
            List<Vector2D> pts = lane.getwaypoints();
            for (int i = 0; i < pts.size() - 1; i++) {
                if (distSeg(px, py,
                        pts.get(i).getX(), pts.get(i).getY(),
                        pts.get(i+1).getX(), pts.get(i+1).getY()) < thr)
                    return true;
            }
        }
        // Kiểm tra thêm với intersections
        for (Intersection inter : intersections) {
            double[] box = calcIntersectionBox(inter);
            if (px > box[0] - 10 && px < box[2] + 10 &&
                py > box[1] - 10 && py < box[3] + 10) return true;
        }
        return false;
    }

    private double distSeg(double px, double py,
                            double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        if (dx == 0 && dy == 0) return Math.hypot(px - ax, py - ay);
        double t = Math.max(0, Math.min(1,
            ((px-ax)*dx + (py-ay)*dy) / (dx*dx + dy*dy)));
        return Math.hypot(px - (ax + t*dx), py - (ay + t*dy));
    }

    private long hash2(int x, int y) {
        long h = (long)x * 73856093L ^ (long)y * 19349663L;
        h ^= (h >>> 16); h *= 0x45d9f3bL; h ^= (h >>> 16);
        return h;
    }

    private double clamp(long v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // =========================================================================
    //  NGÃ GIAO — vẽ SAU buildings, TRƯỚC đường
    // =========================================================================

    protected void drawIntersections(GraphicsContext gc) {
        for (Intersection inter : intersections) {
            double[] b = calcIntersectionBox(inter);
            double x1 = b[0], y1 = b[1], x2 = b[2], y2 = b[3];
            double bw = x2 - x1, bh = y2 - y1;

            // ── 1. Nền asphalt (Xóa các vạch kẻ đường đè lên nhau) ────────
            gc.setFill(Color.rgb(38, 41, 50));
            gc.fillRect(x1, y1, bw, bh);

            // Highlight ánh sáng giữa ngã tư
            gc.setFill(Color.rgb(255, 255, 255, 0.04));
            gc.fillOval(x1 + bw*0.1, y1 + bh*0.1, bw*0.8, bh*0.8);

            // ── 2. Vẽ vạch kẻ theo TỪNG LÀN (Realistic Markings) ──────────
            for (Lane lane : inter.getLanes()) {
                Vector2D start = lane.getStart();
                Vector2D end = lane.getEnd();
                Vector2D stop = lane.getStopLine();
                
                double dx = end.getX() - start.getX();
                double dy = end.getY() - start.getY();
                double len = Math.hypot(dx, dy);
                if (len == 0) continue;
                double nx = dx / len;
                double ny = dy / len;
                double px = -ny; // Vector vuông góc
                double py = nx;
                
                double hw = ROAD_HALF; // 40px
                
                // --- Stop Line (Vạch dừng) ---
                gc.setStroke(Color.rgb(240, 240, 240, 0.85));
                gc.setLineWidth(4.0);
                gc.setLineCap(StrokeLineCap.BUTT);
                gc.strokeLine(
                    stop.getX() + px * hw, stop.getY() + py * hw,
                    stop.getX() - px * hw, stop.getY() - py * hw
                );
                
                // --- Zebra Crossing (Vạch người đi bộ) ---
                double zebraDist = 18; // lùi về sau vạch dừng
                double zcx = stop.getX() - nx * zebraDist;
                double zcy = stop.getY() - ny * zebraDist;
                
                double zebraLen = 16;
                gc.setStroke(Color.rgb(220, 220, 220, 0.45));
                gc.setLineWidth(5.0);
                // Vẽ từng sọc song song với hướng đi
                for (double offset = -hw + 8; offset <= hw - 8; offset += 12) {
                    double cx = zcx + px * offset;
                    double cy = zcy + py * offset;
                    gc.strokeLine(
                        cx - nx * (zebraLen/2), cy - ny * (zebraLen/2),
                        cx + nx * (zebraLen/2), cy + ny * (zebraLen/2)
                    );
                }
                
                // --- Mũi tên chỉ hướng (Road Arrow) ---
                double arrowDist = 55; // lùi xa hơn zebra
                double ax = stop.getX() - nx * arrowDist;
                double ay = stop.getY() - ny * arrowDist;
                drawRoadArrow(gc, ax, ay, nx, ny);
            }
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
        gc.fillPolygon(new double[]{8, 8, 18}, new double[]{-7, 7, 0}, 3);
        
        gc.restore();
    }

    // =========================================================================
    //  Tính bounding box ngã giao từ các làn liên quan
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
        return new double[]{minX, minY, maxX, maxY};
    }

    public abstract void draw(GraphicsContext gc, double width, double height);
}