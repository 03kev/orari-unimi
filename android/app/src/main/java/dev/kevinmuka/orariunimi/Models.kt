package dev.kevinmuka.orariunimi

import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

enum class SearchKind(val label: String, val subtitle: String) {
    COURSE("Corsi", "Corso di studio"),
    TEACHER("Docenti", "Docente"),
    SUBJECT("Insegnamenti", "Insegnamento")
}

data class AcademicYear(val code: String, val name: String)

data class SearchItem(
    val code: String,
    val name: String,
    val kind: SearchKind,
    val paths: List<String> = emptyList()
)

data class SavedSubject(val year: String, val code: String, val name: String)

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

fun filterItems(items: List<SearchItem>, query: String, limit: Int = 40): List<SearchItem> {
    val terms = normalize(query).split(' ').filter { it.isNotBlank() }
    if (terms.isEmpty()) return emptyList()
    return items.asSequence().mapNotNull { item ->
        val text = normalize("${item.code} ${item.name}")
        val positions = terms.map { text.indexOf(it) }
        if (positions.any { it < 0 }) null else item to positions.min()
    }.sortedWith(compareBy<Pair<SearchItem, Int>> { it.second }.thenBy { normalize(it.first.name) })
        .take(limit).map { it.first }.toList()
}

private fun normalize(value: String): String = Normalizer.normalize(
    value.lowercase(Locale.ROOT).trim(), Normalizer.Form.NFD
).replace(Regex("\\p{M}+"), "")
