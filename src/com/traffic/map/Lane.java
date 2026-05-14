package com.traffic.map;

import com.traffic.core.Vehicle;
import com.traffic.core.Vector2D;
import java.util.ArrayList;
import java.util.List;

/** Một làn đường: có điểm bắt đầu, kết thúc và đèn kiểm soát */
public class Lane {

    private final Vector2D     start;
    private final Vector2D     end;
    private final TrafficLight light;
    private final List<Vehicle> vehicles = new ArrayList<>();

    public Lane(double startX, double startY,
                double endX,   double endY,
                TrafficLight light) {
        this.start = new Vector2D(startX, startY);
        this.end   = new Vector2D(endX,   endY);
        this.light = light;
    }

    public Vector2D    getStart()  { return start; }
    public Vector2D    getEnd()    { return end;   }
    public TrafficLight getLight() { return light; }

    public void addVehicle(Vehicle v)    { vehicles.add(v);    }
    public void removeVehicle(Vehicle v) { vehicles.remove(v); }
    public List<Vehicle> getVehicles()   { return vehicles;    }
}