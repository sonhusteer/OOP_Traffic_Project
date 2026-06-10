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
    // COLOUR PALETTES (Cyberpunk / Neon Theme)
    // =========================================================

    private static final Color[] BLDG = {
        Color.web("#1a0b2e"), Color.web("#0f1c2b"), Color.web("#140d1f"),
        Color.web("#1c0a18"), Color.web("#0c1f1a"), Color.web("#151520")
    };
    private static final Color[] ROOF = {
        Color.web("#00f0ff"), Color.web("#ff003c"), Color.web("#bc13fe"),
        Color.web("#00ff66"), Color.web("#ffe600"), Color.web("#ff00a0")
    };
    private static final Color[] SHOP_WALL = {
        Color.web("#200b2e"), Color.web("#0b2e2d"), Color.web("#2e0b1c"),
        Color.web("#130b2e"), Color.web("#2b2e0b"), Color.web("#1b1b1b")
    };
    private static final Color[] AWNING = {
        Color.web("#ff00e4"), Color.web("#00e4ff"), Color.web("#e4ff00"),
        Color.web("#ff3b00"), Color.web("#00ff3b"), Color.web("#8000ff")
    };
    private static final String[] LABELS = {
        "CYBER", "NEON", "HACK", "SYNTH", "TECH", "DATA", "NET", "CODE"
    };
    private static final Color[] WALL = {
        Color.web("#181822"), Color.web("#22181d"), Color.web("#182022"),
        Color.web("#1c1822"), Color.web("#221c18"), Color.web("#1f2218")
    };
    private static final Color[] HROOF = {
        Color.web("#ff007f"), Color.web("#00ffcc"), Color.web("#cc00ff"),
        Color.web("#ffff00"), Color.web("#ff5500"), Color.web("#0055ff")
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
        gc.setFill(Color.web("#f0f0f5")); // Clean white/light background
        gc.fillRect(camX - 5000, camY - 5000, 15000, 15000);
        
        // Light grid lines
        gc.setStroke(Color.web("#d0d0e0", 0.5));
        gc.setLineWidth(1);
        for(int i = -5000; i < 5000; i += 100) {
            gc.strokeLine(camX - 5000, i, camX + 10000, i);
            gc.strokeLine(i, camY - 5000, i, camY + 10000);
        }
    }

    // =========================================================
    // RENDER — SIDEWALKS
    // =========================================================

    public static void drawSidewalks(GraphicsContext gc, CityMap map) {
        gc.setLineCap(StrokeLineCap.BUTT);
        gc.setStroke(Color.web("#e8e8f0")); // Light gray sidewalk
        gc.setLineWidth(Constants.ROAD_WIDTH + 28);
        for (RoadEdge r : map.getRoads()) {
            if (r.getType() == RoadEdge.RoadType.ALLEY) continue;
            gc.strokeLine(r.getStartNode().getX(), r.getStartNode().getY(),
                          r.getEndNode().getX(),   r.getEndNode().getY());
        }
        // Sidewalk border
        gc.setStroke(Color.web("#c0c0d0", 0.8));
        gc.setLineWidth(Constants.ROAD_WIDTH + 26);
        for (RoadEdge r : map.getRoads()) {
            if (r.getType() == RoadEdge.RoadType.ALLEY) continue;
            gc.strokeLine(r.getStartNode().getX(), r.getStartNode().getY(),
                          r.getEndNode().getX(),   r.getEndNode().getY());
        }
        
        // Intersection fill
        double hw = Constants.ROAD_WIDTH / 2 + 14;
        for (IntersectionNode n : map.getNodes()) {
            if (n.isSpawnNode() || n.getType() == IntersectionNode.NodeType.FIVE_WAY) continue;
            gc.setFill(Color.web("#c0c0d0", 0.8));
            gc.fillRect(n.getX() - hw + 1, n.getY() - hw + 1, (hw-1)*2, (hw-1)*2);
            gc.setFill(Color.web("#e8e8f0"));
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
            gc.setStroke(Color.web("#555566")); // Medium gray asphalt
            gc.setLineWidth(rw);
            gc.setLineCap(StrokeLineCap.BUTT);
            gc.strokeLine(sx, sy, ex, ey);

            if (road.getType() != RoadEdge.RoadType.ALLEY) {
                gc.setStroke(Color.web("#ff2255")); gc.setLineWidth(2.5); // Bright neon red divider
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
        gc.setStroke(Color.web("#00ffff")); gc.setLineWidth(2.5); gc.setLineDashes(15, 15); // Bright cyan dashes
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

                gc.setFill(Color.web("#555566"));
                gc.fillOval(nX-junctionR, nY-junctionR, junctionR*2, junctionR*2);

                gc.setStroke(Color.WHITE); gc.setLineWidth(2.5); gc.setLineDashes(12, 10);
                gc.strokeOval(nX-ringR, nY-ringR, ringR*2, ringR*2);
                gc.setLineDashes(null);

                gc.setFill(Color.web("#77cc44")); // Green island
                gc.fillOval(nX-islandR, nY-islandR, islandR*2, islandR*2);
                gc.setStroke(Color.web("#44aa22")); gc.setLineWidth(2);
                gc.strokeOval(nX-islandR, nY-islandR, islandR*2, islandR*2);

                drawArmMarkings(gc, nX, nY,   0, junctionR, halfW);
                drawArmMarkings(gc, nX, nY,  90, junctionR, halfW);
                drawArmMarkings(gc, nX, nY, 180, junctionR, halfW);
                drawArmMarkings(gc, nX, nY, 270, junctionR, halfW);
                if (node.isHasNW()) drawArmMarkings(gc, nX, nY, 225, junctionR, halfW);

            } else {
                // Clear stray road lines
                gc.setFill(Color.web("#555566"));
                gc.fillRect(nX-halfW, nY-halfW, Constants.ROAD_WIDTH, Constants.ROAD_WIDTH);

                boolean hasN = node.isHasNorth(), hasS = node.isHasSouth(),
                        hasE = node.isHasEast(),  hasW = node.isHasWest();

                // Zebra crossings (bright cyan)
                gc.setStroke(Color.web("#00ffff")); gc.setLineWidth(6); gc.setLineDashes(4, 6);
                if (hasN) gc.strokeLine(nX-halfW+5, nY-halfW-5, nX+halfW-5, nY-halfW-5);
                if (hasS) gc.strokeLine(nX-halfW+5, nY+halfW+5, nX+halfW-5, nY+halfW+5);
                if (hasW) gc.strokeLine(nX-halfW-5, nY-halfW+5, nX-halfW-5, nY+halfW-5);
                if (hasE) gc.strokeLine(nX+halfW+5, nY-halfW+5, nX+halfW+5, nY+halfW-5);
                gc.setLineDashes(null);

                // Stop lines (solid, offset +15 — farther from intersection)
                gc.setStroke(Color.web("#ff2255")); gc.setLineWidth(4);
                if (hasN) gc.strokeLine(nX-halfW, nY-halfW-15, nX,       nY-halfW-15);
                if (hasS) gc.strokeLine(nX,       nY+halfW+15, nX+halfW, nY+halfW+15);
                if (hasW) gc.strokeLine(nX-halfW-15, nY,       nX-halfW-15, nY+halfW);
                if (hasE) gc.strokeLine(nX+halfW+15, nY-halfW, nX+halfW+15, nY);

                // Guide arcs 
                if (!Constants.BASIC_MODE) {
                    gc.setStroke(Color.rgb(241, 196, 15, 0.4)); gc.setLineWidth(1.5); gc.setLineDashes(5, 10);
                    gc.strokeArc(nX-halfW, nY-halfW, halfW, halfW, 270, 90, javafx.scene.shape.ArcType.OPEN);
                    gc.strokeArc(nX,       nY-halfW, halfW, halfW, 180, 90, javafx.scene.shape.ArcType.OPEN);
                    gc.strokeArc(nX-halfW, nY,       halfW, halfW,   0, 90, javafx.scene.shape.ArcType.OPEN);
                    gc.strokeArc(nX,       nY,       halfW, halfW,  90, 90, javafx.scene.shape.ArcType.OPEN);
                    gc.setLineDashes(null);
                }
            }
        }
    }

    private static void drawArmMarkings(GraphicsContext gc, double cx, double cy,
                                        double angDeg, double junctionR, double halfW) {
        double a = Math.toRadians(angDeg);
        double ux = Math.cos(a), uy = Math.sin(a);
        double px = uy, py = -ux; // perpendicular

        double zd = junctionR + 55, zx = cx+ux*zd, zy = cy+uy*zd;
        gc.setStroke(Color.web("#00f0ff")); gc.setLineWidth(6); gc.setLineDashes(4, 6);
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
        if (Constants.BASIC_MODE) return;
        for (Decoration d : decs) {
            if (d.type == Decoration.Type.PARK)    drawPark   (gc, d);
            if (d.type == Decoration.Type.PARKING) drawParking(gc, d);
        }
    }

    // =========================================================
    // RENDER — DECORATIONS (elevated: buildings, trees, shops)
    // =========================================================

    public static void drawDecorationsAbove(GraphicsContext gc, List<Decoration> decs, double darkness) {
        if (Constants.BASIC_MODE) return;
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
                    if (darkness > 0.4 && lit)  gc.setFill(Color.rgb(0, 240, 255, 0.85+0.15*darkness)); // Cyan light
                    else if (darkness > 0.4)    gc.setFill(Color.rgb(20, 10, 40, 0.75));
                    else if (lit)                gc.setFill(Color.rgb(255, 0, 228, 0.7)); // Pink light day
                    else                         gc.setFill(Color.rgb(30, 20, 50, 0.5));
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
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 7));
            gc.fillText(d.label, d.x+3, d.y+d.h/2+3);
        }
        gc.setStroke(d.c2); gc.setLineWidth(1.5); gc.strokeRect(d.x, d.y, d.w, d.h); // glowing edge
    }

    private static void drawHouse(GraphicsContext gc, Decoration d) {
        gc.setFill(Color.rgb(0,0,0,0.15)); gc.fillRect(d.x+4, d.y+5, d.w, d.h);
        gc.setFill(d.c1); gc.fillRect(d.x, d.y, d.w, d.h);
        // Pitched roof
        double[] rx = {d.x-3, d.x+d.w+3, d.x+d.w/2};
        double[] ry = {d.y,   d.y,        d.y-d.h*0.38};
        gc.setFill(d.c2); gc.fillPolygon(rx, ry, 3);
        gc.setFill(Color.web("#00f0ff")); gc.fillRect(d.x+d.w/2-3, d.y+d.h-8, 6, 8); // neon door
        gc.setStroke(d.c2); gc.setLineWidth(1.0); gc.strokePolygon(rx, ry, 3); // neon roof border
    }

    private static void drawTree(GraphicsContext gc, Decoration d) {
        double tx = d.x, ty = d.y, r = d.w;
        gc.setFill(Color.rgb(0,0,0,0.3)); gc.fillOval(tx-r+4, ty+r*0.2, r*2-4, r*0.8);
        gc.setStroke(Color.web("#dd44ff")); gc.setLineWidth(3); // Bright purple trunk
        gc.strokeLine(tx, ty+r*0.3, tx, ty+r*1.05);
        gc.setFill(Color.web("#ff44cc", 0.9)); gc.fillOval(tx-r*0.85, ty-r*0.85, r*1.7, r*1.7); // Bright pink
        gc.setFill(Color.web("#44ffff", 0.85)); gc.fillOval(tx-r*0.70, ty-r*0.95, r*1.45, r*1.45); // Bright cyan
        gc.setFill(Color.web("#ff4466", 1.0)); gc.fillOval(tx-r*0.42, ty-r*0.88, r*0.85, r*0.85); // Bright red
    }

    private static void drawPark(GraphicsContext gc, Decoration d) {
        gc.setFill(Color.web("#2d1560", 0.9)); gc.fillRoundRect(d.x, d.y, d.w, d.h, 8, 8); // Lighter cyber park
        gc.setStroke(Color.web("#dd44ff")); gc.setLineWidth(2.5); gc.strokeRoundRect(d.x, d.y, d.w, d.h, 8, 8);
        gc.setFill(Color.web("#44ffff", 0.4));
        gc.fillRect(d.x+d.w/2-5, d.y, 10, d.h); // holographic path
        if (d.w > 80) {
            gc.setFill(Color.web("#ff44ff", 0.55));
            gc.fillOval(d.x+d.w*0.6, d.y+d.h*0.3, d.w*0.25, d.h*0.3);
        }
        for (double[] s : d.subs) {
            if (s[2] < 0) {
                gc.setFill(Color.web("#44ffff")); gc.fillRect(s[0]-8, s[1]-3, 16, 4);
            } else {
                double tr = s[2];
                gc.setFill(Color.web("#dd44ff")); gc.fillOval(s[0]-tr, s[1]-tr, tr*2, tr*2);
                gc.setFill(Color.web("#ff44cc")); gc.fillOval(s[0]-tr*.7, s[1]-tr*.9, tr*1.4, tr*1.4);
            }
        }
    }

    private static void drawParking(GraphicsContext gc, Decoration d) {
        gc.setFill(Color.web("#1e1045")); gc.fillRect(d.x, d.y, d.w, d.h);
        gc.setStroke(Color.web("#44ffff")); gc.setLineWidth(1.5);
        int slots = 4;
        for (int i=0; i<=slots; i++) gc.strokeLine(d.x+i*d.w/slots, d.y, d.x+i*d.w/slots, d.y+d.h);
        gc.strokeLine(d.x, d.y, d.x+d.w, d.y);
        gc.strokeLine(d.x, d.y+d.h, d.x+d.w, d.y+d.h);
        Color[] cc = {Color.web("#ff003c"),Color.web("#00f0ff"),Color.web("#00ff66"),
                      Color.web("#ffe600"),Color.web("#bc13fe"),Color.web("#ff00a0")};
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
        if (Constants.BASIC_MODE) return;
        for (StreetLight sl : lights) {
            gc.setStroke(Color.web("#bc13fe")); gc.setLineWidth(2);
            gc.strokeLine(sl.x, sl.y+12, sl.x, sl.y);
            gc.setFill(Color.web("#00f0ff")); gc.fillOval(sl.x-3, sl.y-3, 6, 6);
        }
    }

    public static void drawStreetLightGlow(GraphicsContext gc, List<StreetLight> lights, double darkness) {
        if (Constants.BASIC_MODE || darkness < 0.3) return;
        double alpha = (darkness - 0.3) / 0.7 * 0.15;
        for (StreetLight sl : lights) {
            gc.setGlobalBlendMode(BlendMode.ADD);
            gc.setFill(Color.rgb(0, 240, 255, alpha)); // Cyan glow
            gc.fillOval(sl.x-50, sl.y-10, 100, 60);
            gc.setGlobalBlendMode(BlendMode.SRC_OVER);
            gc.setFill(Color.rgb(188, 19, 254, Math.min(1.0, (darkness-0.3)*2))); // Purple core
            gc.fillOval(sl.x-3, sl.y-3, 6, 6);
        }
    }

    // =========================================================
    // ĐỒ HỌA ĐẶC TRƯNG BÁCH KHOA HÀ NỘI
    // =========================================================

    /** Vẽ đường sắt Hà Nội - Sài Gòn chạy dọc bên trái Giải Phóng */
    public static void drawRailway(GraphicsContext gc, double railX, double fromY, double toY) {
        if (Constants.BASIC_MODE) return;
        // Ballast (đá ba lát)
        gc.setFill(Color.web("#b0a898")); gc.fillRect(railX - 18, fromY, 36, toY - fromY);
        // 2 ray thép
        gc.setStroke(Color.web("#8a8a8a")); gc.setLineWidth(4);
        gc.strokeLine(railX - 10, fromY, railX - 10, toY);
        gc.strokeLine(railX + 10, fromY, railX + 10, toY);
        // Tà vẹt (sleepers) mỗi 22px
        gc.setStroke(Color.web("#5c3d1e")); gc.setLineWidth(6);
        for (double y = fromY; y < toY; y += 22) gc.strokeLine(railX - 16, y, railX + 16, y);
        // Viền ray bóng
        gc.setStroke(Color.web("#c0c0c0")); gc.setLineWidth(2);
        gc.strokeLine(railX - 10, fromY, railX - 10, toY);
        gc.strokeLine(railX + 10, fromY, railX + 10, toY);
    }

    /** Vẽ cổng parabol đặc trưng ĐHBK Hà Nội */
    public static void drawHUSTGate(GraphicsContext gc, double gateX, double gateY) {
        if (Constants.BASIC_MODE) return;
        double gateW = 130, gateH = 90;
        double lx = gateX - gateW / 2, rx = gateX + gateW / 2, baseY = gateY + 5;
        // 2 trụ cổng (đỏ gạch BK)
        gc.setFill(Color.web("#8B1A1A")); gc.fillRect(lx - 8, baseY - gateH, 16, gateH); gc.fillRect(rx - 8, baseY - gateH, 16, gateH);
        gc.setStroke(Color.web("#C8960C")); gc.setLineWidth(2); gc.strokeRect(lx - 8, baseY - gateH, 16, gateH); gc.strokeRect(rx - 8, baseY - gateH, 16, gateH);
        // Vòm parabol (3 đường cung lồng nhau)
        double arcW = gateW + 16, arcH = gateH * 0.9;
        gc.setFill(Color.web("#f5f0e8")); gc.fillArc(gateX - arcW/2, baseY - arcH*2 + arcH*0.1, arcW, arcH*2, 0, 180, javafx.scene.shape.ArcType.CHORD);
        gc.setStroke(Color.web("#8B1A1A")); gc.setLineWidth(5); gc.strokeArc(gateX - arcW/2, baseY - arcH*2 + arcH*0.1, arcW, arcH*2, 0, 180, javafx.scene.shape.ArcType.OPEN);
        double arcW2 = arcW - 14, arcH2 = arcH * 0.82;
        gc.setStroke(Color.web("#C8960C")); gc.setLineWidth(3); gc.strokeArc(gateX - arcW2/2, baseY - arcH2*2 + arcH2*0.12 + 6, arcW2, arcH2*2, 0, 180, javafx.scene.shape.ArcType.OPEN);
        // Chữ tên trường trên cổng
        gc.setFill(Color.web("#8B1A1A")); gc.setFont(Font.font("Arial", FontWeight.BOLD, 8));
        gc.fillText("ĐẠI HỌC BÁCH KHOA", gateX - 45, baseY - arcH + 24);
        gc.setFill(Color.web("#C8960C")); gc.setFont(Font.font("Arial", FontWeight.BOLD, 7));
        gc.fillText("1956", gateX - 10, baseY - 5);
        // Đèn trang trí đỉnh trụ
        gc.setFill(Color.web("#FFD700")); gc.fillOval(lx - 5, baseY - gateH - 5, 10, 10); gc.fillOval(rx - 5, baseY - gateH - 5, 10, 10);
    }

    /** Vẽ hồ nước trong campus */
    public static void drawCampusLake(GraphicsContext gc, double cx, double cy, double rx, double ry) {
        if (Constants.BASIC_MODE) return;
        gc.setFill(Color.web("#2980b9", 0.25)); gc.fillOval(cx-rx+4, cy-ry+4, rx*2, ry*2);
        gc.setFill(Color.web("#3498db", 0.75)); gc.fillOval(cx-rx, cy-ry, rx*2, ry*2);
        gc.setFill(Color.web("#74b9ff", 0.5)); gc.fillOval(cx-rx*0.5, cy-ry*0.55, rx*0.6, ry*0.4);
        gc.setStroke(Color.web("#2471a3")); gc.setLineWidth(2); gc.strokeOval(cx-rx, cy-ry, rx*2, ry*2);
        gc.setFill(Color.web("#1a5276")); gc.setFont(Font.font("Arial", FontWeight.BOLD, 9)); gc.fillText("Hồ BK", cx - 16, cy + 5);
    }

    /** Vẽ tòa nhà ĐHBK đặc trưng (màu đỏ gạch) */
    public static void drawHUSTBuilding(GraphicsContext gc, double x, double y, double w, double h, String label) {
        if (Constants.BASIC_MODE) return;
        gc.setFill(Color.web("#C0392B")); gc.fillRect(x, y, w, h);
        gc.setFill(Color.web("#ABEBC6", 0.7));
        int cols = Math.max(2, (int)(w/18)), rows = Math.max(2, (int)(h/18));
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) gc.fillRect(x + c*(w/cols)+3, y + r*(h/rows)+3, w/(cols*2.0), h/(rows*2.0));
        gc.setFill(Color.web("#BDC3C7")); gc.fillRect(x-3, y-6, w+6, 8);
        gc.setStroke(Color.web("#922B21")); gc.setLineWidth(1.5); gc.strokeRect(x, y, w, h);
        gc.setFill(Color.WHITE); gc.setFont(Font.font("Arial", FontWeight.BOLD, 9)); gc.fillText(label, x+3, y+h/2+4);
    }

    /** Vẽ toàn bộ cảnh đặc trưng ĐHBK Hà Nội */
    public static void drawBachKhoaLandmarks(GraphicsContext gc) {
        if (Constants.BASIC_MODE) return;
        // Đường sắt
        drawRailway(gc, com.traffic.model.map.CityMap.BK_RAIL_X, -100, 900);
        // Nhãn đường Giải Phóng
        gc.setFill(Color.web("#2c3e50")); gc.setFont(Font.font("Arial", FontWeight.BOLD, 11));
        gc.fillText("Đ. Giải Phóng", 225, 400);
        // Nhãn đường sắt (xoay dọc)
        gc.save(); gc.translate(130, 400); gc.rotate(-90);
        gc.setFill(Color.web("#555")); gc.setFont(Font.font("Arial", FontWeight.BOLD, 9));
        gc.fillText("Đường sắt HN-SG", -55, 0); gc.restore();
        // Cổng parabol ĐHBK
        drawHUSTGate(gc, 475, 403);
        // Hồ campus
        drawCampusLake(gc, 850, 400, 55, 38);
        // Các tòa nhà
        drawHUSTBuilding(gc, 635, 255, 72, 58, "C1");
        drawHUSTBuilding(gc, 635, 455, 72, 58, "B1");
        drawHUSTBuilding(gc, 755, 335, 56, 46, "TQB");
        // Cây xanh campus
        double[] tx = {482, 512, 542, 622, 652, 682, 732};
        double[] ty = {358, 325, 358, 278, 293, 278, 358};
        for (int i = 0; i < tx.length; i++) {
            gc.setFill(Color.web("#5d4037", 0.8)); gc.fillRect(tx[i]-2, ty[i]+8, 4, 12);
            gc.setFill(Color.web("#27ae60")); gc.fillOval(tx[i]-9, ty[i]-9, 18, 18);
            gc.setFill(Color.web("#58d68d")); gc.fillOval(tx[i]-6, ty[i]-12, 13, 13);
        }
        // Logo HUST
        gc.setFill(Color.web("#8B1A1A", 0.12)); gc.fillOval(678, 378, 124, 84);
        gc.setStroke(Color.web("#8B1A1A", 0.35)); gc.setLineWidth(2); gc.strokeOval(678, 378, 124, 84);
        gc.setFill(Color.web("#8B1A1A")); gc.setFont(Font.font("Arial", FontWeight.BOLD, 15)); gc.fillText("HUST", 716, 426);
        // Biển tên trường
        gc.setFill(Color.web("#8B1A1A")); gc.fillRoundRect(453, 318, 178, 32, 6, 6);
        gc.setFill(Color.WHITE); gc.setFont(Font.font("Arial", FontWeight.BOLD, 10)); gc.fillText("ĐH Bách Khoa Hà Nội", 460, 339);
    }

    /**
     * Vẽ rào chắn đường sắt (tự động hạ/nâng theo timer).
     * @param barrierDown  true = rào đang hạ (tàu đang qua)
     * @param timer        thời gian còn lại ở trạng thái hiện tại (giây)
     * @param totalTime    tổng thời gian của trạng thái hiện tại
     */
    public static void drawRailBarrier(GraphicsContext gc, double cx, double cy,
                                       boolean barrierDown, double timer, double totalTime) {
        // Góc tay rào: 0° = nằm ngang (đóng), -80° = dựng đứng (mở)
        double progress = Math.min(1.0, (totalTime - timer) / 0.8); // 0.8s animation
        double targetAngle = barrierDown ? 0.0 : -80.0;
        double prevAngle   = barrierDown ? -80.0 : 0.0;
        double armAngle = prevAngle + (targetAngle - prevAngle) * progress;

        // === TRỤ CHẮN (cột đứng) ===
        gc.setFill(Color.web("#e74c3c")); gc.fillRect(cx - 5, cy - 28, 10, 28); // thân trụ
        gc.setFill(Color.web("#c0392b")); gc.fillRect(cx - 7, cy - 30, 14, 6);  // đỉnh trụ
        gc.setFill(Color.BLACK); gc.fillOval(cx - 4, cy - 24, 8, 8);            // khớp xoay

        // === ĐÈN CẢNH BÁO trên trụ ===
        boolean blinkOn = (timer % 0.5) < 0.25; // chớp 2Hz
        gc.setFill(barrierDown && blinkOn ? Color.web("#ff0000") : Color.web("#880000"));
        gc.fillOval(cx - 5, cy - 42, 10, 10);

        // === TAY RÀO (xoay quanh khớp) ===
        gc.save();
        gc.translate(cx, cy - 20); // điểm xoay = khớp
        gc.rotate(armAngle);
        // Vẽ tay rào dài 70px với sọc đỏ-trắng
        double armLen = 70;
        int stripes = 5;
        for (int i = 0; i < stripes; i++) {
            double sx = i * armLen / stripes;
            gc.setFill(i % 2 == 0 ? Color.web("#e74c3c") : Color.WHITE);
            gc.fillRect(sx, -4, armLen / stripes, 8);
        }
        // Viền tay rào
        gc.setStroke(Color.web("#c0392b")); gc.setLineWidth(1);
        gc.strokeRect(0, -4, armLen, 8);
        // Đầu tay rào (hình tròn nhỏ)
        gc.setFill(Color.web("#e74c3c")); gc.fillOval(armLen - 5, -5, 10, 10);
        gc.restore();

        // === BIỂN CẢNH BÁO bên đường ===
        if (barrierDown) {
            gc.setFill(Color.web("#e74c3c", 0.85)); gc.fillRoundRect(cx + 5, cy - 45, 52, 14, 3, 3);
            gc.setFill(Color.WHITE); gc.setFont(Font.font("Arial", FontWeight.BOLD, 8));
            gc.fillText("🚂 TÀU QUA!", cx + 7, cy - 34);
        }
    }

    // =========================================================
    // ĐỒ HỌA ĐẶC TRƯNG MAP HỖN HỢP (sông + cầu + cây ven bờ)
    // =========================================================

    public static void drawMixedMapLandmarks(GraphicsContext gc, long tick) {
        if (Constants.BASIC_MODE) return;

        final double RIVER_Y    = 530;   // tâm sông (y world)
        final double RIVER_W    = 62;    // bề rộng sông
        final double RIVER_FROM = -300;  // bắt đầu từ trái
        final double RIVER_TO   = 1600;  // kết thúc bên phải

        // --- Sông ---
        drawRiver(gc, RIVER_Y, RIVER_W, RIVER_FROM, RIVER_TO, tick);

        // --- Cầu đường dọc x=580 (nối Ngã 5 ↔ Ngã 3) ---
        drawBridge(gc, 580, RIVER_Y, RIVER_W, true);

        // --- Cầu đường dọc x=840 (nối Ngã 4 phải ↔ Ngã 4 dưới) ---
        drawBridge(gc, 840, RIVER_Y, RIVER_W, true);

        // --- Cây ven bờ sông ---
        drawRiverBankTrees(gc, RIVER_Y, RIVER_W, RIVER_FROM, RIVER_TO);

        // --- Nhãn sông ---
        gc.setFill(Color.web("#1a6ea8"));
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        gc.fillText("Sông Hồng", 270, RIVER_Y + 5);
    }

    /** Vẽ thân sông với sóng và gradient */
    private static void drawRiver(GraphicsContext gc, double riverY, double riverW,
                                   double fromX, double toX, long tick) {
        gc.setFill(Color.web("#1565c0", 0.88));
        gc.fillRect(fromX, riverY - riverW / 2, toX - fromX, riverW);

        gc.setFill(Color.web("#0d47a1", 0.6));
        gc.fillRect(fromX, riverY - riverW / 2, toX - fromX, 8);
        gc.fillRect(fromX, riverY + riverW / 2 - 8, toX - fromX, 8);

        double waveOffset = (tick % 120) * 0.5;
        gc.setStroke(Color.web("#64b5f6", 0.55));
        gc.setLineWidth(2);
        gc.setLineDashes(null);
        for (int row = 0; row < 3; row++) {
            double wy = riverY - 10 + row * 11;
            for (double wx = fromX; wx < toX; wx += 60) {
                double ox = (wx + waveOffset) % 60;
                gc.strokeArc(wx - ox, wy - 3, 30, 8, 0, 180, javafx.scene.shape.ArcType.OPEN);
            }
        }

        gc.setFill(Color.web("#90caf9", 0.18));
        gc.fillRect(fromX, riverY - riverW / 2 + 4, toX - fromX, 14);

        gc.setFill(Color.web("#8d6e63", 0.75));
        gc.fillRect(fromX, riverY - riverW / 2 - 8, toX - fromX, 9);
        gc.fillRect(fromX, riverY + riverW / 2,     toX - fromX, 9);

        gc.setFill(Color.web("#66bb6a", 0.65));
        gc.fillRect(fromX, riverY - riverW / 2 - 14, toX - fromX, 7);
        gc.fillRect(fromX, riverY + riverW / 2 +  8, toX - fromX, 7);
    }

    private static void drawBridge(GraphicsContext gc, double bridgeX, double riverY,
                                    double riverW, boolean vertical) {
        double roadW   = Constants.ROAD_WIDTH;
        double deckW   = roadW + 18;
        double deckLen = riverW + 22;

        double dx = bridgeX - deckW / 2;
        double dy = riverY  - deckLen / 2;

        gc.setFill(Color.web("#78909c"));
        gc.fillRoundRect(dx - 6, dy - 6,  deckW + 12, 12, 4, 4);
        gc.fillRoundRect(dx - 6, dy + deckLen - 6, deckW + 12, 12, 4, 4);

        gc.setFill(Color.web("#90a4ae"));
        gc.fillRect(dx, dy, deckW, deckLen);

        gc.setStroke(Color.web("#ffe082")); gc.setLineWidth(3); gc.setLineDashes(10, 8);
        gc.strokeLine(bridgeX, dy + 4, bridgeX, dy + deckLen - 4);
        gc.setLineDashes(null);

        double rail = deckW / 2 - 3;
        int railSections = 5;
        double secH = deckLen / railSections;
        for (int i = 0; i < railSections; i++) {
            Color rc = (i % 2 == 0) ? Color.web("#e53935") : Color.WHITE;
            gc.setFill(rc);
            gc.fillRect(bridgeX - rail - 5, dy + i * secH, 5, secH + 1);
            gc.fillRect(bridgeX + rail,     dy + i * secH, 5, secH + 1);
        }

        gc.setStroke(Color.web("#546e7a")); gc.setLineWidth(2);
        gc.strokeRect(dx, dy, deckW, deckLen);

        gc.setFill(Color.web("#1a237e")); gc.fillRoundRect(bridgeX - 26, riverY - 9, 52, 18, 4, 4);
        gc.setFill(Color.WHITE); gc.setFont(Font.font("Arial", FontWeight.BOLD, 9));
        gc.fillText("CẦU", bridgeX - 11, riverY + 4);
    }

    private static void drawRiverBankTrees(GraphicsContext gc, double riverY, double riverW,
                                            double fromX, double toX) {
        double northBankY = riverY - riverW / 2 - 20;
        double southBankY = riverY + riverW / 2 + 20;
        double[] bridgeXs = {580, 840};
        double avoidR = Constants.ROAD_WIDTH / 2 + 30;

        for (double tx = fromX + 40; tx < toX; tx += 55) {
            boolean nearBridge = false;
            for (double bx : bridgeXs) if (Math.abs(tx - bx) < avoidR) { nearBridge = true; break; }
            if (nearBridge) continue;

            drawRiverTree(gc, tx, northBankY);
            drawRiverTree(gc, tx + 18, southBankY);
        }
    }

    private static void drawRiverTree(GraphicsContext gc, double tx, double ty) {
        double r = 9 + (Math.abs(tx * 13) % 6);
        gc.setFill(Color.rgb(0, 0, 0, 0.2)); gc.fillOval(tx - r + 3, ty + 2, r * 2 - 4, r);
        gc.setStroke(Color.web("#4e342e")); gc.setLineWidth(2.5);
        gc.strokeLine(tx, ty + r * 0.4, tx, ty + r * 1.1);
        gc.setFill(Color.web("#2e7d32")); gc.fillOval(tx - r * 0.9, ty - r * 0.9, r * 1.8, r * 1.8);
        gc.setFill(Color.web("#43a047")); gc.fillOval(tx - r * 0.65, ty - r, r * 1.35, r * 1.35);
        gc.setFill(Color.web("#66bb6a")); gc.fillOval(tx - r * 0.35, ty - r * 0.85, r * 0.75, r * 0.75);
    }
}
