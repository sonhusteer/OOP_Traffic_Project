package com.traffic.core;

public class Bicycle extends Vehicle {

    public Bicycle(double x, double y, IDriver driver) {
        super(x, y, 20.0, driver);
        this.name = "Bicycle";
        this.width = 15;
        this.height = 8;
        this.isPriority = false;
    }

    @Override
    public String getTypeName() {
        return "bicycle";
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
