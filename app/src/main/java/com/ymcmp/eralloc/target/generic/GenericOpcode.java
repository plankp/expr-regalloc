package com.ymcmp.eralloc.target.generic;

import java.util.*;
import com.ymcmp.eralloc.ir.*;

public enum GenericOpcode implements Opcode {

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
    JMP,
    JNZ,
    RET,

    LOAD,
    STORE,
    SAVE,
    RELOAD;

    @Override
    public boolean isTerminator() {
        switch (this) {
        case JMP:
        case JNZ:
        case RET:
            return true;
        default:
            return false;
        }
    }
}
