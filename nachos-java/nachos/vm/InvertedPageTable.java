package nachos.vm;

import nachos.machine.Lib;
import nachos.machine.TranslationEntry;

import java.util.Hashtable;

public class InvertedPageTable {
    final private Hashtable<Integer, InvertedPageTableNode> map = new Hashtable<>();

    InvertedPageTable() {
    }


    public void insert(int processId, TranslationEntry page) {
        map.put(page.ppn, new InvertedPageTableNode(processId, page));
    }

    public void delete(int processId, int Paddr) {
        Lib.assertTrue(map.containsKey(Paddr));
        Lib.assertTrue(map.get(Paddr).getProcessId() == processId);
        map.remove(Paddr);
    }


    public InvertedPageTableNode lookup(int Paddr) {
        if (!map.containsKey(Paddr)) {
            return null;
        }
        return map.get(Paddr);
    }
}
