package app.orariunimi

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AppRelease(
    val version: String,
    val tag: String,
    val downloadUrl: String,
    val sha256: String?
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data object UpToDate : AppUpdateState
    data class Available(val release: AppRelease) : AppUpdateState
    data class Downloading(val release: AppRelease, val progress: Float?) : AppUpdateState
    data class Ready(val release: AppRelease, val file: File) : AppUpdateState
    data class Error(val message: String) : AppUpdateState
}

object AppUpdater {
    private const val latestReleaseUrl =
        "https://api.github.com/repos/kevinmuka/orari-unimi/releases/latest"
    private const val releaseDownloadPrefix =
        "https://github.com/kevinmuka/orari-unimi/releases/download/"
    private const val maxMetadataBytes = 2 * 1024 * 1024
    private const val maxApkBytes = 100 * 1024 * 1024

    fun latestRelease(): AppRelease {
        val response = get(latestReleaseUrl, maxMetadataBytes)
        val json = JSONObject(response.toString(Charsets.UTF_8))
        val tag = json.optString("tag_name")
        val version = versionFromTag(tag)
            ?: throw IllegalStateException("La release GitHub non ha una versione valida.")
        val assets = json.optJSONArray("assets")
            ?: throw IllegalStateException("La release GitHub non contiene un APK.")
        val asset = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            ?: throw IllegalStateException("La release GitHub non contiene un APK.")
        val downloadUrl = asset.optString("browser_download_url")
        if (!downloadUrl.startsWith(releaseDownloadPrefix)) {
            throw IllegalStateException("L'indirizzo dell'aggiornamento non è valido.")
        }
        val digest = asset.optString("digest").takeIf { it.startsWith("sha256:") }
            ?.removePrefix("sha256:")?.lowercase()
        return AppRelease(version, tag, downloadUrl, digest)
    }

    fun isNewer(latest: String, current: String): Boolean {
        val latestParts = numericVersion(latest) ?: return false
        val currentParts = numericVersion(current) ?: return false
        val count = maxOf(latestParts.size, currentParts.size)
        for (index in 0 until count) {
            val latestPart = latestParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (latestPart != currentPart) return latestPart > currentPart
        }
        return false
    }

    fun download(context: Context, release: AppRelease, onProgress: (Float?) -> Unit): File {
        cleanupDownloads(context)
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val partial = File(directory, "orari-unimi-${release.version}.apk.part")
        val destination = File(directory, "orari-unimi-${release.version}.apk")
        val connection = URL(release.downloadUrl).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("User-Agent", "orari-unimi-android/${BuildConfig.VERSION_NAME}")
            connection.setRequestProperty("Accept", "application/vnd.android.package-archive")
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Download non riuscito (${connection.responseCode}).")
            }
            val expectedBytes = connection.contentLengthLong.takeIf { it > 0 }
            if (expectedBytes != null && expectedBytes > maxApkBytes) {
                throw IllegalStateException("L'APK supera la dimensione massima consentita.")
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            partial.outputStream().buffered().use { output ->
                connection.inputStream.buffered().use { input ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        downloaded += count
                        if (downloaded > maxApkBytes) {
                            throw IllegalStateException("L'APK supera la dimensione massima consentita.")
                        }
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        onProgress(expectedBytes?.let { downloaded.toFloat() / it })
                    }
                }
            }
            val actualDigest = digest.digest().joinToString("") { "%02x".format(it) }
            if (release.sha256 != null && actualDigest != release.sha256) {
                throw IllegalStateException("Il controllo di integrità dell'APK non è riuscito.")
            }
            if (!partial.renameTo(destination)) {
                partial.copyTo(destination, overwrite = true)
                partial.delete()
            }
            return destination
        } catch (cause: Exception) {
            partial.delete()
            destination.delete()
            throw cause
        } finally {
            connection.disconnect()
        }
    }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun cleanupDownloads(context: Context) {
        File(context.cacheDir, "updates").deleteRecursively()
    }

    fun unknownSourcesIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}")
    )

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun versionFromTag(tag: String): String? =
        Regex("(?:^|-)v?(\\d+(?:\\.\\d+)+)$").find(tag)?.groupValues?.get(1)

    private fun numericVersion(value: String): List<Int>? {
        if (!Regex("\\d+(?:\\.\\d+)*").matches(value)) return null
        return value.split('.').map { it.toIntOrNull() ?: return null }
    }

    private fun get(url: String, maxBytes: Int): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "orari-unimi-android/${BuildConfig.VERSION_NAME}")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("GitHub ha risposto ${connection.responseCode}.")
            }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > maxBytes) {
                        throw IllegalStateException("La risposta di GitHub è troppo grande.")
                    }
                    output.write(buffer, 0, count)
                }
            }
            return output.toByteArray()
        } finally {
            connection.disconnect()
        }
    }
}
