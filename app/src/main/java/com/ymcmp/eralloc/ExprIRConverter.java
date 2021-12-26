package com.ymcmp.eralloc;

import java.util.*;
import java.util.function.Consumer;
import com.ymcmp.eralloc.ir.*;
import com.ymcmp.eralloc.ast.*;

public final class ExprIRConverter implements ExprAST.Visitor<IRValue> {

    private final HashMap<ExprAST, RegName.Virtual> vregs = new HashMap<>();
    private final HashMap<Integer, IRFrameIndex.Info> frameInfos = new HashMap<>();
    private final IRContext ctx;
    private final Consumer<? super IRInstr> cb;

    public ExprIRConverter(IRContext context, Consumer<? super IRInstr> cb) {
        this.ctx = context;
        this.cb = cb;
    }

    private IRReg allocate(ExprAST e) {
        return new IRReg(this.vregs.computeIfAbsent(e, k -> this.ctx.newVReg(X86RegisterType.GR32)));
    }

    @Override
    public IRValue visitNumeric(Numeric e) {
        final IRReg c = this.allocate(e);
        this.cb.accept(IRInstr.makev("copy", c, new IRImm(e.value)));

        return c;
    }

    @Override
    public IRValue visitFrameIndex(FrameIndex e) {
        final IRReg c = this.allocate(e);
        final IRFrameIndex.Info info = this.frameInfos.computeIfAbsent(e.value, k -> this.ctx.newFrameIndex(4));
        this.cb.accept(IRInstr.makev("copy", c, new IRFrameIndex(info, 0)));

        return c;
    }

    @Override
    public IRValue visitGlobalValue(GlobalValue e) {
        final IRReg c = this.allocate(e);
        this.cb.accept(IRInstr.makev("copy", c, new IRGlobal(e.value)));

        return c;
    }

    @Override
    public IRValue visitLoadExpr(LoadExpr e) {
        final IRValue ptr = e.ptr.accept(this);

        final IRReg c = this.allocate(e);
        this.cb.accept(IRInstr.makev("load", c, ptr));

        return c;
    }

    @Override
    public IRValue visitStoreExpr(StoreExpr e) {
        final IRValue ptr = e.ptr.accept(this);
        final IRValue value = e.value.accept(this);

        final IRReg c = this.allocate(e);
        this.cb.accept(IRInstr.makev("copy", c, value));
        this.cb.accept(IRInstr.make("store", ptr, c));

        return c;
    }

    @Override
    public IRValue visitAddExpr(AddExpr e) {
        final IRValue lhs = e.lhs.accept(this);
        final IRValue rhs = e.rhs.accept(this);

        final IRReg c = this.allocate(e);
        this.cb.accept(IRInstr.makev("add", c, lhs, rhs));

        return c;
    }

    @Override
    public IRValue visitSubExpr(SubExpr e) {
        final IRValue lhs = e.lhs.accept(this);
        final IRValue rhs = e.rhs.accept(this);

        final IRReg c = this.allocate(e);
        this.cb.accept(IRInstr.makev("sub", c, lhs, rhs));

        return c;
    }

    @Override
    public IRValue visitMulExpr(MulExpr e) {
        final IRValue lhs = e.lhs.accept(this);
        final IRValue rhs = e.rhs.accept(this);

        final IRReg c = this.allocate(e);
        this.cb.accept(IRInstr.makev("mul", c, lhs, rhs));

        return c;
    }

    @Override
    public IRValue visitDivExpr(DivExpr e) {
        final IRValue lhs = e.lhs.accept(this);
        final IRValue rhs = e.rhs.accept(this);

        final IRReg c = this.allocate(e);
        this.cb.accept(IRInstr.makev("div", c, lhs, rhs));

        return c;
    }

    @Override
    public IRValue visitRemExpr(RemExpr e) {
        final IRValue lhs = e.lhs.accept(this);
        final IRValue rhs = e.rhs.accept(this);

        final IRReg c = this.allocate(e);
        this.cb.accept(IRInstr.makev("rem", c, lhs, rhs));

        return c;
    }

    @Override
    public IRValue visitShlExpr(ShlExpr e) {
        final IRValue lhs = e.lhs.accept(this);
        final IRValue rhs = e.rhs.accept(this);

        final IRReg c = this.allocate(e);
        this.cb.accept(IRInstr.makev("shl", c, lhs, rhs));

        return c;
    }

    @Override
    public IRValue visitSraExpr(SraExpr e) {
        final IRValue lhs = e.lhs.accept(this);
        final IRValue rhs = e.rhs.accept(this);

        final IRReg c = this.allocate(e);
        this.cb.accept(IRInstr.makev("sra", c, lhs, rhs));

        return c;
    }

    @Override
    public IRValue visitSrlExpr(SrlExpr e) {
        final IRValue lhs = e.lhs.accept(this);
        final IRValue rhs = e.rhs.accept(this);

        final IRReg c = this.allocate(e);
        this.cb.accept(IRInstr.makev("srl", c, lhs, rhs));

        return c;
    }

    @Override
    public IRValue visitCallExpr(CallExpr e) {
        final IRValue[] info = new IRValue[e.args.length + 1];
        for (int i = 0; i < e.args.length; ++i)
            info[i + 1] = e.args[i].accept(this);
        info[0] = e.fn.accept(this);

        final IRReg c = this.allocate(e);
        this.cb.accept(IRInstr.makev("call", c, info));

        return c;
    }
}
