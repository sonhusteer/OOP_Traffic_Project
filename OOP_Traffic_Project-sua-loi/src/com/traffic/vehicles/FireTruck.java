package com.traffic.vehicles;

import com.traffic.core.IDriver;
import com.traffic.core.Vehicle;

/** Xe cứu hỏa — ưu tiên cao, to nhất */
public class FireTruck extends Vehicle {

    public FireTruck(double x, double y, IDriver driver) {
        super(x, y, 130, driver);
        this.name       = "Fire";
        this.width      = 56;
        this.height     = 26;
        this.isPriority = true;  // ← xe ưu tiên
    }

    @Override
    public String getTypeName() { return "firetruck"; }
}
