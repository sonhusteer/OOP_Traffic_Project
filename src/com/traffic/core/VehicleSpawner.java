package com.traffic.core;

import com.traffic.map.Lane;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

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

    public enum SpawnTurnMode {
        STRAIGHT,
        LEFT,
        RIGHT,
        RANDOM
    }

    private static final double SPAWN_GAP = 58.0;

    private final TrafficEngine engine;
    private final Map<Lane, Double> lastAutoOffsetByLane = new HashMap<>();
    private final Random random = new Random();

    public VehicleSpawner(TrafficEngine engine) {
        this.engine = engine;
    }

    public int spawn(String type, Lane lane, SpawnPosition position,
                     SpawnLateralMode lateralMode, int count) {
        return spawn(type, lane, position, lateralMode, SpawnTurnMode.RANDOM, count);
    }

    public int spawn(String type, Lane lane, SpawnPosition position,
                     SpawnLateralMode lateralMode, SpawnTurnMode turnMode, int count) {
        if (lane == null || !lane.isUsableForSpawn() || count <= 0) return 0;

        int created = 0;
        double baseProgress = baseProgress(lane, position);
        int direction = position == SpawnPosition.START ? 1 : -1;

        for (int i = 0; i < count; i++) {
            double progress = baseProgress + direction * i * SPAWN_GAP;
            double[] candidates = lateralCandidates(lane, lateralMode);
            boolean spawned = false;

            for (double offset : candidates) {
                if (createIfPossible(type, lane, progress, offset, turnMode)) {
                    created++;
                    spawned = true;
                    break;
                }
            }

            if (!spawned && position == SpawnPosition.START) {
                double fallbackProgress = baseProgress - (i + 1) * SPAWN_GAP;
                for (double offset : candidates) {
                    if (createIfPossible(type, lane, fallbackProgress, offset, turnMode)) {
                        created++;
                        break;
                    }
                }
            }
        }
        return created;
    }

    public boolean canSpawnAt(Lane lane, double progress, double lateralOffset) {
        Vehicle probe = VehicleFactory.create("Car", 0, 0);
        return lane != null && lane.isSpawnSpaceFree(progress, lateralOffset,
                probe.getWidth(), probe.getHeight());
    }

    public void clearState() {
        lastAutoOffsetByLane.clear();
    }

    private boolean createIfPossible(String type, Lane lane, double progress, double offset,
                                     SpawnTurnMode turnMode) {
        Vehicle v = VehicleFactory.create(type, 0, 0);
        if (lane == null || !lane.isSpawnSpaceFree(progress, offset, v.getWidth(), v.getHeight())) {
            return false;
        }
        v.setLanePosition(lane, progress, offset);
        v.setPreferredLateralOffset(offset);
        v.setTargetLateralOffset(offset);
        v.setTurnDecision(toTurnDecision(turnMode));
        engine.addVehicle(v);
        lastAutoOffsetByLane.put(lane, offset);
        return true;
    }

    private Vehicle.TurnDecision toTurnDecision(SpawnTurnMode mode) {
        SpawnTurnMode safe = mode == null ? SpawnTurnMode.RANDOM : mode;
        return switch (safe) {
            case STRAIGHT -> Vehicle.TurnDecision.STRAIGHT;
            case LEFT -> Vehicle.TurnDecision.LEFT;
            case RIGHT -> Vehicle.TurnDecision.RIGHT;
            case RANDOM -> {
                // Debug-friendly distribution: enough cars still go straight, but
                // left/right turns appear frequently so turn logic problems are
                // visible without manual spawning.
                int r = random.nextInt(100);
                if (r < 40) yield Vehicle.TurnDecision.STRAIGHT;
                if (r < 70) yield Vehicle.TurnDecision.LEFT;
                yield Vehicle.TurnDecision.RIGHT;
            }
        };
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
        return new double[] { first, second };
    }
}
