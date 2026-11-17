package edu.investigacion.application

import edu.investigacion.domain.*
import java.util.UUID

class EvidenceService(
    private val evidenceRepository: EvidenceRepository,
    private val caseRepository: CaseRepository,
    private val suspectRepository: SuspectRepository
) {
    fun getById(id: String, currentUserId: String, currentUserRole: Role): Result<Evidence> {
        val err = Validation.validateUUID(id)
        if (err.isNotEmpty()) return Result.Failure(AppError.Validation("ID inválido", err))

        val e = evidenceRepository.findById(id) ?: return Result.Failure(AppError.NotFound("Evidencia no encontrada"))
        val c = caseRepository.findById(e.caseId) ?: return Result.Failure(AppError.NotFound("Caso no encontrado"))

        val accessErr = AccessPolicy.checkCaseAccess(currentUserId, currentUserRole, c.ownerId)
        if (accessErr != null) return Result.Failure(accessErr)

        return Result.Success(e)
    }

    fun list(
        page: Int,
        limit: Int,
        caseId: String?,
        suspectId: String?,
        type: String?,
        query: String?,
        currentUserId: String,
        currentUserRole: Role
    ): Result<Page<Evidence>> {
        val safePage = if (page < 1) 1 else page
        val safeLimit = when {
            limit < 1 -> 10
            limit > 50 -> 50
            else -> limit
        }

        val evidenceType = if (!type.isNullOrBlank()) {
            try { EvidenceType.valueOf(type) } catch (_: Exception) { return Result.Failure(AppError.Validation("Tipo inválido", listOf(FieldError("type", "Tipo no permitido")))) }
        } else null

        // Si es USER, solo puede ver evidencias de sus propios casos
        val allowedCaseIds = if (currentUserRole == Role.ADMIN) {
            null
        } else {
            val userCases = caseRepository.findAll(1, 10000, null, null, null, null, currentUserId)
            userCases.data.map { it.id }
        }

        val resultPage = evidenceRepository.findAll(safePage, safeLimit, caseId, suspectId, evidenceType, query, allowedCaseIds)
        return Result.Success(resultPage)
    }

    fun create(
        caseId: String?,
        suspectId: String?,
        title: String?,
        description: String?,
        type: String?,
        collectedAt: String?,
        relevance: Int?,
        imageUrl: String?,
        currentUserId: String,
        currentUserRole: Role
    ): Result<Evidence> {
        val errors = Validation.validateEvidence(caseId, suspectId, title, description, type, collectedAt, relevance, imageUrl)
        if (errors.isNotEmpty()) return Result.Failure(AppError.Validation("Error de validación", errors))

        val c = caseRepository.findById(caseId!!) ?: return Result.Failure(AppError.NotFound("Caso no encontrado"))
        val accessErr = AccessPolicy.checkCaseAccess(currentUserId, currentUserRole, c.ownerId)
        if (accessErr != null) return Result.Failure(accessErr)

        // Regla 3: Si el caso está CLOSED no se pueden crear sospechosos ni evidencias (409)
        if (c.status == CaseStatus.CLOSED) {
            return Result.Failure(AppError.Conflict("No se pueden agregar evidencias a un caso cerrado"))
        }

        // Regla 4: El suspectId de una evidencia debe pertenecer al mismo caseId (400)
        if (suspectId != null) {
            val suspect = suspectRepository.findById(suspectId)
            if (suspect == null || suspect.caseId != caseId) {
                return Result.Failure(AppError.Validation("Validación fallida", listOf(FieldError("suspectId", "El sospechoso debe pertenecer al mismo caso"))))
            }
        }

        val newEvidence = Evidence(
            id = UUID.randomUUID().toString(),
            caseId = caseId,
            suspectId = suspectId,
            title = title!!.trim(),
            description = description!!.trim(),
            type = EvidenceType.valueOf(type!!),
            collectedAt = collectedAt!!.trim(),
            relevance = relevance!!,
            imageUrl = imageUrl?.trim()?.ifBlank { null },
            state = RecordState.ACTIVE,
            createdAt = "",
            updatedAt = ""
        )

        return evidenceRepository.create(newEvidence)
    }

    fun update(
        id: String,
        suspectId: String?,
        title: String?,
        description: String?,
        type: String?,
        collectedAt: String?,
        relevance: Int?,
        imageUrl: String?,
        currentUserId: String,
        currentUserRole: Role
    ): Result<Evidence> {
        val uuidErr = Validation.validateUUID(id)
        if (uuidErr.isNotEmpty()) return Result.Failure(AppError.Validation("ID inválido", uuidErr))

        val existing = evidenceRepository.findById(id) ?: return Result.Failure(AppError.NotFound("Evidencia no encontrada"))
        val c = caseRepository.findById(existing.caseId) ?: return Result.Failure(AppError.NotFound("Caso no encontrado"))
        val accessErr = AccessPolicy.checkCaseAccess(currentUserId, currentUserRole, c.ownerId)
        if (accessErr != null) return Result.Failure(accessErr)

        val errors = Validation.validateEvidence(existing.caseId, suspectId, title, description, type, collectedAt, relevance, imageUrl)
        if (errors.isNotEmpty()) return Result.Failure(AppError.Validation("Error de validación", errors))

        // Regla 4: El suspectId de una evidencia debe pertenecer al mismo caseId (400)
        if (suspectId != null) {
            val suspect = suspectRepository.findById(suspectId)
            if (suspect == null || suspect.caseId != existing.caseId) {
                return Result.Failure(AppError.Validation("Validación fallida", listOf(FieldError("suspectId", "El sospechoso debe pertenecer al mismo caso"))))
            }
        }

        return evidenceRepository.update(
            id = id,
            suspectId = suspectId,
            title = title!!.trim(),
            description = description!!.trim(),
            type = EvidenceType.valueOf(type!!),
            collectedAt = collectedAt!!.trim(),
            relevance = relevance!!,
            imageUrl = imageUrl?.trim()?.ifBlank { null }
        )
    }

    fun delete(id: String, currentUserId: String, currentUserRole: Role): Result<Unit> {
        val uuidErr = Validation.validateUUID(id)
        if (uuidErr.isNotEmpty()) return Result.Failure(AppError.Validation("ID inválido", uuidErr))

        val existing = evidenceRepository.findById(id) ?: return Result.Failure(AppError.NotFound("Evidencia no encontrada"))
        val c = caseRepository.findById(existing.caseId) ?: return Result.Failure(AppError.NotFound("Caso no encontrado"))
        val accessErr = AccessPolicy.checkCaseAccess(currentUserId, currentUserRole, c.ownerId)
        if (accessErr != null) return Result.Failure(accessErr)

        return evidenceRepository.delete(id)
    }
}
