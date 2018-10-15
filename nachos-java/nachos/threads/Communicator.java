package nachos.threads;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    private int word;
    final private Lock lock = new Lock();
    final private Condition2 notFull = new Condition2(lock);
    final private Condition2 notEmpty = new Condition2(lock);
    private boolean written = false;

    /**
     * Allocate a new communicator.
     */
    public Communicator() {
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param word the integer to transfer.
     */
    public void speak(int word) {
        lock.acquire();
        while (written) {
            notFull.sleep();
        }
        written = true;
        this.word = word;
        notEmpty.wakeAll();
        lock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return the integer transferred.
     */
    public int listen() {
        int _word;
        lock.acquire();
        while (!written) {
            notEmpty.sleep();
        }
        written = false;
        _word = this.word;
        notFull.wakeAll();
        lock.release();
        return _word;
    }
}
