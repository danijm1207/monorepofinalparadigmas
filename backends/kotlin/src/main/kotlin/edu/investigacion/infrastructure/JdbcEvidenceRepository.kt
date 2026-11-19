package edu.investigacion.infrastructure

import edu.investigacion.domain.*
import java.sql.ResultSet
import javax.sql.DataSource

class JdbcEvidenceRepository(private val dataSource: DataSource) : EvidenceRepository {

    private fun mapEvidence(rs: ResultSet): Evidence {
        return Evidence(
            id = rs.getString("id"),
            caseId = rs.getString("case_id"),
            suspectId = rs.getString("suspect_id"),
            title = rs.getString("title"),
            description = rs.getString("description"),
            type = EvidenceType.valueOf(rs.getString("type")),
            collectedAt = JsonTreeService.formatInstant(rs.getTimestamp("collected_at").toInstant()),
            relevance = rs.getInt("relevance"),
            imageUrl = rs.getString("image_url"),
            state = RecordState.valueOf(rs.getString("state")),
            createdAt = JsonTreeService.formatInstant(rs.getTimestamp("created_at").toInstant()),
            updatedAt = JsonTreeService.formatInstant(rs.getTimestamp("updated_at").toInstant())
        )
    }

    override fun findById(id: String): Evidence? {
        return DatabaseManager.withConnection(dataSource) { conn ->
            val sql = "SELECT * FROM evidences WHERE id = ?::uuid AND state = 'ACTIVE'"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id)
                val rs = stmt.executeQuery()
                if (rs.next()) mapEvidence(rs) else null
            }
        }
    }

    override fun create(evidence: Evidence): Result<Evidence> {
        return DatabaseManager.withConnection(dataSource) { conn ->
            val sql = """
                INSERT INTO evidences (id, case_id, suspect_id, title, description, type, collected_at, relevance, image_url, state)
                VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?::timestamptz, ?, ?, ?)
                RETURNING created_at, updated_at
            """.trimIndent()
            try {
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, evidence.id)
                    stmt.setString(2, evidence.caseId)
                    if (evidence.suspectId != null) stmt.setString(3, evidence.suspectId) else stmt.setNull(3, java.sql.Types.OTHER)
                    stmt.setString(4, evidence.title)
                    stmt.setString(5, evidence.description)
                    stmt.setString(6, evidence.type.name)
                    stmt.setString(7, evidence.collectedAt)
                    stmt.setInt(8, evidence.relevance)
                    stmt.setString(9, evidence.imageUrl)
                    stmt.setString(10, evidence.state.name)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        val created = evidence.copy(
                            createdAt = JsonTreeService.formatInstant(rs.getTimestamp("created_at").toInstant()),
                            updatedAt = JsonTreeService.formatInstant(rs.getTimestamp("updated_at").toInstant())
                        )
                        Result.Success(created)
                    } else {
                        Result.Failure(AppError.Internal("No se pudo crear la evidencia"))
                    }
                }
            } catch (e: Exception) {
                Result.Failure(AppError.Internal(e.message ?: "Error al crear evidencia"))
            }
        }
    }

    override fun update(
        id: String,
        suspectId: String?,
        title: String,
        description: String,
        type: EvidenceType,
        collectedAt: String,
        relevance: Int,
        imageUrl: String?
    ): Result<Evidence> {
        return DatabaseManager.withConnection(dataSource) { conn ->
            val sql = """
                UPDATE evidences 
                SET suspect_id = ?::uuid, title = ?, description = ?, type = ?, collected_at = ?::timestamptz, relevance = ?, image_url = ?, updated_at = now()
                WHERE id = ?::uuid AND state = 'ACTIVE'
                RETURNING *
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                if (suspectId != null) stmt.setString(1, suspectId) else stmt.setNull(1, java.sql.Types.OTHER)
                stmt.setString(2, title)
                stmt.setString(3, description)
                stmt.setString(4, type.name)
                stmt.setString(5, collectedAt)
                stmt.setInt(6, relevance)
                stmt.setString(7, imageUrl)
                stmt.setString(8, id)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    Result.Success(mapEvidence(rs))
                } else {
                    Result.Failure(AppError.NotFound("Evidencia no encontrada"))
                }
            }
        }
    }

    override fun delete(id: String): Result<Unit> {
        return DatabaseManager.withConnection(dataSource) { conn ->
            val sql = "UPDATE evidences SET state = 'INACTIVE', updated_at = now() WHERE id = ?::uuid AND state = 'ACTIVE'"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id)
                val affected = stmt.executeUpdate()
                if (affected > 0) Result.Success(Unit) else Result.Failure(AppError.NotFound("Evidencia no encontrada"))
            }
        }
    }

    override fun findAll(
        page: Int,
        limit: Int,
        caseId: String?,
        suspectId: String?,
        type: EvidenceType?,
        query: String?,
        allowedCaseIds: List<String>?
    ): Page<Evidence> {
        return DatabaseManager.withConnection(dataSource) { conn ->
            val whereClauses = mutableListOf("state = 'ACTIVE'")
            val params = mutableListOf<Any>()

            if (!caseId.isNullOrBlank()) {
                whereClauses.add("case_id = ?::uuid")
                params.add(caseId)
            }
            if (!suspectId.isNullOrBlank()) {
                whereClauses.add("suspect_id = ?::uuid")
                params.add(suspectId)
            }
            if (type != null) {
                whereClauses.add("type = ?")
                params.add(type.name)
            }
            if (!query.isNullOrBlank()) {
                whereClauses.add("(title ILIKE ? OR description ILIKE ?)")
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
            val countSql = "SELECT COUNT(*) FROM evidences $whereSql"

            val total = conn.prepareStatement(countSql).use { stmt ->
                params.forEachIndexed { i, p -> stmt.setObject(i + 1, p) }
                val rs = stmt.executeQuery()
                if (rs.next()) rs.getLong(1) else 0L
            }

            val offset = (page - 1) * limit
            val selectSql = """
                SELECT * FROM evidences 
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
                val list = mutableListOf<Evidence>()
                while (rs.next()) {
                    list.add(mapEvidence(rs))
                }
                list
            }

            val totalPages = if (total == 0L) 0 else ((total + limit - 1) / limit).toInt()
            Page(data, Pagination(total, page, limit, totalPages))
        }
    }
}
