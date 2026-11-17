package edu.investigacion.application

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import edu.investigacion.analytics.*
import edu.investigacion.doubles.InMemoryAnalyticsRepository

class BenchmarkServiceSpec extends AnyFlatSpec with Matchers:

  "BenchmarkService.runBenchmark" should "generate and process scenario T with exact checksum" in {
    val repo = InMemoryAnalyticsRepository()
    val service = BenchmarkService(repo)

    val res = service.runBenchmark(scenario = "T", seed = 42L, includeReport = true)
    res.scenario shouldBe "T"
    res.seed shouldBe 42L
    res.checksum shouldBe "bf5c6284888610a729ceeebf66b32d45854018ff61f0cf1867f67fba67d2a91e"
    res.report.isDefined shouldBe true
    res.processedRecords should be > 0L
    res.timings.generationNanos should be >= 0L
    res.timings.pipelineNanos should be >= 0L
  }

  it should "omit report when includeReport is false" in {
    val repo = InMemoryAnalyticsRepository()
    val service = BenchmarkService(repo)

    val res = service.runBenchmark(scenario = "T", seed = 42L, includeReport = false)
    res.report.isDefined shouldBe false
  }

  "BenchmarkService.runOverview" should "process records from analytics repository" in {
    val repo = InMemoryAnalyticsRepository()
    val (c, s, e) = ScenarioGenerator.generate("T", 42L)
    repo.cases = c.toList
    repo.suspects = s.toList
    repo.evidences = e.toList

    val service = BenchmarkService(repo)
    val res = service.runOverview()
    res.checksum shouldBe "bf5c6284888610a729ceeebf66b32d45854018ff61f0cf1867f67fba67d2a91e"
    res.processedRecords shouldBe (c.size + s.size + e.size).toLong
  }
