package edu.investigacion.application;

import edu.investigacion.domain.AppError;
import edu.investigacion.domain.Enums.CaseStatus;
import edu.investigacion.domain.Enums.EvidenceType;
import edu.investigacion.domain.Enums.Role;
import edu.investigacion.domain.Enums.State;
import edu.investigacion.domain.Models.Case;
import edu.investigacion.domain.Models.Evidence;
import edu.investigacion.domain.Models.Page;
import edu.investigacion.domain.Models.Suspect;
import edu.investigacion.domain.Models.User;
import edu.investigacion.domain.Repositories.CaseRepository;
import edu.investigacion.domain.Repositories.EvidenceRepository;
import edu.investigacion.domain.Repositories.SuspectRepository;
import edu.investigacion.domain.Result;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class EvidenceUseCase {
    private final EvidenceRepository evidenceRepository;
    private final CaseRepository caseRepository;
    private final SuspectRepository suspectRepository;

    public EvidenceUseCase(
        EvidenceRepository evidenceRepository,
        CaseRepository caseRepository,
        SuspectRepository suspectRepository
    ) {
        this.evidenceRepository = evidenceRepository;
        this.caseRepository = caseRepository;
        this.suspectRepository = suspectRepository;
    }

    public Result<Page<Evidence>> list(
        User currentUser,
        int page,
        int limit,
        Optional<UUID> caseId,
        Optional<UUID> suspectId,
        Optional<EvidenceType> type,
        Optional<String> q
    ) {
        var pageRes = Validators.validatePagination(page, limit);
        if (pageRes.isFailure()) {
            return Result.fail(((Result.Failure<Void>) pageRes).error());
        }

        Optional<UUID> ownerFilter = (currentUser.role() == Role.USER)
            ? Optional.of(currentUser.id())
            : Optional.empty();

        List<Evidence> evidences = evidenceRepository.findAll(page, limit, caseId, suspectId, type, q, ownerFilter);
        long total = evidenceRepository.countAll(caseId, suspectId, type, q, ownerFilter);
        int totalPages = total == 0 ? 1 : (int) Math.ceil((double) total / limit);

        return Result.ok(new Page<>(evidences, total, page, limit, totalPages));
    }

    public Result<Evidence> getById(User currentUser, UUID id) {
        Optional<Evidence> evidenceOpt = evidenceRepository.findById(id);
        if (evidenceOpt.isEmpty() || evidenceOpt.get().state() != State.ACTIVE) {
            return Result.fail(new AppError.NotFound("Evidencia no encontrada"));
        }

        Evidence evidence = evidenceOpt.get();
        Optional<Case> caseOpt = caseRepository.findById(evidence.caseId());
        if (caseOpt.isEmpty() || !AccessPolicy.canAccessCase(currentUser, caseOpt.get().ownerId())) {
            return Result.fail(new AppError.NotFound("Evidencia no encontrada"));
        }

        return Result.ok(evidence);
    }

    public Result<Evidence> create(
        User currentUser,
        UUID caseId,
        Optional<UUID> suspectId,
        String title,
        String description,
        String typeStr,
        String collectedAtStr,
        Integer relevance,
        Optional<String> imageUrl
    ) {
        var valRes = Validators.validateCreateEvidence(title, description, typeStr, collectedAtStr, relevance);
        if (valRes.isFailure()) {
            return Result.fail(((Result.Failure<Instant>) valRes).error());
        }

        Optional<Case> caseOpt = caseRepository.findById(caseId);
        if (caseOpt.isEmpty() || caseOpt.get().state() != State.ACTIVE ||
            !AccessPolicy.canAccessCase(currentUser, caseOpt.get().ownerId())) {
            return Result.fail(new AppError.NotFound("Caso no encontrado"));
        }

        Case targetCase = caseOpt.get();
        if (targetCase.status() == CaseStatus.CLOSED) {
            return Result.fail(new AppError.Conflict("No se pueden agregar evidencias a un caso cerrado"));
        }

        if (suspectId.isPresent()) {
            Optional<Suspect> suspectOpt = suspectRepository.findById(suspectId.get());
            if (suspectOpt.isEmpty() || suspectOpt.get().state() != State.ACTIVE ||
                !suspectOpt.get().caseId().equals(caseId)) {
                return Result.fail(new AppError.ValidationError("El sospechoso debe pertenecer al mismo caso"));
            }
        }

        Instant collectedAt = ((Result.Success<Instant>) valRes).value();
        Instant now = Instant.now();
        Evidence newEvidence = new Evidence(
            UUID.randomUUID(),
            caseId,
            suspectId,
            title.trim(),
            description.trim(),
            EvidenceType.valueOf(typeStr),
            collectedAt,
            relevance,
            imageUrl,
            State.ACTIVE,
            now,
            now
        );

        Evidence saved = evidenceRepository.create(newEvidence);
        return Result.ok(saved);
    }

    public Result<Evidence> update(
        User currentUser,
        UUID id,
        Optional<UUID> suspectId,
        String title,
        String description,
        String typeStr,
        String collectedAtStr,
        Integer relevance,
        Optional<String> imageUrl
    ) {
        var existingRes = getById(currentUser, id);
        if (existingRes.isFailure()) {
            return existingRes;
        }

        var valRes = Validators.validateCreateEvidence(title, description, typeStr, collectedAtStr, relevance);
        if (valRes.isFailure()) {
            return Result.fail(((Result.Failure<Instant>) valRes).error());
        }

        Evidence existing = ((Result.Success<Evidence>) existingRes).value();
        if (suspectId.isPresent()) {
            Optional<Suspect> suspectOpt = suspectRepository.findById(suspectId.get());
            if (suspectOpt.isEmpty() || suspectOpt.get().state() != State.ACTIVE ||
                !suspectOpt.get().caseId().equals(existing.caseId())) {
                return Result.fail(new AppError.ValidationError("El sospechoso debe pertenecer al mismo caso"));
            }
        }

        Instant collectedAt = ((Result.Success<Instant>) valRes).value();
        Evidence updated = new Evidence(
            existing.id(),
            existing.caseId(), // caseId cannot be changed
            suspectId,
            title.trim(),
            description.trim(),
            EvidenceType.valueOf(typeStr),
            collectedAt,
            relevance,
            imageUrl.isPresent() ? imageUrl : existing.imageUrl(),
            existing.state(),
            existing.createdAt(),
            Instant.now()
        );

        Evidence saved = evidenceRepository.update(updated);
        return Result.ok(saved);
    }

    public Result<Void> delete(User currentUser, UUID id) {
        var existingRes = getById(currentUser, id);
        if (existingRes.isFailure()) {
            return Result.fail(((Result.Failure<Evidence>) existingRes).error());
        }

        evidenceRepository.deactivate(id);
        return Result.ok(null);
    }
}
