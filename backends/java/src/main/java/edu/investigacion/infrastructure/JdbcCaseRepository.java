package edu.investigacion.infrastructure;

import edu.investigacion.domain.Enums.CaseStatus;
import edu.investigacion.domain.Enums.State;
import edu.investigacion.domain.Models.Case;
import edu.investigacion.domain.Repositories.CaseRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcCaseRepository implements CaseRepository {
    private final DataSource dataSource;

    public JdbcCaseRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Case> findById(UUID id) {
        String sql = "SELECT id, case_number, title, description, location, incident_date, status, " +
                     "image_url, state, owner_id, created_at, updated_at " +
                     "FROM cases WHERE id = ? AND state = 'ACTIVE'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al consultar caso por id", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Case> findAll(
        int page,
        int limit,
        Optional<String> q,
        Optional<CaseStatus> status,
        Optional<Instant> from,
        Optional<Instant> to,
        Optional<UUID> ownerId
    ) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, case_number, title, description, location, incident_date, status, " +
            "image_url, state, owner_id, created_at, updated_at " +
            "FROM cases WHERE state = 'ACTIVE'"
        );
        List<Object> params = new ArrayList<>();
        applyFilters(sql, params, q, status, from, to, ownerId);

        sql.append(" ORDER BY created_at DESC, id ASC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add((page - 1) * limit);

        List<Case> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al listar casos", e);
        }
        return list;
    }

    @Override
    public long countAll(
        Optional<String> q,
        Optional<CaseStatus> status,
        Optional<Instant> from,
        Optional<Instant> to,
        Optional<UUID> ownerId
    ) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM cases WHERE state = 'ACTIVE'");
        List<Object> params = new ArrayList<>();
        applyFilters(sql, params, q, status, from, to, ownerId);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al contar casos", e);
        }
        return 0L;
    }

    @Override
    public Case create(Case c) {
        String sql = "INSERT INTO cases (id, title, description, location, incident_date, status, image_url, state, owner_id, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                     "RETURNING case_number, created_at, updated_at";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, c.id());
            ps.setString(2, c.title());
            ps.setString(3, c.description());
            ps.setString(4, c.location());
            ps.setTimestamp(5, Timestamp.from(c.incidentDate()));
            ps.setString(6, c.status().name());
            ps.setString(7, c.imageUrl().orElse(null));
            ps.setString(8, c.state().name());
            ps.setObject(9, c.ownerId());
            ps.setTimestamp(10, Timestamp.from(c.createdAt()));
            ps.setTimestamp(11, Timestamp.from(c.updatedAt()));

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int caseNum = rs.getInt("case_number");
                    Instant createdAt = rs.getTimestamp("created_at").toInstant();
                    Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
                    return new Case(
                        c.id(),
                        caseNum,
                        c.title(),
                        c.description(),
                        c.location(),
                        c.incidentDate(),
                        c.status(),
                        c.imageUrl(),
                        c.state(),
                        c.ownerId(),
                        createdAt,
                        updatedAt
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al insertar caso", e);
        }
        return c;
    }

    @Override
    public Case update(Case c) {
        String sql = "UPDATE cases SET title = ?, description = ?, location = ?, incident_date = ?, " +
                     "status = ?, image_url = ?, updated_at = NOW() " +
                     "WHERE id = ? AND state = 'ACTIVE' " +
                     "RETURNING updated_at";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.title());
            ps.setString(2, c.description());
            ps.setString(3, c.location());
            ps.setTimestamp(4, Timestamp.from(c.incidentDate()));
            ps.setString(5, c.status().name());
            ps.setString(6, c.imageUrl().orElse(null));
            ps.setObject(7, c.id());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
                    return new Case(
                        c.id(),
                        c.caseNumber(),
                        c.title(),
                        c.description(),
                        c.location(),
                        c.incidentDate(),
                        c.status(),
                        c.imageUrl(),
                        c.state(),
                        c.ownerId(),
                        c.createdAt(),
                        updatedAt
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al actualizar caso", e);
        }
        return c;
    }

    @Override
    public boolean deactivateCascade(UUID id) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Deactivate case
                try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE cases SET state = 'INACTIVE', updated_at = NOW() WHERE id = ? AND state = 'ACTIVE'")) {
                    ps.setObject(1, id);
                    ps.executeUpdate();
                }

                // 2. Deactivate suspects of this case
                try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE suspects SET state = 'INACTIVE', updated_at = NOW() WHERE case_id = ? AND state = 'ACTIVE'")) {
                    ps.setObject(1, id);
                    ps.executeUpdate();
                }

                // 3. Deactivate evidences of this case
                try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE evidences SET state = 'INACTIVE', updated_at = NOW() WHERE case_id = ? AND state = 'ACTIVE'")) {
                    ps.setObject(1, id);
                    ps.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw new RuntimeException("Error en transacción de desactivación en cascada", e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al abrir conexión para desactivación en cascada", e);
        }
    }

    private void applyFilters(
        StringBuilder sql,
        List<Object> params,
        Optional<String> q,
        Optional<CaseStatus> status,
        Optional<Instant> from,
        Optional<Instant> to,
        Optional<UUID> ownerId
    ) {
        if (q.isPresent() && !q.get().trim().isEmpty()) {
            sql.append(" AND (title ILIKE ? OR location ILIKE ?)");
            String p = "%" + q.get().trim() + "%";
            params.add(p);
            params.add(p);
        }
        if (status.isPresent()) {
            sql.append(" AND status = ?");
            params.add(status.get().name());
        }
        if (from.isPresent()) {
            sql.append(" AND incident_date >= ?");
            params.add(Timestamp.from(from.get()));
        }
        if (to.isPresent()) {
            sql.append(" AND incident_date <= ?");
            params.add(Timestamp.from(to.get()));
        }
        if (ownerId.isPresent()) {
            sql.append(" AND owner_id = ?");
            params.add(ownerId.get());
        }
    }

    private Case mapRow(ResultSet rs) throws SQLException {
        String img = rs.getString("image_url");
        return new Case(
            rs.getObject("id", UUID.class),
            rs.getInt("case_number"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getString("location"),
            rs.getTimestamp("incident_date").toInstant(),
            CaseStatus.valueOf(rs.getString("status")),
            Optional.ofNullable(img),
            State.valueOf(rs.getString("state")),
            rs.getObject("owner_id", UUID.class),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }
}
