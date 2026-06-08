package com.traffic.model.vehicle;

import com.traffic.model.map.RoadEdge;
import javafx.scene.paint.Color;

public class Ambulance extends Vehicle {
    public Ambulance(String id, RoadEdge road, int laneIndex) {
        // Ambulance dimensions: length 28, width 15, color Red
        super(id, road, laneIndex, 28, 15, Color.web("#ff003c"));
    }

    @Override
    public double getMaxSpeed() {
        return 3.5;
    }

    @Override
    public double getSafeDistance() {
        return 30.0;
    }

    @Override
    public boolean isPriorityVehicle() {
        return true;
    }

    @Override
    public String getVehicleType() {
        return "ambulance";
    }
}
