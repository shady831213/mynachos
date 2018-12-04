package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
    /**
     * Allocate a new process.
     */
    public VMProcess() {
        super();
        //VMKernel.InvertedPageTable.put(processID, pageTable);
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
        super.saveState();

    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
        // super.restoreState();
        //invalid all tlb
        invalidTLB();
    }

    private void invalidTLB() {
        Processor processor = Machine.processor();
        for (int i = 0; i < processor.getTLBSize(); i++) {
            processor.writeTLBEntry(i, new TranslationEntry());
        }
    }

    private void invalidTLBEntry(TranslationEntry page) {
        Processor processor = Machine.processor();
        for (int i = 0; i < processor.getTLBSize(); i++) {
            if (processor.readTLBEntry(i).ppn == page.ppn) {
                processor.writeTLBEntry(i, new TranslationEntry());
            }
        }
    }

    /**
     * Initializes page tables for this process so that the executable can be
     * demand-paged.
     *
     * @return <tt>true</tt> if successful.
     */
    protected boolean loadSections() {
        return super.loadSections();
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
        super.unloadSections();
    }


    protected int readVirtualMemoryInPage(int vaddr, byte[] data, int offset,
                                          int length, byte[] memory) {
        TranslationEntry page = translate(vaddr / pageSize);
        if (page == null) {
            return 0;
        }
        if (!page.valid) {
            VMKernel.ipt.swapIn(processID, page);
            invalidTLBEntry(page);
        }
        int paddrInPage = page.ppn * pageSize + vaddr % pageSize;
        int amount = Math.min(length, pageSize - (vaddr % pageSize));
        System.arraycopy(memory, paddrInPage, data, offset, amount);
        return amount;
    }

    protected int writeVirtualMemoryInPage(int vaddr, byte[] data, int offset,
                                           int length, byte[] memory) {
        TranslationEntry page = translate(vaddr / pageSize);
        if (page == null || page.readOnly) {
            return 0;
        }
        if (!page.valid) {
            VMKernel.ipt.swapIn(processID, page);
            invalidTLBEntry(page);
        }
        int paddrInPage = page.ppn * pageSize + vaddr % pageSize;
        int amount = Math.min(length, pageSize - (vaddr % pageSize));
        System.arraycopy(data, offset, memory, paddrInPage, amount);
        return amount;
    }


    protected void allocMemory(int vaddr, int length, boolean readOnly) {
        for (int i = 0; i < length; i++) {
            pageTable[vaddr + i] = VMKernel.allocPage(processID);
            pageTable[vaddr + i].readOnly = readOnly;
            pageTable[vaddr + i].valid = true;
            pageTable[vaddr + i].vpn = vaddr + i;

        }
    }

    protected void freeMemory(int vaddr, int length) {
        for (int i = 0; i < length; i++) {
            VMKernel.freePage(processID, pageTable[vaddr + i]);
            pageTable[vaddr + i] = null;
        }
    }

    private void handleTlbMiss() {
        Lib.debug(dbgVM, "handleTlbMiss!");
        Processor processor = Machine.processor();
        int vpn = processor.readRegister(Processor.regBadVAddr);
        Lib.debug(dbgVM, "vaddr = " + Lib.toHexString(vpn));
        TranslationEntry page = translate(vpn / pageSize);
        Lib.debug(dbgVM, "vpn = " + page.vpn);
        Lib.debug(dbgVM, "ppn = " + page.ppn);
        Lib.debug(dbgVM, "valid = " + page.valid);

        if (!page.valid) {
            VMKernel.ipt.swapIn(processID, page);
        }
        //ranodomly update tlb
        int tlbIdx = Lib.random(processor.getTLBSize());
        //update dirty and used bit
        TranslationEntry oldPage = processor.readTLBEntry(tlbIdx);
        InvertedPageTableNode invertPage = VMKernel.ipt.lookup(oldPage.ppn);
        if (invertPage != null) {
            invertPage.getPage().used = oldPage.used;
            invertPage.getPage().dirty = oldPage.dirty;
        }
        //tlb replacement
        processor.writeTLBEntry(tlbIdx, page);
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param cause the user exception that occurred.
     */
    public void handleException(int cause) {
        Processor processor = Machine.processor();

        switch (cause) {
            case Processor.exceptionTLBMiss:
                handleTlbMiss();
                break;
            default:
                super.handleException(cause);
                break;
        }
    }


//    protected void freeResources() {
//        super.freeResources();
//        VMKernel.InvertedPageTable.remove(processID);
//    }

    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
    private static final char dbgVM = 'v';
}
