package com.traffic.drivers;

public class NormalDriver extends AbstractBaseDriver {

    @Override protected double getBrakeDistance() { return 80.0; }
    @Override protected double getStopDistance()  { return 32.0; }
    @Override protected double getMaxSpeed()      { return 80.0; }
    @Override protected double getMinSpeed()      { return 0.0;  }
    @Override protected double getSafeDistance()  { return 45.0; }
    @Override protected double getOvertakeGap()   { return 60.0; } // Cần khoảng trống lớn để vượt an toàn
    @Override protected boolean canOvertake()     { return true; } // Ít khi vượt nhưng vẫn có khả năng

}