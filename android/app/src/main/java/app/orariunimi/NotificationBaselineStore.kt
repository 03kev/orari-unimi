package app.orariunimi

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

data class NotificationBaseline(
    val lessons: List<Lesson>,
    val missingCounts: Map<String, Int> = emptyMap(),
    val consecutiveEmptyResponses: Int = 0
)

class NotificationBaselineStore(context: Context) {
    private val directory = File(context.filesDir, "notifications")
    private val file = File(directory, "schedule-baseline.json")

    fun read(): NotificationBaseline? = synchronized(lock) {
        if (!file.isFile) return@synchronized null
        runCatching {
            val root = JSONObject(file.readText())
            val lessons = root.optJSONArray("lessons") ?: JSONArray()
            val missing = root.optJSONObject("missingCounts") ?: JSONObject()
            NotificationBaseline(
                lessons = (0 until lessons.length()).mapNotNull { lessons.optJSONObject(it)?.toLesson() },
                missingCounts = missing.keys().asSequence().associateWith { missing.optInt(it) },
                consecutiveEmptyResponses = root.optInt("consecutiveEmptyResponses", 0)
            )
        }.getOrNull()
    }

    fun write(baseline: NotificationBaseline) = synchronized(lock) {
        directory.mkdirs()
        val lessons = JSONArray().apply { baseline.lessons.forEach { put(it.toJson()) } }
        val missing = JSONObject().apply { baseline.missingCounts.forEach { (key, value) -> put(key, value) } }
        val root = JSONObject()
            .put("lessons", lessons)
            .put("missingCounts", missing)
            .put("consecutiveEmptyResponses", baseline.consecutiveEmptyResponses)
        val temporary = File(directory, "schedule-baseline.tmp")
        temporary.writeText(root.toString())
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }

    fun clear() = synchronized(lock) { file.delete() }

    private fun Lesson.toJson() = JSONObject()
        .put("id", id).put("subjectCode", subjectCode).put("subject", subject)
        .put("date", date.toString()).put("start", start).put("end", end)
        .put("room", room).put("teacher", teacher).put("type", type)
        .put("notes", notes).put("cancelled", cancelled)

    private fun JSONObject.toLesson(): Lesson? = runCatching {
        Lesson(
            id = optString("id"), subjectCode = getString("subjectCode"), subject = getString("subject"),
            date = LocalDate.parse(getString("date")), start = getString("start"), end = getString("end"),
            room = optString("room"), teacher = optString("teacher"), type = optString("type"),
            notes = optString("notes"), cancelled = optBoolean("cancelled")
        )
    }.getOrNull()

    private companion object { val lock = Any() }
}
