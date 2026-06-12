package com.traffic.core;

import com.traffic.map.Lane;
import java.util.HashMap;
import java.util.Map;

/** Creates vehicles safely and keeps spawn logic out of the JavaFX UI. */
public class VehicleSpawner {

    public enum SpawnPosition {
        START,
        MIDDLE,
        END
    }

    public enum SpawnLateralMode {
        AUTO,
        LEFT,
        CENTER,
        RIGHT
    }

    private static final double SPAWN_GAP = 58.0;
    private static final double FRONT_GAP = 62.0;
    private static final double BACK_GAP = 36.0;
    private static final double LATERAL_GAP = 24.0;

    private final TrafficEngine engine;
    private final Map<Lane, Double> lastAutoOffsetByLane = new HashMap<>();

    public VehicleSpawner(TrafficEngine engine) {
        this.engine = engine;
    }

    public int spawn(String type, Lane lane, SpawnPosition position,
                     SpawnLateralMode lateralMode, int count) {
        if (lane == null || !lane.isUsableForSpawn() || count <= 0) return 0;

        int created = 0;
        double baseProgress = baseProgress(lane, position);
        int direction = position == SpawnPosition.START ? 1 : -1;

        for (int i = 0; i < count; i++) {
            double progress = baseProgress + direction * i * SPAWN_GAP;
            double[] candidates = lateralCandidates(lane, lateralMode);
            boolean spawned = false;

            for (double offset : candidates) {
                if (canSpawnAt(lane, progress, offset)) {
                    create(type, lane, progress, offset);
                    created++;
                    spawned = true;
                    break;
                }
            }

            if (!spawned && position == SpawnPosition.START) {
                double fallbackProgress = baseProgress - (i + 1) * SPAWN_GAP;
                for (double offset : candidates) {
                    if (canSpawnAt(lane, fallbackProgress, offset)) {
                        create(type, lane, fallbackProgress, offset);
                        created++;
                        break;
                    }
                }
            }
        }
        return created;
    }

    public boolean canSpawnAt(Lane lane, double progress, double lateralOffset) {
        return lane != null && lane.isSpaceFree(progress, lateralOffset,
                FRONT_GAP, BACK_GAP, LATERAL_GAP, null);
    }

    public void clearState() {
        lastAutoOffsetByLane.clear();
    }

    private void create(String type, Lane lane, double progress, double offset) {
        Vehicle v = VehicleFactory.create(type, 0, 0);
        v.setLanePosition(lane, progress, offset);
        v.setPreferredLateralOffset(offset);
        v.setTargetLateralOffset(offset);
        engine.addVehicle(v);
        lastAutoOffsetByLane.put(lane, offset);
    }

    private double baseProgress(Lane lane, SpawnPosition position) {
        double length = lane.getLength();
        return switch (position) {
            case START -> 0.0;
            case MIDDLE -> length * 0.50;
            case END -> length * 0.86;
        };
    }

    private double[] lateralCandidates(Lane lane, SpawnLateralMode mode) {
        return switch (mode) {
            case LEFT -> new double[] { Vehicle.LEFT_OFFSET };
            case CENTER -> new double[] { Vehicle.CENTER_OFFSET };
            case RIGHT -> new double[] { Vehicle.RIGHT_OFFSET };
            case AUTO -> autoCandidates(lane);
        };
    }

    private double[] autoCandidates(Lane lane) {
        double last = lastAutoOffsetByLane.getOrDefault(lane, Vehicle.RIGHT_OFFSET);
        double first = last == Vehicle.RIGHT_OFFSET ? Vehicle.LEFT_OFFSET : Vehicle.RIGHT_OFFSET;
        double second = first == Vehicle.LEFT_OFFSET ? Vehicle.RIGHT_OFFSET : Vehicle.LEFT_OFFSET;
        return new double[] { first, second, Vehicle.CENTER_OFFSET };
    }
}
