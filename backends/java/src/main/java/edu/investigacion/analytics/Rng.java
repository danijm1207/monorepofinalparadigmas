package edu.investigacion.analytics;

public final class Rng {
    private long s;

    public Rng(long seed) {
        this.s = seed & 0xFFFFFFFFL;
    }

    public long next() {
        this.s = (this.s * 1664525L + 1013904223L) & 0xFFFFFFFFL;
        return this.s;
    }

    public int nextInt(int n) {
        return (int) ((next() >>> 8) % (long) n);
    }
}
