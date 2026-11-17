package edu.investigacion.analytics

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AnalyticsPipelineSpec extends AnyFlatSpec with Matchers:

  val ExpectedTChecksum = "bf5c6284888610a729ceeebf66b32d45854018ff61f0cf1867f67fba67d2a91e"
  val ExpectedSChecksum = "a47b9117ce5d4a2ebf2b53a9d315435efcd58da1b54027647e7c83ea2c1941a9"
  val ExpectedMChecksum = "0d2258e413ea3166ae104f79c4f6ec51ef6bd1595a8c0ed27bcdc97aa4ff58c4"

  "AnalyticsPipeline" should "reproduce exact scenario T canonical lines and checksum" in {
    val (cases, suspects, evidences) = ScenarioGenerator.generate("T", 42L)
    val report = AnalyticsPipeline.run(cases, suspects, evidences)
    val lines = ReportFormatter.formatReport(report)
    val checksum = ReportFormatter.computeChecksum(lines)

    lines.head shouldBe "totals|valid=159|score=5701|maxScore=75|casesWithEvidence=20"
    lines(1) shouldBe "type|PHYSICAL|count=29|score=860"
    lines(2) shouldBe "type|DIGITAL|count=22|score=1020"
    lines(3) shouldBe "type|DOCUMENT|count=31|score=824"
    lines(4) shouldBe "type|TESTIMONY|count=36|score=732"
    lines(5) shouldBe "type|FORENSIC|count=41|score=2265"

    checksum shouldBe ExpectedTChecksum
  }

  it should "reproduce exact scenario S checksum" in {
    val (cases, suspects, evidences) = ScenarioGenerator.generate("S", 42L)
    val report = AnalyticsPipeline.run(cases, suspects, evidences)
    val lines = ReportFormatter.formatReport(report)
    val checksum = ReportFormatter.computeChecksum(lines)

    checksum shouldBe ExpectedSChecksum
  }

  it should "reproduce exact scenario M checksum" in {
    val (cases, suspects, evidences) = ScenarioGenerator.generate("M", 42L)
    val report = AnalyticsPipeline.run(cases, suspects, evidences)
    val lines = ReportFormatter.formatReport(report)
    val checksum = ReportFormatter.computeChecksum(lines)

    checksum shouldBe ExpectedMChecksum
  }

  it should "filter out inactive or low relevance evidences" in {
    val dummyCases = List(CaseRec("C0000001", CaseStatus.OPEN, 20000L))
    val dummySuspects = Nil
    val dummyEvidences = List(
      EvidenceRec("C0000001", None, EvidenceType.PHYSICAL, 1L, 20000L, true),  // rel < 2
      EvidenceRec("C0000001", None, EvidenceType.PHYSICAL, 3L, 20000L, false), // inactive
      EvidenceRec("C0000001", None, EvidenceType.PHYSICAL, 4L, 20000L, true)   // valid
    )

    val report = AnalyticsPipeline.run(dummyCases, dummySuspects, dummyEvidences)
    report.totals.valid shouldBe 1L
    report.totals.score shouldBe (4L * 10L)
    report.totals.maxScore shouldBe (4L * 10L)
    report.totals.casesWithEvidence shouldBe 1L
  }
