package edu.investigacion.application;

import edu.investigacion.domain.AppError;
import edu.investigacion.domain.Enums.CaseStatus;
import edu.investigacion.domain.Enums.EvidenceType;
import edu.investigacion.domain.Enums.Role;
import edu.investigacion.domain.Enums.State;
import edu.investigacion.domain.Enums.SuspectStatus;
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

class EvidenceUseCaseTest {

    private FakeCaseRepository caseRepo;
    private FakeSuspectRepository suspectRepo;
    private FakeEvidenceRepository evidenceRepo;
    private EvidenceUseCase evidenceUseCase;

    private final UUID user1Id = UUID.randomUUID();
    private final User user1 = new User(user1Id, "User 1", "u1@test.com", "hash", Role.USER, State.ACTIVE, Instant.now(), Instant.now());
    private UUID case1Id;
    private UUID case2Id;
    private UUID suspectInCase2Id;

    @BeforeEach
    void setUp() {
        evidenceRepo = new FakeEvidenceRepository();
        suspectRepo = new FakeSuspectRepository();
        caseRepo = new FakeCaseRepository(suspectRepo, evidenceRepo);
        evidenceUseCase = new EvidenceUseCase(evidenceRepo, caseRepo, suspectRepo);

        case1Id = UUID.randomUUID();
        caseRepo.create(new Case(case1Id, 1, "Caso 1", "Desc", "Loc", Instant.now(), CaseStatus.OPEN, Optional.empty(), State.ACTIVE, user1Id, Instant.now(), Instant.now()));

        case2Id = UUID.randomUUID();
        caseRepo.create(new Case(case2Id, 2, "Caso 2", "Desc", "Loc", Instant.now(), CaseStatus.OPEN, Optional.empty(), State.ACTIVE, user1Id, Instant.now(), Instant.now()));

        suspectInCase2Id = UUID.randomUUID();
        suspectRepo.create(new Suspect(suspectInCase2Id, case2Id, "Sospechoso de Caso 2", Optional.empty(), Optional.empty(), 50, SuspectStatus.PERSON_OF_INTEREST, Optional.empty(), State.ACTIVE, Instant.now(), Instant.now()));
    }

    @Test
    @DisplayName("Crear evidencia exitosa en caso propio")
    void createEvidenceSuccess() {
        var res = evidenceUseCase.create(user1, case1Id, Optional.empty(), "Huella digital", "Muestra en vaso", "FORENSIC", "2026-05-10T10:00:00.000Z", 4, Optional.empty());
        assertThat(res.isSuccess()).isTrue();
        Evidence e = ((Result.Success<Evidence>) res).value();
        assertThat(e.type()).isEqualTo(EvidenceType.FORENSIC);
        assertThat(e.caseId()).isEqualTo(case1Id);
    }

    @Test
    @DisplayName("Crear evidencia con suspectId de otro caso devuelve error 400")
    void evidenceWithSuspectFromDifferentCaseFails() {
        // suspectInCase2Id pertenece a case2Id, intentamos asociarlo a case1Id
        var res = evidenceUseCase.create(user1, case1Id, Optional.of(suspectInCase2Id), "Video CCTV", "Grabacion entrada", "DIGITAL", "2026-05-10T10:00:00.000Z", 3, Optional.empty());
        assertThat(res.isFailure()).isTrue();
        var err = ((Result.Failure<Evidence>) res).error();
        assertThat(err).isInstanceOf(AppError.ValidationError.class);
    }

    @Test
    @DisplayName("Eliminar evidencia realiza borrado lógico")
    void deleteEvidenceSuccess() {
        var res = evidenceUseCase.create(user1, case1Id, Optional.empty(), "Contrato", "Papel firmado", "DOCUMENT", "2026-05-10T10:00:00.000Z", 2, Optional.empty());
        UUID eid = ((Result.Success<Evidence>) res).value().id();

        var del = evidenceUseCase.delete(user1, eid);
        assertThat(del.isSuccess()).isTrue();

        var get = evidenceUseCase.getById(user1, eid);
        assertThat(get.isFailure()).isTrue();
    }
}
