package com.ymcmp.eralloc.ast;

public final class GlobalValue implements ExprAST {

    public final String value;

    public GlobalValue(String value) {
        this.value = value;
    }

    @Override
    public <V> V accept(Visitor<V> vis) {
        return vis.visitGlobalValue(this);
    }
}