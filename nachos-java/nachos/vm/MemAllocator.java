package nachos.vm;

import nachos.machine.Lib;
import nachos.machine.TranslationEntry;

public class MemAllocator {
    final private Page pages[];
    private int pointer;

    MemAllocator(Page pages[]) {
        pointer = 0;
        this.pages = pages;
    }

    private void nextPointer() {
        //make it a ring
        pointer++;
        if (pointer == pages.length) {
            pointer = 0;
        }
    }


    public Page getFreePage() {
        int currentPointer = pointer;
        if (pages[pointer].free) {
            return pages[pointer];
        }
        nextPointer();
        while (pointer != currentPointer) {
            if (pages[pointer].free) {
                return pages[pointer];
            }
            nextPointer();
        }
        return null;
    }

}
