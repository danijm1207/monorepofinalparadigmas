package edu.investigacion.analytics

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RngSpec extends AnyFlatSpec with Matchers:

  "Rng" should "produce the exact first 5 32-bit integers from seed 42" in {
    val rng = new Rng(42L)
    val expected = List(1083814273L, 378494188L, 2479403867L, 955863294L, 1613448261L)
    val actual = (1 to 5).map(_ => rng.next()).toList
    actual shouldBe expected
  }

  it should "produce the exact first 5 nextInt(10) numbers from seed 42" in {
    val rng = new Rng(42L)
    val expected = List(9, 2, 1, 0, 2)
    val actual = (1 to 5).map(_ => rng.nextInt(10)).toList
    actual shouldBe expected
  }

  it should "handle 0xFFFFFFFF seed masking properly" in {
    val rng1 = new Rng(42L)
    val rng2 = new Rng(42L + 0x100000000L) // same lower 32-bits
    rng1.next() shouldBe rng2.next()
  }
