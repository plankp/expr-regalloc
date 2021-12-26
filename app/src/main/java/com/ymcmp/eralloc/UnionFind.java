package com.ymcmp.eralloc;

import java.util.*;

public final class UnionFind<E> {

    private final Map<E, E> data = new HashMap<>();

    public E root(E e) {
        if (!this.data.containsKey(e))
            return null;

        for (;;) {
            final E parent = this.data.get(e);
            if (Objects.equals(e, parent))
                return parent;

            // Path compression
            this.data.put(e, this.data.get(parent));
            e = parent;
        }
    }

    public boolean find(E e1, E e2) {
        if (!this.data.containsKey(e1) || !this.data.containsKey(e2))
            return false;

        return Objects.equals(root(e1), root(e2));
    }

    public void union(E e1, E e2) {
        // make sure the two entries exist
        this.data.putIfAbsent(e1, e1);
        this.data.putIfAbsent(e2, e2);

        e1 = this.root(e1);
        e2 = this.root(e2);
        this.data.put(e1, e2);
    }
}
