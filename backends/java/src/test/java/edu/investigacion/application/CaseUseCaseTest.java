package edu.investigacion.application;

import edu.investigacion.domain.AppError;
import edu.investigacion.domain.Enums.CaseStatus;
import edu.investigacion.domain.Enums.Role;
import edu.investigacion.domain.Enums.State;
import edu.investigacion.domain.Models.Case;
import edu.investigacion.domain.Models.Page;
import edu.investigacion.domain.Models.User;
import edu.investigacion.domain.Result;
import edu.investigacion.fakes.FakeRepositories.FakeCaseRepository;
import edu.investigacion.fakes.FakeRepositories.FakeEvidenceRepository;
import edu.investigacion.fakes.FakeRepositories.FakeSuspectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CaseUseCaseTest {

    private FakeCaseRepository caseRepo;
    private CaseUseCase caseUseCase;

    private final UUID user1Id = UUID.randomUUID();
    private final UUID user2Id = UUID.randomUUID();
    private final User user1 = new User(user1Id, "User 1", "u1@test.com", "hash", Role.USER, State.ACTIVE, Instant.now(), Instant.now());
    private final User user2 = new User(user2Id, "User 2", "u2@test.com", "hash", Role.USER, State.ACTIVE, Instant.now(), Instant.now());
    private final User admin = new User(UUID.randomUUID(), "Admin", "admin@test.com", "hash", Role.ADMIN, State.ACTIVE, Instant.now(), Instant.now());

    @BeforeEach
    void setUp() {
        FakeEvidenceRepository evidenceRepo = new FakeEvidenceRepository();
        FakeSuspectRepository suspectRepo = new FakeSuspectRepository();
        suspectRepo.evidenceRepo = evidenceRepo;
        caseRepo = new FakeCaseRepository(suspectRepo, evidenceRepo);
        caseUseCase = new CaseUseCase(caseRepo);
    }

    @Test
    @DisplayName("Crear caso asigna propietario y estado activo")
    void createCaseSuccess() {
        var res = caseUseCase.create(user1, "Caso Homicidio", "Investigacion preliminar en curso", "Centro", "2026-05-10T10:00:00.000Z", Optional.empty(), Optional.empty());
        assertThat(res.isSuccess()).isTrue();
        Case c = ((Result.Success<Case>) res).value();
        assertThat(c.ownerId()).isEqualTo(user1Id);
        assertThat(c.status()).isEqualTo(CaseStatus.OPEN);
        assertThat(c.state()).isEqualTo(State.ACTIVE);
    }

    @Test
    @DisplayName("USER solo lista sus propios casos")
    void userOnlyListsOwnCases() {
        caseUseCase.create(user1, "Caso User 1", "Descripcion larga adecuada", "Norte", "2026-05-10T10:00:00.000Z", Optional.empty(), Optional.empty());
        caseUseCase.create(user2, "Caso User 2", "Descripcion larga adecuada", "Sur", "2026-05-10T10:00:00.000Z", Optional.empty(), Optional.empty());

        var res = caseUseCase.list(user1, 1, 10, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        Page<Case> p = ((Result.Success<Page<Case>>) res).value();
        assertThat(p.items()).hasSize(1);
        assertThat(p.items().get(0).ownerId()).isEqualTo(user1Id);
    }

    @Test
    @DisplayName("ADMIN puede listar casos de todos los usuarios")
    void adminListsAllCases() {
        caseUseCase.create(user1, "Caso User 1", "Descripcion larga adecuada", "Norte", "2026-05-10T10:00:00.000Z", Optional.empty(), Optional.empty());
        caseUseCase.create(user2, "Caso User 2", "Descripcion larga adecuada", "Sur", "2026-05-10T10:00:00.000Z", Optional.empty(), Optional.empty());

        var res = caseUseCase.list(admin, 1, 10, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        Page<Case> p = ((Result.Success<Page<Case>>) res).value();
        assertThat(p.items()).hasSize(2);
    }

    @Test
    @DisplayName("USER intentando acceder a caso ajeno recibe 404 NOT_FOUND")
    void userAccessingForeignCaseReturns404() {
        var created = caseUseCase.create(user2, "Caso Privado", "Descripcion larga adecuada", "Sur", "2026-05-10T10:00:00.000Z", Optional.empty(), Optional.empty());
        UUID cid = ((Result.Success<Case>) created).value().id();

        var res = caseUseCase.getById(user1, cid);
        assertThat(res.isFailure()).isTrue();
        var err = ((Result.Failure<Case>) res).error();
        assertThat(err).isInstanceOf(AppError.NotFound.class);
    }

    @Test
    @DisplayName("Actualizar caso modifica datos y preserva caseNumber y ownerId")
    void updateCaseSuccess() {
        var created = caseUseCase.create(user1, "Caso Original", "Descripcion larga adecuada", "Norte", "2026-05-10T10:00:00.000Z", Optional.empty(), Optional.empty());
        Case orig = ((Result.Success<Case>) created).value();

        var upd = caseUseCase.update(user1, orig.id(), "Caso Modificado", "Descripcion larga adecuada 2", "Occidente", "2026-05-11T10:00:00.000Z", Optional.of(CaseStatus.IN_PROGRESS), Optional.empty());
        assertThat(upd.isSuccess()).isTrue();
        Case updated = ((Result.Success<Case>) upd).value();
        assertThat(updated.title()).isEqualTo("Caso Modificado");
        assertThat(updated.caseNumber()).isEqualTo(orig.caseNumber());
        assertThat(updated.ownerId()).isEqualTo(user1Id);
    }

    @Test
    @DisplayName("Eliminar caso realiza borrado lógico")
    void deleteCaseSuccess() {
        var created = caseUseCase.create(user1, "Caso Para Borrar", "Descripcion larga adecuada", "Norte", "2026-05-10T10:00:00.000Z", Optional.empty(), Optional.empty());
        UUID cid = ((Result.Success<Case>) created).value().id();

        var del = caseUseCase.delete(user1, cid);
        assertThat(del.isSuccess()).isTrue();

        var get = caseUseCase.getById(user1, cid);
        assertThat(get.isFailure()).isTrue();
    }
}
