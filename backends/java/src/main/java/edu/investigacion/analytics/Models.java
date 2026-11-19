package edu.investigacion.analytics;

import java.util.List;
import java.util.Optional;

public final class Models {
    private Models() {}

    public enum CaseStatus {
        OPEN,
        IN_PROGRESS,
        CLOSED
    }

    public enum SuspectStatus {
        PERSON_OF_INTEREST,
        DETAINED,
        CLEARED
    }

    public enum EvidenceType {
        PHYSICAL(10),
        DIGITAL(12),
        DOCUMENT(8),
        TESTIMONY(6),
        FORENSIC(15);

        private final int weight;

        EvidenceType(int weight) {
            this.weight = weight;
        }

        public int weight() {
            return weight;
        }
    }

    public record CaseRec(String id, CaseStatus status, int incidentEpochDay) {}

    public record SuspectRec(String id, String caseId, int suspicionLevel, SuspectStatus status) {}

    public record EvidenceRec(
        String caseId,
        Optional<String> suspectId,
        EvidenceType type,
        int relevance,
        int collectedEpochDay,
        boolean active
    ) {}

    public record CaseScoreSummary(long count, long totalScore) {
        public static final CaseScoreSummary EMPTY = new CaseScoreSummary(0L, 0L);
    }

    public record SuspectScoreSummary(long count, long totalScore) {
        public static final SuspectScoreSummary EMPTY = new SuspectScoreSummary(0L, 0L);
    }

    public record RankedSuspect(
        String sid,
        long count,
        long score,
        int suspicion,
        long priority
    ) {}

    public record AnalyticsReport(List<String> lines, String checksum) {}
}
