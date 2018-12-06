package nachos.vm;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.Processor;
import nachos.machine.TranslationEntry;

import java.util.Hashtable;

public class MemMap {

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

        public int delete(int vpn) {
            mem.remove(vpn);
            return mem.size();
        }
    }

    class SwapBlockData {
        final private Hashtable<Integer, SwapData> mem = new Hashtable<>();

        SwapBlockData() {
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

        public int delete(int processID, int vpn) {
            if (mem.containsKey(processID)) {
                if (mem.get(processID).delete(vpn) == 0) {
                    mem.remove(processID);
                }
            }
            return mem.size();
        }
    }

    private Page pages[];
    private MemAllocator allocator;
    final private SwapBlockData swapDisc = new SwapBlockData();

    MemMap() {
    }

    public void initialize(int pageNum) {
        Lib.assertTrue(pageNum > 0);
        pages = new Page[pageNum];
        for (int i = 0; i < pageNum; i++) {
            pages[i] = new Page(i);
        }
        allocator = new MemAllocator(pages);
    }

    public Page allocPage() {
        Page page = allocator.getFreePage();
        if (page == null) {
            swap();
            return allocator.getFreePage();
        }
        return page;
    }

    public void map(int processId, TranslationEntry entry) {
        //Lib.assertTrue(!pages[entry.ppn].free);
        pages[entry.ppn].map(processId, entry);
    }

    public void unmap(int Paddr) {
        pages[Paddr].unmap();
    }

    public void updateEntry(TranslationEntry entry) {
        pages[entry.ppn].updateEntry(entry);
    }

    private void swapOut(int Paddr) {
        byte _data[];
        _data = new byte[Processor.pageSize];
        System.arraycopy(Machine.processor().getMemory(), Processor.pageSize * Paddr, _data, 0, Processor.pageSize);
        swapDisc.write(pages[Paddr].processId, pages[Paddr].mappingEntry.vpn, _data);
        pages[Paddr].mappingEntry.valid = false;
        unmap(Paddr);
    }

    public void swap() {
        for (int i = 0; i < pages.length; i++) {
            swapOut(i);
        }
    }

    public void swapIn(int processId, TranslationEntry entry) {
        if (!pages[entry.ppn].free) {
            swapOut(entry.ppn);
        }
        System.arraycopy(swapDisc.read(processId, entry.vpn), 0, Machine.processor().getMemory(), Processor.pageSize * entry.ppn, Processor.pageSize);
        map(processId, entry);
    }
}
