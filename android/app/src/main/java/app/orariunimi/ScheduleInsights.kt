package app.orariunimi

import java.time.LocalDateTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class LessonConflict(val first: Lesson, val second: Lesson)

private val lessonTimeFormat = DateTimeFormatter.ofPattern("H:mm")

fun lessonKey(lesson: Lesson): String = lesson.id.ifBlank {
    "${lesson.subjectCode}|${lesson.date}|${lesson.start}|${lesson.end}|${lesson.room}"
}

fun nextLesson(lessons: List<Lesson>, now: LocalDateTime = LocalDateTime.now()): Lesson? = lessons
    .asSequence()
    .filterNot { it.cancelled }
    .mapNotNull { lesson -> lessonInterval(lesson)?.let { interval -> lesson to interval } }
    .filter { (_, interval) -> interval.second.isAfter(now) }
    .minByOrNull { (_, interval) -> interval.first }
    ?.first

fun lessonsForDay(lessons: List<Lesson>, day: LocalDate = LocalDate.now()): List<Lesson> = lessons
    .filter { it.date == day }
    .sortedWith(compareBy<Lesson> { it.start }.thenBy { it.subject })

fun lessonConflicts(lessons: List<Lesson>): List<LessonConflict> {
    val active = lessons.filterNot { it.cancelled }.groupBy { it.date }
    return active.values.flatMap { sameDay ->
        val ordered = sameDay.sortedBy { it.start }
        ordered.indices.flatMap { firstIndex ->
            ((firstIndex + 1) until ordered.size).mapNotNull { secondIndex ->
                val first = ordered[firstIndex]
                val second = ordered[secondIndex]
                if (first.subjectCode == second.subjectCode) return@mapNotNull null
                val firstInterval = lessonInterval(first) ?: return@mapNotNull null
                val secondInterval = lessonInterval(second) ?: return@mapNotNull null
                if (firstInterval.first < secondInterval.second && secondInterval.first < firstInterval.second) {
                    LessonConflict(first, second)
                } else null
            }
        }
    }
}

fun conflictingLessonKeys(lessons: List<Lesson>): Set<String> = lessonConflicts(lessons)
    .flatMap { listOf(lessonKey(it.first), lessonKey(it.second)) }
    .toSet()

fun conflictInterval(conflict: LessonConflict): Pair<String, String>? {
    val first = lessonInterval(conflict.first) ?: return null
    val second = lessonInterval(conflict.second) ?: return null
    val start = maxOf(first.first, second.first).toLocalTime()
    val end = minOf(first.second, second.second).toLocalTime()
    if (start >= end) return null
    return start.format(lessonTimeFormat) to end.format(lessonTimeFormat)
}

private fun lessonInterval(lesson: Lesson): Pair<LocalDateTime, LocalDateTime>? = runCatching {
    val start = LocalTime.parse(lesson.start, lessonTimeFormat)
    val end = LocalTime.parse(lesson.end, lessonTimeFormat)
    lesson.date.atTime(start) to lesson.date.atTime(end)
}.getOrNull()
