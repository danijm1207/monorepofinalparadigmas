package edu.investigacion.web

import io.javalin.http.{Context, Handler}
import edu.investigacion.domain.{AppError, AppException, Role, User, UserRepository}
import edu.investigacion.infrastructure.TokenService

class AuthMiddleware(tokenService: TokenService, userRepository: UserRepository) extends Handler:

  override def handle(ctx: Context): Unit =
    val header = Option(ctx.header("Authorization")).map(_.trim)
    header match
      case Some(h) if h.startsWith("Bearer ") =>
        val token = h.substring(7).trim
        tokenService.verifyToken(token) match
          case Right((userId, _)) =>
            userRepository.findById(userId) match
              case Some(user) if user.state == edu.investigacion.domain.EntityState.ACTIVE =>
                ctx.attribute("currentUser", user)
              case _ =>
                throw AppException(AppError.UnauthorizedError("Usuario no encontrado o inactivo"))
          case Left(err) =>
            throw AppException(err)
      case _ =>
        throw AppException(AppError.UnauthorizedError("Token de autorización ausente o con formato incorrecto"))

object Middlewares:

  def respondError(ctx: Context, error: AppError): Unit =
    ctx.status(error.statusCode)
    ctx.contentType("application/json")
    ctx.result(JsonSupport.errorResponse(error).toString)

  def adminOnly: Handler = ctx =>
    val userOpt = Option(ctx.attribute[User]("currentUser"))
    userOpt match
      case Some(u) if u.role == Role.ADMIN =>
      case Some(_) =>
        throw AppException(AppError.ForbiddenError("Se requieren privilegios de administrador"))
      case None =>
        throw AppException(AppError.UnauthorizedError("No autenticado"))

