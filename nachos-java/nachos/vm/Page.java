package nachos.vm;

import nachos.machine.Lib;
import nachos.machine.TranslationEntry;

public class Page {
    int processId;
    TranslationEntry mappingEntry;
    boolean free;
    final int ppn;

    Page(int ppn) {
        this.free = true;
        this.ppn = ppn;
    }

    public void map(int processId, TranslationEntry entry) {
        free = false;
        this.processId = processId;
        this.mappingEntry = entry;
        entry.valid = true;
        entry.ppn = ppn;
    }

    public void unmap() {
        free = true;
    }

    public void updateEntry(TranslationEntry entry) {
        if (!free) {
            mappingEntry.dirty = entry.dirty;
            mappingEntry.used = entry.used;
        }
    }
}
