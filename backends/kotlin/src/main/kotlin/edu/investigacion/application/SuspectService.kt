package edu.investigacion.application

import edu.investigacion.domain.*
import java.util.UUID

class SuspectService(
    private val suspectRepository: SuspectRepository,
    private val caseRepository: CaseRepository
) {
    fun getById(id: String, currentUserId: String, currentUserRole: Role): Result<Suspect> {
        val err = Validation.validateUUID(id)
        if (err.isNotEmpty()) return Result.Failure(AppError.Validation("ID inválido", err))

        val s = suspectRepository.findById(id) ?: return Result.Failure(AppError.NotFound("Sospechoso no encontrado"))
        val c = caseRepository.findById(s.caseId) ?: return Result.Failure(AppError.NotFound("Caso no encontrado"))

        val accessErr = AccessPolicy.checkCaseAccess(currentUserId, currentUserRole, c.ownerId)
        if (accessErr != null) return Result.Failure(accessErr)

        return Result.Success(s)
    }

    fun list(
        page: Int,
        limit: Int,
        caseId: String?,
        status: String?,
        query: String?,
        currentUserId: String,
        currentUserRole: Role
    ): Result<Page<Suspect>> {
        val safePage = if (page < 1) 1 else page
        val safeLimit = when {
            limit < 1 -> 10
            limit > 50 -> 50
            else -> limit
        }

        val suspectStatus = if (!status.isNullOrBlank()) {
            try { SuspectStatus.valueOf(status) } catch (_: Exception) { return Result.Failure(AppError.Validation("Estado inválido", listOf(FieldError("status", "Estado no permitido")))) }
        } else null

        // Si es USER, solo puede ver sospechosos de casos suyos
        val allowedCaseIds = if (currentUserRole == Role.ADMIN) {
            null
        } else {
            val userCases = caseRepository.findAll(1, 10000, null, null, null, null, currentUserId)
            userCases.data.map { it.id }
        }

        val resultPage = suspectRepository.findAll(safePage, safeLimit, caseId, suspectStatus, query, allowedCaseIds)
        return Result.Success(resultPage)
    }

    fun create(
        caseId: String?,
        fullName: String?,
        alias: String?,
        description: String?,
        suspicionLevel: Int?,
        status: String?,
        photoUrl: String?,
        currentUserId: String,
        currentUserRole: Role
    ): Result<Suspect> {
        val errors = Validation.validateSuspect(caseId, fullName, alias, description, suspicionLevel, status, photoUrl)
        if (errors.isNotEmpty()) return Result.Failure(AppError.Validation("Error de validación", errors))

        val c = caseRepository.findById(caseId!!) ?: return Result.Failure(AppError.NotFound("Caso no encontrado"))
        val accessErr = AccessPolicy.checkCaseAccess(currentUserId, currentUserRole, c.ownerId)
        if (accessErr != null) return Result.Failure(accessErr)

        // Regla 3: Si el caso está CLOSED no se pueden crear sospechosos ni evidencias (409)
        if (c.status == CaseStatus.CLOSED) {
            return Result.Failure(AppError.Conflict("No se pueden agregar sospechosos a un caso cerrado"))
        }

        val suspectStatus = if (status != null) SuspectStatus.valueOf(status) else SuspectStatus.PERSON_OF_INTEREST
        val newSuspect = Suspect(
            id = UUID.randomUUID().toString(),
            caseId = caseId,
            fullName = fullName!!.trim(),
            alias = alias?.trim()?.ifBlank { null },
            description = description?.trim()?.ifBlank { null },
            suspicionLevel = suspicionLevel ?: 0,
            status = suspectStatus,
            photoUrl = photoUrl?.trim()?.ifBlank { null },
            state = RecordState.ACTIVE,
            createdAt = "",
            updatedAt = ""
        )

        return suspectRepository.create(newSuspect)
    }

    fun update(
        id: String,
        fullName: String?,
        alias: String?,
        description: String?,
        suspicionLevel: Int?,
        status: String?,
        photoUrl: String?,
        currentUserId: String,
        currentUserRole: Role
    ): Result<Suspect> {
        val uuidErr = Validation.validateUUID(id)
        if (uuidErr.isNotEmpty()) return Result.Failure(AppError.Validation("ID inválido", uuidErr))

        val existing = suspectRepository.findById(id) ?: return Result.Failure(AppError.NotFound("Sospechoso no encontrado"))
        val c = caseRepository.findById(existing.caseId) ?: return Result.Failure(AppError.NotFound("Caso no encontrado"))
        val accessErr = AccessPolicy.checkCaseAccess(currentUserId, currentUserRole, c.ownerId)
        if (accessErr != null) return Result.Failure(accessErr)

        val errors = Validation.validateSuspect(existing.caseId, fullName, alias, description, suspicionLevel, status, photoUrl)
        if (errors.isNotEmpty()) return Result.Failure(AppError.Validation("Error de validación", errors))

        val suspectStatus = if (status != null) SuspectStatus.valueOf(status) else existing.status
        return suspectRepository.update(
            id = id,
            fullName = fullName!!.trim(),
            alias = alias?.trim()?.ifBlank { null },
            description = description?.trim()?.ifBlank { null },
            suspicionLevel = suspicionLevel ?: existing.suspicionLevel,
            status = suspectStatus,
            photoUrl = photoUrl?.trim()?.ifBlank { null }
        )
    }

    fun delete(id: String, currentUserId: String, currentUserRole: Role): Result<Unit> {
        val uuidErr = Validation.validateUUID(id)
        if (uuidErr.isNotEmpty()) return Result.Failure(AppError.Validation("ID inválido", uuidErr))

        val existing = suspectRepository.findById(id) ?: return Result.Failure(AppError.NotFound("Sospechoso no encontrado"))
        val c = caseRepository.findById(existing.caseId) ?: return Result.Failure(AppError.NotFound("Caso no encontrado"))
        val accessErr = AccessPolicy.checkCaseAccess(currentUserId, currentUserRole, c.ownerId)
        if (accessErr != null) return Result.Failure(accessErr)

        return suspectRepository.delete(id)
    }
}
