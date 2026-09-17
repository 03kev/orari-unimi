package app.orariunimi

import java.security.MessageDigest
import java.time.LocalDate

data class NotificationDraft(
    val id: String,
    val title: String,
    val message: String,
    val targetDate: LocalDate?
)

data class ScheduleChangeEvaluation(
    val drafts: List<NotificationDraft>,
    val lessons: List<Lesson>,
    val missingCounts: Map<String, Int>
)

object NotificationEngine {
    fun shouldDeferEmptySnapshot(
        previous: List<Lesson>,
        current: List<Lesson>,
        consecutiveEmptyResponses: Int,
        today: LocalDate
    ): Boolean = current.none { !it.date.isBefore(today) } &&
        previous.any { !it.date.isBefore(today) } && consecutiveEmptyResponses < 1

    fun evaluate(
        previous: List<Lesson>,
        current: List<Lesson>,
        previousMissingCounts: Map<String, Int>,
        today: LocalDate
    ): ScheduleChangeEvaluation {
        val old = previous.filter { !it.date.isBefore(today) }.associateBy(::lessonIdentity)
        val fresh = current.filter { !it.date.isBefore(today) }.associateBy(::lessonIdentity)
        val next = fresh.toMutableMap()
        val missing = mutableMapOf<String, Int>()
        val drafts = mutableListOf<NotificationDraft>()

        old.forEach { (key, oldLesson) ->
            val newLesson = fresh[key]
            if (newLesson == null) {
                val count = previousMissingCounts.getOrDefault(key, 0) + 1
                if (count >= 2) {
                    drafts += draft(
                        "removed", key, oldLesson,
                        "Lezione rimossa",
                        "${oldLesson.subject}: non risulta più in programma il ${formatDate(oldLesson.date)} alle ${oldLesson.start}."
                    )
                } else {
                    missing[key] = count
                    next[key] = oldLesson
                }
                return@forEach
            }

            describeChange(oldLesson, newLesson)?.let { description ->
                drafts += draft("changed", key, newLesson, "Orario aggiornato", description)
            }
        }

        fresh.forEach { (key, lesson) ->
            if (key !in old) {
                drafts += draft(
                    "added", key, lesson,
                    "Nuova lezione",
                    "${lesson.subject}: ${formatDate(lesson.date)}, ${lesson.start}–${lesson.end}${roomSuffix(lesson.room)}."
                )
            }
        }

        return ScheduleChangeEvaluation(
            drafts = drafts,
            lessons = next.values.sortedWith(compareBy<Lesson> { it.date }.thenBy { it.start }.thenBy { it.subject }),
            missingCounts = missing
        )
    }

    fun lessonIdentity(lesson: Lesson): String = lesson.id.takeIf { it.isNotBlank() }
        ?.let { "id:$it" }
        ?: "fallback:${lesson.subjectCode}|${lesson.date}|${lesson.start}|${lesson.end}"

    fun lessonFingerprint(lesson: Lesson): String = digest(
        listOf(lessonIdentity(lesson), lesson.date, lesson.start, lesson.end, lesson.room,
            lesson.teacher, lesson.type, lesson.notes, lesson.cancelled).joinToString("|")
    )

    private fun describeChange(old: Lesson, fresh: Lesson): String? {
        val changes = mutableListOf<String>()
        if (!old.cancelled && fresh.cancelled) changes += "lezione annullata"
        if (old.cancelled && !fresh.cancelled) changes += "lezione ripristinata"
        if (old.date != fresh.date) changes += "nuova data ${formatDate(fresh.date)}"
        if (old.start != fresh.start || old.end != fresh.end) changes += "nuovo orario ${fresh.start}–${fresh.end}"
        if (old.room != fresh.room) changes += if (fresh.room.isBlank()) "aula da definire" else "nuova aula ${fresh.room}"
        if (old.teacher != fresh.teacher && fresh.teacher.isNotBlank()) changes += "docente ${fresh.teacher}"
        if (old.notes != fresh.notes && fresh.notes.isNotBlank()) changes += "nuova nota: ${fresh.notes}"
        if (changes.isEmpty()) return null
        return "${fresh.subject}: ${changes.joinToString(", ")}."
    }

    private fun draft(kind: String, key: String, lesson: Lesson, title: String, message: String) =
        NotificationDraft("schedule-$kind-${digest("$key|${lessonFingerprint(lesson)}|$message")}",
            title, message, lesson.date)

    private fun roomSuffix(room: String) = if (room.isBlank()) "" else " · $room"
    private fun formatDate(date: LocalDate) = "%02d/%02d/%04d".format(date.dayOfMonth, date.monthValue, date.year)
    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).take(10).joinToString("") { "%02x".format(it) }
}
