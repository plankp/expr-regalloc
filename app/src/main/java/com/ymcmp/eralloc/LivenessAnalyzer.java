package com.ymcmp.eralloc;

import java.util.*;
import java.util.stream.*;
import com.ymcmp.eralloc.ir.*;

public final class LivenessAnalyzer {

    public static final class LiveRange {

        public final IRBlock block;
        public final int start;
        public final int end;

        public LiveRange(IRBlock block, int start, int end) {
            this.block = block;
            this.start = start;
            this.end = end;
        }

        public boolean overlaps(LiveRange range) {
            // take the complement of the disjoint condition:
            //
            // +----x
            //       +-----x

            return block.equals(range.block)
                && !((this.start < range.start && this.end <= range.start)
                    || (range.start < this.start && range.end <= this.start));
        }

        @Override
        public String toString() {
            return block.handle + ":[" + start + "," + end + "]";
        }
    }

    private static final class BlockInfo {

        public final Set<Register.Virtual> liveIns;
        public final Set<Register.Virtual> defs;
        public final Map<Register, List<LiveRange>> ranges;

        public BlockInfo(Set<Register.Virtual> liveIns, Set<Register.Virtual> defs, Map<Register, List<LiveRange>> ranges) {
            this.liveIns = liveIns;
            this.defs = defs;
            this.ranges = ranges;
        }
    }

    private static final class VisitInfo {

        public final IRBlock block;
        public final Map<Register.Virtual, IRBlock> defs;
        public final Map<IRBlock, List<IRBlock>> visited;

        public VisitInfo(IRBlock block) {
            this(block, Collections.emptyMap(), Collections.emptyMap());
        }

        public VisitInfo(IRBlock block, Map<Register.Virtual, IRBlock> defs, Map<IRBlock, List<IRBlock>> visited) {
            this.block = block;
            this.defs = defs;
            this.visited = visited;
        }

        public VisitInfo(VisitInfo info) {
            this(info.block, new HashMap<>(info.defs), new HashMap<>(info.visited));
            for (final Map.Entry<IRBlock, List<IRBlock>> entry : this.visited.entrySet())
                entry.setValue(new ArrayList<>(entry.getValue()));
        }
    }

    private final Map<Register, List<LiveRange>> ranges = new HashMap<>();
    private final UnionFind<Register> tied = new UnionFind<>();

    public void compute(IRFunction fn) {
        if (fn.isEmpty())
            return; // nothing to compute

        final Map<IRBlock, BlockInfo> infos = new HashMap<>();
        for (final IRBlock block : fn)
            infos.put(block, this.compute(block));

        final Graph<IRBlock.Handle> cfg = new FlowGraphGenerator().compute(fn);
        this.traverseCFG(cfg, Collections.unmodifiableMap(infos), fn.blocks.get(0));

        for (final BlockInfo info : infos.values())
            for (final Map.Entry<Register, List<LiveRange>> entry : info.ranges.entrySet())
                this.ranges.computeIfAbsent(entry.getKey(), k -> new ArrayList<>(0)).addAll(entry.getValue());
    }

    private BlockInfo compute(IRBlock block) {
        final Map<Register, List<LiveRange>> ranges = new HashMap<>();
        final ListIterator<IRInstr> it = block.iterator(block.size());
        final HashMap<Register, Integer> live = new HashMap<>();

        // Check liveness across instructions in reverse order
        while (it.hasPrevious()) {
            final int i = it.previousIndex();
            final IRInstr instr = it.previous();

            for (final IRReg def : instr.defs)
                this.processDef(block, def.name, i, live, ranges);

            for (final Map.Entry<Integer, Integer> pair : instr.opcode.getTiedDefs().entrySet()) {
                final IRReg vdef = instr.defs.get(pair.getKey());
                final IRReg vuse = (IRReg) instr.uses.get(pair.getValue());

                // Mark the registers as tied.
                this.tied.union(vdef.name, vuse.name);
            }

            for (final IRValue use : instr.uses) {
                if (!(use instanceof IRReg))
                    continue;

                final IRReg reg = (IRReg) use;
                live.putIfAbsent(reg.name, i);
            }
        }

        // Account for incoming phi values (treat as first instruction)
        for (final Register.Virtual phi : block.inputs)
            this.processDef(block, phi, 0, live, ranges);

        // At this point, all the ranges have real definitions. Copy it before
        // we deal with vregs that are still live.
        final Set<Register.Virtual> defs = ranges.keySet().stream()
                .filter(x -> x instanceof Register.Virtual)
                .map(x -> (Register.Virtual) x)
                .collect(Collectors.toSet());

        // Lastly, it's possible for vregs to still be live:
        // A:
        //    v = ...
        //    JMP B
        // B:
        //    ...
        //    ... v     <- see here: v comes from an inflow that is not phi
        //
        // For block B, v has to be at least live from the start of B.
        final Set<Register.Virtual> liveIns = new HashSet<>();
        for (final Map.Entry<Register, Integer> entry : live.entrySet()) {
            final Register r = entry.getKey();
            if (r instanceof Register.Physical)
                throw new UnsupportedOperationException("Cross-block definition for physical register(" + r + ") is not supported");

            final ArrayList<LiveRange> lst = new ArrayList<>(1);
            lst.add(new LiveRange(block, 0, entry.getValue()));
            ranges.put(r, lst);

            liveIns.add((Register.Virtual) r);
        }

        return new BlockInfo(Collections.unmodifiableSet(liveIns),
                             Collections.unmodifiableSet(defs),
                             ranges);
    }

    private void processDef(IRBlock block, Register def, int index, Map<Register, Integer> live, Map<Register, List<LiveRange>> ranges) {
        // Say a register is only def'ed and never used. In that case, it
        // still affects the interference graph, so just assume it's also used
        // at the point of definition.
        final int lastUse[] = new int[] { index };
        final boolean changed = live.entrySet().removeIf(entry -> {
            final Register use = entry.getKey();
            if (!this.isParentReg(def, use))
                return false;

            // Example:
            // v = ...  <- index            +
            // ...                          | <- a pocket
            // ... v    <- entry.getValue   |

            lastUse[0] = Math.max(lastUse[0], entry.getValue());
            return true;
        });

        List<LiveRange> lst = ranges.get(def);
        if (lst == null) {
            boolean found = false;
            if (def instanceof Register.Physical) {
                // physical registers could be aliased.
                for (final Register reg : ranges.keySet()) {
                    if (!(reg instanceof Register.Physical))
                        continue;

                    final Register.Physical r1 = (Register.Physical) def;
                    final Register.Physical r2 = (Register.Physical) reg;

                    if (this.isParentReg(r2, r1)) {
                        // all good, just load the corresponding entry.
                        lst = ranges.get(r2);
                        found = true;
                        break;
                    }

                    if (this.isParentReg(r1, r2)) {
                        // swap the two keys because we want to always
                        // keep the parent register.
                        lst = ranges.remove(r2);
                        ranges.put(r1, lst);
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                lst = new ArrayList<>(1);
                ranges.put(def, lst);
            }
        }

        final LiveRange pocket = new LiveRange(block, index, lastUse[0]);
        lst.add(pocket);
    }

    private boolean isParentReg(Register parent, Register sub) {
        if (parent.equals(sub))
            return true;
        if ((parent instanceof Register.Physical) && (sub instanceof Register.Physical))
            return this.isParentReg((Register.Physical) parent, (Register.Physical) sub);
        return false;
    }

    private boolean isParentReg(Register.Physical parent, Register.Physical sub) {
        for (; sub != null; sub = sub.getParent())
            if (sub.equals(parent))
                return true;
        return false;
    }

    public void traverseCFG(Graph<IRBlock.Handle> cfg, Map<IRBlock, BlockInfo> infos, IRBlock start) {
        final Deque<VisitInfo> stack = new ArrayDeque<>();
        stack.push(new VisitInfo(start));
        while (!stack.isEmpty()) {
            final VisitInfo state = stack.pop();
            final IRBlock block = state.block;
            if (state.visited.containsKey(block))
                continue; // don't revisit a back edge

            final VisitInfo nextState = new VisitInfo(state);

            final BlockInfo info = infos.get(block);

            // Check the promised live-ins
            for (final Register.Virtual reg : info.liveIns) {
                final IRBlock srcBlock = nextState.defs.get(reg);
                final List<IRBlock> path = nextState.visited.get(srcBlock);

                // Example:           before    after   (x means live)
                // srcBlock:
                //    ...
                //    v = ...           x         x
                //    JMP path1                   x
                // path1:
                //    JMP path2                   x
                // path2:
                //    JMP current                 x
                // current:
                //    ...               x         x
                //    ... v             x         x

                // srcBlock will have a live range from the def to the end of
                // the block.
                final List<LiveRange> pockets = infos.get(srcBlock).ranges.get(reg);
                pockets.set(0, new LiveRange(srcBlock, pockets.get(0).start, srcBlock.size() - 1));

                // path will have a live range across the entire block.
                for (final IRBlock p : path)
                    infos.get(p).ranges.put(reg, Arrays.asList(new LiveRange(p, 0, p.size() - 1)));
            }

            // Bump the existing paths
            for (final List<IRBlock> path : nextState.visited.values())
                path.add(block);

            // Mark the current block as visited
            nextState.visited.put(block, new ArrayList<>(1));

            // Add the defs of this block
            for (final Register.Virtual def : info.defs)
                nextState.defs.put(def, block);

            // Queue the successors for visiting
            for (final IRBlock.Handle next : cfg.getNeighbours(block.handle))
                stack.push(new VisitInfo(next.block, nextState.defs, nextState.visited));
        }
    }

    public Set<Register> getRegs() {
        return Collections.unmodifiableSet(this.ranges.keySet());
    }

    public List<LiveRange> getLiveRanges(Register reg) {
        return Collections.unmodifiableList(this.ranges.get(reg));
    }

    public List<Set<Register>> getRegGroups() {
        final List<Set<Register>> ret = new ArrayList<>();
        final Map<Register, Set<Register>> revTied = new HashMap<>();
        for (final Register reg : this.getRegs()) {
            final Register tiedKey = this.tied.root(reg);
            if (tiedKey == null) {
                ret.add(Collections.singleton(reg));
                continue;
            }

            Set<Register> set = revTied.get(tiedKey);
            if (set == null) {
                set = new HashSet<>();
                revTied.put(tiedKey, set);
                ret.add(set);
            }

            set.add(reg);
        }

        return ret;
    }

    @Override
    public String toString() {
        return this.ranges.toString();
    }
}
