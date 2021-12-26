package com.ymcmp.eralloc;

import java.util.*;
import java.util.function.Consumer;
import com.ymcmp.eralloc.ir.*;
import com.ymcmp.eralloc.ast.*;
import static com.ymcmp.eralloc.ir.InstrName.Generic.*;

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
        this.cb.accept(IRInstr.makev(COPY, c, new IRImm(e.value)));

        return c;
    }

    @Override
    public IRValue visitFrameIndex(FrameIndex e) {
        final IRReg c = this.allocate(e);
        final IRFrameIndex.Info info = this.frameInfos.computeIfAbsent(e.value, k -> this.ctx.newFrameIndex(4));
        this.cb.accept(IRInstr.makev(COPY, c, new IRFrameIndex(info, 0)));

        return c;
    }

    @Override
    public IRValue visitGlobalValue(GlobalValue e) {
        final IRReg c = this.allocate(e);
        this.cb.accept(IRInstr.makev(COPY, c, new IRGlobal(e.value)));

        return c;
    }

    @Override
    public IRValue visitLoadExpr(LoadExpr e) {
        final IRValue ptr = e.ptr.accept(this);

        final IRReg c = this.allocate(e);
        this.cb.accept(IRInstr.makev(LOAD, c, ptr));

        return c;
    }

    @Override
    public IRValue visitStoreExpr(StoreExpr e) {
        final IRValue ptr = e.ptr.accept(this);
        final IRValue value = e.value.accept(this);

        final IRReg c = this.allocate(e);
        this.cb.accept(IRInstr.makev(COPY, c, value));
        this.cb.accept(IRInstr.make(STORE, ptr, c));

        return c;
    }

    @Override
    public IRValue visitAddExpr(AddExpr e) {
        final IRValue lhs = e.lhs.accept(this);
        final IRValue rhs = e.rhs.accept(this);

        final IRReg c = this.allocate(e);
        this.cb.accept(IRInstr.makev(ADD, c, lhs, rhs));

        return c;
    }

    @Override
    public IRValue visitSubExpr(SubExpr e) {
        final IRValue lhs = e.lhs.accept(this);
        final IRValue rhs = e.rhs.accept(this);

        final IRReg c = this.allocate(e);
        this.cb.accept(IRInstr.makev(SUB, c, lhs, rhs));

        return c;
    }

    @Override
    public IRValue visitMulExpr(MulExpr e) {
        final IRValue lhs = e.lhs.accept(this);
        final IRValue rhs = e.rhs.accept(this);

        final IRReg c = this.allocate(e);
        this.cb.accept(IRInstr.makev(MUL, c, lhs, rhs));

        return c;
    }

    @Override
    public IRValue visitDivExpr(DivExpr e) {
        final IRValue lhs = e.lhs.accept(this);
        final IRValue rhs = e.rhs.accept(this);

        final IRReg c = this.allocate(e);
        this.cb.accept(IRInstr.makev(DIV, c, lhs, rhs));

        return c;
    }

    @Override
    public IRValue visitRemExpr(RemExpr e) {
        final IRValue lhs = e.lhs.accept(this);
        final IRValue rhs = e.rhs.accept(this);

        final IRReg c = this.allocate(e);
        this.cb.accept(IRInstr.makev(REM, c, lhs, rhs));

        return c;
    }

    @Override
    public IRValue visitShlExpr(ShlExpr e) {
        final IRValue lhs = e.lhs.accept(this);
        final IRValue rhs = e.rhs.accept(this);

        final IRReg c = this.allocate(e);
        this.cb.accept(IRInstr.makev(SHL, c, lhs, rhs));

        return c;
    }

    @Override
    public IRValue visitSraExpr(SraExpr e) {
        final IRValue lhs = e.lhs.accept(this);
        final IRValue rhs = e.rhs.accept(this);

        final IRReg c = this.allocate(e);
        this.cb.accept(IRInstr.makev(SRA, c, lhs, rhs));

        return c;
    }

    @Override
    public IRValue visitSrlExpr(SrlExpr e) {
        final IRValue lhs = e.lhs.accept(this);
        final IRValue rhs = e.rhs.accept(this);

        final IRReg c = this.allocate(e);
        this.cb.accept(IRInstr.makev(SRL, c, lhs, rhs));

        return c;
    }

    @Override
    public IRValue visitCallExpr(CallExpr e) {
        final IRValue[] info = new IRValue[e.args.length + 1];
        for (int i = 0; i < e.args.length; ++i)
            info[i + 1] = e.args[i].accept(this);
        info[0] = e.fn.accept(this);

        final IRReg c = this.allocate(e);
        this.cb.accept(IRInstr.makev(CALL, c, info));

        return c;
    }
}
