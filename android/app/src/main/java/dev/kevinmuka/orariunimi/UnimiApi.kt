package dev.kevinmuka.orariunimi

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The same public AgendaWeb endpoints and response fields used by the Go client. */
class UnimiApi(private val baseUrl: String = "https://orari-be.divsi.unimi.it/AgendaWeb/Orario/") {
    private val dateFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    fun years(): List<AcademicYear> {
        val years = combo("1", null, "anni_accademici_ec") as JSONObject
        return years.keys().asSequence().map { key ->
            val value = years.getJSONObject(key)
            AcademicYear(value.optString("valore"), repair(value.optString("label")))
        }.filter { it.code.isNotBlank() }.sortedByDescending { it.code }.toList()
    }

    fun entries(kind: SearchKind, year: String): List<SearchItem> {
        val (page, variable) = when (kind) {
            SearchKind.COURSE -> "corsi" to "elenco_corsi"
            SearchKind.TEACHER -> "docenti" to "elenco_docenti"
            SearchKind.SUBJECT -> "attivita" to "elenco_attivita"
        }
        val values = combo(year, page, variable) as JSONArray
        return (0 until values.length()).mapNotNull { index ->
            val value = values.optJSONObject(index) ?: return@mapNotNull null
            val code = value.optString("valore")
            val name = when (kind) {
                SearchKind.SUBJECT -> value.optString("label").ifBlank { value.optString("nome_insegnamento") }
                else -> value.optString("label")
            }
            if (code.isBlank() || name.isBlank()) return@mapNotNull null
            val coursePaths = value.optJSONArray("elenco_anni")?.let { pathValues ->
                (0 until pathValues.length()).mapNotNull pathLoop@ { pathIndex ->
                    val path = pathValues.optJSONObject(pathIndex) ?: return@pathLoop null
                    val pathCode = path.optString("valore")
                    if (pathCode.isBlank()) return@pathLoop null
                    val subjects = path.optJSONArray("elenco_insegnamenti")
                    val teachings = (0 until (subjects?.length() ?: 0)).mapNotNull subjectLoop@ { subjectIndex ->
                        val subject = subjects?.optJSONObject(subjectIndex) ?: return@subjectLoop null
                        val subjectCode = subject.optString("valore")
                        val subjectName = subject.optString("label")
                        if (subjectCode.isBlank() || subjectName.isBlank()) null else CourseTeaching(
                            subjectCode, repair(subjectName), repair(subject.optString("docente"))
                        )
                    }
                    CoursePath(pathCode, repair(path.optString("label")), teachings)
                }
            }.orEmpty()
            SearchItem(code, repair(name), kind, coursePaths.map { it.code },
                if (kind == SearchKind.COURSE) DegreeType.fromPortal(value.optString("tipo")) else null,
                coursePaths)
        }
    }

    fun lessons(year: String, item: SearchItem): List<Lesson> {
        val fields = mutableListOf<Pair<String, String>>()
        when (item.kind) {
            SearchKind.COURSE -> {
                require(item.paths.isNotEmpty()) { "Il corso non ha percorsi pubblicati per l'anno $year." }
                fields += "include" to "corso"
                fields += "corso" to item.code
                item.paths.forEach { fields += "anno2[]" to it }
            }
            SearchKind.TEACHER -> {
                fields += "include" to "docente"
                fields += "docente" to item.code
            }
            SearchKind.SUBJECT -> {
                fields += "include" to "attivita"
                fields += "attivita[]" to item.code
            }
        }
        fields += "view" to "easycourse"
        fields += "_lang" to "it"
        fields += "anno" to year
        fields += "date" to "01-08-$year"
        fields += "all_events" to "1"
        val response = JSONObject(request("grid_call.php", fields))
        val cells = response.optJSONArray("celle") ?: JSONArray()
        return (0 until cells.length()).mapNotNull { index ->
            val cell = cells.optJSONObject(index) ?: return@mapNotNull null
            val subjectCode = cell.optString("codice_insegnamento")
            if (subjectCode.isBlank()) return@mapNotNull null
            val date = LocalDate.parse(cell.getString("data"), dateFormat)
            Lesson(
                id = cell.optString("id"),
                subjectCode = subjectCode,
                subject = repair(cell.optString("nome_insegnamento")),
                date = date,
                start = cell.optString("ora_inizio"),
                end = cell.optString("ora_fine"),
                room = repair(cell.optString("aula")),
                teacher = repair(cell.optString("docente")),
                type = repair(cell.optString("tipo")),
                notes = listOf("notes", "NoteSettimanali", "note_easyroom")
                    .map { repair(cell.optString(it)).trim() }.filter { it.isNotEmpty() }.joinToString(" — "),
                cancelled = cell.optString("Annullato") == "1"
            )
        }.sortedWith(compareBy<Lesson> { it.date }.thenBy { it.start }.thenBy { it.subject })
    }

    fun savedLessons(saved: List<SavedSubject>): List<Lesson> = saved.flatMap { subject ->
        lessons(subject.year, SearchItem(subject.code, subject.name, SearchKind.SUBJECT))
    }.distinctBy { it.id.ifBlank { "${it.subjectCode}|${it.date}|${it.start}|${it.room}" } }
        .sortedWith(compareBy<Lesson> { it.date }.thenBy { it.start }.thenBy { it.subject })

    private fun combo(year: String, page: String?, variable: String): Any {
        val fields = mutableListOf("sw" to "ec_", "aa" to year)
        if (page != null) fields += "page" to page
        val json = extractVariable(request("combo.php", fields, get = true), variable)
        return if (json.startsWith('[')) JSONArray(json) else JSONObject(json)
    }

    private fun request(path: String, fields: List<Pair<String, String>>, get: Boolean = false): String {
        val encoded = fields.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
        val connection = URL(baseUrl + path + if (get) "?$encoded" else "").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "orari-unimi-android/0.1")
            connection.setRequestProperty("Accept", if (get) "application/javascript, application/json;q=0.9" else "application/json")
            if (!get) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                connection.outputStream.use { it.write(encoded.toByteArray(StandardCharsets.UTF_8)) }
            }
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Il portale UNIMI ha risposto ${connection.responseCode}.")
            }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 64 * 1024 * 1024) throw IllegalStateException("Risposta UNIMI troppo grande.")
                    output.write(buffer, 0, count)
                }
            }
            return output.toString("UTF-8")
        } finally {
            connection.disconnect()
        }
    }

    private fun extractVariable(body: String, name: String): String {
        val match = Regex("\\bvar\\s+${Regex.escape(name)}\\s*=").find(body)
            ?: throw IllegalStateException("La risposta UNIMI non contiene $name.")
        val start = body.indexOfFirstFrom(match.range.last + 1) { !it.isWhitespace() }
        if (start < 0 || (body[start] != '[' && body[start] != '{')) {
            throw IllegalStateException("Il formato di $name non è valido.")
        }
        val stack = ArrayDeque<Char>()
        var quoted = false
        var escaped = false
        for (index in start until body.length) {
            val char = body[index]
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') quoted = false
                continue
            }
            when (char) {
                '"' -> quoted = true
                '{' -> stack.addLast('}')
                '[' -> stack.addLast(']')
                '}', ']' -> {
                    if (stack.removeLastOrNull() != char) throw IllegalStateException("Il formato di $name non è valido.")
                    if (stack.isEmpty()) return body.substring(start, index + 1)
                }
            }
        }
        throw IllegalStateException("La risposta UNIMI per $name è incompleta.")
    }

    private fun String.indexOfFirstFrom(start: Int, predicate: (Char) -> Boolean): Int {
        for (index in start until length) if (predicate(this[index])) return index
        return -1
    }

    private fun repair(text: String): String {
        if (!text.contains('Ã') && !text.contains('Â')) return text
        if (text.any { it.code > 255 }) return text
        val bytes = ByteArray(text.length) { index -> text[index].code.toByte() }
        val repaired = String(bytes, StandardCharsets.UTF_8)
        return if (repaired.contains('\uFFFD')) text else repaired
    }
}
