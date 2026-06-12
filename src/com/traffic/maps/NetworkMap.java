package com.traffic.maps;

import com.traffic.map.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Map 4 — Mạng lưới 2 Ngã Tư liên kết (Two-Intersection Network).
 *
 *        road5 ↓ road6 ↑          road7 ↓ road8 ↑
 *           |      |                |      |
 * road1 → ──┼──────┼────────────────┼──────┼───── →
 * road2 ← ──┼──────┼────────────────┼──────┼───── ←
 *           |      |                |      |
 *        road5 ↓ road6 ↑          road7 ↓ road8 ↑
 *
 *       Ngã tư Trái             Ngã tư Phải
 *       center(250, 300)        center(550, 300)
 *
 * Xe trên road1/road2 đi qua CẢ HAI ngã tư liên tiếp.
 */
public class NetworkMap implements MapConfig {

    private final List<Lane>         lanes         = new ArrayList<>();
    private final List<Intersection> intersections = new ArrayList<>();

    public NetworkMap() {
        // ═══════════════════════════════════════════════════════════════════
        //  NGÃ TƯ TRÁI — center (250, 300)
        // ═══════════════════════════════════════════════════════════════════

        TrafficLight lightLH1 = new CountdownLight(10, 13, 155, 395); // Đi phải (Bottom)
        TrafficLight lightLH2 = new NoCountdownLight(10, 13, 345, 205); // Đi trái (Top)
        lightLH1.setInitialState(TrafficLight.State.GREEN, 10);
        lightLH2.setInitialState(TrafficLight.State.GREEN, 10);

        TrafficLight lightLV1 = new CountdownLight(10, 13, 155, 205); // Đi xuống (Left)
        TrafficLight lightLV2 = new CountdownLight(10, 13, 345, 395); // Đi lên (Right)
        lightLV1.setInitialState(TrafficLight.State.RED, 13);
        lightLV2.setInitialState(TrafficLight.State.RED, 13);

        // ═══════════════════════════════════════════════════════════════════
        //  NGÃ TƯ PHẢI — center (550, 300)
        // ═══════════════════════════════════════════════════════════════════

        TrafficLight lightRH1 = new SmartTrafficLight(10, 13, 455, 395); // Đi phải (Bottom)
        TrafficLight lightRH2 = new Last10SecondsLight(10, 13, 645, 205); // Đi trái (Top)
        lightRH1.setInitialState(TrafficLight.State.GREEN, 10);
        lightRH2.setInitialState(TrafficLight.State.GREEN, 10);

        TrafficLight lightRV1 = new CountdownLight(10, 13, 455, 205); // Đi xuống (Left)
        TrafficLight lightRV2 = new CountdownLight(10, 13, 645, 395); // Đi lên (Right)
        lightRV1.setInitialState(TrafficLight.State.RED, 13);
        lightRV2.setInitialState(TrafficLight.State.RED, 13);

        // ═══════════════════════════════════════════════════════════════════
        //  LÀN ĐƯỜNG (Chuẩn Right-Hand Traffic)
        // ═══════════════════════════════════════════════════════════════════

        // ── Đường ngang xuyên suốt ───────────────────────────────────────

        // road1 → Trái → Phải (Nửa dưới Y=340)
        Lane road1 = new Lane(30, 340, 770, 340, lightLH1);
        road1.addwaypoint(210, 340); // Stop line ngã tư trái
        road1.addwaypoint(510, 340); // Stop line ngã tư phải
        lanes.add(road1);

        // Dummy lane để giữ đèn RH1 cho ngã tư phải (vì xe không nhận 2 đèn trên 1 làn)
        Lane dummyRH1 = new Lane(510, 340, 510, 340, lightRH1);
        lanes.add(dummyRH1);

        // road2 ← Phải → Trái (Nửa trên Y=260)
        Lane road2 = new Lane(770, 260, 30, 260, lightRH2);
        road2.addwaypoint(590, 260); // Stop line ngã tư phải
        road2.addwaypoint(290, 260); // Stop line ngã tư trái
        lanes.add(road2);

        // Dummy lane để giữ đèn LH2 cho ngã tư trái
        Lane dummyLH2 = new Lane(290, 260, 290, 260, lightLH2);
        lanes.add(dummyLH2);

        // ── Đường dọc ngã tư TRÁI (cx=250) ─────────────────────────

        Lane road5 = new Lane(210, 50, 210, 550, lightLV1);
        road5.addwaypoint(210, 260 - 40); // Stop line trên (220)
        lanes.add(road5);

        Lane road6 = new Lane(290, 550, 290, 50, lightLV2);
        road6.addwaypoint(290, 340 + 40); // Stop line dưới (380)
        lanes.add(road6);

        // ── Đường dọc ngã tư PHẢI (cx=550) ─────────────────────────

        Lane road7 = new Lane(510, 50, 510, 550, lightRV1);
        road7.addwaypoint(510, 260 - 40); // Stop line trên (220)
        lanes.add(road7);

        Lane road8 = new Lane(590, 550, 590, 50, lightRV2);
        road8.addwaypoint(590, 340 + 40); // Stop line dưới (380)
        lanes.add(road8);

        // Thiết lập láng giềng
        // road1.setLeftNeighbor(road2);
        // road2.setLeftNeighbor(road1);
        // 
        // road5.setLeftNeighbor(road6);
        // road6.setLeftNeighbor(road5);
        // 
        // road7.setLeftNeighbor(road8);
        // road8.setLeftNeighbor(road7);

        // ═══════════════════════════════════════════════════════════════════
        //  NGÃ TƯ (Intersection)
        // ═══════════════════════════════════════════════════════════════════

        Intersection ngaTuTrai = new Intersection(Intersection.Type.CROSSROADS, 250, 300);
        ngaTuTrai.addLane(road1);
        ngaTuTrai.addLane(road2);
        ngaTuTrai.addLane(dummyLH2); // Để đèn LH2 được vẽ và quản lý
        ngaTuTrai.addLane(road5);
        ngaTuTrai.addLane(road6);

        Intersection ngaTuPhai = new Intersection(Intersection.Type.CROSSROADS, 550, 300);
        ngaTuPhai.addLane(road1);
        ngaTuPhai.addLane(dummyRH1); // Để đèn RH1 được vẽ và quản lý
        ngaTuPhai.addLane(road2);
        ngaTuPhai.addLane(road7);
        ngaTuPhai.addLane(road8);

        intersections.add(ngaTuTrai);
        intersections.add(ngaTuPhai);
    }

    @Override public String getName() { return "Mạng lưới 2 Ngã Tư"; }

    @Override public List<Lane> getLanes() { return lanes; }

    @Override public List<Intersection> getIntersections() { return intersections; }

    @Override
    public String[] getLaneNames() {
        return new String[]{
            "road1 → (ngang)",
            "road2 ← (ngang)",
            "road5 ↓ (dọc trái)",
            "road6 ↑ (dọc trái)",
            "road7 ↓ (dọc phải)",
            "road8 ↑ (dọc phải)"
        };
    }
}
