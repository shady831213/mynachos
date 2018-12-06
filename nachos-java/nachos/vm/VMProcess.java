package nachos.vm;

import nachos.machine.*;
import nachos.userprog.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
    /**
     * Allocate a new process.
     */
    public VMProcess() {
        super();
        //VMKernel.MemMap.put(processID, pageTable);
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

    protected TranslationEntry getEntry(int vaddr) {
        TranslationEntry entry = pageTable[vaddr];
        if (entry == null) {
            return entry;
        }
        if (!entry.valid) {
            VMKernel.memMap.map(processID, entry);
        }
        return entry;
    }

    protected void allocMemory(int vaddr, int length, boolean readOnly) {
        for (int i = 0; i < length; i++) {
            pageTable[vaddr + i] = new TranslationEntry();
            pageTable[vaddr + i].readOnly = readOnly;
            pageTable[vaddr + i].vpn = vaddr + i;
            pageTable[vaddr + i].ppn = -1;
        }
    }

    protected void freeMemory(int vaddr, int length) {
        for (int i = 0; i < length; i++) {
            if (pageTable[vaddr + i].ppn != -1) {
                VMKernel.memMap.unmap(pageTable[vaddr + i].ppn);
            }
            pageTable[vaddr + i] = null;
        }
    }

    private void handleTlbMiss() {
        Lib.debug(dbgVM, "handleTlbMiss!");
        Processor processor = Machine.processor();
        int vpn = processor.readRegister(Processor.regBadVAddr);
        Lib.debug(dbgVM, "vaddr = " + Lib.toHexString(vpn));
        TranslationEntry page = getEntry(vpn / pageSize);
        Lib.debug(dbgVM, "vpn = " + page.vpn);
        Lib.debug(dbgVM, "ppn = " + page.ppn);
        Lib.debug(dbgVM, "valid = " + page.valid);

        //ranodomly update tlb
        int tlbIdx = Lib.random(processor.getTLBSize());
        //update dirty and used bit
        TranslationEntry oldPage = processor.readTLBEntry(tlbIdx);
        VMKernel.memMap.updateEntry(oldPage);
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
//        VMKernel.MemMap.remove(processID);
//    }

    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
    private static final char dbgVM = 'v';
}
