package com.ymcmp.eralloc.ir;

import com.ymcmp.eralloc.*;

public class IRContext {

    private final AtomicCounter vregId = new AtomicCounter();
    private final AtomicCounter frameIndexId = new AtomicCounter();

    public Register.Virtual newVReg(RegisterType info) {
        return new Register.Virtual(this.vregId.getAndIncrement(), info);
    }

    public IRFrameIndex.Info newFrameIndex(long size) {
        return new IRFrameIndex.Info(this.frameIndexId.getAndIncrement(), size);
    }
}
