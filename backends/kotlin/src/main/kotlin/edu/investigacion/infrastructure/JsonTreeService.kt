package edu.investigacion.infrastructure

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import edu.investigacion.domain.*
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object JsonTreeService {
    val mapper: ObjectMapper = ObjectMapper()

    private val UTC_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneOffset.UTC)

    fun formatInstant(instant: Instant): String {
        return UTC_FORMATTER.format(instant)
    }

    fun userPublicToJson(user: UserPublic): ObjectNode {
        val node = mapper.createObjectNode()
        node.put("id", user.id)
        node.put("name", user.name)
        node.put("email", user.email)
        node.put("role", user.role.name)
        node.put("createdAt", user.createdAt)
        return node
    }

    fun caseToJson(c: Case): ObjectNode {
        val node = mapper.createObjectNode()
        node.put("id", c.id)
        node.put("caseNumber", c.caseNumber)
        node.put("title", c.title)
        node.put("description", c.description)
        node.put("location", c.location)
        node.put("incidentDate", c.incidentDate)
        node.put("status", c.status.name)
        if (c.imageUrl != null) node.put("imageUrl", c.imageUrl) else node.putNull("imageUrl")
        node.put("ownerId", c.ownerId)
        node.put("createdAt", c.createdAt)
        node.put("updatedAt", c.updatedAt)
        return node
    }

    fun suspectToJson(s: Suspect): ObjectNode {
        val node = mapper.createObjectNode()
        node.put("id", s.id)
        node.put("caseId", s.caseId)
        node.put("fullName", s.fullName)
        if (s.alias != null) node.put("alias", s.alias) else node.putNull("alias")
        if (s.description != null) node.put("description", s.description) else node.putNull("description")
        node.put("suspicionLevel", s.suspicionLevel)
        node.put("status", s.status.name)
        if (s.photoUrl != null) node.put("photoUrl", s.photoUrl) else node.putNull("photoUrl")
        node.put("createdAt", s.createdAt)
        node.put("updatedAt", s.updatedAt)
        return node
    }

    fun evidenceToJson(e: Evidence): ObjectNode {
        val node = mapper.createObjectNode()
        node.put("id", e.id)
        node.put("caseId", e.caseId)
        if (e.suspectId != null) node.put("suspectId", e.suspectId) else node.putNull("suspectId")
        node.put("title", e.title)
        node.put("description", e.description)
        node.put("type", e.type.name)
        node.put("collectedAt", e.collectedAt)
        node.put("relevance", e.relevance)
        if (e.imageUrl != null) node.put("imageUrl", e.imageUrl) else node.putNull("imageUrl")
        node.put("createdAt", e.createdAt)
        node.put("updatedAt", e.updatedAt)
        return node
    }

    fun singleData(child: JsonNode): ObjectNode {
        val root = mapper.createObjectNode()
        root.set<JsonNode>("data", child)
        return root
    }

    fun authResponse(user: UserPublic, token: String): ObjectNode {
        val root = mapper.createObjectNode()
        root.set<JsonNode>("user", userPublicToJson(user))
        root.put("token", token)
        return root
    }

    fun <T> pageResponse(page: Page<T>, itemConverter: (T) -> JsonNode): ObjectNode {
        val root = mapper.createObjectNode()
        val dataArray = root.putArray("data")
        page.data.forEach { dataArray.add(itemConverter(it)) }

        val paginationNode = root.putObject("pagination")
        paginationNode.put("total", page.pagination.total)
        paginationNode.put("currentPage", page.pagination.currentPage)
        paginationNode.put("limit", page.pagination.limit)
        paginationNode.put("totalPages", page.pagination.totalPages)
        return root
    }

    fun errorResponse(code: String, message: String, details: List<FieldError>? = null): ObjectNode {
        val root = mapper.createObjectNode()
        val errorNode = root.putObject("error")
        errorNode.put("code", code)
        errorNode.put("message", message)
        if (!details.isNullOrEmpty()) {
            val detailsArray = errorNode.putArray("details")
            details.forEach {
                val detailObj = detailsArray.addObject()
                detailObj.put("field", it.field)
                detailObj.put("message", it.message)
            }
        }
        return root
    }
}
