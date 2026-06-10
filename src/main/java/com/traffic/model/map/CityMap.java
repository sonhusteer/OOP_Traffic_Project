package com.traffic.model.map;

import java.util.ArrayList;
import java.util.List;

public class CityMap {
    private List<IntersectionNode> nodes = new ArrayList<>();
    private List<RoadEdge> roads = new ArrayList<>();

    public CityMap() { loadMap("Ô Cờ (Grid)"); }

    public void loadMap(String mapType) {
        nodes.clear();
        roads.clear();

        if      (mapType.equals("Ô Cờ (Grid)"))   { buildGridNetwork();  }
        else if (mapType.equals("Ngã Tư")  || mapType.equals("Ngã 4")) { buildFourWay(); }
        else if (mapType.equals("Cloverleaf"))     { buildCloverleaf();  }
        else if (mapType.equals("Bách Khoa"))       { buildBachKhoa();    }
        else if (mapType.equals("Ngã Ba")  || mapType.equals("Ngã 3")) { buildThreeWay(); }
        else { buildGridNetwork(); }
        finalizeConnections();
    }

    // ============================================================
    // LƯỚI Ô Cờ (Grid Network) - 3×3 ngã tư đường lưới
    // ============================================================
    private void buildGridNetwork() {
        double cx = 640, cy = 400;
        double sp = 280; // khoảng cách giữa các nút

        // 9 nút giao chính (3×3)
        IntersectionNode[][] grid = new IntersectionNode[3][3];
        IntersectionNode.NodeType[][] types = {
            { IntersectionNode.NodeType.THREE_WAY, IntersectionNode.NodeType.FOUR_WAY, IntersectionNode.NodeType.THREE_WAY },
            { IntersectionNode.NodeType.FOUR_WAY,  IntersectionNode.NodeType.FOUR_WAY, IntersectionNode.NodeType.FOUR_WAY  },
            { IntersectionNode.NodeType.THREE_WAY, IntersectionNode.NodeType.FOUR_WAY, IntersectionNode.NodeType.THREE_WAY }
        };
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                double x = cx + (c - 1) * sp;
                double y = cy + (r - 1) * sp;
                grid[r][c] = new IntersectionNode("G" + r + c, x, y, types[r][c]);
                nodes.add(grid[r][c]);
            }
        }

        // Nối ngang
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 2; c++) {
                addBidirectional(grid[r][c], grid[r][c + 1]);
            }
        }
        // Nối dọc
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 3; c++) {
                addBidirectional(grid[r][c], grid[r + 1][c]);
            }
        }

        // Spawn nodes 4 cạnh
        // Cạnh Bắc
        for (int c = 0; c < 3; c++) addSpawnRoad(cx + (c-1)*sp, cy - sp - 380, grid[0][c]);
        // Cạnh Nam
        for (int c = 0; c < 3; c++) addSpawnRoad(cx + (c-1)*sp, cy + sp + 380, grid[2][c]);
        // Cạnh Tây
        for (int r = 0; r < 3; r++) addSpawnRoad(cx - sp - 380, cy + (r-1)*sp, grid[r][0]);
        // Cạnh Đông
        for (int r = 0; r < 3; r++) addSpawnRoad(cx + sp + 380, cy + (r-1)*sp, grid[r][2]);
    }

    // ---- Helpers ----
    private void addBidirectional(IntersectionNode a, IntersectionNode b) {
        roads.add(new RoadEdge(a, b, RoadEdge.RoadType.AVENUE));
        roads.add(new RoadEdge(b, a, RoadEdge.RoadType.AVENUE));
    }

    private void addSpawnRoad(double spawnX, double spawnY, IntersectionNode to) {
        IntersectionNode spawn = new IntersectionNode("SPAWN", spawnX, spawnY,
                IntersectionNode.NodeType.THREE_WAY, true);
        roads.add(new RoadEdge(spawn, to, RoadEdge.RoadType.AVENUE));
    }

    // ============================================================
    // NGÃ 3 - T-Intersection
    // ============================================================
    private void buildThreeWay() {
        double cx = 640, cy = 450;
        IntersectionNode center = new IntersectionNode("Ngã 3", cx, cy, IntersectionNode.NodeType.THREE_WAY);
        nodes.add(center);
        IntersectionNode wSpawn = new IntersectionNode("W", cx - 900, cy, IntersectionNode.NodeType.THREE_WAY, true);
        IntersectionNode eSpawn = new IntersectionNode("E", cx + 900, cy, IntersectionNode.NodeType.THREE_WAY, true);
        IntersectionNode sSpawn = new IntersectionNode("S", cx, cy + 900, IntersectionNode.NodeType.THREE_WAY, true);
        roads.add(new RoadEdge(wSpawn, center, RoadEdge.RoadType.AVENUE));
        roads.add(new RoadEdge(eSpawn, center, RoadEdge.RoadType.AVENUE));
        roads.add(new RoadEdge(sSpawn, center, RoadEdge.RoadType.AVENUE));
    }

    // ============================================================
    // NGÃ 4 - Giao lộ 4 hướng chuẩn
    // Xe vào từ W, E, N, S
    // ============================================================
    private void buildFourWay() {
        double cx = 640, cy = 400;
        IntersectionNode center = new IntersectionNode("Ngã 4", cx, cy, IntersectionNode.NodeType.FOUR_WAY);
        nodes.add(center);

        IntersectionNode wSpawn = new IntersectionNode("W", cx - 900, cy, IntersectionNode.NodeType.THREE_WAY, true);
        IntersectionNode eSpawn = new IntersectionNode("E", cx + 900, cy, IntersectionNode.NodeType.THREE_WAY, true);
        IntersectionNode nSpawn = new IntersectionNode("N", cx, cy - 900, IntersectionNode.NodeType.THREE_WAY, true);
        IntersectionNode sSpawn = new IntersectionNode("S", cx, cy + 900, IntersectionNode.NodeType.THREE_WAY, true);

        roads.add(new RoadEdge(wSpawn, center, RoadEdge.RoadType.AVENUE));
        roads.add(new RoadEdge(eSpawn, center, RoadEdge.RoadType.AVENUE));
        roads.add(new RoadEdge(nSpawn, center, RoadEdge.RoadType.AVENUE));
        roads.add(new RoadEdge(sSpawn, center, RoadEdge.RoadType.AVENUE));
    }

    // ============================================================
    // CLOVERLEAF - Nút giao cánh bướm (2 cao tốc + 4 vòng lăn)
    // ============================================================
    private void buildCloverleaf() {
        double cx = 640, cy = 400, sp = 230;

        // 4 nút chính nơi raímp rẽ vào/ra
        IntersectionNode wRoad = new IntersectionNode("W-Rd", cx - sp, cy,      IntersectionNode.NodeType.FOUR_WAY);
        IntersectionNode eRoad = new IntersectionNode("E-Rd", cx + sp, cy,      IntersectionNode.NodeType.FOUR_WAY);
        IntersectionNode nRoad = new IntersectionNode("N-Rd", cx,      cy - sp, IntersectionNode.NodeType.FOUR_WAY);
        IntersectionNode sRoad = new IntersectionNode("S-Rd", cx,      cy + sp, IntersectionNode.NodeType.FOUR_WAY);
        nodes.add(wRoad); nodes.add(eRoad); nodes.add(nRoad); nodes.add(sRoad);

        // 4 vòng lăn (cánh bướm - mỗi góc 1 vòng)
        IntersectionNode loopNW = new IntersectionNode("Loop-NW", cx - sp, cy - sp, IntersectionNode.NodeType.THREE_WAY);
        IntersectionNode loopNE = new IntersectionNode("Loop-NE", cx + sp, cy - sp, IntersectionNode.NodeType.THREE_WAY);
        IntersectionNode loopSW = new IntersectionNode("Loop-SW", cx - sp, cy + sp, IntersectionNode.NodeType.THREE_WAY);
        IntersectionNode loopSE = new IntersectionNode("Loop-SE", cx + sp, cy + sp, IntersectionNode.NodeType.THREE_WAY);
        nodes.add(loopNW); nodes.add(loopNE); nodes.add(loopSW); nodes.add(loopSE);

        // Đường cao tốc chính (ngang + dọc)
        addBidirectional(wRoad, eRoad);
        addBidirectional(nRoad, sRoad);

        // Vòng lăn kết nối 2 cao tốc
        addBidirectional(wRoad, loopNW); addBidirectional(loopNW, nRoad);
        addBidirectional(eRoad, loopNE); addBidirectional(loopNE, nRoad);
        addBidirectional(wRoad, loopSW); addBidirectional(loopSW, sRoad);
        addBidirectional(eRoad, loopSE); addBidirectional(loopSE, sRoad);

        // Spawn 4 hướng
        addSpawnRoad(cx - 620, cy,      wRoad);
        addSpawnRoad(cx + 620, cy,      eRoad);
        addSpawnRoad(cx,       cy-620,  nRoad);
        addSpawnRoad(cx,       cy+620,  sRoad);
    }

    // ============================================================
    // BÁCH KHOA - Đường Giải Phóng + Campus ĐHBK Hà Nội
    // ============================================================
    public static final double BK_RAIL_X    = 180; // toạ độ X đường sắt
    public static final double BK_GIAIPHONG_X = 370; // toạ độ X Giải Phóng
    public static final double BK_GATE_X    = 370;  // cổng chính
    public static final double BK_GATE_Y    = 400;  // cổng chính

    private void buildBachKhoa() {
        // --- Đường Giải Phóng (truc Bắc - Nam) ---
        IntersectionNode gpN  = new IntersectionNode("GP-N",  370, 160, IntersectionNode.NodeType.FOUR_WAY);  // ngã 4 phía Bắc
        IntersectionNode gpM  = new IntersectionNode("GP-M",  370, 400, IntersectionNode.NodeType.FOUR_WAY);  // ngã 4 cổng chính BK
        IntersectionNode gpS  = new IntersectionNode("GP-S",  370, 630, IntersectionNode.NodeType.FOUR_WAY);  // ngã 4 phía Nam
        nodes.add(gpN); nodes.add(gpM); nodes.add(gpS);

        // --- Đường nội bộ campus ---
        IntersectionNode bkGate  = new IntersectionNode("BK-Gate",  580, 400, IntersectionNode.NodeType.FOUR_WAY); // sau cổng parabol
        IntersectionNode bkC1    = new IntersectionNode("BK-C1",    700, 310, IntersectionNode.NodeType.THREE_WAY); // khu nhà C1
        IntersectionNode bkB1    = new IntersectionNode("BK-B1",    700, 490, IntersectionNode.NodeType.THREE_WAY); // khu nhà B1
        IntersectionNode bkHo    = new IntersectionNode("BK-Ho",    850, 400, IntersectionNode.NodeType.THREE_WAY); // hồ trung tâm
        IntersectionNode gpNI    = new IntersectionNode("GP-NI",    370, 280, IntersectionNode.NodeType.THREE_WAY); // ngã 3 phía bắc trong
        nodes.add(bkGate); nodes.add(bkC1); nodes.add(bkB1); nodes.add(bkHo); nodes.add(gpNI);

        // Giải Phóng truc chính
        addBidirectional(gpN,  gpNI);
        addBidirectional(gpNI, gpM);
        addBidirectional(gpM,  gpS);

        // Từ Giải Phóng vào campus
        addBidirectional(gpM,  bkGate);
        addBidirectional(gpNI, bkC1);

        // Đường nội bộ campus
        addBidirectional(bkGate, bkC1);
        addBidirectional(bkGate, bkB1);
        addBidirectional(bkC1,   bkHo);
        addBidirectional(bkB1,   bkHo);

        // Spawn nodes
        addSpawnRoad(370,  -80, gpN);   // Bắc Giải Phóng
        addSpawnRoad(370,  900, gpS);   // Nam Giải Phóng
        addSpawnRoad(-80,  160, gpN);   // Tây Bắc (từ đường ray sang)
        addSpawnRoad(-80,  400, gpM);   // Tây giữa
        addSpawnRoad(-80,  630, gpS);   // Tây Nam
        addSpawnRoad(1050, 400, bkHo);  // Đông campus
        addSpawnRoad(1050, 310, bkC1);  // Đông Bắc campus
    }



    /**
     * Scan toàn bộ đường để xác định hướng kết nối thực sự của từng node.
     * Gọi sau khi build map xong để IntersectionNode biết bật đèn đúng hướng.
     */
    private void finalizeConnections() {
        for (IntersectionNode node : nodes) {
            if (node.isSpawnNode()) continue;
            boolean hasN = false, hasS = false, hasE = false, hasW = false, hasNW = false;

            for (RoadEdge road : roads) {
                IntersectionNode nb = null;
                if (road.getStartNode() == node) nb = road.getEndNode();
                else if (road.getEndNode() == node) nb = road.getStartNode();
                if (nb == null) continue;

                double dx = nb.getX() - node.getX();
                double dy = nb.getY() - node.getY();
                if (Math.hypot(dx, dy) < 1) continue;

                double a = Math.toDegrees(Math.atan2(dy, dx));
                if (a < 0) a += 360;

                if      (a >= 337.5 || a <  22.5) hasE  = true;
                else if (a >=  67.5 && a < 112.5) hasS  = true;
                else if (a >= 157.5 && a < 202.5) hasW  = true;
                else if (a >= 202.5 && a < 247.5) { hasNW = true; hasW = true; }
                else if (a >= 247.5 && a < 292.5) hasN  = true;
                else if (a >=  22.5 && a <  67.5) { hasE = true; hasS = true; }
                else if (a >= 112.5 && a < 157.5) { hasS = true; hasW = true; }
                else                               { hasN = true; hasE = true; }
            }
            node.setConnectedDirections(hasN, hasS, hasE, hasW, hasNW);
        }
    }

    public List<IntersectionNode> getNodes() { return nodes; }
    public List<RoadEdge> getRoads() { return roads; }
}
