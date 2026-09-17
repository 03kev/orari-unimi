package app.orariunimi

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val italian = Locale.ITALIAN
private val dateLong = DateTimeFormatter.ofPattern("EEEE d MMMM", italian)
private val dateShort = DateTimeFormatter.ofPattern("d MMM", italian)
private val nextLessonDate = DateTimeFormatter.ofPattern("EEE d MMM", italian)
private val updateTime = DateTimeFormatter.ofPattern("HH:mm", italian)
private val updateDateTime = DateTimeFormatter.ofPattern("d MMM, HH:mm", italian)
private val lessonColors = listOf(
    Color(0xFF526FCC), Color(0xFF008A83), Color(0xFFB15D63), Color(0xFF927224),
    Color(0xFF8C68C2), Color(0xFF3C82B4), Color(0xFFB56D3C), Color(0xFF5F8A50)
)

@Composable
fun SearchScreen(
    years: List<AcademicYear>, year: AcademicYear?, loadingYears: Boolean,
    kind: SearchKind, query: String, results: List<SearchItem>?, loadingEntries: Boolean,
    favorites: List<FavoriteCourse>,
    onYear: (AcademicYear) -> Unit, onKind: (SearchKind) -> Unit,
    onQuery: (String) -> Unit, onSelect: (SearchItem) -> Unit,
    onToggleCourseFavorite: (SearchItem) -> Unit,
    onRetryYears: () -> Unit, onRetryEntries: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var yearMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("Trova il tuo orario", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text("Lezioni pubblicate dall’Università degli Studi di Milano.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Box {
                OutlinedButton(
                    onClick = { yearMenu = !yearMenu },
                    enabled = years.isNotEmpty(),
                    modifier = Modifier.width(172.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(year?.name ?: "Anno accademico")
                    Spacer(Modifier.width(6.dp))
                    Icon(if (yearMenu) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = if (yearMenu) "Chiudi elenco anni" else "Apri elenco anni",
                        modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = yearMenu, onDismissRequest = { yearMenu = false },
                    modifier = Modifier.width(172.dp)) {
                    years.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.name) },
                            onClick = { yearMenu = false; onYear(option) },
                            trailingIcon = if (option == year) {{
                                Icon(Icons.Outlined.Check, contentDescription = "Anno selezionato")
                            }} else null
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchKind.entries.forEach { option ->
                    FilterChip(selected = kind == option, onClick = { onKind(option) }, label = { Text(option.label) },
                        leadingIcon = if (kind == option) {{
                            Icon(when (option) {
                                SearchKind.COURSE -> Icons.Outlined.School
                                SearchKind.TEACHER -> Icons.Outlined.PersonOutline
                                SearchKind.SUBJECT -> Icons.AutoMirrored.Outlined.MenuBook
                            }, contentDescription = null, Modifier.size(18.dp))
                        }} else null)
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(),
                singleLine = true, shape = RoundedCornerShape(18.dp),
                label = { Text("Cerca ${kind.label.lowercase(italian)}") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {{
                    IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, contentDescription = "Cancella ricerca") }
                }} else null
            )
            if (query.isNotBlank() && !loadingEntries && results != null) {
                Spacer(Modifier.height(10.dp))
                Text(if (results.size == 40) "Primi 40 risultati · affina la ricerca"
                    else "${results.size} ${if (results.size == 1) "risultato" else "risultati"}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when {
            loadingYears || loadingEntries -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
            year == null -> EmptyPanel("Nessun anno disponibile", "Controlla la connessione e riprova.",
                action = "Riprova", onAction = onRetryYears)
            query.isBlank() -> EmptyPanel("Inizia a cercare", "Scrivi il nome o il codice di un ${kind.subtitle.lowercase(italian)}.")
            results == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
            results.isEmpty() -> EmptyPanel("Nessun risultato", "Prova con un nome diverso o con il codice.",
                action = "Ricarica elenco", onAction = onRetryEntries)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(results, key = { item -> "${item.kind}:${item.code}" }) { item ->
                    SearchResultCard(item,
                        favorite = favorites.any { it.year == year.code && it.code == item.code },
                        onClick = { focusManager.clearFocus(); onSelect(item) },
                        onToggleFavorite = { onToggleCourseFavorite(item) })
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(item: SearchItem, favorite: Boolean, onClick: () -> Unit,
                             onToggleFavorite: () -> Unit) {
    val palette = item.degreeType?.let { degreePalette(it) }
    Card(
        onClick = onClick, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = palette?.container
            ?: MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 15.dp, top = 10.dp, bottom = 10.dp, end = 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(when (item.kind) {
                SearchKind.COURSE -> Icons.Outlined.School
                SearchKind.TEACHER -> Icons.Outlined.PersonOutline
                SearchKind.SUBJECT -> Icons.AutoMirrored.Outlined.MenuBook
            }, contentDescription = null, tint = palette?.accent ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = palette?.content ?: MaterialTheme.colorScheme.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(item.code, style = MaterialTheme.typography.labelMedium,
                        color = (palette?.content ?: MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.75f))
                    if (item.degreeType != null) Text("· ${item.degreeType.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette?.content ?: MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (item.kind == SearchKind.COURSE) IconButton(onClick = onToggleFavorite) {
                Icon(if (favorite) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (favorite) "Rimuovi ${item.name} dai preferiti"
                        else "Salva ${item.name} nei preferiti", tint = palette?.accent ?: MaterialTheme.colorScheme.primary)
            } else Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SavedScreen(
    saved: List<SavedSubject>,
    personalLessons: List<Lesson>?, loadingPersonal: Boolean,
    onOpen: (SavedSubject) -> Unit, onCombined: () -> Unit,
    onOpenNext: (LocalDate) -> Unit, onAgenda: () -> Unit, onAdd: () -> Unit,
    onRemove: (SavedSubject) -> Unit, onClear: () -> Unit
) {
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Svuotare i miei orari?") },
        text = { Text("Gli insegnamenti salvati verranno rimossi da questo dispositivo.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text("Elimina tutto") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annulla") } }
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("I miei orari", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold)
                if (saved.isNotEmpty()) Surface(shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    IconButton(onClick = onAgenda, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Outlined.ViewWeek, contentDescription = "Apri vista agenda")
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Text("I tuoi insegnamenti, raccolti in un unico calendario.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (saved.isNotEmpty()) item {
            Spacer(Modifier.height(6.dp))
            NextLessonCard(personalLessons, loadingPersonal, onOpenNext)
        }
        item {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onCombined, enabled = saved.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, Modifier.size(19.dp))
                Spacer(Modifier.width(9.dp))
                Text("Apri calendario personale")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null, Modifier.size(19.dp))
                Spacer(Modifier.width(9.dp))
                Text("Aggiungi insegnamento")
            }
        }
        if (saved.isEmpty()) item {
            Spacer(Modifier.height(12.dp))
            EmptyCard("Ancora nessun insegnamento",
                "Cerca un insegnamento e tocca il segnalibro nel calendario.")
        } else {
            item {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("SALVATI · ${saved.size}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { confirmClear = true }) { Text("Svuota elenco") }
                }
            }
            items(saved, key = { item -> "${item.year}:${item.code}" }) { item ->
                Card(onClick = { onOpen(item) }, shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                            Text("${item.code} · ${item.year}", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onRemove(item) }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Rimuovi ${item.name}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NextLessonCard(lessons: List<Lesson>?, loading: Boolean, onOpen: (LocalDate) -> Unit) {
    val next = lessons?.let(::nextLesson)
    Card(onClick = { next?.let { onOpen(it.date) } }, shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    if (loading && lessons == null) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.CalendarMonth, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text("PROSSIMA LEZIONE", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                when {
                    lessons == null -> Text("Aggiornamento del calendario…",
                        style = MaterialTheme.typography.bodyMedium)
                    next == null -> Text("Nessuna lezione in programma",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    else -> {
                        Text(next.subject.ifBlank { "Insegnamento" }, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${next.date.format(nextLessonDate).replaceFirstChar { it.titlecase(italian) }} · " +
                            "${next.start}–${next.end}" + if (next.room.isNotBlank()) " · ${next.room}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleAgendaScreen(
    lessons: List<Lesson>?, loading: Boolean, weekend: Boolean, onOpenDay: (LocalDate) -> Unit
) {
    var range by remember { mutableStateOf(AgendaRange.WEEK) }
    var anchorDay by remember { mutableStateOf(openingDay(LocalDate.now(), weekend)) }
    val week = startOfWeek(anchorDay)
    val days = if (range == AgendaRange.DAY) listOf(anchorDay) else
        (0 until if (weekend) 7 else 5).map { week.plusDays(it.toLong()) }
    val weekEnd = week.plusDays(if (weekend) 7 else 5)
    val visibleLessons = remember(lessons, range, anchorDay, week, weekend) {
        if (range == AgendaRange.DAY) lessonsForDay(lessons.orEmpty(), anchorDay)
        else lessons.orEmpty().filter { !it.date.isBefore(week) && it.date.isBefore(weekEnd) }
    }
    val placements = remember(visibleLessons) { timelineLessons(visibleLessons) }
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    val timeWidth = 58.dp
    val slotHeight = 52.dp
    val firstMinute = 8 * 60 + 30
    val lastMinute = 19 * 60 + 30
    val slots = (lastMinute - firstMinute) / 30
    val gridHeight = slotHeight * slots

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                anchorDay = if (range == AgendaRange.DAY)
                    adjacentCalendarDay(anchorDay, -1, weekend) else anchorDay.minusWeeks(1)
            }) {
                Icon(Icons.Outlined.ChevronLeft,
                    contentDescription = if (range == AgendaRange.DAY) "Giorno precedente" else "Settimana precedente")
            }
            Column(Modifier.weight(1f)) {
                Text(if (range == AgendaRange.DAY) "GIORNO" else "SETTIMANA",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(if (range == AgendaRange.DAY)
                    anchorDay.format(dateLong).replaceFirstChar { it.titlecase(italian) }
                else "${week.format(dateShort)} – ${week.plusDays(6).format(dateShort)}",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { anchorDay = openingDay(LocalDate.now(), weekend) }) { Text("Oggi") }
            IconButton(onClick = {
                anchorDay = if (range == AgendaRange.DAY)
                    adjacentCalendarDay(anchorDay, 1, weekend) else anchorDay.plusWeeks(1)
            }) {
                Icon(Icons.Outlined.ChevronRight,
                    contentDescription = if (range == AgendaRange.DAY) "Giorno successivo" else "Settimana successiva")
            }
        }
        AgendaRangeSelector(range = range, onRange = { range = it })
        when {
            lessons == null && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> BoxWithConstraints(
                Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
            ) {
                val gridColor = MaterialTheme.colorScheme.outlineVariant
                val availableDayWidth = maxWidth - timeWidth
                val dayWidth = if (range == AgendaRange.DAY && availableDayWidth > 240.dp)
                    availableDayWidth else if (range == AgendaRange.DAY) 240.dp else 138.dp
                val dayWidthPixels = with(LocalDensity.current) { dayWidth.roundToPx() }

                LaunchedEffect(horizontal.maxValue, range, anchorDay, weekend, dayWidthPixels) {
                    if (range == AgendaRange.DAY) horizontal.scrollTo(0)
                    else {
                        val todayIndex = days.indexOf(LocalDate.now())
                        if (horizontal.maxValue > 0 && todayIndex >= 0) {
                            horizontal.scrollTo((todayIndex * dayWidthPixels).coerceAtMost(horizontal.maxValue))
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        Modifier.fillMaxSize().clipToBounds().pointerInput(horizontal, vertical) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                horizontal.dispatchRawDelta(-dragAmount.x)
                                vertical.dispatchRawDelta(-dragAmount.y)
                            }
                        }
                    ) {
                        Row(Modifier.fillMaxWidth().height(66.dp)) {
                            Box(
                                Modifier.width(timeWidth).fillMaxHeight(),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Text("ORA", Modifier.padding(bottom = 9.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Canvas(Modifier.fillMaxSize()) {
                                    drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                        end = androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = 1f)
                                }
                            }
                            Box(Modifier.weight(1f).clipToBounds()) {
                                Row(Modifier.horizontalScroll(horizontal, enabled = false)) {
                                    days.forEach { day ->
                                        val today = day == LocalDate.now()
                                        Surface(color = if (today) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainerLow,
                                            modifier = Modifier.width(dayWidth).fillMaxHeight()) {
                                            Box {
                                                Column(Modifier.fillMaxSize(),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center) {
                                                    Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, italian).uppercase(italian),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (today) MaterialTheme.colorScheme.primary
                                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontWeight = FontWeight.Bold)
                                                    Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge,
                                                        fontWeight = FontWeight.Bold)
                                                }
                                                Canvas(Modifier.fillMaxSize()) {
                                                    drawLine(gridColor,
                                                        start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                                        end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                                                        strokeWidth = 1f)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.weight(1f).fillMaxWidth().clipToBounds()
                            .verticalScroll(vertical, enabled = false)) {
                            Box(Modifier.width(timeWidth).height(gridHeight)) {
                                val gridColor = MaterialTheme.colorScheme.outlineVariant
                                Canvas(Modifier.fillMaxSize()) {
                                    for (index in 1 until slots) {
                                        val y = slotHeight.toPx() * index
                                        drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, y),
                                            end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1f)
                                    }
                                    drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                        end = androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = 1f)
                                }
                                repeat(slots + 1) { index ->
                                    val minute = firstMinute + index * 30
                                    val labelOffset = when (index) {
                                        0 -> 2.dp
                                        slots -> gridHeight - 18.dp
                                        else -> slotHeight * index - 8.dp
                                    }
                                    Text(LocalTime.of(minute / 60, minute % 60).format(updateTime),
                                        modifier = Modifier.width(timeWidth).offset(y = labelOffset),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1)
                                }
                            }
                            Box(Modifier.weight(1f).clipToBounds()) {
                                Box(Modifier.horizontalScroll(horizontal, enabled = false)) {
                                    Box(Modifier.width(dayWidth * days.size).height(gridHeight)) {
                                        val gridColor = MaterialTheme.colorScheme.outlineVariant
                                        Canvas(Modifier.fillMaxSize()) {
                                            for (index in 1 until slots) {
                                                val y = slotHeight.toPx() * index
                                                drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, y),
                                                    end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1f)
                                            }
                                            for (index in 1 until days.size) {
                                                val x = dayWidth.toPx() * index
                                                drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(x, 0f),
                                                    end = androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1f)
                                            }
                                        }
                                        placements.forEach { placement ->
                                            val lesson = placement.lesson
                                            val dayIndex = days.indexOf(lesson.date)
                                            val start = timelineMinutes(lesson.start).coerceAtLeast(firstMinute)
                                            val end = timelineMinutes(lesson.end).coerceAtMost(lastMinute)
                                            if (dayIndex >= 0 && end > start) {
                                                val laneWidth = dayWidth / placement.laneCount
                                                val top = slotHeight * ((start - firstMinute) / 30f)
                                                val height = maxOf(44.dp, slotHeight * ((end - start) / 30f) - 4.dp)
                                                TimelineLessonCard(
                                                    lesson = lesson,
                                                    compact = placement.laneCount > 1,
                                                    modifier = Modifier
                                                        .offset(
                                                            x = dayWidth * dayIndex + laneWidth * placement.lane + 2.dp,
                                                            y = top + 2.dp
                                                        )
                                                        .width(laneWidth - 4.dp)
                                                        .height(height),
                                                    onClick = { onOpenDay(lesson.date) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class AgendaRange { DAY, WEEK }

@Composable
private fun AgendaRangeSelector(range: AgendaRange, onRange: (AgendaRange) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(Modifier.padding(4.dp)) {
            AgendaRangeOption("Giorno", range == AgendaRange.DAY, Modifier.weight(1f)) {
                onRange(AgendaRange.DAY)
            }
            AgendaRangeOption("Settimana", range == AgendaRange.WEEK, Modifier.weight(1f)) {
                onRange(AgendaRange.WEEK)
            }
        }
    }
}

@Composable
private fun AgendaRangeOption(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun TimelineLessonCard(lesson: Lesson, compact: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = lessonColors[(lesson.subjectCode.hashCode() and Int.MAX_VALUE) % lessonColors.size]
    val background = if (lesson.cancelled) MaterialTheme.colorScheme.errorContainer else accent.copy(alpha = 0.24f)
    val content = if (lesson.cancelled) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    Surface(onClick = onClick, modifier = modifier, color = background, shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(if (compact) 5.dp else 7.dp)) {
            Text(lesson.subject.ifBlank { "Insegnamento" },
                color = content, fontSize = if (compact) 9.sp else 12.sp,
                fontWeight = FontWeight.Bold, maxLines = if (compact) 5 else 3,
                overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(if (compact) 2.dp else 3.dp))
            Text("${lesson.start}–${lesson.end}", color = content.copy(alpha = 0.82f),
                fontSize = if (compact) 8.sp else 10.sp, maxLines = 1)
            if (lesson.room.isNotBlank()) Text(lesson.room, color = content.copy(alpha = 0.72f),
                fontSize = if (compact) 8.sp else 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun timelineMinutes(value: String): Int = runCatching {
    val time = LocalTime.parse(value, DateTimeFormatter.ofPattern("H:mm"))
    time.hour * 60 + time.minute
}.getOrDefault(0)

@Composable
fun SettingsScreen(weekend: Boolean, onWeekend: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Personalizza il calendario", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text("Le preferenze restano salvate su questo dispositivo.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
        }
        item { SettingCard(Icons.Outlined.CalendarMonth, "Mostra il weekend",
            "Aggiunge sabato e domenica alla settimana.", weekend, onWeekend) }
        item {
            Spacer(Modifier.height(12.dp))
            Text("DATI E PRIVACY", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(9.dp))
            Text("Gli orari arrivano dal portale pubblico UNIMI. Insegnamenti, corsi preferiti e preferenze rimangono solo sul telefono.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String,
                        value: Boolean, onChange: () -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onChange).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = value, onCheckedChange = { onChange() })
        }
    }
}

@Composable
fun CalendarScreen(
    calendar: CalendarData, week: LocalDate, selectedDay: LocalDate, weekend: Boolean,
    savedSubjects: List<SavedSubject>, onToggleSubject: (Lesson) -> Unit,
    refreshing: Boolean, onRefresh: () -> Unit,
    onMoveWeek: (Long) -> Unit, onSelectDay: (LocalDate) -> Unit
) {
    var showMonth by remember { mutableStateOf(false) }
    val days = (0 until if (weekend) 7 else 5).map { week.plusDays(it.toLong()) }
    val visibleLessons = calendar.lessons.filter { it.date == selectedDay }
    if (showMonth) MonthCalendarDialog(
        selectedDay = selectedDay,
        lessons = calendar.lessons,
        onDismiss = { showMonth = false },
        onSelectDay = { day -> onSelectDay(day); showMonth = false }
    )
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp)) {
            WeekHeader(week, onMoveWeek, onOpenMonth = { showMonth = true })
            Spacer(Modifier.height(14.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(days) { day ->
                    DayTile(day, selected = day == selectedDay,
                        count = calendar.lessons.count { it.date == day }, onClick = { onSelectDay(day) })
                }
            }
            Spacer(Modifier.height(6.dp))
            CalendarFreshness(calendar, weekend, refreshing, onRefresh)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
        LazyColumn(
            modifier = Modifier.fillMaxSize().pointerInput(selectedDay, weekend) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onHorizontalDrag = { change, amount -> drag += amount; change.consume() },
                    onDragCancel = { drag = 0f },
                    onDragEnd = {
                        if (drag > 70f) onSelectDay(adjacentCalendarDay(selectedDay, -1, weekend))
                        else if (drag < -70f) onSelectDay(adjacentCalendarDay(selectedDay, 1, weekend))
                        drag = 0f
                    }
                )
            },
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(selectedDay.format(dateLong).replaceFirstChar { it.titlecase(italian) },
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(if (visibleLessons.isEmpty()) "Nessuna lezione" else
                    "${visibleLessons.size} ${if (visibleLessons.size == 1) "lezione" else "lezioni"}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (visibleLessons.isEmpty()) item {
                EmptyCard("Nessuna lezione in questo giorno", "Scegli un’altra data o cambia settimana.")
            }
            items(visibleLessons, key = { "${it.id}:${it.subjectCode}:${it.start}" }) { lesson ->
                val canSave = calendar.source?.kind == SearchKind.COURSE ||
                    calendar.source?.kind == SearchKind.TEACHER
                LessonCard(lesson,
                    saved = savedSubjects.any { it.year == calendar.year && it.code == lesson.subjectCode },
                    onToggleSave = if (canSave) {{ onToggleSubject(lesson) }} else null)
            }
        }
    }
}

@Composable
private fun CalendarFreshness(
    calendar: CalendarData, weekend: Boolean, refreshing: Boolean, onRefresh: () -> Unit
) {
    val updated = remember(calendar.updatedAtMillis) {
        Instant.ofEpochMilli(calendar.updatedAtMillis).atZone(ZoneId.systemDefault())
    }
    val timestamp = if (updated.toLocalDate() == LocalDate.now()) {
        "alle ${updated.format(updateTime)}"
    } else {
        "il ${updated.format(updateDateTime)}"
    }
    val status = when {
        calendar.offline -> "Dati offline · aggiornati $timestamp"
        refreshing -> "Aggiornato $timestamp · controllo in corso"
        else -> "Aggiornato $timestamp"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (calendar.offline) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(6.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("${calendar.lessons.size} lezioni nell’anno" +
                if (weekend) " · scorri i giorni per il weekend" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(status, style = MaterialTheme.typography.labelSmall,
                color = if (calendar.offline) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRefresh, enabled = !refreshing, modifier = Modifier.size(40.dp)) {
            if (refreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.Outlined.Refresh, contentDescription = "Aggiorna calendario")
        }
    }
}

@Composable
private fun WeekHeader(week: LocalDate, onMoveWeek: (Long) -> Unit, onOpenMonth: () -> Unit) {
    var drag by remember(week) { mutableFloatStateOf(0f) }
    Row(
        Modifier.fillMaxWidth().pointerInput(week) {
            detectHorizontalDragGestures(
                onHorizontalDrag = { change, amount -> drag += amount; change.consume() },
                onDragEnd = {
                    if (drag > 70f) onMoveWeek(-1) else if (drag < -70f) onMoveWeek(1)
                    drag = 0f
                }
            )
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("SETTIMANA", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("${week.format(dateShort)} – ${week.plusDays(6).format(dateShort)}",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onOpenMonth) {
            Icon(Icons.Outlined.CalendarMonth, "Apri calendario mensile")
        }
        IconButton(onClick = { onMoveWeek(-1) }) { Icon(Icons.Outlined.ChevronLeft, "Settimana precedente") }
        IconButton(onClick = { onMoveWeek(1) }) { Icon(Icons.Outlined.ChevronRight, "Settimana successiva") }
    }
}

@Composable
private fun MonthCalendarDialog(
    selectedDay: LocalDate,
    lessons: List<Lesson>,
    onDismiss: () -> Unit,
    onSelectDay: (LocalDate) -> Unit
) {
    var month by remember(selectedDay) { mutableStateOf(YearMonth.from(selectedDay)) }
    var drag by remember(month) { mutableFloatStateOf(0f) }
    val lessonDays = remember(lessons) { lessons.groupingBy { it.date }.eachCount() }
    val monthTitle = remember(month) {
        month.format(DateTimeFormatter.ofPattern("MMMM yyyy", italian))
            .replaceFirstChar { it.titlecase(italian) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.Outlined.ChevronLeft, "Mese precedente")
                }
                Text(monthTitle, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { month = month.plusMonths(1) }) {
                    Icon(Icons.Outlined.ChevronRight, "Mese successivo")
                }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().pointerInput(month) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, amount -> drag += amount; change.consume() },
                    onDragEnd = {
                        if (drag > 70f) month = month.minusMonths(1)
                        else if (drag < -70f) month = month.plusMonths(1)
                        drag = 0f
                    }
                )
            }) {
                Row(Modifier.fillMaxWidth()) {
                    listOf("L", "M", "M", "G", "V", "S", "D").forEach { label ->
                        Box(Modifier.weight(1f).height(30.dp), contentAlignment = Alignment.Center) {
                            Text(label, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                monthDates(month).chunked(7).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { day -> MonthDay(day, selectedDay, lessonDays[day] ?: 0, onSelectDay) }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { onSelectDay(LocalDate.now()) }) { Text("Oggi") }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } }
    )
}

@Composable
private fun RowScope.MonthDay(
    day: LocalDate?, selectedDay: LocalDate, lessonCount: Int, onSelectDay: (LocalDate) -> Unit
) {
    if (day == null) {
        Spacer(Modifier.weight(1f).height(42.dp))
        return
    }
    val selected = day == selectedDay
    Surface(
        onClick = { onSelectDay(day) },
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        modifier = Modifier.weight(1f).height(42.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            Box(Modifier.size(4.dp).background(if (lessonCount > 0) {
                if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
            } else Color.Transparent, CircleShape))
        }
    }
}

fun monthDates(month: YearMonth): List<LocalDate?> {
    val leading = month.atDay(1).dayOfWeek.value - 1
    return List(42) { index ->
        val day = index - leading + 1
        if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
    }
}

@Composable
private fun DayTile(day: LocalDate, selected: Boolean, count: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.width(68.dp).height(86.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, italian).replaceFirstChar { it.titlecase(italian) },
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface)
            Box(Modifier.size(6.dp).background(if (count > 0) {
                if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
            } else Color.Transparent, CircleShape))
        }
    }
}

@Composable
private fun LessonCard(lesson: Lesson, saved: Boolean, onToggleSave: (() -> Unit)?) {
    val accent = lessonColors[(lesson.subjectCode.hashCode() and Int.MAX_VALUE) % lessonColors.size]
    Card(shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight()
                .background(if (lesson.cancelled) MaterialTheme.colorScheme.error else accent))
            Column(Modifier.weight(1f).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${lesson.start}–${lesson.end}", style = MaterialTheme.typography.labelLarge,
                        color = if (lesson.cancelled) MaterialTheme.colorScheme.error else accent,
                        fontWeight = FontWeight.Bold)
                    if (lesson.cancelled) {
                        Spacer(Modifier.width(10.dp))
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(7.dp)) {
                            Text("ANNULLATA", Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(lesson.subject.ifBlank { "Insegnamento" }, Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (onToggleSave != null) IconButton(onClick = onToggleSave) {
                        Icon(if (saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (saved) "Rimuovi ${lesson.subject} dai miei orari"
                                else "Salva ${lesson.subject} nei miei orari")
                    }
                }
                if (lesson.room.isNotBlank()) {
                    Spacer(Modifier.height(7.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Place, contentDescription = null, Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(lesson.room, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (lesson.teacher.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(lesson.teacher, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (lesson.notes.isNotBlank()) {
                    Spacer(Modifier.height(7.dp))
                    Text(lesson.notes, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun ErrorBanner(message: String, onDismiss: () -> Unit, onRetry: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(message, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer)
                if (onRetry != null) TextButton(onClick = onRetry) { Text("Riprova") }
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Chiudi") }
        }
    }
}

@Composable
private fun EmptyPanel(title: String, description: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape,
                modifier = Modifier.size(64.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.height(15.dp))
            Text(title, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(5.dp))
            Text(description, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (action != null && onAction != null) {
                Spacer(Modifier.height(14.dp))
                FilledTonalButton(onClick = onAction) { Text(action) }
            }
        }
    }
}

@Composable
private fun EmptyCard(title: String, description: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
