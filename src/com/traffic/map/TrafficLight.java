package com.traffic.map;

import java.util.List;

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
   protected int redTime = 3;
    protected int greenTime=3;
    protected int yellowTime = 1;
    protected Vector2D   position;

    public TrafficLight(int greenTime, int redTime, double x, double y) {
        this.greenTime = greenTime;
        this.redTime   = redTime;
        this.state     = State.RED;
        this.timeLeft  = redTime;
        this.position  = new Vector2D(x, y);
    }

    // ── Logic chung — KHÔNG override ──────────────────────────────────────

    public TrafficLight(List<Lane> allLanes) {
        this.greenTime = 0;
        //TODO Auto-generated constructor stub
        this.redTime = 0;
    }

    /** Đếm ngược 1 giây, tự chuyển pha khi hết giờ */
    public void tick() {
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

    public abstract String getDisplay(); // Mỗi loại đèn sẽ có cách hiển thị riêng 
    public int getTimeLeft() { return timeLeft; }
    public String getColor() { return state.name(); }
        public Vector2D getPosition() { return position; }

        public abstract boolean isRed();

        public abstract boolean isYellow();
}
