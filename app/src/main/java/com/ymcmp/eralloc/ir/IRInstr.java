package com.ymcmp.eralloc.ir;

import java.util.*;

public final class IRInstr {

    public static final class Builder {

        private final InstrName opcode;
        private final List<IRReg> defs = new ArrayList<>();
        private final List<IRValue> uses = new ArrayList<>();

        public Builder(InstrName opcode) {
            this.opcode = opcode;
        }

        public Builder addDef(IRReg def) {
            this.defs.add(def);
            return this;
        }

        public Builder addDefs(List<IRReg> defs) {
            this.defs.addAll(defs);
            return this;
        }

        public Builder addDefs(IRReg... defs) {
            return this.addDefs(Arrays.asList(defs));
        }

        public Builder addUse(IRValue use) {
            this.uses.add(use);
            return this;
        }

        public Builder addUses(List<IRValue> uses) {
            this.uses.addAll(uses);
            return this;
        }

        public Builder addUses(IRValue... uses) {
            return this.addUses(Arrays.asList(uses));
        }

        public IRInstr build() {
            final List<IRReg> pdefs = defs.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(defs));
            final List<IRValue> puses = uses.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(uses));
            return new IRInstr(opcode, pdefs, puses);
        }
    }

    public final InstrName opcode;
    public final List<IRReg> defs;
    public final List<IRValue> uses;

    private IRInstr(InstrName opcode, List<IRReg> defs, List<IRValue> uses) {
        this.opcode = opcode;
        this.defs = defs;
        this.uses = uses;
    }

    public static Builder of(InstrName opcode) {
        return new Builder(opcode);
    }

    public static IRInstr makev(InstrName opcode, IRReg out, IRValue... in) {
        return of(opcode).addDef(out).addUses(in).build();
    }

    public static IRInstr make(InstrName opcode, IRValue... in) {
        return of(opcode).addUses(in).build();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        if (!this.defs.isEmpty()) {
            final Iterator<IRReg> it = this.defs.iterator();
            sb.append(it.next());
            while (it.hasNext())
                sb.append(", ").append(it.next());

            sb.append(" = ");
        }

        sb.append(this.opcode);

        if (!this.uses.isEmpty()) {
            final Iterator<IRValue> it = this.uses.iterator();
            sb.append(' ').append(it.next());
            while (it.hasNext())
                sb.append(", ").append(it.next());
        }

        return sb.toString();
    }
}
