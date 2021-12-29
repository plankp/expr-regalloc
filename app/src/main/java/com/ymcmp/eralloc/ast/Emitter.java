package com.ymcmp.eralloc.ast;

import java.util.*;
import com.ymcmp.eralloc.ir.*;
import com.ymcmp.eralloc.target.generic.*;
import com.ymcmp.eralloc.target.generic.types.*;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

public final class Emitter extends SampleBaseVisitor<Object> {

    private IRContext irctx;
    private IRFunction fn;
    private IRBlock block;
    private Map<String, Register.Virtual> locals;

    public Emitter(IRContext ctx) {
        this.irctx = ctx;
    }

    private void createBlock() {
        final IRBlock block = new IRBlock();
        this.block = block;
        this.fn.blocks.add(block);
    }

    @Override
    public Object visitFdecl(SampleParser.FdeclContext ctx) {
        this.fn = new IRFunction();
        fn.setName(ctx.name.getText());

        this.createBlock();

        this.locals = new HashMap<>();
        for (final Token token : ctx.params) {
            final String name = token.getText();
            final Register.Virtual vreg = this.irctx.newVReg(new IntType(32));
            this.block.inputs.add(vreg);
            if (this.locals.put(name, vreg) != null)
                throw new RuntimeException("Duplicate name definition: " + name);
        }

        final IRValue v = (IRValue) this.visit(ctx.e);
        this.block.instrs.add(IRInstr.of(GenericOpcode.RET)
                .addUse(v)
                .build());

        return this.fn;
    }

    @Override
    public IRValue visitExprAdditive(SampleParser.ExprAdditiveContext ctx) {
        final IRValue lhs = (IRValue) this.visit(ctx.lhs);
        final IRValue rhs = (IRValue) this.visit(ctx.rhs);
        final IRReg tmp = new IRReg(this.irctx.newVReg(new IntType(32)));

        final GenericOpcode op;
        switch (ctx.op.getText()) {
        case "+": op = GenericOpcode.ADD; break;
        case "-": op = GenericOpcode.SUB; break;
        default:  throw new AssertionError("Unknown operator " + ctx.op.getText());
        }

        this.block.instrs.add(IRInstr.of(op)
                .addDef(tmp)
                .addUse(lhs)
                .addUse(rhs)
                .build());
        return tmp;
    }

    @Override
    public IRValue visitExprMultiplicative(SampleParser.ExprMultiplicativeContext ctx) {
        final IRValue lhs = (IRValue) this.visit(ctx.lhs);
        final IRValue rhs = (IRValue) this.visit(ctx.rhs);
        final IRReg tmp = new IRReg(this.irctx.newVReg(new IntType(32)));

        final GenericOpcode op;
        switch (ctx.op.getText()) {
        case "*":   op = GenericOpcode.MUL; break;
        case "/":   op = GenericOpcode.DIV; break;
        case "%":   op = GenericOpcode.REM; break;
        case "<<":  op = GenericOpcode.SHL; break;
        case ">>":  op = GenericOpcode.SRA; break;
        case ">>>": op = GenericOpcode.SRL; break;
        default:    throw new AssertionError("Unknown operator " + ctx.op.getText());
        }

        this.block.instrs.add(IRInstr.of(op)
                .addDef(tmp)
                .addUse(lhs)
                .addUse(rhs)
                .build());
        return tmp;
    }

    @Override
    public IRValue visitExprParenthesis(SampleParser.ExprParenthesisContext ctx) {
        return (IRValue) this.visit(ctx.e);
    }

    @Override
    public IRValue visitExprNumeric(SampleParser.ExprNumericContext ctx) {
        // Get rid of the underscores...
        String v = ctx.v.getText().replace("_", "");

        int radix = 10;
        if (v.length() > 2) {
            switch (v.charAt(1)) {
            case 'x': radix = 16; break;
            case 'c': radix = 8;  break;
            case 'b': radix = 2;  break;
            }
            if (radix != 0)
                v = v.substring(2);
        }

        final long amt = Long.parseUnsignedLong(v, radix);
        final IRReg tmp = new IRReg(this.irctx.newVReg(new IntType(32)));
        this.block.instrs.add(IRInstr.of(GenericOpcode.COPY)
                .addDef(tmp)
                .addUse(new IRImm(amt))
                .build());
        return tmp;
    }

    @Override
    public IRValue visitExprName(SampleParser.ExprNameContext ctx) {
        final String name = ctx.name.getText();
        final Register.Virtual vreg = this.locals.get(name);

        // if it's defined locally, treat it as a local.
        // otherwise, treat it as a global.
        final IRReg reg;
        if (vreg != null)
            return new IRReg(vreg);

        final IRReg tmp = new IRReg(this.irctx.newVReg(new IntType(32)));
        this.block.instrs.add(IRInstr.of(GenericOpcode.COPY)
                .addDef(tmp)
                .addUse(new IRGlobal(name))
                .build());
        return tmp;
    }
}
