package nachos.network;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.MalformedPacketException;
import nachos.machine.OpenFile;
import nachos.threads.Condition2;

public class SocketServer extends Socket {

    private Condition2 waitAcc;


    public SocketServer(int srcPort) {
        super(srcPort);
        waitAcc = new Condition2(lock);
    }

    public OpenFile accept() {
        lock.acquire();
        while (this.state != SocketState.ESTABLISHED) {
            waitAcc.sleep();
        }
        lock.release();
        return new File();
    }

    protected void handleClosed() {
        lock.acquire();
        try {
            SocketMessage message = rec();
            dstLink = message.message.packet.srcLink;
            dstPort = message.message.srcPort;
            curSeqNo = message.seqNo;
            curRecSeqNo = curSeqNo;
            curSendSeqNo = curSeqNo;
            if (message.syn) {
                state = SocketState.SYN_RCVD;
            }
        } catch (MalformedPacketException e) {
            Lib.assertNotReached("receive bad package!");
        }
        lock.release();
    }

    protected void handleSynSent() {

    }

    protected void handleSynRcvd() {
        lock.acquire();
        try {
            send(false, false, true, true, curSeqNo, new byte[0]);
        } catch (MalformedPacketException e) {
            Lib.assertNotReached("send bad package!");
            return;
        }
        state = SocketState.ESTABLISHED;
        waitAcc.wake();
        lock.release();
    }

    protected void handleEstablished() {
        while (true) {

        }
    }

    protected void handleStpSent() {

    }

    protected void handleStpRcvd() {

    }

    protected void handleClosing() {

    }


}
