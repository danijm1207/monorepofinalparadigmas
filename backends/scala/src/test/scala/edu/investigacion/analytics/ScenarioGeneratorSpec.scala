package edu.investigacion.analytics

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ScenarioGeneratorSpec extends AnyFlatSpec with Matchers:

  "ScenarioGenerator" should "generate exact counts for scenario T" in {
    val (cases, suspects, evidences) = ScenarioGenerator.generate("T", 42L)
    cases.size shouldBe 20
    suspects.size shouldBe 60
    evidences.size shouldBe 200
  }

  it should "generate exact counts for scenario S" in {
    val (cases, suspects, evidences) = ScenarioGenerator.generate("S", 42L)
    cases.size shouldBe 1000
    suspects.size shouldBe 3000
    evidences.size shouldBe 10000
  }

  it should "generate deterministic ids and properties for first records in T" in {
    val (cases, suspects, evidences) = ScenarioGenerator.generate("T", 42L)
    cases.head shouldBe CaseRec("C0000000", CaseStatus.IN_PROGRESS, 20442L)
    suspects.head shouldBe SuspectRec("S0000000", "C0000006", 92L, SuspectStatus.PERSON_OF_INTEREST)
    evidences.head shouldBe EvidenceRec("C0000010", None, EvidenceType.FORENSIC, 3L, 19509L, true)
  }

  it should "throw IllegalArgumentException for unknown scenario" in {
    an[IllegalArgumentException] should be thrownBy {
      ScenarioGenerator.generate("UNKNOWN", 42L)
    }
  }
