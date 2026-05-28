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
    protected double     timeLeft;
    protected final double greenTime;
    protected final double yellowTime;
    protected final double redTime;
    protected Vector2D   position;

    // Tích lũy thời gian thực — chỉ đếm ngược khi đủ 1 giây
    private double timeAccumulator = 0.0;

    public TrafficLight(double greenTime, double redTime, double x, double y) {
        this.greenTime  = greenTime;
        this.yellowTime = 3.0;
        this.redTime    = redTime;
        this.state      = State.RED;
        this.timeLeft   = redTime;
        this.position   = new Vector2D(x, y);
    }

    public TrafficLight(int greenTime, int yellowTime, int redTime, double x, double y) {
        this.greenTime  = greenTime;
        this.yellowTime = yellowTime;
        this.redTime    = redTime;
        this.state      = State.RED;
        this.timeLeft   = redTime;
        this.position   = new Vector2D(x, y);
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

    public abstract String getDisplay(); // Mỗi loại đèn sẽ có cách hiển thị riêng

    // ── Getters ───────────────────────────────────────────────────────────

    public State    getState()    { return state;        }
    public double   getTimeLeft() { return timeLeft;     }
    public Vector2D getPosition() { return position;     }
    public String   getColor()    { return state.name(); }

    public boolean isRed()    { return state == State.RED;    }
    public boolean isYellow() { return state == State.YELLOW; }
    public boolean isGreen()  { return state == State.GREEN;  }
}
