package com.ymcmp.eralloc;

import java.math.*;
import java.util.*;
import java.util.stream.*;
import com.ymcmp.eralloc.ir.*;
import com.ymcmp.eralloc.target.generic.GenericOpcode;

public class GraphAllocator {

    public static final class SpillRequiredException extends Exception {

        public final Map<Object, Integer> candidates;

        public SpillRequiredException(Map<Object, Integer> candidates) {
            this.candidates = candidates;
        }
    }

    public static final class InterferenceGraph {

        private final Graph<Object> g;
        private final Map<Register, Object> map;

        private InterferenceGraph(Graph<Object> g, Map<Register, Object> map) {
            this.g = g;
            this.map = map;
        }

        @Override
        public String toString() {
            return this.g.toString();
        }
    }

    private final IRContext ctx;

    public GraphAllocator(IRContext ctx) {
        this.ctx = ctx;
    }

    public void allocate(IRFunction fn) {
        new PHIRemover(this.ctx).convert(fn);

        final HashSet<Register> spilled = new HashSet<>();

        while (true) {
            final LivenessAnalyzer lr = new LivenessAnalyzer();
            lr.compute(fn);

            final InterferenceGraph g = this.buildGraph(lr);

            try {
                this.applyAssignment(fn, g, this.colorGraph(g));
                return;
            } catch (SpillRequiredException ex) {
                Register spill = null;
                int maximum = -1;
                for (final Register reg : lr.getRegs()) {
                    if (reg instanceof Register.Physical)
                        continue; // you cannot spill physical registers!
                    if (spilled.contains(reg))
                        continue; // already spilled this one, try another one.

                    final Object node = g.map.get(reg);
                    final Integer occurrences = ex.candidates.get(node);
                    if (occurrences == null)
                        continue; // not a candidate for spilling!

                    // Compute the spill factor
                    final Map<IRBlock, int[]> map = new HashMap<>();
                    for (final LivenessAnalyzer.LiveRange range : lr.getLiveRanges(reg)) {
                        final int[] tuple = map.computeIfAbsent(range.block, k -> new int[] { Integer.MAX_VALUE, Integer.MIN_VALUE });
                        tuple[0] = Math.min(tuple[0], range.start);
                        tuple[1] = Math.max(tuple[1], range.end);
                    }

                    // TODO: take number of uses (frequency) into account
                    final int distance = map.values().stream()
                            .mapToInt(tuple -> tuple[1] - tuple[0] + 1)
                            .sum();
                    final int factor = occurrences * distance;
                    if (factor > maximum) {
                        spill = reg;
                        maximum = factor;
                    }
                }

                if (spill == null)
                    // Help??
                    throw new AssertionError("We cannot allocate the provided code");

                spilled.add(spill);
                this.spill(fn, Collections.singleton(spill));
            }
        }
    }

    public InterferenceGraph buildGraph(LivenessAnalyzer lr) {
        final Graph<Object> g = new Graph<>();
        final Map<Register, Object> map = new HashMap<>();

        // use BigInteger as the vertex names just for visual sanity purposes.
        BigInteger counter = BigInteger.ZERO;
        for (final Set<Register> group : lr.getRegGroups()) {
            final Object vertex = counter;
            counter = counter.add(BigInteger.ONE);
            g.addNode(vertex);

            // all registers of the same group are tied and, therefore, must
            // share the same graph vertex.
            for (final Register reg : group)
                map.put(reg, vertex);
        }

        final Register[] regs = map.keySet().toArray(new Register[0]);
        for (int i = 0; i < regs.length; ++i) {
            outer:
            for (int j = i + 1; j < regs.length; ++j) {
                // two tied registers must not share an edge
                // (since they already share the vertex)
                final Object node1 = map.get(regs[i]);
                final Object node2 = map.get(regs[j]);
                if (node1 == node2)
                    continue outer;

                // two distinct physical registers must share an edge.
                // (distinctness is already enforced by analyzer)
                if ((regs[i] instanceof Register.Physical)
                        && (regs[j] instanceof Register.Physical)) {
                    g.addEdge(node1, node2);
                    continue outer;
                }

                // If the live ranges overlap, then they must share an edge.
                final List<LivenessAnalyzer.LiveRange> ranges1 = lr.getLiveRanges(regs[i]);
                final List<LivenessAnalyzer.LiveRange> ranges2 = lr.getLiveRanges(regs[j]);

                for (final LivenessAnalyzer.LiveRange r1 : ranges1) {
                    for (final LivenessAnalyzer.LiveRange r2 : ranges2) {
                        if (r1.overlaps(r2)) {
                            g.addEdge(node1, node2);
                            continue outer;
                        }
                    }
                }
            }
        }

        return new InterferenceGraph(g, map);
    }

    public Map<Object, Register.Physical> colorGraph(final InterferenceGraph g) throws SpillRequiredException {
        final Map<Object, Register.Physical> assignment = new HashMap<>();
        final Map<Object, Long> priority = new HashMap<>();
        final Map<Object, RegisterType> regType = new HashMap<>();

        final PriorityQueue<Object> queue = new PriorityQueue<>((lhs, rhs) -> {
            // Start with the one with the highest priority
            final long lhsKey = priority.getOrDefault(lhs, 0L);
            final long rhsKey = priority.getOrDefault(rhs, 0L);
            return Long.compare(rhsKey, lhsKey);
        });

        final Map<Object, Integer> spillCandidates = new HashMap<>();

        // Populate the queue, vreg type and precolor if necessary
        for (final Map.Entry<Register, Object> mapping : g.map.entrySet()) {
            final Register reg = mapping.getKey();
            final Object node = mapping.getValue();

            if (reg instanceof Register.Virtual) {
                if (regType.putIfAbsent(node, reg.getInfo()) == null)
                    // make sure each node only appears once in the queue.
                    queue.add(node);
                continue;
            }

            assignment.put(node, (Register.Physical) reg);
            for (final Object next : g.g.getNeighbours(node))
                priority.put(next, priority.getOrDefault(next, 0L) + 1);
        }

        while (!queue.isEmpty()) {
            final Object node = queue.poll();
            final Set<Register.Physical> used = g.g.getNeighbours(node)
                    .stream()
                    .map(assignment::get)
                    .filter(x -> x != null)
                    .collect(Collectors.toSet());
            final Optional<Register.Physical> color = regType.get(node).getRegs()
                    .stream()
                    .filter(r1 -> {
                        return used.stream().noneMatch(r2 -> this.aliases(r1, r2));
                    })
                    .findFirst();

            // If there is a register available, assign it to the node.
            if (color.isPresent()) {
                assignment.put(node, color.get());
                for (final Object next : g.g.getNeighbours(node))
                    priority.put(next, priority.getOrDefault(next, 0L) + 1);
                continue;
            }

            // If no registers are available, queue it for spill analysis.
            spillCandidates.put(node, spillCandidates.getOrDefault(node, 0) + 1);
            g.g.getNeighbours(node)
                    .stream()
                    .filter(assignment::containsKey)
                    .forEach(r -> {
                        spillCandidates.put(r, spillCandidates.getOrDefault(r, 0) + 1);
                    });
        }

        if (!spillCandidates.isEmpty())
            throw new SpillRequiredException(spillCandidates);

        return assignment;
    }

    private boolean aliases(Register.Physical r1, Register.Physical r2) {
        return isParentReg(r1, r2) || isParentReg(r2, r1);
    }

    private boolean isParentReg(Register.Physical parent, Register.Physical sub) {
        for (; sub != null; sub = sub.getParent())
            if (sub.equals(parent))
                return true;
        return false;
    }

    public void spill(IRFunction fn, Set<Register> regs) {
        final Map<Register, IRFrameIndex> slots = new HashMap<>();
        for (final Register reg : regs)
            slots.put(reg, new IRFrameIndex(this.ctx.newFrameIndex(reg.getInfo().width()), 0));

        for (final IRBlock block : fn) {
            if (!block.inputs.isEmpty())
                throw new UnsupportedOperationException("Spill with phis are not supported yet");

            final ListIterator<IRInstr> it = block.iterator();
            while (it.hasNext()) {
                final IRInstr instr = it.next();
                final IRInstr.Builder builder = IRInstr.of(instr.opcode);

                // Example:
                //   d1, d2, ... = INSTR s1, s2, ...
                //
                // Say all d's and s's are registers needed to be spilled:
                //   n1 = RELOAD (FRAME)
                //   n2 = RELOAD (FRAME)
                //   ...
                //   d1, d2, ... = INSTR n1, n2, ...
                //   SAVE (FRAME), d1
                //   SAVE (FRAME), d2
                //   ...
                //
                // and n's are freshly generated temporaries.

                it.previous(); // assume we need to insert RELOAD's
                boolean modded = false;
                for (final IRValue v : instr.uses) {
                    if (v instanceof IRReg) {
                        final IRReg r = (IRReg) v;
                        if (slots.containsKey(r.name)) {
                            modded = true;

                            // generate fresh temporary
                            final IRReg fresh = new IRReg(this.ctx.newVReg(r.name.getInfo()));
                            builder.addUse(fresh);

                            // reload into the fresh temporary
                            it.add(IRInstr.of(GenericOpcode.RELOAD)
                                    .addDef(fresh)
                                    .addUse(slots.get(r.name))
                                    .build());
                            continue;
                        }
                    }

                    builder.addUse(v);
                }

                it.next();

                if (modded) {
                    builder.addDefs(instr.defs);
                    it.set(builder.build());
                }

                for (final IRReg r : instr.defs)
                    if (slots.containsKey(r.name))
                        it.add(IRInstr.of(GenericOpcode.SAVE)
                                .addUse(slots.get(r.name))
                                .addUse(r)
                                .build());
            }
        }
    }

    public void applyAssignment(IRFunction fn, InterferenceGraph g, Map<Object, Register.Physical> assignment) {
        for (final IRBlock block : fn) {
            final ListIterator<IRInstr> it = block.iterator();
            while (it.hasNext()) {
                final IRInstr instr = it.next();
                final IRInstr.Builder builder = IRInstr.of(instr.opcode);

                for (final IRReg r : instr.defs) {
                    if (r.name instanceof Register.Virtual) {
                        builder.addDef(new IRReg(assignment.get(g.map.get(r.name))));
                        continue;
                    }

                    builder.addDef(r);
                }

                for (final IRValue v : instr.uses) {
                    if (v instanceof IRReg) {
                        final IRReg r = (IRReg) v;
                        if (r.name instanceof Register.Virtual) {
                            builder.addUse(new IRReg(assignment.get(g.map.get(r.name))));
                            continue;
                        }
                    }

                    builder.addUse(v);
                }

                it.set(builder.build());
            }
        }
    }
}
