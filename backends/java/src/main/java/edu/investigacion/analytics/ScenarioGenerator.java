package edu.investigacion.analytics;

import edu.investigacion.analytics.Models.CaseRec;
import edu.investigacion.analytics.Models.CaseStatus;
import edu.investigacion.analytics.Models.EvidenceRec;
import edu.investigacion.analytics.Models.EvidenceType;
import edu.investigacion.analytics.Models.SuspectRec;
import edu.investigacion.analytics.Models.SuspectStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ScenarioGenerator {
    public static final int BASE_DAY = 19358; // LocalDate.of(2023, 1, 1).toEpochDay()

    public record ScenarioCounts(int cases, int suspects, int evidences) {}

    public static final Map<String, ScenarioCounts> SCENARIOS = Map.of(
        "T", new ScenarioCounts(20, 60, 200),
        "S", new ScenarioCounts(1000, 3000, 10000),
        "M", new ScenarioCounts(10000, 30000, 100000),
        "L", new ScenarioCounts(50000, 150000, 500000),
        "XL", new ScenarioCounts(100000, 300000, 1000000)
    );

    public record GeneratedData(
        List<CaseRec> cases,
        List<SuspectRec> suspects,
        List<EvidenceRec> evidences
    ) {}

    private ScenarioGenerator() {}

    public static GeneratedData generate(String scenario, long seed) {
        ScenarioCounts counts = SCENARIOS.get(scenario);
        if (counts == null) {
            throw new IllegalArgumentException("Escenario desconocido: " + scenario);
        }

        Rng rng = new Rng(seed);
        int c = counts.cases();
        int s = counts.suspects();
        int e = counts.evidences();

        CaseStatus[] caseStatuses = CaseStatus.values();
        List<CaseRec> cases = new ArrayList<>(c);
        for (int i = 0; i < c; i++) {
            CaseStatus st = caseStatuses[rng.nextInt(3)];
            int day = BASE_DAY + rng.nextInt(1096);
            String id = String.format("C%07d", i);
            cases.add(new CaseRec(id, st, day));
        }

        SuspectStatus[] suspectStatuses = SuspectStatus.values();
        List<SuspectRec> suspects = new ArrayList<>(s);
        for (int j = 0; j < s; j++) {
            int ci = rng.nextInt(c);
            int sus = rng.nextInt(101);
            SuspectStatus st = suspectStatuses[rng.nextInt(3)];
            String id = String.format("S%07d", j);
            String caseId = String.format("C%07d", ci);
            suspects.add(new SuspectRec(id, caseId, sus, st));
        }

        EvidenceType[] evidenceTypes = EvidenceType.values();
        List<EvidenceRec> evidences = new ArrayList<>(e);
        for (int k = 0; k < e; k++) {
            int ci = rng.nextInt(c);
            int h = rng.nextInt(100);
            int si = rng.nextInt(s);
            EvidenceType t = evidenceTypes[rng.nextInt(5)];
            int rel = 1 + rng.nextInt(5);
            int day = BASE_DAY + rng.nextInt(1096);
            int a = rng.nextInt(100);

            String caseId = String.format("C%07d", ci);
            Optional<String> suspectId = (h < 60)
                ? Optional.of(String.format("S%07d", si))
                : Optional.empty();
            boolean active = (a < 95);

            evidences.add(new EvidenceRec(caseId, suspectId, t, rel, day, active));
        }

        return new GeneratedData(
            List.copyOf(cases),
            List.copyOf(suspects),
            List.copyOf(evidences)
        );
    }
}
