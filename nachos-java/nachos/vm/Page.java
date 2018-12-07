package nachos.vm;

import nachos.machine.TranslationEntry;

public class Page {
    AddressMapping mappingEntry;
    boolean free;
    final int ppn;

    Page(int ppn) {
        this.free = true;
        this.ppn = ppn;
    }

    public void map(AddressMapping mappingEntry) {
        free = false;
        this.mappingEntry = mappingEntry;
        mappingEntry.map(this);
    }

    public void unmap() {
        mappingEntry.unmap();
        free = true;
    }

    public void swapOut() {
        mappingEntry.storedPageData();
        mappingEntry.entry.valid = false;
    }

}
