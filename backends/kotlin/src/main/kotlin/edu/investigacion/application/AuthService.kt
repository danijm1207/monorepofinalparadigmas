package edu.investigacion.application

import edu.investigacion.domain.*
import edu.investigacion.infrastructure.PasswordHasher
import edu.investigacion.infrastructure.TokenService
import java.util.UUID

class AuthService(
    private val userRepository: UserRepository,
    private val tokenService: TokenService
) {
    fun register(name: String?, email: String?, password: String?): Result<Pair<UserPublic, String>> {
        val validationErrors = Validation.validateRegister(name, email, password)
        if (validationErrors.isNotEmpty()) {
            return Result.Failure(AppError.Validation("Error de validación", validationErrors))
        }

        val cleanEmail = email!!.trim().lowercase()
        val existing = userRepository.findByEmail(cleanEmail)
        if (existing != null) {
            return Result.Failure(AppError.Conflict("El correo electrónico ya está registrado"))
        }

        val newUser = User(
            id = UUID.randomUUID().toString(),
            name = name!!.trim(),
            email = cleanEmail,
            passwordHash = PasswordHasher.hash(password!!),
            role = Role.USER,
            state = RecordState.ACTIVE,
            createdAt = "",
            updatedAt = ""
        )

        return userRepository.create(newUser).map { created ->
            val publicUser = UserPublic(created.id, created.name, created.email, created.role, created.createdAt)
            val token = tokenService.createToken(created.id, created.role)
            Pair(publicUser, token)
        }
    }

    fun login(email: String?, password: String?): Result<Pair<UserPublic, String>> {
        val validationErrors = Validation.validateLogin(email, password)
        if (validationErrors.isNotEmpty()) {
            return Result.Failure(AppError.Validation("Error de validación", validationErrors))
        }

        val cleanEmail = email!!.trim().lowercase()
        val user = userRepository.findByEmail(cleanEmail)
        if (user == null || user.state == RecordState.INACTIVE || !PasswordHasher.verify(password!!, user.passwordHash)) {
            return Result.Failure(AppError.Unauthorized("Credenciales inválidas"))
        }

        val publicUser = UserPublic(user.id, user.name, user.email, user.role, user.createdAt)
        val token = tokenService.createToken(user.id, user.role)
        return Result.Success(Pair(publicUser, token))
    }

    fun me(userId: String): Result<UserPublic> {
        val user = userRepository.findById(userId)
        if (user == null || user.state == RecordState.INACTIVE) {
            return Result.Failure(AppError.Unauthorized("Usuario no encontrado o inactivo"))
        }
        return Result.Success(UserPublic(user.id, user.name, user.email, user.role, user.createdAt))
    }
}
