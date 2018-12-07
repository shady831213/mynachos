package nachos.vm;

import nachos.machine.Lib;
import nachos.machine.TranslationEntry;

public class MemMap {

    private Page pages[];
    private int freePages;
    private int pointer;

    MemMap() {
    }

    public void initialize(int pageNum) {
        Lib.assertTrue(pageNum > 0);
        pages = new Page[pageNum];
        for (int i = 0; i < pageNum; i++) {
            pages[i] = new Page(i);
        }
        freePages = pageNum;
    }

    private void nextPointer() {
        //make it a ring
        pointer++;
        if (pointer == pages.length) {
            pointer = 0;
        }
    }


    protected Page getFreePage() {
        if (freePages == 0) {
            swap();
        }
        int currentPointer = pointer;
        if (pages[pointer].free) {
            freePages--;
            return pages[pointer];
        }
        nextPointer();
        while (pointer != currentPointer) {
            if (pages[pointer].free) {
                freePages--;
                return pages[pointer];
            }
            nextPointer();
        }
        Lib.assertNotReached("OOM!");
        return null;
    }

    public void swap() {
        //first round : collection not dirty and not used page;and give used page second chance
        for (int i = 0; i < pages.length; i++) {
            if (!pages[i].free) {
                if (!pages[i].mappingEntry.entry.used && !pages[i].mappingEntry.entry.dirty) {
                    Lib.debug(dbgVM, "reclaim not used, not dirty page");
                    Lib.debug(dbgVM, "vpn = " + pages[i].mappingEntry.entry.vpn);
                    Lib.debug(dbgVM, "ppn = " + pages[i].mappingEntry.entry.ppn);
                    Lib.debug(dbgVM, "valid = " + pages[i].mappingEntry.entry.valid);
                    pages[i].unmap();
                    freePages++;
                    Lib.debug(dbgVM, "vpn = " + pages[i].mappingEntry.entry.vpn);
                    Lib.debug(dbgVM, "ppn = " + pages[i].mappingEntry.entry.ppn);
                    Lib.debug(dbgVM, "valid = " + pages[i].mappingEntry.entry.valid);
                    Lib.debug(dbgVM, "--------------");
                }
            } else {
                freePages++;
            }
        }
        //second round : collection not used and dirty page
        if (freePages == 0) {
            for (int i = 0; i < pages.length; i++) {
                if (!pages[i].free) {
                    if (!pages[i].mappingEntry.entry.used && pages[i].mappingEntry.entry.dirty) {
                        pages[i].mappingEntry.entry.dirty = false;
                        pages[i].unmap();
                        pages[i].swapOut();
                        freePages++;
                    }
                }
            }
        }
        //clr used bit round, give it second chance
        for (int i = 0; i < pages.length; i++) {
            if (!pages[i].free) {
                pages[i].mappingEntry.entry.used = false;
            }
        }
        //worst case : swap all
        if (freePages == 0) {
            for (int i = 0; i < pages.length; i++) {
                pages[i].unmap();
                if (pages[i].mappingEntry.entry.dirty) {
                    pages[i].swapOut();
                }
                freePages++;
            }
        }
    }


    public void map(AddressMapping mapping) {
        Page page = getFreePage();
        page.map(mapping);
    }

    public Page getPage(int ppn) {
        return pages[ppn];
    }

    private static final char dbgVM = 'v';
}
