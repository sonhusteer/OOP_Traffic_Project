package com.traffic.view;

import com.traffic.config.Constants;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import com.traffic.model.map.CityMap;
import com.traffic.model.map.IntersectionNode;
import com.traffic.model.map.RoadEdge;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MapRenderer {

    // =========================================================
    // DATA CLASSES
    // =========================================================

    public static class StreetLight {
        public final double x, y, faceAngle;
        public StreetLight(double x, double y, double fa) { this.x = x; this.y = y; this.faceAngle = fa; }
    }

    public static class Decoration {
        public enum Type { BUILDING, SHOP, HOUSE, TREE, PARK, PARKING, BUS_STOP, GAS_STATION }
        public final Type type;
        public final double x, y, w, h;
        public Color c1, c2;
        public boolean[][] windows;
        public String label;
        public final List<double[]> subs = new ArrayList<>();

        public Decoration(Type t, double x, double y, double w, double h) {
            this.type = t; this.x = x; this.y = y; this.w = w; this.h = h;
        }
    }

    // =========================================================
    // COLOUR PALETTES (static finals — no alloc per frame)
    // =========================================================

    private static final Color[] BLDG = {
        Color.web("#c0a882"), Color.web("#8fa3c0"), Color.web("#b5c4a0"),
        Color.web("#c4a0a0"), Color.web("#a0b5c4"), Color.web("#c4baa0")
    };
    private static final Color[] ROOF = {
        Color.web("#7a6e5e"), Color.web("#5e6e7a"), Color.web("#6e7a5e"),
        Color.web("#7a5e5e"), Color.web("#5e5e7a"), Color.web("#635d4e")
    };
    private static final Color[] SHOP_WALL = {
        Color.web("#f5e6cc"), Color.web("#cce6f5"), Color.web("#e6ccf5"),
        Color.web("#ccf5e6"), Color.web("#f5cccc"), Color.web("#fff0d4")
    };
    private static final Color[] AWNING = {
        Color.web("#e74c3c"), Color.web("#3498db"), Color.web("#e67e22"),
        Color.web("#2ecc71"), Color.web("#9b59b6"), Color.web("#e91e63")
    };
    private static final String[] LABELS = {
        "Coffee", "Bakery", "Market", "Pho", "BBQ", "Bun bo", "Cafe", "Shop"
    };
    private static final Color[] WALL = {
        Color.web("#f0e6d3"), Color.web("#d3e6f0"), Color.web("#e6d3f0"),
        Color.web("#d3f0e6"), Color.web("#f0d3d3"), Color.web("#f5f0e6")
    };
    private static final Color[] HROOF = {
        Color.web("#c0392b"), Color.web("#e74c3c"), Color.web("#8e44ad"),
        Color.web("#d35400"), Color.web("#7f8c8d"), Color.web("#2c3e50")
    };

    // =========================================================
    // GENERATION  (call once on map load — deterministic seed)
    // =========================================================

    public static List<Decoration> generateDecorations(CityMap map) {
        long seed = 12345L;
        for (IntersectionNode n : map.getNodes()) seed = seed * 31 + (long)(n.getX() * 997 + n.getY() * 31);
        Random rng = new Random(seed);
        List<Decoration> result = new ArrayList<>();
        double hw = Constants.ROAD_WIDTH / 2, clear = hw + 28;

        for (double gx = -500; gx < 2200; gx += 78) {
            for (double gy = -600; gy < 1900; gy += 78) {
                double cx = gx + (rng.nextDouble() - .5) * 44;
                double cy = gy + (rng.nextDouble() - .5) * 44;
                double minD = minDistToRoads(cx, cy, map);
                if (minD < clear) continue;
                double avail = minD - clear;
                int roll = rng.nextInt(100);

                if      (avail > 50 && roll < 18) addBuilding(result, cx, cy, avail, rng);
                else if (avail > 35 && roll < 32) addShop   (result, cx, cy, avail, rng);
                else if (avail > 28 && roll < 46) addHouse  (result, cx, cy, avail, rng);
                else if (avail > 12 && roll < 68) addTree   (result, cx, cy, avail, rng);
                else if (avail > 65 && roll < 74) addPark   (result, cx, cy, avail, rng);
                else if (avail > 70 && roll < 79) addParking(result, cx, cy, avail, rng);
                else if (avail > 18 && roll < 82) addBusStop(result, cx, cy);
                else if (avail > 60 && roll < 85) addGas    (result, cx, cy, avail, rng);
            }
        }
        return result;
    }

    private static void addBuilding(List<Decoration> out, double cx, double cy, double av, Random r) {
        double w = 38 + r.nextInt(28), h = 45 + r.nextInt(50);
        if (av < Math.max(w,h)/2 + 5) return;
        Decoration d = new Decoration(Decoration.Type.BUILDING, cx-w/2, cy-h/2, w, h);
        d.c1 = BLDG[r.nextInt(BLDG.length)];
        d.c2 = ROOF[r.nextInt(ROOF.length)];
        int rows = Math.max(2,(int)(h/11)), cols = Math.max(1,(int)(w/9));
        d.windows = new boolean[rows][cols];
        for (int i=0;i<rows;i++) for (int j=0;j<cols;j++) d.windows[i][j] = r.nextBoolean();
        out.add(d);
    }
    private static void addShop(List<Decoration> out, double cx, double cy, double av, Random r) {
        double w = 30 + r.nextInt(20), h = 20 + r.nextInt(14);
        if (av < Math.max(w,h)/2 + 5) return;
        Decoration d = new Decoration(Decoration.Type.SHOP, cx-w/2, cy-h/2, w, h);
        d.c1 = SHOP_WALL[r.nextInt(SHOP_WALL.length)];
        d.c2 = AWNING[r.nextInt(AWNING.length)];
        d.label = LABELS[r.nextInt(LABELS.length)];
        out.add(d);
    }
    private static void addHouse(List<Decoration> out, double cx, double cy, double av, Random r) {
        double w = 26 + r.nextInt(14), h = 20 + r.nextInt(10);
        if (av < Math.max(w,h)/2 + 5) return;
        Decoration d = new Decoration(Decoration.Type.HOUSE, cx-w/2, cy-h/2, w, h);
        d.c1 = WALL [r.nextInt(WALL .length)];
        d.c2 = HROOF[r.nextInt(HROOF.length)];
        out.add(d);
    }
    private static void addTree(List<Decoration> out, double cx, double cy, double av, Random r) {
        double rad = 10 + r.nextInt(10);
        if (av < rad + 5) return;
        out.add(new Decoration(Decoration.Type.TREE, cx, cy, rad, rad));
    }
    private static void addPark(List<Decoration> out, double cx, double cy, double av, Random r) {
        double w = 65 + r.nextInt(40), h = 55 + r.nextInt(35);
        if (av < Math.max(w,h)/2 + 5) return;
        Decoration d = new Decoration(Decoration.Type.PARK, cx-w/2, cy-h/2, w, h);
        int n = 2 + r.nextInt(4);
        for (int i=0;i<n;i++) d.subs.add(new double[]{
            cx-w/2+10+r.nextDouble()*(w-20), cy-h/2+10+r.nextDouble()*(h-20), 6+r.nextInt(6)});
        d.subs.add(new double[]{cx-10, cy+h/2-15, -1}); // bench marker
        out.add(d);
    }
    private static void addParking(List<Decoration> out, double cx, double cy, double av, Random r) {
        double w = 82, h = 62;
        if (av < Math.max(w,h)/2 + 5) return;
        Decoration d = new Decoration(Decoration.Type.PARKING, cx-w/2, cy-h/2, w, h);
        for (int i=0;i<3;i++) d.subs.add(new double[]{cx-w/2+13+i*25, cy, r.nextInt(6)*1.0});
        out.add(d);
    }
    private static void addBusStop(List<Decoration> out, double cx, double cy) {
        out.add(new Decoration(Decoration.Type.BUS_STOP, cx, cy, 22, 32));
    }
    private static void addGas(List<Decoration> out, double cx, double cy, double av, Random r) {
        double w = 72, h = 52;
        if (av < Math.max(w,h)/2 + 5) return;
        Decoration d = new Decoration(Decoration.Type.GAS_STATION, cx-w/2, cy-h/2, w, h);
        d.c1 = Color.web("#ecf0f1"); d.c2 = Color.web("#e74c3c");
        out.add(d);
    }

    // ---- Street lights ----

    public static List<StreetLight> generateStreetLights(CityMap map) {
        List<StreetLight> lights = new ArrayList<>();
        double off = Constants.ROAD_WIDTH / 2 + 12, spacing = 150;
        for (RoadEdge road : map.getRoads()) {
            double sx = road.getStartNode().getX(), sy = road.getStartNode().getY();
            double ex = road.getEndNode().getX(),   ey = road.getEndNode().getY();
            double len = Math.hypot(ex-sx, ey-sy);
            if (len < 1) continue;
            double dx = (ex-sx)/len, dy = (ey-sy)/len, px = -dy, py = dx;
            int cnt = 0;
            for (double t = spacing/2; t < len - spacing/4; t += spacing, cnt++) {
                double bx = sx+dx*t, by = sy+dy*t;
                double side = (cnt % 2 == 0) ? 1 : -1;
                double fa = Math.toDegrees(Math.atan2(-py*side, -px*side));
                lights.add(new StreetLight(bx + px*off*side, by + py*off*side, fa));
            }
        }
        return lights;
    }

    // =========================================================
    // GEOMETRY HELPERS
    // =========================================================

    private static double minDistToRoads(double px, double py, CityMap map) {
        double min = Double.MAX_VALUE;
        for (RoadEdge r : map.getRoads())
            min = Math.min(min, pointSegDist(px, py,
                r.getStartNode().getX(), r.getStartNode().getY(),
                r.getEndNode().getX(),   r.getEndNode().getY()));
        for (IntersectionNode n : map.getNodes())
            min = Math.min(min, Math.hypot(px-n.getX(), py-n.getY()));
        return min;
    }

    private static double pointSegDist(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx-ax, dy = by-ay, len2 = dx*dx+dy*dy;
        if (len2 < .001) return Math.hypot(px-ax, py-ay);
        double t = Math.max(0, Math.min(1, ((px-ax)*dx+(py-ay)*dy)/len2));
        return Math.hypot(px-(ax+t*dx), py-(ay+t*dy));
    }

    // =========================================================
    // RENDER — BACKGROUND
    // =========================================================

    public static void drawBackground(GraphicsContext gc, double camX, double camY) {
        gc.setFill(Color.web("#E8E6E0"));
        gc.fillRect(camX - 5000, camY - 5000, 15000, 15000);
    }

    // =========================================================
    // RENDER — SIDEWALKS
    // =========================================================

    public static void drawSidewalks(GraphicsContext gc, CityMap map) {
        gc.setLineCap(StrokeLineCap.BUTT);
        gc.setStroke(Color.web("#C8C4B8"));
        gc.setLineWidth(Constants.ROAD_WIDTH + 24);
        for (RoadEdge r : map.getRoads()) {
            if (r.getType() == RoadEdge.RoadType.ALLEY) continue;
            gc.strokeLine(r.getStartNode().getX(), r.getStartNode().getY(),
                          r.getEndNode().getX(),   r.getEndNode().getY());
        }
        // Intersection fill
        double hw = Constants.ROAD_WIDTH / 2 + 12;
        for (IntersectionNode n : map.getNodes()) {
            if (n.isSpawnNode() || n.getType() == IntersectionNode.NodeType.FIVE_WAY) continue;
            gc.setFill(Color.web("#C8C4B8"));
            gc.fillRect(n.getX() - hw, n.getY() - hw, hw*2, hw*2);
        }
    }

    // =========================================================
    // RENDER — ROADS + LANE MARKINGS
    // =========================================================

    public static void drawRoads(GraphicsContext gc, CityMap map) {
        for (RoadEdge road : map.getRoads()) {
            double sx = road.getStartNode().getX(), sy = road.getStartNode().getY();
            double ex = road.getEndNode().getX(),   ey = road.getEndNode().getY();
            double rw = road.getWidth();
            gc.setStroke(Color.web("#34495e"));
            gc.setLineWidth(rw);
            gc.setLineCap(StrokeLineCap.BUTT);
            gc.strokeLine(sx, sy, ex, ey);

            if (road.getType() != RoadEdge.RoadType.ALLEY) {
                gc.setStroke(Color.web("#f1c40f")); gc.setLineWidth(2);
                if (road.getType() == RoadEdge.RoadType.AVENUE) {
                    gc.setLineDashes(null);
                    gc.strokeLine(sx+2, sy+2, ex+2, ey+2);
                    gc.strokeLine(sx-2, sy-2, ex-2, ey-2);
                    drawDashedOffset(gc, sx, sy, ex, ey,  rw/4);
                    drawDashedOffset(gc, sx, sy, ex, ey, -rw/4);
                } else {
                    gc.setLineDashes(15, 15); gc.strokeLine(sx, sy, ex, ey); gc.setLineDashes(null);
                }
            }
        }
    }

    private static void drawDashedOffset(GraphicsContext gc, double sx, double sy, double ex, double ey, double off) {
        double a = Math.atan2(ey-sy, ex-sx), p = Math.PI/2;
        double ox = Math.cos(a+p)*off, oy = Math.sin(a+p)*off;
        gc.setStroke(Color.WHITE); gc.setLineWidth(2); gc.setLineDashes(15, 15);
        gc.strokeLine(sx+ox, sy+oy, ex+ox, ey+oy);
        gc.setLineDashes(null);
    }

    // =========================================================
    // RENDER — INTERSECTION DETAILS  (zebra, stop lines, bùng binh)
    // =========================================================

    public static void drawIntersectionDetails(GraphicsContext gc, CityMap map) {
        double halfW = Constants.ROAD_WIDTH / 2;
        for (IntersectionNode node : map.getNodes()) {
            double nX = node.getX(), nY = node.getY();

            if (node.getType() == IntersectionNode.NodeType.FIVE_WAY) {
                double islandR  = 40;
                double junctionR = Constants.ROUNDABOUT_RADIUS;
                double ringR     = (islandR + junctionR) / 2.0;

                gc.setFill(Color.web("#34495e"));
                gc.fillOval(nX-junctionR, nY-junctionR, junctionR*2, junctionR*2);

                gc.setStroke(Color.WHITE); gc.setLineWidth(2); gc.setLineDashes(12, 10);
                gc.strokeOval(nX-ringR, nY-ringR, ringR*2, ringR*2);
                gc.setLineDashes(null);

                gc.setFill(Color.web("#2ecc71"));
                gc.fillOval(nX-islandR, nY-islandR, islandR*2, islandR*2);
                gc.setStroke(Color.WHITE); gc.setLineWidth(2);
                gc.strokeOval(nX-islandR, nY-islandR, islandR*2, islandR*2);

                drawArmMarkings(gc, nX, nY,   0, junctionR, halfW);
                drawArmMarkings(gc, nX, nY,  90, junctionR, halfW);
                drawArmMarkings(gc, nX, nY, 180, junctionR, halfW);
                drawArmMarkings(gc, nX, nY, 270, junctionR, halfW);
                if (node.isHasNW()) drawArmMarkings(gc, nX, nY, 225, junctionR, halfW);

            } else {
                // Clear stray road lines
                gc.setFill(Color.web("#34495e"));
                gc.fillRect(nX-halfW, nY-halfW, Constants.ROAD_WIDTH, Constants.ROAD_WIDTH);

                boolean hasN = node.isHasNorth(), hasS = node.isHasSouth(),
                        hasE = node.isHasEast(),  hasW = node.isHasWest();

                // Zebra crossings (dashed, offset +5 — closer to intersection)
                gc.setStroke(Color.WHITE); gc.setLineWidth(6); gc.setLineDashes(4, 6);
                if (hasN) gc.strokeLine(nX-halfW+5, nY-halfW-5, nX+halfW-5, nY-halfW-5);
                if (hasS) gc.strokeLine(nX-halfW+5, nY+halfW+5, nX+halfW-5, nY+halfW+5);
                if (hasW) gc.strokeLine(nX-halfW-5, nY-halfW+5, nX-halfW-5, nY+halfW-5);
                if (hasE) gc.strokeLine(nX+halfW+5, nY-halfW+5, nX+halfW+5, nY+halfW-5);
                gc.setLineDashes(null);

                // Stop lines (solid, offset +15 — farther from intersection)
                gc.setStroke(Color.WHITE); gc.setLineWidth(3);
                if (hasN) gc.strokeLine(nX-halfW, nY-halfW-15, nX,       nY-halfW-15);
                if (hasS) gc.strokeLine(nX,       nY+halfW+15, nX+halfW, nY+halfW+15);
                if (hasW) gc.strokeLine(nX-halfW-15, nY,       nX-halfW-15, nY+halfW);
                if (hasE) gc.strokeLine(nX+halfW+15, nY-halfW, nX+halfW+15, nY);

                // Guide arcs 
                gc.setStroke(Color.rgb(241, 196, 15, 0.4)); gc.setLineWidth(1.5); gc.setLineDashes(5, 10);
                gc.strokeArc(nX-halfW, nY-halfW, halfW, halfW, 270, 90, javafx.scene.shape.ArcType.OPEN);
                gc.strokeArc(nX,       nY-halfW, halfW, halfW, 180, 90, javafx.scene.shape.ArcType.OPEN);
                gc.strokeArc(nX-halfW, nY,       halfW, halfW,   0, 90, javafx.scene.shape.ArcType.OPEN);
                gc.strokeArc(nX,       nY,       halfW, halfW,  90, 90, javafx.scene.shape.ArcType.OPEN);
                gc.setLineDashes(null);
            }
        }
    }

    private static void drawArmMarkings(GraphicsContext gc, double cx, double cy,
                                        double angDeg, double junctionR, double halfW) {
        double a = Math.toRadians(angDeg);
        double ux = Math.cos(a), uy = Math.sin(a);
        double px = uy, py = -ux; // perpendicular

        double zd = junctionR + 55, zx = cx+ux*zd, zy = cy+uy*zd;
        gc.setStroke(Color.WHITE); gc.setLineWidth(6); gc.setLineDashes(4, 6);
        gc.strokeLine(zx-px*halfW, zy-py*halfW, zx+px*halfW, zy+py*halfW);
        gc.setLineDashes(null);

        double sd = junctionR + 69, sx = cx+ux*sd, sy = cy+uy*sd;
        gc.setLineWidth(3);
        gc.strokeLine(sx, sy, sx+px*halfW, sy+py*halfW);
    }

    // =========================================================
    // RENDER — DECORATIONS (ground level: parks, parking)
    // =========================================================

    public static void drawDecorationsGround(GraphicsContext gc, List<Decoration> decs) {
        for (Decoration d : decs) {
            if (d.type == Decoration.Type.PARK)    drawPark   (gc, d);
            if (d.type == Decoration.Type.PARKING) drawParking(gc, d);
        }
    }

    // =========================================================
    // RENDER — DECORATIONS (elevated: buildings, trees, shops)
    // =========================================================

    public static void drawDecorationsAbove(GraphicsContext gc, List<Decoration> decs, double darkness) {
        for (Decoration d : decs) {
            switch (d.type) {
                case BUILDING    -> drawBuilding  (gc, d, darkness);
                case SHOP        -> drawShop      (gc, d);
                case HOUSE       -> drawHouse     (gc, d);
                case TREE        -> drawTree      (gc, d);
                case BUS_STOP    -> drawBusStop   (gc, d);
                case GAS_STATION -> drawGasStation(gc, d);
                default -> {}
            }
        }
    }

    // ---- individual draw helpers ----

    private static void drawBuilding(GraphicsContext gc, Decoration d, double darkness) {
        gc.setFill(Color.rgb(0,0,0, 0.15)); gc.fillRect(d.x+5, d.y+8, d.w, d.h);
        gc.setFill(d.c1); gc.fillRect(d.x, d.y, d.w, d.h);
        gc.setFill(d.c2); gc.fillRect(d.x, d.y, d.w, 5);
        if (d.windows != null) {
            int rows = d.windows.length, cols = d.windows[0].length;
            double ww = (d.w-4)/cols, wh = (d.h-8)/rows;
            for (int r=0; r<rows; r++) {
                for (int c=0; c<cols; c++) {
                    double wx = d.x+2+c*ww+1, wy = d.y+7+r*wh+1;
                    double ws = Math.min(ww-2, wh-2);
                    if (ws < 2) continue;
                    boolean lit = d.windows[r][c];
                    if (darkness > 0.4 && lit)  gc.setFill(Color.rgb(255,230,150, 0.85+0.15*darkness));
                    else if (darkness > 0.4)    gc.setFill(Color.rgb(20, 30, 50, 0.75));
                    else                         gc.setFill(Color.rgb(180,210,230, 0.7));
                    gc.fillRect(wx, wy, ws, ws);
                }
            }
        }
        gc.setStroke(d.c1.darker()); gc.setLineWidth(0.5); gc.strokeRect(d.x, d.y, d.w, d.h);
    }

    private static void drawShop(GraphicsContext gc, Decoration d) {
        gc.setFill(Color.rgb(0,0,0,0.15)); gc.fillRect(d.x+4, d.y+5, d.w, d.h);
        gc.setFill(d.c1); gc.fillRect(d.x, d.y, d.w, d.h);
        // Striped awning at bottom
        int strips = Math.max(2,(int)(d.w/6));
        for (int i=0; i<strips; i++) {
            gc.setFill(i%2==0 ? d.c2 : d.c2.brighter());
            gc.fillRect(d.x+i*d.w/strips, d.y+d.h-6, d.w/strips+1, 6);
        }
        if (d.label != null) {
            gc.setFill(Color.web("#2c3e50"));
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 7));
            gc.fillText(d.label, d.x+3, d.y+d.h/2+3);
        }
        gc.setStroke(Color.web("#bdc3c7")); gc.setLineWidth(0.5); gc.strokeRect(d.x, d.y, d.w, d.h);
    }

    private static void drawHouse(GraphicsContext gc, Decoration d) {
        gc.setFill(Color.rgb(0,0,0,0.15)); gc.fillRect(d.x+4, d.y+5, d.w, d.h);
        gc.setFill(d.c1); gc.fillRect(d.x, d.y, d.w, d.h);
        // Pitched roof
        double[] rx = {d.x-3, d.x+d.w+3, d.x+d.w/2};
        double[] ry = {d.y,   d.y,        d.y-d.h*0.38};
        gc.setFill(d.c2); gc.fillPolygon(rx, ry, 3);
        gc.setFill(Color.web("#8B6914")); gc.fillRect(d.x+d.w/2-3, d.y+d.h-8, 6, 8);
        gc.setStroke(d.c1.darker()); gc.setLineWidth(0.5); gc.strokeRect(d.x, d.y, d.w, d.h);
    }

    private static void drawTree(GraphicsContext gc, Decoration d) {
        double tx = d.x, ty = d.y, r = d.w;
        gc.setFill(Color.rgb(0,0,0,0.15)); gc.fillOval(tx-r+4, ty+r*0.2, r*2-4, r*0.8);
        gc.setStroke(Color.web("#7d5a3c")); gc.setLineWidth(3);
        gc.strokeLine(tx, ty+r*0.3, tx, ty+r*1.05);
        gc.setFill(Color.web("#1e8449")); gc.fillOval(tx-r*0.85, ty-r*0.85, r*1.7, r*1.7);
        gc.setFill(Color.web("#27ae60")); gc.fillOval(tx-r*0.70, ty-r*0.95, r*1.45, r*1.45);
        gc.setFill(Color.web("#58d68d")); gc.fillOval(tx-r*0.42, ty-r*0.88, r*0.85, r*0.85);
    }

    private static void drawPark(GraphicsContext gc, Decoration d) {
        gc.setFill(Color.web("#58d68d", 0.65)); gc.fillRoundRect(d.x, d.y, d.w, d.h, 8, 8);
        gc.setStroke(Color.web("#27ae60")); gc.setLineWidth(1); gc.strokeRoundRect(d.x, d.y, d.w, d.h, 8, 8);
        gc.setFill(Color.web("#d5b880", 0.5));
        gc.fillRect(d.x+d.w/2-5, d.y, 10, d.h); // path
        if (d.w > 80) {
            gc.setFill(Color.web("#3498db", 0.45));
            gc.fillOval(d.x+d.w*0.6, d.y+d.h*0.3, d.w*0.25, d.h*0.3);
        }
        for (double[] s : d.subs) {
            if (s[2] < 0) {
                gc.setFill(Color.web("#8B6914")); gc.fillRect(s[0]-8, s[1]-3, 16, 4);
            } else {
                double tr = s[2];
                gc.setFill(Color.web("#27ae60")); gc.fillOval(s[0]-tr, s[1]-tr, tr*2, tr*2);
                gc.setFill(Color.web("#2ecc71")); gc.fillOval(s[0]-tr*.7, s[1]-tr*.9, tr*1.4, tr*1.4);
            }
        }
    }

    private static void drawParking(GraphicsContext gc, Decoration d) {
        gc.setFill(Color.web("#bdc3c7")); gc.fillRect(d.x, d.y, d.w, d.h);
        gc.setStroke(Color.WHITE); gc.setLineWidth(1);
        int slots = 4;
        for (int i=0; i<=slots; i++) gc.strokeLine(d.x+i*d.w/slots, d.y, d.x+i*d.w/slots, d.y+d.h);
        gc.strokeLine(d.x, d.y, d.x+d.w, d.y);
        gc.strokeLine(d.x, d.y+d.h, d.x+d.w, d.y+d.h);
        Color[] cc = {Color.web("#e74c3c"),Color.web("#3498db"),Color.web("#2ecc71"),
                      Color.web("#e67e22"),Color.web("#9b59b6"),Color.web("#95a5a6")};
        for (double[] s : d.subs) {
            gc.setFill(cc[(int)s[2] % cc.length]);
            gc.fillRoundRect(s[0]-9, s[1]-5, 18, 10, 3, 3);
        }
        gc.setFill(Color.web("#7f8c8d")); gc.setFont(Font.font("Arial",FontWeight.BOLD,8));
        gc.fillText("P", d.x+d.w/2-3, d.y-3);
    }

    private static void drawBusStop(GraphicsContext gc, Decoration d) {
        gc.setStroke(Color.web("#7f8c8d")); gc.setLineWidth(2.5);
        gc.strokeLine(d.x, d.y, d.x, d.y+d.h);
        gc.setFill(Color.web("#3498db",0.75)); gc.fillRect(d.x-10, d.y, 20, 5);
        gc.setFill(Color.web("#8B6914")); gc.fillRect(d.x-8, d.y+d.h-8, 16, 3);
        gc.setFill(Color.web("#e74c3c")); gc.fillRect(d.x-4, d.y+7, 8, 10);
        gc.setFill(Color.WHITE); gc.setFont(Font.font("Arial",4)); gc.fillText("BUS", d.x-4, d.y+15);
    }

    private static void drawGasStation(GraphicsContext gc, Decoration d) {
        gc.setFill(d.c1); gc.fillRect(d.x, d.y, d.w, d.h*0.5);
        gc.setFill(d.c2); gc.fillRect(d.x, d.y, d.w, 5);
        for (int i=0; i<2; i++) {
            double px2 = d.x+d.w*0.25+i*d.w*0.5;
            gc.setFill(Color.web("#c0392b")); gc.fillRect(px2-5, d.y+d.h*.5, 10, 15);
            gc.setFill(Color.WHITE); gc.fillRect(px2-3, d.y+d.h*.5+3, 6, 5);
        }
        gc.setFill(d.c2); gc.fillRect(d.x+d.w/2-10, d.y+d.h*.6, 20, 12);
        gc.setFill(Color.WHITE); gc.setFont(Font.font("Arial",FontWeight.BOLD,7));
        gc.fillText("GAS", d.x+d.w/2-8, d.y+d.h*.6+9);
    }

    // =========================================================
    // RENDER — STREET LIGHTS
    // =========================================================

    public static void drawStreetLightPoles(GraphicsContext gc, List<StreetLight> lights) {
        for (StreetLight sl : lights) {
            gc.setStroke(Color.web("#606060")); gc.setLineWidth(1.8);
            gc.strokeLine(sl.x, sl.y+12, sl.x, sl.y);
            gc.setFill(Color.web("#808080")); gc.fillOval(sl.x-3, sl.y-3, 6, 6);
        }
    }

    public static void drawStreetLightGlow(GraphicsContext gc, List<StreetLight> lights, double darkness) {
        if (darkness < 0.3) return;
        double alpha = (darkness - 0.3) / 0.7 * 0.13;
        for (StreetLight sl : lights) {
            gc.setGlobalBlendMode(BlendMode.ADD);
            gc.setFill(Color.rgb(255, 215, 80, alpha));
            gc.fillOval(sl.x-44, sl.y-8, 88, 50);
            gc.setGlobalBlendMode(BlendMode.SRC_OVER);
            gc.setFill(Color.rgb(255, 235, 100, Math.min(1.0, (darkness-0.3)*2)));
            gc.fillOval(sl.x-3, sl.y-3, 6, 6);
        }
    }
}
