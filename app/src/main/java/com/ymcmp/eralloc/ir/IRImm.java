package com.ymcmp.eralloc.ir;

public final class IRImm implements IRValue {

    public final long value;

    public IRImm(long value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return '#' + Long.toHexString(this.value);
    }
}
