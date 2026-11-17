package edu.investigacion.web

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import edu.investigacion.application.{BenchmarkResult, OverviewResult, UploadResult}
import edu.investigacion.domain.*

object JsonSupport:

  val mapper: ObjectMapper = new ObjectMapper()

  // Formato exacto requerido: UTC con milisegundos y Z
  val IsoUtcFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

  def formatInstant(instant: Instant): String =
    IsoUtcFormatter.format(instant)

  def userToJson(u: User): ObjectNode =
    val node = mapper.createObjectNode()
    node.put("id", u.id.toString)
    node.put("name", u.name)
    node.put("email", u.email)
    node.put("role", u.role.toString)
    node.put("createdAt", formatInstant(u.createdAt))
    node

  def caseToJson(c: Case): ObjectNode =
    val node = mapper.createObjectNode()
    node.put("id", c.id.toString)
    node.put("caseNumber", c.caseNumber)
    node.put("title", c.title)
    node.put("description", c.description)
    node.put("location", c.location)
    node.put("incidentDate", formatInstant(c.incidentDate))
    node.put("status", c.status.toString)
    c.imageUrl match
      case Some(url) => node.put("imageUrl", url)
      case None      => node.putNull("imageUrl")
    node.put("ownerId", c.ownerId.toString)
    node.put("createdAt", formatInstant(c.createdAt))
    node.put("updatedAt", formatInstant(c.updatedAt))
    node

  def suspectToJson(s: Suspect): ObjectNode =
    val node = mapper.createObjectNode()
    node.put("id", s.id.toString)
    node.put("caseId", s.caseId.toString)
    node.put("fullName", s.fullName)
    s.alias match
      case Some(a) => node.put("alias", a)
      case None    => node.putNull("alias")
    s.description match
      case Some(d) => node.put("description", d)
      case None    => node.putNull("description")
    node.put("suspicionLevel", s.suspicionLevel)
    node.put("status", s.status.toString)
    s.photoUrl match
      case Some(p) => node.put("photoUrl", p)
      case None    => node.putNull("photoUrl")
    node.put("createdAt", formatInstant(s.createdAt))
    node.put("updatedAt", formatInstant(s.updatedAt))
    node

  def evidenceToJson(e: Evidence): ObjectNode =
    val node = mapper.createObjectNode()
    node.put("id", e.id.toString)
    node.put("caseId", e.caseId.toString)
    e.suspectId match
      case Some(sid) => node.put("suspectId", sid.toString)
      case None      => node.putNull("suspectId")
    node.put("title", e.title)
    node.put("description", e.description)
    node.put("type", e.`type`.toString)
    node.put("collectedAt", formatInstant(e.collectedAt))
    node.put("relevance", e.relevance)
    e.imageUrl match
      case Some(url) => node.put("imageUrl", url)
      case None      => node.putNull("imageUrl")
    node.put("createdAt", formatInstant(e.createdAt))
    node.put("updatedAt", formatInstant(e.updatedAt))
    node

  def singleResource[T](item: T, serializer: T => ObjectNode): ObjectNode =
    val root = mapper.createObjectNode()
    root.set("data", serializer(item))
    root

  def pagedResource[T](paged: PageResult[T], serializer: T => ObjectNode): ObjectNode =
    val root = mapper.createObjectNode()
    val dataArr = mapper.createArrayNode()
    paged.data.foreach(item => dataArr.add(serializer(item)))
    root.set("data", dataArr)

    val pagination = mapper.createObjectNode()
    pagination.put("total", paged.pagination.total)
    pagination.put("currentPage", paged.pagination.currentPage)
    pagination.put("limit", paged.pagination.limit)
    pagination.put("totalPages", paged.pagination.totalPages)
    root.set("pagination", pagination)

    root

  def authResponse(user: User, token: String): ObjectNode =
    val root = mapper.createObjectNode()
    root.set("user", userToJson(user))
    root.put("token", token)
    root

  def uploadResponse(res: UploadResult): ObjectNode =
    val root = mapper.createObjectNode()
    val data = mapper.createObjectNode()
    data.put("url", res.url)
    data.put("path", res.path)
    root.set("data", data)
    root

  def benchmarkResponse(res: BenchmarkResult): ObjectNode =
    val root = mapper.createObjectNode()
    val data = mapper.createObjectNode()
    data.put("scenario", res.scenario)
    data.put("seed", res.seed)

    val countsNode = mapper.createObjectNode()
    countsNode.put("cases", res.counts("cases"))
    countsNode.put("suspects", res.counts("suspects"))
    countsNode.put("evidences", res.counts("evidences"))
    data.set("counts", countsNode)

    data.put("processedRecords", res.processedRecords)

    val timingsNode = mapper.createObjectNode()
    timingsNode.put("generationNanos", res.timings.generationNanos)
    timingsNode.put("pipelineNanos", res.timings.pipelineNanos)
    data.set("timings", timingsNode)

    val jvmNode = mapper.createObjectNode()
    jvmNode.put("heapUsedBytes", res.jvm.heapUsedBytes)
    jvmNode.put("heapMaxBytes", res.jvm.heapMaxBytes)
    data.set("jvm", jvmNode)

    data.put("checksum", res.checksum)

    res.report.foreach { lines =>
      val repArr = mapper.createArrayNode()
      lines.foreach(repArr.add)
      data.set("report", repArr)
    }

    root.set("data", data)
    root

  def overviewResponse(res: OverviewResult): ObjectNode =
    val root = mapper.createObjectNode()
    val data = mapper.createObjectNode()
    data.put("processedRecords", res.processedRecords)

    val timingsNode = mapper.createObjectNode()
    timingsNode.put("loadNanos", res.timings.loadNanos)
    timingsNode.put("pipelineNanos", res.timings.pipelineNanos)
    data.set("timings", timingsNode)

    data.put("checksum", res.checksum)

    val repArr = mapper.createArrayNode()
    res.report.foreach(repArr.add)
    data.set("report", repArr)

    root.set("data", data)
    root

  def errorResponse(error: AppError): ObjectNode =
    val root = mapper.createObjectNode()
    val errNode = mapper.createObjectNode()
    errNode.put("code", error.code)
    errNode.put("message", error.message)

    error match
      case AppError.ValidationError(_, details) if details.nonEmpty =>
        val detArr = mapper.createArrayNode()
        details.foreach { d =>
          val dNode = mapper.createObjectNode()
          dNode.put("field", d.field)
          dNode.put("message", d.message)
          detArr.add(dNode)
        }
        errNode.set("details", detArr)
      case _ =>

    root.set("error", errNode)
    root
