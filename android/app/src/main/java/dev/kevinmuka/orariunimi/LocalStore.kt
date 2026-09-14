package dev.kevinmuka.orariunimi

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Device-local preferences; no account or cloud service is required. */
class LocalStore(context: Context) {
    private val preferences = context.getSharedPreferences("orari_unimi", Context.MODE_PRIVATE)

    var showWeekend: Boolean
        get() = preferences.getBoolean("show_weekend", false)
        set(value) { preferences.edit().putBoolean("show_weekend", value).commit() }

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
}
