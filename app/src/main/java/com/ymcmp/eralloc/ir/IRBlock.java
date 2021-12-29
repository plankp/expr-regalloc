package com.ymcmp.eralloc.ir;

import java.math.*;
import java.util.*;
import com.ymcmp.eralloc.AtomicCounter;

public final class IRBlock implements Iterable<IRInstr> {

    public static final class Handle implements IRValue {

        private static final AtomicCounter counter = new AtomicCounter();

        private final BigInteger id;
        private String name;

        public final IRBlock block;

        private Handle(IRBlock block) {
            this.block = block;
            this.id = counter.getAndIncrement();
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            if (this.name == null)
                return this.id.toString();
            return this.name + '.' + this.id;
        }
    }

    public final Handle handle = new Handle(this);
    public final List<Register.Virtual> inputs = new ArrayList<>(0);
    public final List<IRInstr> instrs = new ArrayList<>();

    @Override
    public ListIterator<IRInstr> iterator() {
        return this.instrs.listIterator();
    }

    public ListIterator<IRInstr> iterator(int index) {
        return this.instrs.listIterator(index);
    }

    public int size() {
        return this.instrs.size();
    }

    public boolean isEmpty() {
        return this.instrs.isEmpty();
    }

    public String getName() {
        return this.handle.toString();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(this.getName()).append('(');
        final Iterator<Register.Virtual> it = this.inputs.iterator();
        if (it.hasNext()) {
            sb.append(it.next());
            while (it.hasNext())
                sb.append(", ").append(it.next());
        }
        sb.append("):\n");
        for (final IRInstr instr : this.instrs)
            sb.append("    ").append(instr).append('\n');
        return sb.toString();
    }
}
