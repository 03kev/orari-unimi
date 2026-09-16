package app.orariunimi

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class LessonConflict(val first: Lesson, val second: Lesson)
data class TimelineLesson(val lesson: Lesson, val lane: Int, val laneCount: Int)

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

fun timelineLessons(lessons: List<Lesson>): List<TimelineLesson> = lessons
    .groupBy { it.date }
    .values
    .flatMap { sameDay ->
        val intervals = sameDay.mapNotNull { lesson ->
            val interval = lessonInterval(lesson) ?: return@mapNotNull null
            Triple(lesson, interval.first.toLocalTime(), interval.second.toLocalTime())
        }.sortedWith(compareBy<Triple<Lesson, LocalTime, LocalTime>> { it.second }.thenBy { it.third })

        val result = mutableListOf<TimelineLesson>()
        var cluster = mutableListOf<Triple<Lesson, LocalTime, LocalTime>>()
        var clusterEnd: LocalTime? = null

        fun flushCluster() {
            if (cluster.isEmpty()) return
            val laneEnds = mutableListOf<LocalTime>()
            val assigned = cluster.map { item ->
                val lane = laneEnds.indexOfFirst { end -> end <= item.second }.let { available ->
                    if (available >= 0) available else laneEnds.size
                }
                if (lane == laneEnds.size) laneEnds += item.third else laneEnds[lane] = item.third
                item.first to lane
            }
            val laneCount = laneEnds.size.coerceAtLeast(1)
            result += assigned.map { (lesson, lane) -> TimelineLesson(lesson, lane, laneCount) }
            cluster = mutableListOf()
            clusterEnd = null
        }

        intervals.forEach { item ->
            if (clusterEnd?.let { item.second >= it } == true) flushCluster()
            cluster += item
            clusterEnd = maxOf(clusterEnd ?: item.third, item.third)
        }
        flushCluster()
        result
    }
    .sortedWith(compareBy<TimelineLesson> { it.lesson.date }.thenBy { it.lesson.start }.thenBy { it.lane })

private fun lessonInterval(lesson: Lesson): Pair<LocalDateTime, LocalDateTime>? = runCatching {
    val start = LocalTime.parse(lesson.start, lessonTimeFormat)
    val end = LocalTime.parse(lesson.end, lessonTimeFormat)
    lesson.date.atTime(start) to lesson.date.atTime(end)
}.getOrNull()
