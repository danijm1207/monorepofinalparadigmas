package edu.investigacion.application;

import edu.investigacion.analytics.Models.CaseRec;
import edu.investigacion.analytics.Models.CaseStatus;
import edu.investigacion.analytics.Models.EvidenceRec;
import edu.investigacion.analytics.Models.EvidenceType;
import edu.investigacion.analytics.Models.SuspectRec;
import edu.investigacion.analytics.Models.SuspectStatus;
import edu.investigacion.domain.AppError;
import edu.investigacion.domain.Enums.Role;
import edu.investigacion.domain.Enums.State;
import edu.investigacion.domain.Models.User;
import edu.investigacion.domain.Repositories.AnalyticsDbRepository;
import edu.investigacion.domain.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkUseCaseTest {

    private BenchmarkUseCase benchmarkUseCase;
    private final User admin = new User(UUID.randomUUID(), "Admin", "admin@test.com", "hash", Role.ADMIN, State.ACTIVE, Instant.now(), Instant.now());
    private final User regularUser = new User(UUID.randomUUID(), "User", "user@test.com", "hash", Role.USER, State.ACTIVE, Instant.now(), Instant.now());

    @BeforeEach
    void setUp() {
        AnalyticsDbRepository fakeAnalyticsRepo = new AnalyticsDbRepository() {
            @Override
            public List<CaseRec> findActiveCases() {
                return List.of(new CaseRec("c1", CaseStatus.OPEN, 19358));
            }

            @Override
            public List<SuspectRec> findActiveSuspects() {
                return List.of(new SuspectRec("s1", "c1", 60, SuspectStatus.PERSON_OF_INTEREST));
            }

            @Override
            public List<EvidenceRec> findActiveEvidences() {
                return List.of(new EvidenceRec("c1", Optional.of("s1"), EvidenceType.FORENSIC, 4, 19358, true));
            }
        };

        benchmarkUseCase = new BenchmarkUseCase(fakeAnalyticsRepo);
    }

    @Test
    @DisplayName("USER no puede ejecutar benchmark (403 FORBIDDEN)")
    void userCannotRunBenchmark() {
        var res = benchmarkUseCase.runBenchmark(regularUser, "T", 42L, false);
        assertThat(res.isFailure()).isTrue();
        var err = ((Result.Failure<BenchmarkUseCase.BenchmarkResult>) res).error();
        assertThat(err).isInstanceOf(AppError.Forbidden.class);
    }

    @Test
    @DisplayName("ADMIN ejecutando escenario T recibe métricas y checksum esperado")
    void adminRunsScenarioT() {
        var res = benchmarkUseCase.runBenchmark(admin, "T", 42L, true);
        assertThat(res.isSuccess()).isTrue();
        var data = ((Result.Success<BenchmarkUseCase.BenchmarkResult>) res).value();
        assertThat(data.checksum()).isEqualTo("bf5c6284888610a729ceeebf66b32d45854018ff61f0cf1867f67fba67d2a91e");
        assertThat(data.processedRecords()).isEqualTo(280);
        assertThat(data.report()).isPresent();
    }

    @Test
    @DisplayName("Escenario no permitido devuelve 400 VALIDATION_ERROR")
    void invalidScenarioFails() {
        var res = benchmarkUseCase.runBenchmark(admin, "INVALIDO", 42L, false);
        assertThat(res.isFailure()).isTrue();
        var err = ((Result.Failure<BenchmarkUseCase.BenchmarkResult>) res).error();
        assertThat(err).isInstanceOf(AppError.ValidationError.class);
    }

    @Test
    @DisplayName("Overview ejecuta pipeline sobre datos activos de BD")
    void overviewExecutesSuccessfully() {
        var res = benchmarkUseCase.runOverview(admin);
        assertThat(res.isSuccess()).isTrue();
        var data = ((Result.Success<BenchmarkUseCase.OverviewResult>) res).value();
        assertThat(data.processedRecords()).isEqualTo(3);
        assertThat(data.checksum()).isNotBlank();
        assertThat(data.report()).isNotEmpty();
    }
}
