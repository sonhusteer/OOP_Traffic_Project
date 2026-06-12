package com.traffic.drivers;

public class EmergencyDriver extends AbstractBaseDriver {

    @Override protected double getStopLineGap() { return 5.0;  }
    @Override protected double getStopCarGap()  { return 5.0;  } // đủ chỗ cho xe phía trước
    @Override protected double getBrakeGap()    { return 60.0; } // bắt đầu phanh sớm hơn
    @Override protected double getSafeGap()     { return 45.0; } // giữ khoảng cách với xe đang rẽ
    @Override protected double getOvertakeGap() { return 60.0; }
    @Override protected double getBaseMaxSpeed() { return 160.0; }
    @Override protected double getMinSpeed()    { return 0.0;   }
    @Override protected boolean canOvertake()   { return false; }
    @Override protected boolean obeyTrafficLight(){ return false; } // Không tuân thủ đèn đỏ

}
