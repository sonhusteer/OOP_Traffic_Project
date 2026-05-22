package com.traffic.core;

public class Motorcycle extends Vehicle {

    public Motorcycle(double x, double y, IDriver driver) {
        super(x, y, 70.0, driver);
        this.name = "Motorcycle";
        this.width = 20;
        this.height = 10;
        this.isPriority = false;
    }

    @Override
    public String getTypeName() {
        return "motorcycle";
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
