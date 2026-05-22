package com.traffic.core;

public class Car extends Vehicle {

    public Car(double x, double y, IDriver driver) {
        super(x, y, 60.0, driver);
        this.name = "Car";
        this.width = 30;
        this.height = 15;
        this.isPriority = false;
    }

    @Override
    public String getTypeName() {
        return "car";
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
