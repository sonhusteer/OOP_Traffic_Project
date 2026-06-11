package com.traffic.maps;

import com.traffic.map.Intersection;
import com.traffic.map.Lane;
import java.util.List;

/**
 * Giao diện chung cho mọi bản đồ mô phỏng.
 *
 * Nguyên tắc OCP: thêm map mới chỉ cần tạo class mới implement
 * interface này — không sửa MainApp hay bất kỳ class nào khác.
 */
public interface MapConfig {

    /** Tên hiển thị trên dropdown chọn map */
    String getName();

    /** Danh sách tất cả làn đường của map */
    List<Lane> getLanes();

    /** Danh sách tất cả ngã rẽ của map */
    List<Intersection> getIntersections();

    /** Tên các làn đường hiển thị trên spawn panel */
    String[] getLaneNames();
}
