package test.nachos.threads;

import nachos.threads.KThread;
import nachos.threads.ThreadedKernel;

import static org.junit.Assert.*;

public class TestKernal extends ThreadedKernel {

    private void test() {
        final int forkNum = 5;
        class forkTestJob implements Runnable {
            forkTestJob() {
                this.count = 0;
            }

            public void run() {
                for (int i = 0; i < forkNum; i++) {
                    count++;
                    KThread.yield();
                }
            }

            public int getCount() {
                return count;
            }

            private int count;
        }

        int forkCnt = 0;
        forkTestJob j1 = new forkTestJob();
        KThread t1 = new KThread(j1);
        t1.fork();
        t1.join();
        assertEquals(forkNum, j1.getCount());

        forkTestJob j2 = new forkTestJob();
        KThread t2 = new KThread(j2);
        forkTestJob j3 = new forkTestJob();
        KThread t3 = new KThread(j3);
        t2.fork();
        t3.fork();
        assertNotEquals(forkNum, j2.getCount());
        assertNotEquals(forkNum, j3.getCount());
        t2.join();
        assertEquals(forkNum, j2.getCount());
        t3.join();
        assertEquals(forkNum, j3.getCount());
    }

    public void run() {
        test();
    }

}