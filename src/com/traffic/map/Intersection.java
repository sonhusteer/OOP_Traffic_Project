package com.traffic.map;

import java.util.List;
import java.util.ArrayList;

public class Intersection {
    // Một ngã tư sẽ quản lý nhiều làn đường đi vào và đi ra
    private List<Lane> incomingLanes;
    private List<Lane> outgoingLanes;
    private TrafficLight trafficLight; // Đèn giao thông tại ngã tư này

    public Intersection(TrafficLight light) {
        this.incomingLanes = new ArrayList<>();
        this.outgoingLanes = new ArrayList<>();
        this.trafficLight = light;
    }

    public void addIncomingLane(Lane lane) { incomingLanes.add(lane); }
    public void addOutgoingLane(Lane lane) { outgoingLanes.add(lane); }
}