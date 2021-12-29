package com.ymcmp.eralloc.ir;

import java.util.*;

public interface Opcode {

    /**
     * A constraint for register allocators to assign multiple virtual
     * registers to the same physical register.
     *
     * @return mapping from def index to use index
     */
    public default Map<Integer, Integer> getTiedDefs() {
        return Collections.emptyMap();
    }

    public default boolean isTerminator() {
        return false;
    }
}
