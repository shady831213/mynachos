package nachos.userprog;

import nachos.machine.Lib;
import nachos.machine.TranslationEntry;

public class PagePool {
    private boolean bitmap[];
    private int pointer;
    private int freePages;

    PagePool() {
        pointer = 0;
    }

    public void initialize(int pageNum) {
        Lib.assertTrue(pageNum > 0);
        bitmap = new boolean[pageNum];
        for (int i = 0; i < pageNum; i++) {
            bitmap[i] = true;
        }
        freePages = pageNum;
    }

    private int nextPointer() {
        //make it a ring
        pointer++;
        if (pointer == bitmap.length) {
            pointer = 0;
        }
        return pointer;
    }

    public int getFreePages() {
        return freePages;
    }

    public TranslationEntry allocPage() {
        TranslationEntry entry = new TranslationEntry();
        int currentPointer = pointer;
        if (bitmap[pointer]) {
            bitmap[pointer] = false;
            entry.ppn = pointer;
            nextPointer();
            return entry;
        }
        for (int p = pointer; p != currentPointer; p = nextPointer()) {
            if (bitmap[p]) {
                bitmap[p] = false;
                entry.ppn = p;
                return entry;
            }
        }
        Lib.assertNotReached("OOM!");
        return null;
    }

    public void freePage(int Paddr) {
        Lib.assertTrue(!bitmap[Paddr]);
        bitmap[Paddr] = true;
        freePages++;
    }
}
