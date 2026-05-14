package com.traffic.core;

// Lop truu tuong cho cac phuong tien
public abstract class Vehicle {
    protected Vector2D position;
    protected double speed;
    protected double angle;
    protected IDriver driver;

    public Vehicle(double x, double y, double speed, IDriver driver) {
        this.position = new Vector2D(x, y);
        this.speed = speed;
        this.angle = 0;
        this.driver = driver;
    }

    // Tinh toan toa do moi theo thoi gian
    public void update(double deltaTime) {
        double radians = Math.toRadians(this.angle);
        double newX = position.getX() + Math.cos(radians) * speed * deltaTime;
        double newY = position.getY() + Math.sin(radians) * speed * deltaTime;
        
        position.setX(newX);
        position.setY(newY);
    }

    // Ham ve xe, tminh se implement chi tiet
    public abstract void draw(Object context);

    public Vector2D getPosition() { return position; }
    
    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }
}