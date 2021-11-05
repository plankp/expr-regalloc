package com.ymcmp.eralloc.ast;

public final class MulExpr implements ExprAST {

    public final ExprAST lhs;
    public final ExprAST rhs;

    public MulExpr(ExprAST lhs, ExprAST rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public <V> V accept(Visitor<V> vis) {
        return vis.visitMulExpr(this);
    }
}