package com.ymcmp.eralloc;

import java.io.PrintWriter;
import java.util.*;
import com.ymcmp.eralloc.ir.IRInstr;
import com.ymcmp.eralloc.ir.IRContext;
import com.ymcmp.eralloc.ast.*;

public final class ExprPrinter {

    private final IRContext ctx;

    public ExprPrinter(IRContext ctx) {
        this.ctx = ctx;
    }

    public List<IRInstr> emit(ExprAST e) {
        final List<IRInstr> lst = new ArrayList<>();
        final ExprIRConverter conv = new ExprIRConverter(this.ctx, lst::add);
        e.accept(conv);
        return lst;
    }
}