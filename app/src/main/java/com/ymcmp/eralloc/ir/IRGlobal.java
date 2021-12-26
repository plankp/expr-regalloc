package com.ymcmp.eralloc.ir;

public final class IRGlobal implements IRValue {

    public final String value;

    public IRGlobal(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "(GLOBAL " + this.value + ")";
    }
}
