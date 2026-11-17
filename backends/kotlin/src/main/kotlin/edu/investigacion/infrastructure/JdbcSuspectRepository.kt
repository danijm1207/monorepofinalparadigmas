package edu.investigacion.infrastructure

import edu.investigacion.domain.*
import java.sql.ResultSet
import javax.sql.DataSource

class JdbcSuspectRepository(private val dataSource: DataSource) : SuspectRepository {

    private fun mapSuspect(rs: ResultSet): Suspect {
        return Suspect(
            id = rs.getString("id"),
            caseId = rs.getString("case_id"),
            fullName = rs.getString("full_name"),
            alias = rs.getString("alias"),
            description = rs.getString("description"),
            suspicionLevel = rs.getInt("suspicion_level"),
            status = SuspectStatus.valueOf(rs.getString("status")),
            photoUrl = rs.getString("photo_url"),
            state = RecordState.valueOf(rs.getString("state")),
            createdAt = JsonTreeService.formatInstant(rs.getTimestamp("created_at").toInstant()),
            updatedAt = JsonTreeService.formatInstant(rs.getTimestamp("updated_at").toInstant())
        )
    }

    override fun findById(id: String): Suspect? {
        return DatabaseManager.withConnection(dataSource) { conn ->
            val sql = "SELECT * FROM suspects WHERE id = ?::uuid AND state = 'ACTIVE'"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id)
                val rs = stmt.executeQuery()
                if (rs.next()) mapSuspect(rs) else null
            }
        }
    }

    override fun create(suspect: Suspect): Result<Suspect> {
        return DatabaseManager.withConnection(dataSource) { conn ->
            val sql = """
                INSERT INTO suspects (id, case_id, full_name, alias, description, suspicion_level, status, photo_url, state)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?)
                RETURNING created_at, updated_at
            """.trimIndent()
            try {
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, suspect.id)
                    stmt.setString(2, suspect.caseId)
                    stmt.setString(3, suspect.fullName)
                    stmt.setString(4, suspect.alias)
                    stmt.setString(5, suspect.description)
                    stmt.setInt(6, suspect.suspicionLevel)
                    stmt.setString(7, suspect.status.name)
                    stmt.setString(8, suspect.photoUrl)
                    stmt.setString(9, suspect.state.name)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        val created = suspect.copy(
                            createdAt = JsonTreeService.formatInstant(rs.getTimestamp("created_at").toInstant()),
                            updatedAt = JsonTreeService.formatInstant(rs.getTimestamp("updated_at").toInstant())
                        )
                        Result.Success(created)
                    } else {
                        Result.Failure(AppError.Internal("No se pudo crear el sospechoso"))
                    }
                }
            } catch (e: Exception) {
                Result.Failure(AppError.Internal(e.message ?: "Error al crear sospechoso"))
            }
        }
    }

    override fun update(
        id: String,
        fullName: String,
        alias: String?,
        description: String?,
        suspicionLevel: Int,
        status: SuspectStatus,
        photoUrl: String?
    ): Result<Suspect> {
        return DatabaseManager.withConnection(dataSource) { conn ->
            val sql = """
                UPDATE suspects 
                SET full_name = ?, alias = ?, description = ?, suspicion_level = ?, status = ?, photo_url = ?, updated_at = now()
                WHERE id = ?::uuid AND state = 'ACTIVE'
                RETURNING *
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, fullName)
                stmt.setString(2, alias)
                stmt.setString(3, description)
                stmt.setInt(4, suspicionLevel)
                stmt.setString(5, status.name)
                stmt.setString(6, photoUrl)
                stmt.setString(7, id)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    Result.Success(mapSuspect(rs))
                } else {
                    Result.Failure(AppError.NotFound("Sospechoso no encontrado"))
                }
            }
        }
    }

    override fun delete(id: String): Result<Unit> {
        return DatabaseManager.withTransaction(dataSource) { conn ->
            // 1. Desactivar el sospechoso
            val deactivateSql = "UPDATE suspects SET state = 'INACTIVE', updated_at = now() WHERE id = ?::uuid AND state = 'ACTIVE'"
            val affected = conn.prepareStatement(deactivateSql).use { stmt ->
                stmt.setString(1, id)
                stmt.executeUpdate()
            }
            if (affected == 0) {
                return@withTransaction Result.Failure(AppError.NotFound("Sospechoso no encontrado"))
            }

            // 2. Desvincular evidencias (suspect_id = null)
            val unlinkEvidencesSql = "UPDATE evidences SET suspect_id = NULL, updated_at = now() WHERE suspect_id = ?::uuid"
            conn.prepareStatement(unlinkEvidencesSql).use { stmt ->
                stmt.setString(1, id)
                stmt.executeUpdate()
            }

            Result.Success(Unit)
        }
    }

    override fun findAll(
        page: Int,
        limit: Int,
        caseId: String?,
        status: SuspectStatus?,
        query: String?,
        allowedCaseIds: List<String>?
    ): Page<Suspect> {
        return DatabaseManager.withConnection(dataSource) { conn ->
            val whereClauses = mutableListOf("state = 'ACTIVE'")
            val params = mutableListOf<Any>()

            if (!caseId.isNullOrBlank()) {
                whereClauses.add("case_id = ?::uuid")
                params.add(caseId)
            }
            if (status != null) {
                whereClauses.add("status = ?")
                params.add(status.name)
            }
            if (!query.isNullOrBlank()) {
                whereClauses.add("(full_name ILIKE ? OR alias ILIKE ?)")
                params.add("%${query.trim()}%")
                params.add("%${query.trim()}%")
            }
            if (allowedCaseIds != null) {
                if (allowedCaseIds.isEmpty()) {
                    whereClauses.add("1 = 0")
                } else {
                    val inSql = allowedCaseIds.joinToString(",") { "?::uuid" }
                    whereClauses.add("case_id IN ($inSql)")
                    params.addAll(allowedCaseIds)
                }
            }

            val whereSql = "WHERE " + whereClauses.joinToString(" AND ")
            val countSql = "SELECT COUNT(*) FROM suspects $whereSql"

            val total = conn.prepareStatement(countSql).use { stmt ->
                params.forEachIndexed { i, p -> stmt.setObject(i + 1, p) }
                val rs = stmt.executeQuery()
                if (rs.next()) rs.getLong(1) else 0L
            }

            val offset = (page - 1) * limit
            val selectSql = """
                SELECT * FROM suspects 
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
                val list = mutableListOf<Suspect>()
                while (rs.next()) {
                    list.add(mapSuspect(rs))
                }
                list
            }

            val totalPages = if (total == 0L) 0 else ((total + limit - 1) / limit).toInt()
            Page(data, Pagination(total, page, limit, totalPages))
        }
    }
}
