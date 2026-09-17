package app.orariunimi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NotificationEngineTest {
    private val today = LocalDate.of(2026, 9, 17)

    @Test fun detectsCancellationTimeAndRoomChangesOnce() {
        val old = lesson(id = "42", start = "09:00", end = "11:00", room = "Aula 1")
        val fresh = old.copy(start = "10:00", end = "12:00", room = "Aula 2", cancelled = true)

        val result = NotificationEngine.evaluate(listOf(old), listOf(fresh), emptyMap(), today)

        assertEquals(1, result.drafts.size)
        assertTrue(result.drafts.single().message.contains("annullata"))
        assertTrue(result.drafts.single().message.contains("10:00–12:00"))
        assertTrue(result.drafts.single().message.contains("Aula 2"))
        assertEquals(listOf(fresh), result.lessons)
    }

    @Test fun missingLessonRequiresTwoConsecutiveSnapshots() {
        val old = lesson(id = "42")

        val first = NotificationEngine.evaluate(listOf(old), emptyList(), emptyMap(), today)
        assertTrue(first.drafts.isEmpty())
        assertEquals(listOf(old), first.lessons)
        assertEquals(1, first.missingCounts["id:42"])

        val second = NotificationEngine.evaluate(first.lessons, emptyList(), first.missingCounts, today)
        assertEquals(1, second.drafts.size)
        assertEquals("Lezione rimossa", second.drafts.single().title)
        assertTrue(second.lessons.isEmpty())
    }

    @Test fun repeatedIdenticalSnapshotCreatesNoNotification() {
        val lesson = lesson(id = "42")
        val result = NotificationEngine.evaluate(listOf(lesson), listOf(lesson), emptyMap(), today)
        assertTrue(result.drafts.isEmpty())
        assertEquals(listOf(lesson), result.lessons)
    }

    @Test fun suspiciousEmptySnapshotIsDeferredOnlyOnce() {
        val old = lesson(id = "42")
        assertTrue(NotificationEngine.shouldDeferEmptySnapshot(listOf(old), emptyList(), 0, today))
        assertEquals(false, NotificationEngine.shouldDeferEmptySnapshot(listOf(old), emptyList(), 1, today))
        assertEquals(false, NotificationEngine.shouldDeferEmptySnapshot(emptyList(), emptyList(), 0, today))
    }

    @Test fun pastLessonsAreDiscardedWithoutNotifications() {
        val old = lesson(id = "old").copy(date = today.minusDays(1))
        val result = NotificationEngine.evaluate(listOf(old), emptyList(), emptyMap(), today)
        assertTrue(result.drafts.isEmpty())
        assertTrue(result.lessons.isEmpty())
    }

    private fun lesson(
        id: String,
        start: String = "09:00",
        end: String = "11:00",
        room: String = "Aula 1"
    ) = Lesson(id, "ALG", "Algoritmi", today.plusDays(1), start, end, room,
        "Ada Lovelace", "Lezione", "", false)
}
