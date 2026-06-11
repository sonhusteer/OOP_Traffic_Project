package com.traffic.drivers;

public class EmergencyDriver extends AbstractBaseDriver {

    @Override protected double getBrakeDistance() { return 20.0;  }
    @Override protected double getStopDistance()  { return 0.0;   }
    @Override protected double getMaxSpeed()      { return 160.0; }
    @Override protected double getMinSpeed()      { return 0.0;   }
    
    // Khoảng cách an toàn CỰC NHỎ để lách qua mọi khe hở
    @Override protected double getSafeDistance()  { return 15.0;  }
    @Override protected double getOvertakeGap()   { return 20.0;  } // Có thể vượt qua các khoảng trống rất hẹp
    
    @Override protected boolean canOvertake()     { return true;  }
    @Override protected boolean obeyTrafficLight(){ return false; } // Không tuân thủ đèn đỏ

}
