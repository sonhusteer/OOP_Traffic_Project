package com.traffic.maps;

import com.traffic.map.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Map 1 — Ngã Tư cổ điển (Crossroads).
 *
 *         road3 ↓  road4 ↑
 *            |      |
 * road1 → ───┼──────┼──── → road1
 * road2 ← ───┼──────┼──── ← road2
 *            |      |
 *         road3 ↓  road4 ↑
 *
 * Center: (400, 300)
 */
public class CrossroadsMap implements MapConfig {

    private final List<Lane>         lanes         = new ArrayList<>();
    private final List<Intersection> intersections = new ArrayList<>();

    public CrossroadsMap() {
        // ── Đèn giao thông (Đặt ở góc phải của hướng đi) ────────────────
        // Đi Phải (Y=340) -> góc dưới-trái
        TrafficLight lightH1 = new CountdownLight(10, 13, 305, 395);
        // Đi Trái (Y=260) -> góc trên-phải
        TrafficLight lightH2 = new NoCountdownLight(10, 13, 495, 205);
        lightH1.setInitialState(TrafficLight.State.GREEN, 10);
        lightH2.setInitialState(TrafficLight.State.GREEN, 10);

        // Đi Xuống (X=360) -> góc trên-trái
        TrafficLight lightV1 = new SmartTrafficLight(10, 13, 305, 205);
        // Đi Lên (X=440) -> góc dưới-phải
        TrafficLight lightV2 = new Last10SecondsLight(10, 13, 495, 395);
        lightV1.setInitialState(TrafficLight.State.RED, 13);
        lightV2.setInitialState(TrafficLight.State.RED, 13);

        // ── Làn đường (Chuẩn Right-Hand Traffic) ────────────────────────
        // Ngang: Trái → Phải (Nửa dưới)
        Lane road1 = new Lane(50, 340, 750, 340, lightH1);
        road1.addwaypoint(320, 340); // Stop line
        lanes.add(road1);

        // Ngang: Phải → Trái (Nửa trên)
        Lane road2 = new Lane(750, 260, 50, 260, lightH2);
        road2.addwaypoint(480, 260); // Stop line
        lanes.add(road2);

        // Dọc: Trên → Dưới (Nửa trái)
        Lane road3 = new Lane(360, 50, 360, 550, lightV1);
        road3.addwaypoint(360, 220); // Stop line
        lanes.add(road3);

        // Dọc: Dưới → Trên (Nửa phải)
        Lane road4 = new Lane(440, 550, 440, 50, lightV2);
        road4.addwaypoint(440, 380); // Stop line
        lanes.add(road4);

        // Thiết lập láng giềng (để lấn làn / nhường đường)
        road1.setOpposingLane(road2);
        road2.setOpposingLane(road1);
        road3.setOpposingLane(road4);
        road4.setOpposingLane(road3);
        road1.setLeftNeighbor(road2);
        road2.setLeftNeighbor(road1);
        road3.setLeftNeighbor(road4);
        road4.setLeftNeighbor(road3);

        // ── Ngã tư ──────────────────────────────────────────────────────
        Intersection ngaTu = new Intersection(Intersection.Type.CROSSROADS, 400, 300);
        for (Lane lane : lanes) ngaTu.addLane(lane);
        intersections.add(ngaTu);
    }

    @Override public String getName() { return "Ngã Tư"; }

    @Override public List<Lane> getLanes() { return lanes; }

    @Override public List<Intersection> getIntersections() { return intersections; }

    @Override
    public String[] getLaneNames() {
        return new String[]{ "road1 →", "road2 ←", "road3 ↓", "road4 ↑" };
    }
}
