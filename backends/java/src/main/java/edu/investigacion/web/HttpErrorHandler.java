package edu.investigacion.web;

import edu.investigacion.domain.AppError;
import io.javalin.http.Context;

public final class HttpErrorHandler {
    private HttpErrorHandler() {}

    public static void respondError(Context ctx, AppError error) {
        int status = switch (error) {
            case AppError.ValidationError e -> 400;
            case AppError.Unauthorized e -> 401;
            case AppError.Forbidden e -> 403;
            case AppError.NotFound e -> 404;
            case AppError.Conflict e -> 409;
            case AppError.PayloadTooLarge e -> 413;
            case AppError.InternalError e -> 500;
        };
        ctx.status(status).json(JsonRenderer.renderError(error));
    }
}
