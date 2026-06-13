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
 */
public class FiveWayMap implements MapConfig {

    private final List<Lane> lanes = new ArrayList<>();
    private final List<Intersection> intersections = new ArrayList<>();

    public FiveWayMap() {
        Intersection ngaNam = new Intersection(Intersection.Type.FIVE_WAY, 400, 300);

        double CX = 400;
        double CY = 300;
        double R = 85.0; // Roundabout radius
        double stopDist = 115.0; // Đẩy ra xa (115) để các vạch dừng không đè lên nhau
        double lightDist = 125.0; // Đèn giao thông đặt ngoài vạch dừng
        double L = 500.0; // Distance to lane start/end

        Lane[] inLanes = new Lane[5];
        Lane[] outLanes = new Lane[5];

        double[] angles = {
            -Math.PI / 2,                          // 0: -90 deg (North)
            -Math.PI / 2 + 2 * Math.PI / 5,        // 1: -18 deg
            -Math.PI / 2 + 4 * Math.PI / 5,        // 2: 54 deg
            -Math.PI / 2 + 6 * Math.PI / 5,        // 3: 126 deg
            -Math.PI / 2 + 8 * Math.PI / 5         // 4: 198 deg
        };

        for (int i = 0; i < 5; i++) {
            double a = angles[i];
            double vx = Math.cos(a);
            double vy = Math.sin(a);

            // Incoming Lane (hướng vào tâm)
            // Lùi về bên phải (Right-hand traffic): offset = (40 * vy, -40 * vx)
            double oxIn = 40 * vy;
            double oyIn = -40 * vx;
            
            // Outgoing Lane (hướng ra khỏi tâm)
            // Lùi về bên phải: offset = (-40 * vy, 40 * vx)
            double oxOut = -40 * vy;
            double oyOut = 40 * vx;

            // Đèn giao thông cho làn đi vào (đặt bên phải làn)
            double offset = 80; // Trả lại đèn về đúng lề phải
            double lightX = CX + lightDist * vx + offset * vy;
            double lightY = CY + lightDist * vy - offset * vx;
            
            TrafficLight light;
            if (i == 0 || i == 2) {
                light = new CountdownLight(10, 14, lightX, lightY);
                light.setInitialState(TrafficLight.State.GREEN, 10);
            } else {
                light = new CountdownLight(10, 14, lightX, lightY);
                light.setInitialState(TrafficLight.State.RED, 14);
            }

            // Tạo làn đi vào
            double inStartX = CX + L * vx + oxIn;
            double inStartY = CY + L * vy + oyIn;
            double inStopX = CX + stopDist * vx + oxIn;
            double inStopY = CY + stopDist * vy + oyIn;
            
            Lane inLane = new Lane(inStartX, inStartY, CX + 20 * vx + oxIn, CY + 20 * vy + oyIn, light);
            inLane.addWaypoint(inStopX, inStopY);
            lanes.add(inLane);
            inLanes[i] = inLane;

            // Tạo làn đi ra
            double outStartX = CX + 20 * vx + oxOut;
            double outStartY = CY + 20 * vy + oyOut;
            double outEndX = CX + L * vx + oxOut;
            double outEndY = CY + L * vy + oyOut;
            
            Lane outLane = new Lane(outStartX, outStartY, outEndX, outEndY, null);
            outLane.setSpawnAllowed(false); // Ngăn spawn xe ở tâm vòng xuyến đi ra
            lanes.add(outLane);
            outLanes[i] = outLane;

            // Láng giềng đối diện
            inLane.setOpposingLane(outLane);
            outLane.setOpposingLane(inLane);
            inLane.setLeftNeighbor(outLane);
            outLane.setLeftNeighbor(inLane);

            ngaNam.addLane(inLane);
            ngaNam.addLane(outLane);
        }

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
