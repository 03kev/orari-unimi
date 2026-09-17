package app.orariunimi

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Device-local preferences; no account or cloud service is required. */
class LocalStore(context: Context) {
    private val preferences = context.getSharedPreferences("orari_unimi", Context.MODE_PRIVATE)

    companion object {
        private val notificationLock = Any()
        private const val MAX_NOTIFICATION_ENTRIES = 100
    }

    var showWeekend: Boolean
        get() = preferences.getBoolean("show_weekend", false)
        set(value) { preferences.edit().putBoolean("show_weekend", value).commit() }

    var notificationPreferences: NotificationPreferences
        get() = NotificationPreferences(
            enabled = preferences.getBoolean("notifications_enabled", false),
            importantChanges = preferences.getBoolean("notifications_important_changes", false),
            lessonReminders = preferences.getBoolean("notifications_lesson_reminders", false),
            appUpdates = preferences.getBoolean("notifications_app_updates", false),
            reminderMinutes = preferences.getInt("notifications_reminder_minutes", 30)
                .takeIf { it in setOf(15, 30, 60) } ?: 30
        )
        set(value) {
            preferences.edit()
                .putBoolean("notifications_enabled", value.enabled)
                .putBoolean("notifications_important_changes", value.importantChanges)
                .putBoolean("notifications_lesson_reminders", value.lessonReminders)
                .putBoolean("notifications_app_updates", value.appUpdates)
                .putInt("notifications_reminder_minutes", value.reminderMinutes)
                .commit()
        }

    var lastLessonReminderKey: String?
        get() = preferences.getString("last_lesson_reminder_key", null)
        set(value) { preferences.edit().putString("last_lesson_reminder_key", value).commit() }

    var lastUpdateNotificationVersion: String?
        get() = preferences.getString("last_update_notification_version", null)
        set(value) { preferences.edit().putString("last_update_notification_version", value).commit() }

    fun notifications(): List<AppNotificationEntry> = synchronized(notificationLock) {
        readNotifications()
    }

    fun addNotification(entry: AppNotificationEntry): Boolean = synchronized(notificationLock) {
        val current = readNotifications()
        if (current.any { it.id == entry.id }) return@synchronized false
        writeNotifications((listOf(entry) + current).take(MAX_NOTIFICATION_ENTRIES))
        true
    }

    fun deleteNotification(id: String) = synchronized(notificationLock) {
        writeNotifications(readNotifications().filterNot { it.id == id })
    }

    fun markNotificationsRead() = synchronized(notificationLock) {
        val current = readNotifications()
        if (current.any { !it.read }) writeNotifications(current.map { it.copy(read = true) })
    }

    fun clearNotifications() = synchronized(notificationLock) { writeNotifications(emptyList()) }

    fun saved(): List<SavedSubject> = try {
        val values = JSONArray(preferences.getString("saved_subjects", "[]"))
        (0 until values.length()).mapNotNull { index ->
            val value = values.optJSONObject(index) ?: return@mapNotNull null
            val year = value.optString("year")
            val code = value.optString("code")
            val name = value.optString("name")
            if (year.isBlank() || code.isBlank() || name.isBlank()) null else SavedSubject(year, code, name)
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun add(subject: SavedSubject): Boolean {
        val items = saved()
        if (items.any { it.year == subject.year && it.code == subject.code }) return false
        save(items + subject)
        return true
    }

    fun remove(subject: SavedSubject) = save(saved().filterNot { it.year == subject.year && it.code == subject.code })

    fun clear() = save(emptyList())

    fun favoriteCourses(): List<FavoriteCourse> = try {
        val values = JSONArray(preferences.getString("favorite_courses", "[]"))
        (0 until values.length()).mapNotNull { index ->
            val value = values.optJSONObject(index) ?: return@mapNotNull null
            val year = value.optString("year")
            val code = value.optString("code")
            val name = value.optString("name")
            if (year.isBlank() || code.isBlank() || name.isBlank()) null else FavoriteCourse(
                year, code, name,
                runCatching { DegreeType.valueOf(value.optString("degreeType")) }.getOrDefault(DegreeType.OTHER)
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun addFavoriteCourse(course: FavoriteCourse) {
        val items = favoriteCourses()
        if (items.none { it.year == course.year && it.code == course.code }) saveFavoriteCourses(items + course)
    }

    fun removeFavoriteCourse(course: FavoriteCourse) = saveFavoriteCourses(
        favoriteCourses().filterNot { it.year == course.year && it.code == course.code }
    )

    private fun save(items: List<SavedSubject>) {
        val values = JSONArray()
        items.forEach { item ->
            values.put(JSONObject().put("year", item.year).put("code", item.code).put("name", item.name))
        }
        preferences.edit().putString("saved_subjects", values.toString()).commit()
    }

    private fun saveFavoriteCourses(items: List<FavoriteCourse>) {
        val values = JSONArray()
        items.forEach { item ->
            values.put(JSONObject().put("year", item.year).put("code", item.code)
                .put("name", item.name).put("degreeType", item.degreeType.name))
        }
        preferences.edit().putString("favorite_courses", values.toString()).commit()
    }

    private fun readNotifications(): List<AppNotificationEntry> = try {
        val values = JSONArray(preferences.getString("notification_inbox", "[]"))
        (0 until values.length()).mapNotNull { index ->
            val value = values.optJSONObject(index) ?: return@mapNotNull null
            val id = value.optString("id")
            val title = value.optString("title")
            if (id.isBlank() || title.isBlank()) return@mapNotNull null
            AppNotificationEntry(
                id = id,
                type = runCatching { AppNotificationType.valueOf(value.optString("type")) }
                    .getOrDefault(AppNotificationType.IMPORTANT_CHANGE),
                title = title,
                message = value.optString("message"),
                timestampMillis = value.optLong("timestampMillis"),
                targetDate = value.optString("targetDate").takeIf { it.isNotBlank() }
                    ?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
                read = value.optBoolean("read", false)
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun writeNotifications(items: List<AppNotificationEntry>) {
        val values = JSONArray()
        items.forEach { item ->
            values.put(JSONObject()
                .put("id", item.id)
                .put("type", item.type.name)
                .put("title", item.title)
                .put("message", item.message)
                .put("timestampMillis", item.timestampMillis)
                .put("targetDate", item.targetDate?.toString().orEmpty())
                .put("read", item.read))
        }
        preferences.edit().putString("notification_inbox", values.toString()).commit()
    }
}
