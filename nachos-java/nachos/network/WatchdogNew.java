package nachos.network;

import nachos.machine.Machine;

public class WatchdogNew {
    private long time;
    private long startTime;
    private Runnable timeoutHandler;
    private int repeat;

    WatchdogNew(int time, Runnable timeoutHandler) {
        this.time = time;
        this.timeoutHandler = timeoutHandler;
        this.repeat = -1;
    }


    public boolean expired() {
        return repeat == 0;
    }


    public void expire(WatchdogTimer timer) {
        timer.removeHandler(this);
    }

    public void start(WatchdogTimer timer) {
        repeat = -1;
        startTime = Machine.timer().getTime();
        timer.addHandler(this);
    }

    public void start(WatchdogTimer timer, int repeat) {
        this.repeat = repeat;
        startTime = Machine.timer().getTime();
        timer.addHandler(this);
    }

    public void handle() {
        if (startTime + time > Machine.timer().getTime()) {
            startTime = Machine.timer().getTime();
            timeoutHandler.run();
            if (repeat > 0) {
                repeat--;
            }
        }
    }
}
