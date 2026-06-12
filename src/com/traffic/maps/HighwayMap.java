package com.traffic.maps;

import com.traffic.map.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Map 5 — Đại Lộ Cao Tốc (HighwayMap).
 * Không có ngã tư, 2 làn mỗi chiều.
 *
 * (Chiều phải → trái)
 * road3 ← (làn chậm, y=220)
 * road4 ← (làn nhanh, y=260)
 * ---------------------------- (Dải phân cách giữa)
 * road2 → (làn nhanh, y=300)
 * road1 → (làn chậm, y=340)
 * (Chiều trái → phải)
 */
public class HighwayMap implements MapConfig {

    private final List<Lane>         lanes         = new ArrayList<>();
    private final List<Intersection> intersections = new ArrayList<>();

    public HighwayMap() {
        // Đường thẳng, không cần đèn giao thông
        // Làn xe chạy liên tục từ hai đầu bản đồ

        // ── Chiều Trái → Phải ─────────────────────────────────────────────
        // road1 (Làn chậm, y=370)
        Lane road1 = new Lane(50, 370, 950, 370, null);
        road1.addwaypoint(500, 370);
        lanes.add(road1);

        // road2 (Làn nhanh, y=290)
        Lane road2 = new Lane(50, 290, 950, 290, null);
        road2.addwaypoint(500, 290);
        lanes.add(road2);

        // Thiết lập láng giềng chiều Trái → Phải
        // Chỉ giữ neighbor để tham chiếu/hỗ trợ UI.
        // Không cho xe bay sang hẳn lane khác; vượt/né vẫn nằm trong cùng Lane.
        road1.setLeftAdjacentLane(road2);
        road2.setRightAdjacentLane(road1);
        road1.setFormalLaneChangeAllowed(false);
        road2.setFormalLaneChangeAllowed(false);


        // ── Chiều Phải → Trái ─────────────────────────────────────────────
        // road3 (Làn chậm, y=130)
        Lane road3 = new Lane(950, 130, 50, 130, null);
        road3.addwaypoint(500, 130);
        lanes.add(road3);

        // road4 (Làn nhanh, y=210)
        Lane road4 = new Lane(950, 210, 50, 210, null);
        road4.addwaypoint(500, 210);
        lanes.add(road4);

        // Thiết lập láng giềng chiều Phải → Trái
        road3.setLeftAdjacentLane(road4);  // Metadata: lane nhanh cùng chiều ở bên trái.
        road4.setRightAdjacentLane(road3); // Metadata: lane chậm cùng chiều ở bên phải.

        // Highway vẫn có 2 lane cùng chiều, nhưng xe chỉ vượt/né trong chính Lane của nó.
        // Việc chuyển hẳn sang lane khác nhìn không tự nhiên với mô hình lane rộng 2 slot.
        for (Lane lane : lanes) {
            lane.setAllowFormalLaneChange(false);
            lane.setInLaneOvertakeAllowed(true);
        }
        road3.setFormalLaneChangeAllowed(false);
        road4.setFormalLaneChangeAllowed(false);
    }

    @Override public String getName() { return "Đại Lộ Cao Tốc"; }

    @Override public List<Lane> getLanes() { return lanes; }

    @Override public List<Intersection> getIntersections() { return intersections; }

    @Override
    public String[] getLaneNames() {
        return new String[]{
            "road1 → (Chậm)",
            "road2 → (Nhanh)",
            "road3 ← (Chậm)",
            "road4 ← (Nhanh)"
        };
    }
}
