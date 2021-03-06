package nachos.vm;

import nachos.machine.*;
import nachos.threads.ThreadedKernel;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.Hashtable;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {

    class CoffAddressMapping extends AddressMapping {
        final private CoffSection section;
        final private int spn;

        CoffAddressMapping(TranslationEntry entry, CoffSection section, int spn) {
            super(entry);
            this.section = section;
            this.spn = spn;
            this.readOnly = section.isReadOnly();
        }

        public void loadPageData() {
            section.loadPage(this.spn, page.ppn);
        }

        public void storedPageData() {
            Lib.assertNotReached("not allow write!");
        }

        public int readVirtualMemoryInPage(int vaddr, byte[] data, int offset,
                                           int length) {
            int amount = Math.min(length, pageSize - (vaddr % pageSize));
            System.arraycopy(section.loadPage(this.spn), vaddr % pageSize, data, offset, amount);
            return amount;
        }

        public int writeVirtualMemoryInPage(int vaddr, byte[] data, int offset,
                                            int length) {
            Lib.assertNotReached("not allow write!");
            return 0;
        }
    }

    class DataAddressMapping extends AddressMapping {

        DataAddressMapping(TranslationEntry entry) {
            super(entry);
            this.readOnly = false;
        }

        public void loadPageData() {
            if (swapDisc.exist(processID, entry.vpn)) {
                System.arraycopy(swapDisc.read(processID, entry.vpn), 0, Machine.processor().getMemory(), Processor.pageSize * page.ppn, Processor.pageSize);
            }
        }

        public void storedPageData() {
            byte _data[];
            _data = new byte[Processor.pageSize];
            System.arraycopy(Machine.processor().getMemory(), Processor.pageSize * page.ppn, _data, 0, Processor.pageSize);
            swapDisc.write(processID, entry.vpn, _data);
        }

        public int readVirtualMemoryInPage(int vaddr, byte[] data, int offset,
                                           int length) {
            if (swapDisc.exist(processID, vaddr / pageSize)) {
                int amount = Math.min(length, pageSize - (vaddr % pageSize));
                System.arraycopy(swapDisc.read(processID, vaddr / pageSize), vaddr % pageSize, data, offset, amount);
                return amount;
            }
            return 0;
        }

        public int writeVirtualMemoryInPage(int vaddr, byte[] data, int offset,
                                            int length) {
            byte _data[];
            if (swapDisc.exist(processID, entry.vpn)) {
                _data = swapDisc.read(processID, vaddr / pageSize);
            } else {
                _data = new byte[Processor.pageSize];
            }
            int amount = Math.min(length, pageSize - (vaddr % pageSize));
            System.arraycopy(data, offset, _data, vaddr % pageSize, amount);
            swapDisc.write(processID, vaddr / pageSize, _data);
            return amount;
        }
    }

    private Hashtable<Integer, AddressMapping> mappingTable;

    /**
     * Allocate a new process.
     */
    public VMProcess() {
        super();
        mappingTable = new Hashtable<>();
    }

    protected UserProcess createSubProcess() {
        return new VMProcess();
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

    private void invalidTLBEntry(int vpn) {
        Processor processor = Machine.processor();
        for (int i = 0; i < processor.getTLBSize(); i++) {
            TranslationEntry entry = processor.readTLBEntry(i);
            if (entry.vpn == vpn) {
                entry.valid = false;
                processor.writeTLBEntry(i, entry);
            }
        }
    }

    /**
     * Initializes page tables for this process so that the executable can be
     * demand-paged.
     *
     * @return <tt>true</tt> if successful.
     */

    protected boolean load(String name, String[] args) {
        Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

        OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
        if (executable == null) {
            Lib.debug(dbgProcess, "\topen failed");
            return false;
        }

        try {
            coff = new Coff(executable);
        } catch (EOFException e) {
            executable.close();
            Lib.debug(dbgProcess, "\tcoff load failed");
            return false;
        }

        // make sure the sections are contiguous and start at page 0
        numPages = 0;
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            if (section.getFirstVPN() != numPages) {
                coff.close();
                Lib.debug(dbgProcess, "\tfragmented executable");
                return false;
            }
            numPages += section.getLength();
        }

        // make sure the argv array will fit in one page
        byte[][] argv = new byte[args.length][];
        int argsSize = 0;
        for (int i = 0; i < args.length; i++) {
            argv[i] = args[i].getBytes();
            // 4 bytes for argv[] pointer; then string plus one for null byte
            argsSize += 4 + argv[i].length + 1;
        }
        if (argsSize > pageSize) {
            coff.close();
            Lib.debug(dbgProcess, "\targuments too long");
            return false;
        }

        // program counter initially points at the program entry point
        initialPC = coff.getEntryPoint();

        // next comes the stack; stack pointer initially points to top of it
        allocDataMemory(numPages, stackPages);

        numPages += stackPages;
        initialSP = numPages * pageSize;

        // and finally reserve 1 page for arguments
        allocDataMemory(numPages, 1);
        numPages++;

        if (!loadSections())
            return false;

        // store arguments in last page
        int entryOffset = (numPages - 1) * pageSize;
        int stringOffset = entryOffset + args.length * 4;

        this.argc = args.length;
        this.argv = entryOffset;
        Lib.debug(dbgProcess, "numPages is " + numPages + " page arg is " + entryOffset / pageSize);
        for (int i = 0; i < argv.length; i++) {
            byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
            Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
            entryOffset += 4;
            Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
                    argv[i].length);
            stringOffset += argv[i].length;
            Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[]{0}) == 1);
            stringOffset += 1;
        }


        return true;
    }

    protected boolean loadSections() {
        // load sections
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);

            Lib.debug(dbgProcess, "\tinitializing " + section.getName()
                    + " section (" + section.getLength() + " pages)");
            //alloc memory
            allocCoffSectionMemory(section);
//            if (section.isInitialzed()) {
//                for (int i = 0; i < section.getLength(); i++) {
//                    AddressMapping mapping = getMapping(section.getFirstVPN() + i);
//                    VMKernel.memMap.map(mapping);
//                    section.loadPage(i, mapping.entry.ppn);
//                    mapping.entry.valid = true;
//                }
//            }
        }

        return true;
    }

    protected int readVirtualMemoryInPage(int vaddr, byte[] data, int offset,
                                          int length, byte[] memory) {
        AddressMapping mapping = getMapping(vaddr / pageSize);
        if (!mapping.entry.valid) {
            return mapping.readVirtualMemoryInPage(vaddr, data, offset, length);
        }


        int paddrInPage = mapping.entry.ppn * pageSize + vaddr % pageSize;
        int amount = Math.min(length, pageSize - (vaddr % pageSize));
        System.arraycopy(memory, paddrInPage, data, offset, amount);
        return amount;
    }

    protected int writeVirtualMemoryInPage(int vaddr, byte[] data, int offset,
                                           int length, byte[] memory) {
        AddressMapping mapping = getMapping(vaddr / pageSize);
        if (!mapping.entry.valid) {
            if (!mapping.isReadOnly()) {
                DataAddressMapping newMapping = new DataAddressMapping(mapping.entry);
                newMapping.entry.readOnly = false;
                mappingTable.put(vaddr / pageSize, newMapping);
                return newMapping.writeVirtualMemoryInPage(vaddr, data, offset, length);
            }
            return mapping.writeVirtualMemoryInPage(vaddr, data, offset, length);
        }

        int paddrInPage = mapping.entry.ppn * pageSize + vaddr % pageSize;
        int amount = Math.min(length, pageSize - (vaddr % pageSize));
        System.arraycopy(data, offset, memory, paddrInPage, amount);
        return amount;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
        super.unloadSections();
    }

    protected void allocCoffSectionMemory(CoffSection section) {
        for (int i = 0; i < section.getLength(); i++) {
            TranslationEntry entry = new TranslationEntry();
            entry.readOnly = true;
            entry.vpn = section.getFirstVPN() + i;
            mappingTable.put(section.getFirstVPN() + i, new CoffAddressMapping(entry, section, i));
        }
    }

    protected void allocDataMemory(int vaddr, int length) {
        for (int i = 0; i < length; i++) {
            TranslationEntry entry = new TranslationEntry();
            entry.readOnly = true;
            entry.vpn = vaddr + i;
            mappingTable.put(vaddr + i, new DataAddressMapping(entry));
        }
    }

    protected void freeMemory(int vaddr, int length) {
        for (int i = 0; i < length; i++) {
            AddressMapping mapping = getMapping(vaddr + i);
            if (mapping.entry.valid) {
                VMKernel.memMap.getPage(mapping.entry.ppn).unmap();
            }
            mappingTable.remove(vaddr + i);
        }
    }

    protected AddressMapping getMapping(int vaddr) {
        return mappingTable.get(vaddr);
    }

    private void updateTLBHW() {
        Processor processor = Machine.processor();
        for (int i = 0; i < processor.getTLBSize(); i++) {
            TranslationEntry oldPage = processor.readTLBEntry(i);
            if (oldPage.used || oldPage.dirty) {
                Lib.debug(dbgVM, "update used and dirty!");
                Lib.debug(dbgVM, "vpn = " + oldPage.vpn);
                Lib.debug(dbgVM, "ppn = " + oldPage.ppn);
                Lib.debug(dbgVM, "dirty = " + oldPage.dirty);
                Lib.debug(dbgVM, "used = " + oldPage.used);
                getMapping(oldPage.vpn).updateEntryHW(oldPage);
                oldPage.used = false;
                oldPage.dirty = false;
                processor.writeTLBEntry(i, oldPage);
            }
        }
    }

    private void handleTlbMiss() {
        Lib.debug(dbgVM, "handleTlbMiss!");
        Processor processor = Machine.processor();
        //update tlb by hardware
        //update dirty and used bit

        int vpn = processor.readRegister(Processor.regBadVAddr);
        Lib.debug(dbgVM, "vaddr = " + Lib.toHexString(vpn));
        AddressMapping mapping = getMapping(vpn / pageSize);
        if (!mapping.entry.valid) {
            VMKernel.memMap.map(mapping);
            mapping.loadPageData();
            mapping.entry.valid = true;
        }
        Lib.debug(dbgVM, "vpn = " + mapping.entry.vpn);
        Lib.debug(dbgVM, "ppn = " + mapping.entry.ppn);
        Lib.debug(dbgVM, "valid = " + mapping.entry.valid);

        //ranodomly update tlb
        int tlbIdx = Lib.random(processor.getTLBSize());

        //tlb replacement
        processor.writeTLBEntry(tlbIdx, mapping.entry);
    }


    private void handleReadOnly() {
        Lib.debug(dbgVM, "handleReadOnly!");
        Processor processor = Machine.processor();
        int vpn = processor.readRegister(Processor.regBadVAddr);
        Lib.debug(dbgVM, "vaddr = " + Lib.toHexString(vpn));
        AddressMapping mapping = getMapping(vpn / pageSize);
        //remap on copy
        if (!mapping.isReadOnly()) {
            DataAddressMapping newMapping = new DataAddressMapping(mapping.entry);
            VMKernel.memMap.getPage(mapping.page.ppn).map(newMapping);
            newMapping.entry.readOnly = false;
            newMapping.entry.dirty = true;
            mappingTable.put(vpn / pageSize, newMapping);
            invalidTLBEntry(vpn / pageSize);
        }
        Lib.debug(dbgVM, "vpn = " + mapping.entry.vpn);
        Lib.debug(dbgVM, "ppn = " + mapping.entry.ppn);
        Lib.debug(dbgVM, "valid = " + mapping.entry.valid);

        //ranodomly update tlb
        int tlbIdx = Lib.random(processor.getTLBSize());
        //tlb replacement
        processor.writeTLBEntry(tlbIdx, mapping.entry);
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
        updateTLBHW();
        switch (cause) {
            case Processor.exceptionTLBMiss:
                handleTlbMiss();
                break;
            case Processor.exceptionReadOnly:
                handleReadOnly();
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
