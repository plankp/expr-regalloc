package com.ymcmp.eralloc;

import java.io.PrintWriter;
import java.util.*;
import com.ymcmp.eralloc.ast.*;

public final class ExprPrinter {

    private ExprPrinter() {
    }

    public static void print(final PrintWriter pw, ExprAST e) {
        final ExprIRConverter conv = new ExprIRConverter(i ->
                pw.println(i.toString()));
        e.accept(conv);
    }

    public static String emitIR(ExprAST e) {
        final StringBuilder sb = new StringBuilder();
        final ExprIRConverter conv = new ExprIRConverter(i ->
                sb.append(i).append('\n'));
        e.accept(conv);
        return sb.toString();
    }
}
