package edu.investigacion.analytics

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ReportFormatterSpec extends AnyFlatSpec with Matchers:

  "ReportFormatter" should "calculate exact SHA-256 in lowercase hex" in {
    val lines = List("hello", "world")
    // sha256("hello\nworld") = 26c60a61d01db5836ca70fefd44a6a016620413c8ef5f259a6c5612d4f79d3b8
    val hash = ReportFormatter.computeChecksum(lines)
    hash shouldBe "26c60a61d01db5836ca70fefd44a6a016620413c8ef5f259a6c5612d4f79d3b8"
  }

  it should "format lines without trailing newlines" in {
    val report = AnalyticsReport(
      totals = Totals(1, 10, 10, 1),
      typeScores = List(TypeScore(EvidenceType.PHYSICAL, 1, 10)),
      months = List(MonthCount(202301L, 1)),
      topCases = List(TopCase(1, "C0000001", 1, 10)),
      topSuspects = List(TopSuspect(1, "S0000001", 1, 10, 50, 60)),
      caseStatuses = List(CaseStatusSummary(CaseStatus.OPEN, 1, 1)),
      suspectStatuses = List(SuspectStatusSummary(SuspectStatus.PERSON_OF_INTEREST, 1, 5000))
    )
    val lines = ReportFormatter.formatReport(report)
    lines.size shouldBe 7
    lines.head should startWith("totals|")
  }
