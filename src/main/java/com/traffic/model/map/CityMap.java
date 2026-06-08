package com.traffic.model.map;

import java.util.ArrayList;
import java.util.List;

public class CityMap {
    private List<IntersectionNode> nodes = new ArrayList<>();
    private List<RoadEdge> roads = new ArrayList<>();

    public CityMap() { loadMap("Mạng lưới"); }

    public void loadMap(String mapType) {
        nodes.clear();
        roads.clear();

        if (mapType.equals("Ngã Ba")   || mapType.equals("Ngã 3"))         { buildThreeWay();    }
        else if (mapType.equals("Ngã Tư")   || mapType.equals("Ngã 4"))  { buildFourWay();     }
        else if (mapType.equals("Ngã Năm")  || mapType.equals("Bùng binh")) { buildRoundabout(); }
        else { buildDefaultCity(); }
        finalizeConnections(); // Scan roads → set connected directions for each node
    }

    // ============================================================
    // NGÃ 3 - T-Intersection: Đông-Tây + một cánh phía Nam
    // Xe vào từ 3 hướng (W, E, S), trung tâm ở giữa canvas
    // ============================================================
    private void buildThreeWay() {
        double cx = 640, cy = 450;
        IntersectionNode center = new IntersectionNode("Ngã 3", cx, cy, IntersectionNode.NodeType.THREE_WAY);
        nodes.add(center);

        // Spawn nodes ngoài rìa màn hình (~800px từ center)
        IntersectionNode wSpawn = new IntersectionNode("W", cx - 900, cy, IntersectionNode.NodeType.THREE_WAY, true);
        IntersectionNode eSpawn = new IntersectionNode("E", cx + 900, cy, IntersectionNode.NodeType.THREE_WAY, true);
        IntersectionNode sSpawn = new IntersectionNode("S", cx, cy + 900, IntersectionNode.NodeType.THREE_WAY, true);

        // Tất cả đường đều đi VÀO center (spawn tại startNode)
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
    // NGÃ 5 - Bùng binh 5 cánh (4 cánh cardinal + 1 cánh Tây-Bắc)
    // Node FIVE_WAY → hiển thị đảo tròn + không có đèn tín hiệu
    // ============================================================
    private void buildRoundabout() {
        double cx = 640, cy = 450;
        IntersectionNode center = new IntersectionNode("Ngã 5", cx, cy, IntersectionNode.NodeType.FIVE_WAY);
        nodes.add(center);

        // 4 cánh cardinal + 1 cánh Tây-Bắc (góc ~45°)
        IntersectionNode wSpawn  = new IntersectionNode("W",  cx - 900, cy,       IntersectionNode.NodeType.THREE_WAY, true);
        IntersectionNode eSpawn  = new IntersectionNode("E",  cx + 900, cy,       IntersectionNode.NodeType.THREE_WAY, true);
        IntersectionNode nSpawn  = new IntersectionNode("N",  cx,       cy - 900, IntersectionNode.NodeType.THREE_WAY, true);
        IntersectionNode sSpawn  = new IntersectionNode("S",  cx,       cy + 900, IntersectionNode.NodeType.THREE_WAY, true);
        IntersectionNode nwSpawn = new IntersectionNode("NW", cx - 650, cy - 650, IntersectionNode.NodeType.THREE_WAY, true);

        roads.add(new RoadEdge(wSpawn,  center, RoadEdge.RoadType.AVENUE));
        roads.add(new RoadEdge(eSpawn,  center, RoadEdge.RoadType.AVENUE));
        roads.add(new RoadEdge(nSpawn,  center, RoadEdge.RoadType.AVENUE));
        roads.add(new RoadEdge(sSpawn,  center, RoadEdge.RoadType.AVENUE));
        roads.add(new RoadEdge(nwSpawn, center, RoadEdge.RoadType.AVENUE));
    }

    // ============================================================
    // MẠNG LƯỚI RỘNG - Lưới 3×3 với bùng binh ở trung tâm
    // 9 ngã tư + 12 spawn node ngoài rìa
    // Khoảng cách giữa ngã tư: 300px
    // ============================================================
    private void buildDefaultCity() {
        double cx = 640, cy = 400;
        double sp = 300; // spacing giữa các ngã tư

        // --- 9 NÚT GIAO CHÍNH (đa dạng loại ngã) ---
        // Góc: Ngã 3 (chỉ có 3 hướng vì là góc lưới)
        IntersectionNode nw = new IntersectionNode("NW", cx-sp, cy-sp, IntersectionNode.NodeType.THREE_WAY);
        IntersectionNode ne = new IntersectionNode("NE", cx+sp, cy-sp, IntersectionNode.NodeType.THREE_WAY);
        IntersectionNode sw = new IntersectionNode("SW", cx-sp, cy+sp, IntersectionNode.NodeType.THREE_WAY);
        IntersectionNode se = new IntersectionNode("SE", cx+sp, cy+sp, IntersectionNode.NodeType.THREE_WAY);
        // Cạnh giữa: Ngã 4 (4 hướng)
        IntersectionNode nc = new IntersectionNode("N",  cx,    cy-sp, IntersectionNode.NodeType.FOUR_WAY);
        IntersectionNode wc = new IntersectionNode("W",  cx-sp, cy,    IntersectionNode.NodeType.FOUR_WAY);
        IntersectionNode ec = new IntersectionNode("E",  cx+sp, cy,    IntersectionNode.NodeType.FOUR_WAY);
        IntersectionNode sc = new IntersectionNode("S",  cx,    cy+sp, IntersectionNode.NodeType.FOUR_WAY);
        // Trung tâm: Ngã 5 – Bùng binh
        IntersectionNode center = new IntersectionNode("C", cx, cy,    IntersectionNode.NodeType.FIVE_WAY);

        // Center node đầu tiên để camera focus vào giữa
        nodes.add(center);
        nodes.add(nw); nodes.add(nc); nodes.add(ne);
        nodes.add(wc); nodes.add(ec);
        nodes.add(sw); nodes.add(sc); nodes.add(se);

        // --- 12 ĐƯỜNG NỘI BỘ (BI-DIRECTIONAL) ---
        // Hàng ngang
        addBidirectional(nw, nc); addBidirectional(nc, ne);
        addBidirectional(wc, center); addBidirectional(center, ec);
        addBidirectional(sw, sc); addBidirectional(sc, se);
        // Hàng dọc
        addBidirectional(nw, wc); addBidirectional(wc, sw);
        addBidirectional(nc, center); addBidirectional(center, sc);
        addBidirectional(ne, ec); addBidirectional(ec, se);

        // --- CÁNH TÂY BẮC CỦA NGÃ 5 TRUNG TÂM ---
        addSpawnRoad(cx - 750, cy - 750, center); // Spawn chéo TâyBắc → ngã 5

        // --- 12 SPAWN NODE NGOÀI RÌA ---
        // Cạnh Bắc (3 spawn)
        addSpawnRoad(cx-sp, cy-sp-400, nw);
        addSpawnRoad(cx,    cy-sp-400, nc);
        addSpawnRoad(cx+sp, cy-sp-400, ne);
        // Cạnh Nam (3 spawn)
        addSpawnRoad(cx-sp, cy+sp+400, sw);
        addSpawnRoad(cx,    cy+sp+400, sc);
        addSpawnRoad(cx+sp, cy+sp+400, se);
        // Cạnh Tây (3 spawn)
        addSpawnRoad(cx-sp-400, cy-sp, nw);
        addSpawnRoad(cx-sp-400, cy,    wc);
        addSpawnRoad(cx-sp-400, cy+sp, sw);
        // Cạnh Đông (3 spawn)
        addSpawnRoad(cx+sp+400, cy-sp, ne);
        addSpawnRoad(cx+sp+400, cy,    ec);
        addSpawnRoad(cx+sp+400, cy+sp, se);
    }

    // ---- Helpers ----

    /** Tạo 2 đường ngược chiều giữa 2 nút */
    private void addBidirectional(IntersectionNode a, IntersectionNode b) {
        roads.add(new RoadEdge(a, b, RoadEdge.RoadType.AVENUE));
        roads.add(new RoadEdge(b, a, RoadEdge.RoadType.AVENUE));
    }

    /** Tạo spawn node off-screen và đường 1 chiều vào grid node */
    private void addSpawnRoad(double spawnX, double spawnY, IntersectionNode to) {
        IntersectionNode spawn = new IntersectionNode("SPAWN", spawnX, spawnY,
                IntersectionNode.NodeType.THREE_WAY, true);
        roads.add(new RoadEdge(spawn, to, RoadEdge.RoadType.AVENUE));
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
