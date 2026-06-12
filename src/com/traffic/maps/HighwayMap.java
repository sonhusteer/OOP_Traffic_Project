package com.traffic.maps;

import com.traffic.map.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Map 5 — Đại Lộ Cao Tốc.
 * Không có ngã tư, 2 làn mỗi chiều.
 *
 * (Chiều phải → trái)
 * road3 ← (làn chậm, y=130)
 * road4 ← (làn nhanh, y=210)
 * ----------------------------
 * road2 → (làn nhanh, y=290)
 * road1 → (làn chậm, y=370)
 * (Chiều trái → phải)
 */
public class HighwayMap implements MapConfig {

    private final List<Lane> lanes = new ArrayList<>();
    private final List<Intersection> intersections = new ArrayList<>();

    public HighwayMap() {
        // ── Chiều Trái → Phải ───────────────────────────────────────────
        Lane road1 = new Lane(50, 370, 950, 370, null); // làn chậm
        road1.addWaypoint(500, 370);
        lanes.add(road1);

        Lane road2 = new Lane(50, 290, 950, 290, null); // làn nhanh
        road2.addWaypoint(500, 290);
        lanes.add(road2);

        road1.setLeftNeighbor(road2);  // vượt sang làn nhanh
        road2.setRightNeighbor(road1); // về lại làn chậm

        // ── Chiều Phải → Trái ───────────────────────────────────────────
        Lane road3 = new Lane(950, 130, 50, 130, null); // làn chậm
        road3.addWaypoint(500, 130);
        lanes.add(road3);

        Lane road4 = new Lane(950, 210, 50, 210, null); // làn nhanh
        road4.addWaypoint(500, 210);
        lanes.add(road4);

        road3.setLeftNeighbor(road4);  // vượt sang làn nhanh
        road4.setRightNeighbor(road3); // về lại làn chậm
    }

    @Override public String getName() { return "Đại Lộ Cao Tốc"; }
    @Override public List<Lane> getLanes() { return lanes; }
    @Override public List<Intersection> getIntersections() { return intersections; }

    @Override
    public String[] getLaneNames() {
        return new String[]{
            "road1 → (Chậm)",
            "road2 → (Nhanh)",
            "road3 ← (Chậm)",
            "road4 ← (Nhanh)"
        };
    }
}
