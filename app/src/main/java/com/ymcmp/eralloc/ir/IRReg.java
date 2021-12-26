package com.ymcmp.eralloc.ir;

public final class IRReg implements IRValue {

    public final RegName name;

    public IRReg(RegName name) {
        if (name == null)
            throw new IllegalArgumentException("Register name cannot be null");

        this.name = name;
    }

    @Override
    public String toString() {
        return this.name.toString();
    }

    @Override
    public int hashCode() {
        return this.name.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof IRReg))
            return false;

        return this.name.equals(((IRReg) o).name);
    }
}
