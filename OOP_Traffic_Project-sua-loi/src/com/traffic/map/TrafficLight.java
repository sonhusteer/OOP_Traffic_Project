package com.traffic.map;

import com.traffic.core.Vector2D;

/**
 * Lớp trừu tượng cho đèn giao thông.
 * tick() dùng time accumulator: tích lũy deltaTime thực tế,
 * chỉ đếm ngược khi đủ 1 giây → đảm bảo đúng thời gian thực.
 */
public abstract class TrafficLight {

    public enum State { RED, YELLOW, GREEN }

    protected State      state;
    protected double        timeLeft;
    protected double        greenTime;
    protected final double  yellowTime = 3.0;
    protected double        redTime;
    protected Vector2D   position;

    // Tích lũy thời gian thực — chỉ đếm ngược khi đủ 1 giây
    private double timeAccumulator = 0.0;

    public TrafficLight(double greenTime, double redTime, double x, double y) {
        this.greenTime = greenTime;
        this.redTime   = redTime;
        this.state     = State.RED;
        this.timeLeft  = redTime;
        this.position  = new Vector2D(x, y);
    }

    // ── Khởi tạo trạng thái ban đầu ──────────────────────────────────────

    public void setInitialState(State initialState, int initialTimeLeft) {
        this.state    = initialState;
        this.timeLeft = initialTimeLeft;
    }

    // ── Logic chung ───────────────────────────────────────────────────────

    /**
     * Tích lũy deltaTime, chỉ đếm ngược khi đủ 1 giây thực tế.
     * Tránh đèn chạy quá nhanh khi tick() được gọi 33 lần/giây.
     */
    public final void tick(double deltaTime) {
        // Đang ở chế độ thủ công → không đếm ngược tự động
        if (manualMode) return;

        timeAccumulator += deltaTime;

        // Mỗi khi tích lũy đủ 1 giây → đếm ngược 1 đơn vị
        while (timeAccumulator >= 1.0) {
            timeAccumulator -= 1.0;
            if (timeLeft > 0) {
                timeLeft--;
            } else {
                switchState();
            }
        }
    }

    private void switchState() {
        state = switch (state) {
            case GREEN  -> { timeLeft = yellowTime; yield State.YELLOW; }
            case YELLOW -> { timeLeft = redTime;    yield State.RED;    }
            case RED    -> { timeLeft = greenTime;  yield State.GREEN;  }
        };
    }

    // ── Điều khiển thủ công (Manual Mode) ───────────────────────────────

    // Khi manualMode = true, đèn KHÔNG tự chuyển theo timer
    private boolean manualMode = false;

    /**
     * Bật/tắt chế độ điều khiển thủ công.
     * Khi bật: đèn đứng yên, chờ người dùng click.
     * Khi tắt: đèn tiếp tục chạy tự động từ timeLeft hiện tại.
     */
    public void setManualMode(boolean manual) {
        this.manualMode = manual;
    }

    public boolean isManualMode() { return manualMode; }

    /**
     * Chuyển màu đèn thủ công theo thứ tự: RED → GREEN → YELLOW → RED.
     * Chỉ hoạt động khi manualMode = true.
     * Reset timeLeft về giá trị tương ứng của màu mới.
     */
    public void manualSwitch() {
        if (!manualMode) return;
        state = switch (state) {
            case RED    -> { timeLeft = greenTime;  yield State.GREEN;  }
            case GREEN  -> { timeLeft = yellowTime; yield State.YELLOW; }
            case YELLOW -> { timeLeft = redTime;    yield State.RED;    }
        };
    }

    // ── Getters & Setters ──────────────────────────────────────────────────

    public void setGreenTime(double time) {
        this.greenTime = time;
        if (state == State.GREEN && timeLeft > greenTime) timeLeft = greenTime;
    }

    public void setRedTime(double time) {
        this.redTime = time;
        if (state == State.RED && timeLeft > redTime) timeLeft = redTime;
    }

    // ── Kiểm tra trạng thái ───────────────────────────────────────────────

    public boolean isRed()    { return state == State.RED;    }
    public boolean isYellow() { return state == State.YELLOW; }
    public boolean isGreen()  { return state == State.GREEN;  }

    // ── Getters ───────────────────────────────────────────────────────────

    public State    getState()    { return state;        }
    public double   getTimeLeft() { return timeLeft;     }
    public Vector2D getPosition() { return position;     }
    public String   getColor()    { return state.name(); }

    public abstract String getDisplay();
}