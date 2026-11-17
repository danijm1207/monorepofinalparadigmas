package edu.investigacion.infrastructure

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

object FileStorageService {
    private const val MAX_SIZE = 5 * 1024 * 1024 // 5 MB

    fun detectExtension(bytes: ByteArray): String? {
        if (bytes.size < 12) return null
        // JPEG: FF D8 FF
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            return "jpg"
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() && bytes[4] == 0x0D.toByte() && bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte()) {
            return "png"
        }
        // WEBP: RIFF .... WEBP
        if (bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() && bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()) {
            return "webp"
        }
        return null
    }

    fun saveFile(
        baseDir: String,
        folder: String,
        inputStream: InputStream
    ): Pair<String, String>? {
        val bytes = inputStream.readAllBytes()
        if (bytes.isEmpty() || bytes.size > MAX_SIZE) return null

        val ext = detectExtension(bytes) ?: return null
        val targetFolder = if (folder in listOf("cases", "suspects", "evidences")) folder else "cases"

        val dirPath = Paths.get(baseDir, targetFolder)
        Files.createDirectories(dirPath)

        val filename = "${UUID.randomUUID()}.$ext"
        val filePath = dirPath.resolve(filename)
        Files.write(filePath, bytes)

        val relativePath = "$targetFolder/$filename"
        return Pair(relativePath, filename)
    }
}
