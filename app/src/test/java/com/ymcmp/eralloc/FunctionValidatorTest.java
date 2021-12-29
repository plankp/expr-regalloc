package com.ymcmp.eralloc;

import java.util.*;
import com.ymcmp.eralloc.ir.*;
import com.ymcmp.eralloc.target.generic.GenericOpcode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class FunctionValidatorTest {

    private enum DummyRegType implements RegisterType {

        INSTANCE;

        @Override
        public int width() {
            return 32;
        }

        @Override
        public Collection<Register.Physical> getRegs() {
            return Collections.emptySet();
        }
    }

    @Test
    public void testUnterminatedBlock() {
        final IRContext ctx = new IRContext();
        final Register.Virtual faulty = ctx.newVReg(DummyRegType.INSTANCE);

        /* Construct this case:

           r = COPY 0
                        <- missing terminator
         */

        final IRBlock blockA = new IRBlock();

        blockA.instrs.add(IRInstr.of(GenericOpcode.COPY)
                .addDef(new IRReg(faulty))
                .addUse(new IRImm(0))
                .build());

        final IRFunction fn = new IRFunction();
        fn.blocks.add(blockA);

        final Exception ex = assertThrows(RuntimeException.class, () -> new FunctionValidator().validate(fn));
        assertEquals("Unterminated block", ex.getMessage());
    }

    @Test
    public void testBadStructure() {
        final IRContext ctx = new IRContext();
        final Register.Virtual faulty = ctx.newVReg(DummyRegType.INSTANCE);

        /* Construct this case:

           RET
           r = COPY 0   <- shouldn't be here
         */

        final IRBlock blockA = new IRBlock();

        blockA.instrs.add(IRInstr.of(GenericOpcode.RET)
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.COPY)
                .addDef(new IRReg(faulty))
                .addUse(new IRImm(0))
                .build());

        final IRFunction fn = new IRFunction();
        fn.blocks.add(blockA);

        final Exception ex = assertThrows(RuntimeException.class, () -> new FunctionValidator().validate(fn));
        assertEquals("Illegal instruction after terminator", ex.getMessage());
    }

    @Test
    public void testDuplicateDef() {
        final IRContext ctx = new IRContext();
        final Register.Virtual faulty = ctx.newVReg(DummyRegType.INSTANCE);

        /* Construct this case:

           r = COPY 0
           r = ADD r, 1     <- r is illegally redefined
         */

        final IRBlock blockA = new IRBlock();

        blockA.instrs.add(IRInstr.of(GenericOpcode.COPY)
                .addDef(new IRReg(faulty))
                .addUse(new IRImm(0))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.ADD)
                .addDef(new IRReg(faulty))
                .addUse(new IRReg(faulty))
                .addUse(new IRImm(0))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.RET)
                .build());

        final IRFunction fn = new IRFunction();
        fn.blocks.add(blockA);

        final Exception ex = assertThrows(RuntimeException.class, () -> new FunctionValidator().validate(fn));
        assertEquals("Duplicate definition of virtual register " + faulty, ex.getMessage());
    }

    @Test
    public void testDuplicateDefViaPhi() {
        final IRContext ctx = new IRContext();
        final Register.Virtual faulty = ctx.newVReg(DummyRegType.INSTANCE);

        /* Construct this case:
        A:
           r = COPY 0
           JMP B, r

        B(r):       <- r is illegally reused
           RET
         */

        final IRBlock blockA = new IRBlock();
        final IRBlock blockB = new IRBlock();

        blockA.instrs.add(IRInstr.of(GenericOpcode.COPY)
                .addDef(new IRReg(faulty))
                .addUse(new IRImm(0))
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.JMP)
                .addUse(blockB.handle)
                .addUse(new IRReg(faulty))
                .build());

        blockB.inputs.add(faulty);
        blockB.instrs.add(IRInstr.of(GenericOpcode.RET)
                .build());

        final IRFunction fn = new IRFunction();
        fn.blocks.add(blockA);
        fn.blocks.add(blockB);

        final Exception ex = assertThrows(RuntimeException.class, () -> new FunctionValidator().validate(fn));
        assertEquals("Duplicate definition of virtual register " + faulty, ex.getMessage());
    }

    @Test
    public void testDuplicateDefViaPath() {
        final IRContext ctx = new IRContext();
        final Register.Virtual faulty = ctx.newVReg(DummyRegType.INSTANCE);
        final Register.Virtual dummy = ctx.newVReg(DummyRegType.INSTANCE);

        /* Construct this case:
           A
           | \
           B  C     and define the same variable in both B and C
         */

        final IRBlock blockA = new IRBlock();
        final IRBlock blockB = new IRBlock();
        final IRBlock blockC = new IRBlock();

        blockA.instrs.add(IRInstr.of(GenericOpcode.JNZ)
                .addUse(new IRImm(0))
                .addUse(blockC.handle)
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.JMP)
                .addUse(blockB.handle)
                .build());

        blockB.instrs.add(IRInstr.of(GenericOpcode.COPY)
                .addDef(new IRReg(faulty))
                .addUse(new IRImm(0))
                .build());
        blockB.instrs.add(IRInstr.of(GenericOpcode.RET)
                .build());

        blockC.instrs.add(IRInstr.of(GenericOpcode.COPY)
                .addDef(new IRReg(faulty))
                .addUse(new IRImm(1))
                .build());
        blockC.instrs.add(IRInstr.of(GenericOpcode.RET)
                .build());

        final IRFunction fn = new IRFunction();
        fn.blocks.add(blockA);
        fn.blocks.add(blockB);
        fn.blocks.add(blockC);

        final Exception ex = assertThrows(RuntimeException.class, () -> new FunctionValidator().validate(fn));
        assertEquals("Duplicate definition of virtual register " + faulty, ex.getMessage());
    }

    @Test
    public void testMismatchPhi() {
        final IRContext ctx = new IRContext();
        final Register.Virtual faulty = ctx.newVReg(DummyRegType.INSTANCE);

        /* Construct this case:
        A:
           JMP B    <- missing (r)

        B(r):
           RET
         */

        final IRBlock blockA = new IRBlock();
        final IRBlock blockB = new IRBlock();

        blockA.instrs.add(IRInstr.of(GenericOpcode.JMP)
                .addUse(blockB.handle)
                .build());

        blockB.inputs.add(faulty);
        blockB.instrs.add(IRInstr.of(GenericOpcode.RET)
                .build());

        final IRFunction fn = new IRFunction();
        fn.blocks.add(blockA);
        fn.blocks.add(blockB);

        final Exception ex = assertThrows(RuntimeException.class, () -> new FunctionValidator().validate(fn));
        assertEquals("Mismatched phi node value", ex.getMessage());
    }

    @Test
    public void testMissingDef1() {
        final IRContext ctx = new IRContext();
        final Register.Virtual faulty = ctx.newVReg(DummyRegType.INSTANCE);
        final Register.Virtual dummy = ctx.newVReg(DummyRegType.INSTANCE);

        /* Construct this case:
           A
           | \      Visit Order: (A C [fail])
           B  |     this one takes the forward edge first.
           | /
           C
         */

        final IRBlock blockA = new IRBlock();
        final IRBlock blockB = new IRBlock();
        final IRBlock blockC = new IRBlock();

        blockA.instrs.add(IRInstr.of(GenericOpcode.JNZ)
                .addUse(new IRImm(0))
                .addUse(blockB.handle)
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.JMP)
                .addUse(blockC.handle)
                .build());

        blockB.instrs.add(IRInstr.of(GenericOpcode.COPY)
                .addDef(new IRReg(faulty))
                .addUse(new IRImm(0))
                .build());
        blockB.instrs.add(IRInstr.of(GenericOpcode.JMP)
                .addUse(blockC.handle)
                .build());

        blockC.instrs.add(IRInstr.of(GenericOpcode.ADD)
                .addDef(new IRReg(dummy))
                .addUse(new IRReg(faulty))
                .addUse(new IRImm(0))
                .build());
        blockC.instrs.add(IRInstr.of(GenericOpcode.RET)
                .build());

        final IRFunction fn = new IRFunction();
        fn.blocks.add(blockA);
        fn.blocks.add(blockB);
        fn.blocks.add(blockC);

        final Exception ex = assertThrows(RuntimeException.class, () -> new FunctionValidator().validate(fn));
        assertEquals("Use of undefined virtual register " + faulty, ex.getMessage());
    }

    @Test
    public void testMissingDef2() {
        final IRContext ctx = new IRContext();
        final Register.Virtual faulty = ctx.newVReg(DummyRegType.INSTANCE);
        final Register.Virtual dummy = ctx.newVReg(DummyRegType.INSTANCE);

        /* Construct this case:
           A
           | \      Visit Order: (A B C C [fail])
           B  |     this one takes the forward edge last.
           | /
           C
         */

        final IRBlock blockA = new IRBlock();
        final IRBlock blockB = new IRBlock();
        final IRBlock blockC = new IRBlock();

        blockA.instrs.add(IRInstr.of(GenericOpcode.JNZ)
                .addUse(new IRImm(0))
                .addUse(blockC.handle)
                .build());
        blockA.instrs.add(IRInstr.of(GenericOpcode.JMP)
                .addUse(blockB.handle)
                .build());

        blockB.instrs.add(IRInstr.of(GenericOpcode.COPY)
                .addDef(new IRReg(faulty))
                .addUse(new IRImm(0))
                .build());
        blockB.instrs.add(IRInstr.of(GenericOpcode.JMP)
                .addUse(blockC.handle)
                .build());

        blockC.instrs.add(IRInstr.of(GenericOpcode.ADD)
                .addDef(new IRReg(dummy))
                .addUse(new IRReg(faulty))
                .addUse(new IRImm(0))
                .build());
        blockC.instrs.add(IRInstr.of(GenericOpcode.RET)
                .build());

        final IRFunction fn = new IRFunction();
        fn.blocks.add(blockA);
        fn.blocks.add(blockB);
        fn.blocks.add(blockC);

        final Exception ex = assertThrows(RuntimeException.class, () -> new FunctionValidator().validate(fn));
        assertEquals("Use of undefined virtual register " + faulty, ex.getMessage());
    }
}
