package nachos.network;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.MalformedPacketException;
import nachos.threads.Lock;

import java.util.Iterator;
import java.util.LinkedList;

public class SocketPostOffice {
    final private PostOfficeExt postOffice;
    private Lock[] SocketListLock;
    private LinkedList<Socket>[] Sockets;
    private static final char dbgSocket = 's';

    SocketPostOffice(PostOfficeExt postOffice) {
        this.postOffice = postOffice;
        SocketListLock = new Lock[MailMessage.portLimit];
        Sockets = new LinkedList[MailMessage.portLimit];
        for (int i = 0; i < Sockets.length; i++) {
            Sockets[i] = new LinkedList<>();
            SocketListLock[i] = new Lock();
            final int port = i;
            postOffice.setReceiveHandler(port, new Runnable() {
                @Override
                public void run() {
                    dispatch(postOffice.receive(port));
                }
            });
        }
    }

    public boolean bind(Socket socket) {
        for (int port = 0; port < Sockets.length; port++) {
            SocketListLock[port].acquire();
            if (Sockets[port].isEmpty()) {
                socket.srcPort = port;
                Sockets[port].add(socket);
                SocketListLock[port].release();
                return true;
            }
            SocketListLock[port].release();
        }
        return false;
    }

    public void bind(Socket socket, int port) {
        SocketListLock[port].acquire();
        socket.srcPort = port;
        Sockets[port].add(socket);
        SocketListLock[port].release();
    }


    public void unbind(Socket socket) {
        SocketListLock[socket.srcPort].acquire();
        Sockets[socket.srcPort].remove(socket);
        SocketListLock[socket.srcPort].release();
    }

    private void dispatch(MailMessage mail) {
        SocketMessage message = new SocketMessage();
        try {
            message.recMailMessage(mail);
        } catch (MalformedPacketException e) {
            Lib.assertNotReached("get a bad mail!");
        }
        SocketListLock[mail.dstPort].acquire();
//        if (message.syn) {
//            System.out.println("get syn from port " + message.message.srcPort + " to port " + message.message.dstPort);
//        }
        for (Iterator i = Sockets[mail.dstPort].iterator(); i.hasNext(); ) {
            if (((Socket) i.next()).receive(message)) {
                Lib.debug(dbgSocket, "valid message!");
                SocketListLock[mail.dstPort].release();
                return;
            }
        }
        SocketListLock[mail.dstPort].release();
    }

    public void send(Socket socket, SocketMessage message) {
        MailMessage mailHeader;
        try {
            mailHeader = new MailMessage(socket.dstLink, socket.dstPort, Machine.networkLink().getLinkAddress(), socket.srcPort, new byte[0]);
            message.sendMailMessage(mailHeader);
        } catch (MalformedPacketException e) {
            System.out.println(socket.dstLink);
            System.out.println(socket.dstPort);
            System.out.println(Machine.networkLink().getLinkAddress());
            System.out.println(socket.srcPort);
            Lib.assertNotReached("get a bad mail!");
        }
        this.postOffice.send(message.message);
    }

}
