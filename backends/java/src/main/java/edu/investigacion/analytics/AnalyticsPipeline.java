package edu.investigacion.analytics;

import edu.investigacion.analytics.Models.CaseRec;
import edu.investigacion.analytics.Models.CaseScoreSummary;
import edu.investigacion.analytics.Models.CaseStatus;
import edu.investigacion.analytics.Models.EvidenceRec;
import edu.investigacion.analytics.Models.EvidenceType;
import edu.investigacion.analytics.Models.RankedSuspect;
import edu.investigacion.analytics.Models.SuspectRec;
import edu.investigacion.analytics.Models.SuspectScoreSummary;
import edu.investigacion.analytics.Models.SuspectStatus;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class AnalyticsPipeline {
    private static final int MIN_RELEVANCE = 2;

    private record ScoredEvidence(EvidenceRec evidence, long score) {}

    private AnalyticsPipeline() {}

    public static List<String> run(
        List<CaseRec> cases,
        List<SuspectRec> suspects,
        List<EvidenceRec> evidences
    ) {
        List<EvidenceRec> valid = evidences.stream()
            .filter(EvidenceRec::active)
            .filter(e -> e.relevance() >= MIN_RELEVANCE)
            .toList();

        List<ScoredEvidence> scored = valid.stream()
            .map(e -> new ScoredEvidence(e, (long) e.relevance() * (long) e.type().weight()))
            .toList();

        long totalScore = scored.stream()
            .mapToLong(ScoredEvidence::score)
            .reduce(0L, Long::sum);

        long maxScore = scored.stream()
            .mapToLong(ScoredEvidence::score)
            .reduce(0L, Long::max);

        Map<String, CaseScoreSummary> byCase = scored.stream()
            .collect(Collectors.groupingBy(
                s -> s.evidence().caseId(),
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    list -> new CaseScoreSummary(
                        list.size(),
                        list.stream().mapToLong(ScoredEvidence::score).sum()
                    )
                )
            ));

        List<String> totalsLines = List.of(String.format(
            "totals|valid=%d|score=%d|maxScore=%d|casesWithEvidence=%d",
            valid.size(),
            totalScore,
            maxScore,
            byCase.size()
        ));

        List<String> typeLines = Arrays.stream(EvidenceType.values())
            .map(t -> {
                List<ScoredEvidence> sel = scored.stream()
                    .filter(s -> s.evidence().type() == t)
                    .toList();
                long typeScore = sel.stream()
                    .mapToLong(ScoredEvidence::score)
                    .sum();
                return String.format("type|%s|count=%d|score=%d", t.name(), sel.size(), typeScore);
            })
            .toList();

        Map<Integer, Long> months = scored.stream()
            .collect(Collectors.groupingBy(
                s -> {
                    LocalDate d = LocalDate.ofEpochDay(s.evidence().collectedEpochDay());
                    return d.getYear() * 100 + d.getMonthValue();
                },
                Collectors.counting()
            ));

        List<String> monthLines = months.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> String.format("month|%d|%d", entry.getKey(), entry.getValue()))
            .toList();

        Comparator<Map.Entry<String, CaseScoreSummary>> caseComp = Comparator
            .<Map.Entry<String, CaseScoreSummary>>comparingLong(e -> e.getValue().totalScore())
            .reversed()
            .thenComparing(Map.Entry::getKey);

        List<Map.Entry<String, CaseScoreSummary>> topCases = byCase.entrySet().stream()
            .sorted(caseComp)
            .limit(10)
            .toList();

        List<String> topCaseLines = IntStream.range(0, topCases.size())
            .mapToObj(idx -> {
                Map.Entry<String, CaseScoreSummary> entry = topCases.get(idx);
                return String.format(
                    "topCase|%d|%s|count=%d|score=%d",
                    idx + 1,
                    entry.getKey(),
                    entry.getValue().count(),
                    entry.getValue().totalScore()
                );
            })
            .toList();

        Map<String, SuspectScoreSummary> bySus = scored.stream()
            .filter(s -> s.evidence().suspectId().isPresent())
            .collect(Collectors.groupingBy(
                s -> s.evidence().suspectId().orElse(""),
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    list -> new SuspectScoreSummary(
                        list.size(),
                        list.stream().mapToLong(ScoredEvidence::score).sum()
                    )
                )
            ));

        Map<String, SuspectRec> susMap = suspects.stream()
            .collect(Collectors.toMap(SuspectRec::id, s -> s, (s1, s2) -> s1));

        List<RankedSuspect> ranked = bySus.entrySet().stream()
            .filter(entry -> susMap.containsKey(entry.getKey()))
            .map(entry -> {
                String sid = entry.getKey();
                SuspectRec s = susMap.get(sid);
                long cnt = entry.getValue().count();
                long sc = entry.getValue().totalScore();
                int susp = s.suspicionLevel();
                long pr = sc + (long) susp;
                return new RankedSuspect(sid, cnt, sc, susp, pr);
            })
            .sorted(Comparator.comparingLong(RankedSuspect::priority).reversed().thenComparing(RankedSuspect::sid))
            .limit(10)
            .toList();

        List<String> topSuspectLines = IntStream.range(0, ranked.size())
            .mapToObj(idx -> {
                RankedSuspect r = ranked.get(idx);
                return String.format(
                    "topSuspect|%d|%s|count=%d|score=%d|suspicion=%d|priority=%d",
                    idx + 1,
                    r.sid(),
                    r.count(),
                    r.score(),
                    r.suspicion(),
                    r.priority()
                );
            })
            .toList();

        List<String> caseStatusLines = Arrays.stream(CaseStatus.values())
            .map(st -> {
                List<String> ids = cases.stream()
                    .filter(c -> c.status() == st)
                    .map(CaseRec::id)
                    .toList();
                long evCount = ids.stream()
                    .mapToLong(id -> byCase.getOrDefault(id, CaseScoreSummary.EMPTY).count())
                    .sum();
                return String.format("caseStatus|%s|cases=%d|evidences=%d", st.name(), ids.size(), evCount);
            })
            .toList();

        List<String> suspectStatusLines = Arrays.stream(SuspectStatus.values())
            .map(st -> {
                List<Integer> sel = suspects.stream()
                    .filter(s -> s.status() == st)
                    .map(SuspectRec::suspicionLevel)
                    .toList();
                long count = sel.size();
                long avg = (count > 0L)
                    ? (sel.stream().mapToLong(x -> (long) x).sum() * 100L) / count
                    : 0L;
                return String.format("suspectStatus|%s|count=%d|avgSuspicionX100=%d", st.name(), count, avg);
            })
            .toList();

        return Stream.of(
            totalsLines,
            typeLines,
            monthLines,
            topCaseLines,
            topSuspectLines,
            caseStatusLines,
            suspectStatusLines
        ).flatMap(List::stream).toList();
    }
}
