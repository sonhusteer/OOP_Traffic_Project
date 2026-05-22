package com.traffic.core;

public class Ambulance extends Vehicle {

    public Ambulance(double x, double y, IDriver driver) {
        super(x, y, 100.0, driver);
        this.name = "Ambulance";
        this.width = 35;
        this.height = 18;
        this.isPriority = true;
    }

    @Override
    public String getTypeName() {
        return "ambulance";
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
