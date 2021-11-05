package com.ymcmp.eralloc.ast;

public final class ShlExpr implements ExprAST {

    public final ExprAST lhs;
    public final ExprAST rhs;

    public ShlExpr(ExprAST lhs, ExprAST rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public <V> V accept(Visitor<V> vis) {
        return vis.visitShlExpr(this);
    }
}