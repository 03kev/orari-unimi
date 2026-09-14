package dev.kevinmuka.orariunimi

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

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

    @Test fun calendarStartsAtNearestWeekAndRespectsWeekend() {
        val saturday = LocalDate.of(2026, 9, 19)
        val lesson = Lesson("1", "ABC", "Lezione", saturday, "09:00", "11:00", "", "", "", "", false)
        val week = closestWeek(listOf(lesson), LocalDate.of(2026, 9, 14))
        assertEquals(LocalDate.of(2026, 9, 14), week)
        assertEquals(week, preferredDay(listOf(lesson), week, false))
        assertEquals(saturday, preferredDay(listOf(lesson), week, true))
    }
}
