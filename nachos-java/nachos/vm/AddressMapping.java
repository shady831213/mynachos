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
        //hardware set only
        if (entry.used) {
            this.entry.used = entry.used;
        }
        if (entry.dirty) {
            this.entry.dirty = entry.dirty;
        }
    }


    abstract public void loadPageData();

    abstract public void storedPageData();

    abstract public int readVirtualMemoryInPage(int vaddr, byte[] data, int offset,
                                                int length);

    abstract public int writeVirtualMemoryInPage(int vaddr, byte[] data, int offset,
                                                 int length);
}
