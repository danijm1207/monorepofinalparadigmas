package edu.investigacion.domain;

import java.util.function.Function;

public sealed interface Result<T> permits Result.Success, Result.Failure {
    record Success<T>(T value) implements Result<T> {}
    record Failure<T>(AppError error) implements Result<T> {}

    static <T> Result<T> ok(T value) {
        return new Success<>(value);
    }

    static <T> Result<T> fail(AppError error) {
        return new Failure<>(error);
    }

    default boolean isSuccess() {
        return this instanceof Success;
    }

    default boolean isFailure() {
        return this instanceof Failure;
    }

    default <R> Result<R> map(Function<? super T, ? extends R> mapper) {
        return switch (this) {
            case Success<T> s -> Result.ok(mapper.apply(s.value()));
            case Failure<T> f -> Result.fail(f.error());
        };
    }

    default <R> Result<R> flatMap(Function<? super T, Result<R>> mapper) {
        return switch (this) {
            case Success<T> s -> mapper.apply(s.value());
            case Failure<T> f -> Result.fail(f.error());
        };
    }
}
