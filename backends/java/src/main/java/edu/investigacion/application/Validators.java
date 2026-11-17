package edu.investigacion.application;

import edu.investigacion.domain.AppError;
import edu.investigacion.domain.AppError.FieldDetail;
import edu.investigacion.domain.Enums.CaseStatus;
import edu.investigacion.domain.Enums.EvidenceType;
import edu.investigacion.domain.Enums.SuspectStatus;
import edu.investigacion.domain.Result;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class Validators {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    private Validators() {}

    public static Result<Void> validatePagination(int page, int limit) {
        if (page < 1 || limit < 1 || limit > 50) {
            return Result.fail(new AppError.ValidationError("Parámetros de paginación inválidos"));
        }
        return Result.ok(null);
    }

    public static Result<Void> validateRegister(String name, String email, String password) {
        List<FieldDetail> details = new ArrayList<>();

        if (name == null || name.trim().length() < 2 || name.trim().length() > 80) {
            details.add(new FieldDetail("name", "El nombre debe tener entre 2 y 80 caracteres"));
        }

        if (email == null || email.trim().isEmpty() || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            details.add(new FieldDetail("email", "Formato de email inválido"));
        }

        if (password == null || password.length() < 8 || password.length() > 72 ||
            !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            details.add(new FieldDetail("password", "La contraseña debe tener entre 8 y 72 caracteres, con al menos una letra y un número"));
        }

        if (!details.isEmpty()) {
            return Result.fail(new AppError.ValidationError("Error de validación en registro", details));
        }
        return Result.ok(null);
    }

    public static Result<Void> validateLogin(String email, String password) {
        List<FieldDetail> details = new ArrayList<>();
        if (email == null || email.trim().isEmpty()) {
            details.add(new FieldDetail("email", "El email es requerido"));
        }
        if (password == null || password.isEmpty()) {
            details.add(new FieldDetail("password", "La contraseña es requerida"));
        }
        if (!details.isEmpty()) {
            return Result.fail(new AppError.ValidationError("Credenciales requeridas", details));
        }
        return Result.ok(null);
    }

    public static Result<Instant> validateCreateCase(String title, String description, String location, String incidentDateStr) {
        List<FieldDetail> details = new ArrayList<>();

        if (title == null || title.trim().length() < 3 || title.trim().length() > 120) {
            details.add(new FieldDetail("title", "El título debe tener entre 3 y 120 caracteres"));
        }

        if (description == null || description.trim().length() < 10 || description.trim().length() > 2000) {
            details.add(new FieldDetail("description", "La descripción debe tener entre 10 y 2000 caracteres"));
        }

        if (location == null || location.trim().length() < 3 || location.trim().length() > 120) {
            details.add(new FieldDetail("location", "La ubicación debe tener entre 3 y 120 caracteres"));
        }

        Instant incidentDate = null;
        if (incidentDateStr == null || incidentDateStr.trim().isEmpty()) {
            details.add(new FieldDetail("incidentDate", "La fecha del incidente es requerida"));
        } else {
            try {
                incidentDate = Instant.parse(incidentDateStr.trim());
            } catch (DateTimeParseException e) {
                details.add(new FieldDetail("incidentDate", "Formato de fecha inválido (debe ser ISO 8601)"));
            }
        }

        if (!details.isEmpty()) {
            return Result.fail(new AppError.ValidationError("Error de validación al crear caso", details));
        }
        return Result.ok(incidentDate);
    }

    public static Result<Void> validateCreateSuspect(String fullName, Integer suspicionLevel) {
        List<FieldDetail> details = new ArrayList<>();

        if (fullName == null || fullName.trim().length() < 3 || fullName.trim().length() > 120) {
            details.add(new FieldDetail("fullName", "El nombre completo debe tener entre 3 y 120 caracteres"));
        }

        if (suspicionLevel != null && (suspicionLevel < 0 || suspicionLevel > 100)) {
            details.add(new FieldDetail("suspicionLevel", "El nivel de sospecha debe estar entre 0 y 100"));
        }

        if (!details.isEmpty()) {
            return Result.fail(new AppError.ValidationError("Error de validación al crear sospechoso", details));
        }
        return Result.ok(null);
    }

    public static Result<Instant> validateCreateEvidence(String title, String description, String typeStr, String collectedAtStr, Integer relevance) {
        List<FieldDetail> details = new ArrayList<>();

        if (title == null || title.trim().length() < 3 || title.trim().length() > 120) {
            details.add(new FieldDetail("title", "El título debe tener entre 3 y 120 caracteres"));
        }

        if (description == null || description.trim().length() < 5 || description.trim().length() > 1000) {
            details.add(new FieldDetail("description", "La descripción debe tener entre 5 y 1000 caracteres"));
        }

        if (relevance == null || relevance < 1 || relevance > 5) {
            details.add(new FieldDetail("relevance", "La relevancia debe estar entre 1 y 5"));
        }

        try {
            EvidenceType.valueOf(typeStr);
        } catch (Exception e) {
            details.add(new FieldDetail("type", "Tipo de evidencia inválido"));
        }

        Instant collectedAt = null;
        if (collectedAtStr == null || collectedAtStr.trim().isEmpty()) {
            details.add(new FieldDetail("collectedAt", "La fecha de recolección es requerida"));
        } else {
            try {
                collectedAt = Instant.parse(collectedAtStr.trim());
                if (collectedAt.isAfter(Instant.now().plusSeconds(60))) {
                    details.add(new FieldDetail("collectedAt", "La fecha de recolección no puede ser futura"));
                }
            } catch (DateTimeParseException e) {
                details.add(new FieldDetail("collectedAt", "Formato de fecha inválido (debe ser ISO 8601)"));
            }
        }

        if (!details.isEmpty()) {
            return Result.fail(new AppError.ValidationError("Error de validación al crear evidencia", details));
        }
        return Result.ok(collectedAt);
    }
}
