package com.traffic.core;

import com.traffic.map.TrafficLight;

// Giao dien quy dinh hanh vi lai xe
public interface IDriver {
    // Ra quyet dinh dua tren den giao thong do bson phat trien
    void makeDecision(Vehicle vehicle, TrafficLight nextLight);
}