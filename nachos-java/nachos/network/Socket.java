package nachos.network;

import nachos.machine.*;
import nachos.threads.*;

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

abstract class SocketChannel {
    final protected Socket socket;

    SocketChannel(Socket socket) {
        this.socket = socket;
    }

}

class SocketRx extends SocketChannel {
    private int curRecSeqNo;

    public void setStpSeqNo(int stpSeqNo) {
        this.stpSeqNo = stpSeqNo;
    }

    private int stpSeqNo = -1;
    private Lock recListLock;
    private LinkedList<SocketMessage> recInOrderList;
    private PriorityQueue<SocketMessage> recOutOfOrderList;
    private int winSize;
    private static final char dbgSocket = 's';

    SocketRx(Socket socket, int winSize) {
        super(socket);
        this.winSize = winSize;
        recOutOfOrderList = new PriorityQueue<>(this.winSize, new Comparator<SocketMessage>() {
            @Override
            public int compare(SocketMessage t1, SocketMessage t2) {
                return t1.seqNo - t2.seqNo;
            }
        });
        recInOrderList = new LinkedList<>();
        recListLock = new Lock();
    }

    public boolean receiveData(SocketMessage message) {
        if (message.seqNo < curRecSeqNo) {
            //for delayed trans
            sendAck(message);
            Lib.debug(dbgSocket, "receiver: resp seqno " + message.seqNo + " curRecSeqNo = " + curRecSeqNo);
            return true;
        }
        if (message.seqNo == curRecSeqNo) {
            recListLock.acquire();
            recInOrderList.add(message);
            curRecSeqNo++;
            while (!recOutOfOrderList.isEmpty()) {
                SocketMessage m = recOutOfOrderList.peek();
                if (m.seqNo > curRecSeqNo) {
                    break;
                }
                if (m.seqNo == curRecSeqNo) {
                    recInOrderList.add(m);
                    curRecSeqNo++;
                }
                recOutOfOrderList.remove(m);
            }
            recListLock.release();
            sendAck(message);
            Lib.debug(dbgSocket, "receiver: resp seqno " + message.seqNo + " curRecSeqNo = " + curRecSeqNo);
            return true;
        } else if (message.seqNo < curRecSeqNo + winSize) {
            recListLock.acquire();
            recOutOfOrderList.add(message);
            recListLock.release();
            sendAck(message);
            Lib.debug(dbgSocket, "receiver: resp seqno " + message.seqNo + " curRecSeqNo = " + curRecSeqNo);
            return true;
        }
        return false;
    }

    public boolean receiveSyn(SocketMessage message) {
        sendAck(message);
        return true;
    }

    public boolean receiveFin(SocketMessage message) {
        sendAck(message);
        return true;
    }

    public boolean receiveStp(SocketMessage message) {
        sendAck(message);
        return true;
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

    public void sendAck(SocketMessage message) {
        try {
            socket.send(new SocketMessage(message.fin, message.stp, true, message.syn, message.seqNo, new byte[0]));
        } catch (MalformedPacketException e) {
            Lib.assertNotReached("bad SocketMessage !");
        }
    }
}

class SocketTx extends SocketChannel {
    private int winSize;
    private int curSendSeqNo;
    private LinkedList<SocketMessage> sendInputList;
    private Lock sendingListLock;
    private Condition2 sendingBusy;
    private LinkedList<SocketMessage> sendingList;
    private static final char dbgSocket = 's';
    private static int sendingTimeout = 20000;
    final public Watchdog resendSynWd = new Watchdog(sendingTimeout, new Runnable() {
        @Override
        public void run() {
            sendSyn();
        }
    });

    final public Watchdog resendStpWd = new Watchdog(sendingTimeout, new Runnable() {
        @Override
        public void run() {
            sendStp();
        }
    });

    final public Watchdog resendDataWd = new Watchdog(sendingTimeout, new Runnable() {
        @Override
        public void run() {
            reSendData();
        }
    });

    final public Watchdog resendFinWd = new Watchdog(sendingTimeout, new Runnable() {
        @Override
        public void run() {
            sendFin();
        }
    });

    SocketTx(Socket socket, int winSize) {
        super(socket);
        this.winSize = winSize;
        curSendSeqNo = 0;
        sendInputList = new LinkedList<>();
        sendingList = new LinkedList<>();
        sendingListLock = new Lock();
        sendingBusy = new Condition2(sendingListLock);
    }

    //data channel
    public boolean receiveAck(SocketMessage message) {
        sendingListLock.acquire();
        boolean removed = false;
        for (Iterator i = sendingList.iterator(); i.hasNext(); ) {
            if (message.seqNo == ((SocketMessage) i.next()).seqNo) {
                i.remove();
                Lib.debug(dbgSocket, "Sender: get resp of seqno " + message.seqNo);
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

    //this method is idempotent
    public int sendData() {
        int burstSize = 0;
        sendingListLock.acquire();
        while (sendingList.size() < winSize) {
            if (sendInputList.isEmpty()) {
                break;
            }
            SocketMessage message = sendInputList.removeFirst();
            socket.send(message);
            sendingList.add(message);
            burstSize++;
        }

        sendingListLock.release();
        return burstSize;
    }

    public int reSendData() {
        int burstSize = 0;
        sendingListLock.acquire();
        //send old data
        for (Iterator i = sendingList.iterator(); i.hasNext(); ) {
            socket.send((SocketMessage) (i.next()));
            burstSize++;
        }
        sendingListLock.release();
        return burstSize;
    }

    public void waitSendListEmpty() {
        sendingListLock.acquire();
        while (!sendInputList.isEmpty() || !sendingList.isEmpty()) {
            sendingBusy.sleep();
        }
        sendingListLock.release();
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

    //command channels
    public SocketMessage sendCmd(boolean fin, boolean stp, boolean syn) {
        SocketMessage message;
        try {
            message = new SocketMessage(fin, stp, false, syn, curSendSeqNo, new byte[0]);
        } catch (MalformedPacketException e) {
            message = null;
            Lib.assertNotReached("bad SocketMessage !");
        }
        socket.send(message);
        return message;
    }

    public SocketMessage sendFin() {
        return sendCmd(true, false, false);
    }

    public SocketMessage sendStp() {
        return sendCmd(false, true, false);
    }

    public SocketMessage sendSyn() {
        return sendCmd(false, false, true);
    }


}

public class Socket {
    //states
    abstract class SocketState {

        SocketState() {
        }

        //user event
        protected void connect() {
        }

        protected void accept() {
        }

        protected boolean close() {
            return false;
        }

        protected int read(byte[] buf, int offset, int length) {
            return rx.read(buf, offset, length);
        }

        protected int write(byte[] buf, int offset, int length) {
            return -1;
        }

        //protocol event
        protected boolean syn(SocketMessage message) {
            return false;
        }

        protected boolean synAck(SocketMessage message) {
            return false;
        }

        protected boolean ack(SocketMessage message) {
            return false;
        }

        protected boolean data(SocketMessage message) {
            return false;
        }

        protected boolean stp(SocketMessage message) {
            return false;
        }

        protected boolean stpAck(SocketMessage message) {
            return false;
        }

        protected boolean fin(SocketMessage message) {
            return false;
        }

        protected boolean finAck(SocketMessage message) {
            return false;
        }

    }

    class SocketClosed extends SocketState {

        SocketClosed() {
        }

        //user event
        protected void connect() {
            changeState(this, new SocketSynSent(), new Runnable() {
                @Override
                public void run() {
                    tx.sendSyn();
                    tx.resendSynWd.start(wdt);
                }
            });
            //System.out.println("send syn from @ srcPort " + srcPort + " dstport " + dstPort);
        }

        //protocol event
        //only in closed state and received syn, don't check (dstLink, dstPort) tuple
        protected boolean syn(SocketMessage message) {
            dstLink = message.message.packet.srcLink;
            dstPort = message.message.srcPort;
            changeState(this, new SocketEstablished(), new Runnable() {
                @Override
                public void run() {
                    rx.receiveSyn(message);
                    canOpen.triggerEvent();
                }
            });

            //Lib.debug(dbgSocket, "get syn @ closed!");
            //System.out.println("get syn @closed srcPort = " + srcPort + " dstPort = " + dstPort);
            return true;
        }
    }

    class SocketSynSent extends SocketState {

        SocketSynSent() {
        }

        //user event

        //protocol event
        protected boolean synAck(SocketMessage message) {
            if (!checkLinkAndPort(message)) {
                return false;
            }
            changeState(this, new SocketEstablished(), new Runnable() {
                @Override
                public void run() {
                    tx.resendSynWd.expire(wdt);
                    canOpen.triggerEvent();
                }
            });

            //System.out.println("connect @SocketSynSent srcPort = " + srcPort + " dstPort = " + dstPort);
            return true;
        }
    }


    class SocketEstablished extends SocketState {

        SocketEstablished() {

        }

        private void sendData() {
            if (tx.sendData() > 0) {
                tx.resendDataWd.start(wdt);
            }
        }

        //user event
        protected boolean close() {
            tx.waitSendListEmpty();
            return changeState(this, new SocketStpSent(), new Runnable() {
                @Override
                public void run() {
                    tx.sendStp();
                    tx.resendStpWd.start(wdt);
                }
            });
        }

        protected int write(byte[] buf, int offset, int length) {
            int amount = tx.write(buf, offset, length);
            sendData();
            return amount;
        }

        //protocol event
        protected boolean syn(SocketMessage message) {
            if (!checkLinkAndPort(message)) {
                return false;
            }
            rx.receiveSyn(message);
            return true;
        }

        protected boolean ack(SocketMessage message) {
            if (!checkLinkAndPort(message)) {
                return false;
            }
            if (tx.receiveAck(message)) {
                tx.resendDataWd.expire(wdt);
                sendData();
            }
            return true;
        }

        protected boolean data(SocketMessage message) {
            if (!checkLinkAndPort(message)) {
                return false;
            }
            rx.receiveData(message);
            return true;
        }


        protected boolean stp(SocketMessage message) {
            if (!checkLinkAndPort(message)) {
                return false;
            }
            changeState(this, new SocketStpRcvd(), new Runnable() {
                @Override
                public void run() {
                    rx.setStpSeqNo(message.seqNo);
                    rx.receiveStp(message);
                }
            });
            return true;
        }
    }


    class SocketStpSent extends SocketState {
        //if both enter this state, and both stp missed
        SocketStpSent() {
        }

        //user event
        //protocol event
        protected boolean syn(SocketMessage message) {
            if (!checkLinkAndPort(message)) {
                return false;
            }
            rx.receiveSyn(message);
            return true;
        }

        protected boolean data(SocketMessage message) {
            if (!checkLinkAndPort(message)) {
                return false;
            }
            Lib.debug(dbgSocket, "get data @ stpsent!");
            rx.receiveData(message);
            return true;
        }

        protected boolean stpAck(SocketMessage message) {
            if (!checkLinkAndPort(message)) {
                return false;
            }
            changeState(this, new SocketClosing(), new Runnable() {
                @Override
                public void run() {
                    tx.resendStpWd.expire(wdt);

                }
            });
            //System.out.println("enter SocketClosing because stpAck srcPort = " + srcPort + " dstPort = " + dstPort);
            return true;
        }

        //both active close, use fin as response, no stpack
        protected boolean stp(SocketMessage message) {
            if (!checkLinkAndPort(message)) {
                return false;
            }
            //System.out.println("enter SocketClosing because stp srcPort = " + srcPort + " dstPort = " + dstPort);
            changeState(this, new SocketClosing(), new Runnable() {
                @Override
                public void run() {
                    tx.sendFin();
                }
            });
            return true;
        }
    }

    class SocketStpRcvd extends SocketState {

        SocketStpRcvd() {
        }

        //user event
        protected boolean close() {
            tx.waitSendListEmpty();
            tx.sendFin();
            tx.resendFinWd.start(wdt);
            return true;
        }
        //protocol event

        protected boolean ack(SocketMessage message) {
            if (!checkLinkAndPort(message)) {
                return false;
            }
            if (tx.receiveAck(message)) {
                tx.resendDataWd.expire(wdt);
                if (tx.sendData() > 0) {
                    tx.resendDataWd.start(wdt);
                }
            }
            return true;
        }

        protected boolean stp(SocketMessage message) {
            if (!checkLinkAndPort(message)) {
                return false;
            }
            rx.receiveStp(message);
            return true;
        }

        protected boolean finAck(SocketMessage message) {
            if (!checkLinkAndPort(message)) {
                return false;
            }
            System.out.println("closed @ SocketLastAck srcPort = " + srcPort + " dstPort = " + dstPort);
            changeState(this, new SocketClosed(), new Runnable() {
                @Override
                public void run() {
                    tx.resendFinWd.expire(wdt);
                    canClose.triggerEvent();
                }
            });
            return true;
        }
    }


    class SocketClosing extends SocketState {
        SocketClosing() {
            System.out.println("enter SocketClosing srcPort = " + srcPort + " dstPort = " + dstPort);
        }

        //protocol event

        protected boolean data(SocketMessage message) {
            if (!checkLinkAndPort(message)) {
                return false;
            }
            Lib.debug(dbgSocket, "get data @ stpsent!");
            rx.receiveData(message);
            return true;
        }

        protected boolean fin(SocketMessage message) {
            if (!checkLinkAndPort(message)) {
                return false;
            }
            changeState(this, new SocketClosing2(), new Runnable() {
                @Override
                public void run() {
                    tx.resendStpWd.expire(wdt);
                    rx.receiveFin(message);
                }
            });
            return true;
        }

        protected boolean stp(SocketMessage message) {
            if (!checkLinkAndPort(message)) {
                return false;
            }
            tx.sendFin();
            return true;
        }

    }

    class SocketClosing2 extends SocketState {
        final Watchdog wd = new Watchdog(sendingTimeout * 200, new Runnable() {
            @Override
            public void run() {
                System.out.println("closed @ SocketClosing2 srcPort = " + srcPort + " dstPort = " + dstPort);
                changeState(SocketClosing2.this, new SocketClosed(), new Runnable() {
                    @Override
                    public void run() {
                        canClose.triggerEvent();
                    }
                });
            }
        });

        SocketClosing2() {
            System.out.println("enter SocketClosing2 srcPort = " + srcPort + " dstPort = " + dstPort);
            wd.start(wdt, 1);
        }

        //protocol event
        protected boolean fin(SocketMessage message) {
            if (!checkLinkAndPort(message)) {
                return false;
            }
            rx.receiveFin(message);
            wd.expire(wdt);
            wd.start(wdt, 1);
            return true;
        }

        protected boolean stp(SocketMessage message) {
            if (!checkLinkAndPort(message)) {
                return false;
            }
            tx.sendFin();
            wd.expire(wdt);
            wd.start(wdt, 1);
            return true;
        }

    }

    //filesystem interface
    private class File extends OpenFile {
        File() {
            super(null, "SocketOld Sever File");
        }

        public void close() {
            Socket.this.close();
        }

        public int read(byte[] buf, int offset, int length) {
            int amount = state.read(buf, offset, length);
            return amount;
        }

        public int write(byte[] buf, int offset, int length) {
            return state.write(buf, offset, length);
        }
    }


    private OpenFile File;

    private SocketState state;
    final Lock stateLock = new Lock();

    private Event canOpen;
    private Event canClose;

    private static int dataWinSize = 16;
    private static int sendingTimeout = 20000;
    final private SocketPostOffice postOffice;
    final private WatchdogTimer wdt;
    int srcPort = -1;
    int dstPort = -1;
    int dstLink = -1;
    private static final char dbgSocket = 's';


    //for receive
    private SocketRx rx;
    //for send
    private SocketTx tx;

    public Socket(SocketPostOffice postOffice, WatchdogTimer wdt) {
        this.postOffice = postOffice;
        this.wdt = wdt;
        state = new SocketClosed();
        canOpen = new Event();
        canClose = new Event();

        rx = new SocketRx(this, dataWinSize);
        tx = new SocketTx(this, dataWinSize);

    }

    private boolean checkLinkAndPort(SocketMessage message) {
        return message.message.srcPort == this.dstPort && message.message.packet.srcLink == this.dstLink;
    }

    //events
    public OpenFile connect(int dstLink, int dstPort) {
        Lib.assertTrue(state instanceof SocketClosed);
        postOffice.bind(this);
        Lib.assertTrue(this.srcPort != -1, "no free port!");
        this.dstLink = dstLink;
        this.dstPort = dstPort;
        state.connect();
        canOpen.waitEvent();
        this.File = new File();
        Lib.debug(dbgSocket, "connected!");
        return this.File;
    }

    public OpenFile accept(int port) {
        Lib.assertTrue(state instanceof SocketClosed);
        postOffice.bind(this, port);
        state.accept();
        canOpen.waitEvent();
        this.File = new File();
        Lib.debug(dbgSocket, "accepted!");
        return this.File;
    }

    public void close() {
        Lib.assertTrue(state instanceof SocketEstablished || state instanceof SocketStpRcvd, "state is " + state.getClass());
        while (!state.close()) ;
        canClose.waitEvent();
        postOffice.unbind(Socket.this);
        //Lib.debug(dbgSocket, "closed!");
        //System.out.println("closed! @ srcPort: " + srcPort + " dstPort: " + dstPort);
    }

    public boolean receive(SocketMessage message) {
        Lib.debug(dbgSocket, "current state :" + state.getClass());
        Lib.debug(dbgSocket, "from port " + message.message.srcPort + " to port " + message.message.dstPort + " @ srcPort: " + srcPort + " dstPort: " + dstPort);
        //syn/synack
        if (message.syn) {
            if (message.ack) {
                Lib.debug(dbgSocket, "get syn ack!");
                return state.synAck(message);
            }
            Lib.debug(dbgSocket, "get syn!");
            return state.syn(message);
        }
        //fin/finack
        if (message.fin) {
            if (message.ack) {
                Lib.debug(dbgSocket, "get fin ack!");
                return state.finAck(message);
            }
            Lib.debug(dbgSocket, "get fin!");
            return state.fin(message);
        }
        //stp
        if (message.stp) {
            if (message.ack) {
                Lib.debug(dbgSocket, "get stp ack!");
                return state.stpAck(message);
            }
            Lib.debug(dbgSocket, "get stp!");
            return state.stp(message);
        }
        //ack
        if (message.ack) {
            Lib.debug(dbgSocket, "get ack!");
            return state.ack(message);
        }
        //data
        Lib.debug(dbgSocket, "get data!");
        return state.data(message);
    }

    //actions
    private boolean changeState(SocketState curState, SocketState nxtState, Runnable action) {
        if (changeState(curState, nxtState)) {
            action.run();
            return true;
        }
        return false;
    }

    private boolean changeState(SocketState curState, SocketState nxtState) {
        stateLock.acquire();
        if (curState == state) {
            state = nxtState;
            stateLock.release();
            return true;
        }
        stateLock.release();
        return false;
    }

    public void send(SocketMessage message) {
        postOffice.send(this, message);
    }
}
