package com.traffic.map;

import com.traffic.core.Vector2D;

/**
 * Lop truong tuong cho den giao thong.
 * tick(deltaTime) dung thoi gian that, khong dem theo frame.
 */
public abstract class TrafficLight {

    public enum State { RED, YELLOW, GREEN }

    protected State state;
    protected double timeLeft;
    protected final double greenTime;
    protected final double yellowTime = 3.0;
    protected final double redTime;
    protected Vector2D position;

    // Khi manualMode = true, den khong tu dong chuyen mau.
    private boolean manualMode = false;

    public TrafficLight(double greenTime, double redTime, double x, double y) {
        this.greenTime = greenTime;
        this.redTime   = redTime;
        this.state     = State.RED;
        this.timeLeft  = redTime;
        this.position  = new Vector2D(x, y);
    }

    public void setInitialState(State initialState, int initialTimeLeft) {
        this.state = initialState;
        this.timeLeft = Math.max(0.0, initialTimeLeft);
    }

    /**
     * Fix lech 1 giay:
     * - Truoc day den dem 10 -> 9 -> ... -> 0 roi frame sau moi doi mau.
     * - Bay gio timeLeft tru truc tiep theo deltaTime, <= 0 thi doi mau ngay.
     */
    public final void tick(double deltaTime) {
        if (manualMode) return;
        if (deltaTime <= 0) return;

        timeLeft -= deltaTime;

        // while de xu ly ca truong hop lag frame lon hon 1 phase.
        while (timeLeft <= 0.0) {
            double overflow = -timeLeft;
            switchState();
            timeLeft -= overflow;
        }
    }

    private void switchState() {
        state = switch (state) {
            case GREEN  -> { timeLeft = yellowTime; yield State.YELLOW; }
            case YELLOW -> { timeLeft = redTime;    yield State.RED;    }
            case RED    -> { timeLeft = greenTime;  yield State.GREEN;  }
        };
    }

    public void setManualMode(boolean manual) {
        this.manualMode = manual;
    }

    public boolean isManualMode() {
        return manualMode;
    }

    /** Doi mau thu cong: RED -> GREEN -> YELLOW -> RED. */
    public void manualSwitch() {
        if (!manualMode) return;
        state = switch (state) {
            case RED    -> { timeLeft = greenTime;  yield State.GREEN;  }
            case GREEN  -> { timeLeft = yellowTime; yield State.YELLOW; }
            case YELLOW -> { timeLeft = redTime;    yield State.RED;    }
        };
    }

    public boolean isRed()    { return state == State.RED;    }
    public boolean isYellow() { return state == State.YELLOW; }
    public boolean isGreen()  { return state == State.GREEN;  }

    public State getState() { return state; }
    public double getTimeLeft() { return Math.max(0.0, timeLeft); }
    public Vector2D getPosition() { return position; }
    public String getColor() { return state.name(); }

    /** So giay hien thi an toan, khong am va khong hien 0 truoc khi doi mau. */
    protected int getDisplayTimeSeconds() {
        return (int) Math.ceil(Math.max(0.0, timeLeft));
    }

    public abstract String getDisplay();
}
