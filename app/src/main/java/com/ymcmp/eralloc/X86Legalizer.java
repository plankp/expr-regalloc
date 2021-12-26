package com.ymcmp.eralloc;

import java.util.*;
import com.ymcmp.eralloc.ir.*;

public final class X86Legalizer {

    public X86Legalizer(IRContext ctx) {
    }

    public void legalize(List<IRInstr> block) {
        final ListIterator<IRInstr> it = block.listIterator();
        while (it.hasNext()) {
            final IRInstr inst = it.next();
            switch (inst.opcode) {
            case "add":
                this.rewriteTwoAddress(it, "X86::add", inst);
                break;
            case "sub":
                this.rewriteTwoAddress(it, "X86::sub", inst);
                break;
            case "mul":
                this.rewriteTwoAddress(it, "X86::imul", inst);
                break;
            case "div": {
                // transforms
                //   %z = div %a, %b
                // into
                //   eax = copy %a
                //   edx, eax = X86::cdq eax
                //   edx, eax = X86::idiv edx, eax, %b
                //   %z = copy eax
                final IRReg rEAX = new IRReg(X86Register.EAX);
                final IRReg rEDX = new IRReg(X86Register.EDX);
                it.set(IRInstr.makev("copy", rEAX, inst.uses.get(0)));
                it.add(IRInstr.of("X86::cdq")
                        .addDefs(rEDX, rEAX)
                        .addUse(rEAX)
                        .build());
                it.add(IRInstr.of("X86::idiv")
                        .addDefs(rEDX, rEAX)
                        .addUses(rEDX, rEAX, inst.uses.get(1))
                        .build());
                it.add(IRInstr.of("copy")
                        .addDefs(inst.defs.get(0))
                        .addUse(rEAX)
                        .build());
                break;
            }
            case "shl": {
                // transforms
                //   %z = shl %a, %b
                // into
                //   %z = copy %a
                //   ecx = copy %b
                //   %z = X86::op %z, cl

                final IRReg rECX = new IRReg(X86Register.ECX);
                final IRReg rCL = new IRReg(X86Register.CL);
                it.set(IRInstr.makev("copy", inst.defs.get(0), inst.uses.get(0)));
                it.add(IRInstr.makev("copy", rECX, inst.uses.get(1)));
                it.add(IRInstr.makev("x86::shl", inst.defs.get(0), inst.defs.get(0), rCL));
                break;
            }
            }
        }
    }

    private void rewriteTwoAddress(ListIterator<IRInstr> it, String opcode, IRInstr inst) {
        // transforms
        //   %z = op %a, %b
        // into
        //   %z = copy %a
        //   %z = X86::op %z, %b

        it.set(IRInstr.makev("copy", inst.defs.get(0), inst.uses.get(0)));
        it.add(IRInstr.makev(opcode, inst.defs.get(0), inst.defs.get(0), inst.uses.get(1)));
    }
}