package com.traffic.maps;

import com.traffic.map.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Map 5 — Đại Lộ Cao Tốc (HighwayMap).
 * Không có ngã tư, 2 làn mỗi chiều.
 *
 * (Chiều phải → trái)
 * road3 ← (làn chậm, y=220)
 * road4 ← (làn nhanh, y=260)
 * ---------------------------- (Dải phân cách giữa)
 * road2 → (làn nhanh, y=300)
 * road1 → (làn chậm, y=340)
 * (Chiều trái → phải)
 */
public class HighwayMap implements MapConfig {

    private final List<Lane>         lanes         = new ArrayList<>();
    private final List<Intersection> intersections = new ArrayList<>();

    public HighwayMap() {
        // Đường thẳng, không cần đèn giao thông
        // Làn xe chạy liên tục từ hai đầu bản đồ

        // ── Chiều Trái → Phải ─────────────────────────────────────────────
        // road1 (Làn chậm, y=370)
        Lane road1 = new Lane(50, 370, 950, 370, null);
        road1.addwaypoint(500, 370);
        lanes.add(road1);

        // road2 (Làn nhanh, y=290)
        Lane road2 = new Lane(50, 290, 950, 290, null);
        road2.addwaypoint(500, 290);
        lanes.add(road2);




        // ── Chiều Phải → Trái ─────────────────────────────────────────────
        // road3 (Làn chậm, y=130)
        Lane road3 = new Lane(950, 130, 50, 130, null);
        road3.addwaypoint(500, 130);
        lanes.add(road3);

        // road4 (Làn nhanh, y=210)
        Lane road4 = new Lane(950, 210, 50, 210, null);
        road4.addwaypoint(500, 210);
        lanes.add(road4);


    }

    @Override public String getName() { return "Đại lộ cao tốc"; }

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
