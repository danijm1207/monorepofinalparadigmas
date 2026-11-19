package edu.investigacion.infrastructure;

import edu.investigacion.domain.Enums.Role;
import edu.investigacion.domain.Enums.State;
import edu.investigacion.domain.Models.User;
import edu.investigacion.domain.Repositories.UserRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcUserRepository implements UserRepository {
    private final DataSource dataSource;

    public JdbcUserRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<User> findById(UUID id) {
        String sql = "SELECT id, name, email, password_hash, role, state, created_at, updated_at " +
                     "FROM users WHERE id = ? AND state = 'ACTIVE'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al consultar usuario por id", e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        String sql = "SELECT id, name, email, password_hash, role, state, created_at, updated_at " +
                     "FROM users WHERE LOWER(email) = LOWER(?) AND state = 'ACTIVE'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al consultar usuario por email", e);
        }
        return Optional.empty();
    }

    @Override
    public List<User> findAll(int page, int limit, Optional<String> q, Optional<Role> role) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, name, email, password_hash, role, state, created_at, updated_at " +
            "FROM users WHERE state = 'ACTIVE'"
        );
        List<Object> params = new ArrayList<>();
        applyFilters(sql, params, q, role);

        sql.append(" ORDER BY created_at DESC, id ASC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add((page - 1) * limit);

        List<User> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al listar usuarios", e);
        }
        return list;
    }

    @Override
    public long countAll(Optional<String> q, Optional<Role> role) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM users WHERE state = 'ACTIVE'");
        List<Object> params = new ArrayList<>();
        applyFilters(sql, params, q, role);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al contar usuarios", e);
        }
        return 0L;
    }

    @Override
    public User save(User user) {
        String sql = "INSERT INTO users (id, name, email, password_hash, role, state, created_at, updated_at) " +
                     "VALUES (?, ?, LOWER(?), ?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, user.id());
            ps.setString(2, user.name());
            ps.setString(3, user.email());
            ps.setString(4, user.passwordHash());
            ps.setString(5, user.role().name());
            ps.setString(6, user.state().name());
            ps.setTimestamp(7, Timestamp.from(user.createdAt()));
            ps.setTimestamp(8, Timestamp.from(user.updatedAt()));
            ps.executeUpdate();
            return user;
        } catch (SQLException e) {
            throw new RuntimeException("Error al insertar usuario", e);
        }
    }

    @Override
    public boolean deactivate(UUID id) {
        String sql = "UPDATE users SET state = 'INACTIVE', updated_at = NOW() WHERE id = ? AND state = 'ACTIVE'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error al desactivar usuario", e);
        }
    }

    private void applyFilters(StringBuilder sql, List<Object> params, Optional<String> q, Optional<Role> role) {
        if (q.isPresent() && !q.get().trim().isEmpty()) {
            sql.append(" AND (name ILIKE ? OR email ILIKE ?)");
            String pattern = "%" + q.get().trim() + "%";
            params.add(pattern);
            params.add(pattern);
        }
        if (role.isPresent()) {
            sql.append(" AND role = ?");
            params.add(role.get().name());
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        return new User(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getString("email"),
            rs.getString("password_hash"),
            Role.valueOf(rs.getString("role")),
            State.valueOf(rs.getString("state")),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }
}
