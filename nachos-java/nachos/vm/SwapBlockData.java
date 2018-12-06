package nachos.vm;

import nachos.machine.Lib;
import nachos.machine.Processor;

import java.util.Hashtable;

class SwapData {
    final private Hashtable<Integer, byte[]> mem = new Hashtable<>();

    SwapData() {
    }

    public void write(int vpn, byte[] data) {
        Lib.assertTrue(data.length == Processor.pageSize);
        mem.put(vpn, data);
    }

    public byte[] read(int vpn) {
        Lib.assertTrue(mem.containsKey(vpn));
        return mem.get(vpn);
    }

    public boolean exist(int vpn) {
        return mem.containsKey(vpn);
    }

    public int delete(int vpn) {
        mem.remove(vpn);
        return mem.size();
    }
}

public class SwapBlockData {
    final private Hashtable<Integer, SwapData> mem = new Hashtable<>();
    final static private SwapBlockData inst = new SwapBlockData();

    private SwapBlockData() {
    }

    static SwapBlockData getSwapBlockData() {
        return inst;
    }

    public void write(int processID, int vpn, byte[] data) {
        Lib.assertTrue(data.length == Processor.pageSize);
        if (!mem.containsKey(processID)) {
            mem.put(processID, new SwapData());
        }
        mem.get(processID).write(vpn, data);
    }

    public byte[] read(int processID, int vpn) {
        Lib.assertTrue(mem.containsKey(processID));
        return mem.get(processID).read(vpn);
    }

    public boolean exist(int processID, int vpn) {
        if (!mem.containsKey(processID)) {
            return false;
        }
        return mem.get(processID).exist(vpn);
    }

    public int delete(int processID, int vpn) {
        if (mem.containsKey(processID)) {
            if (mem.get(processID).delete(vpn) == 0) {
                mem.remove(processID);
            }
        }
        return mem.size();
    }
}
