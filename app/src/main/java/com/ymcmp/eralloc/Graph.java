package com.ymcmp.eralloc;

import java.util.*;

public final class Graph<E> implements Iterable<E> {

    private final Map<E, Set<E>> data;

    public Graph() {
        this.data = new HashMap<>();
    }

    public Graph(Graph<E> g) {
        this.data = new HashMap<>(g.data);

        // Also need to deep copy the edges!
        for (final Map.Entry<E, Set<E>> entry : this.data.entrySet())
            entry.setValue(new HashSet<>(entry.getValue()));
    }

    /**
     * For every directed edge, change the direction:
     *
     * A -> B -> C becomes A <- B <- C
     *
     * For bidirectional edges, nothing happens.
     * (and no new edges are created)
     *
     * @return a new (deep copied) graph with edges reversed
     */
    public Graph<E> reverse() {
        final Graph<E> g = new Graph<>();
        for (final Map.Entry<E, Set<E>> edges : this.data.entrySet()) {
            final E nodeA = edges.getKey();
            for (final E nodeB : edges.getValue())
                g.addDirectedEdge(nodeB, nodeA);
        }
        return g;
    }

    public void addNode(E node) {
        this.data.computeIfAbsent(node, k -> new HashSet<>());
    }

    public void addDirectedEdge(E from, E to) {
        // make sure the nodes exist
        this.addNode(from);
        this.addNode(to);

        // connect from -> to
        this.data.get(from).add(to);
    }

    public void removeDirectedEdge(E from, E to) {
        final Set<E> s1 = this.data.get(from);
        final Set<E> s2 = this.data.get(to);
        if (s1 == null || s2 == null)
            return;

        s1.remove(to);
    }

    public void addEdge(E n1, E n2) {
        // make sure the nodes exist
        this.addNode(n1);
        this.addNode(n2);

        // connect them by refering to each other
        this.data.get(n1).add(n2);
        this.data.get(n2).add(n1);
    }

    public void removeEdge(E n1, E n2) {
        final Set<E> s1 = this.data.get(n1);
        final Set<E> s2 = this.data.get(n2);
        if (s1 == null || s2 == null)
            return;

        s1.remove(n2);
        s2.remove(n1);
    }

    public Set<E> getNeighbours(E node) {
        return Collections.unmodifiableSet(this.data.getOrDefault(node, Collections.emptySet()));
    }

    public int getDegree(E node) {
        return this.data.getOrDefault(node, Collections.emptySet()).size();
    }

    public int size() {
        return this.data.size();
    }

    public boolean isEmpty() {
        return this.data.isEmpty();
    }

    public void clear() {
        this.data.clear();
    }

    public Set<E> getNodes() {
        return Collections.unmodifiableSet(this.data.keySet());
    }

    @Override
    public Iterator<E> iterator() {
        return this.getNodes().iterator();
    }

    @Override
    public String toString() {
        return this.data.toString();
    }
}
