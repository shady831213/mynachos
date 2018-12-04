package nachos.vm;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.Processor;
import nachos.machine.TranslationEntry;
import nachos.userprog.PagePool;

import java.util.Hashtable;
import java.util.Enumeration;

public class InvertedPageTable {

    class discData {
        final private Hashtable<Integer, byte[]> mem = new Hashtable<>();

        discData() {
        }

        public void write(int vpn, byte[] data) {
            Lib.assertTrue(data.length == Processor.pageSize);
            mem.put(vpn, data);
        }

        public byte[] read(int vpn) {
            Lib.assertTrue(mem.containsKey(vpn));
            return mem.get(vpn);
        }

        public int delete(int vpn) {
            mem.remove(vpn);
            return mem.size();
        }
    }

    class discBlockData {
        final private Hashtable<Integer, discData> mem = new Hashtable<>();

        discBlockData() {
        }

        public void write(int processID, int vpn, byte[] data) {
            Lib.assertTrue(data.length == Processor.pageSize);
            if (!mem.containsKey(processID)) {
                mem.put(processID, new discData());
            }
            mem.get(processID).write(vpn, data);
        }

        public byte[] read(int processID, int vpn) {
            Lib.assertTrue(mem.containsKey(processID));
            return mem.get(processID).read(vpn);
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

    final private Hashtable<Integer, InvertedPageTableNode> map = new Hashtable<>();
    final private discBlockData swapDisc = new discBlockData();
    final private PagePool pagePool;

    InvertedPageTable(PagePool pagePool) {
        this.pagePool = pagePool;
    }

    public void insert(int processId, TranslationEntry page) {
        map.put(page.ppn, new InvertedPageTableNode(processId, page));
    }

    public void delete(int Paddr) {
        Lib.assertTrue(map.containsKey(Paddr));
        map.remove(Paddr);
    }

    public void freeSwap(int processId, int vpn) {
        swapDisc.delete(processId, vpn);
    }

    public InvertedPageTableNode lookup(int Paddr) {
        if (!map.containsKey(Paddr)) {
            return null;
        }
        return map.get(Paddr);
    }

    private void _swapOut(int Paddr) {
        InvertedPageTableNode node = lookup(Paddr);
        Lib.assertTrue(node != null);
        byte _data[];
        _data = new byte[Processor.pageSize];
        System.arraycopy(Machine.processor().getMemory(), Processor.pageSize * Paddr, _data, 0, Processor.pageSize);
        swapDisc.write(node.getProcessId(), node.getPage().vpn, _data);
        node.getPage().valid = false;
        map.remove(Paddr);
    }

    public void swapOut(int Paddr) {
        _swapOut(Paddr);
        pagePool.freePage(Paddr);
    }

    public void swap() {
        Enumeration<Integer> keys = map.keys();
        while (keys.hasMoreElements()) {
            swapOut(keys.nextElement());
        }
    }

    public void swapIn(int processId, TranslationEntry page) {
        Lib.assertTrue(page.valid == false);
        InvertedPageTableNode node = lookup(page.ppn);
        if (node != null) {
            _swapOut(page.ppn);
        }
        System.arraycopy(swapDisc.read(processId, page.vpn), 0, Machine.processor().getMemory(), Processor.pageSize * page.ppn, Processor.pageSize);
        page.valid = true;
        pagePool.loadPage(page.ppn);
        insert(processId, page);
    }
}
