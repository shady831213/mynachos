package nachos.threads;

import nachos.machine.*;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;

/**
 * A KThread is a thread that can be used to execute Nachos kernel code. Nachos
 * allows multiple threads to run concurrently.
 * <p>
 * To create a new thread of execution, first declare a class that implements
 * the <tt>Runnable</tt> interface. That class then implements the <tt>run</tt>
 * method. An instance of the class can then be allocated, passed as an
 * argument when creating <tt>KThread</tt>, and forked. For example, a thread
 * that computes pi could be written as follows:
 *
 * <p><blockquote><pre>
 * class PiRun implements Runnable {
 *     public void run() {
 *         // compute pi
 *         ...
 *     }
 * }
 * </pre></blockquote>
 * <p>The following code would then create a thread and start it running:
 *
 * <p><blockquote><pre>
 * PiRun p = new PiRun();
 * new KThread(p).fork();
 * </pre></blockquote>
 */
public class KThread {
    /**
     * Get the current thread.
     *
     * @return the current thread.
     */
    public static KThread currentThread() {
        Lib.assertTrue(currentThread != null);
        return currentThread;
    }

    /**
     * Allocate a new <tt>KThread</tt>. If this is the first <tt>KThread</tt>,
     * create an idle thread as well.
     */
    public KThread() {
        if (currentThread != null) {
            tcb = new TCB();
        } else {
            readyQueue = ThreadedKernel.scheduler.newThreadQueue(false);
            readyQueue.acquire(this);

            currentThread = this;
            tcb = TCB.currentTCB();
            name = "main";
            restoreState();

            createIdleThread();
        }
    }

    /**
     * Allocate a new KThread.
     *
     * @param target the object whose <tt>run</tt> method is called.
     */
    public KThread(Runnable target) {
        this();
        this.target = target;
    }

    /**
     * Set the target of this thread.
     *
     * @param target the object whose <tt>run</tt> method is called.
     * @return this thread.
     */
    public KThread setTarget(Runnable target) {
        Lib.assertTrue(status == statusNew);

        this.target = target;
        return this;
    }

    /**
     * Set the name of this thread. This name is used for debugging purposes
     * only.
     *
     * @param name the name to give to this thread.
     * @return this thread.
     */
    public KThread setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Get the name of this thread. This name is used for debugging purposes
     * only.
     *
     * @return the name given to this thread.
     */
    public String getName() {
        return name;
    }

    /**
     * Get the full name of this thread. This includes its name along with its
     * numerical ID. This name is used for debugging purposes only.
     *
     * @return the full name given to this thread.
     */
    public String toString() {
        return (name + " (#" + id + ")");
    }

    /**
     * Deterministically and consistently compare this thread to another
     * thread.
     */
    public int compareTo(Object o) {
        KThread thread = (KThread) o;

        if (id < thread.id)
            return -1;
        else if (id > thread.id)
            return 1;
        else
            return 0;
    }

    /**
     * Causes this thread to begin execution. The result is that two threads
     * are running concurrently: the current thread (which returns from the
     * call to the <tt>fork</tt> method) and the other thread (which executes
     * its target's <tt>run</tt> method).
     */
    public void fork() {
        Lib.assertTrue(status == statusNew);
        Lib.assertTrue(target != null);

        Lib.debug(dbgThread,
                "Forking thread: " + toString() + " Runnable: " + target);

        boolean intStatus = Machine.interrupt().disable();

        tcb.start(new Runnable() {
            public void run() {
                runThread();
            }
        });

        ready();

        Machine.interrupt().restore(intStatus);
    }

    private void runThread() {
        begin();
        target.run();
        finish();
    }

    private void begin() {
        Lib.debug(dbgThread, "Beginning thread: " + toString());

        Lib.assertTrue(this == currentThread);

        restoreState();

        Machine.interrupt().enable();
    }

    /**
     * Finish the current thread and schedule it to be destroyed when it is
     * safe to do so. This method is automatically called when a thread's
     * <tt>run</tt> method returns, but it may also be called directly.
     * <p>
     * The current thread cannot be immediately destroyed because its stack and
     * other execution state are still in use. Instead, this thread will be
     * destroyed automatically by the next thread to run, when it is safe to
     * delete this thread.
     */
    public static void finish() {
        Lib.debug(dbgThread, "Finishing thread: " + currentThread.toString());

        Machine.interrupt().disable();

        Machine.autoGrader().finishingCurrentThread();

        Lib.assertTrue(toBeDestroyed == null);
        toBeDestroyed = currentThread;

        currentThread.status = statusFinished;
        //notify join
        currentThread.finishMonLock.acquire();
        currentThread.finishMonCon.wake();
        currentThread.finishMonLock.release();
        sleep();
    }

    /**
     * Relinquish the CPU if any other thread is ready to run. If so, put the
     * current thread on the ready queue, so that it will eventually be
     * rescheuled.
     *
     * <p>
     * Returns immediately if no other thread is ready to run. Otherwise
     * returns when the current thread is chosen to run again by
     * <tt>readyQueue.nextThread()</tt>.
     *
     * <p>
     * Interrupts are disabled, so that the current thread can atomically add
     * itself to the ready queue and switch to the next thread. On return,
     * restores interrupts to the previous state, in case <tt>yield()</tt> was
     * called with interrupts disabled.
     */
    public static void yield() {
        Lib.debug(dbgThread, "Yielding thread: " + currentThread.toString());

        Lib.assertTrue(currentThread.status == statusRunning);

        boolean intStatus = Machine.interrupt().disable();

        currentThread.ready();

        runNextThread();

        Machine.interrupt().restore(intStatus);
    }

    /**
     * Relinquish the CPU, because the current thread has either finished or it
     * is blocked. This thread must be the current thread.
     *
     * <p>
     * If the current thread is blocked (on a synchronization primitive, i.e.
     * a <tt>Semaphore</tt>, <tt>Lock</tt>, or <tt>Condition</tt>), eventually
     * some thread will wake this thread up, putting it back on the ready queue
     * so that it can be rescheduled. Otherwise, <tt>finish()</tt> should have
     * scheduled this thread to be destroyed by the next thread to run.
     */
    public static void sleep() {
        Lib.debug(dbgThread, "Sleeping thread: " + currentThread.toString());

        Lib.assertTrue(Machine.interrupt().disabled());

        if (currentThread.status != statusFinished)
            currentThread.status = statusBlocked;

        runNextThread();
    }

    /**
     * Moves this thread to the ready state and adds this to the scheduler's
     * ready queue.
     */
    public void ready() {
        Lib.debug(dbgThread, "Ready thread: " + toString());

        Lib.assertTrue(Machine.interrupt().disabled());
        Lib.assertTrue(status != statusReady);

        status = statusReady;
        if (this != idleThread)
            readyQueue.waitForAccess(this);

        Machine.autoGrader().readyThread(this);
    }

    /**
     * Waits for this thread to finish. If this thread is already finished,
     * return immediately. This method must only be called once; the second
     * call is not guaranteed to return. This thread must not be the current
     * thread.
     */
    private final Lock finishMonLock = new Lock();
    private final Condition2 finishMonCon = new Condition2(finishMonLock);

    public void join() {
        Lib.debug(dbgThread, "Joining to thread: " + toString());

        Lib.assertTrue(this != currentThread);
        if (status == statusFinished) return;
        finishMonLock.acquire();
        while (status != statusFinished) {
            finishMonCon.sleep();
        }
        finishMonLock.release();
        Lib.debug(dbgThread, "join: " + currentThread.toString());
    }

    /**
     * Create the idle thread. Whenever there are no threads ready to be run,
     * and <tt>runNextThread()</tt> is called, it will run the idle thread. The
     * idle thread must never block, and it will only be allowed to run when
     * all other threads are blocked.
     *
     * <p>
     * Note that <tt>ready()</tt> never adds the idle thread to the ready set.
     */
    private static void createIdleThread() {
        Lib.assertTrue(idleThread == null);

        idleThread = new KThread(new Runnable() {
            public void run() {
                while (true) yield();
            }
        });
        idleThread.setName("idle");

        Machine.autoGrader().setIdleThread(idleThread);

        idleThread.fork();
    }

    /**
     * Determine the next thread to run, then dispatch the CPU to the thread
     * using <tt>run()</tt>.
     */
    private static void runNextThread() {
        KThread nextThread = readyQueue.nextThread();
        if (nextThread == null)
            nextThread = idleThread;

        nextThread.run();
    }

    /**
     * Dispatch the CPU to this thread. Save the state of the current thread,
     * switch to the new thread by calling <tt>TCB.contextSwitch()</tt>, and
     * load the state of the new thread. The new thread becomes the current
     * thread.
     *
     * <p>
     * If the new thread and the old thread are the same, this method must
     * still call <tt>saveState()</tt>, <tt>contextSwitch()</tt>, and
     * <tt>restoreState()</tt>.
     *
     * <p>
     * The state of the previously running thread must already have been
     * changed from running to blocked or ready (depending on whether the
     * thread is sleeping or yielding).
     *
     * @param finishing <tt>true</tt> if the current thread is
     *                  finished, and should be destroyed by the new
     *                  thread.
     */
    private void run() {
        Lib.assertTrue(Machine.interrupt().disabled());

        Machine.yield();

        currentThread.saveState();

        Lib.debug(dbgThread, "Switching from: " + currentThread.toString()
                + " to: " + toString());

        currentThread = this;

        tcb.contextSwitch();

        currentThread.restoreState();
    }

    /**
     * Prepare this thread to be run. Set <tt>status</tt> to
     * <tt>statusRunning</tt> and check <tt>toBeDestroyed</tt>.
     */
    protected void restoreState() {
        Lib.debug(dbgThread, "Running thread: " + currentThread.toString());

        Lib.assertTrue(Machine.interrupt().disabled());
        Lib.assertTrue(this == currentThread);
        Lib.assertTrue(tcb == TCB.currentTCB());

        Machine.autoGrader().runningThread(this);

        status = statusRunning;

        if (toBeDestroyed != null) {
            toBeDestroyed.tcb.destroy();
            toBeDestroyed.tcb = null;
            toBeDestroyed = null;
        }
    }

    /**
     * Prepare this thread to give up the processor. Kernel threads do not
     * need to do anything here.
     */
    protected void saveState() {
        Lib.assertTrue(Machine.interrupt().disabled());
        Lib.assertTrue(this == currentThread);
    }

    private static class PingTest implements Runnable {
        PingTest(int which) {
            this.which = which;
            this.cnt = 0;
        }

        public void run() {
            for (int i = 0; i < 5; i++) {
                Lib.debug(Lib.dbgTest, "*** thread " + which + " looped "
                        + i + " times");
                cnt++;
                currentThread.yield();
            }
        }

        private int which;
        public int cnt;
    }

    /**
     * Tests whether this module is working.
     */
    public static void selfTest() {
        Lib.debug(dbgThread, "Enter KThread.selfTest");
        Lib.TestSuite ts = new Lib.TestSuite();
        //join test
        ts.addTest(new Lib.Test("join_test", new Runnable() {
            @Override
            public void run() {
                PingTest j1 = new PingTest(1);
                KThread testThread1 = new KThread(j1);
                testThread1.setName("forked thread").fork();
                new PingTest(0).run();
                testThread1.join();
                Lib.assertTrue(j1.cnt == 5);
                new PingTest(2).run();
            }
        }));

        //waitutil test
        ts.addTest(new Lib.Test("waituntil_test", new Runnable() {
            @Override
            public void run() {
                class waitUtilJob implements Runnable {
                    private long waitTime;
                    private String jobName;
                    private long endTime;
                    private long startTime;

                    waitUtilJob(String jobName, long waitTime) {
                        this.jobName = jobName;
                        this.waitTime = waitTime;
                    }

                    @Override
                    public void run() {
                        this.startTime = Machine.timer().getTime();
                        Lib.debug(Lib.dbgTest, jobName + " time before waitutil : " + this.startTime);
                        ThreadedKernel.alarm.waitUntil(waitTime);
                        this.endTime = Machine.timer().getTime();
                        Lib.debug(Lib.dbgTest, jobName + " time after waitutil : " + this.endTime);
                        check();
                    }

                    private void check() {
                        Lib.assertTrue(this.endTime >= this.startTime + this.waitTime && this.endTime <= this.startTime + this.waitTime + Stats.TimerTicks);
                    }
                }
                waitUtilJob j1 = new waitUtilJob("j1", 2000);
                waitUtilJob j2 = new waitUtilJob("j2", 1000);
                waitUtilJob j3 = new waitUtilJob("j3", 1500);
                KThread t1 = new KThread(j1);
                KThread t2 = new KThread(j2);
                KThread t3 = new KThread(j3);
                t1.fork();
                t2.fork();
                t3.fork();
                t1.join();
                t2.join();
                t3.join();
            }
        }));
        //communicator test
        ts.addTest(new Lib.Test("communicator_test", new Runnable() {
            int speakCnt, listenCnt;

            @Override
            public void run() {
                Communicator c = new Communicator();
                class Speaker implements Runnable {
                    private Communicator c;
                    private String jobName;
                    private int word;

                    Speaker(String jobName, Communicator c, int word) {
                        this.jobName = jobName;
                        this.c = c;
                        this.word = word;
                    }

                    public void run() {
                        for (int i = 0; i < 2; i++) {
                            ThreadedKernel.alarm.waitUntil(Lib.random(100));
                            Lib.debug(Lib.dbgTest, jobName + " begin speak " + this.word + " @ " + Machine.timer().getTime());
                            c.speak(word);
                            speakCnt++;
                            Lib.debug(Lib.dbgTest, jobName + " speak done " + this.word + " @ " + Machine.timer().getTime());
                        }
                    }
                }
                class Listner implements Runnable {
                    private Communicator c;
                    private String jobName;
                    private int word;

                    Listner(String jobName, Communicator c) {
                        this.jobName = jobName;
                        this.c = c;
                    }

                    public void run() {
                        for (int i = 0; i < 2; i++) {
                            ThreadedKernel.alarm.waitUntil(Lib.random(100));
                            Lib.debug(Lib.dbgTest, jobName + " begin listen " + this.word + " @ " + Machine.timer().getTime());
                            this.word = c.listen();
                            Lib.debug(Lib.dbgTest, jobName + " listen done " + this.word + " @ " + Machine.timer().getTime());
                            listenCnt++;
                        }
                    }
                }
                Speaker s1 = new Speaker("s1", c, 1), s2 = new Speaker("s2", c, 2), s3 = new Speaker("s3", c, 3);
                Listner l1 = new Listner("l1", c), l2 = new Listner("l2", c), l3 = new Listner("l3", c);
                KThread ts1 = new KThread(s1), ts2 = new KThread(s2), ts3 = new KThread(s3), tl1 = new KThread(l1), tl2 = new KThread(l2), tl3 = new KThread(l3);
                ts1.fork();
                tl1.fork();
                ts2.fork();
                ts3.fork();
                tl2.fork();
                tl3.fork();
                ts1.join();
                tl1.join();
                ts2.join();
                ts3.join();
                tl2.join();
                tl3.join();
                Lib.debug(Lib.dbgTest, " speakCnt " + this.speakCnt);
                Lib.debug(Lib.dbgTest, " listenCnt " + this.listenCnt);
                Lib.assertTrue(speakCnt == listenCnt);
            }
        }));
        //thread queue priority test
        ts.addTest(new Lib.Test("PQ_priority_test", new Runnable() {
            @Override
            public void run() {
                ThreadedKernel.scheduler = (Scheduler) Lib.constructObject("nachos.threads.PriorityScheduler");
                ThreadQueue testQueue = ThreadedKernel.scheduler.newThreadQueue(false);
                KThread[] threads = new KThread[3];
                boolean intStatus = Machine.interrupt().disable();
                for (int i = 0; i < threads.length; i++) {
                    final int _i = i;
                    threads[_i] = new KThread(new Runnable() {
                        @Override
                        public void run() {
                            Lib.debug(Lib.dbgTest, "Run t" + (_i + 1));
                        }
                    });
                    threads[_i].setName("t" + (_i + 1));
                    ThreadedKernel.scheduler.setPriority(threads[_i], (_i + 1));
                    testQueue.waitForAccess(threads[_i]);
                }
                for (int i = 0; i < threads.length; i++) {
                    KThread _t = testQueue.nextThread();
                    Lib.debug(Lib.dbgTest, "get Thread " + _t.name);
                    Lib.assertTrue(_t.name.equals("t" + (threads.length - i)));
                }
                Machine.interrupt().restore(intStatus);
                String schedulerName = Config.getString("ThreadedKernel.scheduler");
                ThreadedKernel.scheduler = (Scheduler) Lib.constructObject(schedulerName);
            }
        }));
        //thread queue priority_equal test
        ts.addTest(new Lib.Test("PQ_priority_equal_test", new Runnable() {
            @Override
            public void run() {
                ThreadedKernel.scheduler = (Scheduler) Lib.constructObject("nachos.threads.PriorityScheduler");
                ThreadQueue testQueue = ThreadedKernel.scheduler.newThreadQueue(false);
                KThread[] threads = new KThread[3];
                boolean intStatus = Machine.interrupt().disable();
                for (int i = 0; i < threads.length; i++) {
                    final int _i = i;
                    threads[_i] = new KThread(new Runnable() {
                        @Override
                        public void run() {
                            Lib.debug(Lib.dbgTest, "Run t" + (_i + 1));
                        }
                    });
                    threads[_i].setName("t" + (_i + 1));
                    testQueue.waitForAccess(threads[_i]);
                }
                for (int i = 0; i < threads.length; i++) {
                    KThread _t = testQueue.nextThread();
                    Lib.debug(Lib.dbgTest, "get Thread " + _t.name);
                    Lib.assertTrue(_t.name.equals("t" + (i + 1)));
                }
                Machine.interrupt().restore(intStatus);
                String schedulerName = Config.getString("ThreadedKernel.scheduler");
                ThreadedKernel.scheduler = (Scheduler) Lib.constructObject(schedulerName);
            }
        }));
        //thread queue priority_modify test
        ts.addTest(new Lib.Test("PQ_priority_modify_test", new Runnable() {
            @Override
            public void run() {
                ThreadedKernel.scheduler = (Scheduler) Lib.constructObject("nachos.threads.PriorityScheduler");
                ThreadQueue testQueue = ThreadedKernel.scheduler.newThreadQueue(false);
                KThread[] threads = new KThread[3];
                boolean intStatus = Machine.interrupt().disable();
                for (int i = 0; i < threads.length; i++) {
                    final int _i = i;
                    threads[_i] = new KThread(new Runnable() {
                        @Override
                        public void run() {
                            Lib.debug(Lib.dbgTest, "Run t" + (_i + 1));
                        }
                    });
                    threads[_i].setName("t" + (_i + 1));
                    ThreadedKernel.scheduler.setPriority(threads[_i], (_i + 1));
                    testQueue.waitForAccess(threads[_i]);
                }
                ThreadedKernel.scheduler.setPriority(threads[0], 3);
                ThreadedKernel.scheduler.setPriority(threads[1], 4);
                KThread t1 = testQueue.nextThread();
                Lib.debug(Lib.dbgTest, "get Thread " + t1.name);
                Lib.assertTrue(t1.name.equals("t2"));
                KThread t2 = testQueue.nextThread();
                Lib.debug(Lib.dbgTest, "get Thread " + t2.name);
                Lib.assertTrue(t2.name.equals("t1"));
                KThread t3 = testQueue.nextThread();
                Lib.debug(Lib.dbgTest, "get Thread " + t3.name);
                Lib.assertTrue(t3.name.equals("t3"));
                Machine.interrupt().restore(intStatus);
                String schedulerName = Config.getString("ThreadedKernel.scheduler");
                ThreadedKernel.scheduler = (Scheduler) Lib.constructObject(schedulerName);
            }
        }));
        //thread queue priority_donate test
        ts.addTest(new Lib.Test("PQ_priority_donate_test", new Runnable() {
            @Override
            public void run() {
                ThreadedKernel.scheduler = (Scheduler) Lib.constructObject("nachos.threads.PriorityScheduler");
                ThreadQueue testQueue = ThreadedKernel.scheduler.newThreadQueue(false);
                ThreadQueue donateQueue = ThreadedKernel.scheduler.newThreadQueue(true);
                KThread[] threads = new KThread[3];
                boolean intStatus = Machine.interrupt().disable();

                KThread donateT1 = new KThread(new Runnable() {
                    @Override
                    public void run() {
                        Lib.debug(Lib.dbgTest, "Run t_donate1");
                    }
                });
                donateT1.setName("t_donate1");
                ThreadedKernel.scheduler.setPriority(donateT1, 4);
                donateQueue.waitForAccess(donateT1);

                KThread donateT2 = new KThread(new Runnable() {
                    @Override
                    public void run() {
                        Lib.debug(Lib.dbgTest, "Run t_donate2");
                    }
                });
                donateT2.setName("t_donate2");
                ThreadedKernel.scheduler.setPriority(donateT2, 0);
                donateQueue.acquire(donateT2);

                for (int i = 0; i < threads.length; i++) {
                    final int _i = i;
                    threads[_i] = new KThread(new Runnable() {
                        @Override
                        public void run() {
                            Lib.debug(Lib.dbgTest, "Run t" + (_i + 1));
                        }
                    });
                    threads[_i].setName("t" + (_i + 1));
                    ThreadedKernel.scheduler.setPriority(threads[_i], (_i + 1));
                    testQueue.waitForAccess(threads[_i]);
                }
                //donated thread donateT2 should be donated by donateT1
                testQueue.waitForAccess(donateT2);

                KThread t1 = testQueue.nextThread();
                Lib.debug(Lib.dbgTest, "get Thread " + t1.name);
                Lib.assertTrue(t1.name.equals("t_donate2"));
                Lib.debug(Lib.dbgTest, t1.name + " priority = " + ThreadedKernel.scheduler.getPriority(t1) + " effective_priority = " + ThreadedKernel.scheduler.getEffectivePriority(t1));
                Lib.assertTrue(ThreadedKernel.scheduler.getPriority(t1) == 0);
                Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(t1) == 4);

                //modify donateT1 priority should affect donateT2
                testQueue.waitForAccess(donateT2);
                ThreadedKernel.scheduler.setPriority(donateT1, 5);
                t1 = testQueue.nextThread();
                Lib.debug(Lib.dbgTest, "get Thread " + t1.name);
                Lib.assertTrue(t1.name.equals("t_donate2"));
                Lib.debug(Lib.dbgTest, t1.name + " priority = " + ThreadedKernel.scheduler.getPriority(t1) + " effective_priority = " + ThreadedKernel.scheduler.getEffectivePriority(t1));
                Lib.assertTrue(ThreadedKernel.scheduler.getPriority(t1) == 0);
                Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(t1) == 5);

                //insert donateT3 priority should affect donateT2
                testQueue.waitForAccess(donateT2);
                KThread donateT3 = new KThread(new Runnable() {
                    @Override
                    public void run() {
                        Lib.debug(Lib.dbgTest, "Run t_donate3");
                    }
                });
                donateT3.setName("t_donate3");
                ThreadedKernel.scheduler.setPriority(donateT3, 6);
                donateQueue.waitForAccess(donateT3);
                t1 = testQueue.nextThread();
                Lib.debug(Lib.dbgTest, "get Thread " + t1.name);
                Lib.assertTrue(t1.name.equals("t_donate2"));
                Lib.debug(Lib.dbgTest, t1.name + " priority = " + ThreadedKernel.scheduler.getPriority(t1) + " effective_priority = " + ThreadedKernel.scheduler.getEffectivePriority(t1));
                Lib.assertTrue(ThreadedKernel.scheduler.getPriority(t1) == 0);
                Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(t1) == 6);

                //donated thread donateT3 should be donated by donateT1
                testQueue.waitForAccess(donateQueue.nextThread());
                ThreadedKernel.scheduler.setPriority(donateT1, 7);
                t1 = testQueue.nextThread();
                Lib.debug(Lib.dbgTest, "get Thread " + t1.name);
                Lib.assertTrue(t1.name.equals("t_donate3"));
                Lib.debug(Lib.dbgTest, t1.name + " priority = " + ThreadedKernel.scheduler.getPriority(t1) + " effective_priority = " + ThreadedKernel.scheduler.getEffectivePriority(t1));
                Lib.assertTrue(ThreadedKernel.scheduler.getPriority(t1) == 6);
                Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(t1) == 7);

                KThread t2 = testQueue.nextThread();
                Lib.debug(Lib.dbgTest, "get Thread " + t2.name);
                Lib.assertTrue(t2.name.equals("t3"));
                Lib.debug(Lib.dbgTest, t2.name + " priority = " + ThreadedKernel.scheduler.getPriority(t2) + " effective_priority = " + ThreadedKernel.scheduler.getEffectivePriority(t2));
                Lib.assertTrue(ThreadedKernel.scheduler.getPriority(t2) == 3);
                Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(t2) == 3);


                KThread t3 = testQueue.nextThread();
                Lib.debug(Lib.dbgTest, "get Thread " + t3.name);
                Lib.assertTrue(t3.name.equals("t2"));
                Lib.debug(Lib.dbgTest, t3.name + " priority = " + ThreadedKernel.scheduler.getPriority(t3) + " effective_priority = " + ThreadedKernel.scheduler.getEffectivePriority(t3));
                Lib.assertTrue(ThreadedKernel.scheduler.getPriority(t3) == 2);
                Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(t3) == 2);

                KThread t4 = testQueue.nextThread();
                Lib.debug(Lib.dbgTest, "get Thread " + t4.name);
                Lib.assertTrue(t4.name.equals("t1"));
                Lib.debug(Lib.dbgTest, t4.name + " priority = " + ThreadedKernel.scheduler.getPriority(t4) + " effective_priority = " + ThreadedKernel.scheduler.getEffectivePriority(t4));
                Lib.assertTrue(ThreadedKernel.scheduler.getPriority(t4) == 1);
                Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(t4) == 1);

                Machine.interrupt().restore(intStatus);
                String schedulerName = Config.getString("ThreadedKernel.scheduler");
                ThreadedKernel.scheduler = (Scheduler) Lib.constructObject(schedulerName);
            }
        }));
        //fire!
        ts.run();
    }

    private static final char dbgThread = 't';

    /**
     * Additional state used by schedulers.
     *
     * @see nachos.threads.PriorityScheduler.ThreadState
     */
    public Object schedulingState = null;

    private static final int statusNew = 0;
    private static final int statusReady = 1;
    private static final int statusRunning = 2;
    private static final int statusBlocked = 3;
    private static final int statusFinished = 4;

    /**
     * The status of this thread. A thread can either be new (not yet forked),
     * ready (on the ready queue but not running), running, or blocked (not
     * on the ready queue and not running).
     */
    private int status = statusNew;
    private String name = "(unnamed thread)";
    private Runnable target;
    private TCB tcb;

    /**
     * Unique identifer for this thread. Used to deterministically compare
     * threads.
     */
    private int id = numCreated++;
    /**
     * Number of times the KThread constructor was called.
     */
    private static int numCreated = 0;

    private static ThreadQueue readyQueue = null;
    private static KThread currentThread = null;
    private static KThread toBeDestroyed = null;
    private static KThread idleThread = null;
}
