package edu.investigacion.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.investigacion.application.BenchmarkUseCase.BenchmarkResult;
import edu.investigacion.application.BenchmarkUseCase.OverviewResult;
import edu.investigacion.domain.AppError;
import edu.investigacion.domain.AppError.FieldDetail;
import edu.investigacion.domain.Models.Case;
import edu.investigacion.domain.Models.Evidence;
import edu.investigacion.domain.Models.Page;
import edu.investigacion.domain.Models.Suspect;
import edu.investigacion.domain.Models.User;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class JsonRenderer {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter
        .ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneOffset.UTC);

    private JsonRenderer() {}

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String formatInstant(Instant instant) {
        if (instant == null) return null;
        return ISO_FORMATTER.format(instant);
    }

    public static ObjectNode renderError(AppError error) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode err = root.putObject("error");

        String code = switch (error) {
            case AppError.ValidationError e -> "VALIDATION_ERROR";
            case AppError.Unauthorized e -> "UNAUTHORIZED";
            case AppError.Forbidden e -> "FORBIDDEN";
            case AppError.NotFound e -> "NOT_FOUND";
            case AppError.Conflict e -> "CONFLICT";
            case AppError.PayloadTooLarge e -> "PAYLOAD_TOO_LARGE";
            case AppError.InternalError e -> "INTERNAL_ERROR";
        };

        String message = switch (error) {
            case AppError.ValidationError e -> e.message();
            case AppError.Unauthorized e -> e.message();
            case AppError.Forbidden e -> e.message();
            case AppError.NotFound e -> e.message();
            case AppError.Conflict e -> e.message();
            case AppError.PayloadTooLarge e -> e.message();
            case AppError.InternalError e -> e.message();
        };

        err.put("code", code);
        err.put("message", message);

        if (error instanceof AppError.ValidationError ve && !ve.details().isEmpty()) {
            ArrayNode detailsArr = err.putArray("details");
            for (FieldDetail fd : ve.details()) {
                ObjectNode d = detailsArr.addObject();
                d.put("field", fd.field());
                d.put("message", fd.message());
            }
        }

        return root;
    }

    public static ObjectNode renderUserPublic(User u) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", u.id().toString());
        node.put("name", u.name());
        node.put("email", u.email());
        node.put("role", u.role().name());
        node.put("createdAt", formatInstant(u.createdAt()));
        return node;
    }

    public static ObjectNode renderAuth(User u, String token) {
        ObjectNode root = MAPPER.createObjectNode();
        root.set("user", renderUserPublic(u));
        root.put("token", token);
        return root;
    }

    public static ObjectNode renderCase(Case c) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", c.id().toString());
        node.put("caseNumber", c.caseNumber());
        node.put("title", c.title());
        node.put("description", c.description());
        node.put("location", c.location());
        node.put("incidentDate", formatInstant(c.incidentDate()));
        node.put("status", c.status().name());
        if (c.imageUrl().isPresent()) {
            node.put("imageUrl", c.imageUrl().get());
        } else {
            node.putNull("imageUrl");
        }
        node.put("ownerId", c.ownerId().toString());
        node.put("createdAt", formatInstant(c.createdAt()));
        node.put("updatedAt", formatInstant(c.updatedAt()));
        return node;
    }

    public static ObjectNode renderSuspect(Suspect s) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", s.id().toString());
        node.put("caseId", s.caseId().toString());
        node.put("fullName", s.fullName());
        if (s.alias().isPresent()) node.put("alias", s.alias().get()); else node.putNull("alias");
        if (s.description().isPresent()) node.put("description", s.description().get()); else node.putNull("description");
        node.put("suspicionLevel", s.suspicionLevel());
        node.put("status", s.status().name());
        if (s.photoUrl().isPresent()) node.put("photoUrl", s.photoUrl().get()); else node.putNull("photoUrl");
        node.put("createdAt", formatInstant(s.createdAt()));
        node.put("updatedAt", formatInstant(s.updatedAt()));
        return node;
    }

    public static ObjectNode renderEvidence(Evidence e) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", e.id().toString());
        node.put("caseId", e.caseId().toString());
        if (e.suspectId().isPresent()) node.put("suspectId", e.suspectId().get().toString()); else node.putNull("suspectId");
        node.put("title", e.title());
        node.put("description", e.description());
        node.put("type", e.type().name());
        node.put("collectedAt", formatInstant(e.collectedAt()));
        node.put("relevance", e.relevance());
        if (e.imageUrl().isPresent()) node.put("imageUrl", e.imageUrl().get()); else node.putNull("imageUrl");
        node.put("createdAt", formatInstant(e.createdAt()));
        node.put("updatedAt", formatInstant(e.updatedAt()));
        return node;
    }

    public static <T> ObjectNode renderPage(Page<T> page, java.util.function.Function<T, ObjectNode> itemRenderer) {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode dataArr = root.putArray("data");
        for (T item : page.items()) {
            dataArr.add(itemRenderer.apply(item));
        }
        ObjectNode pag = root.putObject("pagination");
        pag.put("total", page.total());
        pag.put("currentPage", page.currentPage());
        pag.put("limit", page.limit());
        pag.put("totalPages", page.totalPages());
        return root;
    }

    public static ObjectNode renderDataWrapper(ObjectNode data) {
        ObjectNode root = MAPPER.createObjectNode();
        root.set("data", data);
        return root;
    }

    public static ObjectNode renderBenchmarkResult(BenchmarkResult b) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode data = root.putObject("data");
        data.put("scenario", b.scenario());
        data.put("seed", b.seed());

        ObjectNode counts = data.putObject("counts");
        counts.put("cases", b.counts().get("cases"));
        counts.put("suspects", b.counts().get("suspects"));
        counts.put("evidences", b.counts().get("evidences"));

        data.put("processedRecords", b.processedRecords());

        ObjectNode timings = data.putObject("timings");
        timings.put("generationNanos", b.timings().get("generationNanos"));
        timings.put("pipelineNanos", b.timings().get("pipelineNanos"));

        ObjectNode jvm = data.putObject("jvm");
        jvm.put("heapUsedBytes", b.jvm().heapUsedBytes());
        jvm.put("heapMaxBytes", b.jvm().heapMaxBytes());

        data.put("checksum", b.checksum());

        if (b.report().isPresent()) {
            ArrayNode rep = data.putArray("report");
            for (String l : b.report().get()) rep.add(l);
        }

        return root;
    }

    public static ObjectNode renderOverviewResult(OverviewResult o) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode data = root.putObject("data");
        data.put("processedRecords", o.processedRecords());

        ObjectNode timings = data.putObject("timings");
        timings.put("loadNanos", o.timings().get("loadNanos"));
        timings.put("pipelineNanos", o.timings().get("pipelineNanos"));

        data.put("checksum", o.checksum());

        ArrayNode rep = data.putArray("report");
        for (String l : o.report()) rep.add(l);

        return root;
    }
}
