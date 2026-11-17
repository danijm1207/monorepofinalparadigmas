package edu.investigacion.application

import edu.investigacion.analytics.*
import edu.investigacion.domain.AnalyticsRepository
import edu.investigacion.domain.AppError
import edu.investigacion.domain.Result

data class BenchmarkResult(
    val scenario: String,
    val seed: Long,
    val counts: Map<String, Int>,
    val processedRecords: Long,
    val timings: Map<String, Long>,
    val jvm: Map<String, Long>,
    val checksum: String,
    val report: List<String>?
)

data class OverviewResult(
    val processedRecords: Long,
    val timings: Map<String, Long>,
    val checksum: String,
    val report: List<String>
)

class AnalyticsService(
    private val analyticsRepository: AnalyticsRepository
) {
    fun runBenchmark(scenario: String?, seed: Long?, includeReport: Boolean): Result<BenchmarkResult> {
        val validScenario = scenario?.uppercase()
        if (validScenario == null || validScenario !in listOf("T", "S", "M", "L", "XL")) {
            return Result.Failure(AppError.Validation("Escenario inválido", emptyList()))
        }
        val actualSeed = seed ?: 42L
        if (actualSeed < 0L || actualSeed > 4294967295L) {
            return Result.Failure(AppError.Validation("Semilla inválida", emptyList()))
        }

        val genStart = System.nanoTime()
        val (cases, suspects, evidences) = ScenarioGenerator.generate(validScenario, actualSeed)
        val genNanos = System.nanoTime() - genStart

        val pipeStart = System.nanoTime()
        val report = AnalyticsPipeline.run(cases, suspects, evidences)
        val pipeNanos = System.nanoTime() - pipeStart

        val lines = ReportFormatter.formatLines(report)
        val chk = ReportFormatter.checksum(lines)

        val rt = Runtime.getRuntime()
        val heapUsed = rt.totalMemory() - rt.freeMemory()
        val heapMax = rt.maxMemory()

        val benchmarkResult = BenchmarkResult(
            scenario = validScenario,
            seed = actualSeed,
            counts = mapOf("cases" to cases.size, "suspects" to suspects.size, "evidences" to evidences.size),
            processedRecords = (cases.size + suspects.size + evidences.size).toLong(),
            timings = mapOf("generationNanos" to genNanos, "pipelineNanos" to pipeNanos),
            jvm = mapOf("heapUsedBytes" to heapUsed, "heapMaxBytes" to heapMax),
            checksum = chk,
            report = if (includeReport) lines else null
        )

        return Result.Success(benchmarkResult)
    }

    fun getOverview(): Result<OverviewResult> {
        val loadStart = System.nanoTime()
        val cases = analyticsRepository.getActiveCases()
        val suspects = analyticsRepository.getActiveSuspects()
        val evidences = analyticsRepository.getActiveEvidences()
        val loadNanos = System.nanoTime() - loadStart

        val pipeStart = System.nanoTime()
        val report = AnalyticsPipeline.run(cases, suspects, evidences)
        val pipeNanos = System.nanoTime() - pipeStart

        val lines = ReportFormatter.formatLines(report)
        val chk = ReportFormatter.checksum(lines)

        val result = OverviewResult(
            processedRecords = (cases.size + suspects.size + evidences.size).toLong(),
            timings = mapOf("loadNanos" to loadNanos, "pipelineNanos" to pipeNanos),
            checksum = chk,
            report = lines
        )

        return Result.Success(result)
    }
}
