package edu.investigacion.application

import edu.investigacion.domain.AppError
import edu.investigacion.domain.Role

object AccessPolicy {
    fun canAccessCase(userId: String, userRole: Role, caseOwnerId: String): Boolean {
        return userRole == Role.ADMIN || userId == caseOwnerId
    }

    fun checkCaseAccess(userId: String, userRole: Role, caseOwnerId: String): AppError? {
        return if (canAccessCase(userId, userRole, caseOwnerId)) null else AppError.NotFound("Caso no encontrado")
    }
}
