package com.traffic.drivers;

public class NormalDriver extends AbstractBaseDriver {

    @Override protected double getStopLineGap() { return 10.0; }
    @Override protected double getStopCarGap()  { return 10.0; }  // khoảng dừng sát xe
    @Override protected double getBrakeGap()    { return 90.0; }  // khoảng bắt đầu phanh
    @Override protected double getSafeGap()     { return 90.0; }  // khoảng an toàn tối thiểu
    @Override protected double getOvertakeGap() { return 120.0; }
    @Override protected double getBaseMaxSpeed() { return 80.0; }
    @Override protected double getMinSpeed()    { return 0.0;  }
    @Override protected boolean canOvertake()   { return false; }

}