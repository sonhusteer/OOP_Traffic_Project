package com.traffic.core;

import com.traffic.map.TrafficLight;

// Giao dien quy dinh hanh vi lai xe
public interface IDriver {
    // Ra quyet dinh dua tren den giao thong do bson phat trien.
    void makeDecision(Vehicle vehicle, TrafficLight nextLight);

    // New simulation-time aware API. Existing drivers remain compatible via the
    // legacy overload, while modern drivers can use deltaTime for behavior timers.
    default void makeDecision(Vehicle vehicle, TrafficLight nextLight, double deltaTime) {
        makeDecision(vehicle, nextLight);
    }
}