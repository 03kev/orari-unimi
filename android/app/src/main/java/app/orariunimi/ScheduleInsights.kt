package app.orariunimi

import java.time.LocalDateTime
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

private fun lessonInterval(lesson: Lesson): Pair<LocalDateTime, LocalDateTime>? = runCatching {
    val start = LocalTime.parse(lesson.start, lessonTimeFormat)
    val end = LocalTime.parse(lesson.end, lessonTimeFormat)
    lesson.date.atTime(start) to lesson.date.atTime(end)
}.getOrNull()
