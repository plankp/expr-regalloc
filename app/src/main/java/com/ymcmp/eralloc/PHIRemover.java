package com.ymcmp.eralloc;

import java.util.*;
import com.ymcmp.eralloc.ir.*;
import com.ymcmp.eralloc.target.generic.*;

public final class PHIRemover {

    private final IRContext ctx;

    public PHIRemover(IRContext ctx) {
        this.ctx = ctx;
    }

    public void convert(IRFunction fn) {
        final Map<Register.Virtual, Register.Virtual> primed = new HashMap<>();

        // Example:
        // a:
        //   JMP block, #10
        // b:
        //   JMP block, #11
        // block(%v):
        //   ...
        //
        // Becomes:
        // a:
        //   %v' = COPY #10
        //   JMP block
        // b:
        //   %v' = COPY #11     <- and we (intentionally) broke SSA
        //   JMP block
        // block():
        //   %v = COPY %v'

        // Emit the (%v' = COPY ...) instructions and fixup the branches
        for (final IRBlock block : fn) {
            final ListIterator<IRInstr> it = block.iterator(block.size());
            while (it.hasPrevious()) {
                final IRInstr instr = it.previous();
                if (!instr.opcode.isTerminator())
                    break;

                final Iterator<IRValue> vals = instr.uses.iterator();
                while (vals.hasNext()) {
                    final IRValue use = vals.next();
                    if (!(use instanceof IRBlock.Handle))
                        continue;

                    final IRBlock.Handle handle = (IRBlock.Handle) use;

                    // Check if the operands match the phi edges
                    final Iterator<Register.Virtual> phis = handle.block.inputs.iterator();
                    while (phis.hasNext() && vals.hasNext()) {
                        final Register.Virtual prime = primed.computeIfAbsent(phis.next(), r -> this.ctx.newVReg(r.getInfo()));
                        final IRValue value = vals.next();
                        vals.remove();

                        it.add(IRInstr.of(GenericOpcode.COPY)
                                .addDef(new IRReg(prime))
                                .addUse(value)
                                .build());
                        it.previous();
                    }
                }
            }
        }

        // Emit the (%v = COPY %v') instructions and remove the phi edges
        for (final IRBlock block : fn) {
            final Iterator<Register.Virtual> phis = block.inputs.iterator();
            while (phis.hasNext()) {
                final Register.Virtual phi = phis.next();
                phis.remove();

                block.instrs.add(0, IRInstr.of(GenericOpcode.COPY)
                        .addDef(new IRReg(phi))
                        .addUse(new IRReg(primed.get(phi)))
                        .build());
            }
        }
    }
}
