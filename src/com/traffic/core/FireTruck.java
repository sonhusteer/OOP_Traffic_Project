package com.traffic.core;

public class FireTruck extends Vehicle {

    public FireTruck(double x, double y, IDriver driver) {
        super(x, y, 90.0, driver);
        this.name = "FireTruck";
        this.width = 40;
        this.height = 20;
        this.isPriority = true;
    }

    @Override
    public String getTypeName() {
        return "firetruck";
    }

    @Override
    public void setX(double x) {
        this.position.setX(x);
    }

    @Override
    public void setY(double y) {
        this.position.setY(y);
    }
}
