package edu.investigacion.application

import edu.investigacion.domain.*
import java.util.UUID

class CaseService(
    private val caseRepository: CaseRepository
) {
    fun getById(id: String, currentUserId: String, currentUserRole: Role): Result<Case> {
        val err = Validation.validateUUID(id)
        if (err.isNotEmpty()) return Result.Failure(AppError.Validation("ID inválido", err))

        val c = caseRepository.findById(id) ?: return Result.Failure(AppError.NotFound("Caso no encontrado"))
        val accessErr = AccessPolicy.checkCaseAccess(currentUserId, currentUserRole, c.ownerId)
        if (accessErr != null) return Result.Failure(accessErr)

        return Result.Success(c)
    }

    fun list(
        page: Int,
        limit: Int,
        query: String?,
        status: String?,
        from: String?,
        to: String?,
        ownerId: String?,
        currentUserId: String,
        currentUserRole: Role
    ): Result<Page<Case>> {
        val safePage = if (page < 1) 1 else page
        val safeLimit = when {
            limit < 1 -> 10
            limit > 50 -> 50
            else -> limit
        }

        val caseStatus = if (!status.isNullOrBlank()) {
            try { CaseStatus.valueOf(status) } catch (_: Exception) { return Result.Failure(AppError.Validation("Estado inválido", listOf(FieldError("status", "Estado no permitido")))) }
        } else null

        // Un USER solo ve sus casos; ADMIN puede filtrar por ownerId
        val effectiveOwnerId = if (currentUserRole == Role.ADMIN) ownerId else currentUserId

        val resultPage = caseRepository.findAll(safePage, safeLimit, query, caseStatus, from, to, effectiveOwnerId)
        return Result.Success(resultPage)
    }

    fun create(
        title: String?,
        description: String?,
        location: String?,
        incidentDate: String?,
        status: String?,
        imageUrl: String?,
        currentUserId: String
    ): Result<Case> {
        val errors = Validation.validateCase(title, description, location, incidentDate, status, imageUrl)
        if (errors.isNotEmpty()) return Result.Failure(AppError.Validation("Error de validación", errors))

        val caseStatus = if (status != null) CaseStatus.valueOf(status) else CaseStatus.OPEN
        val newCase = Case(
            id = UUID.randomUUID().toString(),
            caseNumber = 0,
            title = title!!.trim(),
            description = description!!.trim(),
            location = location!!.trim(),
            incidentDate = incidentDate!!.trim(),
            status = caseStatus,
            imageUrl = imageUrl?.trim()?.ifBlank { null },
            state = RecordState.ACTIVE,
            ownerId = currentUserId,
            createdAt = "",
            updatedAt = ""
        )

        return caseRepository.create(newCase)
    }

    fun update(
        id: String,
        title: String?,
        description: String?,
        location: String?,
        incidentDate: String?,
        status: String?,
        imageUrl: String?,
        currentUserId: String,
        currentUserRole: Role
    ): Result<Case> {
        val uuidErr = Validation.validateUUID(id)
        if (uuidErr.isNotEmpty()) return Result.Failure(AppError.Validation("ID inválido", uuidErr))

        val existing = caseRepository.findById(id) ?: return Result.Failure(AppError.NotFound("Caso no encontrado"))
        val accessErr = AccessPolicy.checkCaseAccess(currentUserId, currentUserRole, existing.ownerId)
        if (accessErr != null) return Result.Failure(accessErr)

        val errors = Validation.validateCase(title, description, location, incidentDate, status, imageUrl)
        if (errors.isNotEmpty()) return Result.Failure(AppError.Validation("Error de validación", errors))

        val caseStatus = if (status != null) CaseStatus.valueOf(status) else existing.status
        return caseRepository.update(
            id = id,
            title = title!!.trim(),
            description = description!!.trim(),
            location = location!!.trim(),
            incidentDate = incidentDate!!.trim(),
            status = caseStatus,
            imageUrl = imageUrl?.trim()?.ifBlank { null }
        )
    }

    fun delete(id: String, currentUserId: String, currentUserRole: Role): Result<Unit> {
        val uuidErr = Validation.validateUUID(id)
        if (uuidErr.isNotEmpty()) return Result.Failure(AppError.Validation("ID inválido", uuidErr))

        val existing = caseRepository.findById(id) ?: return Result.Failure(AppError.NotFound("Caso no encontrado"))
        val accessErr = AccessPolicy.checkCaseAccess(currentUserId, currentUserRole, existing.ownerId)
        if (accessErr != null) return Result.Failure(accessErr)

        return caseRepository.delete(id)
    }
}
