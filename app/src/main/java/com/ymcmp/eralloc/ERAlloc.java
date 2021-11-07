package com.ymcmp.eralloc;

import java.util.*;
import java.util.function.BiFunction;
import com.ymcmp.eralloc.ast.*;

public final class ERAlloc {

    public static List<String> codegen(ExprAST e) {
        final ErshovLabel scheduler = new ErshovLabel();
        e.accept(scheduler);
        final EmitX86 emitter = new EmitX86(scheduler.labels, 4);
        e.accept(emitter);
        return emitter.getResult();
    }
}

final class ErshovLabel implements ExprAST.Visitor<Integer> {

    public final Map<ExprAST, Integer> labels = new HashMap<>();

    private int success(ExprAST e, int v) {
        final Integer p = this.labels.put(e, v);
        if (p != null && p != v)
            throw new RuntimeException("Node relabelled as different value!");
        return v;
    }

    @Override
    public Integer visitNumeric(Numeric e) {
        return this.success(e, 1);
    }

    @Override
    public Integer visitFrameIndex(FrameIndex e) {
        return this.success(e, 1);
    }

    @Override
    public Integer visitGlobalValue(GlobalValue e) {
        return this.success(e, 1);
    }

    @Override
    public Integer visitLoadExpr(LoadExpr e) {
        return this.success(e, 1);
    }

    @Override
    public Integer visitStoreExpr(StoreExpr e) {
        final int ptr = e.ptr.accept(this);
        final int value = e.value.accept(this);

        return this.success(e, ptr == value ? ptr + 1 : Math.max(ptr, value));
    }

    @Override
    public Integer visitAddExpr(AddExpr e) {
        final int lhs = e.lhs.accept(this);
        final int rhs = e.rhs.accept(this);

        return this.success(e, lhs == rhs ? lhs + 1 : Math.max(lhs, rhs));
    }

    @Override
    public Integer visitSubExpr(SubExpr e) {
        final int lhs = e.lhs.accept(this);
        final int rhs = e.rhs.accept(this);

        return this.success(e, lhs == rhs ? lhs + 1 : Math.max(lhs, rhs));
    }

    @Override
    public Integer visitMulExpr(MulExpr e) {
        final int lhs = e.lhs.accept(this);
        final int rhs = e.rhs.accept(this);

        return this.success(e, lhs == rhs ? lhs + 1 : Math.max(lhs, rhs));
    }

    @Override
    public Integer visitDivExpr(DivExpr e) {
        final int lhs = e.lhs.accept(this);
        final int rhs = e.rhs.accept(this);

        return this.success(e, lhs == rhs ? lhs + 1 : Math.max(lhs, rhs));
    }

    @Override
    public Integer visitRemExpr(RemExpr e) {
        final int lhs = e.lhs.accept(this);
        final int rhs = e.rhs.accept(this);

        return this.success(e, lhs == rhs ? lhs + 1 : Math.max(lhs, rhs));
    }

    @Override
    public Integer visitShlExpr(ShlExpr e) {
        final int lhs = e.lhs.accept(this);
        final int rhs = e.rhs.accept(this);

        return this.success(e, lhs == rhs ? lhs + 1 : Math.max(lhs, rhs));
    }

    @Override
    public Integer visitSraExpr(SraExpr e) {
        final int lhs = e.lhs.accept(this);
        final int rhs = e.rhs.accept(this);

        return this.success(e, lhs == rhs ? lhs + 1 : Math.max(lhs, rhs));
    }

    @Override
    public Integer visitSrlExpr(SrlExpr e) {
        final int lhs = e.lhs.accept(this);
        final int rhs = e.rhs.accept(this);

        return this.success(e, lhs == rhs ? lhs + 1 : Math.max(lhs, rhs));
    }

    @Override
    public Integer visitCallExpr(CallExpr e) {
        // Function calls are mostly dictated by calling convention, but since
        // we're assuming only concerned with single registers, we just need to
        // allocate one register to this (the return value).

        for (final ExprAST arg : e.args)
            arg.accept(this);

        return this.success(e, 1);
    }
}

final class EmitX86 implements ExprAST.Visitor<Void> {

    private final List<String> registers = Collections.unmodifiableList(Arrays.asList("eax", "ecx", "edx"));
    private final Deque<String> freeRegs = new ArrayDeque<>(registers);

    private final List<String> instrs = new ArrayList<>();

    private final Map<ExprAST, Integer> labels;

    private int curOffset;
    private int maxOffset;

    public EmitX86(Map<ExprAST, Integer> labels, int curOffset) {
        // SANCHECK: we shouldn't be calling mutable methods on it
        this.labels = Collections.unmodifiableMap(labels);
        this.curOffset = curOffset;
    }

    public List<String> getResult() {
        return new ArrayList<>(this.instrs);
    }

    public String getTopReg() {
        return this.freeRegs.getFirst();
    }

    public String popTopReg() {
        return this.freeRegs.removeFirst();
    }

    public void pushTopReg(String r) {
        this.freeRegs.addFirst(r);
    }

    public void swapTopRegs() {
        //   p        q
        //   q  -->   p
        // ----     ----

        final String p = this.freeRegs.removeFirst();
        final String q = this.freeRegs.removeFirst();
        this.freeRegs.addFirst(p);
        this.freeRegs.addFirst(q);
    }

    public int shiftOffset() {
        final int value = this.curOffset;
        this.curOffset = value + 4;
        this.maxOffset = Math.max(value + 4, this.maxOffset);
        return value;
    }

    public void unshiftOffset() {
        this.curOffset -= 4;
    }

    @Override
    public Void visitNumeric(Numeric e) {
        this.instrs.add("mov " + this.getTopReg() + ", " + e.value);
        return null;
    }

    @Override
    public Void visitFrameIndex(FrameIndex e) {
        this.instrs.add("lea " + this.getTopReg() + ", [ebp-" + e.value + "]");
        return null;
    }

    @Override
    public Void visitGlobalValue(GlobalValue e) {
        this.instrs.add("extern " + e.value);
        this.instrs.add("mov " + this.getTopReg() + ", " + e.value);
        return null;
    }

    @Override
    public Void visitLoadExpr(LoadExpr e) {
        e.ptr.accept(this);

        this.instrs.add("mov " + this.getTopReg() + ", [" + this.getTopReg() + "]");
        return null;
    }

    public void emitSimpleBinaryInstr(ExprAST lhs, ExprAST rhs, BiFunction<String, String, List<String>> fn) {
        final List<String> spills = new ArrayList<>();
        if (this.freeRegs.size() < 2) {
            int minSpillCount = 2 - this.freeRegs.size();
            final Set<String> hitset = new HashSet<>(this.freeRegs);
            for (final String r : this.registers) {
                if (hitset.contains(r))
                    continue; // it's not suitable for spilling
                this.freeRegs.addLast(r);

                final int offset = this.shiftOffset();
                this.instrs.add("mov [ebp-" + offset + "], " + r);
                spills.add("mov " + r + ", [ebp-" + offset + "]");

                if (--minSpillCount < 1)
                    break;
            }
        }

        final int lhsP = this.labels.get(lhs);
        final int rhsP = this.labels.get(rhs);

        if (lhsP < rhsP) {
            final ExprAST te = lhs; lhs = rhs; rhs = te;
            this.swapTopRegs();
        }

        lhs.accept(this);
        final String lreg = this.popTopReg();
        final String rreg = this.getTopReg();
        rhs.accept(this);
        this.pushTopReg(lreg);

        this.instrs.addAll(fn.apply(lreg, rreg));

        if (lhsP < rhsP)
            this.swapTopRegs();

        for (final String spill : spills) {
            this.unshiftOffset();
            this.freeRegs.removeLast();
            this.instrs.add(spill);
        }
    }

    @Override
    public Void visitStoreExpr(StoreExpr e) {
        this.emitSimpleBinaryInstr(e.value, e.ptr, (vreg, preg) -> {
            return Collections.singletonList("mov [" + preg + "], " + vreg);
        });
        return null;
    }

    @Override
    public Void visitAddExpr(AddExpr e) {
        this.emitSimpleBinaryInstr(e.lhs, e.rhs, (lreg, rreg) -> {
            return Collections.singletonList("add " + lreg + ", " + rreg);
        });
        return null;
    }

    @Override
    public Void visitSubExpr(SubExpr e) {
        this.emitSimpleBinaryInstr(e.lhs, e.rhs, (lreg, rreg) -> {
            return Collections.singletonList("sub " + lreg + ", " + rreg);
        });
        return null;
    }

    @Override
    public Void visitMulExpr(MulExpr e) {
        this.emitSimpleBinaryInstr(e.lhs, e.rhs, (lreg, rreg) -> {
            return Collections.singletonList("imul " + lreg + ", " + rreg);
        });
        return null;
    }

    public void emitDivRemInstr(ExprAST lhs, ExprAST rhs, boolean div) {
        this.emitSimpleBinaryInstr(lhs, rhs, (divident, divisor) -> {
            // the division and remainder instruction requires the divident to
            // be in eax, divisor to *not* be in edx, and it computes the
            // quotient into eax, the remainder into edx.

            final Set<String> hitset = new HashSet<>(this.freeRegs);
            hitset.add(divident);

            final List<String> instrs = new ArrayList<>();
            final List<String> spills = new ArrayList<>();
            for (final String r : Arrays.asList("eax", "edx", "ecx")) {
                if (hitset.contains(r))
                    continue; // no need to emergency spill it

                final int offset = this.shiftOffset();
                instrs.add("mov [ebp-" + offset + "], " + r);
                spills.add("mov " + r + ", [ebp-" + offset + "]");
            }

            if (!"eax".equals(divisor)) {
                if (!"eax".equals(divident))
                    instrs.add("mov eax, " + divident);
                if (!"ecx".equals(divisor))
                    instrs.add("mov ecx, " + divisor);
            } else {
                switch (divident) {
                case "eax":
                    throw new RuntimeException("ILLEGAL ALLOCATION");
                case "ecx":
                    instrs.add("xchg eax, ecx");
                    break;
                default:
                    instrs.add("mov ecx, eax");
                    instrs.add("mov eax, " + divident);
                }
            }

            instrs.add("cdq");
            instrs.add("idiv ecx");

            final String result = div ? "eax" : "edx";
            if (!result.equals(divident))
                instrs.add("mov " + divident + ", " + result);

            for (final String spill : spills) {
                this.unshiftOffset();
                instrs.add(spill);
            }

            return instrs;
        });
    }

    @Override
    public Void visitDivExpr(DivExpr e) {
        this.emitDivRemInstr(e.lhs, e.rhs, true);
        return null;
    }

    @Override
    public Void visitRemExpr(RemExpr e) {
        this.emitDivRemInstr(e.lhs, e.rhs, false);
        return null;
    }

    public void emitShiftInstr(ExprAST lhs, ExprAST rhs, String op) {
        this.emitSimpleBinaryInstr(lhs, rhs, (value, shamt) -> {
            // shift instructions require the shift amount to be in ecx
            // (technically only cl, the top bits are ignored).

            final List<String> instrs = new ArrayList<>();
            if ("ecx".equals(shamt)) {
                instrs.add(op + " " + value + ", cl");
            } else if ("ecx".equals(value)) {
                instrs.add("xchg ecx, " + shamt);
                instrs.add(op + " " + shamt + ", cl");
                instrs.add("mov ecx, " + shamt);
            } else if (this.freeRegs.contains("ecx")) {
                instrs.add("mov ecx, " + shamt);
                instrs.add(op + " " + value + ", " + shamt);
            } else {
                instrs.add("xchg ecx, " + shamt);
                instrs.add(op + " " + value + ", cl");
                instrs.add("xchg ecx, " + shamt);
            }

            return instrs;
        });
    }

    @Override
    public Void visitShlExpr(ShlExpr e) {
        this.emitShiftInstr(e.lhs, e.rhs, "shl");
        return null;
    }

    @Override
    public Void visitSraExpr(SraExpr e) {
        this.emitShiftInstr(e.lhs, e.rhs, "sar");
        return null;
    }

    @Override
    public Void visitSrlExpr(SrlExpr e) {
        this.emitShiftInstr(e.lhs, e.rhs, "shr");
        return null;
    }

    @Override
    public Void visitCallExpr(CallExpr e) {
        // cdecl has eax, ecx, edx as caller saved
        final Set<String> hitset = new HashSet<>(this.freeRegs);
        final List<String> spills = new ArrayList<>();
        for (final String r : Arrays.asList("eax", "ecx", "edx")) {
            if (hitset.contains(r))
                continue; // no need to save it

            final int offset = this.shiftOffset();
            this.instrs.add("mov [ebp-" + offset + "], " + r);
            spills.add("mov " + r + ", [ebp-" + offset + "]");
        }

        // cdecl pushes the arguments from right to left and returns in eax.
        for (int i = e.args.length; i-- > 0; ) {
            e.args[i].accept(this);
            this.instrs.add("push " + this.getTopReg());
        }

        e.fn.accept(this);

        final String reg = this.getTopReg();
        this.instrs.add("call " + reg);

        if (!"eax".equals(reg))
            this.instrs.add("mov " + reg + ", eax");

        if (e.args.length != 0)
            this.instrs.add("add esp, " + 4 * e.args.length);

        for (final String spill : spills) {
            this.unshiftOffset();
            this.instrs.add(spill);
        }
        return null;
    }
}
