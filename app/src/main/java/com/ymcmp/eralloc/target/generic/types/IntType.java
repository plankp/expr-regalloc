package com.ymcmp.eralloc.target.generic.types;

import com.ymcmp.eralloc.target.generic.DataType;

public final class IntType extends DataType {

    public final int bits;

    public IntType(int bits) {
        if (bits < 1)
            throw new IllegalArgumentException("Too few bits for integers: " + bits);

        this.bits = bits;
    }

    @Override
    public int width() {
        return this.bits;
    }

    @Override
    public boolean isInt() {
        return true;
    }

    @Override
    public boolean isInt(int bits) {
        return this.bits == bits;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(this.bits);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof IntType))
            return false;

        return this.bits == ((IntType) o).bits;
    }

    @Override
    public String toString() {
        return "i" + this.bits;
    }
}
