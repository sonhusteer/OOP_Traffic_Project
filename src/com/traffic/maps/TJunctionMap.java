package com.traffic.maps;

import com.traffic.map.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Map 2 — Ngã Ba hình chữ T (T-Junction).
 *
 *         road3 ↓  road4 ↑
 *            |      |
 * road1 → ───┼──────┼──── → road1
 * road2 ← ───┼──────┼──── ← road2
 *
 * Center: (400, 300)
 */
public class TJunctionMap implements MapConfig {

    private final List<Lane> lanes = new ArrayList<>();
    private final List<Intersection> intersections = new ArrayList<>();

    public TJunctionMap() {
        TrafficLight lightH1 = new CountdownLight(10, 8, 305, 395);   // road1 →
        TrafficLight lightH2 = new NoCountdownLight(10, 8, 495, 205); // road2 ←
        lightH1.setInitialState(TrafficLight.State.GREEN, 10);
        lightH2.setInitialState(TrafficLight.State.GREEN, 10);

        TrafficLight lightV1 = new CountdownLight(10, 8, 305, 205);   // road3 ↓
        lightV1.setInitialState(TrafficLight.State.RED, 8);

        // ── Làn đường ───────────────────────────────────────────────────
        Lane road1 = new Lane(50, 340, 750, 340, lightH1);
        road1.addWaypoint(320, 340);
        lanes.add(road1);

        Lane road2 = new Lane(750, 260, 50, 260, lightH2);
        road2.addWaypoint(480, 260);
        lanes.add(road2);

        Lane road3 = new Lane(360, -50, 360, 340, lightV1);
        road3.addWaypoint(360, 220);
        lanes.add(road3);

        // road4 là làn đi ra khỏi nhánh dọc, không cho spawn nên không có trong getLaneNames().
        Lane road4 = new Lane(440, 340, 440, -50, null);
        lanes.add(road4);

        road1.setLeftNeighbor(road2);
        road1.setRightNeighbor(road2);
        road2.setLeftNeighbor(road1);
        road2.setRightNeighbor(road1);
        road3.setLeftNeighbor(road4);
        road3.setRightNeighbor(road4);
        road4.setLeftNeighbor(road3);
        road4.setRightNeighbor(road3);

        Intersection ngaBa = new Intersection(Intersection.Type.T_JUNCTION, 400, 300);
        for (Lane lane : lanes) ngaBa.addLane(lane);
        intersections.add(ngaBa);
    }

    @Override public String getName() { return "Ngã Ba"; }
    @Override public List<Lane> getLanes() { return lanes; }

    @Override
    public List<Lane> getSpawnLanes() {
        // road4 là lane đi ra khỏi ngã ba, không spawn từ UI để tránh xe xuất hiện sai chiều.
        return lanes.subList(0, 3);
    }
    @Override public List<Intersection> getIntersections() { return intersections; }

    @Override
    public String[] getLaneNames() {
        return new String[]{ "road1 →", "road2 ←", "road3 ↓" };
    }
}
