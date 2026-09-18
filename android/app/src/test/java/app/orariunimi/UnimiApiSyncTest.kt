package app.orariunimi

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class UnimiApiSyncTest {
    @Test fun savedSubjectsAreGroupedByAcademicYear() = withCache { directory ->
        val requests = Collections.synchronizedList(mutableListOf<Map<String, List<String>>>())
        val api = UnimiApi(cache = ResponseCache(directory), transport = { path, encoded, get ->
            assertEquals("grid_call.php", path)
            assertEquals(false, get)
            val form = formValues(encoded)
            requests += form
            response(form.getValue("attivita[]"))
        })

        val snapshot = api.refreshSavedLessons(listOf(
            SavedSubject("2025", "B", "B"),
            SavedSubject("2025", "A", "A"),
            SavedSubject("2024", "C", "C")
        ))

        assertEquals(2, requests.size)
        assertEquals(setOf("2024", "2025"), requests.map { it.getValue("anno").single() }.toSet())
        assertEquals(listOf("A", "B"), requests.single {
            it.getValue("anno").single() == "2025"
        }.getValue("attivita[]"))
        assertEquals(setOf("A", "B", "C"), snapshot.lessons.map { it.subjectCode }.toSet())
    }

    @Test fun automaticRefreshReusesOnlyVeryRecentData() = withCache { directory ->
        var now = 1_000_000L
        val calls = AtomicInteger()
        val cache = ResponseCache(directory, clockMillis = { now })
        val api = UnimiApi(cache = cache, clockMillis = { now }, transport = { _, encoded, _ ->
            calls.incrementAndGet()
            response(formValues(encoded).getValue("attivita[]"))
        })
        val saved = listOf(SavedSubject("2025", "A", "A"))

        api.refreshSavedLessons(saved)
        now += 119_000L
        api.refreshSavedLessonsIfStale(saved)
        assertEquals(1, calls.get())

        now += 2_000L
        api.refreshSavedLessonsIfStale(saved)
        assertEquals(2, calls.get())

        now += 1L
        api.refreshSavedLessons(saved)
        assertEquals(3, calls.get())
    }

    @Test fun concurrentForcedRefreshesShareOneNetworkResponse() = withCache { directory ->
        val now = 2_000_000L
        val calls = AtomicInteger()
        val enteredTransport = CountDownLatch(1)
        val releaseTransport = CountDownLatch(1)
        val cache = ResponseCache(directory, clockMillis = { now })
        val api = UnimiApi(cache = cache, clockMillis = { now }, transport = { _, encoded, _ ->
            calls.incrementAndGet()
            enteredTransport.countDown()
            assertTrue(releaseTransport.await(5, TimeUnit.SECONDS))
            response(formValues(encoded).getValue("attivita[]"))
        })
        val saved = listOf(SavedSubject("2025", "A", "A"), SavedSubject("2025", "B", "B"))
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<ScheduleSnapshot> { api.refreshSavedLessons(saved) }
            assertTrue(enteredTransport.await(5, TimeUnit.SECONDS))
            val second = executor.submit<ScheduleSnapshot> { api.refreshSavedLessons(saved) }
            releaseTransport.countDown()

            assertEquals(2, first.get(5, TimeUnit.SECONDS).lessons.size)
            assertEquals(2, second.get(5, TimeUnit.SECONDS).lessons.size)
            assertEquals(1, calls.get())
        } finally {
            releaseTransport.countDown()
            executor.shutdownNow()
        }
    }

    private fun response(codes: List<String>): String {
        val cells = JSONArray()
        codes.forEachIndexed { index, code ->
            cells.put(JSONObject()
                .put("id", "lesson-$code")
                .put("codice_insegnamento", code)
                .put("nome_insegnamento", "Insegnamento $code")
                .put("data", "21-09-2026")
                .put("ora_inizio", "%02d:00".format(9 + index))
                .put("ora_fine", "%02d:00".format(10 + index))
                .put("aula", "Aula $code")
                .put("docente", "Docente $code")
                .put("tipo", "Lezione")
                .put("Annullato", "0"))
        }
        return JSONObject().put("celle", cells).toString()
    }

    private fun formValues(encoded: String): Map<String, List<String>> = encoded.split('&')
        .map { value ->
            val parts = value.split('=', limit = 2)
            URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
        }
        .groupBy({ it.first }, { it.second })

    private inline fun withCache(block: (java.io.File) -> Unit) {
        val directory = Files.createTempDirectory("unimi-sync-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
