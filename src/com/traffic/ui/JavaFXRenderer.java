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
    private final List<Image[]> castleAnimations = new java.util.ArrayList<>();
    private final List<Image[]> houseAnimations = new java.util.ArrayList<>();
    private final List<Image> treeSprites = new java.util.ArrayList<>();
    
    // ── Scenery tiles (GK Japanese City) ────────────────────────────────────
    private final Map<Integer, Image> tiles = new HashMap<>();
    private String sceneryType = "CROSSROADS"; // được cập nhật khi đổi map
    
    private ImagePattern roadPattern = null;
    private ImagePattern riverPattern = null;

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
        
        // Tải các chuỗi animation nhà cửa
        loadAnimation(castleAnimations, "/images/Sprites/Towers/Blue towers/castle_tower_blue(%d).png", 9);
        loadAnimation(castleAnimations, "/images/Sprites/Towers/Green towers/castle_tower_green(%d).png", 9);
        loadAnimation(castleAnimations, "/images/Sprites/Towers/Red tower/castle_tower_red(%d).png", 9);
        loadAnimation(castleAnimations, "/images/Sprites/Towers/Wood towers/castle_tower_wood(%d).png", 9);
        
        loadAnimation(houseAnimations, "/images/Sprites/Buildings/house/White houses/Blue house/white_blue_house(%d).png", 4);
        loadAnimation(houseAnimations, "/images/Sprites/Buildings/house/White houses/Green house/white_green_house(%d).png", 4);
        loadAnimation(houseAnimations, "/images/Sprites/Buildings/house/White houses/Red house/white_red_house(%d).png", 4);
        loadAnimation(houseAnimations, "/images/Sprites/Buildings/house/Wood houses/Blue house/wood_blue_house(%d).png", 4);
        loadAnimation(houseAnimations, "/images/Sprites/Buildings/house/Wood houses/Green house/wood_green_house(%d).png", 4);
        loadAnimation(houseAnimations, "/images/Sprites/Buildings/house/Wood houses/Red house/wood_red_house(%d).png", 4);
        
        loadAnimation(houseAnimations, "/images/Sprites/Buildings/house/blacksmith_blue(%d).png", 2);
        loadAnimation(houseAnimations, "/images/Sprites/Buildings/house/blacksmith_green(%d).png", 2);
        loadAnimation(houseAnimations, "/images/Sprites/Buildings/house/blacksmith_red(%d).png", 2);
        loadAnimation(houseAnimations, "/images/Sprites/Buildings/house/blacksmith_wood(%d).png", 2);
        
        loadAnimation(houseAnimations, "/images/Sprites/Buildings/mill/mill(%d).png", 4);

        // Tải sprite cây
        String[] trees = {"tree(3)", "tree(4)"};
        for (String t : trees) {
            try {
                InputStream is = getClass().getResourceAsStream("/images/Enviroument/Spring/" + t + ".png");
                if (is == null) is = getClass().getResourceAsStream("/images/" + t + ".png");
                if (is != null) treeSprites.add(new Image(is));
            } catch (Exception ignored) {}
        }
        
        // Tải GK Japanese City tiles
        loadSceneryTiles();
    }

    private void loadSceneryTiles() {
        // Danh sách tile cần tải theo vai trò
        int[] needed = {
            // Nền vỉa hè (sidewalk checker)
            236,
            // Mái nhà – xanh xám
            1, 2, 3, 4,
            // Mái nhà – đỏ cam (ngói)
            5, 6, 7, 8,
            // Thân nhà phần trên (sàn trên)
            211, 212, 213, 214,
            // Mặt tiền nhà (tường gỗ ngang)
            278, 279, 280,
            // Cửa ra vào
            355, 357,
            // Cửa sổ / bảng hiệu
            358, 359,
            // Máy bán nước
            68,
            // Đèn đường
            29,
            // Chậu cây / potted plant
            84,
            // Rèm cửa noren / chi tiết mặt tiền
            186, 187, 188,
            // Tường nhà (panel ngang)
            103, 104, 105, 106, 107, 108,
            // Mái nhà nghiêng bên trái
            129, 130, 131, 132,
            // Mái nhà nghiêng bên phải
            133, 134, 135, 136,
            // Góc mái
            151, 152, 153, 154, 155, 156,
            // Hàng rào / tường thấp
            47, 48, 76
        };
        for (int id : needed) {
            String path = String.format("/images/Tiles/GK_JC_Free_%03d.png", id);
            try {
                InputStream is = getClass().getResourceAsStream(path);
                if (is != null) tiles.put(id, new Image(is));
            } catch (Exception ignored) {}
        }
    }

    private void loadAnimation(List<Image[]> list, String pathTemplate, int numFrames) {
        Image[] frames = new Image[numFrames];
        boolean valid = true;
        for (int i = 1; i <= numFrames; i++) {
            try {
                String path = String.format(pathTemplate, i);
                InputStream is = getClass().getResourceAsStream(path);
                if (is != null) {
                    frames[i - 1] = new Image(is);
                } else {
                    valid = false;
                    break;
                }
            } catch (Exception e) {
                valid = false;
                break;
            }
        }
        if (valid) {
            list.add(frames);
        }
        
        // Thử tải mẫu đường và sông
        try {
            if (roadPattern == null) {
                InputStream isRoad = getClass().getResourceAsStream("/images/Roads/Spring/road(1).png");
                if (isRoad != null) roadPattern = new ImagePattern(new Image(isRoad), 0, 0, 100, 100, false);
            }
            if (riverPattern == null) {
                InputStream isRiver = getClass().getResourceAsStream("/images/Rivers/Spring/river(1).png");
                if (isRiver != null) riverPattern = new ImagePattern(new Image(isRiver), 0, 0, 120, 120, false);
            }
        } catch (Exception ignored) {}
    }

    // =====================================================================
    //  MAIN DRAW
    // =====================================================================
    @Override
    public void draw(GraphicsContext gc, double w, double h) {
        drawBackground(gc, w, h);
        drawRivers(gc, w, h);        // Dòng sông trang trí dưới mặt đất
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
        // Solid, smooth dark green grass color, NO grid lines for all maps
        gc.setFill(Color.rgb(34, 139, 34)); // Forest green
        gc.fillRect(0, 0, w, h);
    }

    // =====================================================================
    //  1.2 SÔNG TRANG TRÍ
    // =====================================================================
    private void drawRivers(GraphicsContext gc, double w, double h) {
        // Trống - chờ làm lại
    }

    // =====================================================================
    //  PUBLIC: đổi map type để renderer vẽ scenery phù hợp
    // =====================================================================
    public void setSceneryType(String type) {
        this.sceneryType = (type != null) ? type : "CROSSROADS";
    }

    // =====================================================================
    //  1.5. CẢnh QUAN — Universal City Blocks Generator
    // =====================================================================
    @Override
    protected void drawBuildings(GraphicsContext gc, double W, double H) {
        drawCityBlocks(gc, W, H);
    }

    // ── UNIVERSAL CITY BLOCKS GENERATOR ─────────────────────────────────
    private void drawCityBlocks(GraphicsContext gc, double W, double H) {
        Image pineTree = treeSprites.size() > 0 ? treeSprites.get(0) : null;
        
        for (double y = 40; y < H - 20; y += 60) {
            for (double x = 40; x < W - 20; x += 50) {
                if (isSafeGrass(x, y)) {
                    double noise = (x * 17 + y * 23) % 100;
                    
                    double treeProb = 20;
                    double houseProb = 90;
                    
                    if ("T_JUNCTION".equals(sceneryType)) {
                        treeProb = 8;
                        houseProb = 25; // 25-8 = 17% chance for a house
                    }

                    if (noise < treeProb && pineTree != null) {
                        gc.drawImage(pineTree, x, y, 36, 48);
                    } else if (noise >= treeProb && noise < houseProb) {
                        drawHouseAt(gc, x, y);
                    }
                }
            }
        }
    }

    private boolean isSafeGrass(double x, double y) {
        double m = 25; // Safe margin from road edges
        if ("CROSSROADS".equals(sceneryType)) {
            if (y > 220 - m && y < 380 + m) return false;
            if (x > 320 - m && x < 480 + m) return false;
            return true;
        } else if ("T_JUNCTION".equals(sceneryType)) {
            if (y > 220 - m && y < 380 + m) return false;
            if (x > 320 - m && x < 480 + m && y < 380 + m) return false;
            return true;
        } else if ("NETWORK".equals(sceneryType)) {
            if (y > 220 - m && y < 380 + m) return false;
            if (x > 170 - m && x < 330 + m) return false;
            if (x > 470 - m && x < 630 + m) return false;
            return true;
        } else if ("HIGHWAY".equals(sceneryType)) {
            // Highway takes roughly Y=200~400
            if (y > 200 - m && y < 400 + m) return false;
            return true;
        } else if ("FIVE_WAY".equals(sceneryType)) {
            double cx = 400, cy = 300;
            double dx = x - cx, dy = y - cy;
            // Central roundabout clearance
            if (dx*dx + dy*dy < 130*130) return false; 
            // 5 radiating roads
            for (int i = 0; i < 5; i++) {
                double angle = Math.PI * 2 * i / 5 - Math.PI / 2;
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                double u = dx * cos + dy * sin;
                if (u > -20) {
                    double dist = Math.abs(dx * sin - dy * cos);
                    if (dist < 80 + m) return false; // Road width is roughly 160 (radius 80)
                }
            }
            return true;
        }
        return true;
    }

    @Override
    protected void drawIntersections(GraphicsContext gc) {
        super.drawIntersections(gc);
        // Zebra Crosswalks
        gc.setFill(Color.WHITE);
        
        if ("CROSSROADS".equals(sceneryType)) {
            double cx = 400, cy = 300;
            drawZebraCrosswalk(gc, cx - 35, cy - 105, 70, 15, false); // Top
            drawZebraCrosswalk(gc, cx - 35, cy + 90, 70, 15, false);  // Bottom
            drawZebraCrosswalk(gc, cx - 105, cy - 35, 15, 70, true);  // Left
            drawZebraCrosswalk(gc, cx + 90, cy - 35, 15, 70, true);   // Right
        } else if ("T_JUNCTION".equals(sceneryType)) {
            double cx = 400, cy = 300;
            drawZebraCrosswalk(gc, cx - 35, cy - 105, 70, 15, false); // Top
            drawZebraCrosswalk(gc, cx - 105, cy - 35, 15, 70, true);  // Left
            drawZebraCrosswalk(gc, cx + 90, cy - 35, 15, 70, true);   // Right
        } else if ("NETWORK".equals(sceneryType)) {
            double[] centersX = {250, 550};
            for (double cx : centersX) {
                double cy = 300;
                drawZebraCrosswalk(gc, cx - 35, cy - 105, 70, 15, false); // Top
                drawZebraCrosswalk(gc, cx - 35, cy + 90, 70, 15, false);  // Bottom
                drawZebraCrosswalk(gc, cx - 105, cy - 35, 15, 70, true);  // Left
                drawZebraCrosswalk(gc, cx + 90, cy - 35, 15, 70, true);   // Right
            }
        } else if ("FIVE_WAY".equals(sceneryType)) {
            double cx = 400, cy = 300;
            for (int i = 0; i < 5; i++) {
                double angle = Math.PI * 2 * i / 5 - Math.PI / 2;
                gc.save();
                gc.translate(cx, cy);
                gc.rotate(Math.toDegrees(angle));
                drawZebraCrosswalk(gc, 90, -35, 15, 70, true); // Rotate and draw "Right"
                gc.restore();
            }
        }
    }

    private void drawZebraCrosswalk(GraphicsContext gc, double x, double y, double w, double h, boolean verticalStripe) {
        double stripeThickness = 4;
        double gap = 4;
        if (verticalStripe) {
            for (double sy = y; sy < y + h; sy += stripeThickness + gap) {
                gc.fillRect(x, sy, w, stripeThickness);
            }
        } else {
            for (double sx = x; sx < x + w; sx += stripeThickness + gap) {
                gc.fillRect(sx, y, stripeThickness, h);
            }
        }
    }

    private void drawArrow(GraphicsContext gc, double sx, double sy, double dx, double dy) {
        gc.strokeLine(sx, sy, sx + dx, sy + dy);
        double headLen = 6;
        if (dx == 0) { // Vertical
            double dir = Math.signum(dy);
            gc.strokeLine(sx + dx, sy + dy, sx - headLen, sy + dy - dir * headLen);
            gc.strokeLine(sx + dx, sy + dy, sx + headLen, sy + dy - dir * headLen);
        } else { // Horizontal
            double dir = Math.signum(dx);
            gc.strokeLine(sx + dx, sy + dy, sx + dx - dir * headLen, sy - headLen);
            gc.strokeLine(sx + dx, sy + dy, sx + dx - dir * headLen, sy + headLen);
        }
    }


    // ── OLD SCENERY METHODS REMOVED ─────────────────────────────────────
    // A universal `drawCityBlocks` generator now gracefully populates 
    // all map variations based on the sceneryType boundaries.

    private void drawHouseAt(GraphicsContext gc, double x, double y) {
        if (houseAnimations == null || houseAnimations.isEmpty()) return;
        // Use coordinates to deterministically pick a random house index
        int index = (int)((x * 13 + y * 7) % houseAnimations.size());
        Image house = houseAnimations.get(index)[0];
        if (house != null) {
            gc.drawImage(house, x, y, 32, 36);
        }
    }

    // Vẽ dải phân cách cao tốc (màu xanh lá + sọc trắng)
    private void drawMedianStrip(GraphicsContext gc, double x, double y, double w, double h) {
        gc.setFill(Color.rgb(34, 80, 34));
        gc.fillRect(x, y, w, h);
        gc.setFill(Color.rgb(255, 255, 255, 0.35));
        for (double mx = x; mx < x + w; mx += 24) {
            gc.fillRect(mx, y + h/2 - 2, 14, 4);
        }
        // Chậu cây dọc median
        double T = 36;
        for (double mx = x + 20; mx < x + w - 20; mx += 100) {
            drawTileAt(gc, 84, mx, y + (h - T)/2, T, T);
        }
    }

    // Vẽ biển báo cao tốc (hình chữ nhật xanh)
    private void drawHighwaySign(GraphicsContext gc, double x, double y) {
        gc.setFill(Color.rgb(0, 100, 0));
        gc.fillRoundRect(x, y, 44, 22, 4, 4);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(x+2, y+2, 40, 18, 3, 3);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 9));
        gc.fillText("HIGHWAY", x + 5, y + 14);
        // Cột biển
        gc.setStroke(Color.rgb(160, 160, 160));
        gc.setLineWidth(2);
        gc.strokeLine(x + 22, y + 22, x + 22, y + 38);
    }

    // Vẽ đèn đường tại 4 góc ngã tư
    private void drawStreetlights(GraphicsContext gc, double roadTop, double roadBot, double roadLeft, double roadRight) {
        double T = 32;
        // 4 góc
        double[][] corners = {
            {roadLeft  - T - 4, roadTop  - T - 4},
            {roadRight + 4,      roadTop  - T - 4},
            {roadLeft  - T - 4, roadBot  + 4},
            {roadRight + 4,      roadBot  + 4},
        };
        for (double[] c : corners) {
            drawTileAt(gc, 29, c[0], c[1], T * 0.5, T); // đèn đường tile #29
        }
    }

    // Vẽ mây trang trí cho ngã năm
    private void drawClouds(GraphicsContext gc, double W, double H) {
        gc.setFill(Color.rgb(220, 235, 255, 0.18));
        double[][] clouds = {{80, 30, 90, 32}, {W-160, 20, 110, 28}, {W/2-50, 10, 80, 26}};
        for (double[] c : clouds) {
            gc.fillOval(c[0], c[1], c[2], c[3]);
            gc.fillOval(c[0]+15, c[1]-8, c[2]*0.7, c[3]*0.8);
            gc.fillOval(c[0]+30, c[1]+4, c[2]*0.6, c[3]*0.7);
        }
    }

    // Helper: vẽ tile tại tọa độ cụ thể (alias ngắn gọn cho drawTile)
    private void drawTileAt(GraphicsContext gc, int id, double x, double y, double w, double h) {
        drawTile(gc, id, x, y, w, h);
    }


    // Vẽ nền sidewalk (tile checker xám)
    private void drawSidewalk(GraphicsContext gc, double x, double y, double w, double h) {
        Image sw = tiles.get(236);
        double T = 48;
        if (sw != null) {
            for (double ty = y; ty < y + h; ty += T) {
                for (double tx = x; tx < x + w; tx += T) {
                    double tw = Math.min(T, x + w - tx);
                    double th = Math.min(T, y + h - ty);
                    gc.drawImage(sw, tx, ty, tw, th);
                }
            }
        } else {
            gc.setFill(Color.rgb(90, 104, 112));
            gc.fillRect(x, y, w, h);
        }
    }

    // Vẽ một dải sidewalk hẹp (kẻ đôi màu đậm hơn)
    private void drawSidewalkStrip(GraphicsContext gc, double x, double y, double w, double h) {
        gc.setFill(Color.rgb(80, 92, 100, 0.85));
        gc.fillRect(x, y, w, h);
        gc.setStroke(Color.rgb(55, 65, 75));
        gc.setLineWidth(1);
        gc.strokeRect(x, y, w, h);
    }

    // Vẽ dãy nhà ngang (TOP hoặc BOTTOM)
    // variant: 0=xanh mái+gỗ nâu, 1=ngói đỏ+trắng, 2=xanh đậm+gỗ tối
    private void drawBuildingStrip(GraphicsContext gc, double x, double y, double totalW, double totalH, boolean facingDown, int baseVariant) {
        if (totalW < 32 || totalH < 32) return;
        double T = 48.0;
        // Mỗi căn nhà có độ rộng 3~5 tiles
        int[] widthTiles = {4, 3, 5, 3, 4, 5, 3, 4};
        int wi = 0;
        double cx = x;
        while (cx < x + totalW - T) {
            int w = widthTiles[wi % widthTiles.length];
            double bW = w * T;
            if (cx + bW > x + totalW) bW = x + totalW - cx;
            int variant = (baseVariant + wi) % 3;
            drawMachiya(gc, cx, y, bW, totalH, T, facingDown, variant);
            cx += bW;
            wi++;
        }
    }

    // Vẽ dãy nhà dọc (LEFT hoặc RIGHT)
    private void drawBuildingStripV(GraphicsContext gc, double x, double y, double totalW, double totalH, boolean facingRight, int baseVariant) {
        if (totalW < 32 || totalH < 32) return;
        double T = 48.0;
        int[] heightTiles = {3, 4, 3, 5, 3, 4};
        int hi = 0;
        double cy = y;
        while (cy < y + totalH - T) {
            int h = heightTiles[hi % heightTiles.length];
            double bH = h * T;
            if (cy + bH > y + totalH) bH = y + totalH - cy;
            int variant = (baseVariant + hi) % 3;
            drawMachiyaV(gc, x, cy, totalW, bH, T, facingRight, variant);
            cy += bH;
            hi++;
        }
    }

    // ── Vẽ 1 ngôi nhà machiya NGANG ─────────────────────────────────────
    //  facingDown=false → mái trên, mặt tiền dưới (nhìn xuống đường)
    //  facingDown=true  → mặt tiền trên, mái dưới  (nhìn lên đường)
    private void drawMachiya(GraphicsContext gc, double x, double y, double bW, double bH, double T,
                              boolean facingDown, int variant) {
        double roofH  = Math.min(T * 1.1, bH * 0.45);
        double wallH  = bH - roofH;
        double roofY  = facingDown ? y + wallH : y;
        double wallY  = facingDown ? y          : y + roofH;

        // -- Tường --
        drawWall(gc, x, wallY, bW, wallH, T, variant);
        // -- Mặt tiền (cửa, rèm) --
        double frontH = Math.min(T * 1.3, wallH);
        double frontY = facingDown ? wallY : wallY + wallH - frontH;
        drawFacade(gc, x, frontY, bW, frontH, T, variant);
        // -- Mái --
        drawRoof(gc, x, roofY, bW, roofH, T, variant);
    }

    // ── Vẽ 1 ngôi nhà machiya DỌC ───────────────────────────────────────
    private void drawMachiyaV(GraphicsContext gc, double x, double y, double bW, double bH, double T,
                               boolean facingRight, int variant) {
        double roofW  = Math.min(T * 1.0, bW * 0.4);
        double wallW  = bW - roofW;
        double roofX  = facingRight ? x : x + wallW;
        double wallX  = facingRight ? x + roofW : x;

        drawWallV(gc, wallX, y, wallW, bH, T, variant);
        double facadeW = Math.min(T * 1.2, wallW);
        double facadeX = facingRight ? wallX : wallX + wallW - facadeW;
        drawFacadeV(gc, facadeX, y, facadeW, bH, T, variant);
        drawRoofV(gc, roofX, y, roofW, bH, T, variant);
    }

    // -- Vẽ phần tường --
    private void drawWall(GraphicsContext gc, double x, double y, double w, double h, double T, int v) {
        int[] wallIds = {211, 212, 213};
        int tid = wallIds[v % wallIds.length];
        for (double ty = y; ty < y + h; ty += T)
            for (double tx = x; tx < x + w; tx += T)
                drawTile(gc, tid, tx, ty, Math.min(T, x+w-tx), Math.min(T, y+h-ty));
    }

    private void drawWallV(GraphicsContext gc, double x, double y, double w, double h, double T, int v) {
        drawWall(gc, x, y, w, h, T, v);
    }

    // -- Vẽ phần mặt tiền (kẻ ngang gỗ, cửa, rèm) --
    private void drawFacade(GraphicsContext gc, double x, double y, double w, double h, double T, int v) {
        // Kẻ sọc gỗ ngang
        int[] facadeIds = {278, 279, 280};
        int fid = facadeIds[v % facadeIds.length];
        for (double tx = x; tx < x + w; tx += T)
            drawTile(gc, fid, tx, y, Math.min(T, x+w-tx), h);

        // Cửa chính ở giữa
        double doorW = Math.min(T * 1.1, w * 0.35);
        double doorH = h * 1.15;
        double doorX = x + (w - doorW) / 2;
        int[] doorIds = {355, 357, 355};
        drawTile(gc, doorIds[v % doorIds.length], doorX, y - h * 0.1, doorW, doorH);

        // Rèm noren trái
        if (w > T * 3) {
            drawTile(gc, 186, doorX - T * 0.95, y + h * 0.05, T * 0.85, h * 0.85);
        }
        // Bảng hiệu/cửa sổ phải
        if (w > T * 3.5) {
            int[] winIds = {358, 359, 358};
            drawTile(gc, winIds[v % winIds.length], doorX + doorW + T * 0.1, y + h * 0.1, T * 0.9, h * 0.75);
        }
    }

    private void drawFacadeV(GraphicsContext gc, double x, double y, double w, double h, double T, int v) {
        int[] facadeIds = {278, 279, 280};
        int fid = facadeIds[v % facadeIds.length];
        for (double ty = y; ty < y + h; ty += T)
            drawTile(gc, fid, x, ty, w, Math.min(T, y+h-ty));
        // Cửa ở giữa
        double doorH2 = Math.min(T * 1.0, h * 0.35);
        double doorY2 = y + (h - doorH2) / 2;
        int[] doorIds = {355, 357, 355};
        drawTile(gc, doorIds[v % doorIds.length], x, doorY2, w, doorH2);
    }

    // -- Vẽ phần mái --
    private void drawRoof(GraphicsContext gc, double x, double y, double w, double h, double T, int v) {
        // Góc trái, giữa lặp, góc phải
        int[][] sets = {
            {151, 152, 153},  // variant 0: mái xanh xám
            {154, 155, 156},  // variant 1: mái đỏ cam
            {129, 130, 131}   // variant 2: mái tối
        };
        int[] s = sets[v % sets.length];
        drawTile(gc, s[0], x, y, T, h);
        double mid = x + T;
        while (mid + T < x + w) {
            drawTile(gc, s[1], mid, y, T, h);
            mid += T;
        }
        if (mid < x + w) drawTile(gc, s[2], mid, y, x + w - mid, h);
        else              drawTile(gc, s[2], x + w - T, y, T, h);
    }

    private void drawRoofV(GraphicsContext gc, double x, double y, double w, double h, double T, int v) {
        int[] ids = {1, 5, 3};
        int tid = ids[v % ids.length];
        for (double ty = y; ty < y + h; ty += T)
            drawTile(gc, tid, x, ty, w, Math.min(T, y+h-ty));
    }

    // -- Props (máy bán nước, chậu cây, hàng rào) --
    private void drawStreetProps(GraphicsContext gc, double W, double H,
                                  double roadTop, double roadBot, double roadLeft, double roadRight, double pad) {
        double T = 48;
        double sidewalkN = roadTop - pad - 18;   // Y vỉa hè phía bắc
        double sidewalkS = roadBot + pad;         // Y vỉa hè phía nam

        // Máy bán nước - đặt ngay trên vỉa hè phía Bắc
        if (sidewalkN > T) {
            drawTile(gc, 68, roadLeft  - T * 1.5, sidewalkN - T * 1.4, T * 1.0, T * 1.4);
            drawTile(gc, 68, roadRight + T * 0.5, sidewalkN - T * 1.4, T * 1.0, T * 1.4);
        }
        // Máy bán nước - phía Nam
        if (H - sidewalkS > T * 1.5) {
            drawTile(gc, 68, roadLeft  - T * 1.5, sidewalkS + 2, T * 1.0, T * 1.4);
            drawTile(gc, 68, roadRight + T * 0.5, sidewalkS + 2, T * 1.0, T * 1.4);
        }

        // Chậu cây - dọc theo đường Bắc
        double[] pxN = {T * 0.5, T * 1.8, W - T * 2.5, W - T * 1.1};
        if (sidewalkN > T) {
            for (double px : pxN)
                drawTile(gc, 84, px, sidewalkN - T * 1.1, T * 0.85, T * 0.95);
        }
        // Chậu cây - đường Nam
        double[] pxS = {T * 0.5, T * 1.8, W - T * 2.5, W - T * 1.1};
        if (H - sidewalkS > T * 1.2) {
            for (double px : pxS)
                drawTile(gc, 84, px, sidewalkS + 4, T * 0.85, T * 0.95);
        }
        // Chậu cây - bên trái đường
        double leftEdge = roadLeft - pad - 18;
        if (leftEdge > T) {
            drawTile(gc, 84, leftEdge - T * 1.1, roadTop + (roadBot-roadTop)/2 - T*0.4, T*0.85, T*0.95);
        }
        // Chậu cây - bên phải đường
        double rightEdge = roadRight + pad;
        if (W - rightEdge > T) {
            drawTile(gc, 84, rightEdge + 4, roadTop + (roadBot-roadTop)/2 - T*0.4, T*0.85, T*0.95);
        }
    }

    // Helper: vẽ một tile với kích thước tùy chỉnh
    private void drawTile(GraphicsContext gc, int tileId, double x, double y, double w, double h) {
        Image img = tiles.get(tileId);
        if (img != null && w > 0 && h > 0) {
            gc.drawImage(img, x, y, w, h);
        }
    }



    // Tính ranh giới an toàn (khoảng cách tối thiểu tới làn đường)
    private double getSafeBoundary(double W, double H, String direction) {
        double closest = switch (direction) {
            case "north" -> H / 2;
            case "south" -> H / 2;
            case "west"  -> W / 2;
            case "east"  -> W / 2;
            default -> H / 2;
        };
        for (Lane lane : lanes) {
            if (lane == null) continue;
            List<com.traffic.core.Vector2D> pts = lane.getwaypoints();
            if (pts == null || pts.size() < 2) continue;
            for (com.traffic.core.Vector2D pt : pts) {
                switch (direction) {
                    case "north" -> closest = Math.min(closest, pt.getY());
                    case "south" -> closest = Math.max(closest, pt.getY());
                    case "west"  -> closest = Math.min(closest, pt.getX());
                    case "east"  -> closest = Math.max(closest, pt.getX());
                }
            }
        }
        return closest;
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
        // Cây và props đã được vẽ trong drawBuildings -> drawStreetProps
    }


    private void drawPedestrians(GraphicsContext gc) {
        // Trống - chờ làm lại
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

            // 2. Mặt đường asphalt hoặc texture đường
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
        if (v.getYieldMode() == Vehicle.YieldMode.STOP_BEFORE_CONFLICT
                || v.getYieldMode() == Vehicle.YieldMode.STOP
                || v.getYieldMode() == Vehicle.YieldMode.CLEAR_CONFLICT
                || v.getYieldMode() == Vehicle.YieldMode.CLEAR_INTERSECTION
                || v.getYieldMode() == Vehicle.YieldMode.CLEAR_PATH
                || v.getYieldMode() == Vehicle.YieldMode.URGENT_CLEAR_PATH) {
            gc.setStroke(Color.rgb(255, 140, 0, 160.0 / 255));
            gc.setLineWidth(2.5);
            gc.strokeRoundRect(-w / 2 - 4, -h / 2 - 4, w + 8, h + 8, 5, 5);
        }

        if (isUrgentYield(v)) {
            gc.setStroke(Color.rgb(255, 230, 70, 220.0 / 255));
            gc.setLineWidth(1.4);
            gc.setLineDashes(4, 4);
            gc.strokeRoundRect(-w / 2 - 6, -h / 2 - 6, w + 12, h + 12, 6, 6);
            gc.setLineDashes();
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
    private boolean isUrgentYield(Vehicle v) {
        return v != null && (v.getYieldMode() == Vehicle.YieldMode.URGENT_CLEAR_PATH
                || v.getYieldMode() == Vehicle.YieldMode.CLEAR_PATH
                || v.getManeuverState() == Vehicle.ManeuverState.URGENT_CLEARING);
    }

}