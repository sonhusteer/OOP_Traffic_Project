package com.traffic.maps;

import com.traffic.map.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Map 4 - Mang luoi 2 nga tu lien ket.
 *
 * Fix chinh trong ban nay:
 * - Khong add dummy lane vao lanes, nen combo spawn khong bi lech index.
 * - road1/road2 dung addTrafficControlPoint() de co 2 den tren cung 1 lane.
 */
public class NetworkMap implements MapConfig {

    private final List<Lane> lanes = new ArrayList<>();
    private final List<Intersection> intersections = new ArrayList<>();

    public NetworkMap() {
        // -----------------------------------------------------------------
        // Nga tu trai - center (250, 300)
        // -----------------------------------------------------------------
        TrafficLight lightLH1 = new CountdownLight(10, 13, 155, 395);
        TrafficLight lightLH2 = new NoCountdownLight(10, 13, 345, 205);
        lightLH1.setInitialState(TrafficLight.State.GREEN, 10);
        lightLH2.setInitialState(TrafficLight.State.GREEN, 10);

        TrafficLight lightLV1 = new CountdownLight(10, 13, 155, 205);
        TrafficLight lightLV2 = new CountdownLight(10, 13, 345, 395);
        lightLV1.setInitialState(TrafficLight.State.RED, 13);
        lightLV2.setInitialState(TrafficLight.State.RED, 13);

        // -----------------------------------------------------------------
        // Nga tu phai - center (550, 300)
        // -----------------------------------------------------------------
        TrafficLight lightRH1 = new SmartTrafficLight(10, 13, 455, 395);
        TrafficLight lightRH2 = new Last10SecondsLight(10, 13, 645, 205);
        lightRH1.setInitialState(TrafficLight.State.GREEN, 10);
        lightRH2.setInitialState(TrafficLight.State.GREEN, 10);

        TrafficLight lightRV1 = new CountdownLight(10, 13, 455, 205);
        TrafficLight lightRV2 = new CountdownLight(10, 13, 645, 395);
        lightRV1.setInitialState(TrafficLight.State.RED, 13);
        lightRV2.setInitialState(TrafficLight.State.RED, 13);

        // -----------------------------------------------------------------
        // Lane ngang xuyen suot.
        // -----------------------------------------------------------------
        Lane road1 = new Lane(30, 340, 770, 340, lightLH1);
        road1.addTrafficControlPoint(210, 340, lightLH1); // Den nga tu trai
        road1.addTrafficControlPoint(510, 340, lightRH1); // Den nga tu phai
        lanes.add(road1);

        Lane road2 = new Lane(770, 260, 30, 260, lightRH2);
        road2.addTrafficControlPoint(590, 260, lightRH2); // Den nga tu phai
        road2.addTrafficControlPoint(290, 260, lightLH2); // Den nga tu trai
        lanes.add(road2);

        // -----------------------------------------------------------------
        // Lane doc nga tu trai.
        // -----------------------------------------------------------------
        Lane road5 = new Lane(210, 50, 210, 550, lightLV1);
        road5.addwaypoint(210, 220);
        lanes.add(road5);

        Lane road6 = new Lane(290, 550, 290, 50, lightLV2);
        road6.addwaypoint(290, 380);
        lanes.add(road6);

        // -----------------------------------------------------------------
        // Lane doc nga tu phai.
        // -----------------------------------------------------------------
        Lane road7 = new Lane(510, 50, 510, 550, lightRV1);
        road7.addwaypoint(510, 220);
        lanes.add(road7);

        Lane road8 = new Lane(590, 550, 590, 50, lightRV2);
        road8.addwaypoint(590, 380);
        lanes.add(road8);

        // Lane neighbor giu theo logic cu cua project.
        road1.setLeftNeighbor(road2);
        road2.setLeftNeighbor(road1);
        road5.setLeftNeighbor(road6);
        road6.setLeftNeighbor(road5);
        road7.setLeftNeighbor(road8);
        road8.setLeftNeighbor(road7);

        // -----------------------------------------------------------------
        // Intersection.
        // Dummy lane da bi loai bo khoi lanes va khong can nua, vi lane co
        // the chua nhieu TrafficControlPoint.
        // -----------------------------------------------------------------
        Intersection ngaTuTrai = new Intersection(Intersection.Type.CROSSROADS, 250, 300);
        ngaTuTrai.addLane(road1);
        ngaTuTrai.addLane(road2);
        ngaTuTrai.addLane(road5);
        ngaTuTrai.addLane(road6);

        Intersection ngaTuPhai = new Intersection(Intersection.Type.CROSSROADS, 550, 300);
        ngaTuPhai.addLane(road1);
        ngaTuPhai.addLane(road2);
        ngaTuPhai.addLane(road7);
        ngaTuPhai.addLane(road8);

        intersections.add(ngaTuTrai);
        intersections.add(ngaTuPhai);
    }

    @Override public String getName() { return "Mang luoi 2 Nga Tu"; }

    @Override public List<Lane> getLanes() { return lanes; }

    @Override public List<Intersection> getIntersections() { return intersections; }

    @Override
    public String[] getLaneNames() {
        return new String[]{
            "road1 -> (ngang)",
            "road2 <- (ngang)",
            "road5 v (doc trai)",
            "road6 ^ (doc trai)",
            "road7 v (doc phai)",
            "road8 ^ (doc phai)"
        };
    }
}
