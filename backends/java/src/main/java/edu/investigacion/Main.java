package edu.investigacion;

import com.zaxxer.hikari.HikariDataSource;
import edu.investigacion.application.AuthUseCase;
import edu.investigacion.application.BenchmarkUseCase;
import edu.investigacion.application.CaseUseCase;
import edu.investigacion.application.EvidenceUseCase;
import edu.investigacion.application.SuspectUseCase;
import edu.investigacion.application.UserUseCase;
import edu.investigacion.infrastructure.AppConfig;
import edu.investigacion.infrastructure.BcryptPasswordHasher;
import edu.investigacion.infrastructure.Database;
import edu.investigacion.infrastructure.JdbcAnalyticsRepository;
import edu.investigacion.infrastructure.JdbcCaseRepository;
import edu.investigacion.infrastructure.JdbcEvidenceRepository;
import edu.investigacion.infrastructure.JdbcSuspectRepository;
import edu.investigacion.infrastructure.JdbcUserRepository;
import edu.investigacion.infrastructure.JwtTokenService;
import edu.investigacion.infrastructure.StorageService;
import edu.investigacion.web.AppServer;
import edu.investigacion.web.AuthHandler;
import edu.investigacion.web.AuthMiddleware;
import edu.investigacion.web.BenchmarkHandler;
import edu.investigacion.web.CaseHandler;
import edu.investigacion.web.EvidenceHandler;
import edu.investigacion.web.SuspectHandler;
import edu.investigacion.web.UploadHandler;
import edu.investigacion.web.UserHandler;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        // 1. Configuration
        AppConfig config = AppConfig.loadFromEnv();

        // 2. Infrastructure & Data Source
        HikariDataSource ds = Database.createDataSource(config);

        // 3. Security & Repositories
        BcryptPasswordHasher passwordHasher = new BcryptPasswordHasher();
        JwtTokenService tokenService = new JwtTokenService(config.jwtSecret(), config.jwtExpiresInHours());

        JdbcUserRepository userRepo = new JdbcUserRepository(ds);
        JdbcCaseRepository caseRepo = new JdbcCaseRepository(ds);
        JdbcSuspectRepository suspectRepo = new JdbcSuspectRepository(ds);
        JdbcEvidenceRepository evidenceRepo = new JdbcEvidenceRepository(ds);
        JdbcAnalyticsRepository analyticsRepo = new JdbcAnalyticsRepository(ds);

        StorageService storageService = new StorageService(config.uploadDir(), config.publicBaseUrl());

        // 4. Application Use Cases
        AuthUseCase authUseCase = new AuthUseCase(userRepo, passwordHasher, tokenService);
        CaseUseCase caseUseCase = new CaseUseCase(caseRepo);
        SuspectUseCase suspectUseCase = new SuspectUseCase(suspectRepo, caseRepo);
        EvidenceUseCase evidenceUseCase = new EvidenceUseCase(evidenceRepo, caseRepo, suspectRepo);
        UserUseCase userUseCase = new UserUseCase(userRepo);
        BenchmarkUseCase benchmarkUseCase = new BenchmarkUseCase(analyticsRepo);

        // 5. Web Middlewares & Handlers
        AuthMiddleware authMiddleware = new AuthMiddleware(tokenService, userRepo);
        AuthHandler authHandler = new AuthHandler(authUseCase, authMiddleware);
        CaseHandler caseHandler = new CaseHandler(caseUseCase, authMiddleware);
        SuspectHandler suspectHandler = new SuspectHandler(suspectUseCase, authMiddleware);
        EvidenceHandler evidenceHandler = new EvidenceHandler(evidenceUseCase, authMiddleware);
        UserHandler userHandler = new UserHandler(userUseCase, authMiddleware);
        BenchmarkHandler benchmarkHandler = new BenchmarkHandler(benchmarkUseCase, authMiddleware);
        UploadHandler uploadHandler = new UploadHandler(storageService, authMiddleware);

        // 6. HTTP Server
        AppServer server = new AppServer(
            config,
            authHandler,
            caseHandler,
            suspectHandler,
            evidenceHandler,
            userHandler,
            benchmarkHandler,
            uploadHandler
        );

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            ds.close();
        }));

        server.start(config.port());
    }
}
