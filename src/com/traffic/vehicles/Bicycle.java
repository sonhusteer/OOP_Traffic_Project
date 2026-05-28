package com.traffic.vehicles;

import com.traffic.core.IDriver;
import com.traffic.core.Vehicle;

/** Xe đạp — chậm nhất, ưu tiên thấp nhất */
public class Bicycle extends Vehicle {

    public Bicycle(double x, double y, IDriver driver) {
        super(x, y, 30, driver);
        this.name      = "Bike";
        this.width     = 10;
        this.height    = 20;
        this.isPriority = false;
    }

    @Override
    public String getTypeName() { return "bicycle"; }
}
