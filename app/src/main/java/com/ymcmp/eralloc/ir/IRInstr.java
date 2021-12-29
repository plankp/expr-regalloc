package com.ymcmp.eralloc.ir;

import java.util.*;

public final class IRInstr {

    public static final class Builder {

        private final Opcode opcode;
        private final List<IRReg> defs = new ArrayList<>();
        private final List<IRValue> uses = new ArrayList<>();

        public Builder(Opcode opcode) {
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

        public Builder addUses(List<? extends IRValue> uses) {
            this.uses.addAll(uses);
            return this;
        }

        public Builder addUses(IRValue... uses) {
            return this.addUses(Arrays.asList(uses));
        }

        public IRInstr build() {
            final ArrayList<IRReg> pdefs = new ArrayList<>(defs);
            pdefs.trimToSize();

            final ArrayList<IRValue> puses = new ArrayList<>(uses);
            puses.trimToSize();

            return new IRInstr(opcode, pdefs, puses);
        }
    }

    public final Opcode opcode;
    public final List<IRReg> defs;
    public final List<IRValue> uses;

    private IRInstr(Opcode opcode, List<IRReg> defs, List<IRValue> uses) {
        this.opcode = opcode;
        this.defs = defs;
        this.uses = uses;
    }

    public static Builder of(Opcode opcode) {
        return new Builder(opcode);
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
