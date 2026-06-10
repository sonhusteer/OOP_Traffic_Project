package com.traffic.model.map;

import java.util.ArrayList;
import java.util.List;

public class CityMap {
    public static final double BK_RAIL_X = 275; // Vị trí X đường sắt trong map Bách Khoa

    private List<IntersectionNode> nodes = new ArrayList<>();
    private List<RoadEdge> roads = new ArrayList<>();

    public CityMap() { loadMap("Ô Cờ (Grid)"); }

    public void loadMap(String mapType) {
        nodes.clear();
        roads.clear();

        if      (mapType.equals("Ô Cờ (Grid)"))   { buildGridNetwork();  }
        else if (mapType.equals("Ngã Tư")  || mapType.equals("Ngã 4")) { buildFourWay(); }
        else if (mapType.equals("Ngã Ba")  || mapType.equals("Ngã 3")) { buildThreeWay(); }
        else if (mapType.equals("Ngã 5"))            { buildFiveWay(); }
        else if (mapType.equals("Hỗn Hợp"))         { buildMixedNetwork(); }
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
    // NGÃ 5 - Giao lộ 5 hướng (bùng binh sao 5 cánh)
    // Các hướng: Bắc, Nam, Đông, Tây, Tây-Bắc (chéo)
    // ============================================================
    private void buildFiveWay() {
        double cx = 640, cy = 400;
        IntersectionNode center = new IntersectionNode("Ngã 5", cx, cy, IntersectionNode.NodeType.FIVE_WAY);
        nodes.add(center);

        // 5 spawn nodes tương ứng 5 hướng
        IntersectionNode nSpawn  = new IntersectionNode("N",  cx,        cy - 900, IntersectionNode.NodeType.THREE_WAY, true);
        IntersectionNode sSpawn  = new IntersectionNode("S",  cx,        cy + 900, IntersectionNode.NodeType.THREE_WAY, true);
        IntersectionNode eSpawn  = new IntersectionNode("E",  cx + 900,  cy,       IntersectionNode.NodeType.THREE_WAY, true);
        IntersectionNode wSpawn  = new IntersectionNode("W",  cx - 900,  cy,       IntersectionNode.NodeType.THREE_WAY, true);
        IntersectionNode nwSpawn = new IntersectionNode("NW", cx - 637,  cy - 637, IntersectionNode.NodeType.THREE_WAY, true);

        roads.add(new RoadEdge(nSpawn,  center, RoadEdge.RoadType.AVENUE));
        roads.add(new RoadEdge(sSpawn,  center, RoadEdge.RoadType.AVENUE));
        roads.add(new RoadEdge(eSpawn,  center, RoadEdge.RoadType.AVENUE));
        roads.add(new RoadEdge(wSpawn,  center, RoadEdge.RoadType.AVENUE));
        roads.add(new RoadEdge(nwSpawn, center, RoadEdge.RoadType.AVENUE));
    }

    // ============================================================
    // HỘN HỢP - Mạng lưới có ngã 3, ngã 4 và ngã 5 kết hợp
    //  Sơ đồ:
    //    [SPAWN-N]  [SPAWN-N]
    //       |           |
    //  [NW-SPAWN]-[ngã 5 trung tâm]-[ngã 4 phải]---[SPAWN-E]
    //                   |                  |
    //              [ngã 3 dưới]      [ngã 4 dưới]
    //               |       |               |
    //          [SPAWN-SW] [SPAWN-SE]    [SPAWN-SE2]
    // ============================================================
    private void buildMixedNetwork() {
        double cx = 580, cy = 400;
        double sp = 260;

        // === NGÃ 5 (trung tâm) ===
        IntersectionNode n5 = new IntersectionNode("N5-Center", cx, cy, IntersectionNode.NodeType.FIVE_WAY);
        nodes.add(n5);

        // === NGÃ 4 (bên phải) ===
        IntersectionNode n4R = new IntersectionNode("N4-Right", cx + sp, cy, IntersectionNode.NodeType.FOUR_WAY);
        nodes.add(n4R);

        // === NGÃ 4 (phía dưới phải) ===
        IntersectionNode n4B = new IntersectionNode("N4-Bottom", cx + sp, cy + sp, IntersectionNode.NodeType.FOUR_WAY);
        nodes.add(n4B);

        // === NGÃ 3 (phía dưới trung tâm) ===
        IntersectionNode n3 = new IntersectionNode("N3-Bottom", cx, cy + sp, IntersectionNode.NodeType.THREE_WAY);
        nodes.add(n3);

        // Kết nối chính
        addBidirectional(n5,  n4R);  // ngã 5 <-> ngã 4 phải
        addBidirectional(n5,  n3);   // ngã 5 <-> ngã 3 dưới
        addBidirectional(n4R, n4B);  // ngã 4 phải <-> ngã 4 dưới
        addBidirectional(n3,  n4B);  // ngã 3 <-> ngã 4 dưới

        // Spawn nodes cho Ngã 5
        addSpawnRoad(cx,       cy - 700, n5);   // Bắc
        addSpawnRoad(cx - 700, cy,       n5);   // Tây
        addSpawnRoad(cx - 495, cy - 495, n5);   // Tây-Bắc (chéo, hướng NW)

        // Spawn nodes cho Ngã 4 phải
        addSpawnRoad(cx + sp,       cy - 700, n4R);  // Bắc
        addSpawnRoad(cx + sp + 700, cy,       n4R);  // Đông

        // Spawn nodes cho Ngã 4 dưới
        addSpawnRoad(cx + sp + 700, cy + sp,  n4B);  // Đông
        addSpawnRoad(cx + sp,       cy + sp + 700, n4B); // Nam

        // Spawn nodes cho Ngã 3
        addSpawnRoad(cx - 700, cy + sp,       n3);   // Tây
        addSpawnRoad(cx,       cy + sp + 700, n3);   // Nam
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
