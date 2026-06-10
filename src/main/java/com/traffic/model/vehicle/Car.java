package com.traffic.model.vehicle;

import com.traffic.model.map.RoadEdge;
import javafx.scene.paint.Color;

public class Car extends Vehicle {
    public Car(String id, RoadEdge road, int laneIndex) {
        // Car dimensions: length 26, width 14, color Cyan
        super(id, road, laneIndex, 26, 14, Color.web("#00f0ff"));
    }

    @Override
    public double getMaxSpeed() {
        return 2.2;
    }

    @Override
    public double getSafeDistance() {
        return 25.0;
    }

    @Override
    public boolean isPriorityVehicle() {
        return false;
    }

    @Override
    public String getVehicleType() {
        return "car";
    }
}
