package com.ymcmp.eralloc.ast;

public final class Numeric implements ExprAST {

    public final int value;

    public Numeric(int value) {
        this.value = value;
    }

    @Override
    public <V> V accept(Visitor<V> vis) {
        return vis.visitNumeric(this);
    }
}