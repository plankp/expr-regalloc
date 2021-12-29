package com.ymcmp.eralloc.target.x86;

import java.util.*;
import java.util.stream.*;
import com.ymcmp.eralloc.*;
import com.ymcmp.eralloc.ir.*;
import com.ymcmp.eralloc.target.generic.DataType;
import com.ymcmp.eralloc.target.generic.GenericOpcode;
import com.ymcmp.eralloc.target.generic.types.*;

public final class X86Legalizer {

    private final IRContext ctx;
    private final Map<Register.Virtual, Register.Virtual> map = new HashMap<>();

    public X86Legalizer(IRContext ctx) {
        this.ctx = ctx;
    }

    public void legalize(IRFunction fn) {
        if (fn.isEmpty())
            return;

        for (final IRBlock block : fn)
            this.legalize(block);

        // Incoming function arguments are represented as PHI edges to the
        // entry block. We get rid of that by adding a new block:
        //
        // f {
        // entry(%a):
        //     ...
        // }
        //
        // Becomes
        //
        // f {
        // entry'():
        //     %a' = ...        <- depends on calling convention
        //     JMP entry, %a'
        // entry(%a):
        //     ...
        // }

        // TODO: calling convention
        final IRBlock oldEntry = fn.blocks.get(0);
        if (oldEntry.inputs.isEmpty())
            return;

        final IRBlock entry = new IRBlock();
        fn.blocks.add(0, entry);

        final List<IRReg> xs = new ArrayList<>();
        long base = 8; // because of (4 byte) base pointer and return address
        for (final Register.Virtual r : oldEntry.inputs) {
            final IRReg reg = new IRReg(this.ctx.newVReg(r.getInfo()));
            entry.instrs.add(IRInstr.of(X86Opcode.PLOAD)
                    .addDef(reg)
                    .addUse(new IRImm(base))
                    .build());
            xs.add(reg);
            base += 4;
        }
        entry.instrs.add(IRInstr.of(GenericOpcode.JMP)
                .addUse(oldEntry.handle)
                .addUses(xs)
                .build());
    }

    public X86RegisterType mapDataType(DataType dt) {
        if (dt.isInt()) {
            switch (dt.width()) {
            case 32:    return X86RegisterType.GR32;
            case 16:    return X86RegisterType.GR16;
            case 8:     return X86RegisterType.GR8;
            }
        }

        throw new AssertionError("Illegal type of " + dt);
    }

    private X86RegisterType convertType(RegisterType ty) {
        if (ty instanceof DataType)
            // TODO:
            // As with all legalizers, we sort of cheat a bit and assume all types are
            // somehow representable (so we don't have i3's for example). This is
            // obviously possible... and not a responsibility of this pass.
            return this.mapDataType((DataType) ty);

        return (X86RegisterType) ty;
    }

    private Register.Virtual convertVReg(Register.Virtual vreg) {
        return this.map.computeIfAbsent(vreg, r -> {
            return this.ctx.newVReg(this.convertType(r.getInfo()));
        });
    }

    @SuppressWarnings({"unchecked"})
    private <T extends IRValue> T convertValue(T v) {
        if (v instanceof IRReg) {
            final IRReg r = (IRReg) v;
            if (r.name instanceof Register.Virtual)
                return (T) new IRReg(this.convertVReg((Register.Virtual) r.name));
        }

        return v; // nothing to do
    }

    private <T extends IRValue> List<T> convertValues(List<T> vs) {
        return vs.stream().map(this::convertValue).collect(Collectors.toList());
    }

    private <T> List<T> drop(List<T> xs, int cnt) {
        return xs.subList(cnt, xs.size());
    }

    private void legalize(IRBlock block) {
        final ListIterator<Register.Virtual> phis = block.inputs.listIterator();
        while (phis.hasNext())
            phis.set(this.convertVReg(phis.next()));

        final ListIterator<IRInstr> it = block.iterator();
        while (it.hasNext()) {
            final IRInstr inst = it.next();
            switch ((GenericOpcode) inst.opcode) {
            case COPY:
            case LOAD:
                it.set(IRInstr.of(inst.opcode)
                        .addDef(this.convertValue(inst.defs.get(0)))
                        .addUse(this.convertValue(inst.uses.get(0)))
                        .build());
                break;
            case JMP:
                it.set(IRInstr.of(inst.opcode)
                        .addUses(this.convertValues(inst.uses))
                        .build());
                break;
            case JNZ:
                // assume we don't know about the test instr.
                it.set(IRInstr.of(X86Opcode.CMP)
                        .addDef(new IRReg(X86Register.EFLAGS))
                        .addUse(this.convertValue(inst.uses.get(0)))
                        .addUse(new IRImm(0))
                        .build());
                it.add(IRInstr.of(inst.opcode)
                        .addUse(new IRReg(X86Register.EFLAGS))
                        .addUses(this.convertValues(drop(inst.uses, 1)))
                        .build());
                break;
            case ADD:
                it.set(IRInstr.of(X86Opcode.ADD)
                        .addDef(this.convertValue(inst.defs.get(0)))
                        .addUse(this.convertValue(inst.uses.get(0)))
                        .addUse(this.convertValue(inst.uses.get(1)))
                        .build());
                break;
            case SUB:
                it.set(IRInstr.of(X86Opcode.SUB)
                        .addDef(this.convertValue(inst.defs.get(0)))
                        .addUse(this.convertValue(inst.uses.get(0)))
                        .addUse(this.convertValue(inst.uses.get(1)))
                        .build());
                break;
            case MUL:
                it.set(IRInstr.of(X86Opcode.IMUL)
                        .addDef(this.convertValue(inst.defs.get(0)))
                        .addUse(this.convertValue(inst.uses.get(0)))
                        .addUse(this.convertValue(inst.uses.get(1)))
                        .build());
                break;
            case SHL:
                it.set(IRInstr.of(GenericOpcode.COPY)
                        .addDef(new IRReg(X86Register.ECX))
                        .addUse(this.convertValue(inst.uses.get(1)))
                        .build());
                it.add(IRInstr.of(X86Opcode.SHL)
                        .addDef(this.convertValue(inst.defs.get(0)))
                        .addUse(this.convertValue(inst.uses.get(0)))
                        .addUse(new IRReg(X86Register.CL))
                        .build());
                break;
            case DIV:
                it.set(IRInstr.of(GenericOpcode.COPY)
                        .addDef(new IRReg(X86Register.EAX))
                        .addUse(this.convertValue(inst.uses.get(0)))
                        .build());
                it.add(IRInstr.of(X86Opcode.CDQ)
                        .addDef(new IRReg(X86Register.EDX))
                        .addDef(new IRReg(X86Register.EAX))
                        .addUse(new IRReg(X86Register.EAX))
                        .build());
                it.add(IRInstr.of(X86Opcode.IDIV)
                        .addDef(new IRReg(X86Register.EDX))
                        .addDef(new IRReg(X86Register.EAX))
                        .addUse(new IRReg(X86Register.EDX))
                        .addUse(new IRReg(X86Register.EAX))
                        .addUse(this.convertValue(inst.uses.get(1)))
                        .build());
                it.add(IRInstr.of(GenericOpcode.COPY)
                        .addDef(this.convertValue(inst.defs.get(0)))
                        .addUse(new IRReg(X86Register.EAX))
                        .build());
                break;
            case REM:
                it.set(IRInstr.of(GenericOpcode.COPY)
                        .addDef(new IRReg(X86Register.EAX))
                        .addUse(this.convertValue(inst.uses.get(0)))
                        .build());
                it.add(IRInstr.of(X86Opcode.CDQ)
                        .addDef(new IRReg(X86Register.EDX))
                        .addDef(new IRReg(X86Register.EAX))
                        .addUse(new IRReg(X86Register.EAX))
                        .build());
                it.add(IRInstr.of(X86Opcode.IDIV)
                        .addDef(new IRReg(X86Register.EDX))
                        .addDef(new IRReg(X86Register.EAX))
                        .addUse(new IRReg(X86Register.EDX))
                        .addUse(new IRReg(X86Register.EAX))
                        .addUse(this.convertValue(inst.uses.get(1)))
                        .build());
                it.add(IRInstr.of(GenericOpcode.COPY)
                        .addDef(this.convertValue(inst.defs.get(0)))
                        .addUse(new IRReg(X86Register.EDX))
                        .build());
                break;
            case RET:
                // TODO: calling convention
                if (!inst.uses.isEmpty()) {
                    it.set(IRInstr.of(GenericOpcode.COPY)
                            .addDef(new IRReg(X86Register.EAX))
                            .addUse(this.convertValue(inst.uses.get(0)))
                            .build());
                    it.add(IRInstr.of(inst.opcode)
                            .addUse(new IRReg(X86Register.EAX))
                            .build());
                }
                break;
            case CALL:
                // TODO: calling convention
                it.previous();
                for (int i = inst.uses.size(); i-- > 1; ) {
                    it.add(IRInstr.of(X86Opcode.PUSH)
                            .addUse(this.convertValue(inst.uses.get(i)))
                            // Clobbers?
                            .build());
                }
                it.next();
                it.set(IRInstr.of(GenericOpcode.CALL)
                        .addDef(new IRReg(X86Register.EAX))
                        .addDef(new IRReg(X86Register.ECX))
                        .addDef(new IRReg(X86Register.EDX))
                        .addUse(this.convertValue(inst.uses.get(0)))
                        .build());
                for (int i = inst.uses.size(); i-- > 1; ) {
                    it.add(IRInstr.of(X86Opcode.POP)
                            .addDef(new IRReg(this.ctx.newVReg(X86RegisterType.GR32)))
                            .build());
                }
                it.add(IRInstr.of(GenericOpcode.COPY)
                        .addDef(this.convertValue(inst.defs.get(0)))
                        .addUse(new IRReg(X86Register.EAX))
                        .build());
                break;
            default:
                throw new UnsupportedOperationException("Unsupported generic opcode: " + inst.opcode);
            }
        }
    }
}
