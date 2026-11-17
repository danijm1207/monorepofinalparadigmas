package edu.investigacion.application

import edu.investigacion.domain.FieldError
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

object Validation {
    private val EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
    private val URL_REGEX = "^https?://.+".toRegex()

    fun validateUUID(value: String?, fieldName: String = "id"): List<FieldError> {
        if (value.isNullOrBlank()) return listOf(FieldError(fieldName, "El campo $fieldName es obligatorio"))
        return try {
            UUID.fromString(value)
            emptyList()
        } catch (_: IllegalArgumentException) {
            listOf(FieldError(fieldName, "El campo $fieldName debe ser un UUID válido"))
        }
    }

    fun validateRegister(name: String?, email: String?, password: String?): List<FieldError> {
        val errors = mutableListOf<FieldError>()
        if (name == null || name.trim().length !in 2..80) {
            errors.add(FieldError("name", "El nombre debe tener entre 2 y 80 caracteres"))
        }
        if (email == null || !EMAIL_REGEX.matches(email.trim())) {
            errors.add(FieldError("email", "El correo electrónico no tiene un formato válido"))
        }
        if (password == null || password.length !in 8..72 || !password.any { it.isLetter() } || !password.any { it.isDigit() }) {
            errors.add(FieldError("password", "La contraseña debe tener entre 8 y 72 caracteres, con al menos una letra y un número"))
        }
        return errors
    }

    fun validateLogin(email: String?, password: String?): List<FieldError> {
        val errors = mutableListOf<FieldError>()
        if (email.isNullOrBlank()) errors.add(FieldError("email", "El correo electrónico es obligatorio"))
        if (password.isNullOrBlank()) errors.add(FieldError("password", "La contraseña es obligatoria"))
        return errors
    }

    fun validateCase(title: String?, description: String?, location: String?, incidentDate: String?, status: String?, imageUrl: String?): List<FieldError> {
        val errors = mutableListOf<FieldError>()
        if (title == null || title.trim().length !in 3..120) {
            errors.add(FieldError("title", "El título debe tener entre 3 y 120 caracteres"))
        }
        if (description == null || description.trim().length !in 10..2000) {
            errors.add(FieldError("description", "La descripción debe tener entre 10 y 2000 caracteres"))
        }
        if (location == null || location.trim().length !in 3..120) {
            errors.add(FieldError("location", "La ubicación debe tener entre 3 y 120 caracteres"))
        }
        if (incidentDate.isNullOrBlank()) {
            errors.add(FieldError("incidentDate", "La fecha del incidente es obligatoria"))
        } else {
            try {
                Instant.parse(incidentDate)
            } catch (_: DateTimeParseException) {
                errors.add(FieldError("incidentDate", "La fecha del incidente debe ser un ISO 8601 válido"))
            }
        }
        if (status != null && status !in listOf("OPEN", "IN_PROGRESS", "CLOSED")) {
            errors.add(FieldError("status", "El estado debe ser OPEN, IN_PROGRESS o CLOSED"))
        }
        if (!imageUrl.isNullOrBlank() && !URL_REGEX.matches(imageUrl.trim())) {
            errors.add(FieldError("imageUrl", "La URL de la imagen no es válida"))
        }
        return errors
    }

    fun validateSuspect(caseId: String?, fullName: String?, alias: String?, description: String?, suspicionLevel: Int?, status: String?, photoUrl: String?): List<FieldError> {
        val errors = mutableListOf<FieldError>()
        errors.addAll(validateUUID(caseId, "caseId"))
        if (fullName == null || fullName.trim().length !in 3..120) {
            errors.add(FieldError("fullName", "El nombre completo debe tener entre 3 y 120 caracteres"))
        }
        if (alias != null && alias.length > 60) {
            errors.add(FieldError("alias", "El alias no puede superar los 60 caracteres"))
        }
        if (description != null && description.length > 1000) {
            errors.add(FieldError("description", "La descripción no puede superar los 1000 caracteres"))
        }
        if (suspicionLevel != null && suspicionLevel !in 0..100) {
            errors.add(FieldError("suspicionLevel", "El nivel de sospecha debe estar entre 0 y 100"))
        }
        if (status != null && status !in listOf("PERSON_OF_INTEREST", "DETAINED", "CLEARED")) {
            errors.add(FieldError("status", "El estado debe ser PERSON_OF_INTEREST, DETAINED o CLEARED"))
        }
        if (!photoUrl.isNullOrBlank() && !URL_REGEX.matches(photoUrl.trim())) {
            errors.add(FieldError("photoUrl", "La URL de la foto no es válida"))
        }
        return errors
    }

    fun validateEvidence(caseId: String?, suspectId: String?, title: String?, description: String?, type: String?, collectedAt: String?, relevance: Int?, imageUrl: String?): List<FieldError> {
        val errors = mutableListOf<FieldError>()
        errors.addAll(validateUUID(caseId, "caseId"))
        if (suspectId != null) {
            errors.addAll(validateUUID(suspectId, "suspectId"))
        }
        if (title == null || title.trim().length !in 3..120) {
            errors.add(FieldError("title", "El título debe tener entre 3 y 120 caracteres"))
        }
        if (description == null || description.trim().length !in 5..1000) {
            errors.add(FieldError("description", "La descripción debe tener entre 5 y 1000 caracteres"))
        }
        if (type == null || type !in listOf("PHYSICAL", "DIGITAL", "DOCUMENT", "TESTIMONY", "FORENSIC")) {
            errors.add(FieldError("type", "El tipo de evidencia debe ser PHYSICAL, DIGITAL, DOCUMENT, TESTIMONY o FORENSIC"))
        }
        if (collectedAt.isNullOrBlank()) {
            errors.add(FieldError("collectedAt", "La fecha de recolección es obligatoria"))
        } else {
            try {
                val parsed = Instant.parse(collectedAt)
                if (parsed.isAfter(Instant.now())) {
                    errors.add(FieldError("collectedAt", "La fecha de recolección no puede ser futura"))
                }
            } catch (_: DateTimeParseException) {
                errors.add(FieldError("collectedAt", "La fecha de recolección debe ser un ISO 8601 válido"))
            }
        }
        if (relevance == null || relevance !in 1..5) {
            errors.add(FieldError("relevance", "La relevancia debe ser un entero entre 1 y 5"))
        }
        if (!imageUrl.isNullOrBlank() && !URL_REGEX.matches(imageUrl.trim())) {
            errors.add(FieldError("imageUrl", "La URL de la imagen no es válida"))
        }
        return errors
    }
}
