package com.traffic.model.vehicle;

import com.traffic.model.map.RoadEdge;
import javafx.scene.paint.Color;

public class Motorcycle extends Vehicle {
    public Motorcycle(String id, RoadEdge road, int laneIndex) {
        // Motorcycle dimensions: length 18, width 8, color Yellow
        super(id, road, laneIndex, 18, 8, Color.web("#ffe600"));
    }

    @Override
    public double getMaxSpeed() {
        return 3.0;
    }

    @Override
    public double getSafeDistance() {
        return 18.0;
    }

    @Override
    public boolean isPriorityVehicle() {
        return false;
    }

    @Override
    public String getVehicleType() {
        return "motorcycle";
    }
}
