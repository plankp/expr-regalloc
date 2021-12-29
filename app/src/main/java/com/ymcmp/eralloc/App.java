package com.ymcmp.eralloc;

import java.io.IOException;
import com.ymcmp.eralloc.ir.*;
import com.ymcmp.eralloc.ast.*;
import com.ymcmp.eralloc.target.x86.*;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

public class App {
    public static void main(String[] args) throws IOException {
        final CharStream stream;
        if (args.length == 0) {
            stream = CharStreams.fromStream(System.in);
        } else {
            stream = CharStreams.fromFileName(args[0]);
        }

        final SampleLexer lexer = new SampleLexer(stream);
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final SampleParser parser = new SampleParser(tokens);
        final ParseTree tree = parser.fdecl();

        final IRContext ctx = new IRContext();
        final IRFunction fn = (IRFunction) new Emitter(ctx).visit(tree);

        new FunctionValidator().validate(fn);

        final X86Legalizer legalizer = new X86Legalizer(ctx);
        legalizer.legalize(fn);

        final GraphAllocator allocator = new GraphAllocator(ctx);
        allocator.allocate(fn);

        final X86Finalizer finalizer = new X86Finalizer(ctx);
        for (final String line : finalizer.finalize(fn))
            System.out.println(line);
    }
}
