package edu.investigacion.application

import java.io.InputStream
import java.nio.file.{Files, Paths}
import java.time.Instant
import java.util.UUID
import scala.util.Try
import edu.investigacion.analytics.{AnalyticsPipeline, ReportFormatter, ScenarioGenerator}
import edu.investigacion.domain.*
import edu.investigacion.infrastructure.{AppConfig, PasswordHasher, TokenService}

class AuthService(
    userRepository: UserRepository,
    hasher: PasswordHasher,
    tokenService: TokenService
):

  def register(input: RegisterInput): Either[AppError, (User, String)] =
    for
      validated <- Validators.validateRegister(input)
      (name, email, password) = validated
      _ <- userRepository.findByEmail(email) match
        case Some(_) => Left(AppError.ConflictError("El correo electrónico ya está registrado"))
        case None    => Right(())
      hash = hasher.hash(password)
      now = Instant.now()
      newUser = User(
        id = UUID.randomUUID(),
        name = name,
        email = email,
        passwordHash = hash,
        role = Role.USER,
        state = EntityState.ACTIVE,
        createdAt = now,
        updatedAt = now
      )
      saved = userRepository.create(newUser)
      token = tokenService.createToken(saved.id, saved.role)
    yield (saved, token)

  def login(input: LoginInput): Either[AppError, (User, String)] =
    for
      validated <- Validators.validateLogin(input)
      (email, password) = validated
      user <- userRepository.findByEmail(email) match
        case Some(u) if u.state == EntityState.ACTIVE && hasher.verify(password, u.passwordHash) =>
          Right(u)
        case _ =>
          // Texto exacto estipulado en punto 8: "Credenciales inválidas"
          Left(AppError.UnauthorizedError("Credenciales inválidas"))
      token = tokenService.createToken(user.id, user.role)
    yield (user, token)

  def getMe(currentUser: User): Either[AppError, User] =
    Right(currentUser)

class UserService(userRepository: UserRepository):

  def listUsers(currentUser: User, filter: UserFilter, page: PageRequest): Either[AppError, PageResult[User]] =
    Right(userRepository.findAll(filter, page))

  def deactivateUser(currentUser: User, targetId: UUID): Either[AppError, Unit] =
    // Regla 9 del punto 7: El admin no puede desactivarse a sí mismo (409)
    if currentUser.id == targetId then
      Left(AppError.ConflictError("El administrador no puede desactivarse a sí mismo"))
    else
      userRepository.findById(targetId) match
        case None => Left(AppError.NotFoundError("Usuario no encontrado"))
        case Some(_) =>
          userRepository.deactivate(targetId)
          Right(())

class CaseService(caseRepository: CaseRepository):

  def listCases(currentUser: User, filter: CaseFilter, page: PageRequest): Either[AppError, PageResult[Case]] =
    val scopedFilter =
      if currentUser.role == Role.USER then filter.copy(ownerId = Some(currentUser.id))
      else filter
    Right(caseRepository.findAll(scopedFilter, page))

  def getCaseById(currentUser: User, caseId: UUID): Either[AppError, Case] =
    for
      caseItem <- caseRepository.findById(caseId).toRight(AppError.NotFoundError("Caso no encontrado"))
      _ <- AccessPolicy.canAccessCase(currentUser, caseItem)
    yield caseItem

  def createCase(currentUser: User, input: CaseInput): Either[AppError, Case] =
    for
      validated <- Validators.validateCase(input)
      (title, desc, loc, incDate, status, img) = validated
      now = Instant.now()
      newCase = Case(
        id = UUID.randomUUID(),
        caseNumber = 0L,
        title = title,
        description = desc,
        location = loc,
        incidentDate = incDate,
        status = status,
        imageUrl = img,
        state = EntityState.ACTIVE,
        ownerId = currentUser.id,
        createdAt = now,
        updatedAt = now
      )
    yield caseRepository.create(newCase)

  def updateCase(currentUser: User, caseId: UUID, input: CaseInput): Either[AppError, Case] =
    for
      existing <- caseRepository.findById(caseId).toRight(AppError.NotFoundError("Caso no encontrado"))
      _ <- AccessPolicy.canAccessCase(currentUser, existing)
      validated <- Validators.validateCase(input)
      (title, desc, loc, incDate, status, img) = validated
      now = Instant.now()
      updatedCase = existing.copy(
        title = title,
        description = desc,
        location = loc,
        incidentDate = incDate,
        status = status,
        imageUrl = img,
        updatedAt = now
      )
    yield caseRepository.update(updatedCase)

  def deleteCase(currentUser: User, caseId: UUID): Either[AppError, Unit] =
    for
      existing <- caseRepository.findById(caseId).toRight(AppError.NotFoundError("Caso no encontrado"))
      _ <- AccessPolicy.canAccessCase(currentUser, existing)
    yield
      caseRepository.deactivateCascade(caseId)
      ()

class SuspectService(
    suspectRepository: SuspectRepository,
    caseRepository: CaseRepository
):

  def listSuspects(currentUser: User, filter: SuspectFilter, page: PageRequest): Either[AppError, PageResult[Suspect]] =
    val scopedFilter =
      if currentUser.role == Role.USER then filter.copy(ownerId = Some(currentUser.id))
      else filter
    Right(suspectRepository.findAll(scopedFilter, page))

  def getSuspectById(currentUser: User, suspectId: UUID): Either[AppError, Suspect] =
    for
      suspect <- suspectRepository.findById(suspectId).toRight(AppError.NotFoundError("Sospechoso no encontrado"))
      caseItem <- caseRepository.findById(suspect.caseId).toRight(AppError.NotFoundError("Caso no encontrado"))
      _ <- AccessPolicy.canAccessCase(currentUser, caseItem)
    yield suspect

  def createSuspect(currentUser: User, input: SuspectInput): Either[AppError, Suspect] =
    for
      validated <- Validators.validateSuspect(input, requireCaseId = true)
      (caseIdOpt, fullName, alias, desc, suspLevel, status, photo) = validated
      cid = caseIdOpt.get
      caseItem <- caseRepository.findById(cid).toRight(AppError.NotFoundError("Caso no encontrado"))
      _ <- AccessPolicy.canAccessCase(currentUser, caseItem)
      // Regla 3 del punto 7: Si el caso está CLOSED no se pueden crear sospechosos (409)
      _ <- if caseItem.status == CaseStatus.CLOSED then
        Left(AppError.ConflictError("No se pueden agregar sospechosos a un caso cerrado"))
      else Right(())
      now = Instant.now()
      newSuspect = Suspect(
        id = UUID.randomUUID(),
        caseId = cid,
        fullName = fullName,
        alias = alias,
        description = desc,
        suspicionLevel = suspLevel,
        status = status,
        photoUrl = photo,
        state = EntityState.ACTIVE,
        createdAt = now,
        updatedAt = now
      )
    yield suspectRepository.create(newSuspect)

  def updateSuspect(currentUser: User, suspectId: UUID, input: SuspectInput): Either[AppError, Suspect] =
    for
      existing <- suspectRepository.findById(suspectId).toRight(AppError.NotFoundError("Sospechoso no encontrado"))
      caseItem <- caseRepository.findById(existing.caseId).toRight(AppError.NotFoundError("Caso no encontrado"))
      _ <- AccessPolicy.canAccessCase(currentUser, caseItem)
      validated <- Validators.validateSuspect(input, requireCaseId = false)
      (_, fullName, alias, desc, suspLevel, status, photo) = validated
      now = Instant.now()
      updatedSuspect = existing.copy(
        fullName = fullName,
        alias = alias,
        description = desc,
        suspicionLevel = suspLevel,
        status = status,
        photoUrl = photo,
        updatedAt = now
      )
    yield suspectRepository.update(updatedSuspect)

  def deleteSuspect(currentUser: User, suspectId: UUID): Either[AppError, Unit] =
    for
      existing <- suspectRepository.findById(suspectId).toRight(AppError.NotFoundError("Sospechoso no encontrado"))
      caseItem <- caseRepository.findById(existing.caseId).toRight(AppError.NotFoundError("Caso no encontrado"))
      _ <- AccessPolicy.canAccessCase(currentUser, caseItem)
    yield
      // Regla 7 del punto 7: Eliminar sospechoso desvincula evidencias asociadas
      suspectRepository.deactivateAndUnlinkEvidences(suspectId)
      ()

class EvidenceService(
    evidenceRepository: EvidenceRepository,
    caseRepository: CaseRepository,
    suspectRepository: SuspectRepository
):

  def listEvidences(currentUser: User, filter: EvidenceFilter, page: PageRequest): Either[AppError, PageResult[Evidence]] =
    val scopedFilter =
      if currentUser.role == Role.USER then filter.copy(ownerId = Some(currentUser.id))
      else filter
    Right(evidenceRepository.findAll(scopedFilter, page))

  def getEvidenceById(currentUser: User, evidenceId: UUID): Either[AppError, Evidence] =
    for
      evidence <- evidenceRepository.findById(evidenceId).toRight(AppError.NotFoundError("Evidencia no encontrada"))
      caseItem <- caseRepository.findById(evidence.caseId).toRight(AppError.NotFoundError("Caso no encontrado"))
      _ <- AccessPolicy.canAccessCase(currentUser, caseItem)
    yield evidence

  def createEvidence(currentUser: User, input: EvidenceInput): Either[AppError, Evidence] =
    for
      validated <- Validators.validateEvidence(input, requireCaseId = true)
      (caseIdOpt, suspectIdOpt, title, desc, evType, collAt, rel, img) = validated
      cid = caseIdOpt.get
      caseItem <- caseRepository.findById(cid).toRight(AppError.NotFoundError("Caso no encontrado"))
      _ <- AccessPolicy.canAccessCase(currentUser, caseItem)
      // Regla 3 del punto 7: Si el caso está CLOSED no se pueden crear evidencias (409)
      _ <- if caseItem.status == CaseStatus.CLOSED then
        Left(AppError.ConflictError("No se pueden agregar evidencias a un caso cerrado"))
      else Right(())
      // Regla 4 del punto 7: El suspectId de una evidencia debe pertenecer al mismo caseId (400)
      _ <- suspectIdOpt match
        case Some(sid) =>
          suspectRepository.findById(sid) match
            case Some(s) if s.caseId == cid => Right(())
            case _ =>
              Left(AppError.ValidationError(
                "El sospechoso no pertenece al caso especificado",
                List(FieldError("suspectId", "El sospechoso debe pertenecer al mismo caseId"))
              ))
        case None => Right(())
      now = Instant.now()
      newEvidence = Evidence(
        id = UUID.randomUUID(),
        caseId = cid,
        suspectId = suspectIdOpt,
        title = title,
        description = desc,
        `type` = evType,
        collectedAt = collAt,
        relevance = rel,
        imageUrl = img,
        state = EntityState.ACTIVE,
        createdAt = now,
        updatedAt = now
      )
    yield evidenceRepository.create(newEvidence)

  def updateEvidence(currentUser: User, evidenceId: UUID, input: EvidenceInput): Either[AppError, Evidence] =
    for
      existing <- evidenceRepository.findById(evidenceId).toRight(AppError.NotFoundError("Evidencia no encontrada"))
      caseItem <- caseRepository.findById(existing.caseId).toRight(AppError.NotFoundError("Caso no encontrado"))
      _ <- AccessPolicy.canAccessCase(currentUser, caseItem)
      validated <- Validators.validateEvidence(input, requireCaseId = false)
      (_, suspectIdOpt, title, desc, evType, collAt, rel, img) = validated
      // Regla 4 del punto 7: El suspectId de una evidencia debe pertenecer al mismo caseId (400)
      _ <- suspectIdOpt match
        case Some(sid) =>
          suspectRepository.findById(sid) match
            case Some(s) if s.caseId == existing.caseId => Right(())
            case _ =>
              Left(AppError.ValidationError(
                "El sospechoso no pertenece al caso especificado",
                List(FieldError("suspectId", "El sospechoso debe pertenecer al mismo caseId"))
              ))
        case None => Right(())
      now = Instant.now()
      updatedEvidence = existing.copy(
        suspectId = suspectIdOpt,
        title = title,
        description = desc,
        `type` = evType,
        collectedAt = collAt,
        relevance = rel,
        imageUrl = img,
        updatedAt = now
      )
    yield evidenceRepository.update(updatedEvidence)

  def deleteEvidence(currentUser: User, evidenceId: UUID): Either[AppError, Unit] =
    for
      existing <- evidenceRepository.findById(evidenceId).toRight(AppError.NotFoundError("Evidencia no encontrada"))
      caseItem <- caseRepository.findById(existing.caseId).toRight(AppError.NotFoundError("Caso no encontrado"))
      _ <- AccessPolicy.canAccessCase(currentUser, caseItem)
    yield
      evidenceRepository.deactivate(evidenceId)
      ()

final case class UploadResult(url: String, path: String)

class UploadService(config: AppConfig):

  private val MaxSizeBytes = 5 * 1024 * 1024 // 5 MB
  private val AllowedFolders = Set("cases", "suspects", "evidences")

  def uploadFile(
      folderOpt: Option[String],
      inputStream: InputStream,
      declaredSize: Long
  ): Either[AppError, UploadResult] =
    val folder = folderOpt.map(_.trim.toLowerCase).filter(_.nonEmpty).getOrElse("cases")
    if !AllowedFolders.contains(folder) then
      Left(AppError.ValidationError("Carpeta de destino no permitida", List(FieldError("folder", "Debe ser cases, suspects o evidences"))))
    else
      // Leer primeros bytes para validar firma
      val bytes = inputStream.readAllBytes()
      if bytes.length == 0 then
        Left(AppError.ValidationError("El archivo no puede estar vacío", List(FieldError("file", "Archivo ausente o vacío"))))
      else if bytes.length > MaxSizeBytes then
        Left(AppError.PayloadTooLargeError("El archivo excede el tamaño máximo permitido de 5 MB"))
      else
        detectExtension(bytes) match
          case None =>
            Left(AppError.ValidationError("Tipo de archivo no permitido", List(FieldError("file", "Solo se permiten imágenes JPEG, PNG o WEBP con firma válida"))))
          case Some(ext) =>
            val filename = s"${UUID.randomUUID()}.$ext"
            val targetDir = Paths.get(config.uploadDir, folder)
            val targetFile = targetDir.resolve(filename)
            Try {
              Files.createDirectories(targetDir)
              Files.write(targetFile, bytes)
            }.toEither.left.map(_ => AppError.InternalError("Error al guardar archivo en disco")).map { _ =>
              val relPath = s"$folder/$filename"
              val url = s"${config.publicBaseUrl}/uploads/$relPath"
              UploadResult(url, relPath)
            }

  def detectExtension(bytes: Array[Byte]): Option[String] =
    if bytes.length >= 3 &&
       (bytes(0) & 0xFF) == 0xFF && (bytes(1) & 0xFF) == 0xD8 && (bytes(2) & 0xFF) == 0xFF then
      Some("jpg")
    else if bytes.length >= 8 &&
      (bytes(0) & 0xFF) == 0x89 && (bytes(1) & 0xFF) == 0x50 && (bytes(2) & 0xFF) == 0x4E && (bytes(3) & 0xFF) == 0x47 &&
      (bytes(4) & 0xFF) == 0x0D && (bytes(5) & 0xFF) == 0x0A && (bytes(6) & 0xFF) == 0x1A && (bytes(7) & 0xFF) == 0x0A then
      Some("png")
    else if bytes.length >= 12 &&
      bytes(0) == 'R'.toByte && bytes(1) == 'I'.toByte && bytes(2) == 'F'.toByte && bytes(3) == 'F'.toByte &&
      bytes(8) == 'W'.toByte && bytes(9) == 'E'.toByte && bytes(10) == 'B'.toByte && bytes(11) == 'P'.toByte then
      Some("webp")
    else None

final case class BenchmarkTimings(generationNanos: Long, pipelineNanos: Long)
final case class JvmMemory(heapUsedBytes: Long, heapMaxBytes: Long)
final case class BenchmarkResult(
    scenario: String,
    seed: Long,
    counts: Map[String, Long],
    processedRecords: Long,
    timings: BenchmarkTimings,
    jvm: JvmMemory,
    checksum: String,
    report: Option[List[String]]
)

final case class OverviewTimings(loadNanos: Long, pipelineNanos: Long)
final case class OverviewResult(
    processedRecords: Long,
    timings: OverviewTimings,
    checksum: String,
    report: List[String]
)

class BenchmarkService(analyticsRepository: AnalyticsRepository):

  def runBenchmark(scenario: String, seed: Long, includeReport: Boolean): BenchmarkResult =
    val t0 = System.nanoTime()
    val (cases, suspects, evidences) = ScenarioGenerator.generate(scenario, seed)
    val t1 = System.nanoTime()

    val t2 = System.nanoTime()
    val report = AnalyticsPipeline.run(cases, suspects, evidences)
    val lines = ReportFormatter.formatReport(report)
    val checksum = ReportFormatter.computeChecksum(lines)
    val t3 = System.nanoTime()

    val generationNanos = t1 - t0
    val pipelineNanos = t3 - t2
    val processedRecords = cases.size.toLong + suspects.size.toLong + evidences.size.toLong

    val runtime = Runtime.getRuntime
    val heapUsed = runtime.totalMemory() - runtime.freeMemory()
    val heapMax = runtime.maxMemory()

    BenchmarkResult(
      scenario = scenario,
      seed = seed,
      counts = Map(
        "cases" -> cases.size.toLong,
        "suspects" -> suspects.size.toLong,
        "evidences" -> evidences.size.toLong
      ),
      processedRecords = processedRecords,
      timings = BenchmarkTimings(generationNanos, pipelineNanos),
      jvm = JvmMemory(heapUsed, heapMax),
      checksum = checksum,
      report = if includeReport then Some(lines) else None
    )

  def runOverview(): OverviewResult =
    val t0 = System.nanoTime()
    val (cases, suspects, evidences) = analyticsRepository.fetchActiveRecords()
    val t1 = System.nanoTime()

    val t2 = System.nanoTime()
    val report = AnalyticsPipeline.run(cases, suspects, evidences)
    val lines = ReportFormatter.formatReport(report)
    val checksum = ReportFormatter.computeChecksum(lines)
    val t3 = System.nanoTime()

    val loadNanos = t1 - t0
    val pipelineNanos = t3 - t2
    val processedRecords = cases.size.toLong + suspects.size.toLong + evidences.size.toLong

    OverviewResult(
      processedRecords = processedRecords,
      timings = OverviewTimings(loadNanos, pipelineNanos),
      checksum = checksum,
      report = lines
    )
