package nachos.network;

import nachos.machine.Lib;
import nachos.machine.OpenFile;
import nachos.machine.Stats;
import nachos.threads.Condition2;
import nachos.threads.KThread;
import nachos.threads.Lock;
import nachos.threads.ThreadedKernel;

import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.function.Predicate;

class Event {
    private Lock lock = new Lock();
    private boolean triggered;
    private Condition2 cond = new Condition2(lock);

    Event() {
    }

    public void waitEvent() {
        lock.acquire();
        while (!triggered) {
            cond.sleep();
        }
        triggered = false;
        lock.release();
    }

    public void waitEventOn() {
        lock.acquire();
        while (!triggered) {
            cond.sleep();
        }
        lock.release();
    }

    public void triggerEvent() {
        lock.acquire();
        triggered = true;
        cond.wake();
        lock.release();
    }

    public void resetEvent() {
        lock.acquire();
        triggered = false;
        lock.release();
    }
}


class watchdog {
    final private int period;
    private boolean clear;
    private KThread timeoutT;
    Event started;
    Event ended;

    watchdog(int period) {
        this.period = period;
        started = new Event();
        ended = new Event();
    }

    //must call after start!
    public void reset() {
        started.waitEvent();
        clear = true;
        ended.waitEvent();
    }

    public KThread start(int time, Runnable timeoutHandler, int repeat) {
        timeoutT = new KThread(new Runnable() {
            @Override
            public void run() {
                started.triggerEvent();
                _timeout(time, timeoutHandler, repeat);
                ended.triggerEvent();
            }
        });
        timeoutT.fork();
        return timeoutT;
    }

    //if repeat <0, forever
    private void _timeout(int time, Runnable timeoutHandler, int repeat) {
        int repeatCnt = repeat;
        while (repeatCnt != 0) {
            int watchdog = time;
            while (watchdog > 0) {
                ThreadedKernel.alarm.waitUntil(period);
                if (clear) {
                    clear = false;
                    return;
                }
                watchdog -= period;
            }
            timeoutHandler.run();
            if (repeatCnt > 0) {
                repeatCnt--;
            }
        }
    }

}

public class SocketNew {
    //states
    abstract class SocketState {

        SocketState() {
        }

        //user event
        private void connect() {
        }

        private void accept() {
        }

        private void close() {

        }

        private void write() {

        }

        private void read() {

        }

        //protocol event
        private void syn(SocketMessage message) {

        }

        private void synAck(SocketMessage message) {

        }

        private void ack(SocketMessage message) {

        }

        private void data(SocketMessage message) {

        }

        private void stp(SocketMessage message) {

        }

        private void fin(SocketMessage message) {

        }

        private void finAck(SocketMessage message) {

        }

    }

    class SocketClosed extends SocketState {
        private SocketMessage syncMessage;

        SocketClosed() {
        }

        //user event
        private void connect() {
            syncMessage = sendSyn();
            wd.start(sendingTimeout, new Runnable() {
                @Override
                public void run() {
                    syncMessage = sendSyn();
                }
            }, -1).join();
            state = new SocketEstablished();
        }

        private void read() {
        }


        private void accept() {
            wd.start(sendingTimeout, new Runnable() {
                @Override
                public void run() {
                }
            }, -1).join();
            state = new SocketEstablished();
        }

        //protocol event
        private void syn(SocketMessage message) {
            sendAck(message);
            wd.reset();
        }

        private void synAck(SocketMessage message) {
            wd.reset();
        }

        private void fin(SocketMessage message) {
        }

    }

    class SocketEstablished extends SocketState {


        SocketEstablished() {

        }

        //user event
        private void close() {

        }

        private void write() {

        }

        private void read() {

        }

        //protocol event
        private void ack(SocketMessage message) {
            if (receiveAck(message)) {
                wd.reset();
                if (sendData() > 0) {
                    wd.start(sendingTimeout, new Runnable() {
                        @Override
                        public void run() {
                            reSendData();
                        }
                    }, -1);
                }
            }
        }

        private void data(SocketMessage message) {
            if (receiveData(message)) {
                sendAck(message);
            }
        }


        private void stp(SocketMessage message) {
            stpSeqNo = message.seqNo;
        }
    }


    private OpenFile File;

    private SocketState state;

    private static int dataWinSize = 16;
    private static int sendingTimeout = 20000;
    final private watchdog wd = new watchdog(Stats.NetworkTime);


    //for receive
    protected int curRecSeqNo;
    protected int stpSeqNo = -1;
    Lock recListLock;
    Event recDone;
    LinkedList<SocketMessage> recInOrderList;
    PriorityQueue<SocketMessage> recOutOfOrderList;

    //for send
    protected int curSendSeqNo;
    LinkedList<SocketMessage> sendInputList;
    Lock sendingListLock;
    Condition2 sendingBusy;
    LinkedList<SocketMessage> sendingList;

    public SocketNew() {
        state = new SocketClosed();

        recOutOfOrderList = new PriorityQueue<>(dataWinSize, new Comparator<SocketMessage>() {
            @Override
            public int compare(SocketMessage t1, SocketMessage t2) {
                return t1.seqNo - t2.seqNo;
            }
        });
        recInOrderList = new LinkedList<>();
        recListLock = new Lock();
        recDone = new Event();

        sendInputList = new LinkedList<>();
        sendingList = new LinkedList<>();
        sendingListLock = new Lock();
        sendingBusy = new Condition2(sendingListLock);
    }

    public OpenFile connect() {
        state.connect();
        return openStream();
    }

    public OpenFile accept() {
        state.accept();
        return openStream();
    }

    private OpenFile openStream() {
        this.File = new File();
        return this.File;
    }

    //actions
    private SocketMessage sendSyn() {
        return new SocketMessage(false, false, false, true, 0);
    }

    private boolean receiveData(SocketMessage message) {
        recListLock.acquire();
        while (!recOutOfOrderList.isEmpty()) {
            SocketMessage m = recOutOfOrderList.peek();
            if (m.seqNo != curRecSeqNo) {
                break;
            }
            recInOrderList.add(m);
            curRecSeqNo++;
            recOutOfOrderList.remove(m);
        }
        recListLock.release();
        if (message.seqNo == curRecSeqNo) {
            recListLock.acquire();
            recInOrderList.add(message);
            curRecSeqNo++;
            recListLock.release();
            return true;
        } else if (message.seqNo > curRecSeqNo) {
            recListLock.acquire();
            recOutOfOrderList.add(message);
            recListLock.release();
            return true;
        }
        return false;
    }

    private boolean receiveAck(SocketMessage message) {
        sendingListLock.acquire();
        boolean removed = false;
        for (Iterator i = sendingList.iterator(); i.hasNext(); ) {
            if (message.seqNo == ((SocketMessage) i.next()).seqNo) {
                i.remove();
                removed = true;
                break;
            }
        }
        if (sendingList.isEmpty() && removed) {
            sendingListLock.release();
            return true;
        }
        sendingListLock.release();
        return false;
    }

    private int sendData() {
        int burstSize = 0;
        sendingListLock.acquire();
        //shift data in
        while (sendingList.size() < dataWinSize) {
            if (sendInputList.isEmpty()) {
                break;
            }
            SocketMessage message = sendInputList.removeFirst();
            //send(message);
            sendingList.add(message);
            burstSize++;
        }

        sendingListLock.release();
        return burstSize;
    }

    private int reSendData() {
        int burstSize = 0;
        sendingListLock.acquire();
        //send old data
        for (Iterator i = sendingList.iterator(); i.hasNext(); ) {
            //send((SocketMessage)(i.next()));
            burstSize++;
        }
        sendingListLock.release();
        return burstSize;
    }

    private void sendAck(SocketMessage message) {
        //send(new SocketMessage(message.fin, message.stp, true, message.syn, message.seqNo));
    }

    private class File extends OpenFile {

    }
}
