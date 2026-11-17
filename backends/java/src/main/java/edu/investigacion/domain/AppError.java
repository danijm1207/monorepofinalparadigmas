package edu.investigacion.domain;

import java.util.List;

public sealed interface AppError permits
    AppError.ValidationError,
    AppError.Unauthorized,
    AppError.Forbidden,
    AppError.NotFound,
    AppError.Conflict,
    AppError.PayloadTooLarge,
    AppError.InternalError {

    record FieldDetail(String field, String message) {}

    record ValidationError(String message, List<FieldDetail> details) implements AppError {
        public ValidationError(String message) {
            this(message, List.of());
        }
    }
    record Unauthorized(String message) implements AppError {}
    record Forbidden(String message) implements AppError {}
    record NotFound(String message) implements AppError {}
    record Conflict(String message) implements AppError {}
    record PayloadTooLarge(String message) implements AppError {}
    record InternalError(String message) implements AppError {}
}
