package edu.investigacion.web

import edu.investigacion.domain.AppError
import edu.investigacion.infrastructure.JsonTreeService
import io.javalin.http.Context

object ErrorTranslator {
    fun translate(ctx: Context, error: AppError) {
        when (error) {
            is AppError.Validation -> {
                ctx.status(400)
                ctx.json(JsonTreeService.errorResponse("VALIDATION_ERROR", error.message, error.details))
            }
            is AppError.Unauthorized -> {
                ctx.status(401)
                ctx.json(JsonTreeService.errorResponse("UNAUTHORIZED", error.message))
            }
            is AppError.Forbidden -> {
                ctx.status(403)
                ctx.json(JsonTreeService.errorResponse("FORBIDDEN", error.message))
            }
            is AppError.NotFound -> {
                ctx.status(404)
                ctx.json(JsonTreeService.errorResponse("NOT_FOUND", error.message))
            }
            is AppError.Conflict -> {
                ctx.status(409)
                ctx.json(JsonTreeService.errorResponse("CONFLICT", error.message))
            }
            is AppError.PayloadTooLarge -> {
                ctx.status(413)
                ctx.json(JsonTreeService.errorResponse("PAYLOAD_TOO_LARGE", error.message))
            }
            is AppError.Internal -> {
                ctx.status(500)
                ctx.json(JsonTreeService.errorResponse("INTERNAL_ERROR", error.message))
            }
        }
    }
}
