package nachos.vm;

import nachos.machine.TranslationEntry;

public abstract class AddressMapping {
    final protected SwapBlockData swapDisc = SwapBlockData.getSwapBlockData();
    final TranslationEntry entry;
    protected Page page;

    public boolean isReadOnly() {
        return readOnly;
    }

    protected boolean readOnly;

    AddressMapping(TranslationEntry entry) {
        this.entry = entry;
    }

    public void map(Page page) {
        this.page = page;
        entry.ppn = page.ppn;
        entry.valid = true;
    }

    public void unmap() {
        entry.valid = false;
    }

    public void updateEntryHW(TranslationEntry entry) {
        this.entry.used = entry.used;
        this.entry.dirty = entry.dirty;
    }


    abstract public void loadPageData();

    abstract public void storedPageData();
}
