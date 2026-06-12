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

    private final List<Lane>         lanes         = new ArrayList<>();
    private final List<Intersection> intersections = new ArrayList<>();

    public TJunctionMap() {
        // ── Đèn giao thông (Góc phải theo chuẩn Right-Hand Traffic) ──────
        TrafficLight lightH1 = new CountdownLight(10, 8, 305, 395); // Đi phải (Bottom)
        TrafficLight lightH2 = new NoCountdownLight(10, 8, 495, 205); // Đi trái (Top)
        lightH1.setInitialState(TrafficLight.State.GREEN, 10);
        lightH2.setInitialState(TrafficLight.State.GREEN, 10);

        TrafficLight lightV1 = new CountdownLight(10, 8, 305, 205); // Đi xuống (Left)
        lightV1.setInitialState(TrafficLight.State.RED, 8);

        // ── Làn đường ──────────────────────────────
        // Ngang: Trái → Phải (Nửa dưới Y=340)
        Lane road1 = new Lane(50, 340, 750, 340, lightH1);
        road1.addwaypoint(320, 340); // Stop line
        lanes.add(road1);

        // Ngang: Phải → Trái (Nửa trên Y=260)
        Lane road2 = new Lane(750, 260, 50, 260, lightH2);
        road2.addwaypoint(480, 260); // Stop line
        lanes.add(road2);

        // Dọc: Trên → Dưới (Nửa trái X=360, Vào ngã tư)
        Lane road3 = new Lane(360, -50, 360, 340, lightV1);
        road3.addwaypoint(360, 220); // Stop line
        lanes.add(road3);

        // Dọc: Dưới → Trên (Nửa phải X=440, Từ ngã tư đi ra)
        Lane road4 = new Lane(440, 340, 440, -50, null);
        // Không add waypoint -> Không hiện stop line trong ngã tư.
        // Đây là lane đi ra khỏi ngã ba, điểm start nằm ngay trong giao lộ,
        // nên không dùng làm lane spawn để tránh xe sinh sai vị trí.
        road4.setSpawnAllowed(false);
        lanes.add(road4);

        // Thiết lập láng giềng
        road1.setLeftNeighbor(road2);
        road2.setLeftNeighbor(road1);
        road3.setLeftNeighbor(road4);
        road4.setLeftNeighbor(road3);

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
