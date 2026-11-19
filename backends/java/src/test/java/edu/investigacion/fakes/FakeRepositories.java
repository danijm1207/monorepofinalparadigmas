package edu.investigacion.fakes;

import edu.investigacion.domain.Enums.CaseStatus;
import edu.investigacion.domain.Enums.EvidenceType;
import edu.investigacion.domain.Enums.Role;
import edu.investigacion.domain.Enums.State;
import edu.investigacion.domain.Enums.SuspectStatus;
import edu.investigacion.domain.Models.Case;
import edu.investigacion.domain.Models.Evidence;
import edu.investigacion.domain.Models.Suspect;
import edu.investigacion.domain.Models.User;
import edu.investigacion.domain.Repositories.CaseRepository;
import edu.investigacion.domain.Repositories.EvidenceRepository;
import edu.investigacion.domain.Repositories.SuspectRepository;
import edu.investigacion.domain.Repositories.UserRepository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class FakeRepositories {

    public static class FakeUserRepository implements UserRepository {
        private final Map<UUID, User> store = new ConcurrentHashMap<>();

        @Override
        public Optional<User> findById(UUID id) {
            User u = store.get(id);
            return (u != null && u.state() == State.ACTIVE) ? Optional.of(u) : Optional.empty();
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return store.values().stream()
                .filter(u -> u.email().equalsIgnoreCase(email.trim()) && u.state() == State.ACTIVE)
                .findFirst();
        }

        @Override
        public List<User> findAll(int page, int limit, Optional<String> q, Optional<Role> role) {
            return store.values().stream()
                .filter(u -> u.state() == State.ACTIVE)
                .filter(u -> q.isEmpty() || u.name().contains(q.get()) || u.email().contains(q.get()))
                .filter(u -> role.isEmpty() || u.role() == role.get())
                .sorted(Comparator.comparing(User::createdAt).reversed())
                .skip((long) (page - 1) * limit)
                .limit(limit)
                .toList();
        }

        @Override
        public long countAll(Optional<String> q, Optional<Role> role) {
            return store.values().stream()
                .filter(u -> u.state() == State.ACTIVE)
                .filter(u -> q.isEmpty() || u.name().contains(q.get()) || u.email().contains(q.get()))
                .filter(u -> role.isEmpty() || u.role() == role.get())
                .count();
        }

        @Override
        public User save(User user) {
            store.put(user.id(), user);
            return user;
        }

        @Override
        public boolean deactivate(UUID id) {
            User u = store.get(id);
            if (u != null && u.state() == State.ACTIVE) {
                store.put(id, new User(u.id(), u.name(), u.email(), u.passwordHash(), u.role(), State.INACTIVE, u.createdAt(), Instant.now()));
                return true;
            }
            return false;
        }
    }

    public static class FakeCaseRepository implements CaseRepository {
        public final Map<UUID, Case> store = new ConcurrentHashMap<>();
        public final FakeSuspectRepository suspectRepo;
        public final FakeEvidenceRepository evidenceRepo;
        private int counter = 1;

        public FakeCaseRepository() {
            this.suspectRepo = null;
            this.evidenceRepo = null;
        }

        public FakeCaseRepository(FakeSuspectRepository suspectRepo, FakeEvidenceRepository evidenceRepo) {
            this.suspectRepo = suspectRepo;
            this.evidenceRepo = evidenceRepo;
        }

        @Override
        public Optional<Case> findById(UUID id) {
            Case c = store.get(id);
            return (c != null && c.state() == State.ACTIVE) ? Optional.of(c) : Optional.empty();
        }

        @Override
        public List<Case> findAll(int page, int limit, Optional<String> q, Optional<CaseStatus> status, Optional<Instant> from, Optional<Instant> to, Optional<UUID> ownerId) {
            return store.values().stream()
                .filter(c -> c.state() == State.ACTIVE)
                .filter(c -> q.isEmpty() || c.title().contains(q.get()) || c.location().contains(q.get()))
                .filter(c -> status.isEmpty() || c.status() == status.get())
                .filter(c -> from.isEmpty() || !c.incidentDate().isBefore(from.get()))
                .filter(c -> to.isEmpty() || !c.incidentDate().isAfter(to.get()))
                .filter(c -> ownerId.isEmpty() || c.ownerId().equals(ownerId.get()))
                .sorted(Comparator.comparing(Case::createdAt).reversed())
                .skip((long) (page - 1) * limit)
                .limit(limit)
                .toList();
        }

        @Override
        public long countAll(Optional<String> q, Optional<CaseStatus> status, Optional<Instant> from, Optional<Instant> to, Optional<UUID> ownerId) {
            return store.values().stream()
                .filter(c -> c.state() == State.ACTIVE)
                .filter(c -> q.isEmpty() || c.title().contains(q.get()) || c.location().contains(q.get()))
                .filter(c -> status.isEmpty() || c.status() == status.get())
                .filter(c -> from.isEmpty() || !c.incidentDate().isBefore(from.get()))
                .filter(c -> to.isEmpty() || !c.incidentDate().isAfter(to.get()))
                .filter(c -> ownerId.isEmpty() || c.ownerId().equals(ownerId.get()))
                .count();
        }

        @Override
        public Case create(Case caseItem) {
            Case c = new Case(caseItem.id(), counter++, caseItem.title(), caseItem.description(), caseItem.location(), caseItem.incidentDate(), caseItem.status(), caseItem.imageUrl(), caseItem.state(), caseItem.ownerId(), caseItem.createdAt(), caseItem.updatedAt());
            store.put(c.id(), c);
            return c;
        }

        @Override
        public Case update(Case caseItem) {
            store.put(caseItem.id(), caseItem);
            return caseItem;
        }

        @Override
        public boolean deactivateCascade(UUID id) {
            Case c = store.get(id);
            if (c != null && c.state() == State.ACTIVE) {
                store.put(id, new Case(c.id(), c.caseNumber(), c.title(), c.description(), c.location(), c.incidentDate(), c.status(), c.imageUrl(), State.INACTIVE, c.ownerId(), c.createdAt(), Instant.now()));
                if (suspectRepo != null) {
                    suspectRepo.store.values().stream()
                        .filter(s -> s.caseId().equals(id))
                        .forEach(s -> suspectRepo.deactivateCascade(s.id()));
                }
                if (evidenceRepo != null) {
                    evidenceRepo.store.values().stream()
                        .filter(e -> e.caseId().equals(id))
                        .forEach(e -> evidenceRepo.deactivate(e.id()));
                }
                return true;
            }
            return false;
        }
    }

    public static class FakeSuspectRepository implements SuspectRepository {
        public final Map<UUID, Suspect> store = new ConcurrentHashMap<>();
        public FakeEvidenceRepository evidenceRepo;

        @Override
        public Optional<Suspect> findById(UUID id) {
            Suspect s = store.get(id);
            return (s != null && s.state() == State.ACTIVE) ? Optional.of(s) : Optional.empty();
        }

        @Override
        public List<Suspect> findAll(int page, int limit, Optional<UUID> caseId, Optional<SuspectStatus> status, Optional<String> q, Optional<UUID> ownerId) {
            return store.values().stream()
                .filter(s -> s.state() == State.ACTIVE)
                .filter(s -> caseId.isEmpty() || s.caseId().equals(caseId.get()))
                .filter(s -> status.isEmpty() || s.status() == status.get())
                .filter(s -> q.isEmpty() || s.fullName().contains(q.get()))
                .sorted(Comparator.comparing(Suspect::createdAt).reversed())
                .skip((long) (page - 1) * limit)
                .limit(limit)
                .toList();
        }

        @Override
        public long countAll(Optional<UUID> caseId, Optional<SuspectStatus> status, Optional<String> q, Optional<UUID> ownerId) {
            return store.values().stream()
                .filter(s -> s.state() == State.ACTIVE)
                .filter(s -> caseId.isEmpty() || s.caseId().equals(caseId.get()))
                .filter(s -> status.isEmpty() || s.status() == status.get())
                .filter(s -> q.isEmpty() || s.fullName().contains(q.get()))
                .count();
        }

        @Override
        public Suspect create(Suspect suspect) {
            store.put(suspect.id(), suspect);
            return suspect;
        }

        @Override
        public Suspect update(Suspect suspect) {
            store.put(suspect.id(), suspect);
            return suspect;
        }

        @Override
        public boolean deactivateCascade(UUID id) {
            Suspect s = store.get(id);
            if (s != null && s.state() == State.ACTIVE) {
                store.put(id, new Suspect(s.id(), s.caseId(), s.fullName(), s.alias(), s.description(), s.suspicionLevel(), s.status(), s.photoUrl(), State.INACTIVE, s.createdAt(), Instant.now()));
                if (evidenceRepo != null) {
                    evidenceRepo.store.values().stream()
                        .filter(e -> e.suspectId().isPresent() && e.suspectId().get().equals(id))
                        .forEach(e -> evidenceRepo.store.put(e.id(), new Evidence(e.id(), e.caseId(), Optional.empty(), e.title(), e.description(), e.type(), e.collectedAt(), e.relevance(), e.imageUrl(), e.state(), e.createdAt(), Instant.now())));
                }
                return true;
            }
            return false;
        }
    }

    public static class FakeEvidenceRepository implements EvidenceRepository {
        public final Map<UUID, Evidence> store = new ConcurrentHashMap<>();

        @Override
        public Optional<Evidence> findById(UUID id) {
            Evidence e = store.get(id);
            return (e != null && e.state() == State.ACTIVE) ? Optional.of(e) : Optional.empty();
        }

        @Override
        public List<Evidence> findAll(int page, int limit, Optional<UUID> caseId, Optional<UUID> suspectId, Optional<EvidenceType> type, Optional<String> q, Optional<UUID> ownerId) {
            return store.values().stream()
                .filter(e -> e.state() == State.ACTIVE)
                .filter(e -> caseId.isEmpty() || e.caseId().equals(caseId.get()))
                .filter(e -> suspectId.isEmpty() || (e.suspectId().isPresent() && e.suspectId().get().equals(suspectId.get())))
                .filter(e -> type.isEmpty() || e.type() == type.get())
                .filter(e -> q.isEmpty() || e.title().contains(q.get()))
                .sorted(Comparator.comparing(Evidence::createdAt).reversed())
                .skip((long) (page - 1) * limit)
                .limit(limit)
                .toList();
        }

        @Override
        public long countAll(Optional<UUID> caseId, Optional<UUID> suspectId, Optional<EvidenceType> type, Optional<String> q, Optional<UUID> ownerId) {
            return store.values().stream()
                .filter(e -> e.state() == State.ACTIVE)
                .filter(e -> caseId.isEmpty() || e.caseId().equals(caseId.get()))
                .filter(e -> suspectId.isEmpty() || (e.suspectId().isPresent() && e.suspectId().get().equals(suspectId.get())))
                .filter(e -> type.isEmpty() || e.type() == type.get())
                .filter(e -> q.isEmpty() || e.title().contains(q.get()))
                .count();
        }

        @Override
        public Evidence create(Evidence evidence) {
            store.put(evidence.id(), evidence);
            return evidence;
        }

        @Override
        public Evidence update(Evidence evidence) {
            store.put(evidence.id(), evidence);
            return evidence;
        }

        @Override
        public boolean deactivate(UUID id) {
            Evidence e = store.get(id);
            if (e != null && e.state() == State.ACTIVE) {
                store.put(id, new Evidence(e.id(), e.caseId(), e.suspectId(), e.title(), e.description(), e.type(), e.collectedAt(), e.relevance(), e.imageUrl(), State.INACTIVE, e.createdAt(), Instant.now()));
                return true;
            }
            return false;
        }
    }
}
