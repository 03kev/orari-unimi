package app.orariunimi

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.time.LocalDate
import java.time.YearMonth

class ScheduleTest {
    @Test fun portalDegreeLabelsHaveDistinctTypes() {
        assertEquals(DegreeType.BACHELOR, DegreeType.fromPortal("CDS TRIENNALE"))
        assertEquals(DegreeType.MASTER, DegreeType.fromPortal("CDS MAGISTRALE"))
        assertEquals(DegreeType.SINGLE_CYCLE, DegreeType.fromPortal("CDS MAGISTRALE A CICLO UNICO"))
        assertEquals(DegreeType.OTHER, DegreeType.fromPortal("master"))
    }

    @Test fun searchIgnoresAccentsAndMatchesCode() {
        val items = listOf(
            SearchItem("MAT01", "Matematica", SearchKind.SUBJECT),
            SearchItem("FIS02", "Fisica e società", SearchKind.SUBJECT)
        )
        assertEquals(listOf(items[1]), filterItems(items, "societa"))
        assertEquals(listOf(items[0]), filterItems(items, "mat01"))
        assertEquals(listOf(items[1]), SearchIndex(items).search("societa"))
    }

    @Test fun emptyCourseUsesTeachingsFromMatchingCatalogCode() {
        val course = SearchItem("FBA", "INFORMATICA", SearchKind.COURSE,
            paths = listOf("FBA-0|insegnamenticomplementari"),
            coursePaths = listOf(CoursePath("FBA-0|insegnamenticomplementari", "Unico", emptyList())))
        val candidates = listOf(
            CourseTeachingCandidate("FBA^FBA-0^FBA-91", CourseTeaching("ECFBA-91_1", "Affective Computing", "")),
            CourseTeachingCandidate("FAA^FAA-0^FAA-91", CourseTeaching("ECFAA-91_1", "Altro", ""))
        )
        val completed = course.withFallbackTeachings(candidates)
        assertEquals(1, completed.coursePaths.single().teachings.size)
        assertEquals("ECFBA-91_1", completed.coursePaths.single().teachings.single().code)
        assertEquals(course.paths, completed.paths)
    }

    @Test fun fallbackKeepsAnUnambiguousCoursePathName() {
        val course = SearchItem("FBA", "INFORMATICA", SearchKind.COURSE,
            coursePaths = listOf(CoursePath("FBA-0|insegnamenticomplementari", "Unico", emptyList())))
        val completed = course.withFallbackTeachings(listOf(
            CourseTeachingCandidate("FBA^FBA-0^FBA-91", CourseTeaching("ECFBA-91_1", "Affective Computing", ""))
        ))
        assertEquals("Unico", completed.coursePaths.single().name)
    }

    @Test fun monthGridStartsOnMondayAndContainsSixWeeks() {
        val dates = monthDates(YearMonth.of(2026, 9))
        assertEquals(42, dates.size)
        assertEquals(LocalDate.of(2026, 9, 1), dates[1])
        assertEquals(LocalDate.of(2026, 9, 30), dates[30])
    }

    @Test fun responseCacheExpiresAndPrunesOldEntries() {
        val directory = Files.createTempDirectory("orari-cache-test").toFile()
        var now = 1_000L
        try {
            val cache = ResponseCache(directory, maxBytes = 10, clockMillis = { now })
            cache.write("first", "123456")
            assertEquals("123456", cache.read("first", 100))
            assertEquals(1_000L, cache.readEntry("first", 100)?.storedAtMillis)
            now = 1_200L
            assertEquals(null, cache.read("first", 100))
            assertEquals("123456", cache.read("first", 300))
            now = 2_000L
            cache.write("second", "abcdef")
            assertEquals(null, cache.read("first", 10_000))
            assertEquals("abcdef", cache.read("second", 100))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun calendarOpensOnTodayAndKeepsSelectionVisibleWithoutWeekend() {
        val tuesday = LocalDate.of(2026, 9, 15)
        val saturday = LocalDate.of(2026, 9, 19)
        assertEquals(tuesday, openingDay(tuesday, false))
        assertEquals(saturday, openingDay(saturday, true))
        assertEquals(LocalDate.of(2026, 9, 18), openingDay(saturday, false))

        val lesson = Lesson("1", "ABC", "Lezione", saturday, "09:00", "11:00", "", "", "", "", false)
        val week = startOfWeek(tuesday)
        assertEquals(week, preferredDay(listOf(lesson), week, false))
        assertEquals(saturday, preferredDay(listOf(lesson), week, true))
    }
}
