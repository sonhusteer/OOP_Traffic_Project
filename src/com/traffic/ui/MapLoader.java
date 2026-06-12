package com.traffic.ui;

import com.traffic.core.TrafficEngine;
import com.traffic.map.Intersection;
import com.traffic.map.RoadNetwork;
import com.traffic.maps.MapConfig;

/**
 * Tiện ích load map vào engine.
 * Tách ra để MainApp/SimulationController không phải xử lý logic đăng ký network.
 */
public class MapLoader {

    /** Xóa dữ liệu cũ và đăng ký map mới vào engine. */
    public static void loadMap(TrafficEngine engine, MapConfig map) {
        engine.clearVehicles();
        engine.clearTrafficLights();
        engine.clearIntersections();
        SoundManager.getInstance().stopAll();
        registerMap(engine, map);
    }

    /** Đăng ký intersections của map vào engine qua RoadNetwork. */
    public static void registerMap(TrafficEngine engine, MapConfig map) {
        RoadNetwork network = new RoadNetwork();
        for (Intersection intersection : map.getIntersections()) {
            network.addIntersection(intersection);
        }
        network.registerTo(engine);
    }
}
