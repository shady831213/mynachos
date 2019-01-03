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
        Lib.TestSuite ts = new Lib.TestSuite();

        //basic socket test
        ts.addTest(new Lib.Test("basic_socket_test", new Runnable() {
            private void ping(int dstPort) {
                int srcLink = Machine.networkLink().getLinkAddress();

                Socket socket = new Socket(postOffice);
                OpenFile file = socket.connect(srcLink, dstPort);
                byte[] tdata, rdata;
                tdata = new byte[456];
                for (int i = 0; i < tdata.length; i++) {
                    tdata[i] = (byte) (i % 255);
                }
                rdata = new byte[777];

                file.write(tdata, 0, tdata.length);
                file.close();
                int rcnt = 0;
                while (rcnt != rdata.length) {
                    rcnt += file.read(rdata, rcnt, rdata.length);
                }

                for (int i = 0; i < rdata.length; i++) {
                    Lib.assertTrue(rdata[i] == (byte) (255 - (i % 255)), "Client data " + i + " expect=" + (byte) (255 - (i % 255)) + " but actaul=" + rdata[i]);
                }
            }

            private void pingServer() {
                Socket socket = new Socket(postOffice);
                OpenFile file = socket.accept(0);
                byte[] tdata, rdata;
                rdata = new byte[456];
                int rcnt = 0;
                while (rcnt != rdata.length) {
                    rcnt += file.read(rdata, rcnt, rdata.length);
                }
                for (int i = 0; i < rdata.length; i++) {
                    Lib.assertTrue(rdata[i] == (byte) (i % 255), "Server data " + i + " expect=" + (byte) (255 - (i % 255)) + " but actaul=" + rdata[i]);
                }

                tdata = new byte[777];
                for (int i = 0; i < tdata.length; i++) {
                    tdata[i] = (byte) (255 - (i % 255));
                }
                file.write(tdata, 0, tdata.length);
                file.close();
            }

            @Override
            public void run() {
                KThread serverThread = new KThread(new Runnable() {
                    public void run() {
                        pingServer();
                    }
                });

                serverThread.fork();

                console.readByte(true);

                // ping this machine first
                ping(0);
            }
        }));
        ts.run();

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
