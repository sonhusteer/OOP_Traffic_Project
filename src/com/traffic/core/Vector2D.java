package com.traffic.core;

// Lop quan ly toa do 2D doc lap
public class Vector2D {
    private double x;
    private double y;

    public Vector2D(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public void set(Vector2D other) {
        this.x = other.x;
        this.y = other.y;
    }

    public Vector2D copy() {
        return new Vector2D(x, y);
    }

    public Vector2D add(Vector2D other) {
        return new Vector2D(x + other.x, y + other.y);
    }

    public Vector2D subtract(Vector2D other) {
        return new Vector2D(x - other.x, y - other.y);
    }

    public Vector2D scale(double factor) {
        return new Vector2D(x * factor, y * factor);
    }

    public double dot(Vector2D other) {
        return x * other.x + y * other.y;
    }

    public double length() {
        return Math.sqrt(x * x + y * y);
    }

    public Vector2D normalized() {
        double len = length();
        if (len < 1e-9) return new Vector2D(1, 0);
        return new Vector2D(x / len, y / len);
    }
}
