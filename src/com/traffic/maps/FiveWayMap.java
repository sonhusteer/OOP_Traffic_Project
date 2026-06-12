package com.traffic.maps;

import com.traffic.map.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Map 3 — Ngã Năm (Five-Way Intersection).
 *
 *            road3 ↓  road4 ↑
 *               |      |
 *  road1 → ─────┼──────┼───── → road1
 *  road2 ← ─────┼──────┼───── ← road2
 *              / |      |
 *            /   |      |
 *          ↙ road5
 *
 * Center: (400, 300)
 * 5 làn: 2 ngang + 2 dọc + 1 chéo (góc 225°, hướng Tây Nam)
 */
public class FiveWayMap implements MapConfig {

    private final List<Lane>         lanes         = new ArrayList<>();
    private final List<Intersection> intersections = new ArrayList<>();

    public FiveWayMap() {
        // ── Đèn giao thông (5 đèn) ──────────────────────────────────────
        TrafficLight lightH1 = new CountdownLight(10, 15, 355, 255);
        TrafficLight lightH2 = new NoCountdownLight(10, 15, 445, 325);
        lightH1.setInitialState(TrafficLight.State.GREEN, 10);
        lightH2.setInitialState(TrafficLight.State.GREEN, 10);

        TrafficLight lightV1 = new SmartTrafficLight(10, 15, 425, 255);
        TrafficLight lightV2 = new Last10SecondsLight(10, 15, 355, 325);
        lightV1.setInitialState(TrafficLight.State.RED, 15);
        lightV2.setInitialState(TrafficLight.State.RED, 15);

        // Đèn cho đường chéo
        TrafficLight lightD1 = new CountdownLight(10, 15, 340, 340);
        lightD1.setInitialState(TrafficLight.State.RED, 15);

        // ── Làn đường (đường rộng 80px) ──────────────────────────────

        Lane road1 = new Lane(50, 260, 750, 260, lightH1);
        road1.addwaypoint(350, 260);
        lanes.add(road1);

        Lane road2 = new Lane(750, 340, 50, 340, lightH2);
        road2.addwaypoint(450, 340);
        lanes.add(road2);

        Lane road3 = new Lane(360, 50, 360, 550, lightV1);
        road3.addwaypoint(360, 250);
        lanes.add(road3);

        Lane road4 = new Lane(440, 550, 440, 50, lightV2);
        road4.addwaypoint(440, 350);
        lanes.add(road4);

        // Chéo: góc Tây-Nam
        Lane road5 = new Lane(80, 550, 350, 350, lightD1);
        road5.addwaypoint(280, 400);
        lanes.add(road5);

        // Thiết lập láng giềng
        // road1.setLeftNeighbor(road2);
        // road2.setLeftNeighbor(road1);
        // road3.setLeftNeighbor(road4);
        // road4.setLeftNeighbor(road3);

        // ── Ngã năm ─────────────────────────────────────────────────────
        Intersection ngaNam = new Intersection(Intersection.Type.FIVE_WAY, 400, 300);
        for (Lane lane : lanes) ngaNam.addLane(lane);
        intersections.add(ngaNam);
    }

    @Override public String getName() { return "Ngã Năm"; }

    @Override public List<Lane> getLanes() { return lanes; }

    @Override public List<Intersection> getIntersections() { return intersections; }

    @Override
    public String[] getLaneNames() {
        return new String[]{ "road1 →", "road2 ←", "road3 ↓", "road4 ↑", "road5 ↙" };
    }
}
