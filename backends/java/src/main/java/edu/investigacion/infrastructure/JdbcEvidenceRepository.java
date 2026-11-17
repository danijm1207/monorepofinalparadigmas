package edu.investigacion.infrastructure;

import edu.investigacion.domain.Enums.EvidenceType;
import edu.investigacion.domain.Enums.State;
import edu.investigacion.domain.Models.Evidence;
import edu.investigacion.domain.Repositories.EvidenceRepository;

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

public final class JdbcEvidenceRepository implements EvidenceRepository {
    private final DataSource dataSource;

    public JdbcEvidenceRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Evidence> findById(UUID id) {
        String sql = "SELECT id, case_id, suspect_id, title, description, type, " +
                     "collected_at, relevance, image_url, state, created_at, updated_at " +
                     "FROM evidences WHERE id = ? AND state = 'ACTIVE'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al consultar evidencia por id", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Evidence> findAll(
        int page,
        int limit,
        Optional<UUID> caseId,
        Optional<UUID> suspectId,
        Optional<EvidenceType> type,
        Optional<String> q,
        Optional<UUID> ownerId
    ) {
        StringBuilder sql = new StringBuilder(
            "SELECT e.id, e.case_id, e.suspect_id, e.title, e.description, e.type, " +
            "e.collected_at, e.relevance, e.image_url, e.state, e.created_at, e.updated_at " +
            "FROM evidences e " +
            "JOIN cases c ON c.id = e.case_id " +
            "WHERE e.state = 'ACTIVE' AND c.state = 'ACTIVE'"
        );
        List<Object> params = new ArrayList<>();
        applyFilters(sql, params, caseId, suspectId, type, q, ownerId);

        sql.append(" ORDER BY e.created_at DESC, e.id ASC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add((page - 1) * limit);

        List<Evidence> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al listar evidencias", e);
        }
        return list;
    }

    @Override
    public long countAll(
        Optional<UUID> caseId,
        Optional<UUID> suspectId,
        Optional<EvidenceType> type,
        Optional<String> q,
        Optional<UUID> ownerId
    ) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM evidences e JOIN cases c ON c.id = e.case_id " +
            "WHERE e.state = 'ACTIVE' AND c.state = 'ACTIVE'"
        );
        List<Object> params = new ArrayList<>();
        applyFilters(sql, params, caseId, suspectId, type, q, ownerId);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al contar evidencias", e);
        }
        return 0L;
    }

    @Override
    public Evidence create(Evidence e) {
        String sql = "INSERT INTO evidences (id, case_id, suspect_id, title, description, type, collected_at, relevance, image_url, state, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING created_at, updated_at";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, e.id());
            ps.setObject(2, e.caseId());
            ps.setObject(3, e.suspectId().orElse(null));
            ps.setString(4, e.title());
            ps.setString(5, e.description());
            ps.setString(6, e.type().name());
            ps.setTimestamp(7, Timestamp.from(e.collectedAt()));
            ps.setInt(8, e.relevance());
            ps.setString(9, e.imageUrl().orElse(null));
            ps.setString(10, e.state().name());
            ps.setTimestamp(11, Timestamp.from(e.createdAt()));
            ps.setTimestamp(12, Timestamp.from(e.updatedAt()));

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Evidence(
                        e.id(),
                        e.caseId(),
                        e.suspectId(),
                        e.title(),
                        e.description(),
                        e.type(),
                        e.collectedAt(),
                        e.relevance(),
                        e.imageUrl(),
                        e.state(),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                    );
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error al crear evidencia", ex);
        }
        return e;
    }

    @Override
    public Evidence update(Evidence e) {
        String sql = "UPDATE evidences SET suspect_id = ?, title = ?, description = ?, type = ?, " +
                     "collected_at = ?, relevance = ?, image_url = ?, updated_at = NOW() " +
                     "WHERE id = ? AND state = 'ACTIVE' RETURNING updated_at";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, e.suspectId().orElse(null));
            ps.setString(2, e.title());
            ps.setString(3, e.description());
            ps.setString(4, e.type().name());
            ps.setTimestamp(5, Timestamp.from(e.collectedAt()));
            ps.setInt(6, e.relevance());
            ps.setString(7, e.imageUrl().orElse(null));
            ps.setObject(8, e.id());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Evidence(
                        e.id(),
                        e.caseId(),
                        e.suspectId(),
                        e.title(),
                        e.description(),
                        e.type(),
                        e.collectedAt(),
                        e.relevance(),
                        e.imageUrl(),
                        e.state(),
                        e.createdAt(),
                        rs.getTimestamp("updated_at").toInstant()
                    );
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error al actualizar evidencia", ex);
        }
        return e;
    }

    @Override
    public boolean deactivate(UUID id) {
        String sql = "UPDATE evidences SET state = 'INACTIVE', updated_at = NOW() WHERE id = ? AND state = 'ACTIVE'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error al desactivar evidencia", e);
        }
    }

    private void applyFilters(
        StringBuilder sql,
        List<Object> params,
        Optional<UUID> caseId,
        Optional<UUID> suspectId,
        Optional<EvidenceType> type,
        Optional<String> q,
        Optional<UUID> ownerId
    ) {
        if (caseId.isPresent()) {
            sql.append(" AND e.case_id = ?");
            params.add(caseId.get());
        }
        if (suspectId.isPresent()) {
            sql.append(" AND e.suspect_id = ?");
            params.add(suspectId.get());
        }
        if (type.isPresent()) {
            sql.append(" AND e.type = ?");
            params.add(type.get().name());
        }
        if (q.isPresent() && !q.get().trim().isEmpty()) {
            sql.append(" AND (e.title ILIKE ? OR e.description ILIKE ?)");
            String p = "%" + q.get().trim() + "%";
            params.add(p);
            params.add(p);
        }
        if (ownerId.isPresent()) {
            sql.append(" AND c.owner_id = ?");
            params.add(ownerId.get());
        }
    }

    private Evidence mapRow(ResultSet rs) throws SQLException {
        Object sid = rs.getObject("suspect_id");
        String img = rs.getString("image_url");
        return new Evidence(
            rs.getObject("id", UUID.class),
            rs.getObject("case_id", UUID.class),
            Optional.ofNullable(sid != null ? (UUID) sid : null),
            rs.getString("title"),
            rs.getString("description"),
            EvidenceType.valueOf(rs.getString("type")),
            rs.getTimestamp("collected_at").toInstant(),
            rs.getInt("relevance"),
            Optional.ofNullable(img),
            State.valueOf(rs.getString("state")),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }
}
