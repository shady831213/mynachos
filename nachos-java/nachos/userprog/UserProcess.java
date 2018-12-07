package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;

import java.io.EOFException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
        int numPhysPages = Machine.processor().getNumPhysPages();
        pageTable = new TranslationEntry[4096];
//        for (int i = 0; i < numPhysPages; i++)
//            pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
        //stdin(0) and stdout(1)
        stdin = UserKernel.console.openForReading();
        stdout = UserKernel.console.openForWriting();

        processID = processCnt;
        processCnt++;
    }

    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
        return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
    }

    public static UserProcess newRootUserProcess() {
        UserProcess process = (UserProcess) Lib.constructObject(Machine.getProcessClassName());
        process.root = true;
        return process;
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param name the name of the file containing the executable.
     * @param args the arguments to pass to the executable.
     * @return <tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
        if (!load(name, args))
            return false;

        UThread thread = new UThread(this);
        thread.setName(name).fork();
        rootThread = thread;
        return true;
    }

    public void join() {
        rootThread.join();
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
        Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param vaddr     the starting virtual address of the null-terminated
     *                  string.
     * @param maxLength the maximum number of characters in the string,
     *                  not including the null terminator.
     * @return the string read, or <tt>null</tt> if no null terminator was
     * found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
        Lib.assertTrue(maxLength >= 0);

        byte[] bytes = new byte[maxLength + 1];

        int bytesRead = readVirtualMemory(vaddr, bytes);

        for (int length = 0; length < bytesRead; length++) {
            if (bytes[length] == 0)
                return new String(bytes, 0, length);
        }

        return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr the first byte of virtual memory to read.
     * @param data  the array where the data will be stored.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
        return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param vaddr  the first byte of virtual memory to read.
     * @param data   the array where the data will be stored.
     * @param offset the first byte to write in the array.
     * @param length the number of bytes to transfer from virtual memory to
     *               the array.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
                                 int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

        byte[] memory = Machine.processor().getMemory();

        // for now, just assume that virtual addresses equal physical addresses
        if (vaddr < 0)
            return 0;

        int startVaddr = vaddr + offset;
        int endVaddr = startVaddr + length - 1;

        int amount = 0;
        int leftLength = length;
        int vaddrACC = startVaddr;

        for (int i = startVaddr / pageSize; i <= endVaddr / pageSize; i++) {
            int amountInPage = readVirtualMemoryInPage(vaddrACC, data, offset + amount, leftLength, memory);
            if (amountInPage == 0) {
                break;
            }
            amount += amountInPage;
            leftLength -= amountInPage;
            vaddrACC += amountInPage;
        }

//        int amount = Math.min(length, memory.length - vaddr);
//        System.arraycopy(memory, vaddr, data, offset, amount);

        return amount;
    }

    protected int readVirtualMemoryInPage(int vaddr, byte[] data, int offset,
                                          int length, byte[] memory) {
        TranslationEntry page = getEntry(vaddr / pageSize);
        if (page == null) {
            return 0;
        }
        int paddrInPage = page.ppn * pageSize + vaddr % pageSize;
        int amount = Math.min(length, pageSize - (vaddr % pageSize));
        System.arraycopy(memory, paddrInPage, data, offset, amount);
        return amount;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr the first byte of virtual memory to write.
     * @param data  the array containing the data to transfer.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
        return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param vaddr  the first byte of virtual memory to write.
     * @param data   the array containing the data to transfer.
     * @param offset the first byte to transfer from the array.
     * @param length the number of bytes to transfer from the array to
     *               virtual memory.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
                                  int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

        byte[] memory = Machine.processor().getMemory();

        // for now, just assume that virtual addresses equal physical addresses
        if (vaddr < 0)
            return 0;

        int startVaddr = vaddr + offset;
        int endVaddr = startVaddr + length - 1;

        int amount = 0;
        int leftLength = length;
        int vaddrACC = startVaddr;

        for (int i = startVaddr / pageSize; i <= endVaddr / pageSize; i++) {
            int amountInPage = writeVirtualMemoryInPage(vaddrACC, data, offset + amount, leftLength, memory);
            if (amountInPage == 0) {
                break;
            }
            amount += amountInPage;
            leftLength -= amountInPage;
            vaddrACC += amountInPage;
        }

//        int amount = Math.min(length, memory.length - vaddr);
//        System.arraycopy(data, offset, memory, vaddr, amount);

        return amount;
    }

    protected int writeVirtualMemoryInPage(int vaddr, byte[] data, int offset,
                                           int length, byte[] memory) {
        TranslationEntry page = getEntry(vaddr / pageSize);
        if (page == null || page.readOnly) {
            return 0;
        }
        int paddrInPage = page.ppn * pageSize + vaddr % pageSize;
        int amount = Math.min(length, pageSize - (vaddr % pageSize));
        System.arraycopy(data, offset, memory, paddrInPage, amount);
        return amount;
    }


    protected void allocMemory(int vaddr, int length, boolean readOnly) {
        for (int i = 0; i < length; i++) {
            pageTable[vaddr + i] = UserKernel.pagePool.allocPage();
            pageTable[vaddr + i].readOnly = readOnly;
            pageTable[vaddr + i].valid = true;
            pageTable[vaddr + i].vpn = vaddr + i;
        }
    }

    protected void freeMemory(int vaddr, int length) {
        for (int i = 0; i < length; i++) {
            UserKernel.pagePool.freePage(pageTable[vaddr + i].ppn);
            pageTable[vaddr + i] = null;
        }
    }

    protected TranslationEntry getEntry(int vaddr) {
        return pageTable[vaddr];
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param name the name of the file containing the executable.
     * @param args the arguments to pass to the executable.
     * @return <tt>true</tt> if the executable was successfully loaded.
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
        allocMemory(numPages, stackPages, false);
        numPages += stackPages;
        initialSP = numPages * pageSize;

        // and finally reserve 1 page for arguments
        allocMemory(numPages, 1, false);
        numPages++;

        if (!loadSections())
            return false;

        // store arguments in last page
        int entryOffset = (numPages - 1) * pageSize;
        int stringOffset = entryOffset + args.length * 4;

        this.argc = args.length;
        this.argv = entryOffset;
        System.out.println("numPages is " + numPages + " page arg is " + entryOffset / pageSize + " ppn = " + getEntry(entryOffset / pageSize).ppn);
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


    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return <tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
        if (numPages > 4096) {
            coff.close();
            Lib.debug(dbgProcess, "\tinsufficient virtual memory");
            return false;
        }
//        if (numPages > Machine.processor().getNumPhysPages()) {
//            coff.close();
//            Lib.debug(dbgProcess, "\tinsufficient physical memory");
//            return false;
//        }

//        if (numPages > UserKernel.pagePool.getFreePages()) {
//            coff.close();
//            Lib.debug(dbgProcess, "\tinsufficient physical memory");
//            return false;
//        }

        // load sections
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);

            Lib.debug(dbgProcess, "\tinitializing " + section.getName()
                    + " section (" + section.getLength() + " pages)");
            //alloc memory
            allocMemory(section.getFirstVPN(), section.getLength(), section.isReadOnly());

            for (int i = 0; i < section.getLength(); i++) {
                int ppn = getEntry(section.getFirstVPN() + i).ppn;

                section.loadPage(i, ppn);
            }
        }

        return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            //alloc memory
            freeMemory(section.getFirstVPN(), section.getLength());
        }
    }

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
        Processor processor = Machine.processor();

        // by default, everything's 0
        for (int i = 0; i < processor.numUserRegisters; i++)
            processor.writeRegister(i, 0);

        // initialize PC and SP according
        processor.writeRegister(Processor.regPC, initialPC);
        processor.writeRegister(Processor.regSP, initialSP);

        // initialize the first two argument registers to argc and argv
        processor.writeRegister(Processor.regA0, argc);
        processor.writeRegister(Processor.regA1, argv);
    }

    protected void freeResources() {
        //free stack
        freeMemory(initialSP / pageSize - stackPages, stackPages);
        //free args
        freeMemory(argv / pageSize, 1);
        unloadSections();
        //close all files
        openedFiles.clear();
    }

    private UserProcess createSubProcess() {
        UserProcess process = newUserProcess();
        subProcess.put(process.processID, process);
        return process;
    }

    /**
     * Handle the halt() system call.
     */
    private int handleHalt() {
        if (isRoot()) {
            Machine.halt();

            Lib.assertNotReached("Machine.halt() did not halt machine!");
        }
        return 0;
    }

    private int handleExit(int status) {
        Lib.debug(dbgProcess, "Exit with status " + status + ", finish process!");
        freeResources();
        exitStatus = status;
        KThread.currentThread().finish();
        if (isRoot()) {
            Machine.halt();
        }
        return status;
    }

    private void handleExitAbnormal() {
        Lib.debug(dbgProcess, "Exit abnormal , finish process!");
        freeResources();
        error = true;
        KThread.currentThread().finish();
        if (isRoot()) {
            Machine.halt();
        }
    }

    private int handleExec(int fileVaddr, int argc, int argv) {
        String filename = readVirtualMemoryString(fileVaddr, maxFileNameLen);
        Lib.debug(dbgProcess, "exec filename :" + filename + "!");
        String[] args = new String[argc];
        byte[] vaddrData = new byte[4];
        readVirtualMemory(argv, vaddrData, 0, 4);
        int vaddr = Lib.bytesToInt(vaddrData, 0);
        int offset = 0;
        for (int i = 0; i < argc; i++) {
            args[i] = readVirtualMemoryString(vaddr + offset, maxFileNameLen);
            offset += args[i].getBytes().length + 1;
            Lib.debug(dbgProcess, "args " + i + " is " + args[i]);
        }
        UserProcess process = createSubProcess();
        process.execute(filename, args);
        return process.processID;
    }

    private int handleJoin(int pid, int vaddr) {
        Lib.debug(dbgProcess, "join!");
        UserProcess process = subProcess.get(pid);
        if (process == null) {
            return -1;
        }
        process.join();
        writeVirtualMemory(vaddr, Lib.bytesFromInt(process.exitStatus));
        subProcess.remove(pid);
        Lib.debug(dbgProcess, "subprocess " + process.processID + " exit!");
        if (process.error) {
            return 0;
        }
        return 1;
    }

    private OpenFile getFileByDesp(int desp) {
        switch (desp) {
            case 0:
                return stdin;
            case 1:
                return stdout;
            default:
                return openedFiles.getFileByDesp(desp - fileDespStart);
        }
    }

    private int handleCreateOrOpen(int vaddr, boolean create) {
        String filename = readVirtualMemoryString(vaddr, maxFileNameLen);
        //cached file
        int desp;
        desp = openedFiles.cachedFileDesp(filename);
        if (desp >= 0) {
            return desp + fileDespStart;
        }
        OpenFile f = ThreadedKernel.fileSystem.open(filename, create);
        if (f == null) {
            Lib.debug(dbgProcess, "bad filename: " + filename + "!");
            return -1;
        }
        desp = openedFiles.addOpenFile(f);
        if (desp == -1) {
            Lib.debug(dbgProcess, "open file has been more than 16!");
            return -1;
        }
        Lib.debug(dbgProcess, "file desp of " + filename + " is " + desp);
        return desp + fileDespStart;
    }

    private int handleCreate(int vaddr) {
        Lib.debug(dbgProcess, "create file");
        return handleCreateOrOpen(vaddr, true);
    }

    private int handleOpen(int vaddr) {
        Lib.debug(dbgProcess, "open file");
        return handleCreateOrOpen(vaddr, false);

    }

    private int handleClose(int desp) {
        Lib.debug(dbgProcess, "close file");
        OpenFile file = getFileByDesp(desp);
        if (file == null) {
            return -1;
        }
        file.close();
        if (desp >= fileDespStart) {
            openedFiles.closeFile(desp - fileDespStart);
        }
        return 0;
    }

    private int handleUnlik(int vaddr) {
        Lib.debug(dbgProcess, "unlink file");
        String filename = readVirtualMemoryString(vaddr, maxFileNameLen);
        //cached file
        int desp;
        desp = openedFiles.cachedFileDesp(filename);
        if (desp >= 0) {
            openedFiles.closeFile(desp);
        }
        boolean success = ThreadedKernel.fileSystem.remove(filename);
        if (!success) {
            return -1;
        }
        return 0;
    }

    private int handleRead(int desp, int vaddr, int count) {
        Lib.debug(dbgProcess, "read file");
        OpenFile file = getFileByDesp(desp);
        if (file == null) {
            return -1;
        }
        byte data[] = new byte[count];
        int readAmount = file.read(data, 0, count);
        if (readAmount == -1) {
            file.seek(0);
            return -1;
        }
        int amount = writeVirtualMemory(vaddr, data, 0, readAmount);
        return amount;
    }

    private int handleWrite(int desp, int vaddr, int count) {
        Lib.debug(dbgProcess, "write file");
        OpenFile file = getFileByDesp(desp);
        if (file == null) {
            return -1;
        }
        byte data[] = new byte[count];
        int amount = readVirtualMemory(vaddr, data, 0, count);

        int writeAmount = file.write(data, 0, amount);
        if (writeAmount < count) {
            file.seek(0);
            return -1;
        }
        return writeAmount;
    }

    private static final int
            syscallHalt = 0,
            syscallExit = 1,
            syscallExec = 2,
            syscallJoin = 3,
            syscallCreate = 4,
            syscallOpen = 5,
            syscallRead = 6,
            syscallWrite = 7,
            syscallClose = 8,
            syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * </tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     * </tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     * </tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     *
     * @param syscall the syscall number.
     * @param a0      the first syscall argument.
     * @param a1      the second syscall argument.
     * @param a2      the third syscall argument.
     * @param a3      the fourth syscall argument.
     * @return the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
        switch (syscall) {
            case syscallHalt:
                return handleHalt();
            case syscallExit:
                return handleExit(a0);
            case syscallCreate:
                return handleCreate(a0);
            case syscallOpen:
                return handleOpen(a0);
            case syscallClose:
                return handleClose(a0);
            case syscallUnlink:
                return handleUnlik(a0);
            case syscallRead:
                return handleRead(a0, a1, a2);
            case syscallWrite:
                return handleWrite(a0, a1, a2);
            case syscallExec:
                return handleExec(a0, a1, a2);
            case syscallJoin:
                return handleJoin(a0, a1);
            default:
                Lib.debug(dbgProcess, "Unknown syscall " + syscall);
                handleExitAbnormal();
                Lib.assertNotReached("Unknown system call!");
        }
        return 0;
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
            case Processor.exceptionSyscall:
                int result = handleSyscall(processor.readRegister(Processor.regV0),
                        processor.readRegister(Processor.regA0),
                        processor.readRegister(Processor.regA1),
                        processor.readRegister(Processor.regA2),
                        processor.readRegister(Processor.regA3)
                );
                processor.writeRegister(Processor.regV0, result);
                processor.advancePC();
                break;

            default:
                Lib.debug(dbgProcess, "Unexpected exception: " +
                        Processor.exceptionNames[cause]);
                handleExitAbnormal();
                //Lib.assertNotReached("Unexpected exception");
        }
    }

    /**
     * The program being run by this process.
     */
    protected Coff coff;

    /**
     * This process's page table.
     */
    protected TranslationEntry[] pageTable;
    /**
     * The number of contiguous pages occupied by the program.
     */
    protected int numPages;

    /**
     * The number of pages in the program's stack.
     */
    protected final int stackPages = 8;


    protected final int maxFileNameLen = 256;

    protected final int maxFiles = 14;

    protected int initialPC, initialSP;
    protected int argc, argv;

    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';

    public boolean isRoot() {
        return root;
    }

    private boolean root = false;

    public int getExitStatus() {
        return exitStatus;
    }

    private int exitStatus = 0;

    public boolean isError() {
        return error;
    }

    private boolean error = false;

    protected final int fileDespStart = 2;
    private OpenFile stdin, stdout;

    private class openFileCache {
        private Vector<OpenFile> cache;

        openFileCache(int size) {
            cache = new Vector<>(size);
        }

        private int cachedFileDesp(String filename) {
            for (int i = cache.size() - 1; i >= 0; i--) {
                if (cache.get(i) != null && cache.get(i).getName().equals(filename)) {
                    return i;
                }
            }
            return -1;
        }

        private int addOpenFile(OpenFile file) {
            for (int i = 0; i < cache.size(); i++) {
                if (cache.get(i) == null) {
                    cache.set(i, file);
                    return i;
                }
            }
            if (cache.size() < maxFiles) {
                cache.add(file);
                return cache.size() - 1;
            }
            return -1;
        }

        private OpenFile getFileByDesp(int desp) {
            OpenFile file = cache.get(desp);
            if (file == null) {
                Lib.debug(dbgProcess, "file desp " + desp + " is not existed!");
            }
            return file;
        }

        private void closeFile(int desp) {
            if (desp < cache.size()) {
                cache.set(desp, null);
            }
        }

        private void clear() {
            for (Iterator i = cache.iterator(); i.hasNext(); ) {
                OpenFile file = ((OpenFile) i.next());
                if (file != null) {
                    file.close();
                }
            }
            cache.clear();
        }
    }

    private openFileCache openedFiles = new openFileCache(maxFiles);

    protected int processID;

    static int processCnt = 0;

    private HashMap<Integer, UserProcess> subProcess = new HashMap<>();

    private UThread rootThread;

}
