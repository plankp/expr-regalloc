package com.ymcmp.eralloc.ir;

import java.util.*;

public interface InstrName {

    /**
     * A constraint for register allocators to give multiple virtual registers
     * the same physical register.
     *
     * @return mapping from def index to use index
     */
    public default Map<Integer, Integer> getTiedDefs() {
        return Collections.emptyMap();
    }

    public enum Generic implements InstrName {

        COPY,

        ADD,
        SUB,

        MUL,
        DIV,
        REM,

        SHL,
        SRA,
        SRL,

        CALL,

        LOAD,
        STORE,
        SAVE,
        RELOAD;
    }
}