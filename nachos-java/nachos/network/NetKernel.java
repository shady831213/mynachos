package nachos.network;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import nachos.network.*;

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
        byte[] data = Lib.bytesFromInt(0x5a5a);
        file.write(data, 0, data.length);
        file.close();
        while (file.read(data, 0, data.length) <= 0) {
        }


        long endTime = Machine.timer().getTime();

        System.out.println("time=" + (endTime - startTime) + " ticks;Client get data " + Integer.toHexString(Lib.bytesToInt(data, 0)));
    }

    private void pingServer() {
        Socket socket = new Socket(postOffice);
        OpenFile file = socket.accept(0);
        System.out.println("accept @ port " + 0 + " from " + socket.dstPort);
        byte[] data;
        data = new byte[4];
        while (file.read(data, 0, data.length) <= 0) {
        }
        System.out.println("Server get data " + Integer.toHexString(Lib.bytesToInt(data, 0)));
        data = Lib.bytesFromInt(0xa5a5);
        file.write(data, 0, data.length);
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
