package com.ymcmp.eralloc;

import java.util.*;
import com.ymcmp.eralloc.ast.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ERAllocTest {

    @Test
    public void testCodegenNumeric() {
        assertEquals(
                Arrays.asList("mov eax, 123"),
                ERAlloc.codegen(new Numeric(123)));
    }

    @Test
    public void testCodegenFrameIndex() {
        assertEquals(
                Arrays.asList("lea eax, [ebp-16]"),
                ERAlloc.codegen(new FrameIndex(16)));
    }

    @Test
    public void testCodegenGlobalValue() {
        assertEquals(
                Arrays.asList("extern foo", "mov eax, foo"),
                ERAlloc.codegen(new GlobalValue("foo")));
    }

    @Test
    public void testCodegenLoad() {
        assertEquals(
                Arrays.asList("extern foo", "mov eax, foo", "mov eax, [eax]"),
                ERAlloc.codegen(new LoadExpr(new GlobalValue("foo"))));
    }

    @Test
    public void testCodegenStore() {
        assertEquals(
                Arrays.asList("mov eax, 10", "extern foo", "mov ecx, foo", "mov [ecx], eax"),
                ERAlloc.codegen(new StoreExpr(new GlobalValue("foo"), new Numeric(10))));
    }

    @Test
    public void testCodegenAdd() {
        assertEquals(
                Arrays.asList("mov eax, 1", "mov ecx, 2", "add eax, ecx"),
                ERAlloc.codegen(new AddExpr(new Numeric(1), new Numeric(2))));
    }

    @Test
    public void testCodegenSub() {
        assertEquals(
                Arrays.asList("mov eax, 1", "mov ecx, 2", "sub eax, ecx"),
                ERAlloc.codegen(new SubExpr(new Numeric(1), new Numeric(2))));
    }

    @Test
    public void testCodegenMul() {
        assertEquals(
                Arrays.asList("mov eax, 1", "mov ecx, 2", "imul eax, ecx"),
                ERAlloc.codegen(new MulExpr(new Numeric(1), new Numeric(2))));
    }

    @Test
    public void testCodegenDiv() {
        assertEquals(
                Arrays.asList("mov eax, 1", "mov ecx, 2", "cdq", "idiv ecx"),
                ERAlloc.codegen(new DivExpr(new Numeric(1), new Numeric(2))));
    }

    @Test
    public void testCodegenRem() {
        assertEquals(
                Arrays.asList("mov eax, 1", "mov ecx, 2", "cdq", "idiv ecx", "mov eax, edx"),
                ERAlloc.codegen(new RemExpr(new Numeric(1), new Numeric(2))));
    }

    @Test
    public void testCodegenShl() {
        assertEquals(
                Arrays.asList("mov eax, 1", "mov ecx, 2", "shl eax, cl"),
                ERAlloc.codegen(new ShlExpr(new Numeric(1), new Numeric(2))));
    }

    @Test
    public void testCodegenSra() {
        assertEquals(
                Arrays.asList("mov eax, 1", "mov ecx, 2", "sar eax, cl"),
                ERAlloc.codegen(new SraExpr(new Numeric(1), new Numeric(2))));
    }

    @Test
    public void testCodegenSrl() {
        assertEquals(
                Arrays.asList("mov eax, 1", "mov ecx, 2", "shr eax, cl"),
                ERAlloc.codegen(new SrlExpr(new Numeric(1), new Numeric(2))));
    }

    @Test
    public void testCodegenCall() {
        assertEquals(
                Arrays.asList("mov eax, 2", "push eax", "extern f", "mov eax, f", "call eax", "add esp, 4"),
                ERAlloc.codegen(new CallExpr(new GlobalValue("f"), new Numeric(2))));
    }

    @Test
    public void testSpilling() {
        assertEquals(
                Arrays.asList(
                    "extern v4",
                    "mov eax, v4",
                    "mov eax, [eax]",
                    "mov ecx, 22",
                    "sub eax, ecx",
                    "extern v8",
                    "mov ecx, v8",
                    "mov ecx, [ecx]",
                    "mov edx, 11",
                    "sub ecx, edx",
                    "sub eax, ecx",
                    "extern v12",
                    "mov ecx, v12",
                    "mov ecx, [ecx]",
                    "mov edx, 3",
                    "sub ecx, edx",
                    "mov [ebp-4], eax",
                    "extern v16",
                    "mov edx, v16",
                    "mov edx, [edx]",
                    "mov eax, 9",
                    "sub edx, eax",
                    "mov eax, [ebp-4]",
                    "sub ecx, edx",
                    "sub eax, ecx"),
                ERAlloc.codegen(new SubExpr(
                    new SubExpr(
                        new SubExpr(new LoadExpr(new GlobalValue("v4")), new Numeric(22)),
                        new SubExpr(new LoadExpr(new GlobalValue("v8")), new Numeric(11))),
                    new SubExpr(
                        new SubExpr(new LoadExpr(new GlobalValue("v12")), new Numeric(3)),
                        new SubExpr(new LoadExpr(new GlobalValue("v16")), new Numeric(9))))));
    }
}
