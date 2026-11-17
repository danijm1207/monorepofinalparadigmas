package edu.investigacion.analytics

class Rng(seed: Long) {
    private var state: Long = seed and 0xFFFFFFFFL

    fun next(): Long {
        state = (state * 1664525L + 1013904223L) and 0xFFFFFFFFL
        return state
    }

    fun nextInt(n: Int): Int {
        return ((next() ushr 8) % n.toLong()).toInt()
    }
}
