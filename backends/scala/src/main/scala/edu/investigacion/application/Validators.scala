package edu.investigacion.application

import java.time.{Instant, OffsetDateTime}
import java.util.UUID
import scala.util.Try
import edu.investigacion.domain.*

final case class RegisterInput(
    name: String,
    email: String,
    password: String
)

final case class LoginInput(
    email: String,
    password: String
)

final case class CaseInput(
    title: String,
    description: String,
    location: String,
    incidentDate: String,
    status: Option[String] = None,
    imageUrl: Option[String] = None
)

final case class SuspectInput(
    caseId: Option[String],
    fullName: String,
    alias: Option[String] = None,
    description: Option[String] = None,
    suspicionLevel: Option[Int] = None,
    status: Option[String] = None,
    photoUrl: Option[String] = None
)

final case class EvidenceInput(
    caseId: Option[String],
    suspectId: Option[String] = None,
    title: String,
    description: String,
    `type`: String,
    collectedAt: String,
    relevance: Int,
    imageUrl: Option[String] = None
)

final case class BenchmarkInput(
    scenario: String,
    seed: Option[Long] = None
)

object Validators:

  private val EmailRegex = """^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$""".r
  private val UrlRegex = """^(https?://)?[^\s/$.?#].[^\s]*$""".r

  def parseUuid(str: String, fieldName: String = "id"): Either[FieldError, UUID] =
    Try(UUID.fromString(str.trim)).toEither.left.map(_ => FieldError(fieldName, s"El campo '$fieldName' debe ser un UUID válido"))

  def parseInstant(str: String, fieldName: String): Either[FieldError, Instant] =
    Try(OffsetDateTime.parse(str.trim).toInstant)
      .orElse(Try(Instant.parse(str.trim)))
      .toEither
      .left
      .map(_ => FieldError(fieldName, s"El campo '$fieldName' debe ser una fecha en formato ISO 8601"))

  def validatePageRequest(pageStr: Option[String], limitStr: Option[String]): Either[AppError.ValidationError, PageRequest] =
    val details = List.newBuilder[FieldError]

    val page = pageStr match
      case None => 1
      case Some(s) if s.trim.isEmpty => 1
      case Some(s) =>
        s.trim.toIntOption match
          case Some(p) if p >= 1 => p
          case Some(_) =>
            details += FieldError("page", "El parámetro 'page' debe ser un número entero mayor o igual a 1")
            1
          case None =>
            details += FieldError("page", "El parámetro 'page' debe ser un número entero válido")
            1

    val limit = limitStr match
      case None => 10
      case Some(s) if s.trim.isEmpty => 10
      case Some(s) =>
        s.trim.toIntOption match
          case Some(l) if l >= 1 && l <= 50 => l
          case Some(_) =>
            details += FieldError("limit", "El parámetro 'limit' debe ser un número entero entre 1 y 50")
            10
          case None =>
            details += FieldError("limit", "El parámetro 'limit' debe ser un número entero válido")
            10

    val errors = details.result()
    if errors.isEmpty then Right(PageRequest(page, limit))
    else Left(AppError.ValidationError("Parámetros de paginación inválidos", errors))

  def validateRegister(input: RegisterInput): Either[AppError.ValidationError, (String, String, String)] =
    val details = List.newBuilder[FieldError]

    val name = input.name.trim
    if name.length < 2 || name.length > 80 then
      details += FieldError("name", "El nombre debe tener entre 2 y 80 caracteres")

    val email = input.email.trim.toLowerCase
    if email.isEmpty || email.length > 254 || !EmailRegex.matches(email) then
      details += FieldError("email", "El correo electrónico no tiene un formato válido")

    val pwd = input.password
    val hasLetter = pwd.exists(_.isLetter)
    val hasDigit = pwd.exists(_.isDigit)
    if pwd.length < 8 || pwd.length > 72 || !hasLetter || !hasDigit then
      details += FieldError("password", "La contraseña debe tener entre 8 y 72 caracteres, con al menos una letra y un número")

    val errors = details.result()
    if errors.isEmpty then Right((name, email, pwd))
    else Left(AppError.ValidationError("Errores de validación en el registro", errors))

  def validateLogin(input: LoginInput): Either[AppError.ValidationError, (String, String)] =
    val details = List.newBuilder[FieldError]
    val email = input.email.trim.toLowerCase
    if email.isEmpty || !EmailRegex.matches(email) then
      details += FieldError("email", "El correo electrónico es requerido y debe ser válido")
    if input.password.isEmpty then
      details += FieldError("password", "La contraseña es requerida")

    val errors = details.result()
    if errors.isEmpty then Right((email, input.password))
    else Left(AppError.ValidationError("Errores de validación en login", errors))

  def validateCase(input: CaseInput): Either[AppError.ValidationError, (String, String, String, Instant, CaseStatus, Option[String])] =
    val details = List.newBuilder[FieldError]

    val title = input.title.trim
    if title.length < 3 || title.length > 120 then
      details += FieldError("title", "El título debe tener entre 3 y 120 caracteres")

    val desc = input.description.trim
    if desc.length < 10 || desc.length > 2000 then
      details += FieldError("description", "La descripción debe tener entre 10 y 2000 caracteres")

    val loc = input.location.trim
    if loc.length < 3 || loc.length > 120 then
      details += FieldError("location", "La ubicación debe tener entre 3 y 120 caracteres")

    val dateOpt = parseInstant(input.incidentDate, "incidentDate") match
      case Right(inst) => Some(inst)
      case Left(err)   => details += err; None

    val statusOpt = input.status match
      case None => Some(CaseStatus.OPEN)
      case Some(s) =>
        Try(CaseStatus.valueOf(s.trim.toUpperCase)).toOption match
          case Some(cs) => Some(cs)
          case None =>
            details += FieldError("status", "El estado del caso debe ser OPEN, IN_PROGRESS o CLOSED")
            None

    val imgOpt = input.imageUrl.map(_.trim).filter(_.nonEmpty)
    imgOpt.foreach { url =>
      if !UrlRegex.matches(url) then
        details += FieldError("imageUrl", "La URL de la imagen no es válida")
    }

    val errors = details.result()
    if errors.isEmpty then
      Right((title, desc, loc, dateOpt.get, statusOpt.get, imgOpt))
    else
      Left(AppError.ValidationError("Errores de validación en caso", errors))

  def validateSuspect(input: SuspectInput, requireCaseId: Boolean): Either[AppError.ValidationError, (Option[UUID], String, Option[String], Option[String], Int, SuspectStatus, Option[String])] =
    val details = List.newBuilder[FieldError]

    val caseIdOpt: Option[UUID] =
      if requireCaseId then
        input.caseId match
          case Some(cidStr) =>
            parseUuid(cidStr, "caseId") match
              case Right(u)  => Some(u)
              case Left(err) => details += err; None
          case None =>
            details += FieldError("caseId", "El caseId es requerido")
            None
      else None

    val fullName = input.fullName.trim
    if fullName.length < 3 || fullName.length > 120 then
      details += FieldError("fullName", "El nombre completo debe tener entre 3 y 120 caracteres")

    val aliasOpt = input.alias.map(_.trim).filter(_.nonEmpty)
    aliasOpt.foreach { a =>
      if a.length > 60 then details += FieldError("alias", "El alias no puede exceder 60 caracteres")
    }

    val descOpt = input.description.map(_.trim).filter(_.nonEmpty)
    descOpt.foreach { d =>
      if d.length > 1000 then details += FieldError("description", "La descripción no puede exceder 1000 caracteres")
    }

    val suspLevel = input.suspicionLevel.getOrElse(0)
    if suspLevel < 0 || suspLevel > 100 then
      details += FieldError("suspicionLevel", "El nivel de sospecha debe estar entre 0 y 100")

    val statusOpt = input.status match
      case None => Some(SuspectStatus.PERSON_OF_INTEREST)
      case Some(s) =>
        Try(SuspectStatus.valueOf(s.trim.toUpperCase)).toOption match
          case Some(st) => Some(st)
          case None =>
            details += FieldError("status", "El estado del sospechoso debe ser PERSON_OF_INTEREST, DETAINED o CLEARED")
            None

    val photoOpt = input.photoUrl.map(_.trim).filter(_.nonEmpty)
    photoOpt.foreach { p =>
      if !UrlRegex.matches(p) then details += FieldError("photoUrl", "La URL de la foto no es válida")
    }

    val errors = details.result()
    if errors.isEmpty then
      Right((caseIdOpt, fullName, aliasOpt, descOpt, suspLevel, statusOpt.get, photoOpt))
    else
      Left(AppError.ValidationError("Errores de validación en sospechoso", errors))

  def validateEvidence(input: EvidenceInput, requireCaseId: Boolean): Either[AppError.ValidationError, (Option[UUID], Option[UUID], String, String, EvidenceType, Instant, Int, Option[String])] =
    val details = List.newBuilder[FieldError]

    val caseIdOpt: Option[UUID] =
      if requireCaseId then
        input.caseId match
          case Some(cidStr) =>
            parseUuid(cidStr, "caseId") match
              case Right(u)  => Some(u)
              case Left(err) => details += err; None
          case None =>
            details += FieldError("caseId", "El caseId es requerido")
            None
      else None

    val suspectIdOpt: Option[UUID] = input.suspectId.map(_.trim).filter(_.nonEmpty).flatMap { sidStr =>
      parseUuid(sidStr, "suspectId") match
        case Right(u)  => Some(u)
        case Left(err) => details += err; None
    }

    val title = input.title.trim
    if title.length < 3 || title.length > 120 then
      details += FieldError("title", "El título debe tener entre 3 y 120 caracteres")

    val desc = input.description.trim
    if desc.length < 5 || desc.length > 1000 then
      details += FieldError("description", "La descripción debe tener entre 5 y 1000 caracteres")

    val typeOpt = Try(EvidenceType.valueOf(input.`type`.trim.toUpperCase)).toOption match
      case Some(t) => Some(t)
      case None =>
        details += FieldError("type", "El tipo de evidencia debe ser PHYSICAL, DIGITAL, DOCUMENT, TESTIMONY o FORENSIC")
        None

    val collectedAtOpt = parseInstant(input.collectedAt, "collectedAt") match
      case Right(inst) =>
        if inst.isAfter(Instant.now()) then
          details += FieldError("collectedAt", "La fecha de recolección no puede ser futura")
          None
        else Some(inst)
      case Left(err) =>
        details += err; None

    if input.relevance < 1 || input.relevance > 5 then
      details += FieldError("relevance", "La relevancia debe estar entre 1 y 5")

    val imgOpt = input.imageUrl.map(_.trim).filter(_.nonEmpty)
    imgOpt.foreach { url =>
      if !UrlRegex.matches(url) then details += FieldError("imageUrl", "La URL de la imagen no es válida")
    }

    val errors = details.result()
    if errors.isEmpty then
      Right((caseIdOpt, suspectIdOpt, title, desc, typeOpt.get, collectedAtOpt.get, input.relevance, imgOpt))
    else
      Left(AppError.ValidationError("Errores de validación en evidencia", errors))

  def validateBenchmark(input: BenchmarkInput): Either[AppError.ValidationError, (String, Long)] =
    val details = List.newBuilder[FieldError]

    val sc = input.scenario.trim.toUpperCase
    if !List("T", "S", "M", "L", "XL").contains(sc) then
      details += FieldError("scenario", "El escenario debe ser T, S, M, L o XL")

    val seed = input.seed.getOrElse(42L)
    if seed < 0L || seed > 4294967295L then
      details += FieldError("seed", "La semilla debe estar entre 0 y 4294967295")

    val errors = details.result()
    if errors.isEmpty then Right((sc, seed))
    else Left(AppError.ValidationError("Errores de validación en benchmark", errors))
