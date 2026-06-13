package com.traffic.core;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Tiny opt-in debug logger for traffic state-machine issues.
 * Enable with one of:
 *   -Dtraffic.debug=true
 *   -Dtraffic.debug.yield=true
 *   -Dtraffic.debug.turn=true
 *   -Dtraffic.debug.route=true
 */
public final class TrafficDebug {
    private static final boolean ALL = Boolean.getBoolean("traffic.debug");
    private static final boolean YIELD = ALL || Boolean.getBoolean("traffic.debug.yield");
    private static final boolean TURN = ALL || Boolean.getBoolean("traffic.debug.turn");
    private static final boolean ROUTE = ALL || Boolean.getBoolean("traffic.debug.route");

    private static long tick = 0L;
    private static final Map<Vehicle, Long> lastPreparingStuckLog = new IdentityHashMap<>();
    private static final Map<Vehicle, Long> lastSameRouteLog = new IdentityHashMap<>();

    private TrafficDebug() {}

    public static void beginTick() {
        tick++;
    }

    public static long tick() {
        return tick;
    }

    public static boolean isYieldEnabled() { return YIELD; }
    public static boolean isTurnEnabled() { return TURN; }
    public static boolean isRouteEnabled() { return ROUTE; }

    public static String id(Vehicle v) {
        if (v == null) return "null";
        String name = v.getName();
        if (name == null || name.isBlank()) {
            name = v.getClass().getSimpleName();
        }
        return name + "#" + Integer.toHexString(System.identityHashCode(v));
    }

    public static void log(String tag, String format, Object... args) {
        if (!ALL) return;
        System.out.printf("[%s] tick=%d %s%n", tag, tick, String.format(format, args));
    }

    public static void logYield(String tag, String format, Object... args) {
        if (!YIELD) return;
        System.out.printf("[%s] tick=%d %s%n", tag, tick, String.format(format, args));
    }

    public static void logTurn(String tag, String format, Object... args) {
        if (!TURN) return;
        System.out.printf("[%s] tick=%d %s%n", tag, tick, String.format(format, args));
    }

    public static void logRoute(String tag, String format, Object... args) {
        if (!ROUTE) return;
        System.out.printf("[%s] tick=%d %s%n", tag, tick, String.format(format, args));
    }

    public static void logPreparingStuck(Vehicle v, boolean lateralStable) {
        if (!TURN || v == null) return;
        long last = lastPreparingStuckLog.getOrDefault(v, -9999L);
        if (tick - last < 18L) return;
        lastPreparingStuckLog.put(v, tick);
        System.out.printf("[STUCK] tick=%d id=%s state=%s"
                        + " speed=%.1f lateralOffset=%.1f targetOffset=%.1f preferredOffset=%.1f"
                        + " isLateralStable=%b hasYieldLock=%b yieldMode=%s waitReason=%s%n",
                tick,
                id(v),
                v.getIntersectionManeuverState(),
                v.getSpeed(),
                v.getLateralOffset(),
                v.getTargetLateralOffset(),
                v.getPreferredLateralOffset(),
                lateralStable,
                v.hasActivePriorityYieldLock(),
                v.getYieldMode(),
                v.getTurnWaitReason());
    }

    public static void logSameRoute(PriorityRouteContext ctx) {
        if (!ROUTE || ctx == null || ctx.getPriority() == null || ctx.getNormal() == null) return;
        Vehicle priority = ctx.getPriority();
        Vehicle normal = ctx.getNormal();
        long last = lastSameRouteLog.getOrDefault(normal, -9999L);
        if (tick - last < 20L) return;
        lastSameRouteLog.put(normal, tick);
        System.out.printf("[SAME_ROUTE] tick=%d priority=%s normal=%s relation=%s"
                        + " priorityIx=%s normalIx=%s prioritySpeed=%.1f normalSpeed=%.1f gap=%.1f%n",
                tick,
                id(priority),
                id(normal),
                ctx.getRelation(),
                priority.getIntersectionManeuverState(),
                normal.getIntersectionManeuverState(),
                priority.getSpeed(),
                normal.getSpeed(),
                ctx.getLongitudinalGap());
    }
}
