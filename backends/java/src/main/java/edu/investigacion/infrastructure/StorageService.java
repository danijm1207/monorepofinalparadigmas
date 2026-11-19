package edu.investigacion.infrastructure;

import edu.investigacion.domain.AppError;
import edu.investigacion.domain.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class StorageService {
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 5 MB
    private static final Set<String> ALLOWED_FOLDERS = Set.of("cases", "suspects", "evidences");

    public record UploadResult(String url, String path) {}

    private final Path uploadDir;
    private final String publicBaseUrl;

    public StorageService(String uploadDirPath, String publicBaseUrl) {
        this.uploadDir = Path.of(uploadDirPath);
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
            ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
            : publicBaseUrl;
    }

    public Result<UploadResult> saveFile(byte[] data, Optional<String> folderParam) {
        if (data == null || data.length == 0) {
            return Result.fail(new AppError.ValidationError("El archivo no puede estar vacío"));
        }

        if (data.length > MAX_FILE_SIZE) {
            return Result.fail(new AppError.PayloadTooLarge("El archivo supera el tamaño máximo permitido de 5 MB"));
        }

        String folder = folderParam.filter(ALLOWED_FOLDERS::contains).orElse("cases");
        Optional<String> extOpt = detectExtension(data);
        if (extOpt.isEmpty()) {
            return Result.fail(new AppError.ValidationError("Formato de imagen no permitido (solo JPEG, PNG o WEBP)"));
        }

        String filename = UUID.randomUUID() + extOpt.get();
        Path targetDir = uploadDir.resolve(folder);
        Path targetPath = targetDir.resolve(filename);

        try {
            Files.createDirectories(targetDir);
            Files.write(targetPath, data, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            return Result.fail(new AppError.InternalError("Error al persistir el archivo en disco"));
        }

        String relativePath = folder + "/" + filename;
        String publicUrl = publicBaseUrl + "/uploads/" + relativePath;

        return Result.ok(new UploadResult(publicUrl, relativePath));
    }

    public Optional<byte[]> readFile(String folder, String filename) {
        if (!ALLOWED_FOLDERS.contains(folder)) {
            return Optional.empty();
        }
        Path target = uploadDir.resolve(folder).resolve(filename).normalize();
        if (!target.startsWith(uploadDir.normalize())) {
            return Optional.empty(); // Directory traversal prevention
        }
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(target));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Optional<String> detectExtension(byte[] data) {
        if (data.length >= 8 &&
            data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47 &&
            data[4] == 0x0D && data[5] == 0x0A && data[6] == 0x1A && data[7] == 0x0A) {
            return Optional.of(".png");
        }
        if (data.length >= 3 &&
            data[0] == (byte) 0xFF && data[1] == (byte) 0xD8 && data[2] == (byte) 0xFF) {
            return Optional.of(".jpg");
        }
        if (data.length >= 12 &&
            data[0] == (byte) 'R' && data[1] == (byte) 'I' && data[2] == (byte) 'F' && data[3] == (byte) 'F' &&
            data[8] == (byte) 'W' && data[9] == (byte) 'E' && data[10] == (byte) 'B' && data[11] == (byte) 'P') {
            return Optional.of(".webp");
        }
        return Optional.empty();
    }
}
