package com.ymcmp.eralloc.ir;

import java.math.BigInteger;

public final class IRFrameIndex implements IRValue {

    public static final class Info {

        public final BigInteger id;
        public final long size;

        protected Info(BigInteger id, long size) {
            // get this via an IRContext
            if (size <= 0)
                throw new IllegalArgumentException("Frame element must have a positive size");

            this.id = id;
            this.size = size;
        }

        @Override
        public String toString() {
            return "(FRAME " + this.id + " " + this.size + ")";
        }
    }

    public final Info info;
    public final long offset;

    public IRFrameIndex(Info info, long offset) {
        this.info = info;
        this.offset = offset;
    }

    @Override
    public String toString() {
        return this.info.toString();
    }
}
