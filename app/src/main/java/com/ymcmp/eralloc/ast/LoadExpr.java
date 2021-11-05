package com.ymcmp.eralloc.ast;

public final class LoadExpr implements ExprAST {

    public final ExprAST ptr;

    public LoadExpr(ExprAST ptr) {
        this.ptr = ptr;
    }

    @Override
    public <V> V accept(Visitor<V> vis) {
        return vis.visitLoadExpr(this);
    }
}