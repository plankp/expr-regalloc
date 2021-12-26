package com.ymcmp.eralloc;

import java.math.BigInteger;

public final class AtomicCounter {

    private BigInteger counter;

    public AtomicCounter() {
        this(BigInteger.ZERO);
    }

    public AtomicCounter(BigInteger value) {
        if (value == null)
            throw new IllegalArgumentException("Initial counter value cannot be null");
        this.counter = value;
    }

    public synchronized BigInteger get() {
        return this.counter;
    }

    public synchronized BigInteger getAndIncrement() {
        final BigInteger ret = this.counter;
        this.counter = ret.add(BigInteger.ONE);
        return ret;
    }

    public synchronized BigInteger incrementAndGet() {
        this.counter = this.counter.add(BigInteger.ONE);
        return this.counter;
    }
}