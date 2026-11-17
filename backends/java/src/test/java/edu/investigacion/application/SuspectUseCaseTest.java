package edu.investigacion.application;

import edu.investigacion.domain.AppError;
import edu.investigacion.domain.Enums.CaseStatus;
import edu.investigacion.domain.Enums.EvidenceType;
import edu.investigacion.domain.Enums.Role;
import edu.investigacion.domain.Enums.State;
import edu.investigacion.domain.Models.Case;
import edu.investigacion.domain.Models.Evidence;
import edu.investigacion.domain.Models.Suspect;
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

class SuspectUseCaseTest {

    private FakeCaseRepository caseRepo;
    private FakeSuspectRepository suspectRepo;
    private FakeEvidenceRepository evidenceRepo;
    private SuspectUseCase suspectUseCase;

    private final UUID user1Id = UUID.randomUUID();
    private final User user1 = new User(user1Id, "User 1", "u1@test.com", "hash", Role.USER, State.ACTIVE, Instant.now(), Instant.now());
    private UUID openCaseId;
    private UUID closedCaseId;

    @BeforeEach
    void setUp() {
        evidenceRepo = new FakeEvidenceRepository();
        suspectRepo = new FakeSuspectRepository();
        suspectRepo.evidenceRepo = evidenceRepo;
        caseRepo = new FakeCaseRepository(suspectRepo, evidenceRepo);
        suspectUseCase = new SuspectUseCase(suspectRepo, caseRepo);

        openCaseId = UUID.randomUUID();
        caseRepo.create(new Case(openCaseId, 1, "Caso Abierto", "Desc", "Loc", Instant.now(), CaseStatus.OPEN, Optional.empty(), State.ACTIVE, user1Id, Instant.now(), Instant.now()));

        closedCaseId = UUID.randomUUID();
        caseRepo.create(new Case(closedCaseId, 2, "Caso Cerrado", "Desc", "Loc", Instant.now(), CaseStatus.CLOSED, Optional.empty(), State.ACTIVE, user1Id, Instant.now(), Instant.now()));
    }

    @Test
    @DisplayName("Crear sospechoso en caso abierto es exitoso")
    void createSuspectSuccess() {
        var res = suspectUseCase.create(user1, openCaseId, "Juan Perez", Optional.of("El Zorro"), Optional.empty(), 50, Optional.empty(), Optional.empty());
        assertThat(res.isSuccess()).isTrue();
        Suspect s = ((Result.Success<Suspect>) res).value();
        assertThat(s.fullName()).isEqualTo("Juan Perez");
        assertThat(s.caseId()).isEqualTo(openCaseId);
    }

    @Test
    @DisplayName("Crear sospechoso en caso CLOSED devuelve conflicto 409")
    void createSuspectInClosedCaseConflict() {
        var res = suspectUseCase.create(user1, closedCaseId, "Juan Perez", Optional.empty(), Optional.empty(), 50, Optional.empty(), Optional.empty());
        assertThat(res.isFailure()).isTrue();
        var err = ((Result.Failure<Suspect>) res).error();
        assertThat(err).isInstanceOf(AppError.Conflict.class);
    }

    @Test
    @DisplayName("Crear sospechoso en caso inexistente o ajeno devuelve 404")
    void createSuspectInForeignCaseNotFound() {
        var res = suspectUseCase.create(user1, UUID.randomUUID(), "Juan Perez", Optional.empty(), Optional.empty(), 50, Optional.empty(), Optional.empty());
        assertThat(res.isFailure()).isTrue();
        var err = ((Result.Failure<Suspect>) res).error();
        assertThat(err).isInstanceOf(AppError.NotFound.class);
    }

    @Test
    @DisplayName("Eliminar sospechoso desvincula suspectId de sus evidencias")
    void deleteSuspectUnlinksEvidences() {
        var sRes = suspectUseCase.create(user1, openCaseId, "Sospechoso Con Evidencia", Optional.empty(), Optional.empty(), 70, Optional.empty(), Optional.empty());
        UUID sid = ((Result.Success<Suspect>) sRes).value().id();

        UUID eid = UUID.randomUUID();
        evidenceRepo.create(new Evidence(eid, openCaseId, Optional.of(sid), "Arma", "Pistola", EvidenceType.PHYSICAL, Instant.now(), 5, Optional.empty(), State.ACTIVE, Instant.now(), Instant.now()));

        var del = suspectUseCase.delete(user1, sid);
        assertThat(del.isSuccess()).isTrue();

        Evidence ev = evidenceRepo.findById(eid).orElseThrow();
        assertThat(ev.suspectId()).isEmpty();
    }
}
