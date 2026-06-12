package com.traffic.drivers;

public class AggressiveDriver extends AbstractBaseDriver {

    @Override protected double getBrakeDistance() { return 40.0;  }
    @Override protected double getStopDistance()  { return 5.0;   }
    @Override protected double getMaxSpeed()      { return 130.0; }
    @Override protected double getMinSpeed()      { return 0.0;   }
    @Override protected double getSafeDistance()  { return 25.0;  }
    @Override protected double getOvertakeGap()   { return 30.0;  } // Dễ dàng vượt hơn
    @Override protected boolean canOvertake()     { return true;  }
    @Override protected boolean canUseMiddleGap() { return true;  }

}