package edu.investigacion.application

import edu.investigacion.domain.*
import java.util.concurrent.ConcurrentHashMap

class InMemoryUserRepository : UserRepository {
    val users = ConcurrentHashMap<String, User>()

    override fun findById(id: String): User? = users[id]?.takeIf { it.state == RecordState.ACTIVE }

    override fun findByEmail(email: String): User? =
        users.values.find { it.email.equals(email, ignoreCase = true) }

    override fun create(user: User): Result<User> {
        if (users.values.any { it.email.equals(user.email, ignoreCase = true) }) {
            return Result.Failure(AppError.Conflict("El correo electrónico ya está registrado"))
        }
        val created = user.copy(
            createdAt = "2026-10-01T00:00:00.000Z",
            updatedAt = "2026-10-01T00:00:00.000Z"
        )
        users[user.id] = created
        return Result.Success(created)
    }

    override fun findAll(page: Int, limit: Int, query: String?, role: Role?): Page<UserPublic> {
        val filtered = users.values
            .filter { it.state == RecordState.ACTIVE }
            .filter { query == null || it.name.contains(query, ignoreCase = true) || it.email.contains(query, ignoreCase = true) }
            .filter { role == null || it.role == role }
            .sortedByDescending { it.createdAt }

        val total = filtered.size.toLong()
        val totalPages = if (total == 0L) 0 else ((total + limit - 1) / limit).toInt()
        val offset = (page - 1) * limit
        val data = filtered.drop(offset).take(limit).map {
            UserPublic(it.id, it.name, it.email, it.role, it.createdAt)
        }
        return Page(data, Pagination(total, page, limit, totalPages))
    }

    override fun deactivate(id: String): Result<Unit> {
        val user = users[id] ?: return Result.Failure(AppError.NotFound("Usuario no encontrado"))
        if (user.state == RecordState.INACTIVE) return Result.Failure(AppError.NotFound("Usuario no encontrado"))
        users[id] = user.copy(state = RecordState.INACTIVE)
        return Result.Success(Unit)
    }
}

class InMemoryCaseRepository : CaseRepository {
    val cases = ConcurrentHashMap<String, Case>()

    override fun findById(id: String): Case? = cases[id]?.takeIf { it.state == RecordState.ACTIVE }

    override fun create(case: Case): Result<Case> {
        val created = case.copy(
            caseNumber = cases.size + 1,
            createdAt = "2026-10-01T00:00:00.000Z",
            updatedAt = "2026-10-01T00:00:00.000Z"
        )
        cases[case.id] = created
        return Result.Success(created)
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
        val existing = cases[id] ?: return Result.Failure(AppError.NotFound("Caso no encontrado"))
        if (existing.state == RecordState.INACTIVE) return Result.Failure(AppError.NotFound("Caso no encontrado"))
        val updated = existing.copy(
            title = title,
            description = description,
            location = location,
            incidentDate = incidentDate,
            status = status,
            imageUrl = imageUrl,
            updatedAt = "2026-10-01T00:00:00.000Z"
        )
        cases[id] = updated
        return Result.Success(updated)
    }

    override fun delete(id: String): Result<Unit> {
        val existing = cases[id] ?: return Result.Failure(AppError.NotFound("Caso no encontrado"))
        if (existing.state == RecordState.INACTIVE) return Result.Failure(AppError.NotFound("Caso no encontrado"))
        cases[id] = existing.copy(state = RecordState.INACTIVE)
        return Result.Success(Unit)
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
        val filtered = cases.values
            .filter { it.state == RecordState.ACTIVE }
            .filter { query == null || it.title.contains(query, ignoreCase = true) || it.location.contains(query, ignoreCase = true) }
            .filter { status == null || it.status == status }
            .filter { ownerId == null || it.ownerId == ownerId }
            .sortedByDescending { it.createdAt }

        val total = filtered.size.toLong()
        val totalPages = if (total == 0L) 0 else ((total + limit - 1) / limit).toInt()
        val offset = (page - 1) * limit
        val data = filtered.drop(offset).take(limit)
        return Page(data, Pagination(total, page, limit, totalPages))
    }
}

class InMemorySuspectRepository : SuspectRepository {
    val suspects = ConcurrentHashMap<String, Suspect>()

    override fun findById(id: String): Suspect? = suspects[id]?.takeIf { it.state == RecordState.ACTIVE }

    override fun create(suspect: Suspect): Result<Suspect> {
        val created = suspect.copy(
            createdAt = "2026-10-01T00:00:00.000Z",
            updatedAt = "2026-10-01T00:00:00.000Z"
        )
        suspects[suspect.id] = created
        return Result.Success(created)
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
        val existing = suspects[id] ?: return Result.Failure(AppError.NotFound("Sospechoso no encontrado"))
        if (existing.state == RecordState.INACTIVE) return Result.Failure(AppError.NotFound("Sospechoso no encontrado"))
        val updated = existing.copy(
            fullName = fullName,
            alias = alias,
            description = description,
            suspicionLevel = suspicionLevel,
            status = status,
            photoUrl = photoUrl,
            updatedAt = "2026-10-01T00:00:00.000Z"
        )
        suspects[id] = updated
        return Result.Success(updated)
    }

    override fun delete(id: String): Result<Unit> {
        val existing = suspects[id] ?: return Result.Failure(AppError.NotFound("Sospechoso no encontrado"))
        if (existing.state == RecordState.INACTIVE) return Result.Failure(AppError.NotFound("Sospechoso no encontrado"))
        suspects[id] = existing.copy(state = RecordState.INACTIVE)
        return Result.Success(Unit)
    }

    override fun findAll(
        page: Int,
        limit: Int,
        caseId: String?,
        status: SuspectStatus?,
        query: String?,
        allowedCaseIds: List<String>?
    ): Page<Suspect> {
        val filtered = suspects.values
            .filter { it.state == RecordState.ACTIVE }
            .filter { caseId == null || it.caseId == caseId }
            .filter { status == null || it.status == status }
            .filter { query == null || it.fullName.contains(query, ignoreCase = true) || (it.alias?.contains(query, ignoreCase = true) == true) }
            .filter { allowedCaseIds == null || allowedCaseIds.contains(it.caseId) }
            .sortedByDescending { it.createdAt }

        val total = filtered.size.toLong()
        val totalPages = if (total == 0L) 0 else ((total + limit - 1) / limit).toInt()
        val offset = (page - 1) * limit
        val data = filtered.drop(offset).take(limit)
        return Page(data, Pagination(total, page, limit, totalPages))
    }
}

class InMemoryEvidenceRepository : EvidenceRepository {
    val evidences = ConcurrentHashMap<String, Evidence>()

    override fun findById(id: String): Evidence? = evidences[id]?.takeIf { it.state == RecordState.ACTIVE }

    override fun create(evidence: Evidence): Result<Evidence> {
        val created = evidence.copy(
            createdAt = "2026-10-01T00:00:00.000Z",
            updatedAt = "2026-10-01T00:00:00.000Z"
        )
        evidences[evidence.id] = created
        return Result.Success(created)
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
        val existing = evidences[id] ?: return Result.Failure(AppError.NotFound("Evidencia no encontrada"))
        if (existing.state == RecordState.INACTIVE) return Result.Failure(AppError.NotFound("Evidencia no encontrada"))
        val updated = existing.copy(
            suspectId = suspectId,
            title = title,
            description = description,
            type = type,
            collectedAt = collectedAt,
            relevance = relevance,
            imageUrl = imageUrl,
            updatedAt = "2026-10-01T00:00:00.000Z"
        )
        evidences[id] = updated
        return Result.Success(updated)
    }

    override fun delete(id: String): Result<Unit> {
        val existing = evidences[id] ?: return Result.Failure(AppError.NotFound("Evidencia no encontrada"))
        if (existing.state == RecordState.INACTIVE) return Result.Failure(AppError.NotFound("Evidencia no encontrada"))
        evidences[id] = existing.copy(state = RecordState.INACTIVE)
        return Result.Success(Unit)
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
        val filtered = evidences.values
            .filter { it.state == RecordState.ACTIVE }
            .filter { caseId == null || it.caseId == caseId }
            .filter { suspectId == null || it.suspectId == suspectId }
            .filter { type == null || it.type == type }
            .filter { query == null || it.title.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }
            .filter { allowedCaseIds == null || allowedCaseIds.contains(it.caseId) }
            .sortedByDescending { it.createdAt }

        val total = filtered.size.toLong()
        val totalPages = if (total == 0L) 0 else ((total + limit - 1) / limit).toInt()
        val offset = (page - 1) * limit
        val data = filtered.drop(offset).take(limit)
        return Page(data, Pagination(total, page, limit, totalPages))
    }
}
