package nachos.network;

import nachos.machine.Lib;
import nachos.machine.MalformedPacketException;
import nachos.machine.OpenFile;
import nachos.machine.Stats;
import nachos.threads.Condition2;
import nachos.threads.KThread;
import nachos.threads.Lock;
import nachos.threads.ThreadedKernel;

import java.util.*;

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

class SocketRx {
    final private PostOffice postOffice;
    private Lock[] SocketListLock;
    private LinkedList<SocketNew>[] Sockets;

    SocketRx(PostOffice postOffice) {
        this.postOffice = postOffice;
        SocketListLock = new Lock[MailMessage.portLimit];
        Sockets = new LinkedList[MailMessage.portLimit];
        for (int i = 0; i < Sockets.length; i++) {
            Sockets[i] = new LinkedList<>();
            SocketListLock[i] = new Lock();
        }
    }

    public void bind(SocketNew socket) {
        SocketListLock[socket.srcPort].acquire();
        Sockets[socket.srcPort].add(socket);
        SocketListLock[socket.srcPort].release();
    }

    public void unbind(SocketNew socket) {
        SocketListLock[socket.srcPort].acquire();
        Sockets[socket.srcPort].remove(socket);
        SocketListLock[socket.srcPort].release();
    }

    private void dispatch(MailMessage mail, int port) {
        SocketListLock[port].acquire();
        for (Iterator i = Sockets[port].iterator(); i.hasNext(); ) {
            if (((SocketNew) i.next()).receiveMail(mail)) {
                SocketListLock[port].release();
                return;
            }
        }
        SocketListLock[port].release();
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
        private boolean syn(SocketMessage message) {
            return false;
        }

        private boolean synAck(SocketMessage message) {
            return false;
        }

        private boolean ack(SocketMessage message) {
            return false;
        }

        private boolean data(SocketMessage message) {
            return false;
        }

        private boolean stp(SocketMessage message) {
            return false;
        }

        private boolean fin(SocketMessage message) {
            return false;
        }

        private boolean finAck(SocketMessage message) {
            return false;
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
        private boolean syn(SocketMessage message) {
            sendAck(message);
            dstLink = message.message.packet.dstLink;
            dstPort = message.message.dstPort;
            canOpen.triggerEvent();
            state = new SocketEstablished();
            return true;
        }

        private boolean fin(SocketMessage message) {
            if (!checkLinkAndPort(message.message)) {
                return false;
            }
            sendAck(message);
            return true;
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
        private boolean synAck(SocketMessage message) {
            if (!checkLinkAndPort(message.message)) {
                return false;
            }
            wd.reset();
            canOpen.triggerEvent();
            state = new SocketEstablished();
            return true;
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
        private boolean syn(SocketMessage message) {
            if (!checkLinkAndPort(message.message)) {
                return false;
            }
            sendAck(message);
            return true;
        }

        private boolean ack(SocketMessage message) {
            if (!checkLinkAndPort(message.message)) {
                return false;
            }
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
            return true;
        }

        private boolean data(SocketMessage message) {
            if (!checkLinkAndPort(message.message)) {
                return false;
            }
            if (receiveData(message)) {
                sendAck(message);
            }
            return true;
        }


        private boolean stp(SocketMessage message) {
            if (!checkLinkAndPort(message.message)) {
                return false;
            }
            stpSeqNo = message.seqNo;
            state = new SocketStpRcvd();
            return true;
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
        private boolean syn(SocketMessage message) {
            if (!checkLinkAndPort(message.message)) {
                return false;
            }
            sendAck(message);
            return true;
        }

        private boolean data(SocketMessage message) {
            if (!checkLinkAndPort(message.message)) {
                return false;
            }
            if (receiveData(message)) {
                sendAck(message);
            }
            return true;
        }

        private boolean fin(SocketMessage message) {
            if (!checkLinkAndPort(message.message)) {
                return false;
            }
            wd.reset();
            sendAck(message);
            state = new SocketClosed();
            return true;
        }

        private boolean stp(SocketMessage message) {
            if (!checkLinkAndPort(message.message)) {
                return false;
            }
            wd.reset();
            sendFin();
            state = new SocketClosing();
            return true;
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

        private boolean ack(SocketMessage message) {
            if (!checkLinkAndPort(message.message)) {
                return false;
            }
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
            return true;
        }

        private boolean fin(SocketMessage message) {
            if (!checkLinkAndPort(message.message)) {
                return false;
            }
            sendAck(message);
            state = new SocketClosed();
            return true;
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
        private boolean fin(SocketMessage message) {
            if (!checkLinkAndPort(message.message)) {
                return false;
            }
            wd.reset();
            sendAck(message);
            state = new SocketClosed();
            return true;
        }

        private boolean finAck(SocketMessage message) {
            if (!checkLinkAndPort(message.message)) {
                return false;
            }
            wd.reset();
            state = new SocketClosed();
            return true;
        }


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


    private OpenFile File;

    private SocketState state;

    private Event canOpen;

    private static int dataWinSize = 16;
    private static int sendingTimeout = 20000;
    final private watchdog wd = new watchdog(Stats.NetworkTime);
    int srcPort = -1;
    int dstPort = -1;
    int dstLink = -1;

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

    private boolean checkLinkAndPort(MailMessage mail) {
        return mail.dstPort == this.dstPort && mail.packet.dstLink == this.dstLink;
    }

    //events
    public OpenFile connect(int dstLink, int dstPort) {
        if (state.connect()) {
            this.File = new File();
        }
        //this.srcPort = getPort()
        this.dstLink = dstLink;
        this.dstPort = dstPort;
        return this.File;
    }

    public OpenFile accept(int port) {
        if (state.accept()) {
            this.File = new File();
        }
        this.srcPort = port;
        return this.File;
    }

    public boolean receiveMail(MailMessage mail) {
        SocketMessage message = new SocketMessage();
        try {
            message.recMailMessage(mail);
        } catch (MalformedPacketException e) {
            Lib.assertNotReached("get a bad mail!");
        }
        //syn/synack
        if (message.syn) {
            if (message.ack) {
                return state.synAck(message);
            }
            return state.syn(message);
        }
        //fin/finack
        if (message.fin) {
            if (message.ack) {
                return state.finAck(message);
            }
            return state.fin(message);
        }
        //stp
        if (message.stp) {
            return state.stp(message);
        }
        //ack
        if (message.ack) {
            return state.ack(message);
        }
        //data
        return state.data(message);
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

}
