package com.ymcmp.eralloc.ast;

public final class DivExpr implements ExprAST {

    public final ExprAST lhs;
    public final ExprAST rhs;

    public DivExpr(ExprAST lhs, ExprAST rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public <V> V accept(Visitor<V> vis) {
        return vis.visitDivExpr(this);
    }
}