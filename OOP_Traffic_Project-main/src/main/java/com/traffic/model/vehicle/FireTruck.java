package com.traffic.model.vehicle;

import com.traffic.model.map.RoadEdge;
import javafx.scene.paint.Color;

public class FireTruck extends Vehicle {
    public FireTruck(String id, RoadEdge road, int laneIndex) {
        // FireTruck dimensions: length 35, width 18, color OrangeRed
        super(id, road, laneIndex, 35, 18, Color.web("#ff3b00"));
    }

    @Override
    public double getMaxSpeed() {
        return 3.0;
    }

    @Override
    public double getSafeDistance() {
        return 38.0;
    }

    @Override
    public boolean isPriorityVehicle() {
        return true;
    }

    @Override
    public String getVehicleType() {
        return "firetruck";
    }
}
