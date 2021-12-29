package com.ymcmp.eralloc.target.x86;

import java.util.*;
import com.ymcmp.eralloc.*;
import com.ymcmp.eralloc.ir.*;
import com.ymcmp.eralloc.ast.*;
import com.ymcmp.eralloc.target.generic.GenericOpcode;
import com.ymcmp.eralloc.target.generic.types.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class X86LegalizerTest {

    @Test
    public void testSpilling() {
        /* Construct this case:

           %0 = COPY (GLOBAL v4)
           %1 = LOAD %0
           %2 = COPY #16
           %3 = SUB %1, %2
           %4 = COPY (GLOBAL v8)
           %5 = LOAD %4
           %6 = COPY #b
           %7 = SUB %5, %6
           %8 = SUB %3, %7          <- %8's live range causes spills
           %9 = COPY (GLOBAL v12)
           %10 = LOAD %9
           %11 = COPY #3
           %12 = SUB %10, %11
           %13 = COPY (GLOBAL v16)
           %14 = LOAD %13
           %15 = COPY #9
           %16 = SUB %14, %15
           %17 = SUB %12, %16
           %18 = SUB %8, %17
           RET %18
         */

        final IRContext ctx = new IRContext();
        final Register.Virtual[] v = new Register.Virtual[19];
        for (int i = 0; i < v.length; ++i)
            v[i] = ctx.newVReg(new IntType(32));

        final IRBlock blockA = new IRBlock();
        blockA.instrs.add(IRInstr.of(GenericOpcode.COPY)
                .addDef(new IRReg(v[0]))
                .addUse(new IRGlobal("v4"))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.LOAD)
                .addDef(new IRReg(v[1]))
                .addUse(new IRReg(v[0]))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.COPY)
                .addDef(new IRReg(v[2]))
                .addUse(new IRImm(22))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.SUB)
                .addDef(new IRReg(v[3]))
                .addUse(new IRReg(v[1]))
                .addUse(new IRReg(v[2]))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.COPY)
                .addDef(new IRReg(v[4]))
                .addUse(new IRGlobal("v8"))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.LOAD)
                .addDef(new IRReg(v[5]))
                .addUse(new IRReg(v[4]))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.COPY)
                .addDef(new IRReg(v[6]))
                .addUse(new IRImm(11))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.SUB)
                .addDef(new IRReg(v[7]))
                .addUse(new IRReg(v[5]))
                .addUse(new IRReg(v[6]))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.SUB)
                .addDef(new IRReg(v[8]))
                .addUse(new IRReg(v[3]))
                .addUse(new IRReg(v[7]))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.COPY)
                .addDef(new IRReg(v[9]))
                .addUse(new IRGlobal("v12"))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.LOAD)
                .addDef(new IRReg(v[10]))
                .addUse(new IRReg(v[9]))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.COPY)
                .addDef(new IRReg(v[11]))
                .addUse(new IRImm(3))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.SUB)
                .addDef(new IRReg(v[12]))
                .addUse(new IRReg(v[10]))
                .addUse(new IRReg(v[11]))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.COPY)
                .addDef(new IRReg(v[13]))
                .addUse(new IRGlobal("v16"))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.LOAD)
                .addDef(new IRReg(v[14]))
                .addUse(new IRReg(v[13]))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.COPY)
                .addDef(new IRReg(v[15]))
                .addUse(new IRImm(9))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.SUB)
                .addDef(new IRReg(v[16]))
                .addUse(new IRReg(v[14]))
                .addUse(new IRReg(v[15]))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.SUB)
                .addDef(new IRReg(v[17]))
                .addUse(new IRReg(v[12]))
                .addUse(new IRReg(v[16]))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.SUB)
                .addDef(new IRReg(v[18]))
                .addUse(new IRReg(v[8]))
                .addUse(new IRReg(v[17]))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.RET)
                .addUse(new IRReg(v[18]))
                .build());

        final IRFunction fn = new IRFunction();
        fn.blocks.add(blockA);

        // sanity check
        new FunctionValidator().validate(fn);

        System.out.println(fn);

        final X86Legalizer legalizer = new X86Legalizer(ctx);
        legalizer.legalize(fn);

        System.out.println(fn);

        final GraphAllocator allocator = new GraphAllocator(ctx);
        allocator.allocate(fn);

        System.out.println(fn);
    }

    @Test
    public void testPrecolorDiv() {
        /* Construct this case:

           %0 = COPY (GLOBAL v4)
           %1 = LOAD %0
           %2 = COPY #16
           %3 = DIV %1, %2      <- on x86, cdq+idiv clobbers edx and eax
           RET %3
         */

        final IRContext ctx = new IRContext();
        final Register.Virtual v0 = ctx.newVReg(new IntType(32));
        final Register.Virtual v1 = ctx.newVReg(new IntType(32));
        final Register.Virtual v2 = ctx.newVReg(new IntType(32));
        final Register.Virtual v3 = ctx.newVReg(new IntType(32));

        final IRBlock blockA = new IRBlock();
        blockA.instrs.add(IRInstr.of(GenericOpcode.COPY)
                .addDef(new IRReg(v0))
                .addUse(new IRGlobal("v4"))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.LOAD)
                .addDef(new IRReg(v1))
                .addUse(new IRReg(v0))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.COPY)
                .addDef(new IRReg(v2))
                .addUse(new IRImm(22))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.DIV)
                .addDef(new IRReg(v3))
                .addUse(new IRReg(v1))
                .addUse(new IRReg(v2))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.RET)
                .addUse(new IRReg(v3))
                .build());

        final IRFunction fn = new IRFunction();
        fn.blocks.add(blockA);

        // sanity check
        new FunctionValidator().validate(fn);

        System.out.println(fn);

        final X86Legalizer legalizer = new X86Legalizer(ctx);
        legalizer.legalize(fn);

        System.out.println(fn);

        final GraphAllocator allocator = new GraphAllocator(ctx);
        allocator.allocate(fn);

        System.out.println(fn);
    }

    @Test
    public void testPrecolorShift() {
        /* Construct this case:

           %0 = COPY (GLOBAL v4)
           %1 = LOAD %0
           %2 = COPY #16
           %3 = SHL %1, %2      <- on x86, %2 must be in cl
           RET %3
         */

        final IRContext ctx = new IRContext();
        final Register.Virtual v0 = ctx.newVReg(new IntType(32));
        final Register.Virtual v1 = ctx.newVReg(new IntType(32));
        final Register.Virtual v2 = ctx.newVReg(new IntType(32));
        final Register.Virtual v3 = ctx.newVReg(new IntType(32));

        final IRBlock blockA = new IRBlock();
        blockA.instrs.add(IRInstr.of(GenericOpcode.COPY)
                .addDef(new IRReg(v0))
                .addUse(new IRGlobal("v4"))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.LOAD)
                .addDef(new IRReg(v1))
                .addUse(new IRReg(v0))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.COPY)
                .addDef(new IRReg(v2))
                .addUse(new IRImm(22))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.SHL)
                .addDef(new IRReg(v3))
                .addUse(new IRReg(v1))
                .addUse(new IRReg(v2))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.RET)
                .addUse(new IRReg(v3))
                .build());

        final IRFunction fn = new IRFunction();
        fn.blocks.add(blockA);

        // sanity check
        new FunctionValidator().validate(fn);

        System.out.println(fn);

        final X86Legalizer legalizer = new X86Legalizer(ctx);
        legalizer.legalize(fn);

        System.out.println(fn);

        final GraphAllocator allocator = new GraphAllocator(ctx);
        allocator.allocate(fn);

        System.out.println(fn);
    }

    @Test
    public void testEasy() {
        /* Construct this case:

           %0 = COPY (GLOBAL v4)
           %1 = LOAD %0
           %2 = COPY #16
           %3 = SUB %1, %2
           RET %3
         */

        final IRContext ctx = new IRContext();
        final Register.Virtual v0 = ctx.newVReg(new IntType(32));
        final Register.Virtual v1 = ctx.newVReg(new IntType(32));
        final Register.Virtual v2 = ctx.newVReg(new IntType(32));
        final Register.Virtual v3 = ctx.newVReg(new IntType(32));

        final IRBlock blockA = new IRBlock();
        blockA.instrs.add(IRInstr.of(GenericOpcode.COPY)
                .addDef(new IRReg(v0))
                .addUse(new IRGlobal("v4"))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.LOAD)
                .addDef(new IRReg(v1))
                .addUse(new IRReg(v0))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.COPY)
                .addDef(new IRReg(v2))
                .addUse(new IRImm(22))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.SUB)
                .addDef(new IRReg(v3))
                .addUse(new IRReg(v1))
                .addUse(new IRReg(v2))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.RET)
                .addUse(new IRReg(v3))
                .build());

        final IRFunction fn = new IRFunction();
        fn.blocks.add(blockA);

        // sanity check
        new FunctionValidator().validate(fn);

        System.out.println(fn);

        final X86Legalizer legalizer = new X86Legalizer(ctx);
        legalizer.legalize(fn);

        System.out.println(fn);

        final GraphAllocator allocator = new GraphAllocator(ctx);
        allocator.allocate(fn);

        System.out.println(fn);
    }

    @Test
    public void testCountingLoop() {
        /* Construct this case:

           %3 = COPY #5
           JMP loop(#10)
        loop(%0):
           %1 = COPY #1
           %2 = SUB %0, %1
           JNZ %2, loop(%2)
           JMP end
        end:
           %4 = ADD %0, %3
           RET %4
         */

        final IRContext ctx = new IRContext();
        final Register.Virtual v0 = ctx.newVReg(new IntType(32));
        final Register.Virtual v1 = ctx.newVReg(new IntType(32));
        final Register.Virtual v2 = ctx.newVReg(new IntType(32));
        final Register.Virtual v3 = ctx.newVReg(new IntType(32));
        final Register.Virtual v4 = ctx.newVReg(new IntType(32));

        final IRBlock blockA = new IRBlock();
        final IRBlock blockB = new IRBlock();
        final IRBlock blockC = new IRBlock();

        blockA.instrs.add(IRInstr.of(GenericOpcode.COPY)
                .addDef(new IRReg(v3))
                .addUse(new IRImm(5))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.JMP)
                .addUse(blockB.handle)
                .addUse(new IRImm(16))
                .build());

        blockB.inputs.add(v0);
        blockB.instrs.add(IRInstr.of(GenericOpcode.COPY)
                .addDef(new IRReg(v1))
                .addUse(new IRImm(1))
                .build());
        blockB.instrs.add(IRInstr.of(GenericOpcode.SUB)
                .addDef(new IRReg(v2))
                .addUse(new IRReg(v0))
                .addUse(new IRReg(v1))
                .build());
        blockB.instrs.add(IRInstr.of(GenericOpcode.JNZ)
                .addUse(new IRReg(v2))
                .addUse(blockB.handle)
                .addUse(new IRReg(v2))
                .build());
        blockB.instrs.add(IRInstr.of(GenericOpcode.JMP)
                .addUse(blockC.handle)
                .build());

        blockC.instrs.add(IRInstr.of(GenericOpcode.ADD)
                .addDef(new IRReg(v4))
                .addUse(new IRReg(v0))
                .addUse(new IRReg(v3))
                .build());
        blockC.instrs.add(IRInstr.of(GenericOpcode.RET)
                .addUse(new IRReg(v4))
                .build());

        final IRFunction fn = new IRFunction();
        fn.blocks.add(blockA);
        fn.blocks.add(blockB);
        fn.blocks.add(blockC);

        // sanity check
        new FunctionValidator().validate(fn);

        System.out.println(fn);

        final X86Legalizer legalizer = new X86Legalizer(ctx);
        legalizer.legalize(fn);

        System.out.println(fn);

        final GraphAllocator allocator = new GraphAllocator(ctx);
        allocator.allocate(fn);

        System.out.println(fn);
    }

    @Test
    public void testSwappingLoop() {
        /* Construct this case:

           JMP loop(#7, #2)
        loop(%0, %1):
           JMP loop(%1, %0)
         */

        final IRContext ctx = new IRContext();
        final Register.Virtual v0 = ctx.newVReg(new IntType(32));
        final Register.Virtual v1 = ctx.newVReg(new IntType(32));

        final IRBlock blockA = new IRBlock();
        final IRBlock blockB = new IRBlock();

        blockA.instrs.add(IRInstr.of(GenericOpcode.JMP)
                .addUse(blockB.handle)
                .addUse(new IRImm(7))
                .addUse(new IRImm(2))
                .build());

        blockB.inputs.addAll(Arrays.asList(v0, v1));
        blockB.instrs.add(IRInstr.of(GenericOpcode.JMP)
                .addUse(blockB.handle)
                .addUse(new IRReg(v1))
                .addUse(new IRReg(v0))
                .build());

        final IRFunction fn = new IRFunction();
        fn.blocks.add(blockA);
        fn.blocks.add(blockB);

        // sanity check
        new FunctionValidator().validate(fn);

        System.out.println(fn);

        final X86Legalizer legalizer = new X86Legalizer(ctx);
        legalizer.legalize(fn);

        System.out.println(fn);

        final GraphAllocator allocator = new GraphAllocator(ctx);
        allocator.allocate(fn);

        System.out.println(fn);
    }
}
