package com.ymcmp.eralloc;

import java.util.*;
import com.ymcmp.eralloc.ir.*;

public final class FlowGraphGenerator {

    public Graph<IRBlock.Handle> compute(IRFunction fn) {
        final Graph<IRBlock.Handle> g = new Graph<>();
        for (final IRBlock block : fn) {
            g.addNode(block.handle);

            for (final IRBlock.Handle next : this.collectSuccessors(block))
                g.addDirectedEdge(block.handle, next);
        }

        return g;
    }

    public Set<IRBlock.Handle> collectSuccessors(final IRBlock block) {
        final Set<IRBlock.Handle> next = new HashSet<>();
        final ListIterator<IRInstr> it = block.iterator(block.size());
        while (it.hasPrevious()) {
            final IRInstr instr = it.previous();
            if (!instr.opcode.isTerminator())
                break;

            next.addAll(this.collectSuccessors(instr));
        }
        return next;
    }

    public Set<IRBlock.Handle> collectSuccessors(final IRInstr instr) {
        if (!instr.opcode.isTerminator())
            return Collections.emptySet();

        final Set<IRBlock.Handle> next = new HashSet<>();
        final Iterator<IRValue> vals = instr.uses.iterator();
        while (vals.hasNext()) {
            final IRValue use = vals.next();
            if (use instanceof IRBlock.Handle)
                next.add((IRBlock.Handle) use);
        }

        return next;
    }
}
