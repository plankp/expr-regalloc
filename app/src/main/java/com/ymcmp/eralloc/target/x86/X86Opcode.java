package com.ymcmp.eralloc.target.x86;

import java.util.*;
import com.ymcmp.eralloc.ir.Opcode;

public enum X86Opcode implements Opcode {

    ADD,
    SUB,
    IMUL,
    CDQ,
    IDIV,
    SHL,
    CMP,
    PUSH,
    POP,

    // temporary hack
    PLOAD;

    @Override
    public String toString() {
        return "X86::" + this.name();
    }

    @Override
    public Map<Integer, Integer> getTiedDefs() {
        switch (this) {
        case ADD:
        case SUB:
        case IMUL:
        case SHL:
            // d0 = add u0, u1   (d0 and u0 tied)
            return Collections.singletonMap(0, 0);
        default:
            return Collections.emptyMap();
        }
    }
}
