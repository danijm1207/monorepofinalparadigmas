package edu.investigacion.web;

import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.investigacion.domain.AppError;
import edu.investigacion.domain.Enums.CaseStatus;
import edu.investigacion.domain.Enums.Role;
import edu.investigacion.domain.Enums.State;
import edu.investigacion.domain.Models.Case;
import edu.investigacion.domain.Models.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class JsonRendererTest {

    private static final Pattern ISO_UTC_REGEX = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$");

    @Test
    @DisplayName("Renderizado de usuario público jamás incluye passwordHash")
    void userPublicExcludesPasswordHash() {
        User u = new User(UUID.randomUUID(), "Admin", "admin@test.com", "secret_bcrypt_hash", Role.ADMIN, State.ACTIVE, Instant.now(), Instant.now());
        ObjectNode json = JsonRenderer.renderUserPublic(u);

        assertThat(json.has("passwordHash")).isFalse();
        assertThat(json.has("password_hash")).isFalse();
        assertThat(json.has("password")).isFalse();
        assertThat(json.get("role").asText()).isEqualTo("ADMIN");
        assertThat(ISO_UTC_REGEX.matcher(json.get("createdAt").asText()).matches()).isTrue();
    }

    @Test
    @DisplayName("Fechas se formatean exactamente en UTC con milisegundos y Z")
    void isoUtcDateFormatting() {
        Instant now = Instant.parse("2026-10-01T15:30:00.123Z");
        String formatted = JsonRenderer.formatInstant(now);
        assertThat(formatted).isEqualTo("2026-10-01T15:30:00.123Z");
        assertThat(ISO_UTC_REGEX.matcher(formatted).matches()).isTrue();
    }

    @Test
    @DisplayName("Renderizado de caso incluye todos los campos del contrato")
    void renderCaseStructure() {
        Case c = new Case(
            UUID.randomUUID(),
            1,
            "Robo Banco",
            "Descripcion pericial",
            "Centro",
            Instant.parse("2026-05-10T12:00:00.000Z"),
            CaseStatus.OPEN,
            Optional.empty(),
            State.ACTIVE,
            UUID.randomUUID(),
            Instant.parse("2026-05-10T13:00:00.000Z"),
            Instant.parse("2026-05-10T13:00:00.000Z")
        );

        ObjectNode json = JsonRenderer.renderCase(c);
        assertThat(json.get("caseNumber").asInt()).isEqualTo(1);
        assertThat(json.get("title").asText()).isEqualTo("Robo Banco");
        assertThat(json.get("status").asText()).isEqualTo("OPEN");
        assertThat(json.get("imageUrl").isNull()).isTrue();
    }

    @Test
    @DisplayName("Renderizado de error produce estructura uniforme con código y mensaje")
    void uniformErrorStructure() {
        AppError err = new AppError.Conflict("El email ya está registrado");
        ObjectNode json = JsonRenderer.renderError(err);

        assertThat(json.has("error")).isTrue();
        ObjectNode errNode = (ObjectNode) json.get("error");
        assertThat(errNode.get("code").asText()).isEqualTo("CONFLICT");
        assertThat(errNode.get("message").asText()).isEqualTo("El email ya está registrado");
    }
}
