package edu.investigacion.application;

import edu.investigacion.analytics.AnalyticsPipeline;
import edu.investigacion.analytics.ReportFormatter;
import edu.investigacion.analytics.ScenarioGenerator;
import edu.investigacion.domain.AppError;
import edu.investigacion.domain.Models.User;
import edu.investigacion.domain.Repositories.AnalyticsDbRepository;
import edu.investigacion.domain.Result;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BenchmarkUseCase {
    public record JvmMetrics(long heapUsedBytes, long heapMaxBytes) {}

    public record BenchmarkResult(
        String scenario,
        long seed,
        Map<String, Integer> counts,
        int processedRecords,
        Map<String, Long> timings,
        JvmMetrics jvm,
        String checksum,
        Optional<List<String>> report
    ) {}

    public record OverviewResult(
        int processedRecords,
        Map<String, Long> timings,
        String checksum,
        List<String> report
    ) {}

    private final AnalyticsDbRepository analyticsDbRepository;

    public BenchmarkUseCase(AnalyticsDbRepository analyticsDbRepository) {
        this.analyticsDbRepository = analyticsDbRepository;
    }

    public Result<BenchmarkResult> runBenchmark(
        User currentUser,
        String scenario,
        Long seedParam,
        boolean includeReport
    ) {
        if (!AccessPolicy.isAdmin(currentUser)) {
            return Result.fail(new AppError.Forbidden("Solo administradores pueden ejecutar benchmarks"));
        }

        if (scenario == null || !ScenarioGenerator.SCENARIOS.containsKey(scenario.toUpperCase())) {
            return Result.fail(new AppError.ValidationError("Escenario no permitido: " + scenario));
        }

        long seed = (seedParam != null) ? seedParam : 42L;
        if (seed < 0 || seed > 4294967295L) {
            return Result.fail(new AppError.ValidationError("Semilla fuera de rango: " + seed));
        }

        long t0 = System.nanoTime();
        var data = ScenarioGenerator.generate(scenario.toUpperCase(), seed);
        long t1 = System.nanoTime();

        List<String> lines = AnalyticsPipeline.run(data.cases(), data.suspects(), data.evidences());
        long t2 = System.nanoTime();

        String cs = ReportFormatter.computeChecksum(lines);

        int cCount = data.cases().size();
        int sCount = data.suspects().size();
        int eCount = data.evidences().size();
        int processed = cCount + sCount + eCount;

        Runtime rt = Runtime.getRuntime();
        long heapUsed = rt.totalMemory() - rt.freeMemory();
        long heapMax = rt.maxMemory();

        BenchmarkResult res = new BenchmarkResult(
            scenario.toUpperCase(),
            seed,
            Map.of("cases", cCount, "suspects", sCount, "evidences", eCount),
            processed,
            Map.of("generationNanos", (t1 - t0), "pipelineNanos", (t2 - t1)),
            new JvmMetrics(heapUsed, heapMax),
            cs,
            includeReport ? Optional.of(lines) : Optional.empty()
        );

        return Result.ok(res);
    }

    public Result<OverviewResult> runOverview(User currentUser) {
        if (!AccessPolicy.isAdmin(currentUser)) {
            return Result.fail(new AppError.Forbidden("Solo administradores pueden consultar el resumen analítico"));
        }

        long t0 = System.nanoTime();
        var cases = analyticsDbRepository.findActiveCases();
        var suspects = analyticsDbRepository.findActiveSuspects();
        var evidences = analyticsDbRepository.findActiveEvidences();
        long t1 = System.nanoTime();

        List<String> lines = AnalyticsPipeline.run(cases, suspects, evidences);
        long t2 = System.nanoTime();

        String cs = ReportFormatter.computeChecksum(lines);
        int processed = cases.size() + suspects.size() + evidences.size();

        OverviewResult res = new OverviewResult(
            processed,
            Map.of("loadNanos", (t1 - t0), "pipelineNanos", (t2 - t1)),
            cs,
            lines
        );

        return Result.ok(res);
    }
}
