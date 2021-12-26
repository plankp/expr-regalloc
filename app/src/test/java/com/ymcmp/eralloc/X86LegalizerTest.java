package com.ymcmp.eralloc;

import java.util.*;
import com.ymcmp.eralloc.ir.*;
import com.ymcmp.eralloc.ast.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class X86LegalizerTest {

    @Test
    public void testSpilling() {
        final IRContext ctx = new IRContext();
        final List<IRInstr> irs = new ExprPrinter(ctx).emit(new SubExpr(
            new SubExpr(
                new SubExpr(new LoadExpr(new GlobalValue("v4")), new Numeric(22)),
                new SubExpr(new LoadExpr(new GlobalValue("v8")), new Numeric(11))),
            new SubExpr(
                new SubExpr(new LoadExpr(new GlobalValue("v12")), new Numeric(3)),
                new SubExpr(new LoadExpr(new GlobalValue("v16")), new Numeric(9)))));

        final X86Legalizer legalizer = new X86Legalizer(ctx);
        legalizer.legalize(irs);

        for (final IRInstr i : irs)
            System.out.println(i);
        System.out.println();

        final GraphAllocator allocator = new GraphAllocator(ctx, X86Target.INSTANCE);
        allocator.allocate(irs);

        for (final IRInstr i : irs)
            System.out.println(i);
        System.out.println();
    }

    @Test
    public void testPrecolorDiv() {
        final IRContext ctx = new IRContext();
        final List<IRInstr> irs = new ExprPrinter(ctx).emit(
                new DivExpr(new LoadExpr(new GlobalValue("v4")), new Numeric(22)));

        final X86Legalizer legalizer = new X86Legalizer(ctx);
        legalizer.legalize(irs);

        for (final IRInstr i : irs)
            System.out.println(i);
        System.out.println();

        final GraphAllocator allocator = new GraphAllocator(ctx, X86Target.INSTANCE);
        allocator.allocate(irs);

        for (final IRInstr i : irs)
            System.out.println(i);
        System.out.println();
    }

    @Test
    public void testPrecolorShift() {
        final IRContext ctx = new IRContext();
        final List<IRInstr> irs = new ExprPrinter(ctx).emit(
                new ShlExpr(new LoadExpr(new GlobalValue("v4")), new Numeric(22)));

        final X86Legalizer legalizer = new X86Legalizer(ctx);
        legalizer.legalize(irs);

        for (final IRInstr i : irs)
            System.out.println(i);
        System.out.println();

        final GraphAllocator allocator = new GraphAllocator(ctx, X86Target.INSTANCE);
        allocator.allocate(irs);

        for (final IRInstr i : irs)
            System.out.println(i);
        System.out.println();
    }

    @Test
    public void testEasy() {
        final IRContext ctx = new IRContext();
        final List<IRInstr> irs = new ExprPrinter(ctx).emit(
                new SubExpr(new LoadExpr(new GlobalValue("v4")), new Numeric(22)));

        final X86Legalizer legalizer = new X86Legalizer(ctx);
        legalizer.legalize(irs);

        for (final IRInstr i : irs)
            System.out.println(i);
        System.out.println();

        final GraphAllocator allocator = new GraphAllocator(ctx, X86Target.INSTANCE);
        allocator.allocate(irs);

        for (final IRInstr i : irs)
            System.out.println(i);
        System.out.println();
    }
}
