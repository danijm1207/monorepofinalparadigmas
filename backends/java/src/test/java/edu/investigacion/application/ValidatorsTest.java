package edu.investigacion.application;

import edu.investigacion.domain.AppError;
import edu.investigacion.domain.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ValidatorsTest {

    @Test
    @DisplayName("Validación de registro con datos correctos retorna éxito")
    void validRegisterPasses() {
        var res = Validators.validateRegister("Detective Juan", "juan@test.com", "Password123");
        assertThat(res.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Validación de registro acumula todos los errores por campo")
    void registerAccumulatesErrors() {
        var res = Validators.validateRegister("A", "no-es-email", "corta");
        assertThat(res.isFailure()).isTrue();
        var err = (AppError.ValidationError) ((Result.Failure<Void>) res).error();
        assertThat(err.details()).hasSize(3);
        assertThat(err.details().stream().map(AppError.FieldDetail::field))
            .containsExactlyInAnyOrder("name", "email", "password");
    }

    @Test
    @DisplayName("Validación de login exige email y contraseña")
    void loginValidationChecksRequiredFields() {
        var res = Validators.validateLogin("", "");
        assertThat(res.isFailure()).isTrue();
        var err = (AppError.ValidationError) ((Result.Failure<Void>) res).error();
        assertThat(err.details()).hasSize(2);
    }

    @Test
    @DisplayName("Validación de paginación rechaza página cero o negativa")
    void paginationInvalidPage() {
        var res = Validators.validatePagination(0, 10);
        assertThat(res.isFailure()).isTrue();
    }

    @Test
    @DisplayName("Validación de paginación rechaza límites mayores a 50")
    void paginationInvalidLimit() {
        var res = Validators.validatePagination(1, 51);
        assertThat(res.isFailure()).isTrue();
    }

    @Test
    @DisplayName("Validación de caso valida longitudes de título y descripción")
    void createCaseValidation() {
        var res = Validators.validateCreateCase("Ab", "Corta", "L", "2026-05-10T10:00:00.000Z");
        assertThat(res.isFailure()).isTrue();
        var err = (AppError.ValidationError) ((Result.Failure<Instant>) res).error();
        assertThat(err.details().stream().map(AppError.FieldDetail::field))
            .contains("title", "description", "location");
    }

    @Test
    @DisplayName("Validación de sospechoso rechaza nivel de sospecha fuera de 0..100")
    void suspectSuspicionRange() {
        var res = Validators.validateCreateSuspect("Carlos Perez", 101);
        assertThat(res.isFailure()).isTrue();
        var resOk = Validators.validateCreateSuspect("Carlos Perez", 50);
        assertThat(resOk.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Validación de evidencia rechaza relevancia inválida o fecha futura")
    void evidenceRelevanceAndFutureDate() {
        var resRel = Validators.validateCreateEvidence("Prueba", "Descripcion larga adecuada", "PHYSICAL", "2026-01-01T00:00:00.000Z", 6);
        assertThat(resRel.isFailure()).isTrue();

        var resFuture = Validators.validateCreateEvidence("Prueba", "Descripcion larga adecuada", "PHYSICAL", "2099-01-01T00:00:00.000Z", 3);
        assertThat(resFuture.isFailure()).isTrue();
    }
}
