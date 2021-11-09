package com.ymcmp.eralloc;

import java.util.*;
import com.ymcmp.eralloc.ast.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ExprPrinterTest {

    @Test
    public void testCodegenNumeric() {
        assertEquals(
                "<0> = const 123\n",
                ExprPrinter.emitIR(new Numeric(123)));
    }

    @Test
    public void testCodegenFrameIndex() {
        assertEquals(
                "<0> = frame 16\n",
                ExprPrinter.emitIR(new FrameIndex(16)));
    }

    @Test
    public void testCodegenGlobalValue() {
        assertEquals(
                "<0> = global foo\n",
                ExprPrinter.emitIR(new GlobalValue("foo")));
    }

    @Test
    public void testCodegenLoad() {
        assertEquals(
                "<0> = global foo\n<1> = load <0>\n",
                ExprPrinter.emitIR(new LoadExpr(new GlobalValue("foo"))));
    }

    @Test
    public void testCodegenStore() {
        assertEquals(
                "<0> = global foo\n<1> = const 10\n<2> = copy <1>\nstore <0>, <2>\n",
                ExprPrinter.emitIR(new StoreExpr(new GlobalValue("foo"), new Numeric(10))));
    }

    @Test
    public void testCodegenAdd() {
        assertEquals(
                "<0> = const 1\n<1> = const 2\n<2> = add <0>, <1>\n",
                ExprPrinter.emitIR(new AddExpr(new Numeric(1), new Numeric(2))));
    }

    @Test
    public void testCodegenSub() {
        assertEquals(
                "<0> = const 1\n<1> = const 2\n<2> = sub <0>, <1>\n",
                ExprPrinter.emitIR(new SubExpr(new Numeric(1), new Numeric(2))));
    }

    @Test
    public void testCodegenMul() {
        assertEquals(
                "<0> = const 1\n<1> = const 2\n<2> = mul <0>, <1>\n",
                ExprPrinter.emitIR(new MulExpr(new Numeric(1), new Numeric(2))));
    }

    @Test
    public void testCodegenDiv() {
        assertEquals(
                "<0> = const 1\n<1> = const 2\n<2> = div <0>, <1>\n",
                ExprPrinter.emitIR(new DivExpr(new Numeric(1), new Numeric(2))));
    }

    @Test
    public void testCodegenRem() {
        assertEquals(
                "<0> = const 1\n<1> = const 2\n<2> = rem <0>, <1>\n",
                ExprPrinter.emitIR(new RemExpr(new Numeric(1), new Numeric(2))));
    }

    @Test
    public void testCodegenShl() {
        assertEquals(
                "<0> = const 1\n<1> = const 2\n<2> = shl <0>, <1>\n",
                ExprPrinter.emitIR(new ShlExpr(new Numeric(1), new Numeric(2))));
    }

    @Test
    public void testCodegenSra() {
        assertEquals(
                "<0> = const 1\n<1> = const 2\n<2> = sra <0>, <1>\n",
                ExprPrinter.emitIR(new SraExpr(new Numeric(1), new Numeric(2))));
    }

    @Test
    public void testCodegenSrl() {
        assertEquals(
                "<0> = const 1\n<1> = const 2\n<2> = srl <0>, <1>\n",
                ExprPrinter.emitIR(new SrlExpr(new Numeric(1), new Numeric(2))));
    }

    @Test
    public void testCodegenCall() {
        assertEquals(
                "<0> = const 2\n<1> = global f\n<2> = call <1>, <0>\n",
                ExprPrinter.emitIR(new CallExpr(new GlobalValue("f"), new Numeric(2))));
    }

    @Test
    public void testSpilling() {
        assertEquals(
                "<0> = global v4\n" +
                "<1> = load <0>\n" +
                "<2> = const 22\n" +
                "<3> = sub <1>, <2>\n" +
                "<4> = global v8\n" +
                "<5> = load <4>\n" +
                "<6> = const 11\n" +
                "<7> = sub <5>, <6>\n" +
                "<8> = sub <3>, <7>\n" +
                "<9> = global v12\n" +
                "<10> = load <9>\n" +
                "<11> = const 3\n" +
                "<12> = sub <10>, <11>\n" +
                "<13> = global v16\n" +
                "<14> = load <13>\n" +
                "<15> = const 9\n" +
                "<16> = sub <14>, <15>\n" +
                "<17> = sub <12>, <16>\n" +
                "<18> = sub <8>, <17>\n",
                ExprPrinter.emitIR(new SubExpr(
                    new SubExpr(
                        new SubExpr(new LoadExpr(new GlobalValue("v4")), new Numeric(22)),
                        new SubExpr(new LoadExpr(new GlobalValue("v8")), new Numeric(11))),
                    new SubExpr(
                        new SubExpr(new LoadExpr(new GlobalValue("v12")), new Numeric(3)),
                        new SubExpr(new LoadExpr(new GlobalValue("v16")), new Numeric(9))))));
    }
}
