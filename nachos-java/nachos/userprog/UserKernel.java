package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
    /**
     * Allocate a new user kernel.
     */
    public UserKernel() {
        super();
    }

    /**
     * Initialize this kernel. Creates a synchronized console and sets the
     * processor's exception handler.
     */
    public void initialize(String[] args) {
        super.initialize(args);

        console = new SynchConsole(Machine.console());

        Machine.processor().setExceptionHandler(new Runnable() {
            public void run() {
                exceptionHandler();
            }
        });
    }

    /**
     * Test the console device.
     */
    public void selfTest() {
        super.selfTest();

        Lib.TestSuite ts = new Lib.TestSuite();

        //non root process halt test
        ts.addTest(new Lib.Test("non_root_process_halt_test", new Runnable() {
            @Override
            public void run() {
                UserProcess process = UserProcess.newUserProcess();
                UThread thread = process.execute("halt.coff", new String[]{});
                Lib.assertTrue(thread != null);
                thread.join();
                //check exit status
                Lib.assertTrue(process.getExitStatus() == 0);
            }
        }));
        //create test
        ts.addTest(new Lib.Test("create_test", new Runnable() {
            @Override
            public void run() {
                UserProcess process = UserProcess.newUserProcess();
                UThread thread = process.execute("create_test.coff", new String[]{"create_test", "create_file"});
                thread.join();
                //check create file success
                Lib.assertTrue(ThreadedKernel.fileSystem.remove("create_file"));
                //check exit status
                Lib.assertTrue(process.getExitStatus() == 0);
            }
        }));
        //open test
        ts.addTest(new Lib.Test("open_test", new Runnable() {
            @Override
            public void run() {
                UserProcess process = UserProcess.newUserProcess();
                UThread thread = process.execute("open_test.coff", new String[]{"open_test", "open_file"});
                thread.join();
                //check create file success
                Lib.assertTrue(ThreadedKernel.fileSystem.remove("open_file"));
                //check exit status
                Lib.assertTrue(process.getExitStatus() == 0);
            }
        }));
        //close test
        ts.addTest(new Lib.Test("close_test", new Runnable() {
            @Override
            public void run() {
                UserProcess process = UserProcess.newUserProcess();
                UThread thread = process.execute("close_test.coff", new String[]{"close_test", "close_file"});
                thread.join();
                //check create file success
                Lib.assertTrue(ThreadedKernel.fileSystem.remove("close_file"));
                //check exit status
                Lib.assertTrue(process.getExitStatus() == 0);
            }
        }));
        //unlink test
        ts.addTest(new Lib.Test("unlink_test", new Runnable() {
            @Override
            public void run() {
                UserProcess process = UserProcess.newUserProcess();
                UThread thread = process.execute("unlink_test.coff", new String[]{"unlink_test", "unlink_file"});
                thread.join();
                //check removed file success
                Lib.assertTrue(!ThreadedKernel.fileSystem.remove("unlink_file"));
                //check exit status
                Lib.assertTrue(process.getExitStatus() == 0);
            }
        }));
        //write & read test
        ts.addTest(new Lib.Test("write_read_test", new Runnable() {
            @Override
            public void run() {
                UserProcess process = UserProcess.newUserProcess();
                UThread thread = process.execute("write_read_test.coff", new String[]{"write_read_test", "read_src_file", "write_dst_file"});
                thread.join();
                //check removed file success
                Lib.assertTrue(ThreadedKernel.fileSystem.remove("read_src_file"));
                Lib.assertTrue(ThreadedKernel.fileSystem.remove("write_dst_file"));
                //check exit status
                Lib.assertTrue(process.getExitStatus() == 0);
            }
        }));
        //fire!
        ts.run();

        System.out.println("Testing the console device. Typed characters");
        System.out.println("will be echoed until q is typed.");

        char c;

        do {
            c = (char) console.readByte(true);
            console.writeByte(c);
        }
        while (c != 'q');
        System.out.println("");
    }

    /**
     * Returns the current process.
     *
     * @return the current process, or <tt>null</tt> if no process is current.
     */
    public static UserProcess currentProcess() {
        if (!(KThread.currentThread() instanceof UThread))
            return null;

        return ((UThread) KThread.currentThread()).process;
    }

    /**
     * The exception handler. This handler is called by the processor whenever
     * a user instruction causes a processor exception.
     *
     * <p>
     * When the exception handler is invoked, interrupts are enabled, and the
     * processor's cause register contains an integer identifying the cause of
     * the exception (see the <tt>exceptionZZZ</tt> constants in the
     * <tt>Processor</tt> class). If the exception involves a bad virtual
     * address (e.g. page fault, TLB miss, read-only, bus error, or address
     * error), the processor's BadVAddr register identifies the virtual address
     * that caused the exception.
     */
    public void exceptionHandler() {
        Lib.assertTrue(KThread.currentThread() instanceof UThread);

        UserProcess process = ((UThread) KThread.currentThread()).process;
        int cause = Machine.processor().readRegister(Processor.regCause);
        process.handleException(cause);
    }

    /**
     * Start running user programs, by creating a process and running a shell
     * program in it. The name of the shell program it must run is returned by
     * <tt>Machine.getShellProgramName()</tt>.
     *
     * @see nachos.machine.Machine#getShellProgramName
     */
    public void run() {
        super.run();

        UserProcess process = UserProcess.newRootUserProcess();

        String shellProgram = Machine.getShellProgramName();
        UThread thread = process.execute(shellProgram, new String[]{});
        Lib.assertTrue(thread != null);


        //wait for process exit! fix me
        thread.join();
    }

    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
        super.terminate();
    }

    /**
     * Globally accessible reference to the synchronized console.
     */
    public static SynchConsole console;

    // dummy variables to make javac smarter
    private static Coff dummy1 = null;
}
