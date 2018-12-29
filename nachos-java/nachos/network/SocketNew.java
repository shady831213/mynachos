package nachos.network;

import nachos.machine.Lib;
import nachos.machine.MalformedPacketException;
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
        private boolean connect() {
            return false;
        }

        private boolean accept() {
            return false;
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

        SocketClosed() {
        }

        //user event
        private boolean connect() {
            sendSyn();
            state = new SocketSynSent();
            canOpen.waitEvent();
            return true;
        }

        private void read() {
        }

        private boolean accept() {
            canOpen.waitEvent();
            return true;
        }

        //protocol event
        private void syn(SocketMessage message) {
            sendAck(message);
            canOpen.triggerEvent();
            state = new SocketEstablished();
        }

        private void fin(SocketMessage message) {
            sendAck(message);
        }

    }

    class SocketSynSent extends SocketState {

        SocketSynSent() {
            wd.start(sendingTimeout, new Runnable() {
                @Override
                public void run() {
                    sendSyn();
                }
            }, -1);
        }

        //user event

        //protocol event
        private void synAck(SocketMessage message) {
            wd.reset();
            canOpen.triggerEvent();
            state = new SocketEstablished();
        }
    }


    class SocketEstablished extends SocketState {


        SocketEstablished() {

        }

        //user event
        private void close() {
            sendStp();
            state = new SocketStpSent();
        }

        private void write() {
        }

        private void read() {

        }

        //protocol event
        private void syn(SocketMessage message) {
            sendAck(message);
        }

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
            state = new SocketStpRcvd();
        }
    }


    class SocketStpSent extends SocketState {
        //if both enter this state, and both stp missed
        SocketStpSent() {
            wd.start(sendingTimeout, new Runnable() {
                @Override
                public void run() {
                    sendStp();
                }
            }, -1);
        }

        //user event
        private void close() {

        }

        private void write() {
        }

        private void read() {

        }

        //protocol event
        private void syn(SocketMessage message) {
            sendAck(message);
        }

        private void data(SocketMessage message) {
            if (receiveData(message)) {
                sendAck(message);
            }
        }

        private void fin(SocketMessage message) {
            wd.reset();
            sendAck(message);
            state = new SocketClosed();
        }

        private void stp(SocketMessage message) {
            wd.reset();
            sendFin();
            state = new SocketClosing();
        }
    }

    class SocketStpRcvd extends SocketState {

        SocketStpRcvd() {
        }

        //user event
        private void close() {
            sendFin();
            state = new SocketClosing();
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

        private void fin(SocketMessage message) {
            sendAck(message);
            state = new SocketClosed();
        }
    }


    class SocketClosing extends SocketState {

        SocketClosing() {
            wd.start(sendingTimeout, new Runnable() {
                @Override
                public void run() {
                    sendFin();
                }
            }, -1);
        }

        //user event
        private void close() {
        }

        private void write() {

        }

        private void read() {

        }


        //protocol event
        private void fin(SocketMessage message) {
            wd.reset();
            sendAck(message);
            state = new SocketClosed();
        }

        private void finAck(SocketMessage message) {
            wd.reset();
            state = new SocketClosed();
        }


    }

    private OpenFile File;

    private SocketState state;

    private Event canOpen;

    private static int dataWinSize = 16;
    private static int sendingTimeout = 20000;
    final private watchdog wd = new watchdog(Stats.NetworkTime);


    //for receive
    protected int curRecSeqNo;
    protected int stpSeqNo = -1;
    Lock recListLock;
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
        canOpen = new Event();

        recOutOfOrderList = new PriorityQueue<>(dataWinSize, new Comparator<SocketMessage>() {
            @Override
            public int compare(SocketMessage t1, SocketMessage t2) {
                return t1.seqNo - t2.seqNo;
            }
        });
        recInOrderList = new LinkedList<>();
        recListLock = new Lock();

        sendInputList = new LinkedList<>();
        sendingList = new LinkedList<>();
        sendingListLock = new Lock();
        sendingBusy = new Condition2(sendingListLock);
    }

    public OpenFile connect() {
        if (state.connect()) {
            this.File = new File();
        }
        return this.File;
    }

    public OpenFile accept() {
        if (state.accept()) {
            this.File = new File();
        }
        return this.File;
    }

    //actions
    private SocketMessage sendSyn() {
        return new SocketMessage(false, false, false, true, 0);
    }

    //must atomic
    private SocketMessage sendFin() {
        SocketMessage finMessage;
        sendingListLock.acquire();
        waitSendListEmpty();
        finMessage = new SocketMessage(true, false, false, false, curSendSeqNo);
        sendingListLock.release();
        return finMessage;
    }

    //must atomic
    private SocketMessage sendStp() {
        SocketMessage stpMessage;
        sendingListLock.acquire();
        waitSendListEmpty();
        stpMessage = new SocketMessage(false, true, false, false, curSendSeqNo);
        sendingListLock.release();
        return stpMessage;
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
        } else if (message.seqNo > curRecSeqNo && message.seqNo < curRecSeqNo + dataWinSize) {
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
            sendingBusy.wake();
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

    private void waitSendListEmpty() {
        while (!sendInputList.isEmpty() || !sendingList.isEmpty()) {
            sendingBusy.sleep();
        }
    }

    private void sendAck(SocketMessage message) {
        //send(new SocketMessage(message.fin, message.stp, true, message.syn, message.seqNo));
    }

    private class File extends OpenFile {
        File() {
            super(null, "Socket Sever File");
        }

        public void close() {
        }

        public int read(byte[] buf, int offset, int length) {
            int amount = 0;
            int pos = offset;
            recListLock.acquire();
            while (amount < length) {
                if (recInOrderList.isEmpty()) {
                    recListLock.release();
                    return amount;
                }
                SocketMessage message = (SocketMessage) recInOrderList.peekFirst();
                int _amount = Math.min(message.contents.length, length - amount);
                System.arraycopy(message.contents, 0, buf, pos, _amount);
                if (_amount == message.contents.length) {
                    recInOrderList.removeFirst();
                } else {
                    //deal with left data
                    byte[] leftContent = new byte[message.contents.length - _amount];
                    System.arraycopy(message.contents, _amount, leftContent, 0, message.contents.length - _amount);
                    message.contents = leftContent;
                }
                amount += _amount;
                pos += _amount;
            }
            recListLock.release();
            return amount;
        }

        public int write(byte[] buf, int offset, int length) {
            sendingListLock.acquire();
            int amount = 0;
            int pos = offset;
            while (amount < length) {
                int _amount = Math.min(SocketMessage.maxContentsLength, length - amount);
                byte[] content = new byte[_amount];
                System.arraycopy(buf, pos, content, 0, _amount);
                try {
                    sendInputList.add(new SocketMessage(false, false, false, false, curSendSeqNo, content));
                } catch (MalformedPacketException e) {
                    sendingListLock.release();
                    return amount;
                }
                curSendSeqNo++;
                amount += _amount;
                pos += _amount;
            }
            sendingListLock.release();
            return amount;
        }
    }
}
