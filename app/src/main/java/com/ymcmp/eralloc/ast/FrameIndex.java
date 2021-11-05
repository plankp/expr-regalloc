package com.ymcmp.eralloc.ast;

public final class FrameIndex implements ExprAST {

    public final int value;

    public FrameIndex(int value) {
        this.value = value;
    }

    @Override
    public <V> V accept(Visitor<V> vis) {
        return vis.visitFrameIndex(this);
    }
}