package com.ymcmp.eralloc.ast;

public final class SrlExpr implements ExprAST {

    public final ExprAST lhs;
    public final ExprAST rhs;

    public SrlExpr(ExprAST lhs, ExprAST rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public <V> V accept(Visitor<V> vis) {
        return vis.visitSrlExpr(this);
    }
}