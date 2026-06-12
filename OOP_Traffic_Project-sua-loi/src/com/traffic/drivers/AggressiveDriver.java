package com.traffic.drivers;

public class AggressiveDriver extends AbstractBaseDriver {

    @Override protected double getStopLineGap() { return 5.0; }
    @Override protected double getStopCarGap()  { return 5.0; }  // khoảng dừng sát xe
    @Override protected double getBrakeGap()    { return 55.0; }  // khoảng bắt đầu phanh
    @Override protected double getSafeGap()     { return 45.0; }  // khoảng an toàn tối thiểu
    @Override protected double getOvertakeGap() { return 75.0; }
    @Override protected double getBaseMaxSpeed() { return 130.0; }
    @Override protected double getMinSpeed()    { return 0.0;   }
    @Override protected boolean canOvertake()   { return false; }

}