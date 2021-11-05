package com.ymcmp.eralloc.ast;

public interface ExprAST {

    public interface Visitor<V> {

        public V visitNumeric(Numeric e);
        public V visitFrameIndex(FrameIndex e);
        public V visitGlobalValue(GlobalValue e);

        public V visitLoadExpr(LoadExpr e);
        public V visitStoreExpr(StoreExpr e);

        public V visitAddExpr(AddExpr e);
        public V visitSubExpr(SubExpr e);
        public V visitMulExpr(MulExpr e);
        public V visitDivExpr(DivExpr e);
        public V visitRemExpr(RemExpr e);

        public V visitShlExpr(ShlExpr e);
        public V visitSraExpr(SraExpr e);
        public V visitSrlExpr(SrlExpr e);

        public V visitCallExpr(CallExpr e);
    }

    public <V> V accept(Visitor<V> vis);
}