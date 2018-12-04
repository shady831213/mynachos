package nachos.vm;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.TranslationEntry;
import nachos.userprog.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
    /**
     * Allocate a new VM kernel.
     */
    public VMKernel() {
        super();
    }

    /**
     * Initialize this kernel.
     */
    public void initialize(String[] args) {
        super.initialize(args);
    }

    /**
     * Test this kernel.
     */
    public void selfTest() {
        super.selfTest();
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

    // dummy variables to make javac smarter
    private static VMProcess dummy1 = null;

    private static final char dbgVM = 'v';

    final public static InvertedPageTable ipt = new InvertedPageTable(pagePool);

    public static TranslationEntry allocPage(int processId) {
        if (pagePool.getFreePages() == 0) {
            ipt.swap();
        }
        TranslationEntry page = pagePool.allocPage();
        ipt.insert(processId, page);
        return page;
    }

    public static void freePage(int processId, TranslationEntry page) {
        if (page.valid) {
            pagePool.freePage(page.ppn);
            ipt.delete(page.ppn);
        }
        ipt.freeSwap(processId, page.vpn);
    }
}
