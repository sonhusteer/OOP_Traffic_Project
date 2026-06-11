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
        // ── Đèn giao thông ───────────────────────────────────────────────
        TrafficLight lightH1 = new CountdownLight(10, 13, 355, 255);
        TrafficLight lightH2 = new NoCountdownLight(10, 13, 445, 325);
        lightH1.setInitialState(TrafficLight.State.GREEN, 10);
        lightH2.setInitialState(TrafficLight.State.GREEN, 10);

        TrafficLight lightV1 = new SmartTrafficLight(10, 13, 425, 255);
        TrafficLight lightV2 = new Last10SecondsLight(10, 13, 355, 325);
        lightV1.setInitialState(TrafficLight.State.RED, 13);
        lightV2.setInitialState(TrafficLight.State.RED, 13);

        // ── Làn đường (đường rộng 80px, cách nhau 80px) ────────────────
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

        // Thiết lập láng giềng (để lấn làn / nhường đường)
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
