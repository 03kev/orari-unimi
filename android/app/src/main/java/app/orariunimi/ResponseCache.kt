package app.orariunimi

import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class ResponseCacheEntry(val value: String, val storedAtMillis: Long)

class ResponseCache(
    private val directory: File,
    private val maxBytes: Long = 8L * 1024 * 1024,
    private val clockMillis: () -> Long = System::currentTimeMillis
) {
    @Synchronized
    fun readEntry(key: String, maxAgeMillis: Long): ResponseCacheEntry? {
        val file = fileFor(key)
        if (!file.isFile) return null
        val age = (clockMillis() - file.lastModified()).coerceAtLeast(0)
        if (age > maxAgeMillis) return null
        return runCatching {
            ResponseCacheEntry(file.readText(StandardCharsets.UTF_8), file.lastModified())
        }.getOrNull()
    }

    fun read(key: String, maxAgeMillis: Long): String? = readEntry(key, maxAgeMillis)?.value

    @Synchronized
    fun write(key: String, value: String): Long? {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > maxBytes) return null
        if (!directory.exists() && !directory.mkdirs()) return null
        val target = fileFor(key)
        val temporary = File(directory, "${target.name}.tmp")
        val storedAt = clockMillis()
        return runCatching {
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            target.setLastModified(storedAt)
            prune()
            storedAt
        }.getOrElse {
            temporary.delete()
            null
        }
    }

    private fun prune() {
        val files = directory.listFiles { file -> file.isFile && file.extension == "cache" }
            ?.sortedBy { it.lastModified() }.orEmpty()
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= maxBytes) break
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    private fun fileFor(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$digest.cache")
    }
}
