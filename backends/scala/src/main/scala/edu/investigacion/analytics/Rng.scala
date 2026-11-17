package edu.investigacion.analytics

/**
 * Generador congruencial lineal de 32 bits idéntico a la especificación de referencia.
 * Encapsula el estado interno usando aritmética sin signo de 64 bits para prevenir desbordes.
 */
final class Rng(seed: Long):
  private var state: Long = seed & 0xFFFFFFFFL

  def next(): Long =
    state = (state * 1664525L + 1013904223L) & 0xFFFFFFFFL
    state

  def nextInt(n: Int): Int =
    ((next() >>> 8) % n.toLong).toInt
