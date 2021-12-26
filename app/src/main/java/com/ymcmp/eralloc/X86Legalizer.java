package com.ymcmp.eralloc;

import java.util.*;
import com.ymcmp.eralloc.ir.*;
import static com.ymcmp.eralloc.ir.InstrName.Generic.*;

public final class X86Legalizer {

    public X86Legalizer(IRContext ctx) {
    }

    public void legalize(List<IRInstr> block) {
        final ListIterator<IRInstr> it = block.listIterator();
        while (it.hasNext()) {
            final IRInstr inst = it.next();
            switch ((InstrName.Generic) inst.opcode) {
            case ADD:
                this.rewriteTwoAddress(it, X86Instr.ADD, inst);
                break;
            case SUB:
                this.rewriteTwoAddress(it, X86Instr.SUB, inst);
                break;
            case MUL:
                this.rewriteTwoAddress(it, X86Instr.IMUL, inst);
                break;
            case DIV: {
                // transforms
                //   %z = div %a, %b
                // into
                //   eax = copy %a
                //   edx, eax = X86::cdq eax
                //   edx, eax = X86::idiv edx, eax, %b
                //   %z = copy eax
                final IRReg rEAX = new IRReg(X86Register.EAX);
                final IRReg rEDX = new IRReg(X86Register.EDX);
                it.set(IRInstr.makev(COPY, rEAX, inst.uses.get(0)));
                it.add(IRInstr.of(X86Instr.CDQ)
                        .addDefs(rEDX, rEAX)
                        .addUse(rEAX)
                        .build());
                it.add(IRInstr.of(X86Instr.IDIV)
                        .addDefs(rEDX, rEAX)
                        .addUses(rEDX, rEAX, inst.uses.get(1))
                        .build());
                it.add(IRInstr.of(COPY)
                        .addDefs(inst.defs.get(0))
                        .addUse(rEAX)
                        .build());
                break;
            }
            case SHL: {
                // transforms
                //   %z = shl %a, %b
                // into
                //   ecx = copy %b
                //   %z = X86::shl %a, cl

                final IRReg rECX = new IRReg(X86Register.ECX);
                final IRReg rCL = new IRReg(X86Register.CL);
                it.set(IRInstr.makev(COPY, rECX, inst.uses.get(1)));
                it.add(IRInstr.makev(X86Instr.SHL, inst.defs.get(0), inst.uses.get(0), rCL));
                break;
            }
            }
        }
    }

    private void rewriteTwoAddress(ListIterator<IRInstr> it, X86Instr opcode, IRInstr inst) {
        // transforms
        //   %z = op %a, %b
        // into
        //   %z = X86::op %a, %b

        it.set(IRInstr.makev(opcode, inst.defs.get(0), inst.uses.get(0), inst.uses.get(1)));
    }
}