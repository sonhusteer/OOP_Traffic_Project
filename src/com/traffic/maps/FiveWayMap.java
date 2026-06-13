package com.traffic.maps;

import com.traffic.core.Vehicle;
import com.traffic.map.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Map 3 — Ngã Năm / vòng xuyến năm nhánh.
 *
 * Khác với ngã tư thường, các lane ở đây không kết thúc ở tâm giao lộ.
 * Chúng nối vào một vòng xuyến thật ở bán kính lane trung tâm, giúp xe đi
 * tiếp tuyến quanh đảo giữa thay vì cắt xuyên qua tâm.
 */
public class FiveWayMap implements MapConfig {

    private static final double CX = 400.0;
    private static final double CY = 300.0;

    /** Khoảng lệch nửa đường đang dùng chung với renderer/Lane. */
    private static final double ROAD_HALF_OFFSET = 40.0;

    /** Bán kính tâm làn xe chạy trong vòng xuyến. */
    public static final double ROUNDABOUT_LANE_RADIUS = 65.0;

    /** Thành phần radial để điểm nối có bán kính đúng ROUNDABOUT_LANE_RADIUS. */
    private static final double ROUNDABOUT_RADIAL_JOIN = Math.sqrt(
            ROUNDABOUT_LANE_RADIUS * ROUNDABOUT_LANE_RADIUS
                    - ROAD_HALF_OFFSET * ROAD_HALF_OFFSET);

    private static final double STOP_DISTANCE = 115.0;
    private static final double LIGHT_DISTANCE = 125.0;
    private static final double ROAD_LENGTH = 500.0;

    private final List<Lane> lanes = new ArrayList<>();
    private final List<Intersection> intersections = new ArrayList<>();

    public FiveWayMap() {
        Intersection ngaNam = new Intersection(Intersection.Type.FIVE_WAY, CX, CY);

        Lane[] inLanes = new Lane[5];
        Lane[] outLanes = new Lane[5];

        double[] angles = {
            -Math.PI / 2,                          // 0: North
            -Math.PI / 2 + 2 * Math.PI / 5,        // 1
            -Math.PI / 2 + 4 * Math.PI / 5,        // 2
            -Math.PI / 2 + 6 * Math.PI / 5,        // 3
            -Math.PI / 2 + 8 * Math.PI / 5         // 4
        };

        for (int i = 0; i < 5; i++) {
            double a = angles[i];
            double vx = Math.cos(a);
            double vy = Math.sin(a);

            // Right-hand traffic: incoming lane is shifted to the driver's right.
            double oxIn = ROAD_HALF_OFFSET * vy;
            double oyIn = -ROAD_HALF_OFFSET * vx;

            // Outgoing lane is the opposite half of the same road arm.
            double oxOut = -ROAD_HALF_OFFSET * vy;
            double oyOut = ROAD_HALF_OFFSET * vx;

            double lightOffset = 80.0;
            double lightX = CX + LIGHT_DISTANCE * vx + lightOffset * vy;
            double lightY = CY + LIGHT_DISTANCE * vy - lightOffset * vx;

            TrafficLight light = new CountdownLight(10, 14, lightX, lightY);
            if (i == 0 || i == 2) {
                light.setInitialState(TrafficLight.State.GREEN, 10);
            } else {
                light.setInitialState(TrafficLight.State.RED, 14);
            }

            double inStartX = CX + ROAD_LENGTH * vx + oxIn;
            double inStartY = CY + ROAD_LENGTH * vy + oyIn;
            double inStopX = CX + STOP_DISTANCE * vx + oxIn;
            double inStopY = CY + STOP_DISTANCE * vy + oyIn;
            double inEndX = CX + ROUNDABOUT_RADIAL_JOIN * vx + oxIn;
            double inEndY = CY + ROUNDABOUT_RADIAL_JOIN * vy + oyIn;

            Lane inLane = new Lane(inStartX, inStartY, inEndX, inEndY, light);
            inLane.addWaypoint(inStopX, inStopY);
            lanes.add(inLane);
            inLanes[i] = inLane;

            double outStartX = CX + ROUNDABOUT_RADIAL_JOIN * vx + oxOut;
            double outStartY = CY + ROUNDABOUT_RADIAL_JOIN * vy + oyOut;
            double outEndX = CX + ROAD_LENGTH * vx + oxOut;
            double outEndY = CY + ROAD_LENGTH * vy + oyOut;

            Lane outLane = new Lane(outStartX, outStartY, outEndX, outEndY, null);
            outLane.setSpawnAllowed(false); // Không spawn xe ở miệng ra vòng xuyến.
            lanes.add(outLane);
            outLanes[i] = outLane;

            inLane.setOpposingLane(outLane);
            outLane.setOpposingLane(inLane);
            inLane.setLeftNeighbor(outLane);
            outLane.setLeftNeighbor(inLane);

            ngaNam.addLane(inLane);
            ngaNam.addLane(outLane);
        }

        configureRoundaboutTurnTargets(ngaNam, inLanes, outLanes);
        intersections.add(ngaNam);
    }

    /**
     * Ngã 5 không có hướng đối diện tuyệt đối như ngã tư, nên target lane phải
     * được khai báo rõ. Mapping này giữ xe chạy theo vòng xuyến thay vì để
     * TurnCoordinator suy đoán bằng hình học và cắt qua đảo giữa.
     */
    private void configureRoundaboutTurnTargets(Intersection ix, Lane[] inLanes, Lane[] outLanes) {
        for (int i = 0; i < 5; i++) {
            inLanes[i].setTurnTarget(ix, Vehicle.TurnDecision.RIGHT, outLanes[rightExit(i)]);
            inLanes[i].setTurnTarget(ix, Vehicle.TurnDecision.STRAIGHT, outLanes[straightExit(i)]);
            inLanes[i].setTurnTarget(ix, Vehicle.TurnDecision.LEFT, outLanes[leftExit(i)]);
        }
    }

    private int rightExit(int i) { return Math.floorMod(i + 4, 5); }
    private int straightExit(int i) { return Math.floorMod(i + 2, 5); }
    private int leftExit(int i) { return Math.floorMod(i + 1, 5); }

    @Override public String getName() { return "Ngã Năm"; }
    @Override public List<Lane> getLanes() { return lanes; }
    @Override public List<Intersection> getIntersections() { return intersections; }

    @Override
    public String[] getLaneNames() {
        return new String[]{ "road1 →", "road2 ←", "road3 ↓", "road4 ↑", "road5 ↙" };
    }
}
