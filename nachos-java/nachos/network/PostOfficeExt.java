package nachos.network;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.MalformedPacketException;
import nachos.machine.Packet;

public class PostOfficeExt extends PostOffice {
    private Runnable[] receiveHandlers;

    public PostOfficeExt() {
        super();
        receiveHandlers = new Runnable[MailMessage.portLimit];
    }

    public void setReceiveHandler(int port, Runnable handler) {
        Lib.assertTrue(port >= 0 && port < receiveHandlers.length);
        receiveHandlers[port] = handler;
    }

    protected void postalDelivery() {
        while (true) {
            messageReceived.P();

            Packet p = Machine.networkLink().receive();

            MailMessage mail;

            try {
                mail = new MailMessage(p);
            } catch (MalformedPacketException e) {
                continue;
            }

            if (Lib.test(dbgNet))
                System.out.println("delivering mail to port " + mail.dstPort
                        + ": " + mail);

            // atomically add message to the mailbox and wake a waiting thread
            queues[mail.dstPort].add(mail);
            //async callback
            if (receiveHandlers[mail.dstPort] != null) {
                receiveHandlers[mail.dstPort].run();
            }
        }
    }
}
