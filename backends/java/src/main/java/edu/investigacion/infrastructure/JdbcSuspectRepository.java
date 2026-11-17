package edu.investigacion.infrastructure;

import edu.investigacion.domain.Enums.State;
import edu.investigacion.domain.Enums.SuspectStatus;
import edu.investigacion.domain.Models.Suspect;
import edu.investigacion.domain.Repositories.SuspectRepository;

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

public final class JdbcSuspectRepository implements SuspectRepository {
    private final DataSource dataSource;

    public JdbcSuspectRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Suspect> findById(UUID id) {
        String sql = "SELECT s.id, s.case_id, s.full_name, s.alias, s.description, " +
                     "s.suspicion_level, s.status, s.photo_url, s.state, s.created_at, s.updated_at " +
                     "FROM suspects s WHERE s.id = ? AND s.state = 'ACTIVE'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al consultar sospechoso por id", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Suspect> findAll(
        int page,
        int limit,
        Optional<UUID> caseId,
        Optional<SuspectStatus> status,
        Optional<String> q,
        Optional<UUID> ownerId
    ) {
        StringBuilder sql = new StringBuilder(
            "SELECT s.id, s.case_id, s.full_name, s.alias, s.description, " +
            "s.suspicion_level, s.status, s.photo_url, s.state, s.created_at, s.updated_at " +
            "FROM suspects s " +
            "JOIN cases c ON c.id = s.case_id " +
            "WHERE s.state = 'ACTIVE' AND c.state = 'ACTIVE'"
        );
        List<Object> params = new ArrayList<>();
        applyFilters(sql, params, caseId, status, q, ownerId);

        sql.append(" ORDER BY s.created_at DESC, s.id ASC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add((page - 1) * limit);

        List<Suspect> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al listar sospechosos", e);
        }
        return list;
    }

    @Override
    public long countAll(
        Optional<UUID> caseId,
        Optional<SuspectStatus> status,
        Optional<String> q,
        Optional<UUID> ownerId
    ) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM suspects s JOIN cases c ON c.id = s.case_id " +
            "WHERE s.state = 'ACTIVE' AND c.state = 'ACTIVE'"
        );
        List<Object> params = new ArrayList<>();
        applyFilters(sql, params, caseId, status, q, ownerId);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al contar sospechosos", e);
        }
        return 0L;
    }

    @Override
    public Suspect create(Suspect s) {
        String sql = "INSERT INTO suspects (id, case_id, full_name, alias, description, suspicion_level, status, photo_url, state, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING created_at, updated_at";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, s.id());
            ps.setObject(2, s.caseId());
            ps.setString(3, s.fullName());
            ps.setString(4, s.alias().orElse(null));
            ps.setString(5, s.description().orElse(null));
            ps.setInt(6, s.suspicionLevel());
            ps.setString(7, s.status().name());
            ps.setString(8, s.photoUrl().orElse(null));
            ps.setString(9, s.state().name());
            ps.setTimestamp(10, Timestamp.from(s.createdAt()));
            ps.setTimestamp(11, Timestamp.from(s.updatedAt()));

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Suspect(
                        s.id(),
                        s.caseId(),
                        s.fullName(),
                        s.alias(),
                        s.description(),
                        s.suspicionLevel(),
                        s.status(),
                        s.photoUrl(),
                        s.state(),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al crear sospechoso", e);
        }
        return s;
    }

    @Override
    public Suspect update(Suspect s) {
        String sql = "UPDATE suspects SET full_name = ?, alias = ?, description = ?, " +
                     "suspicion_level = ?, status = ?, photo_url = ?, updated_at = NOW() " +
                     "WHERE id = ? AND state = 'ACTIVE' RETURNING updated_at";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, s.fullName());
            ps.setString(2, s.alias().orElse(null));
            ps.setString(3, s.description().orElse(null));
            ps.setInt(4, s.suspicionLevel());
            ps.setString(5, s.status().name());
            ps.setString(6, s.photoUrl().orElse(null));
            ps.setObject(7, s.id());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Suspect(
                        s.id(),
                        s.caseId(),
                        s.fullName(),
                        s.alias(),
                        s.description(),
                        s.suspicionLevel(),
                        s.status(),
                        s.photoUrl(),
                        s.state(),
                        s.createdAt(),
                        rs.getTimestamp("updated_at").toInstant()
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al actualizar sospechoso", e);
        }
        return s;
    }

    @Override
    public boolean deactivateCascade(UUID id) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Deactivate suspect
                try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE suspects SET state = 'INACTIVE', updated_at = NOW() WHERE id = ? AND state = 'ACTIVE'")) {
                    ps.setObject(1, id);
                    ps.executeUpdate();
                }

                // 2. Set suspect_id = NULL on evidences linked to this suspect
                try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE evidences SET suspect_id = NULL, updated_at = NOW() WHERE suspect_id = ? AND state = 'ACTIVE'")) {
                    ps.setObject(1, id);
                    ps.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw new RuntimeException("Error en transacción de desactivación de sospechoso", e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al conectar para desactivar sospechoso", e);
        }
    }

    private void applyFilters(
        StringBuilder sql,
        List<Object> params,
        Optional<UUID> caseId,
        Optional<SuspectStatus> status,
        Optional<String> q,
        Optional<UUID> ownerId
    ) {
        if (caseId.isPresent()) {
            sql.append(" AND s.case_id = ?");
            params.add(caseId.get());
        }
        if (status.isPresent()) {
            sql.append(" AND s.status = ?");
            params.add(status.get().name());
        }
        if (q.isPresent() && !q.get().trim().isEmpty()) {
            sql.append(" AND (s.full_name ILIKE ? OR s.alias ILIKE ?)");
            String p = "%" + q.get().trim() + "%";
            params.add(p);
            params.add(p);
        }
        if (ownerId.isPresent()) {
            sql.append(" AND c.owner_id = ?");
            params.add(ownerId.get());
        }
    }

    private Suspect mapRow(ResultSet rs) throws SQLException {
        String alias = rs.getString("alias");
        String desc = rs.getString("description");
        String photo = rs.getString("photo_url");
        return new Suspect(
            rs.getObject("id", UUID.class),
            rs.getObject("case_id", UUID.class),
            rs.getString("full_name"),
            Optional.ofNullable(alias),
            Optional.ofNullable(desc),
            rs.getInt("suspicion_level"),
            SuspectStatus.valueOf(rs.getString("status")),
            Optional.ofNullable(photo),
            State.valueOf(rs.getString("state")),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }
}
