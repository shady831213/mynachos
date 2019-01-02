package nachos.network;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import nachos.network.*;

import java.lang.reflect.Array;

/**
 * A kernel with network support.
 */
public class NetKernel extends VMKernel {
    /**
     * Allocate a new networking kernel.
     */
    public NetKernel() {
        super();
    }

    /**
     * Initialize this kernel.
     */
    public void initialize(String[] args) {
        super.initialize(args);

        postOffice = new SocketPostOffice(new PostOfficeExt());
    }

    /**
     * Test the network. Create a server thread that listens for pings on port
     * 1 and sends replies. Then ping one or two hosts. Note that this test
     * assumes that the network is reliable (i.e. that the network's
     * reliability is 1.0).
     */
    public void selfTest() {
        super.selfTest();

        KThread serverThread = new KThread(new Runnable() {
            public void run() {
                pingServer();
            }
        });

        serverThread.fork();

        System.out.println("Press any key to start the network test...");
        console.readByte(true);

        // ping this machine first
        ping(0);

        // if we're 0 or 1, ping the opposite
//        if (local <= 1)
//            ping(1 - local);
    }

    private void ping(int dstPort) {
        int srcLink = Machine.networkLink().getLinkAddress();


        long startTime = Machine.timer().getTime();
        Socket socket = new Socket(postOffice);
        OpenFile file = socket.connect(srcLink, dstPort);
        System.out.println("PING port " + dstPort + " from " + socket.srcPort);
        byte[] tdata, rdata;
        tdata = new byte[200];
        for (int i = 0; i < tdata.length; i++) {
            tdata[i] = (byte) (i % 255);
        }
        rdata = new byte[295];

        file.write(tdata, 0, tdata.length);
        file.close();
        int rcnt = 0;
        while (rcnt != rdata.length) {
            rcnt += file.read(rdata, rcnt, rdata.length);
            System.out.println("Client get " + rcnt + " data");
        }

        long endTime = Machine.timer().getTime();

        System.out.println("time=" + (endTime - startTime) + " ticks");
        System.out.println("Client get data :");
        for (int i = 0; i < rdata.length; i++) {
            System.out.println("Client rdata " + i + " = " + rdata[i]);
        }
    }

    private void pingServer() {
        Socket socket = new Socket(postOffice);
        OpenFile file = socket.accept(0);
        System.out.println("accept @ port " + 0 + " from " + socket.dstPort);
        byte[] tdata, rdata;
        rdata = new byte[200];
        int rcnt = 0;
        while (rcnt != rdata.length) {
            rcnt += file.read(rdata, rcnt, rdata.length);
            System.out.println("Server get " + rcnt + " data");
        }
        System.out.println("Server get data :");
        for (int i = 0; i < rdata.length; i++) {
            System.out.println("Server rdata " + i + " = " + rdata[i]);
        }

        tdata = new byte[295];
        for (int i = 0; i < tdata.length; i++) {
            tdata[i] = (byte) (255 - (i % 255));
        }
        file.write(tdata, 0, tdata.length);
        file.close();
    }

    /**
     * Start running user programs.
     */
    public void run() {
        super.run();
    }

    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
        super.terminate();
    }

    public static SocketPostOffice postOffice;

    // dummy variables to make javac smarter
    private static NetProcess dummy1 = null;
}
