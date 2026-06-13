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

            // Pick and normalize turn intent once per vehicle. This prevents a
            // failed candidate offset from rerolling L/R/S and keeps badges stable.
            Vehicle.TurnDecision turnDecision = normalizeSpawnDecisionForLane(
                    lane, toTurnDecision(turnMode));
            double[] candidates = lateralCandidates(lane, lateralMode, turnDecision);
            boolean spawned = false;

            for (double offset : candidates) {
                if (createIfPossible(type, lane, progress, offset, turnDecision)) {
                    created++;
                    spawned = true;
                    break;
                }
            }

            if (!spawned && position == SpawnPosition.START) {
                double fallbackProgress = baseProgress - (i + 1) * SPAWN_GAP;
                for (double offset : candidates) {
                    if (createIfPossible(type, lane, fallbackProgress, offset, turnDecision)) {
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
                                     Vehicle.TurnDecision turnDecision) {
        Vehicle v = VehicleFactory.create(type, 0, 0);
        if (lane == null || !lane.isSpawnSpaceFree(progress, offset, v.getWidth(), v.getHeight())) {
            return false;
        }
        Vehicle.TurnDecision effectiveDecision = normalizeSpawnDecisionForLane(lane, turnDecision);
        v.setLanePosition(lane, progress, offset);
        v.setPreferredLateralOffset(offset);
        v.setTargetLateralOffset(offset);
        v.setTurnDecision(effectiveDecision);
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

    private double[] lateralCandidates(Lane lane, SpawnLateralMode mode,
                                       Vehicle.TurnDecision turnDecision) {
        return switch (mode) {
            case LEFT -> new double[] { Vehicle.LEFT_OFFSET };
            case CENTER -> new double[] { Vehicle.CENTER_OFFSET };
            case RIGHT -> new double[] { Vehicle.RIGHT_OFFSET };
            case AUTO -> autoCandidates(lane, turnDecision);
        };
    }

    private double[] autoCandidates(Lane lane, Vehicle.TurnDecision turnDecision) {
        if (turnDecision == Vehicle.TurnDecision.LEFT) {
            return new double[] { Vehicle.LEFT_OFFSET };
        }
        if (turnDecision == Vehicle.TurnDecision.RIGHT) {
            return new double[] { Vehicle.RIGHT_OFFSET };
        }
        double last = lastAutoOffsetByLane.getOrDefault(lane, Vehicle.RIGHT_OFFSET);
        double first = last == Vehicle.RIGHT_OFFSET ? Vehicle.LEFT_OFFSET : Vehicle.RIGHT_OFFSET;
        double second = first == Vehicle.LEFT_OFFSET ? Vehicle.RIGHT_OFFSET : Vehicle.LEFT_OFFSET;
        return new double[] { first, second };
    }

    private Vehicle.TurnDecision normalizeSpawnDecisionForLane(
            Lane lane, Vehicle.TurnDecision requested) {
        Vehicle.TurnDecision safe = requested == null
                ? Vehicle.TurnDecision.STRAIGHT
                : requested;
        if (lane == null) return safe;
        if (lane.isStraightOnly()) return Vehicle.TurnDecision.STRAIGHT;
        if (!lane.hasAnyTurnRule()) return safe;
        if (lane.hasAllowedTurnDecision(safe)) return safe;

        boolean canStraight = lane.hasAllowedTurnDecision(Vehicle.TurnDecision.STRAIGHT);
        boolean canLeft = lane.hasAllowedTurnDecision(Vehicle.TurnDecision.LEFT);
        boolean canRight = lane.hasAllowedTurnDecision(Vehicle.TurnDecision.RIGHT);

        if (safe == Vehicle.TurnDecision.LEFT) {
            if (canStraight) return Vehicle.TurnDecision.STRAIGHT;
            if (canRight) return Vehicle.TurnDecision.RIGHT;
        } else if (safe == Vehicle.TurnDecision.RIGHT) {
            if (canStraight) return Vehicle.TurnDecision.STRAIGHT;
            if (canLeft) return Vehicle.TurnDecision.LEFT;
        } else { // STRAIGHT invalid, common on the stem of a T-junction.
            if (canLeft && canRight) return random.nextBoolean()
                    ? Vehicle.TurnDecision.LEFT
                    : Vehicle.TurnDecision.RIGHT;
            if (canLeft) return Vehicle.TurnDecision.LEFT;
            if (canRight) return Vehicle.TurnDecision.RIGHT;
        }

        if (canStraight) return Vehicle.TurnDecision.STRAIGHT;
        if (canLeft) return Vehicle.TurnDecision.LEFT;
        if (canRight) return Vehicle.TurnDecision.RIGHT;
        return Vehicle.TurnDecision.STRAIGHT;
    }
}
