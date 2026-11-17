package edu.investigacion.application

import edu.investigacion.domain.{AppError, Case, Role, User}

object AccessPolicy:

  def canAccessCase(currentUser: User, caseItem: Case): Either[AppError, Unit] =
    if currentUser.role == Role.ADMIN || caseItem.ownerId == currentUser.id then
      Right(())
    else
      // Regla 1 del punto 7: Si un USER accede a un caso ajeno responde 404 (no 403)
      Left(AppError.NotFoundError("Caso no encontrado"))
