package nachos.network;

import nachos.machine.*;
import nachos.threads.*;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.function.Predicate;

public abstract class Socket {
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
    private int sendingTimeout;
    //state machine

    protected Lock lock;
    protected SocketState state;

    //state vars
    protected int srcPort;
    protected int srcLink;
    protected int dstPort;
    protected int dstLink;
    protected int curSeqNo;


    //for receive
    protected int curRecSeqNo;
    SynchList recInOrderList;
    PriorityQueue<SocketMessage> recOutOfOrderList;

    //for send
    protected int curSendSeqNo;
    SynchList sendInputList;
    Lock sendingListLock;
    Condition2 watchDogEn;
    Condition2 sendingListNotFull;
    LinkedList<SocketMessage> sendingList;

    public Socket(int srcPort) {
        this.srcPort = srcPort;
        this.srcLink = Machine.networkLink().getLinkAddress();
        this.state = SocketState.CLOSED;
        lock = new Lock();
        recOutOfOrderList = new PriorityQueue<>(dataWinSize, new Comparator<SocketMessage>() {
            @Override
            public int compare(SocketMessage t1, SocketMessage t2) {
                return t1.seqNo - t2.seqNo;
            }
        });
        recInOrderList = new SynchList();
        sendInputList = new SynchList();
        sendingList = new LinkedList<>();
        sendingListLock = new Lock();
        watchDogEn = new Condition2(sendingListLock);
        sendingListNotFull = new Condition2(sendingListLock);
        sendingTimeout = 20000;
    }

    abstract protected void handleClosed();

    abstract protected void handleSynSent();

    abstract protected void handleSynRcvd();

    abstract protected void handleEstablished();

    abstract protected void handleStpSent();

    abstract protected void handleStpRcvd();

    abstract protected void handleClosing();

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

    protected void recData() {
        while (true) {
            while (!recOutOfOrderList.isEmpty()) {
                SocketMessage m = recOutOfOrderList.peek();
                if (m.seqNo != curRecSeqNo) {
                    break;
                }
                recInOrderList.add(m);
                curRecSeqNo++;
                recOutOfOrderList.remove(m);
            }
            try {
                SocketMessage message = rec();
                if (!message.isData()) {
                    break;
                }
                //ack
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
                    sendingListLock.release();
                } else {
                    //receive
                    if (message.seqNo == curRecSeqNo) {
                        recInOrderList.add(message);
                        sendDataResp(message.seqNo);
                        curRecSeqNo++;
                    } else if (message.seqNo > curRecSeqNo) {
                        recOutOfOrderList.add(message);
                        sendDataResp(message.seqNo);
                    }
                }

            } catch (MalformedPacketException e) {
            }
        }
    }

    protected void sendData() {
        while (true) {
            SocketMessage message = (SocketMessage) sendInputList.removeFirst();
            sendingListLock.acquire();
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
    //otherwise, resend.
    protected void timeout(SocketMessage message) {
        ThreadedKernel.alarm.waitUntil(sendingTimeout);
        sendingListLock.acquire();
        if (sendingList.contains(message)) {
            send(message);
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
            super(null, "Socket Sever File");
        }

        public void close() {

        }

        public int read(byte[] buf, int offset, int length) {
            int amount = 0;
            int pos = offset;
            while (amount < length) {
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
            return amount;
        }

        public int write(byte[] buf, int offset, int length) {
            int amount = 0;
            int pos = offset;
            while (amount < length) {
                int _amount = Math.min(SocketMessage.maxContentsLength, length - amount);
                byte[] content = new byte[_amount];
                System.arraycopy(buf, pos, content, 0, _amount);
                try {
                    sendInputList.add(getSendMessage(false, false, false, false, curSendSeqNo, content));
                } catch (MalformedPacketException e) {
                    return amount;
                }
                curSendSeqNo++;
                amount += _amount;
                pos += _amount;
            }
            return amount;
        }

    }
}
