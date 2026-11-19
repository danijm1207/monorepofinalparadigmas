package edu.investigacion.web

import edu.investigacion.domain.AppError
import edu.investigacion.domain.Role
import edu.investigacion.infrastructure.TokenService
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.security.RouteRole

class AccessManager(private val tokenService: TokenService) {

    fun manage(handler: Handler, ctx: Context, routeRoles: Set<RouteRole>) {
        if (routeRoles.isEmpty() || routeRoles.contains(AppRole.ANYONE)) {
            handler.handle(ctx)
            return
        }

        val authHeader = ctx.header("Authorization")
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            ErrorTranslator.translate(ctx, AppError.Unauthorized("Token de autenticación requerido"))
            return
        }

        val token = authHeader.removePrefix("Bearer ").trim()
        val payload = tokenService.verifyToken(token)
        if (payload == null) {
            ErrorTranslator.translate(ctx, AppError.Unauthorized("Token inválido o expirado"))
            return
        }

        ctx.attribute("userId", payload.userId)
        ctx.attribute("userRole", payload.role)

        if (routeRoles.contains(AppRole.ADMIN) && payload.role != Role.ADMIN) {
            ErrorTranslator.translate(ctx, AppError.Forbidden("Se requieren permisos de ADMIN"))
            return
        }

        handler.handle(ctx)
    }

    companion object {
        fun getUserId(ctx: Context): String = ctx.attribute("userId") ?: ""
        fun getUserRole(ctx: Context): Role = ctx.attribute("userRole") ?: Role.USER
    }
}
