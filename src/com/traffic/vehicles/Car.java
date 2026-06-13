package com.traffic.vehicles;

import com.traffic.core.IDriver;
import com.traffic.core.Vehicle;

/** Ô tô — xe cá nhân thông thường */
public class Car extends Vehicle {

    public Car(double x, double y, IDriver driver) {
        super(x, y, 80, driver);
        this.name      = "Car";
        this.width     = 34;
        this.height    = 18;
        this.isPriority = false;
    }

    @Override
    public String getTypeName() { return "car"; }
}
