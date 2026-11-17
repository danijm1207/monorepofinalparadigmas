package edu.investigacion.web

import scala.util.Try
import io.javalin.http.Context
import edu.investigacion.application.*
import edu.investigacion.domain.*

class AuthController(authService: AuthService):

  def register(ctx: Context): Unit =
    Try(JsonSupport.mapper.readTree(ctx.body())).toOption match
      case None =>
        Middlewares.respondError(ctx, AppError.ValidationError("Cuerpo de petición inválido", List(FieldError("body", "Formato JSON inválido"))))
      case Some(node) =>
        val name = Option(node.get("name")).map(_.asText("")).getOrElse("")
        val email = Option(node.get("email")).map(_.asText("")).getOrElse("")
        val pwd = Option(node.get("password")).map(_.asText("")).getOrElse("")
        val input = RegisterInput(name, email, pwd)
        authService.register(input) match
          case Right((user, token)) =>
            ctx.status(201)
            ctx.contentType("application/json")
            ctx.result(JsonSupport.authResponse(user, token).toString)
          case Left(err) =>
            Middlewares.respondError(ctx, err)

  def login(ctx: Context): Unit =
    Try(JsonSupport.mapper.readTree(ctx.body())).toOption match
      case None =>
        Middlewares.respondError(ctx, AppError.ValidationError("Cuerpo de petición inválido", List(FieldError("body", "Formato JSON inválido"))))
      case Some(node) =>
        val email = Option(node.get("email")).map(_.asText("")).getOrElse("")
        val pwd = Option(node.get("password")).map(_.asText("")).getOrElse("")
        val input = LoginInput(email, pwd)
        authService.login(input) match
          case Right((user, token)) =>
            ctx.status(200)
            ctx.contentType("application/json")
            ctx.result(JsonSupport.authResponse(user, token).toString)
          case Left(err) =>
            Middlewares.respondError(ctx, err)

  def me(ctx: Context): Unit =
    val currentUser = ctx.attribute[User]("currentUser")
    ctx.status(200)
    ctx.contentType("application/json")
    ctx.result(JsonSupport.singleResource(currentUser, JsonSupport.userToJson).toString)

class UserController(userService: UserService):

  def listUsers(ctx: Context): Unit =
    val currentUser = ctx.attribute[User]("currentUser")
    val q = Option(ctx.queryParam("q")).map(_.trim).filter(_.nonEmpty)
    val role = Option(ctx.queryParam("role")).map(_.trim.toUpperCase).flatMap(r => Try(Role.valueOf(r)).toOption)
    val filter = UserFilter(q, role)

    Validators.validatePageRequest(Option(ctx.queryParam("page")), Option(ctx.queryParam("limit"))) match
      case Left(err) => Middlewares.respondError(ctx, err)
      case Right(pageReq) =>
        userService.listUsers(currentUser, filter, pageReq) match
          case Right(paged) =>
            ctx.status(200)
            ctx.contentType("application/json")
            ctx.result(JsonSupport.pagedResource(paged, JsonSupport.userToJson).toString)
          case Left(err) =>
            Middlewares.respondError(ctx, err)

  def deactivateUser(ctx: Context): Unit =
    val currentUser = ctx.attribute[User]("currentUser")
    val idStr = ctx.pathParam("id")
    Validators.parseUuid(idStr, "id") match
      case Right(targetId) =>
        userService.deactivateUser(currentUser, targetId) match
          case Right(_)  => ctx.status(204)
          case Left(err) => Middlewares.respondError(ctx, err)
      case Left(fieldErr) =>
        Middlewares.respondError(ctx, AppError.ValidationError("ID inválido", List(fieldErr)))

class CaseController(caseService: CaseService):

  def listCases(ctx: Context): Unit =
    val currentUser = ctx.attribute[User]("currentUser")
    val q = Option(ctx.queryParam("q")).map(_.trim).filter(_.nonEmpty)
    val status = Option(ctx.queryParam("status")).map(_.trim.toUpperCase).flatMap(s => Try(CaseStatus.valueOf(s)).toOption)
    val from = Option(ctx.queryParam("from")).flatMap(s => Validators.parseInstant(s, "from").toOption)
    val to = Option(ctx.queryParam("to")).flatMap(s => Validators.parseInstant(s, "to").toOption)
    val ownerId = Option(ctx.queryParam("ownerId")).flatMap(s => Validators.parseUuid(s, "ownerId").toOption)
    val filter = CaseFilter(q, status, from, to, ownerId)

    Validators.validatePageRequest(Option(ctx.queryParam("page")), Option(ctx.queryParam("limit"))) match
      case Left(err) => Middlewares.respondError(ctx, err)
      case Right(pageReq) =>
        caseService.listCases(currentUser, filter, pageReq) match
          case Right(paged) =>
            ctx.status(200)
            ctx.contentType("application/json")
            ctx.result(JsonSupport.pagedResource(paged, JsonSupport.caseToJson).toString)
          case Left(err) =>
            Middlewares.respondError(ctx, err)

  def getCase(ctx: Context): Unit =
    val currentUser = ctx.attribute[User]("currentUser")
    Validators.parseUuid(ctx.pathParam("id"), "id") match
      case Right(cid) =>
        caseService.getCaseById(currentUser, cid) match
          case Right(c)   =>
            ctx.status(200)
            ctx.contentType("application/json")
            ctx.result(JsonSupport.singleResource(c, JsonSupport.caseToJson).toString)
          case Left(err)  => Middlewares.respondError(ctx, err)
      case Left(fieldErr) =>
        Middlewares.respondError(ctx, AppError.ValidationError("ID inválido", List(fieldErr)))

  def createCase(ctx: Context): Unit =
    val currentUser = ctx.attribute[User]("currentUser")
    Try(JsonSupport.mapper.readTree(ctx.body())).toOption match
      case None =>
        Middlewares.respondError(ctx, AppError.ValidationError("Cuerpo de petición inválido", List(FieldError("body", "Formato JSON inválido"))))
      case Some(node) =>
        val title = Option(node.get("title")).map(_.asText("")).getOrElse("")
        val desc = Option(node.get("description")).map(_.asText("")).getOrElse("")
        val loc = Option(node.get("location")).map(_.asText("")).getOrElse("")
        val incDate = Option(node.get("incidentDate")).map(_.asText("")).getOrElse("")
        val status = Option(node.get("status")).map(_.asText(""))
        val img = Option(node.get("imageUrl")).map(_.asText("")).filter(_.nonEmpty)
        val input = CaseInput(title, desc, loc, incDate, status, img)

        caseService.createCase(currentUser, input) match
          case Right(created) =>
            ctx.status(201)
            ctx.contentType("application/json")
            ctx.result(JsonSupport.singleResource(created, JsonSupport.caseToJson).toString)
          case Left(err) =>
            Middlewares.respondError(ctx, err)

  def updateCase(ctx: Context): Unit =
    val currentUser = ctx.attribute[User]("currentUser")
    Validators.parseUuid(ctx.pathParam("id"), "id") match
      case Right(cid) =>
        Try(JsonSupport.mapper.readTree(ctx.body())).toOption match
          case None =>
            Middlewares.respondError(ctx, AppError.ValidationError("Cuerpo de petición inválido", List(FieldError("body", "Formato JSON inválido"))))
          case Some(node) =>
            val title = Option(node.get("title")).map(_.asText("")).getOrElse("")
            val desc = Option(node.get("description")).map(_.asText("")).getOrElse("")
            val loc = Option(node.get("location")).map(_.asText("")).getOrElse("")
            val incDate = Option(node.get("incidentDate")).map(_.asText("")).getOrElse("")
            val status = Option(node.get("status")).map(_.asText(""))
            val img = Option(node.get("imageUrl")).map(_.asText("")).filter(_.nonEmpty)
            val input = CaseInput(title, desc, loc, incDate, status, img)

            caseService.updateCase(currentUser, cid, input) match
              case Right(updated) =>
                ctx.status(200)
                ctx.contentType("application/json")
                ctx.result(JsonSupport.singleResource(updated, JsonSupport.caseToJson).toString)
              case Left(err) =>
                Middlewares.respondError(ctx, err)
      case Left(fieldErr) =>
        Middlewares.respondError(ctx, AppError.ValidationError("ID inválido", List(fieldErr)))

  def deleteCase(ctx: Context): Unit =
    val currentUser = ctx.attribute[User]("currentUser")
    Validators.parseUuid(ctx.pathParam("id"), "id") match
      case Right(cid) =>
        caseService.deleteCase(currentUser, cid) match
          case Right(_)  => ctx.status(204)
          case Left(err) => Middlewares.respondError(ctx, err)
      case Left(fieldErr) =>
        Middlewares.respondError(ctx, AppError.ValidationError("ID inválido", List(fieldErr)))

class SuspectController(suspectService: SuspectService):

  def listSuspects(ctx: Context): Unit =
    val currentUser = ctx.attribute[User]("currentUser")
    val caseId = Option(ctx.queryParam("caseId")).flatMap(s => Validators.parseUuid(s, "caseId").toOption)
    val status = Option(ctx.queryParam("status")).map(_.trim.toUpperCase).flatMap(s => Try(SuspectStatus.valueOf(s)).toOption)
    val q = Option(ctx.queryParam("q")).map(_.trim).filter(_.nonEmpty)
    val filter = SuspectFilter(caseId, status, q)

    Validators.validatePageRequest(Option(ctx.queryParam("page")), Option(ctx.queryParam("limit"))) match
      case Left(err) => Middlewares.respondError(ctx, err)
      case Right(pageReq) =>
        suspectService.listSuspects(currentUser, filter, pageReq) match
          case Right(paged) =>
            ctx.status(200)
            ctx.contentType("application/json")
            ctx.result(JsonSupport.pagedResource(paged, JsonSupport.suspectToJson).toString)
          case Left(err) =>
            Middlewares.respondError(ctx, err)

  def getSuspect(ctx: Context): Unit =
    val currentUser = ctx.attribute[User]("currentUser")
    Validators.parseUuid(ctx.pathParam("id"), "id") match
      case Right(sid) =>
        suspectService.getSuspectById(currentUser, sid) match
          case Right(s)   =>
            ctx.status(200)
            ctx.contentType("application/json")
            ctx.result(JsonSupport.singleResource(s, JsonSupport.suspectToJson).toString)
          case Left(err)  => Middlewares.respondError(ctx, err)
      case Left(fieldErr) =>
        Middlewares.respondError(ctx, AppError.ValidationError("ID inválido", List(fieldErr)))

  def createSuspect(ctx: Context): Unit =
    val currentUser = ctx.attribute[User]("currentUser")
    Try(JsonSupport.mapper.readTree(ctx.body())).toOption match
      case None =>
        Middlewares.respondError(ctx, AppError.ValidationError("Cuerpo de petición inválido", List(FieldError("body", "Formato JSON inválido"))))
      case Some(node) =>
        val caseId = Option(node.get("caseId")).map(_.asText(""))
        val fullName = Option(node.get("fullName")).map(_.asText("")).getOrElse("")
        val alias = Option(node.get("alias")).map(_.asText("")).filter(_.nonEmpty)
        val desc = Option(node.get("description")).map(_.asText("")).filter(_.nonEmpty)
        val suspLevel = Option(node.get("suspicionLevel")).map(_.asInt())
        val status = Option(node.get("status")).map(_.asText(""))
        val photo = Option(node.get("photoUrl")).map(_.asText("")).filter(_.nonEmpty)
        val input = SuspectInput(caseId, fullName, alias, desc, suspLevel, status, photo)

        suspectService.createSuspect(currentUser, input) match
          case Right(created) =>
            ctx.status(201)
            ctx.contentType("application/json")
            ctx.result(JsonSupport.singleResource(created, JsonSupport.suspectToJson).toString)
          case Left(err) =>
            Middlewares.respondError(ctx, err)

  def updateSuspect(ctx: Context): Unit =
    val currentUser = ctx.attribute[User]("currentUser")
    Validators.parseUuid(ctx.pathParam("id"), "id") match
      case Right(sid) =>
        Try(JsonSupport.mapper.readTree(ctx.body())).toOption match
          case None =>
            Middlewares.respondError(ctx, AppError.ValidationError("Cuerpo de petición inválido", List(FieldError("body", "Formato JSON inválido"))))
          case Some(node) =>
            val fullName = Option(node.get("fullName")).map(_.asText("")).getOrElse("")
            val alias = Option(node.get("alias")).map(_.asText("")).filter(_.nonEmpty)
            val desc = Option(node.get("description")).map(_.asText("")).filter(_.nonEmpty)
            val suspLevel = Option(node.get("suspicionLevel")).map(_.asInt())
            val status = Option(node.get("status")).map(_.asText(""))
            val photo = Option(node.get("photoUrl")).map(_.asText("")).filter(_.nonEmpty)
            val input = SuspectInput(None, fullName, alias, desc, suspLevel, status, photo)

            suspectService.updateSuspect(currentUser, sid, input) match
              case Right(updated) =>
                ctx.status(200)
                ctx.contentType("application/json")
                ctx.result(JsonSupport.singleResource(updated, JsonSupport.suspectToJson).toString)
              case Left(err) =>
                Middlewares.respondError(ctx, err)
      case Left(fieldErr) =>
        Middlewares.respondError(ctx, AppError.ValidationError("ID inválido", List(fieldErr)))

  def deleteSuspect(ctx: Context): Unit =
    val currentUser = ctx.attribute[User]("currentUser")
    Validators.parseUuid(ctx.pathParam("id"), "id") match
      case Right(sid) =>
        suspectService.deleteSuspect(currentUser, sid) match
          case Right(_)  => ctx.status(204)
          case Left(err) => Middlewares.respondError(ctx, err)
      case Left(fieldErr) =>
        Middlewares.respondError(ctx, AppError.ValidationError("ID inválido", List(fieldErr)))

class EvidenceController(evidenceService: EvidenceService):

  def listEvidences(ctx: Context): Unit =
    val currentUser = ctx.attribute[User]("currentUser")
    val caseId = Option(ctx.queryParam("caseId")).flatMap(s => Validators.parseUuid(s, "caseId").toOption)
    val suspectId = Option(ctx.queryParam("suspectId")).flatMap(s => Validators.parseUuid(s, "suspectId").toOption)
    val typeFilter = Option(ctx.queryParam("type")).map(_.trim.toUpperCase).flatMap(t => Try(EvidenceType.valueOf(t)).toOption)
    val q = Option(ctx.queryParam("q")).map(_.trim).filter(_.nonEmpty)
    val filter = EvidenceFilter(caseId, suspectId, typeFilter, q)

    Validators.validatePageRequest(Option(ctx.queryParam("page")), Option(ctx.queryParam("limit"))) match
      case Left(err) => Middlewares.respondError(ctx, err)
      case Right(pageReq) =>
        evidenceService.listEvidences(currentUser, filter, pageReq) match
          case Right(paged) =>
            ctx.status(200)
            ctx.contentType("application/json")
            ctx.result(JsonSupport.pagedResource(paged, JsonSupport.evidenceToJson).toString)
          case Left(err) =>
            Middlewares.respondError(ctx, err)

  def getEvidence(ctx: Context): Unit =
    val currentUser = ctx.attribute[User]("currentUser")
    Validators.parseUuid(ctx.pathParam("id"), "id") match
      case Right(eid) =>
        evidenceService.getEvidenceById(currentUser, eid) match
          case Right(e)   =>
            ctx.status(200)
            ctx.contentType("application/json")
            ctx.result(JsonSupport.singleResource(e, JsonSupport.evidenceToJson).toString)
          case Left(err)  => Middlewares.respondError(ctx, err)
      case Left(fieldErr) =>
        Middlewares.respondError(ctx, AppError.ValidationError("ID inválido", List(fieldErr)))

  def createEvidence(ctx: Context): Unit =
    val currentUser = ctx.attribute[User]("currentUser")
    Try(JsonSupport.mapper.readTree(ctx.body())).toOption match
      case None =>
        Middlewares.respondError(ctx, AppError.ValidationError("Cuerpo de petición inválido", List(FieldError("body", "Formato JSON inválido"))))
      case Some(node) =>
        val caseId = Option(node.get("caseId")).map(_.asText(""))
        val suspectId = Option(node.get("suspectId")).map(_.asText("")).filter(_.nonEmpty)
        val title = Option(node.get("title")).map(_.asText("")).getOrElse("")
        val desc = Option(node.get("description")).map(_.asText("")).getOrElse("")
        val evType = Option(node.get("type")).map(_.asText("")).getOrElse("")
        val collAt = Option(node.get("collectedAt")).map(_.asText("")).getOrElse("")
        val relevance = Option(node.get("relevance")).map(_.asInt()).getOrElse(0)
        val img = Option(node.get("imageUrl")).map(_.asText("")).filter(_.nonEmpty)
        val input = EvidenceInput(caseId, suspectId, title, desc, evType, collAt, relevance, img)

        evidenceService.createEvidence(currentUser, input) match
          case Right(created) =>
            ctx.status(201)
            ctx.contentType("application/json")
            ctx.result(JsonSupport.singleResource(created, JsonSupport.evidenceToJson).toString)
          case Left(err) =>
            Middlewares.respondError(ctx, err)

  def updateEvidence(ctx: Context): Unit =
    val currentUser = ctx.attribute[User]("currentUser")
    Validators.parseUuid(ctx.pathParam("id"), "id") match
      case Right(eid) =>
        Try(JsonSupport.mapper.readTree(ctx.body())).toOption match
          case None =>
            Middlewares.respondError(ctx, AppError.ValidationError("Cuerpo de petición inválido", List(FieldError("body", "Formato JSON inválido"))))
          case Some(node) =>
            val suspectId = Option(node.get("suspectId")).map(_.asText("")).filter(_.nonEmpty)
            val title = Option(node.get("title")).map(_.asText("")).getOrElse("")
            val desc = Option(node.get("description")).map(_.asText("")).getOrElse("")
            val evType = Option(node.get("type")).map(_.asText("")).getOrElse("")
            val collAt = Option(node.get("collectedAt")).map(_.asText("")).getOrElse("")
            val relevance = Option(node.get("relevance")).map(_.asInt()).getOrElse(0)
            val img = Option(node.get("imageUrl")).map(_.asText("")).filter(_.nonEmpty)
            val input = EvidenceInput(None, suspectId, title, desc, evType, collAt, relevance, img)

            evidenceService.updateEvidence(currentUser, eid, input) match
              case Right(updated) =>
                ctx.status(200)
                ctx.contentType("application/json")
                ctx.result(JsonSupport.singleResource(updated, JsonSupport.evidenceToJson).toString)
              case Left(err) =>
                Middlewares.respondError(ctx, err)
      case Left(fieldErr) =>
        Middlewares.respondError(ctx, AppError.ValidationError("ID inválido", List(fieldErr)))

  def deleteEvidence(ctx: Context): Unit =
    val currentUser = ctx.attribute[User]("currentUser")
    Validators.parseUuid(ctx.pathParam("id"), "id") match
      case Right(eid) =>
        evidenceService.deleteEvidence(currentUser, eid) match
          case Right(_)  => ctx.status(204)
          case Left(err) => Middlewares.respondError(ctx, err)
      case Left(fieldErr) =>
        Middlewares.respondError(ctx, AppError.ValidationError("ID inválido", List(fieldErr)))

class UploadController(uploadService: UploadService):

  def upload(ctx: Context): Unit =
    val file = ctx.uploadedFile("file")
    if file == null then
      Middlewares.respondError(ctx, AppError.ValidationError("Archivo ausente", List(FieldError("file", "El campo 'file' es obligatorio"))))
    else
      val folderOpt = Option(ctx.formParam("folder"))
      uploadService.uploadFile(folderOpt, file.content(), file.size()) match
        case Right(res) =>
          ctx.status(201)
          ctx.contentType("application/json")
          ctx.result(JsonSupport.uploadResponse(res).toString)
        case Left(err) =>
          Middlewares.respondError(ctx, err)

class AnalyticsController(benchmarkService: BenchmarkService):

  def runBenchmark(ctx: Context): Unit =
    val includeReport = ctx.queryParam("includeReport") == "true"
    Try(JsonSupport.mapper.readTree(ctx.body())).toOption match
      case None =>
        Middlewares.respondError(ctx, AppError.ValidationError("Cuerpo inválido", List(FieldError("body", "Formato JSON inválido"))))
      case Some(node) =>
        val scenario = Option(node.get("scenario")).map(_.asText("")).getOrElse("")
        val seed = Option(node.get("seed")).map(_.asLong())
        val input = BenchmarkInput(scenario, seed)

        Validators.validateBenchmark(input) match
          case Right((sc, s)) =>
            val result = benchmarkService.runBenchmark(sc, s, includeReport)
            ctx.status(200)
            ctx.contentType("application/json")
            ctx.result(JsonSupport.benchmarkResponse(result).toString)
          case Left(err) =>
            Middlewares.respondError(ctx, err)

  def overview(ctx: Context): Unit =
    val result = benchmarkService.runOverview()
    ctx.status(200)
    ctx.contentType("application/json")
    ctx.result(JsonSupport.overviewResponse(result).toString)
