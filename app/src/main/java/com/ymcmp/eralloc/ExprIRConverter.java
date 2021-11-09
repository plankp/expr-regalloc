package com.ymcmp.eralloc;

import java.util.*;
import java.util.function.Consumer;
import com.ymcmp.eralloc.ast.*;

public final class ExprIRConverter implements ExprAST.Visitor<String> {

    private final HashMap<ExprAST, Integer> map = new HashMap<>();
    private final Consumer<? super IRInstr> cb;

    public ExprIRConverter(Consumer<? super IRInstr> cb) {
        this.cb = cb;
    }

    private String allocate(ExprAST e) {
        final int sz = this.map.size();
        final int id = this.map.computeIfAbsent(e, k -> sz);
        return "<" + id + ">";
    }

    @Override
    public String visitNumeric(Numeric e) {
        final String c = this.allocate(e);
        this.cb.accept(IRInstr.makev("const", c, Integer.toString(e.value)));

        return c;
    }

    @Override
    public String visitFrameIndex(FrameIndex e) {
        final String c = this.allocate(e);
        this.cb.accept(IRInstr.makev("frame", c, Integer.toString(e.value)));

        return c;
    }

    @Override
    public String visitGlobalValue(GlobalValue e) {
        final String c = this.allocate(e);
        this.cb.accept(IRInstr.makev("global", c, e.value));

        return c;
    }

    @Override
    public String visitLoadExpr(LoadExpr e) {
        final String ptr = e.ptr.accept(this);

        final String c = this.allocate(e);
        this.cb.accept(IRInstr.makev("load", c, ptr));

        return c;
    }

    @Override
    public String visitStoreExpr(StoreExpr e) {
        final String ptr = e.ptr.accept(this);
        final String value = e.value.accept(this);

        final String c = this.allocate(e);
        this.cb.accept(IRInstr.makev("copy", c, value));
        this.cb.accept(IRInstr.make("store", ptr, c));

        return c;
    }

    @Override
    public String visitAddExpr(AddExpr e) {
        final String lhs = e.lhs.accept(this);
        final String rhs = e.rhs.accept(this);

        final String c = this.allocate(e);
        this.cb.accept(IRInstr.makev("add", c, lhs, rhs));

        return c;
    }

    @Override
    public String visitSubExpr(SubExpr e) {
        final String lhs = e.lhs.accept(this);
        final String rhs = e.rhs.accept(this);

        final String c = this.allocate(e);
        this.cb.accept(IRInstr.makev("sub", c, lhs, rhs));

        return c;
    }

    @Override
    public String visitMulExpr(MulExpr e) {
        final String lhs = e.lhs.accept(this);
        final String rhs = e.rhs.accept(this);

        final String c = this.allocate(e);
        this.cb.accept(IRInstr.makev("mul", c, lhs, rhs));

        return c;
    }

    @Override
    public String visitDivExpr(DivExpr e) {
        final String lhs = e.lhs.accept(this);
        final String rhs = e.rhs.accept(this);

        final String c = this.allocate(e);
        this.cb.accept(IRInstr.makev("div", c, lhs, rhs));

        return c;
    }

    @Override
    public String visitRemExpr(RemExpr e) {
        final String lhs = e.lhs.accept(this);
        final String rhs = e.rhs.accept(this);

        final String c = this.allocate(e);
        this.cb.accept(IRInstr.makev("rem", c, lhs, rhs));

        return c;
    }

    @Override
    public String visitShlExpr(ShlExpr e) {
        final String lhs = e.lhs.accept(this);
        final String rhs = e.rhs.accept(this);

        final String c = this.allocate(e);
        this.cb.accept(IRInstr.makev("shl", c, lhs, rhs));

        return c;
    }

    @Override
    public String visitSraExpr(SraExpr e) {
        final String lhs = e.lhs.accept(this);
        final String rhs = e.rhs.accept(this);

        final String c = this.allocate(e);
        this.cb.accept(IRInstr.makev("sra", c, lhs, rhs));

        return c;
    }

    @Override
    public String visitSrlExpr(SrlExpr e) {
        final String lhs = e.lhs.accept(this);
        final String rhs = e.rhs.accept(this);

        final String c = this.allocate(e);
        this.cb.accept(IRInstr.makev("srl", c, lhs, rhs));

        return c;
    }

    @Override
    public String visitCallExpr(CallExpr e) {
        final String[] info = new String[e.args.length + 1];
        for (int i = 0; i < e.args.length; ++i)
            info[i + 1] = e.args[i].accept(this);
        info[0] = e.fn.accept(this);

        final String c = this.allocate(e);
        this.cb.accept(IRInstr.makev("call", c, info));

        return c;
    }
}
