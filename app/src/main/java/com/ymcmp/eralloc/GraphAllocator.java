package com.ymcmp.eralloc;

import java.util.*;
import java.util.stream.*;
import com.ymcmp.eralloc.ir.*;

public class GraphAllocator {

    public static final class SpillRequiredException extends Exception {

        public final Map<RegName, Integer> candidates;

        public SpillRequiredException(Map<RegName, Integer> candidates) {
            this.candidates = candidates;
        }
    }

    private final IRContext ctx;
    private final Target target;

    public GraphAllocator(IRContext ctx, Target target) {
        this.ctx = ctx;
        this.target = target;
    }

    public void allocate(List<IRInstr> block) {
        final HashSet<RegName> spilled = new HashSet<>();

        while (true) {
            final LivenessAnalyzer lr = new LivenessAnalyzer(this.target);
            lr.compute(block);

            try {
                final Graph<RegName> g = this.buildGraph(lr);
                final Map<RegName, RegName.Physical> assignment = this.colorGraph(g);

                System.out.println("Graph:");
                System.out.println(g);
                System.out.println(assignment);
                System.out.println();

                this.applyAssignment(block, Collections.unmodifiableMap(assignment));
                return;
            } catch (SpillRequiredException ex) {
                RegName spill = null;
                int maximum = -1;
                for (final Map.Entry<RegName, Integer> entry : ex.candidates.entrySet()) {
                    final RegName reg = entry.getKey();
                    if (spilled.contains(reg))
                        continue;

                    // Compute the spill factor
                    int start = Integer.MAX_VALUE;
                    int end = Integer.MIN_VALUE;
                    for (LivenessAnalyzer.LiveRange range : lr.getLiveRanges(reg)) {
                        start = Math.min(start, range.def);
                        end = Math.max(end, range.lastUse);
                    }

                    final int factor = entry.getValue() * (end - start + 1);

                    if (factor > maximum) {
                        spill = reg;
                        maximum = factor;
                    }
                }

                if (spill == null)
                    // Somehow adding more spills does not improve the live range!?
                    throw new AssertionError("We cannot allocate the provided code");

                System.out.println("Spilled " + spill + " of " + ex.candidates);
                spilled.add(spill);
                this.spill(block, Collections.singleton(spill));
            }
        }
    }

    public Graph<RegName> buildGraph(LivenessAnalyzer lr) {
        final Graph<RegName> g = new Graph<>();
        final RegName[] regs = lr.getRegs().toArray(new RegName[0]);
        for (int i = 0; i < regs.length; ++i) {
            g.addNode(regs[i]);

            outer:
            for (int j = i + 1; j < regs.length; ++j) {
                g.addNode(regs[j]);

                if ((regs[i] instanceof RegName.Physical) && (regs[j] instanceof RegName.Physical)) {
                    g.addEdge(regs[i], regs[j]);
                    continue outer;
                }

                final List<LivenessAnalyzer.LiveRange> ranges1 = lr.getLiveRanges(regs[i]);
                final List<LivenessAnalyzer.LiveRange> ranges2 = lr.getLiveRanges(regs[j]);

                for (final LivenessAnalyzer.LiveRange r1 : ranges1) {
                    for (final LivenessAnalyzer.LiveRange r2 : ranges2) {
                        if (r1.overlaps(r2)) {
                            g.addEdge(regs[i], regs[j]);
                            continue outer;
                        }
                    }
                }
            }
        }
        return g;
    }

    public Map<RegName, RegName.Physical> colorGraph(final Graph<RegName> g) throws SpillRequiredException {
        final Map<RegName, RegName.Physical> assignment = new HashMap<>();

        final PriorityQueue<RegName> vregQueue = new PriorityQueue<>(g.size(), (lhs, rhs) -> {
            // Start with the one with the most assigned neighbours
            final long lhsKey = g.getNeighbours(lhs).stream().filter(assignment::containsKey).count();
            final long rhsKey = g.getNeighbours(rhs).stream().filter(assignment::containsKey).count();
            return Long.compare(rhsKey, lhsKey);
        });

        // Precolor if necessary
        for (final RegName reg : g) {
            if (reg instanceof RegName.Physical)
                assignment.put(reg, (RegName.Physical) reg);
            else
                vregQueue.add(reg);
        }

        final Map<RegName, Integer> candidates = new HashMap<>();

        while (!vregQueue.isEmpty()) {
            final RegName node = vregQueue.poll();

            final Set<RegName.Physical> used = g.getNeighbours(node)
                    .stream()
                    .map(assignment::get)
                    .filter(x -> x != null)
                    .collect(Collectors.toSet());

            final Optional<RegName.Physical> color = node.getInfo().getRegs()
                    .stream()
                    .filter(r1 -> {
                        return used.stream().noneMatch(r2 -> this.aliases(r1, r2));
                    })
                    .findFirst();

            // If there is a register available, color it
            if (color.isPresent()) {
                assignment.put(node, color.get());
                continue;
            }

            // If no registers are available, queue it for spill analysis

            candidates.put(node, candidates.getOrDefault(node, 0) + 1);
            g.getNeighbours(node).stream()
                    .filter(assignment::containsKey)
                    .filter(x -> !(x instanceof RegName.Physical))
                    .forEach(reg -> {
                        candidates.put(reg, candidates.getOrDefault(reg, 0) + 1);
                    });
        }

        if (!candidates.isEmpty())
            throw new SpillRequiredException(candidates);

        return assignment;
    }

    private boolean aliases(RegName.Physical r1, RegName.Physical r2) {
        return isParentReg(r1, r2) || isParentReg(r2, r1);
    }

    private boolean isParentReg(RegName.Physical parent, RegName.Physical sub) {
        for (; sub != null; sub = sub.getParent())
            if (sub.equals(parent))
                return true;
        return false;
    }

    public void spill(List<IRInstr> block, Set<RegName> regs) {
        final Map<RegName, IRFrameIndex> slots = new HashMap<>();
        for (final RegName reg : regs)
            slots.put(reg, new IRFrameIndex(this.ctx.newFrameIndex(reg.getInfo().width()), 0));

        final ListIterator<IRInstr> it = block.listIterator();
        while (it.hasNext()) {
            final IRInstr instr = it.next();

            // Example:
            //   d1, d2, ... = INSTR s1, s2, ...
            //
            // Say all d's and s's are registers needed to be spilled:
            //   s1 = RELOAD (FRAME)
            //   s2 = RELOAD (FRAME)
            //   ...
            //   d1, d2, ... = INSTR s1, s2, ...
            //   SAVE (FRAME), d1
            //   SAVE (FRAME), d2
            //   ...

            // Note that this way of spilling is not ideal:
            // While the individual live ranges are shorter, which is good,
            // these registers still need to be allocated onto the same
            // register.
            //
            // We should really be generating fresh registers instead, but we
            // cannot do this until we change how tied registers are
            // represented!

            it.previous(); // assume we need to insert RELOAD's
            for (final IRValue v : instr.uses) {
                if (v instanceof IRReg) {
                    final IRReg r = (IRReg) v;
                    if (slots.containsKey(r.name))
                        it.add(IRInstr.of("RELOAD")
                                .addDef(r)
                                .addUse(slots.get(r.name))
                                .build());
                }
            }

            it.next();
            for (final IRReg r : instr.defs)
                if (slots.containsKey(r.name))
                    it.add(IRInstr.of("SAVE")
                            .addUse(slots.get(r.name))
                            .addUse(r)
                            .build());
        }
    }

    public void applyAssignment(List<IRInstr> block, Map<RegName, RegName.Physical> assignment) {
        // Remove the precolored entries so we can use getOrDefault...
        final Map<RegName, RegName> p = new HashMap<>(assignment);
        p.keySet().removeIf(x -> x instanceof RegName.Physical);

        final ListIterator<IRInstr> it = block.listIterator();
        while (it.hasNext()) {
            final IRInstr instr = it.next();
            final IRInstr.Builder builder = IRInstr.of(instr.opcode);

            for (final IRReg r : instr.defs)
                builder.addDef(new IRReg(p.getOrDefault(r.name, r.name)));

            for (final IRValue v : instr.uses) {
                if (!(v instanceof IRReg))
                    builder.addUse(v);
                else {
                    final IRReg r = (IRReg) v;
                    builder.addUse(new IRReg(p.getOrDefault(r.name, r.name)));
                }
            }

            it.set(builder.build());
        }
    }
}