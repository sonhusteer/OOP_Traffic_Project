package com.traffic.ui;

import com.traffic.core.MathUtils;
import com.traffic.core.Vehicle;
import com.traffic.map.Lane;

/**
 * Tang 3: tinh vi tri spawn xe theo progress + trackIndex.
 *
 * Sau Tang 2, Vehicle da co API setLane(lane, progress, trackIndex). Lop nay
 * gom cong thuc spawn vao mot cho de MainApp va SidebarPanel khong bi copy
 * logic nhau.
 *
 * Cach xep xe:
 * - trackIndex = xe nam o vet ngang nao trong cung mot row.
 * - rowIndex   = xe nam o hang doc thu may.
 *
 * Vi du lane.getTrackCount() = 2:
 * - i = 0 -> row 0, track 0
 * - i = 1 -> row 0, track 1
 * - i = 2 -> row 1, track 0
 * - i = 3 -> row 1, track 1
 *
 * Nhu vay moi hang doc co the chua 2 xe nam ngang hang trong cung Lane.
 */
final class SpawnPlanner {

    /**
     * Giu spacing cu 55px de khi spawn toi da 10 xe o dau lane, xe cuoi
     * khong bi day qua xa khoi vung mo phong va bi TrafficEngine remove ngay.
     */
    private static final double ROW_GAP = 55.0;

    private SpawnPlanner() {
        // Utility class, khong can khoi tao object.
    }

    static void place(Vehicle vehicle, Lane lane, int positionIndex, int vehicleIndex) {
        if (vehicle == null || lane == null) {
            return;
        }

        int trackCount = Math.max(1, lane.getTrackCount());
        int safeIndex = Math.max(0, vehicleIndex);
        int trackIndex = safeIndex % trackCount;
        int rowIndex = safeIndex / trackCount;

        double laneLength = lane.getLength();
        double baseProgress = baseProgressFor(positionIndex, laneLength);

        // Cac xe cung row co cung progress nhung khac lateralOffset theo track.
        // Voi "Dau lan", cho phep progress am de row sau nam ngoai dau lane
        // mot chut, tranh tat ca xe bi de tai progress = 0.
        double progress = baseProgress - rowIndex * ROW_GAP;
        if (positionIndex != 0) {
            progress = MathUtils.clamp(progress, 0.0, Math.max(0.0, laneLength - 5.0));
        }

        vehicle.setLane(lane, progress, trackIndex);
    }

    private static double baseProgressFor(int positionIndex, double laneLength) {
        return switch (positionIndex) {
            case 1 -> laneLength * 0.45; // Giua lan.
            case 2 -> laneLength * 0.75; // Cuoi lan.
            default -> 0.0;              // Dau lan.
        };
    }
}
