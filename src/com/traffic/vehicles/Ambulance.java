package com.traffic.vehicles;

import com.traffic.core.IDriver;
import com.traffic.core.Vehicle;

/** Xe cứu thương — ưu tiên cao, các xe khác phải nhường */
public class Ambulance extends Vehicle {

    private boolean sirenActive;

    public Ambulance(double x, double y, IDriver driver) {
        super(x, y, 150, driver);
        this.name        = "Ambu";
        this.width       = 22;
        this.height      = 44;
        this.isPriority  = true;   // ← xe ưu tiên
        this.sirenActive = true;
    }

    public boolean isSirenActive()         { return sirenActive; }
    public void    setSirenActive(boolean b) { this.sirenActive = b; }

    @Override
    public String getTypeName() { return "ambulance"; }
}
