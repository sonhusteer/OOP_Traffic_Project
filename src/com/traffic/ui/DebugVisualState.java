package com.traffic.ui;

/** High-level visual debug categories. Keep renderers dumb and classification centralized. */
public enum DebugVisualState {
    NORMAL,
    OVERTAKE,
    GAP_FILL,
    TURNING_OR_INTERSECTION,
    ORDINARY_WAIT,
    PRIORITY_QUEUE,
    EMERGENCY_YIELD,
    ERROR
}
