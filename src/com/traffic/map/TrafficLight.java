package com.traffic.map;

import com.traffic.core.Vector2D;

/**
 * Lớp trừu tượng cho đèn giao thông.
 *
 * Tính ĐA HÌNH: 3 lớp con override getDisplay() theo cách khác nhau:
 *   CountdownLight     → luôn hiện số giây
 *   NoCountdownLight   → không hiện số giây
 *   Last10SecondsLight → chỉ hiện khi ≤ 10 giây
 *
 * Logic chuyển trạng thái là CHUNG → đặt ở đây (không lặp lại ở 3 lớp con).
 */
public abstract class TrafficLight {

    public enum State { RED, YELLOW, GREEN }

    protected State      state;
    protected int        timeLeft;   // giây còn lại của pha hiện tại
    protected final int  greenTime;
    protected final int  yellowTime  = 3;
    protected final int  redTime;
    protected Vector2D   position;

    public TrafficLight(int greenTime, int redTime, double x, double y) {
        this.greenTime = greenTime;
        this.redTime   = redTime;
        this.state     = State.RED;
        this.timeLeft  = redTime;
        this.position  = new Vector2D(x, y);
    }

    // ── Logic chung — KHÔNG override ──────────────────────────────────────

    /** Đếm ngược 1 giây, tự chuyển pha khi hết giờ */
    public final void tick() {
        if (timeLeft > 0) {
            timeLeft--;
        } else {
            switchState();
        }
    }

    private void switchState() {
        state = switch (state) {
            case GREEN  -> { timeLeft = yellowTime; yield State.YELLOW; }
            case YELLOW -> { timeLeft = redTime;    yield State.RED;    }
            case RED    -> { timeLeft = greenTime;  yield State.GREEN;  }
        };
    }

    // ── Kiểm tra trạng thái ───────────────────────────────────────────────

    public boolean isRed()    { return state == State.RED;    }
    public boolean isYellow() { return state == State.YELLOW; }
    public boolean isGreen()  { return state == State.GREEN;  }

    // ── Getters ───────────────────────────────────────────────────────────

    public State     getState()    { return state;    }
    public int       getTimeLeft() { return timeLeft; }
    public Vector2D  getPosition() { return position; }

    /**
     * Mỗi loại đèn hiển thị thông tin khác nhau.
     * Đây là nơi thể hiện tính ĐA HÌNH của TrafficLight.
     * @return chuỗi hiển thị (vd: "30", "", "8")
     */
    public abstract String getDisplay();
}
