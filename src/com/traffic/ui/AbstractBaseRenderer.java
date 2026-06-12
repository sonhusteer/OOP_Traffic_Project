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
    
    protected Lane hoveredLane = null;
    protected boolean showHeatmap = false;
    protected Vehicle selectedVehicle = null;

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

    public void setHoveredLane(Lane lane) { this.hoveredLane = lane; }
    public Lane getHoveredLane() { return this.hoveredLane; }

    public void setShowHeatmap(boolean show) { this.showHeatmap = show; }
    public boolean isShowHeatmap() { return this.showHeatmap; }
    
    public void setSelectedVehicle(Vehicle v) { this.selectedVehicle = v; }

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
    private   static final double CONTROL_MARKING_RADIUS = 180.0;

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

        // Đổi vòng lặp gy ra ngoài để vẽ theo thứ tự Y-sorting (nhà phía sau vẽ trước, nhà phía trước đè lên)
        for (int gy = 0; gy * step < canvasH; gy++) {
            for (int gx = 0; gx * step < canvasW; gx++) {

                double cx = gx * step + step / 2.0;
                double cy = gy * step + step / 2.0;

                // Bỏ qua ô quá gần đường hoặc ngã giao
                if (isTooCloseToRoad(cx, cy, 54)) continue;

                // Hash tất định theo ô — đảm bảo ổn định giữa các frame
                long h = hash2(gx, gy);

                // ~40% ô trống (vườn / cây cối)
                if (Math.abs(h % 10) < 4) {
                    if (Math.abs(h % 10) <= 1) { // Vẽ cây 3D phong cách top-down
                        drawTree(gc, cx, cy, h);
                    }
                    continue;
                }

                // Chiều rộng (bw), chiều sâu (bl), chiều cao giả 3D (bZ)
                double bw = 24 + Math.abs(h % 20);
                double bl = 20 + Math.abs((h >> 4) % 20);
                double bZ = 15 + Math.abs((h >> 8) % 45); // Tòa nhà cao 15 -> 60

                // Tọa độ góc trên bên trái của TÒA NHÀ (dưới đất)
                double bx = cx - bw / 2 + clamp((h % 7) - 3, -6, 6);
                double by = cy - bl / 2 + clamp(((h >> 4) % 7) - 3, -6, 6);

                int ci = (int) Math.abs(h % ROOF_COLORS.length);
                Color roofColor = ROOF_COLORS[ci];
                Color wallColor = roofColor.deriveColor(0, 0.7, 0.5, 1.0);

                // 1. Bóng đổ (Drop Shadow)
                gc.setFill(Color.rgb(0, 0, 0, 0.35));
                gc.fillRoundRect(bx + bZ * 0.4, by + 4, bw, bl, 4, 4);

                // 2. Khối tường 2.5D (kéo dài từ nóc xuống đất)
                gc.setFill(wallColor);
                gc.fillRoundRect(bx, by - bZ, bw, bl + bZ, 4, 4);

                // Viền tường tạo khối
                gc.setStroke(wallColor.deriveColor(0, 1.0, 0.3, 1.0));
                gc.setLineWidth(1.0);
                gc.strokeRoundRect(bx, by - bZ, bw, bl + bZ, 4, 4);

                // 3. Mái nhà (Roof) - nằm ở trên cùng (by - bZ)
                gc.setFill(roofColor);
                gc.fillRoundRect(bx - 2, by - bZ - 2, bw + 4, bl + 4, 3, 3);
                
                // Chi tiết mái: Cục nóng điều hòa / Hộp kỹ thuật
                gc.setFill(Color.rgb(150, 160, 170, 0.6));
                gc.fillRoundRect(bx + bw * 0.2, by - bZ + bl * 0.2, bw * 0.3, bl * 0.3, 2, 2);

                // 4. Cửa sổ phát sáng (Hiệu ứng thành phố đêm)
                if (bZ > 20) {
                    gc.setFill(Color.rgb(255, 235, 160, 0.8)); // Ánh sáng vàng ấm
                    int rows = (int) (bZ / 12);
                    int cols = (int) (bw / 10);
                    for (int r = 0; r < rows; r++) {
                        for (int c = 0; c < cols; c++) {
                            // Xác suất 35% phòng bật đèn sáng
                            if (Math.abs(hash2((int)h + r, c)) % 10 < 3) {
                                double wx = bx + 4 + c * 10;
                                double wy = by - bZ + 10 + r * 12;
                                gc.fillRect(wx, wy, 5, 6);
                            }
                        }
                    }
                }
            }
        }
    }

    private void drawTree(GraphicsContext gc, double cx, double cy, long h) {
        double r = 8 + Math.abs(h % 6);
        double tx = cx + clamp((h % 11) - 5, -8, 8);
        double ty = cy + clamp(((h >> 4) % 11) - 5, -8, 8);
        
        // Bóng cây
        gc.setFill(Color.rgb(0, 0, 0, 0.3));
        gc.fillOval(tx - r + 4, ty - r + 4, r*2, r*2);
        
        // Tán lá dưới (Tối)
        gc.setFill(Color.rgb(34, 60, 30, 0.9));
        gc.fillOval(tx - r, ty - r, r*2, r*2);
        
        // Tán lá trên (Sáng hơn, tạo độ bồng bềnh 3D)
        gc.setFill(Color.rgb(55, 100, 45, 0.9));
        gc.fillOval(tx - r*0.6, ty - r*0.6, r*1.2, r*1.2);
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

            // ── 1. Nền asphalt (xóa các vạch kẻ đường đè lên nhau) ─────
            gc.setFill(Color.rgb(38, 41, 50));
            gc.fillRect(x1, y1, bw, bh);

            // Highlight ánh sáng giữa ngã tư
            gc.setFill(Color.rgb(255, 255, 255, 0.04));
            gc.fillOval(x1 + bw * 0.1, y1 + bh * 0.1, bw * 0.8, bh * 0.8);

            // ── 2. Vẽ vạch dừng/zebra theo từng control point gần ngã tư ─
            for (Lane lane : inter.getLanes()) {
                boolean hasMarking = false;

                // Lane co the co nhieu den/vach dung (NetworkMap road1/road2).
                // Chi ve vach dung gan center cua Intersection hien tai de tranh
                // ve ca vach dung cua nga tu ben kia.
                for (Lane.TrafficControlPoint control : lane.getTrafficControls()) {
                    Vector2D stop = control.getStopLine();
                    double distToCenter = Math.hypot(
                        stop.getX() - inter.getCenter().getX(),
                        stop.getY() - inter.getCenter().getY()
                    );
                    if (distToCenter <= CONTROL_MARKING_RADIUS) {
                        drawStopMarking(gc, lane, stop);
                        hasMarking = true;
                    }
                }

                // Fallback cho lane kieu cu chi co 1 den.
                if (!hasMarking && lane.getLight() != null) {
                    drawStopMarking(gc, lane, lane.getStopLine());
                }
            }
        }
    }

    /** Ve vach dung, zebra crossing va mui ten tai 1 stop line. */
    private void drawStopMarking(GraphicsContext gc, Lane lane, Vector2D stop) {
        double stopProgress = lane.getProgress(stop);
        double angle = Math.toRadians(lane.getAngleAtProgress(stopProgress));
        double nx = Math.cos(angle);
        double ny = Math.sin(angle);
        double px = -ny;
        double py = nx;
        double hw = ROAD_HALF;

        // Stop line.
        gc.setStroke(Color.rgb(240, 240, 240, 0.85));
        gc.setLineWidth(4.0);
        gc.setLineCap(StrokeLineCap.BUTT);
        gc.strokeLine(
            stop.getX() + px * hw, stop.getY() + py * hw,
            stop.getX() - px * hw, stop.getY() - py * hw
        );

        // Zebra crossing.
        double zebraDist = 18;
        double zcx = stop.getX() - nx * zebraDist;
        double zcy = stop.getY() - ny * zebraDist;
        double zebraLen = 16;
        gc.setStroke(Color.rgb(220, 220, 220, 0.45));
        gc.setLineWidth(5.0);
        for (double offset = -hw + 8; offset <= hw - 8; offset += 12) {
            double cx = zcx + px * offset;
            double cy = zcy + py * offset;
            gc.strokeLine(
                cx - nx * (zebraLen / 2), cy - ny * (zebraLen / 2),
                cx + nx * (zebraLen / 2), cy + ny * (zebraLen / 2)
            );
        }

        // Road arrow.
        double arrowDist = 55;
        double ax = stop.getX() - nx * arrowDist;
        double ay = stop.getY() - ny * arrowDist;
        drawRoadArrow(gc, ax, ay, nx, ny);
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