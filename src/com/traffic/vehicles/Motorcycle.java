package com.traffic.vehicles;

import com.traffic.core.IDriver;
import com.traffic.core.Vehicle;

/** Xe máy — nhỏ hơn, nhanh hơn ô tô */
public class Motorcycle extends Vehicle {

    public Motorcycle(double x, double y, IDriver driver) {
        super(x, y, 100, driver);
        this.name      = "Moto";
        this.width     = 24;
        this.height    = 12;
        this.isPriority = false;
    }

    @Override
    public String getTypeName() { return "motorcycle"; }
}
