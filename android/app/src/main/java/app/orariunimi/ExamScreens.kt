package app.orariunimi

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
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
    errorMessage: String?,
    loadingCourses: Boolean,
    refreshing: Boolean,
    savedAppeals: List<ExamAppeal>,
    onQuery: (String) -> Unit,
    onSelectCourse: (SearchItem) -> Unit,
    onWindow: (ExamWindow) -> Unit,
    onRefresh: () -> Unit,
    onRetryCourses: () -> Unit,
    onOpenPlanner: () -> Unit,
    onToggleSavedAppeal: (ExamAppeal) -> Unit,
    onAddToCalendar: (ExamAppeal) -> Unit
) {
    if (selectedCourse == null) {
        ExamCoursePicker(courses, favorites, query, results, loadingCourses, onQuery,
            onSelectCourse, onRetryCourses, onOpenPlanner)
    } else {
        ExamAppealsList(selectedCourse, window, appeals, updatedAtMillis, offline, errorMessage, refreshing,
            savedAppeals, onWindow, onRefresh, onOpenPlanner,
            onToggleSavedAppeal, onAddToCalendar)
    }
}

@Composable
private fun ExamCoursePicker(
    courses: List<SearchItem>?, favorites: List<FavoriteCourse>, query: String,
    results: List<SearchItem>?, loading: Boolean, onQuery: (String) -> Unit,
    onSelect: (SearchItem) -> Unit, onRetry: () -> Unit, onOpenPlanner: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Appelli", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Consulta date, aule e iscrizioni degli esami pubblicati da UNIMI.",
                        Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onOpenPlanner) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = "Apri i miei appelli")
                }
            }
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
    offline: Boolean, errorMessage: String?, refreshing: Boolean, savedAppeals: List<ExamAppeal>,
    onWindow: (ExamWindow) -> Unit, onRefresh: () -> Unit, onOpenPlanner: () -> Unit,
    onToggleSavedAppeal: (ExamAppeal) -> Unit,
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
                IconButton(onClick = onOpenPlanner) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = "Apri i miei appelli")
                }
            }
            ExamWindowSelector(window, onWindow, Modifier.padding(top = 14.dp))
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
            if (offline) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.CloudOff, contentDescription = null,
                            modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(9.dp))
                        Column {
                            Text("Dati offline", style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(errorMessage ?: "Non è stato possibile aggiornare gli appelli. Stai vedendo l'ultima copia salvata.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
            if (updatedAtMillis != null) {
                ExamFreshness(appeals.orEmpty().size, updatedAtMillis, offline, refreshing, onRefresh)
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
                    "Impossibile caricare gli appelli",
                    errorMessage ?: "Il servizio UNIMI non è al momento raggiungibile. Riprova tra poco.",
                    "Riprova",
                    onRefresh
                )
            }
            appeals.isEmpty() -> item {
                ExamEmptyPanel("Nessun appello pubblicato", when (window) {
                    ExamWindow.TODAY -> "Il servizio UNIMI non riporta appelli per oggi per questo corso."
                    ExamWindow.NEXT_30_DAYS -> "Il servizio UNIMI non riporta appelli nei prossimi 30 giorni."
                    ExamWindow.ALL -> "Il servizio UNIMI non riporta al momento appelli programmati."
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
                    ExamAppealCard(
                        appeal,
                        saved = savedAppeals.any { it.id == appeal.id && it.courseCode == appeal.courseCode },
                        onToggleSaved = { onToggleSavedAppeal(appeal) },
                        onAddToCalendar = onAddToCalendar
                    )
                }
            }
        }
    }
}

@Composable
private fun ExamWindowSelector(
    window: ExamWindow,
    onWindow: (ExamWindow) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = ExamWindow.entries
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        BoxWithConstraints(Modifier.padding(4.dp)) {
            val segmentWidth = maxWidth / options.size
            val selectedIndex = options.indexOf(window).coerceAtLeast(0)
            val indicatorOffset by animateDpAsState(
                targetValue = segmentWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "exam-window-indicator"
            )
            Box(
                Modifier.offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                    .width(segmentWidth)
                    .height(42.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp))
            )
            Row(Modifier.fillMaxWidth()) {
                options.forEach { option ->
                    ExamWindowOption(
                        label = option.label,
                        selected = option == window,
                        modifier = Modifier.weight(1f),
                        onClick = { onWindow(option) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExamWindowOption(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "exam-window-label"
    )
    Box(
        modifier.height(42.dp).clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        ),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = contentColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun ExamFreshness(
    count: Int,
    updatedAtMillis: Long,
    offline: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit
) {
    val updated = remember(updatedAtMillis) {
        Instant.ofEpochMilli(updatedAtMillis).atZone(ZoneId.systemDefault())
    }
    val timestamp = if (updated.toLocalDate() == LocalDate.now()) {
        "alle ${updated.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    } else {
        "il ${updated.format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))}"
    }
    val status = when {
        offline -> "Dati offline · aggiornati $timestamp"
        refreshing -> "Aggiornato $timestamp · controllo in corso"
        else -> "Aggiornato $timestamp"
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (offline) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(6.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("$count ${if (count == 1) "appello" else "appelli"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(status, style = MaterialTheme.typography.labelSmall,
                color = if (offline) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRefresh, enabled = !refreshing, modifier = Modifier.size(40.dp)) {
            if (refreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.Outlined.Refresh, contentDescription = "Aggiorna appelli")
        }
    }
}

@Composable
fun ExamPlannerScreen(
    savedAppeals: List<ExamAppeal>,
    selectedAppeal: ExamAppeal?,
    savedNote: String,
    onSelectAppeal: (ExamAppeal) -> Unit,
    onSaveNote: (ExamAppeal, String) -> Unit,
    onRemoveAppeal: (ExamAppeal) -> Unit,
    onAddToCalendar: (ExamAppeal) -> Unit
) {
    if (selectedAppeal != null) {
        ExamAppealDetails(selectedAppeal, savedNote, onSaveNote, onRemoveAppeal, onAddToCalendar)
        return
    }
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedDay by remember { mutableStateOf(today) }
    var drag by remember(month) { mutableFloatStateOf(0f) }
    val appealsByDay = remember(savedAppeals) {
        savedAppeals.groupBy { it.date }.mapValues { (_, items) ->
            items.sortedWith(compareBy<ExamAppeal> { it.time }.thenBy { it.subjectName })
        }
    }
    val selectedAppeals = appealsByDay[selectedDay].orEmpty()
    val monthTitle = remember(month) {
        month.format(DateTimeFormatter.ofPattern("MMMM yyyy", examLocale))
            .replaceFirstChar { it.titlecase(examLocale) }
    }

    fun moveMonth(amount: Long) {
        val target = month.plusMonths(amount)
        val day = selectedDay.dayOfMonth.coerceAtMost(target.lengthOfMonth())
        month = target
        selectedDay = target.atDay(day)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(monthTitle, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Text("${savedAppeals.size} ${if (savedAppeals.size == 1) "appello salvato" else "appelli salvati"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = {
                    month = YearMonth.from(today)
                    selectedDay = today
                }) { Text("Oggi") }
                IconButton(onClick = { moveMonth(-1) }) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "Mese precedente")
                }
                IconButton(onClick = { moveMonth(1) }) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "Mese successivo")
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().pointerInput(month) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onHorizontalDrag = { change, amount -> drag += amount; change.consume() },
                    onDragCancel = { drag = 0f },
                    onDragEnd = {
                        when {
                            drag > 70f -> moveMonth(-1)
                            drag < -70f -> moveMonth(1)
                        }
                        drag = 0f
                    }
                )
                },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(13.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            listOf("LUN", "MAR", "MER", "GIO", "VEN", "SAB", "DOM")
                                .forEachIndexed { index, label ->
                                Box(Modifier.weight(1f).height(30.dp), contentAlignment = Alignment.Center) {
                                    Text(label, style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (index >= 5) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    AnimatedContent(
                        targetState = month,
                        transitionSpec = {
                            val forward = targetState.isAfter(initialState)
                            (slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) {
                                if (forward) it / 4 else -it / 4
                            } + fadeIn(tween(180))).togetherWith(
                                slideOutHorizontally(tween(210, easing = FastOutSlowInEasing)) {
                                    if (forward) -it / 4 else it / 4
                                } + fadeOut(tween(140))
                            )
                        },
                        label = "exam-month"
                    ) { displayedMonth ->
                        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            completeExamMonthDates(displayedMonth).chunked(7).forEach { row ->
                                Row(Modifier.fillMaxWidth()) {
                                    row.forEach { day ->
                                        ExamPlannerDay(
                                            day = day,
                                            inCurrentMonth = YearMonth.from(day) == displayedMonth,
                                            selected = day == selectedDay,
                                            today = day == today,
                                            count = appealsByDay[day].orEmpty().size,
                                            onSelect = {
                                                selectedDay = it
                                                month = YearMonth.from(it)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Column {
                Text(formatExamDate(selectedDay), style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                Text(if (selectedAppeals.isEmpty()) "Nessun appello" else
                    "${selectedAppeals.size} ${if (selectedAppeals.size == 1) "appello" else "appelli"}",
                    Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selectedAppeals.isEmpty()) item {
            ExamEmptyPanel(
                if (savedAppeals.isEmpty()) "Nessun appello salvato" else "Giornata libera",
                if (savedAppeals.isEmpty()) "Salva un appello con il segnalibro per ritrovarlo nel calendario."
                else "Non hai appelli salvati per questa data."
            )
        } else items(selectedAppeals, key = { "planner:${it.courseCode}:${it.id}" }) { appeal ->
            ExamPlannerAppeal(appeal, onClick = { onSelectAppeal(appeal) })
        }
    }
}

@Composable
private fun RowScope.ExamPlannerDay(
    day: LocalDate,
    inCurrentMonth: Boolean,
    selected: Boolean,
    today: Boolean,
    count: Int,
    onSelect: (LocalDate) -> Unit
) {
    Box(
        modifier = Modifier.weight(1f).height(56.dp).clickable { onSelect(day) },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Surface(
                shape = CircleShape,
                color = when {
                    selected -> MaterialTheme.colorScheme.primary
                    today -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                },
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected || today) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            selected -> MaterialTheme.colorScheme.onPrimary
                            !inCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            today -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(count.coerceAtMost(3)) {
                    Box(Modifier.size(4.dp).background(
                        when {
                            selected -> MaterialTheme.colorScheme.onPrimary
                            !inCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        CircleShape))
                }
            }
        }
    }
}

@Composable
private fun ExamPlannerAppeal(appeal: ExamAppeal, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(appeal.time.ifBlank { "--:--" }, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(appeal.subjectName, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOf(appeal.courseName.ifBlank { appeal.courseCode }, appeal.location)
                    .filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Apri dettagli",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExamAppealDetails(
    appeal: ExamAppeal,
    savedNote: String,
    onSaveNote: (ExamAppeal, String) -> Unit,
    onRemove: (ExamAppeal) -> Unit,
    onAddToCalendar: (ExamAppeal) -> Unit
) {
    var note by remember(appeal.courseCode, appeal.id) { mutableStateOf(savedNote) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(appeal.subjectName, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text(listOf(appeal.subjectCode, appeal.subjectPortalCode).filter { it.isNotBlank() }.joinToString(" · "),
                Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(500) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(18.dp),
                label = { Text("Note personali") },
                supportingText = { Text("${note.length}/500 · salvate solo su questo dispositivo") }
            )
            OutlinedButton(
                onClick = { onSaveNote(appeal, note) },
                enabled = note.trim() != savedNote,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text(if (note.isBlank()) "Rimuovi nota" else "Salva nota")
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ExamDetailField("Data", formatExamDate(appeal.date))
                    ExamDetailField("Ora", appeal.time.ifBlank { "Da definire" })
                    ExamDetailField("Corso di laurea",
                        listOf(appeal.courseName, appeal.courseCode).filter { it.isNotBlank() }.joinToString(" · "))
                    if (appeal.location.isNotBlank()) ExamDetailField("Luogo", appeal.location)
                    if (appeal.teacher.isNotBlank()) ExamDetailField("Docente", appeal.teacher)
                    if (appeal.testType.isNotBlank()) ExamDetailField("Prova", appeal.testType)
                    if (appeal.appealType.isNotBlank()) ExamDetailField("Tipo di appello", appeal.appealType)
                    val range = listOf(appeal.surnameFrom, appeal.surnameTo)
                        .filter { it.isNotBlank() }.joinToString("–")
                    if (range.isNotBlank()) ExamDetailField("Fascia alfabetica", range)
                    val registration = listOfNotNull(
                        appeal.registrationOpen?.let { "dal ${formatShortDate(it)}" },
                        appeal.registrationClose?.let { "al ${formatShortDate(it)}" }
                    ).joinToString(" ")
                    if (registration.isNotBlank()) ExamDetailField("Iscrizioni", registration)
                    RegistrationBadge(appeal)
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { onAddToCalendar(appeal) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Aggiungi al calendario")
            }
            OutlinedButton(
                onClick = { onRemove(appeal) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Outlined.Bookmark, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Rimuovi dai miei appelli")
            }
        }
    }
}

private fun completeExamMonthDates(month: YearMonth): List<LocalDate> {
    val leadingDays = month.atDay(1).dayOfWeek.value - 1
    val visibleDays = leadingDays + month.lengthOfMonth()
    val cellCount = ((visibleDays + 6) / 7) * 7
    val firstVisibleDay = month.atDay(1).minusDays(leadingDays.toLong())
    return List(cellCount) { firstVisibleDay.plusDays(it.toLong()) }
}

@Composable
private fun ExamDetailField(label: String, value: String) {
    if (value.isBlank()) return
    Column {
        Text(label.uppercase(examLocale), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(value, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ExamAppealCard(
    appeal: ExamAppeal, saved: Boolean, onToggleSaved: () -> Unit,
    onAddToCalendar: (ExamAppeal) -> Unit
) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        IconButton(onClick = onToggleSaved, modifier = Modifier.size(42.dp)) {
                            Icon(if (saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (saved) "Rimuovi dai miei appelli" else "Salva nei miei appelli")
                        }
                    }
                    Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        IconButton(onClick = { onAddToCalendar(appeal) }, modifier = Modifier.size(42.dp)) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = "Aggiungi al calendario")
                        }
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
