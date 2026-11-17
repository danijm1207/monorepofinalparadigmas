package edu.investigacion.infrastructure

import edu.investigacion.domain.*
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcUserRepository(private val dataSource: DataSource) : UserRepository {

    private fun mapUser(rs: ResultSet): User {
        return User(
            id = rs.getString("id"),
            name = rs.getString("name"),
            email = rs.getString("email"),
            passwordHash = rs.getString("password_hash"),
            role = Role.valueOf(rs.getString("role")),
            state = RecordState.valueOf(rs.getString("state")),
            createdAt = JsonTreeService.formatInstant(rs.getTimestamp("created_at").toInstant()),
            updatedAt = JsonTreeService.formatInstant(rs.getTimestamp("updated_at").toInstant())
        )
    }

    override fun findById(id: String): User? {
        return DatabaseManager.withConnection(dataSource) { conn ->
            val sql = "SELECT * FROM users WHERE id = ?::uuid"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id)
                val rs = stmt.executeQuery()
                if (rs.next()) mapUser(rs) else null
            }
        }
    }

    override fun findByEmail(email: String): User? {
        return DatabaseManager.withConnection(dataSource) { conn ->
            val sql = "SELECT * FROM users WHERE LOWER(email) = LOWER(?)"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, email)
                val rs = stmt.executeQuery()
                if (rs.next()) mapUser(rs) else null
            }
        }
    }

    override fun create(user: User): Result<User> {
        return DatabaseManager.withConnection(dataSource) { conn ->
            val sql = """
                INSERT INTO users (id, name, email, password_hash, role, state)
                VALUES (?::uuid, ?, LOWER(?), ?, ?, ?)
                RETURNING created_at, updated_at
            """.trimIndent()
            try {
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, user.id)
                    stmt.setString(2, user.name)
                    stmt.setString(3, user.email)
                    stmt.setString(4, user.passwordHash)
                    stmt.setString(5, user.role.name)
                    stmt.setString(6, user.state.name)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        val created = user.copy(
                            createdAt = JsonTreeService.formatInstant(rs.getTimestamp("created_at").toInstant()),
                            updatedAt = JsonTreeService.formatInstant(rs.getTimestamp("updated_at").toInstant())
                        )
                        Result.Success(created)
                    } else {
                        Result.Failure(AppError.Internal("No se pudo crear el usuario"))
                    }
                }
            } catch (e: Exception) {
                if (e.message?.contains("users_email_key") == true || e.message?.contains("23505") == true) {
                    Result.Failure(AppError.Conflict("El correo electrónico ya está registrado"))
                } else {
                    Result.Failure(AppError.Internal(e.message ?: "Error al crear usuario"))
                }
            }
        }
    }

    override fun findAll(page: Int, limit: Int, query: String?, role: Role?): Page<UserPublic> {
        return DatabaseManager.withConnection(dataSource) { conn ->
            val whereClauses = mutableListOf("state = 'ACTIVE'")
            val params = mutableListOf<Any>()

            if (!query.isNullOrBlank()) {
                whereClauses.add("(name ILIKE ? OR email ILIKE ?)")
                params.add("%${query.trim()}%")
                params.add("%${query.trim()}%")
            }
            if (role != null) {
                whereClauses.add("role = ?")
                params.add(role.name)
            }

            val whereSql = "WHERE " + whereClauses.joinToString(" AND ")
            val countSql = "SELECT COUNT(*) FROM users $whereSql"

            val total = conn.prepareStatement(countSql).use { stmt ->
                params.forEachIndexed { i, p -> stmt.setObject(i + 1, p) }
                val rs = stmt.executeQuery()
                if (rs.next()) rs.getLong(1) else 0L
            }

            val offset = (page - 1) * limit
            val selectSql = """
                SELECT id, name, email, role, created_at 
                FROM users 
                $whereSql 
                ORDER BY created_at DESC, id ASC 
                LIMIT ? OFFSET ?
            """.trimIndent()

            val data = conn.prepareStatement(selectSql).use { stmt ->
                var idx = 1
                params.forEach { p -> stmt.setObject(idx++, p) }
                stmt.setInt(idx++, limit)
                stmt.setInt(idx, offset)
                val rs = stmt.executeQuery()
                val list = mutableListOf<UserPublic>()
                while (rs.next()) {
                    list.add(
                        UserPublic(
                            id = rs.getString("id"),
                            name = rs.getString("name"),
                            email = rs.getString("email"),
                            role = Role.valueOf(rs.getString("role")),
                            createdAt = JsonTreeService.formatInstant(rs.getTimestamp("created_at").toInstant())
                        )
                    )
                }
                list
            }

            val totalPages = if (total == 0L) 0 else ((total + limit - 1) / limit).toInt()
            Page(data, Pagination(total, page, limit, totalPages))
        }
    }

    override fun deactivate(id: String): Result<Unit> {
        return DatabaseManager.withConnection(dataSource) { conn ->
            val sql = "UPDATE users SET state = 'INACTIVE', updated_at = now() WHERE id = ?::uuid AND state = 'ACTIVE'"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id)
                val affected = stmt.executeUpdate()
                if (affected > 0) Result.Success(Unit) else Result.Failure(AppError.NotFound("Usuario no encontrado"))
            }
        }
    }
}
