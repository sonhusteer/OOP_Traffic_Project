package com.traffic.maps;

import com.traffic.map.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Map 2 — Ngã Ba hình chữ T (T-Junction).
 *
 *         road3 ↓
 *            |
 * road1 → ───┼────── → road1
 * road2 ← ───┼────── ← road2
 *         (không có đường lên)
 *
 * Center: (400, 300)
 * 3 làn: ngang trái→phải, ngang phải→trái, dọc trên→dưới
 */
public class TJunctionMap implements MapConfig {

    private final List<Lane>         lanes         = new ArrayList<>();
    private final List<Intersection> intersections = new ArrayList<>();

    public TJunctionMap() {
        // ── Đèn giao thông (3 đèn cho 3 làn) ────────────────────────────
        TrafficLight lightH1 = new CountdownLight(10, 8, 355, 255);
        TrafficLight lightH2 = new NoCountdownLight(10, 8, 445, 325);
        lightH1.setInitialState(TrafficLight.State.GREEN, 10);
        lightH2.setInitialState(TrafficLight.State.GREEN, 10);

        TrafficLight lightV1 = new CountdownLight(10, 8, 425, 255);
        lightV1.setInitialState(TrafficLight.State.RED, 8);

        // ── Làn đường (đường rộng 80px) ──────────────────────────────
        // Ngang: trái → phải
        Lane road1 = new Lane(50, 260, 750, 260, lightH1);
        road1.addwaypoint(350, 260);
        lanes.add(road1);

        // Ngang: phải → trái
        Lane road2 = new Lane(750, 340, 50, 340, lightH2);
        road2.addwaypoint(450, 340);
        lanes.add(road2);

        // Dọc: trên → dưới
        Lane road3 = new Lane(400, 50, 400, 550, lightV1);
        road3.addwaypoint(400, 250);
        lanes.add(road3);

        // Thiết lập láng giềng
        // road1.setLeftNeighbor(road2);
        // road2.setLeftNeighbor(road1);

        // ── Ngã ba ──────────────────────────────────────────────────────
        Intersection ngaBa = new Intersection(Intersection.Type.T_JUNCTION, 400, 300);
        for (Lane lane : lanes) ngaBa.addLane(lane);
        intersections.add(ngaBa);
    }

    @Override public String getName() { return "Ngã Ba"; }

    @Override public List<Lane> getLanes() { return lanes; }

    @Override public List<Intersection> getIntersections() { return intersections; }

    @Override
    public String[] getLaneNames() {
        return new String[]{ "road1 →", "road2 ←", "road3 ↓" };
    }
}
