package edu.investigacion.application

import edu.investigacion.domain.*

class UserService(
    private val userRepository: UserRepository
) {
    fun list(page: Int, limit: Int, query: String?, role: String?): Result<Page<UserPublic>> {
        val safePage = if (page < 1) 1 else page
        val safeLimit = when {
            limit < 1 -> 10
            limit > 50 -> 50
            else -> limit
        }

        val userRole = if (!role.isNullOrBlank()) {
            try { Role.valueOf(role) } catch (_: Exception) { return Result.Failure(AppError.Validation("Rol inválido", listOf(FieldError("role", "Rol no permitido")))) }
        } else null

        return Result.Success(userRepository.findAll(safePage, safeLimit, query, userRole))
    }

    fun deactivate(id: String, currentAdminId: String): Result<Unit> {
        val uuidErr = Validation.validateUUID(id)
        if (uuidErr.isNotEmpty()) return Result.Failure(AppError.Validation("ID inválido", uuidErr))

        // Regla 9: El admin no puede desactivarse a sí mismo (409)
        if (id == currentAdminId) {
            return Result.Failure(AppError.Conflict("El administrador no puede desactivarse a sí mismo"))
        }

        return userRepository.deactivate(id)
    }
}
