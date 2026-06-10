package com.traffic.model.vehicle;

import com.traffic.model.map.RoadEdge;
import javafx.scene.paint.Color;

public class Bicycle extends Vehicle {
    public Bicycle(String id, RoadEdge road, int laneIndex) {
        // Bicycle dimensions: length 14, width 6, color LimeGreen
        super(id, road, laneIndex, 14, 6, Color.web("#00ff66"));
    }

    @Override
    public double getMaxSpeed() {
        return 1.2;
    }

    @Override
    public double getSafeDistance() {
        return 12.0;
    }

    @Override
    public boolean isPriorityVehicle() {
        return false;
    }

    @Override
    public String getVehicleType() {
        return "bicycle";
    }
}
