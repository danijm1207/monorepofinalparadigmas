package edu.investigacion.domain

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ErrorsSpec extends AnyFlatSpec with Matchers:

  "AppError" should "have correct status codes and codes" in {
    val validation = AppError.ValidationError("Error", List(FieldError("field", "msg")))
    validation.statusCode shouldBe 400
    validation.code shouldBe "VALIDATION_ERROR"
    validation.details.size shouldBe 1

    val unauth = AppError.UnauthorizedError("No autorizado")
    unauth.statusCode shouldBe 401
    unauth.code shouldBe "UNAUTHORIZED"

    val forbidden = AppError.ForbiddenError("Prohibido")
    forbidden.statusCode shouldBe 403
    forbidden.code shouldBe "FORBIDDEN"

    val notFound = AppError.NotFoundError("No encontrado")
    notFound.statusCode shouldBe 404
    notFound.code shouldBe "NOT_FOUND"

    val conflict = AppError.ConflictError("Conflicto")
    conflict.statusCode shouldBe 409
    conflict.code shouldBe "CONFLICT"

    val payloadTooLarge = AppError.PayloadTooLargeError("Muy grande")
    payloadTooLarge.statusCode shouldBe 413
    payloadTooLarge.code shouldBe "PAYLOAD_TOO_LARGE"

    val internal = AppError.InternalError("Error")
    internal.statusCode shouldBe 500
    internal.code shouldBe "INTERNAL_ERROR"

    val ex = AppException(unauth)
    ex.error shouldBe unauth
    ex.getMessage shouldBe "No autorizado"
  }
