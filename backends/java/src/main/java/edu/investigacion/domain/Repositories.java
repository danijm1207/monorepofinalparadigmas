package edu.investigacion.domain;

import edu.investigacion.domain.Enums.CaseStatus;
import edu.investigacion.domain.Enums.EvidenceType;
import edu.investigacion.domain.Enums.Role;
import edu.investigacion.domain.Enums.SuspectStatus;
import edu.investigacion.domain.Models.Case;
import edu.investigacion.domain.Models.Evidence;
import edu.investigacion.domain.Models.Suspect;
import edu.investigacion.domain.Models.User;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class Repositories {
    private Repositories() {}

    public interface UserRepository {
        Optional<User> findById(UUID id);
        Optional<User> findByEmail(String email);
        List<User> findAll(int page, int limit, Optional<String> q, Optional<Role> role);
        long countAll(Optional<String> q, Optional<Role> role);
        User save(User user);
        boolean deactivate(UUID id);
    }

    public interface CaseRepository {
        Optional<Case> findById(UUID id);
        List<Case> findAll(int page, int limit, Optional<String> q, Optional<CaseStatus> status, Optional<Instant> from, Optional<Instant> to, Optional<UUID> ownerId);
        long countAll(Optional<String> q, Optional<CaseStatus> status, Optional<Instant> from, Optional<Instant> to, Optional<UUID> ownerId);
        Case create(Case caseItem);
        Case update(Case caseItem);
        boolean deactivateCascade(UUID id);
    }

    public interface SuspectRepository {
        Optional<Suspect> findById(UUID id);
        List<Suspect> findAll(int page, int limit, Optional<UUID> caseId, Optional<SuspectStatus> status, Optional<String> q, Optional<UUID> ownerId);
        long countAll(Optional<UUID> caseId, Optional<SuspectStatus> status, Optional<String> q, Optional<UUID> ownerId);
        Suspect create(Suspect suspect);
        Suspect update(Suspect suspect);
        boolean deactivateCascade(UUID id);
    }

    public interface EvidenceRepository {
        Optional<Evidence> findById(UUID id);
        List<Evidence> findAll(int page, int limit, Optional<UUID> caseId, Optional<UUID> suspectId, Optional<EvidenceType> type, Optional<String> q, Optional<UUID> ownerId);
        long countAll(Optional<UUID> caseId, Optional<UUID> suspectId, Optional<EvidenceType> type, Optional<String> q, Optional<UUID> ownerId);
        Evidence create(Evidence evidence);
        Evidence update(Evidence evidence);
        boolean deactivate(UUID id);
    }

    public interface AnalyticsDbRepository {
        List<edu.investigacion.analytics.Models.CaseRec> findActiveCases();
        List<edu.investigacion.analytics.Models.SuspectRec> findActiveSuspects();
        List<edu.investigacion.analytics.Models.EvidenceRec> findActiveEvidences();
    }
}
