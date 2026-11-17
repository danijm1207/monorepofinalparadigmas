package edu.investigacion.web;

import edu.investigacion.domain.AppError;
import edu.investigacion.domain.Result;
import edu.investigacion.infrastructure.StorageService;
import edu.investigacion.infrastructure.StorageService.UploadResult;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;

import java.io.InputStream;
import java.util.Optional;

public final class UploadHandler {
    private final StorageService storageService;
    private final AuthMiddleware authMiddleware;

    public UploadHandler(StorageService storageService, AuthMiddleware authMiddleware) {
        this.storageService = storageService;
        this.authMiddleware = authMiddleware;
    }

    public void upload(Context ctx) {
        if (!authMiddleware.authenticate(ctx)) return;

        UploadedFile file = ctx.uploadedFile("file");
        if (file == null) {
            HttpErrorHandler.respondError(ctx, new AppError.ValidationError("El campo 'file' es requerido"));
            return;
        }

        byte[] bytes;
        try (InputStream is = file.content()) {
            bytes = is.readAllBytes();
        } catch (Exception e) {
            HttpErrorHandler.respondError(ctx, new AppError.InternalError("Error al leer archivo subido"));
            return;
        }

        Optional<String> folder = Optional.ofNullable(ctx.formParam("folder"));
        var res = storageService.saveFile(bytes, folder);
        switch (res) {
            case Result.Success<UploadResult> s -> {
                var node = JsonRenderer.mapper().createObjectNode();
                var data = node.putObject("data");
                data.put("url", s.value().url());
                data.put("path", s.value().path());
                ctx.status(201).json(node);
            }
            case Result.Failure<UploadResult> f -> {
                HttpErrorHandler.respondError(ctx, f.error());
            }
        }
    }

    public void serve(Context ctx) {
        String folder = ctx.pathParam("folder");
        String filename = ctx.pathParam("filename");

        Optional<byte[]> data = storageService.readFile(folder, filename);
        if (data.isEmpty()) {
            ctx.status(404);
            return;
        }

        String ct = "application/octet-stream";
        if (filename.endsWith(".png")) ct = "image/png";
        else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) ct = "image/jpeg";
        else if (filename.endsWith(".webp")) ct = "image/webp";

        ctx.contentType(ct).result(data.get());
    }
}
