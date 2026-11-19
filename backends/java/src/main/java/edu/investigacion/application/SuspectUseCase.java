package edu.investigacion.application;

import edu.investigacion.domain.AppError;
import edu.investigacion.domain.Enums.CaseStatus;
import edu.investigacion.domain.Enums.Role;
import edu.investigacion.domain.Enums.State;
import edu.investigacion.domain.Enums.SuspectStatus;
import edu.investigacion.domain.Models.Case;
import edu.investigacion.domain.Models.Page;
import edu.investigacion.domain.Models.Suspect;
import edu.investigacion.domain.Models.User;
import edu.investigacion.domain.Repositories.CaseRepository;
import edu.investigacion.domain.Repositories.SuspectRepository;
import edu.investigacion.domain.Result;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SuspectUseCase {
    private final SuspectRepository suspectRepository;
    private final CaseRepository caseRepository;

    public SuspectUseCase(SuspectRepository suspectRepository, CaseRepository caseRepository) {
        this.suspectRepository = suspectRepository;
        this.caseRepository = caseRepository;
    }

    public Result<Page<Suspect>> list(
        User currentUser,
        int page,
        int limit,
        Optional<UUID> caseId,
        Optional<SuspectStatus> status,
        Optional<String> q
    ) {
        var pageRes = Validators.validatePagination(page, limit);
        if (pageRes.isFailure()) {
            return Result.fail(((Result.Failure<Void>) pageRes).error());
        }

        Optional<UUID> ownerFilter = (currentUser.role() == Role.USER)
            ? Optional.of(currentUser.id())
            : Optional.empty();

        List<Suspect> suspects = suspectRepository.findAll(page, limit, caseId, status, q, ownerFilter);
        long total = suspectRepository.countAll(caseId, status, q, ownerFilter);
        int totalPages = total == 0 ? 1 : (int) Math.ceil((double) total / limit);

        return Result.ok(new Page<>(suspects, total, page, limit, totalPages));
    }

    public Result<Suspect> getById(User currentUser, UUID id) {
        Optional<Suspect> suspectOpt = suspectRepository.findById(id);
        if (suspectOpt.isEmpty() || suspectOpt.get().state() != State.ACTIVE) {
            return Result.fail(new AppError.NotFound("Sospechoso no encontrado"));
        }

        Suspect suspect = suspectOpt.get();
        Optional<Case> caseOpt = caseRepository.findById(suspect.caseId());
        if (caseOpt.isEmpty() || !AccessPolicy.canAccessCase(currentUser, caseOpt.get().ownerId())) {
            return Result.fail(new AppError.NotFound("Sospechoso no encontrado"));
        }

        return Result.ok(suspect);
    }

    public Result<Suspect> create(
        User currentUser,
        UUID caseId,
        String fullName,
        Optional<String> alias,
        Optional<String> description,
        Integer suspicionLevel,
        Optional<SuspectStatus> status,
        Optional<String> photoUrl
    ) {
        var valRes = Validators.validateCreateSuspect(fullName, suspicionLevel);
        if (valRes.isFailure()) {
            return Result.fail(((Result.Failure<Void>) valRes).error());
        }

        Optional<Case> caseOpt = caseRepository.findById(caseId);
        if (caseOpt.isEmpty() || caseOpt.get().state() != State.ACTIVE ||
            !AccessPolicy.canAccessCase(currentUser, caseOpt.get().ownerId())) {
            return Result.fail(new AppError.NotFound("Caso no encontrado"));
        }

        Case targetCase = caseOpt.get();
        if (targetCase.status() == CaseStatus.CLOSED) {
            return Result.fail(new AppError.Conflict("No se pueden agregar sospechosos a un caso cerrado"));
        }

        Instant now = Instant.now();
        Suspect newSuspect = new Suspect(
            UUID.randomUUID(),
            caseId,
            fullName.trim(),
            alias.map(String::trim),
            description.map(String::trim),
            suspicionLevel != null ? suspicionLevel : 0,
            status.orElse(SuspectStatus.PERSON_OF_INTEREST),
            photoUrl,
            State.ACTIVE,
            now,
            now
        );

        Suspect saved = suspectRepository.create(newSuspect);
        return Result.ok(saved);
    }

    public Result<Suspect> update(
        User currentUser,
        UUID id,
        String fullName,
        Optional<String> alias,
        Optional<String> description,
        Integer suspicionLevel,
        Optional<SuspectStatus> status,
        Optional<String> photoUrl
    ) {
        var existingRes = getById(currentUser, id);
        if (existingRes.isFailure()) {
            return existingRes;
        }

        var valRes = Validators.validateCreateSuspect(fullName, suspicionLevel);
        if (valRes.isFailure()) {
            return Result.fail(((Result.Failure<Void>) valRes).error());
        }

        Suspect existing = ((Result.Success<Suspect>) existingRes).value();
        Suspect updated = new Suspect(
            existing.id(),
            existing.caseId(), // caseId cannot be changed
            fullName.trim(),
            alias.map(String::trim),
            description.map(String::trim),
            suspicionLevel != null ? suspicionLevel : existing.suspicionLevel(),
            status.orElse(existing.status()),
            photoUrl.isPresent() ? photoUrl : existing.photoUrl(),
            existing.state(),
            existing.createdAt(),
            Instant.now()
        );

        Suspect saved = suspectRepository.update(updated);
        return Result.ok(saved);
    }

    public Result<Void> delete(User currentUser, UUID id) {
        var existingRes = getById(currentUser, id);
        if (existingRes.isFailure()) {
            return Result.fail(((Result.Failure<Suspect>) existingRes).error());
        }

        suspectRepository.deactivateCascade(id);
        return Result.ok(null);
    }
}
