package com.ymcmp.eralloc;

import java.util.*;
import java.util.stream.*;
import com.ymcmp.eralloc.ir.*;

public final class LivenessAnalyzer {

    public static final class LiveRange {

        public final int def;
        public final int lastUse;

        public LiveRange(int def, int lastUse) {
            this.def = def;
            this.lastUse = lastUse;
        }

        public boolean overlaps(LiveRange range) {
            // take the complement of the overlap condition:
            //
            // +----o
            //      +-----o

            return !((this.def < range.def && this.lastUse <= range.def)
                    || (range.def < this.def && range.lastUse <= this.def));
        }

        @Override
        public String toString() {
            return "[" + def + "," + lastUse + "]";
        }
    }

    private final Target target;
    private final Map<RegName, List<LiveRange>> ranges = new HashMap<>();

    public LivenessAnalyzer(Target target) {
        this.target = target;
    }

    public void compute(List<IRInstr> block) {
        final ListIterator<IRInstr> it = block.listIterator(block.size());
        final HashMap<RegName, Integer> live = new HashMap<>();
        while (it.hasPrevious()) {
            final int i = it.previousIndex();
            final IRInstr instr = it.previous();

            for (final IRReg def : instr.defs) {
                // #1
                //    parent-or-same = ...
                //    ... register
                //
                // #2
                //    parent-or-same = ...
                //    ...
                //    subregister = ...
                //    ... register

                final int lastUse[] = new int[] { i };
                final boolean changed = live.entrySet().removeIf(entry -> {
                    final RegName use = entry.getKey();

                    if (this.isParentReg(def.name, use)) {
                        lastUse[0] = Math.max(lastUse[0], entry.getValue());
                        return true;
                    }
                    return false;
                });

                final LiveRange pocket = new LiveRange(i, lastUse[0]);

                List<LiveRange> lst = this.ranges.get(def.name);
                if (lst == null) {
                    boolean found = false;
                    if (def.name instanceof RegName.Physical) {
                        // physical registers could be aliased.
                        for (final RegName reg : this.ranges.keySet()) {
                            if (!(reg instanceof RegName.Physical))
                                continue;

                            final RegName.Physical r1 = (RegName.Physical) def.name;
                            final RegName.Physical r2 = (RegName.Physical) reg;

                            if (this.isParentReg(r2, r1)) {
                                // all good, just load the corresponding entry.
                                lst = this.ranges.get(r2);
                                found = true;
                                break;
                            }

                            if (this.isParentReg(r1, r2)) {
                                // swap the two keys because we want to always
                                // keep the parent register.
                                lst = this.ranges.remove(r2);
                                this.ranges.put(r1, lst);
                                found = true;
                                break;
                            }
                        }
                    }

                    if (!found) {
                        lst = new ArrayList<>(1);
                        this.ranges.put(def.name, lst);
                    }
                }

                lst.add(pocket);
            }

            for (final IRValue use : instr.uses) {
                if (!(use instanceof IRReg))
                    continue;

                final IRReg reg = (IRReg) use;
                live.putIfAbsent(reg.name, i);
            }
        }
    }

    private boolean isParentReg(RegName parent, RegName sub) {
        if (parent.equals(sub))
            return true;
        if ((parent instanceof RegName.Physical) && (sub instanceof RegName.Physical))
            return this.isParentReg((RegName.Physical) parent, (RegName.Physical) sub);
        return false;
    }

    private boolean isParentReg(RegName.Physical parent, RegName.Physical sub) {
        for (; sub != null; sub = sub.getParent())
            if (sub.equals(parent))
                return true;
        return false;
    }

    public Set<RegName> getRegs() {
        return Collections.unmodifiableSet(this.ranges.keySet());
    }

    public List<LiveRange> getLiveRanges(RegName reg) {
        return Collections.unmodifiableList(this.ranges.get(reg));
    }

    @Override
    public String toString() {
        return this.ranges.toString();
    }
}