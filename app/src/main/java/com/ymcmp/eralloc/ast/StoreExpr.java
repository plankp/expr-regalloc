package com.ymcmp.eralloc.ast;

public final class StoreExpr implements ExprAST {

    public final ExprAST ptr;
    public final ExprAST value;

    public StoreExpr(ExprAST ptr, ExprAST value) {
        this.ptr = ptr;
        this.value = value;
    }

    @Override
    public <V> V accept(Visitor<V> vis) {
        return vis.visitStoreExpr(this);
    }
}