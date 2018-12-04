package nachos.vm;

import nachos.machine.TranslationEntry;

public class InvertedPageTableNode {
    final private int processId;

    public int getProcessId() {
        return processId;
    }

    public TranslationEntry getPage() {
        return page;
    }

    final private TranslationEntry page;

    InvertedPageTableNode(int processId, TranslationEntry page) {
        this.processId = processId;
        this.page = page;
    }
}
