package com.ymcmp.eralloc.ast;

public final class CallExpr implements ExprAST {

    public final ExprAST fn;
    public final ExprAST[] args;

    public CallExpr(ExprAST fn, ExprAST... args) {
        this.fn = fn;
        this.args = args;
    }

    @Override
    public <V> V accept(Visitor<V> vis) {
        return vis.visitCallExpr(this);
    }
}