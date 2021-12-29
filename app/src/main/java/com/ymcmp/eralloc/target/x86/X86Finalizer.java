package com.ymcmp.eralloc.target.x86;

import java.util.*;
import com.ymcmp.eralloc.*;
import com.ymcmp.eralloc.ir.*;
import com.ymcmp.eralloc.target.generic.GenericOpcode;

public final class X86Finalizer {

    private final Map<IRFrameIndex.Info, Long> location = new HashMap<>();
    private long max;

    public X86Finalizer(IRContext ctx) {
        // probably wont need the context, but just in case.
    }

    public List<String> finalize(IRFunction fn) {
        this.location.clear();
        this.max = 0;

        final List<String> result = new ArrayList<>();
        result.add(fn.getName() + ":");

        // Emit a dummy prologue
        result.add("    pushl %ebp");
        result.add("    movl %esp, %ebp");

        // Emit the code in each block
        for (final IRBlock block : fn) {
            result.add(".L" + block.getName() + ":");
            for (final IRInstr instr : block)
                for (final String inst : convertInstr(instr))
                    result.add("    " + inst);
        }

        // Patch up the prologue
        if (this.max != 0)
            result.add(2, "    subl $" + this.max + ", %esp");

        return result;
    }

    private String convertFI(IRFrameIndex fi) {
        final long offset = this.location.computeIfAbsent(fi.info, info -> {
            // round the size (in bits) to the next byte
            final long incr = (info.size + 7) / 8;

            // assume no one cares about alignment, which is wrong even in x86
            // because SSE does...
            final long base = this.max;

            this.max = base + incr;

            return -base;
        }) + fi.offset;

        if (offset == 0)
            return "(%ebp)";
        return offset + "(%ebp)";
    }

    private String convertValue(IRValue value) {
        if (value instanceof IRFrameIndex)
            // frame index is weird and requires special handling
            // see this#convertFI(IRFrameIndex)
            throw new UnsupportedOperationException();

        if (value instanceof IRReg)
            return "%" + value.toString().toLowerCase();
        if (value instanceof IRBlock.Handle)
            return ".L" + value.toString();
        if (value instanceof IRImm)
            return "$" + ((IRImm) value).value;
        if (value instanceof IRGlobal)
            return "$" + ((IRGlobal) value).value;
        return value.toString();
    }

    private List<String> convertInstr(IRInstr instr) {
        if (instr.opcode instanceof GenericOpcode) {
            switch ((GenericOpcode) instr.opcode) {
            case COPY:
                return Arrays.asList(
                    "movl " + this.convertValue(instr.uses.get(0))
                        + ", " + this.convertValue(instr.defs.get(0))
                );
            case JMP:
                // it's just jmp (jmpl is something else)
                return Arrays.asList(
                    "jmp " + this.convertValue(instr.uses.get(0))
                );
            case CALL:
                return Arrays.asList(
                    "calll " + this.convertValue(instr.uses.get(0))
                );
            case RET:
                return Arrays.asList(
                    "leave",
                    "retl"
                );
            case LOAD:
            case RELOAD:
                if (instr.uses.get(0) instanceof IRFrameIndex)
                    return Arrays.asList(
                        "movl " + this.convertFI((IRFrameIndex) instr.uses.get(0))
                            + ", " + this.convertValue(instr.defs.get(0))
                    );
                return Arrays.asList(
                    "movl (" + this.convertValue(instr.uses.get(0))
                        + "), " + this.convertValue(instr.defs.get(0))
                );
            case STORE:
            case SAVE:
                if (instr.uses.get(0) instanceof IRFrameIndex)
                    return Arrays.asList(
                        "movl " + this.convertValue(instr.uses.get(1))
                            + ", " + this.convertFI((IRFrameIndex) instr.uses.get(0))
                    );
                return Arrays.asList(
                    "movl " + this.convertValue(instr.uses.get(1))
                        + ", (" + this.convertValue(instr.uses.get(0)) + ")"
                );
            default:
                System.out.println(instr);
                throw new UnsupportedOperationException();
            }
        } else {
            switch ((X86Opcode) instr.opcode) {
            case ADD:
                return Arrays.asList(
                    "addl " + this.convertValue(instr.uses.get(1))
                        + ", " + this.convertValue(instr.uses.get(0))
                );
            case SUB:
                return Arrays.asList(
                    "subl " + this.convertValue(instr.uses.get(1))
                        + ", " + this.convertValue(instr.uses.get(0))
                );
            case IMUL:
                return Arrays.asList(
                    "imull " + this.convertValue(instr.uses.get(1))
                        + ", " + this.convertValue(instr.uses.get(0))
                );
            case CMP:
                return Arrays.asList(
                    "cmpl " + this.convertValue(instr.uses.get(1))
                        + ", " + this.convertValue(instr.uses.get(0))
                );
            case CDQ:
                return Arrays.asList(
                    "cltd"
                );
            case IDIV:
                return Arrays.asList(
                    "idivl " + this.convertValue(instr.uses.get(2))
                );
            case SHL:
                return Arrays.asList(
                    "shll " + this.convertValue(instr.uses.get(1))
                        + ", " + this.convertValue(instr.uses.get(0))
                );
            case PUSH:
                return Arrays.asList(
                    "pushl " + this.convertValue(instr.uses.get(0))
                );
            case POP:
                return Arrays.asList(
                    "popl " + this.convertValue(instr.defs.get(0))
                );
            case PLOAD:
                return Arrays.asList(
                    "movl " + ((IRImm) instr.uses.get(0)).value + "(%ebp)"
                        + ", " + this.convertValue(instr.defs.get(0))
                );
            default:
                System.out.println(instr);
                throw new UnsupportedOperationException();
            }
        }
    }
}
