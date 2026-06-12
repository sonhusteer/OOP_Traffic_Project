package com.traffic.maps;

import com.traffic.map.Intersection;
import com.traffic.map.Lane;
import java.util.List;

/**
 * Giao dien chung cho moi ban do mo phong.
 */
public interface MapConfig {

    /** Ten hien thi tren dropdown chon map. */
    String getName();

    /** Tat ca lane can ve tren map. */
    List<Lane> getLanes();

    /** Lane cho UI spawn xe. Mac dinh = getLanes(). */
    default List<Lane> getSpawnLanes() {
        return getLanes();
    }

    /** Danh sach nga re. */
    List<Intersection> getIntersections();

    /** Ten cac lane hien thi tren spawn panel. */
    String[] getLaneNames();
}
