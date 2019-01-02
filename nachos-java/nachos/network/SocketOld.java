package nachos.network;

import nachos.machine.*;
import nachos.threads.*;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.function.Predicate;

public abstract class SocketOld {
    //constant
    enum SocketState {
        CLOSED,
        SYN_SENT,
        SYN_RCVD,
        ESTABLISHED,
        STP_SENT,
        STP_RCVD,
        CLOSING;
    }

    ;

    private static int dataWinSize = 16;
    private static int sendingTimeout = 20000;

    //state machine

    protected Lock stateLock;
    protected SocketState state;
    private Condition2 waitClose;
    private Condition2 waitOpen;
    //state vars
    protected int srcPort;
    protected int srcLink;
    protected int dstPort;
    protected int dstLink;
    protected int curSeqNo;


    //for receive
    protected int curRecSeqNo;
    protected int stpSeqNo;
    Lock recListLock;
    Condition2 recBusy;
    LinkedList<SocketMessage> recInOrderList;
    PriorityQueue<SocketMessage> recOutOfOrderList;

    //for send
    protected int curSendSeqNo;
    LinkedList<SocketMessage> sendInputList;
    Lock sendingListLock;
    Condition2 watchDogEn;
    Condition2 sendingListNotFull;
    Condition2 sendingListNotEmpty;
    Condition2 sendingBusy;
    LinkedList<SocketMessage> sendingList;

    public SocketOld(int srcPort) {
        this.srcPort = srcPort;
        this.srcLink = Machine.networkLink().getLinkAddress();
        this.state = SocketState.CLOSED;
        stateLock = new Lock();
        waitClose = new Condition2(stateLock);
        waitOpen = new Condition2(stateLock);
        recOutOfOrderList = new PriorityQueue<>(dataWinSize, new Comparator<SocketMessage>() {
            @Override
            public int compare(SocketMessage t1, SocketMessage t2) {
                return t1.seqNo - t2.seqNo;
            }
        });
        recInOrderList = new LinkedList<>();
        recListLock = new Lock();
        recBusy = new Condition2(recListLock);
        sendInputList = new LinkedList<>();
        sendingList = new LinkedList<>();
        sendingListLock = new Lock();
        watchDogEn = new Condition2(sendingListLock);
        sendingListNotFull = new Condition2(sendingListLock);
        sendingListNotEmpty = new Condition2(sendingListLock);
        sendingBusy = new Condition2(sendingListLock);
    }

    protected void handleClosed() {
    }

    protected void handleSynSent() {

    }

    protected void handleSynRcvd() {
    }

    protected void handleEstablished() {
    }


    protected void handleStpSent() {

    }


    protected void handleStpRcvd() {

    }

    protected void handleClosing() {

    }

    final public void schedule() {
        switch (this.state) {
            case CLOSED:
                handleClosed();
                break;
            case SYN_SENT:
                handleSynSent();
                break;
            case SYN_RCVD:
                handleSynRcvd();
                break;
            case ESTABLISHED:
                handleEstablished();
                break;
            case STP_SENT:
                handleStpSent();
                break;
            case STP_RCVD:
                handleStpRcvd();
                break;
            case CLOSING:
                handleClosing();
                break;
            default:
                Lib.assertNotReached("invalid state!");
        }
    }

    public OpenFile accept() {
        stateLock.acquire();
        while (this.state != SocketState.SYN_RCVD) {
            waitOpen.sleep();
        }
        state = SocketState.ESTABLISHED;
        stateLock.release();
        return new File();
    }

    public OpenFile connect() {
        stateLock.acquire();
        sendingListLock.acquire();
        try {
            sendInputList.add(getSendMessage(false, false, false, true, curSendSeqNo, new byte[0]));
        } catch (MalformedPacketException e) {
            sendingListLock.release();
            stateLock.release();
            return null;
        }
        sendingListLock.release();
        this.state = SocketState.SYN_SENT;
        while (this.state != SocketState.ESTABLISHED) {
            waitOpen.sleep();
        }
        stateLock.release();
        return new File();
    }

    protected void recData() {
        while (true) {
            try {
                SocketMessage message = rec();
                //for syn package, in closed state
                if (message.syn && !message.ack) {
                    stateLock.acquire();
                    dstLink = message.message.packet.srcLink;
                    dstPort = message.message.srcPort;
                    curSeqNo = message.seqNo;
                    curRecSeqNo = curSeqNo;
                    curSendSeqNo = curSeqNo;
                    send(false, false, true, true, curSeqNo, new byte[0]);
                    state = SocketState.SYN_RCVD;
                    waitOpen.wake();
                    stateLock.release();
                    break;
                }
                //for syn ack, in syn_sent state
                if (message.syn && message.ack) {
                    stateLock.acquire();
                    state = SocketState.ESTABLISHED;
                    waitOpen.wake();
                    stateLock.release();
                    break;
                }
                //for stp package, in establish state
                if (message.stp) {
                    stateLock.acquire();
                    stpSeqNo = message.seqNo;
                    state = SocketState.STP_RCVD;
                    stateLock.release();
                    break;
                }
                //for stp_send state
                if (message.fin && !message.ack) {
                    stateLock.acquire();
                    send(true, false, true, false, message.seqNo, new byte[0]);
                    state = SocketState.CLOSED;
                    stateLock.release();
                    break;
                }
                //for Closing state
                if (message.fin && message.ack) {
                    stateLock.acquire();
                    state = SocketState.CLOSED;
                    stateLock.release();
                    break;
                }
                //data ack
                if (message.ack) {
                    sendingListLock.acquire();
                    if (sendingList.removeIf(new Predicate<SocketMessage>() {
                        @Override
                        public boolean test(SocketMessage m) {
                            return m.seqNo == message.seqNo;
                        }
                    })) {
                        sendingListNotFull.wake();
                    }
                    if (sendingList.isEmpty()) {
                        sendingBusy.wake();
                    }
                    sendingListLock.release();
                    break;
                }
                //drop illegal package
                if (!message.isData()) {
                    break;
                }

                //receive
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
                    sendDataResp(message.seqNo);
                } else if (message.seqNo > curRecSeqNo) {
                    recListLock.acquire();
                    recOutOfOrderList.add(message);
                    recListLock.release();
                    sendDataResp(message.seqNo);
                }
                recListLock.acquire();
                if (curRecSeqNo == stpSeqNo) {
                    recBusy.wake();
                }
                recListLock.release();


            } catch (MalformedPacketException e) {
            }
        }
    }

    protected void sendData() {
        while (true) {
            sendingListLock.acquire();
            while (sendInputList.isEmpty()) {
                sendingListNotEmpty.sleep();
            }
            SocketMessage message = sendInputList.removeFirst();
            while (sendingList.size() >= dataWinSize) {
                sendingListNotFull.sleep();
            }
            send(message);
            sendingList.add(message);
            watchDogEn.wake();
            sendingListLock.release();
            new KThread(new Runnable() {
                @Override
                public void run() {
                    timeout(message);
                }
            }).fork();
        }
    }

    //timeout kernal thread for every package, if ack is received, package should remove from sending list, then when timeout, nothing to do.
    //otherwise, move package from sending list to head of input list waiting for resend, this will trigger another timeout thread.
    //if resend in timeout thread, it will be complicate to handle fail-again.
    protected void timeout(SocketMessage message) {
        ThreadedKernel.alarm.waitUntil(sendingTimeout);
        sendingListLock.acquire();
        if (sendingList.contains(message)) {
            sendInputList.addFirst(message);
            sendingList.remove(message);
            sendingListNotFull.wake();
            sendingListNotEmpty.wake();
        }
        sendingListLock.release();
    }

    protected SocketMessage rec() throws MalformedPacketException {
        SocketMessage message = new SocketMessage();
        message.recMailMessage(NetKernel.postOffice.receive(this.srcPort));
        return message;
    }

    protected SocketMessage getSendMessage(boolean fin, boolean stp, boolean ack, boolean syn, int seqNo, byte[] contents) throws MalformedPacketException {
        SocketMessage message = new SocketMessage(fin, stp, ack, syn, seqNo, contents);
        message.sendMailMessage(mailHeader());
        return message;
    }

    protected void send(boolean fin, boolean stp, boolean ack, boolean syn, int seqNo, byte[] contents) throws MalformedPacketException {
        send(getSendMessage(fin, stp, ack, syn, seqNo, contents));
    }

    protected void send(SocketMessage message) {
        NetKernel.postOffice.send(message.message);
    }


    protected void sendDataResp(int seqNo) throws MalformedPacketException {
        send(false, false, true, false, seqNo, new byte[0]);
    }

    protected MailMessage mailHeader() throws MalformedPacketException {
        return new MailMessage(dstLink, dstPort, srcLink, srcPort, new byte[0]);
    }

    protected class File extends OpenFile {
        File() {
            super(null, "SocketOld Sever File");
        }

        public void close() {
            stateLock.acquire();
            if (state == SocketState.ESTABLISHED) {
                sendingListLock.acquire();
                while (!sendInputList.isEmpty() || !sendingList.isEmpty()) {
                    sendingBusy.sleep();
                }

                try {
                    sendInputList.add(getSendMessage(false, true, false, false, curSendSeqNo, new byte[0]));
                } catch (MalformedPacketException e) {
                    sendingListLock.release();
                    stateLock.release();
                    return;
                }
                sendingListLock.release();
                state = SocketState.STP_SENT;
            } else if (state == SocketState.STP_RCVD) {
                sendingListLock.acquire();
                while (!sendInputList.isEmpty() || !sendingList.isEmpty()) {
                    sendingBusy.sleep();
                }
                sendingListLock.release();

                recListLock.acquire();
                while (curRecSeqNo != stpSeqNo) {
                    recBusy.sleep();
                }
                recListLock.release();
                stateLock.acquire();
                try {
                    send(true, false, false, false, stpSeqNo + 1, new byte[0]);
                } catch (MalformedPacketException e) {

                }
                state = SocketState.CLOSING;
                stateLock.release();
            }
            while (state != SocketState.CLOSED) {
                waitClose.sleep();
            }
            stateLock.release();
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
            if (state != SocketState.ESTABLISHED) {
                return -1;
            }
            sendingListLock.acquire();
            int amount = 0;
            int pos = offset;
            while (amount < length) {
                int _amount = Math.min(SocketMessage.maxContentsLength, length - amount);
                byte[] content = new byte[_amount];
                System.arraycopy(buf, pos, content, 0, _amount);
                try {
                    sendInputList.add(getSendMessage(false, false, false, false, curSendSeqNo, content));
                } catch (MalformedPacketException e) {
                    sendingListNotEmpty.wake();
                    sendingListLock.release();
                    return amount;
                }
                curSendSeqNo++;
                amount += _amount;
                pos += _amount;
            }
            sendingListNotEmpty.wake();
            sendingListLock.release();
            return amount;
        }

    }
}
