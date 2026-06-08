package com.traffic.model.traffic;

public class TrafficLight {
    public enum Phase { GREEN, YELLOW, RED }
    private Phase phase;

    public TrafficLight(Phase initialPhase) {
        this.phase = initialPhase;
    }

    public void setPhase(Phase p) { this.phase = p; }
    public Phase getPhase() { return phase; }
}