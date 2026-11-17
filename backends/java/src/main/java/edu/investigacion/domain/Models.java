package edu.investigacion.domain;

import edu.investigacion.domain.Enums.CaseStatus;
import edu.investigacion.domain.Enums.EvidenceType;
import edu.investigacion.domain.Enums.Role;
import edu.investigacion.domain.Enums.State;
import edu.investigacion.domain.Enums.SuspectStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class Models {
    private Models() {}

    public record User(
        UUID id,
        String name,
        String email,
        String passwordHash,
        Role role,
        State state,
        Instant createdAt,
        Instant updatedAt
    ) {}

    public record Case(
        UUID id,
        int caseNumber,
        String title,
        String description,
        String location,
        Instant incidentDate,
        CaseStatus status,
        Optional<String> imageUrl,
        State state,
        UUID ownerId,
        Instant createdAt,
        Instant updatedAt
    ) {}

    public record Suspect(
        UUID id,
        UUID caseId,
        String fullName,
        Optional<String> alias,
        Optional<String> description,
        int suspicionLevel,
        SuspectStatus status,
        Optional<String> photoUrl,
        State state,
        Instant createdAt,
        Instant updatedAt
    ) {}

    public record Evidence(
        UUID id,
        UUID caseId,
        Optional<UUID> suspectId,
        String title,
        String description,
        EvidenceType type,
        Instant collectedAt,
        int relevance,
        Optional<String> imageUrl,
        State state,
        Instant createdAt,
        Instant updatedAt
    ) {}

    public record Page<T>(
        java.util.List<T> items,
        long total,
        int currentPage,
        int limit,
        int totalPages
    ) {}
}
