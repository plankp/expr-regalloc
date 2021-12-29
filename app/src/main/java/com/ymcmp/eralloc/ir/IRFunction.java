package com.ymcmp.eralloc.ir;

import java.util.*;

public final class IRFunction implements Iterable<IRBlock> {

    public final List<IRBlock> blocks = new ArrayList<>();

    private String name = "";

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public String getName() {
        return this.name;
    }

    @Override
    public ListIterator<IRBlock> iterator() {
        return this.blocks.listIterator();
    }

    public ListIterator<IRBlock> iterator(int index) {
        return this.blocks.listIterator(index);
    }

    public int size() {
        return this.blocks.size();
    }

    public boolean isEmpty() {
        return this.blocks.isEmpty();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(this.getName()).append(" {");
        for (final IRBlock block : this.blocks)
            sb.append('\n').append(block);
        sb.append('}');
        return sb.toString();
    }
}
