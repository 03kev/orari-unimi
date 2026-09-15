package app.orariunimi

import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

enum class SearchKind(val label: String, val subtitle: String) {
    COURSE("Corsi", "Corso di studio"),
    TEACHER("Docenti", "Docente"),
    SUBJECT("Insegnamenti", "Insegnamento")
}

data class AcademicYear(val code: String, val name: String)

enum class DegreeType(val label: String) {
    BACHELOR("Triennale"), MASTER("Magistrale"), SINGLE_CYCLE("Ciclo unico"), OTHER("Altro");

    companion object {
        fun fromPortal(value: String): DegreeType = when (value.trim().uppercase(Locale.ROOT)) {
            "CDS TRIENNALE" -> BACHELOR
            "CDS MAGISTRALE" -> MASTER
            "CDS MAGISTRALE A CICLO UNICO" -> SINGLE_CYCLE
            else -> OTHER
        }
    }
}

data class CourseTeaching(val code: String, val name: String, val teacher: String)
data class CoursePath(val code: String, val name: String, val teachings: List<CourseTeaching>)
data class CourseTeachingCandidate(val combinedCode: String, val teaching: CourseTeaching)

data class SearchItem(
    val code: String,
    val name: String,
    val kind: SearchKind,
    val paths: List<String> = emptyList(),
    val degreeType: DegreeType? = null,
    val coursePaths: List<CoursePath> = emptyList()
)

data class SavedSubject(val year: String, val code: String, val name: String)
data class FavoriteCourse(val year: String, val code: String, val name: String, val degreeType: DegreeType)

data class Lesson(
    val id: String,
    val subjectCode: String,
    val subject: String,
    val date: LocalDate,
    val start: String,
    val end: String,
    val room: String,
    val teacher: String,
    val type: String,
    val notes: String,
    val cancelled: Boolean
)

class SearchIndex(val items: List<SearchItem>) {
    private data class Entry(val item: SearchItem, val text: String, val name: String)
    private val entries = items.map { Entry(it, normalize("${it.code} ${it.name}"), normalize(it.name)) }

    fun search(query: String, limit: Int = 40): List<SearchItem> {
        val terms = normalize(query).split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        return entries.asSequence().mapNotNull { entry ->
            val positions = terms.map { entry.text.indexOf(it) }
            if (positions.any { it < 0 }) null else Triple(entry.item, positions.min(), entry.name)
        }.sortedWith(compareBy<Triple<SearchItem, Int, String>> { it.second }.thenBy { it.third })
            .take(limit).map { it.first }.toList()
    }
}

fun filterItems(items: List<SearchItem>, query: String, limit: Int = 40): List<SearchItem> =
    SearchIndex(items).search(query, limit)

fun SearchItem.withFallbackTeachings(candidates: List<CourseTeachingCandidate>): SearchItem {
    if (kind != SearchKind.COURSE || coursePaths.any { it.teachings.isNotEmpty() }) return this
    val matching = candidates.filter {
        val parts = it.combinedCode.split('^')
        parts.size >= 3 && parts[0] == code
    }
    if (matching.isEmpty()) return this
    val byPath = matching.groupBy { it.combinedCode.split('^')[1] }
    val originalByBase = coursePaths.groupBy { it.code.substringBefore('|') }
    val rebuilt = byPath.map { (pathCode, values) ->
        val original = originalByBase[pathCode].orEmpty()
        val label = if (original.size == 1) original.single().name else "Insegnamenti disponibili"
        CoursePath("catalog:$pathCode", label, values.map { it.teaching }
            .distinctBy { it.code }.sortedBy { normalize(it.name) })
    }.sortedBy { normalize(it.name) }
    return copy(coursePaths = rebuilt)
}

private fun normalize(value: String): String = Normalizer.normalize(
    value.lowercase(Locale.ROOT).trim(), Normalizer.Form.NFD
).replace(Regex("\\p{M}+"), "")
