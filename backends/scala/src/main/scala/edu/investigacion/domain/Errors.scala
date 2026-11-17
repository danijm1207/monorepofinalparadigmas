package edu.investigacion.domain

final case class FieldError(
    field: String,
    message: String
)

sealed trait AppError:
  def message: String
  def code: String
  def statusCode: Int

final case class AppException(error: AppError) extends RuntimeException(error.message)

object AppError:

  final case class ValidationError(
      message: String,
      details: List[FieldError] = Nil
  ) extends AppError:
    override val code: String = "VALIDATION_ERROR"
    override val statusCode: Int = 400

  final case class UnauthorizedError(
      message: String = "Credenciales inválidas"
  ) extends AppError:
    override val code: String = "UNAUTHORIZED"
    override val statusCode: Int = 401

  final case class ForbiddenError(
      message: String = "Acceso denegado"
  ) extends AppError:
    override val code: String = "FORBIDDEN"
    override val statusCode: Int = 403

  final case class NotFoundError(
      message: String = "Recurso no encontrado"
  ) extends AppError:
    override val code: String = "NOT_FOUND"
    override val statusCode: Int = 404

  final case class ConflictError(
      message: String = "Conflicto con el recurso actual"
  ) extends AppError:
    override val code: String = "CONFLICT"
    override val statusCode: Int = 409

  final case class PayloadTooLargeError(
      message: String = "El tamaño del archivo excede el límite permitido"
  ) extends AppError:
    override val code: String = "PAYLOAD_TOO_LARGE"
    override val statusCode: Int = 413

  final case class InternalError(
      message: String = "Error interno del servidor"
  ) extends AppError:
    override val code: String = "INTERNAL_ERROR"
    override val statusCode: Int = 500
