package nachos.network;

import nachos.threads.KThread;
import nachos.threads.Lock;
import nachos.threads.ThreadedKernel;

import java.util.Iterator;
import java.util.LinkedList;

public class WatchdogTimer {
    LinkedList<WatchdogNew> handlers;
    final private int period;
    final Lock lock = new Lock();

    WatchdogTimer(int period) {
        this.period = period;
        handlers = new LinkedList<>();
        new KThread(new Runnable() {
            @Override
            public void run() {
                timeout();
            }
        }).fork();
    }

    public void addHandler(WatchdogNew handler) {
        lock.acquire();
        handlers.add(handler);
        lock.release();
    }

    public void removeHandler(WatchdogNew handler) {
        lock.acquire();
        handlers.remove(handler);
        lock.release();
    }


    private void timeout() {
        while (true) {
            ThreadedKernel.alarm.waitUntil(period);
            lock.acquire();
            for (Iterator i = handlers.iterator(); i.hasNext(); ) {
                WatchdogNew wd = (WatchdogNew) i.next();
                wd.handle();
                if (wd.expired()) {
                    i.remove();
                }
            }
            lock.release();
        }
    }

}
