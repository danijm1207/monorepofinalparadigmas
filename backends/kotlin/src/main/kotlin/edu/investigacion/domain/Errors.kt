package edu.investigacion.domain

data class FieldError(val field: String, val message: String)

sealed interface AppError {
    val message: String

    data class Validation(override val message: String, val details: List<FieldError>) : AppError
    data class Unauthorized(override val message: String = "No autorizado") : AppError
    data class Forbidden(override val message: String = "Acceso denegado") : AppError
    data class NotFound(override val message: String = "Recurso no encontrado") : AppError
    data class Conflict(override val message: String) : AppError
    data class PayloadTooLarge(override val message: String = "El archivo excede el tamaño máximo permitido de 5 MB") : AppError
    data class Internal(override val message: String = "Error interno del servidor") : AppError
}

sealed interface Result<out T> {
    data class Success<out T>(val value: T) : Result<T>
    data class Failure(val error: AppError) : Result<Nothing>

    fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(value)
        is Failure -> this
    }
}
