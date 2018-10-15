package nachos.threads;

import nachos.machine.*;

import java.util.Iterator;
import java.util.Comparator;

import static java.lang.Math.max;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
    }

    /**
     * Allocate a new priority thread queue.
     *
     * @param transferPriority <tt>true</tt> if this queue should
     *                         transfer priority from waiting threads
     *                         to the owning thread.
     * @return a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
        return new PriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());

        return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());

        return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
        Lib.assertTrue(Machine.interrupt().disabled());

        Lib.assertTrue(priority >= priorityMinimum &&
                priority <= priorityMaximum);

        getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
        boolean intStatus = Machine.interrupt().disable();

        KThread thread = KThread.currentThread();

        int priority = getPriority(thread);
        if (priority == priorityMaximum)
            return false;

        setPriority(thread, priority + 1);

        Machine.interrupt().restore(intStatus);
        return true;
    }

    public boolean decreasePriority() {
        boolean intStatus = Machine.interrupt().disable();

        KThread thread = KThread.currentThread();

        int priority = getPriority(thread);
        if (priority == priorityMinimum)
            return false;

        setPriority(thread, priority - 1);

        Machine.interrupt().restore(intStatus);
        return true;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param thread the thread whose scheduling state to return.
     * @return the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
        if (thread.schedulingState == null)
            thread.schedulingState = new ThreadState(thread);

        return (ThreadState) thread.schedulingState;
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue {
        PriorityQueue(boolean transferPriority) {
            this.transferPriority = transferPriority;
        }

        public void waitForAccess(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());
            getThreadState(thread).waitForAccess(this);
        }

        public void acquire(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());
            if (transferPriority) {
                donateThread = thread;
            }
            if (thread != null) {
                getThreadState(thread).acquire(this);
            }
        }

        public KThread nextThread() {
            Lib.assertTrue(Machine.interrupt().disabled());
            // implement me
            KThread nextT = waitQueue.poll();
            acquire(nextT);
            return nextT;
        }

        /**
         * Return the next thread that <tt>nextThread()</tt> would return,
         * without modifying the state of this queue.
         *
         * @return the next thread that <tt>nextThread()</tt> would
         * return.
         */
        protected ThreadState pickNextThread() {
            // implement me
            Lib.assertTrue(Machine.interrupt().disabled());
            KThread nextT = waitQueue.peek();
            if (nextT != null) {
                return getThreadState(waitQueue.peek());
            }
            return null;
        }

        public void print() {
            Lib.assertTrue(Machine.interrupt().disabled());
            // implement me (if you want)
            for (Iterator i = waitQueue.iterator(); i.hasNext(); )
                System.out.print((KThread) i.next() + " ");
        }

        private void addThread(KThread thread) {
            waitQueue.add(thread);
        }

        private void removeThread(KThread thread) {
            waitQueue.remove(thread);
        }

        private void donate() {
            if (transferPriority && donateThread != null) {
                ThreadState nextTs = pickNextThread();
                if (nextTs != null) {
                    getThreadState(donateThread).setDonatedPriority(nextTs.getEffectivePriority());
                } else {
                    getThreadState(donateThread).setDonatedPriority(priorityMinimum);
                }
            }
        }

        /**
         * <tt>true</tt> if this queue should transfer priority from waiting
         * threads to the owning thread.
         */
        public boolean transferPriority;

        /**
         * compare priority;if priorities are equal, compare timestamp;
         */
        Comparator<KThread> priorityComparator = new Comparator<KThread>() {

            @Override
            public int compare(KThread t1, KThread t2) {
                if (getThreadState(t1).getEffectivePriority() == getThreadState(t2).getEffectivePriority()) {
                    if (getThreadState(t1).getTimestamp() < getThreadState(t2).getTimestamp()) {
                        return -1;
                    } else {
                        return 1;
                    }
                }
                return getThreadState(t2).getEffectivePriority() - getThreadState(t1).getEffectivePriority();
            }
        };

        private java.util.PriorityQueue<KThread> waitQueue = new java.util.PriorityQueue<>(priorityMaximum + 1, priorityComparator);

        private KThread donateThread = null;
    }

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see nachos.threads.KThread#schedulingState
     */
    protected class ThreadState {
        /**
         * Allocate a new <tt>ThreadState</tt> object and associate it with the
         * specified thread.
         *
         * @param thread the thread this state belongs to.
         */
        public ThreadState(KThread thread) {
            this.thread = thread;

            setPriority(priorityDefault);
        }

        /**
         * Return the priority of the associated thread.
         *
         * @return the priority of the associated thread.
         */
        public int getPriority() {
            return priority;
        }

        /**
         * Return the effective priority of the associated thread.
         * get max value of priority and donated priority.
         * if transferPriority is false, no chance to update donatedPriority, which default value is minimum priority
         *
         * @return the effective priority of the associated thread.
         */
        public int getEffectivePriority() {
            // implement me
            return max(priority, donatedPriority);
        }

        /**
         * Set the priority of the associated thread to the specified value.
         * adjust priority-> remove from heap -> add to heap
         *
         * @param priority the new priority.
         */
        public void setPriority(int priority) {
            if (this.priority == priority)
                return;

            this.priority = priority;

            // implement me
            if (currentWaitQueue != null) {
                currentWaitQueue.removeThread(thread);
                currentWaitQueue.addThread(thread);
                currentWaitQueue.donate();
            }
        }

        public void setDonatedPriority(int priority) {
            if (this.donatedPriority == priority)
                return;

            this.donatedPriority = priority;

            // implement me
            if (currentWaitQueue != null) {
                currentWaitQueue.removeThread(thread);
                currentWaitQueue.addThread(thread);
                currentWaitQueue.donate();
            }
        }

        /**
         * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
         * the associated thread) is invoked on the specified priority queue.
         * The associated thread is therefore waiting for access to the
         * resource guarded by <tt>waitQueue</tt>. This method is only called
         * if the associated thread cannot immediately obtain access.
         * update donatedThread of waitQueue when insert new thread.
         *
         * @param waitQueue the queue that the associated thread is
         *                  now waiting on.
         * @see nachos.threads.ThreadQueue#waitForAccess
         */
        public void waitForAccess(PriorityQueue waitQueue) {
            // implement me
            this.timestamp = Machine.timer().getTime();
            waitQueue.addThread(thread);
            waitQueue.donate();
            currentWaitQueue = waitQueue;
        }

        /**
         * Called when the associated thread has acquired access to whatever is
         * guarded by <tt>waitQueue</tt>. This can occur either as a result of
         * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
         * <tt>thread</tt> is the associated thread), or as a result of
         * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
         * update donatedPriority only if transferPriority is true. The value is max value in the waitQueue.
         *
         * @see nachos.threads.ThreadQueue#acquire
         * @see nachos.threads.ThreadQueue#nextThread
         */
        public void acquire(PriorityQueue waitQueue) {
            // implement me
            currentWaitQueue = null;
            waitQueue.donate();
        }

        /**
         * The thread with which this object is associated.
         */
        protected KThread thread;
        /**
         * The priority of the associated thread.
         */
        protected int priority;

        private int donatedPriority = priorityMinimum;

        private PriorityQueue currentWaitQueue = null;

        public long getTimestamp() {
            return timestamp;
        }

        /**
         * time stamp when enter the queue
         */
        protected long timestamp;
    }
}
