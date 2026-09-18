package app.orariunimi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.text.Normalizer
import java.util.Locale

@Composable
fun ExamsScreen(
    courses: List<SearchItem>?,
    favorites: List<FavoriteCourse>,
    query: String,
    results: List<SearchItem>?,
    selectedCourse: SearchItem?,
    window: ExamWindow,
    appeals: List<ExamAppeal>?,
    updatedAtMillis: Long?,
    offline: Boolean,
    loadingCourses: Boolean,
    refreshing: Boolean,
    onQuery: (String) -> Unit,
    onSelectCourse: (SearchItem) -> Unit,
    onClearCourse: () -> Unit,
    onWindow: (ExamWindow) -> Unit,
    onRefresh: () -> Unit,
    onRetryCourses: () -> Unit,
    onAddToCalendar: (ExamAppeal) -> Unit
) {
    if (selectedCourse == null) {
        ExamCoursePicker(courses, favorites, query, results, loadingCourses, onQuery,
            onSelectCourse, onRetryCourses)
    } else {
        ExamAppealsList(selectedCourse, window, appeals, updatedAtMillis, offline, refreshing,
            onClearCourse, onWindow, onRefresh, onAddToCalendar)
    }
}

@Composable
private fun ExamCoursePicker(
    courses: List<SearchItem>?, favorites: List<FavoriteCourse>, query: String,
    results: List<SearchItem>?, loading: Boolean, onQuery: (String) -> Unit,
    onSelect: (SearchItem) -> Unit, onRetry: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Appelli", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Consulta date, aule e iscrizioni degli esami pubblicati da UNIMI.",
                Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                label = { Text("Cerca corso di laurea") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {{
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Cancella ricerca")
                    }
                }} else null
            )
        }

        if (query.isBlank() && favorites.isNotEmpty()) {
            item {
                Text("CORSI PREFERITI", Modifier.padding(top = 10.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold)
            }
            items(favorites, key = { "favorite:${it.year}:${it.code}" }) { favorite ->
                val item = courses?.firstOrNull { it.code == favorite.code }
                    ?: SearchItem(favorite.code, favorite.name, SearchKind.COURSE,
                        degreeType = favorite.degreeType)
                ExamCourseCard(item, favorite = true, onClick = { onSelect(item) })
            }
        }

        when {
            loading && courses == null -> item {
                Box(Modifier.fillMaxWidth().padding(top = 42.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            courses == null -> item {
                ExamEmptyPanel("Elenco non disponibile", "Controlla la connessione e riprova.", "Riprova", onRetry)
            }
            query.isNotBlank() && results == null -> item {
                Box(Modifier.fillMaxWidth().padding(top = 42.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            query.isNotBlank() && results?.isEmpty() == true -> item {
                ExamEmptyPanel("Nessun corso trovato", "Prova con il nome o il codice del corso.")
            }
            query.isNotBlank() -> {
                item {
                    Text("${results.orEmpty().size} ${if (results.orEmpty().size == 1) "risultato" else "risultati"}",
                        Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(results.orEmpty(), key = { "result:${it.code}" }) { course ->
                    ExamCourseCard(course,
                        favorite = favorites.any { it.code == course.code },
                        onClick = { onSelect(course) })
                }
            }
            favorites.isEmpty() && !loading -> item {
                ExamEmptyPanel("Scegli il tuo corso", "Cerca un corso di laurea per vedere gli appelli programmati.")
            }
        }
    }
}

@Composable
private fun ExamCourseCard(course: SearchItem, favorite: Boolean, onClick: () -> Unit) {
    val palette = degreePalette(course.degreeType ?: DegreeType.OTHER)
    Card(onClick = onClick, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = palette.container)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.School, contentDescription = null, tint = palette.accent)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(course.name, style = MaterialTheme.typography.titleMedium, color = palette.content,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOf(course.code, course.degreeType?.label).filterNotNull().joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = palette.content.copy(alpha = 0.78f))
            }
            if (favorite) Icon(Icons.Outlined.Bookmark, contentDescription = "Corso preferito",
                tint = palette.accent, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun ExamAppealsList(
    course: SearchItem, window: ExamWindow, appeals: List<ExamAppeal>?, updatedAtMillis: Long?,
    offline: Boolean, refreshing: Boolean, onClearCourse: () -> Unit,
    onWindow: (ExamWindow) -> Unit, onRefresh: () -> Unit,
    onAddToCalendar: (ExamAppeal) -> Unit
) {
    var filter by remember(course.code, window) { mutableStateOf("") }
    val visibleAppeals = remember(appeals, filter) {
        if (filter.isBlank()) appeals.orEmpty() else appeals.orEmpty().filter { appeal ->
            val searchable = normalizeExamText(listOf(appeal.subjectName, appeal.subjectCode,
                appeal.subjectPortalCode, appeal.teacher).joinToString(" "))
            normalizeExamText(filter).split(' ').filter { it.isNotBlank() }.all { it in searchable }
        }
    }
    val groups = visibleAppeals.groupBy { it.date }.toSortedMap()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    DegreeBadge(course.degreeType ?: DegreeType.OTHER)
                    Text(course.name, Modifier.padding(top = 9.dp),
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(course.code, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onClearCourse) {
                    Icon(Icons.Outlined.SwapHoriz, contentDescription = "Cambia corso")
                }
                IconButton(onClick = onRefresh, enabled = !refreshing) {
                    if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Refresh, contentDescription = "Aggiorna appelli")
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ExamWindow.entries.forEach { option ->
                    FilterChip(
                        selected = option == window,
                        onClick = { onWindow(option) },
                        label = { Text(option.label, maxLines = 1) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (!appeals.isNullOrEmpty()) OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                label = { Text("Filtra insegnamento o docente") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = if (filter.isNotEmpty()) {{
                    IconButton(onClick = { filter = "" }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Cancella filtro")
                    }
                }} else null
            )
            if (updatedAtMillis != null) {
                val updated = Instant.ofEpochMilli(updatedAtMillis).atZone(ZoneId.systemDefault())
                Text((if (offline) "Dati offline" else "Aggiornato") +
                    " · ${updated.format(DateTimeFormatter.ofPattern("dd/MM, HH:mm"))}",
                    Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelSmall,
                    color = if (offline) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        when {
            appeals == null && refreshing -> item {
                Box(Modifier.fillMaxWidth().padding(top = 52.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            appeals == null -> item {
                ExamEmptyPanel(
                    "Appelli non disponibili",
                    "Controlla la connessione e riprova.",
                    "Riprova",
                    onRefresh
                )
            }
            appeals.isEmpty() -> item {
                ExamEmptyPanel("Nessun appello", when (window) {
                    ExamWindow.TODAY -> "Oggi non sono previsti appelli per questo corso."
                    ExamWindow.NEXT_30_DAYS -> "Non risultano appelli nei prossimi 30 giorni."
                    ExamWindow.ALL -> "Al momento non risultano appelli programmati."
                })
            }
            visibleAppeals.isEmpty() -> item {
                ExamEmptyPanel("Nessun risultato", "Nessun appello corrisponde al filtro inserito.")
            }
            else -> groups.forEach { (date, dayAppeals) ->
                item(key = "date:$date") {
                    Text(formatExamDate(date), Modifier.padding(top = 9.dp, bottom = 1.dp),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                }
                items(dayAppeals, key = { "appeal:${it.id}" }) { appeal ->
                    ExamAppealCard(appeal, onAddToCalendar)
                }
            }
        }
    }
}

@Composable
private fun ExamAppealCard(appeal: ExamAppeal, onAddToCalendar: (ExamAppeal) -> Unit) {
    Card(shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(appeal.subjectName, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text(listOf(appeal.subjectCode, appeal.subjectPortalCode).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    IconButton(onClick = { onAddToCalendar(appeal) }, modifier = Modifier.size(42.dp)) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = "Aggiungi al calendario")
                    }
                }
            }
            Spacer(Modifier.height(11.dp))
            ExamInfoRow(Icons.Outlined.EventAvailable,
                listOf(appeal.time.ifBlank { "Orario da definire" }, appeal.testType, appeal.appealType)
                    .filter { it.isNotBlank() }.joinToString(" · "))
            if (appeal.location.isNotBlank()) ExamInfoRow(Icons.Outlined.LocationOn, appeal.location)
            if (appeal.teacher.isNotBlank()) ExamInfoRow(Icons.Outlined.PersonOutline, appeal.teacher)
            val range = listOf(appeal.surnameFrom, appeal.surnameTo).filter { it.isNotBlank() }.joinToString("–")
            if (range.isNotBlank()) Text("Cognomi $range", Modifier.padding(top = 7.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            RegistrationBadge(appeal)
        }
    }
}

@Composable
private fun ExamInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RegistrationBadge(appeal: ExamAppeal) {
    val today = LocalDate.now()
    val open = appeal.registrationOpen
    val close = appeal.registrationClose
    val label = when {
        open == null && close == null -> null
        open != null && today.isBefore(open) -> "Iscrizioni dal ${formatShortDate(open)}"
        close != null && today.isAfter(close) -> "Iscrizioni chiuse il ${formatShortDate(close)}"
        close != null -> "Iscrizioni aperte fino al ${formatShortDate(close)}"
        else -> "Iscrizioni aperte"
    } ?: return
    val active = (open == null || !today.isBefore(open)) && (close == null || !today.isAfter(close))
    Surface(
        modifier = Modifier.padding(top = 11.dp),
        color = if (active) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(9.dp)
    ) {
        Text(label, Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ExamEmptyPanel(title: String, message: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.EventAvailable, contentDescription = null, modifier = Modifier.size(38.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Text(message, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) OutlinedButton(onClick = onAction, modifier = Modifier.padding(top = 14.dp)) {
            Text(action)
        }
    }
}

private val examLocale = Locale.ITALIAN
private val examDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(examLocale)
private val examShortDate = DateTimeFormatter.ofPattern("dd/MM", examLocale)

private fun formatExamDate(date: LocalDate): String =
    date.format(examDate).replaceFirstChar { it.titlecase(examLocale) }

private fun formatShortDate(date: LocalDate): String = date.format(examShortDate)

private fun normalizeExamText(value: String): String = Normalizer.normalize(
    value.lowercase(Locale.ROOT).trim(), Normalizer.Form.NFD
).replace(Regex("\\p{M}+"), "")
