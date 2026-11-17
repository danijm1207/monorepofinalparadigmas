package edu.investigacion.infrastructure

import edu.investigacion.domain.*
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

class JdbcCaseRepository(private val dataSource: DataSource) : CaseRepository {

    private fun mapCase(rs: ResultSet): Case {
        return Case(
            id = rs.getString("id"),
            caseNumber = rs.getInt("case_number"),
            title = rs.getString("title"),
            description = rs.getString("description"),
            location = rs.getString("location"),
            incidentDate = JsonTreeService.formatInstant(rs.getTimestamp("incident_date").toInstant()),
            status = CaseStatus.valueOf(rs.getString("status")),
            imageUrl = rs.getString("image_url"),
            state = RecordState.valueOf(rs.getString("state")),
            ownerId = rs.getString("owner_id"),
            createdAt = JsonTreeService.formatInstant(rs.getTimestamp("created_at").toInstant()),
            updatedAt = JsonTreeService.formatInstant(rs.getTimestamp("updated_at").toInstant())
        )
    }

    override fun findById(id: String): Case? {
        return DatabaseManager.withConnection(dataSource) { conn ->
            val sql = "SELECT * FROM cases WHERE id = ?::uuid AND state = 'ACTIVE'"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id)
                val rs = stmt.executeQuery()
                if (rs.next()) mapCase(rs) else null
            }
        }
    }

    override fun create(case: Case): Result<Case> {
        return DatabaseManager.withConnection(dataSource) { conn ->
            val sql = """
                INSERT INTO cases (id, title, description, location, incident_date, status, image_url, state, owner_id)
                VALUES (?::uuid, ?, ?, ?, ?::timestamptz, ?, ?, ?, ?::uuid)
                RETURNING case_number, created_at, updated_at
            """.trimIndent()
            try {
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, case.id)
                    stmt.setString(2, case.title)
                    stmt.setString(3, case.description)
                    stmt.setString(4, case.location)
                    stmt.setString(5, case.incidentDate)
                    stmt.setString(6, case.status.name)
                    stmt.setString(7, case.imageUrl)
                    stmt.setString(8, case.state.name)
                    stmt.setString(9, case.ownerId)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        val created = case.copy(
                            caseNumber = rs.getInt("case_number"),
                            createdAt = JsonTreeService.formatInstant(rs.getTimestamp("created_at").toInstant()),
                            updatedAt = JsonTreeService.formatInstant(rs.getTimestamp("updated_at").toInstant())
                        )
                        Result.Success(created)
                    } else {
                        Result.Failure(AppError.Internal("No se pudo crear el caso"))
                    }
                }
            } catch (e: Exception) {
                Result.Failure(AppError.Internal(e.message ?: "Error al crear caso"))
            }
        }
    }

    override fun update(
        id: String,
        title: String,
        description: String,
        location: String,
        incidentDate: String,
        status: CaseStatus,
        imageUrl: String?
    ): Result<Case> {
        return DatabaseManager.withConnection(dataSource) { conn ->
            val sql = """
                UPDATE cases 
                SET title = ?, description = ?, location = ?, incident_date = ?::timestamptz, status = ?, image_url = ?, updated_at = now()
                WHERE id = ?::uuid AND state = 'ACTIVE'
                RETURNING *
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, title)
                stmt.setString(2, description)
                stmt.setString(3, location)
                stmt.setString(4, incidentDate)
                stmt.setString(5, status.name)
                stmt.setString(6, imageUrl)
                stmt.setString(7, id)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    Result.Success(mapCase(rs))
                } else {
                    Result.Failure(AppError.NotFound("Caso no encontrado"))
                }
            }
        }
    }

    override fun delete(id: String): Result<Unit> {
        return DatabaseManager.withTransaction(dataSource) { conn ->
            // 1. Desactivar el caso
            val deactivateCaseSql = "UPDATE cases SET state = 'INACTIVE', updated_at = now() WHERE id = ?::uuid AND state = 'ACTIVE'"
            val affected = conn.prepareStatement(deactivateCaseSql).use { stmt ->
                stmt.setString(1, id)
                stmt.executeUpdate()
            }
            if (affected == 0) {
                return@withTransaction Result.Failure(AppError.NotFound("Caso no encontrado"))
            }

            // 2. Desactivar en cascada sospechosos asociados
            val deactivateSuspectsSql = "UPDATE suspects SET state = 'INACTIVE', updated_at = now() WHERE case_id = ?::uuid AND state = 'ACTIVE'"
            conn.prepareStatement(deactivateSuspectsSql).use { stmt ->
                stmt.setString(1, id)
                stmt.executeUpdate()
            }

            // 3. Desactivar en cascada evidencias asociadas
            val deactivateEvidencesSql = "UPDATE evidences SET state = 'INACTIVE', updated_at = now() WHERE case_id = ?::uuid AND state = 'ACTIVE'"
            conn.prepareStatement(deactivateEvidencesSql).use { stmt ->
                stmt.setString(1, id)
                stmt.executeUpdate()
            }

            Result.Success(Unit)
        }
    }

    override fun findAll(
        page: Int,
        limit: Int,
        query: String?,
        status: CaseStatus?,
        from: String?,
        to: String?,
        ownerId: String?
    ): Page<Case> {
        return DatabaseManager.withConnection(dataSource) { conn ->
            val whereClauses = mutableListOf("state = 'ACTIVE'")
            val params = mutableListOf<Any>()

            if (!query.isNullOrBlank()) {
                whereClauses.add("(title ILIKE ? OR location ILIKE ?)")
                params.add("%${query.trim()}%")
                params.add("%${query.trim()}%")
            }
            if (status != null) {
                whereClauses.add("status = ?")
                params.add(status.name)
            }
            if (!from.isNullOrBlank()) {
                whereClauses.add("incident_date >= ?::timestamptz")
                params.add(from)
            }
            if (!to.isNullOrBlank()) {
                whereClauses.add("incident_date <= ?::timestamptz")
                params.add(to)
            }
            if (!ownerId.isNullOrBlank()) {
                whereClauses.add("owner_id = ?::uuid")
                params.add(ownerId)
            }

            val whereSql = "WHERE " + whereClauses.joinToString(" AND ")
            val countSql = "SELECT COUNT(*) FROM cases $whereSql"

            val total = conn.prepareStatement(countSql).use { stmt ->
                params.forEachIndexed { i, p -> stmt.setObject(i + 1, p) }
                val rs = stmt.executeQuery()
                if (rs.next()) rs.getLong(1) else 0L
            }

            val offset = (page - 1) * limit
            val selectSql = """
                SELECT * FROM cases 
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
                val list = mutableListOf<Case>()
                while (rs.next()) {
                    list.add(mapCase(rs))
                }
                list
            }

            val totalPages = if (total == 0L) 0 else ((total + limit - 1) / limit).toInt()
            Page(data, Pagination(total, page, limit, totalPages))
        }
    }
}
