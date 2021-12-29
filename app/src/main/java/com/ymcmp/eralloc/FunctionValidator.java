package com.ymcmp.eralloc;

import java.util.*;
import com.ymcmp.eralloc.ir.*;

public final class FunctionValidator {

    private static final class BlockInfo {

        public final Set<Register.Virtual> liveIns;
        public final Set<Register.Virtual> defs;

        public BlockInfo(Set<Register.Virtual> liveIns, Set<Register.Virtual> defs) {
            this.liveIns = liveIns;
            this.defs = defs;
        }
    }

    private static final class VisitInfo {

        public final IRBlock block;
        public final Set<Register.Virtual> defs;
        public final Set<IRBlock> visited;

        public VisitInfo(IRBlock block, Set<Register.Virtual> defs, Set<IRBlock> visited) {
            this.block = block;
            this.defs = defs;
            this.visited = visited;
        }
    }

    public void validate(IRFunction fn) {
        if (fn.isEmpty())
            return; // nothing to validate

        final Map<IRBlock, BlockInfo> infos = new HashMap<>();
        for (final IRBlock block : fn)
            infos.put(block, this.validate(block));

        final Graph<IRBlock.Handle> cfg = new FlowGraphGenerator().compute(fn);

        this.checkGlobalSSA(Collections.unmodifiableCollection(infos.values()));
        this.traverseCFG(cfg, Collections.unmodifiableMap(infos), fn.blocks.get(0));
    }

    private BlockInfo validate(IRBlock block) {
        final Set<Register.Virtual> liveIns = new HashSet<>();
        final Set<Register.Virtual> defs = new HashSet<>();

        for (final Register.Virtual phi : block.inputs)
            addDef(defs, phi);

        boolean seenTerminator = false;
        for (final IRInstr instr : block) {
            for (final IRValue use : instr.uses)
                asVReg(use).filter(reg -> !defs.contains(reg)).ifPresent(liveIns::add);

            for (final IRReg def : instr.defs)
                asVReg(def).ifPresent(reg -> addDef(defs, reg));

            if (!instr.opcode.isTerminator()) {
                if (seenTerminator)
                    throw new RuntimeException("Illegal instruction after terminator");
            } else {
                seenTerminator = true;
                this.validateBranch(instr);
            }
        }

        if (!seenTerminator)
            throw new RuntimeException("Unterminated block");

        return new BlockInfo(Collections.unmodifiableSet(liveIns),
                             Collections.unmodifiableSet(defs));
    }

    private static void addDef(Set<Register.Virtual> defs, Register.Virtual vreg) {
        if (!defs.add(vreg))
            throw new RuntimeException("Duplicate definition of virtual register " + vreg);
    }

    public static Optional<Register.Virtual> asVReg(IRValue value) {
        if (value == null)
            return Optional.empty();

        try {
            return Optional.of((Register.Virtual) ((IRReg) value).name);
        } catch (ClassCastException ex) {
            return Optional.empty();
        }
    }

    private void validateBranch(final IRInstr instr) {
        if (!instr.opcode.isTerminator())
            return;

        final Set<IRBlock> next = new HashSet<>();
        final Iterator<IRValue> vals = instr.uses.iterator();
        while (vals.hasNext()) {
            final IRValue use = vals.next();
            if (!(use instanceof IRBlock.Handle))
                continue;

            final IRBlock.Handle handle = (IRBlock.Handle) use;

            // Check if the operands match the phi edges
            final Iterator<Register.Virtual> phis = handle.block.inputs.iterator();
            while (phis.hasNext() && vals.hasNext()) {
                // TODO: type checking (so we don't shove a i32 to a i8)
                phis.next();
                vals.next();
            }
            if (phis.hasNext() || vals.hasNext())
                throw new RuntimeException("Mismatched phi node value");
        }
    }

    private void checkGlobalSSA(Collection<BlockInfo> infos) {
        // Check for repeated definitions across different blocks:
        //
        // A        Happens if both B and B' define the same register.
        // |\
        // B B'     Right... if A to B and B' is conditional, then maybe only
        // |/       one definition is alive, but hey... it's SSA.
        // C

        final Set<Register.Virtual> defs = new HashSet<>();
        for (final BlockInfo info : infos)
            for (final Register.Virtual def : info.defs)
                if (!defs.add(def))
                    throw new RuntimeException("Duplicate definition of virtual register " + def);
    }

    public void traverseCFG(Graph<IRBlock.Handle> cfg, Map<IRBlock, BlockInfo> infos, IRBlock start) {
        // Traverse the graph as follows:
        //   It's based on DFS
        //   The difference is that it revisits forward edges:
        //
        //    A         Normally we'd do (A B D E C) and done for DFS.
        //    | \       However, since we neeed to make sure the path via C
        //    B  C      is correctly dominated, we need to do
        //    | /       (A B D E C D E).
        //    D         Unfortunately, we need to visit much more nodes:
        //    |         Consider the case if E back-edges to B.
        //    E

        final Set<IRBlock> allVisited = new HashSet<>();
        final Deque<VisitInfo> stack = new ArrayDeque<>();
        stack.push(new VisitInfo(start, Collections.emptySet(), Collections.emptySet()));
        while (!stack.isEmpty()) {
            final VisitInfo tuple = stack.pop();
            final IRBlock block = tuple.block;
            if (tuple.visited.contains(block))
                continue; // don't revisit a back edge

            // XXX: We should be able to eliminate a few set copy's.
            final Set<IRBlock> visited = new HashSet<>(tuple.visited);
            final Set<Register.Virtual> defs = new HashSet<>(tuple.defs);
            final BlockInfo info = infos.get(block);

            // Visit this block for this path
            visited.add(block);
            allVisited.add(block);

            // Check the promised live-ins (the predecessor defs)
            for (final Register.Virtual reg : info.liveIns)
                if (!defs.contains(reg))
                    throw new RuntimeException("Use of undefined virtual register " + reg);

            // Add the defs before traversing the successors
            defs.addAll(info.defs);

            // Queue the successors for visiting
            for (final IRBlock.Handle next : cfg.getNeighbours(block.handle))
                stack.push(new VisitInfo(next.block, defs, visited));
        }

        // Make sure every block has been visited
        for (final IRBlock block : infos.keySet())
            if (!allVisited.contains(block))
                throw new RuntimeException("Block " + block.getName() + " is unused");
    }
}
