package com.ymcmp.eralloc.target.generic.types;

import com.ymcmp.eralloc.target.generic.DataType;

public final class FloatType extends DataType {

    // Why not make FloatType an enum directly?
    // Because DataType is abstract class (and not interface)
    public enum Name {
        FLOAT(32),
        DOUBLE(64);

        public final int bits;

        private Name(int bits) {
            this.bits = bits;
        }
    }

    public final Name name;

    public FloatType(FloatType.Name name) {
        if (name == null)
            throw new IllegalArgumentException("Invalid float type: " + name);

        this.name = name;
    }

    @Override
    public int width() {
        return this.name.bits;
    }

    @Override
    public int hashCode() {
        return this.name.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FloatType))
            return false;

        return this.name == ((FloatType) o).name;
    }

    @Override
    public String toString() {
        // for now this is sufficient
        return this.name.toString();
    }
}
