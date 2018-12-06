package nachos.vm;

import nachos.machine.Lib;
import nachos.machine.TranslationEntry;

public class MemMap {

    private Page pages[];
    private MemAllocator allocator;

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

    protected Page getFreePage() {
        Page page = allocator.getFreePage();
        if (page == null) {
            swap();
            return allocator.getFreePage();
        }
        return page;
    }

    public void swap() {
        for (int i = 0; i < pages.length; i++) {
            pages[i].unmap();
            pages[i].swapOut();
        }
    }

    public void map(AddressMapping mapping) {
        Page page = getFreePage();
        Lib.assertTrue(page != null, "OOM!");
        page.map(mapping);
    }

    public Page getPage(int ppn) {
        return pages[ppn];
    }
}
