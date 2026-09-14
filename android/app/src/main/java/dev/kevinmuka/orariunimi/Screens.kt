package dev.kevinmuka.orariunimi

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val italian = Locale.ITALIAN
private val dateLong = DateTimeFormatter.ofPattern("EEEE d MMMM", italian)
private val dateShort = DateTimeFormatter.ofPattern("d MMM", italian)
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
                OutlinedButton(onClick = { yearMenu = true }, enabled = years.isNotEmpty()) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(year?.name ?: "Anno accademico")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, Modifier.size(17.dp))
                }
                DropdownMenu(expanded = yearMenu, onDismissRequest = { yearMenu = false }) {
                    years.forEach { option ->
                        DropdownMenuItem(text = { Text(option.name) }, onClick = {
                            yearMenu = false; onYear(option)
                        })
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
    onOpen: (SavedSubject) -> Unit, onCombined: () -> Unit, onAdd: () -> Unit,
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
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("I miei orari", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text("I tuoi insegnamenti, raccolti in un unico calendario.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
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
            if (saved.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("SALVATI · ${saved.size}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { confirmClear = true }) { Text("Svuota elenco") }
                }
            }
        }
        if (saved.isEmpty()) EmptyPanel("Ancora nessun insegnamento",
            "Cerca un insegnamento e tocca il segnalibro nel calendario.")
        else LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
    onMoveWeek: (Long) -> Unit, onSelectDay: (LocalDate) -> Unit
) {
    val days = (0 until if (weekend) 7 else 5).map { week.plusDays(it.toLong()) }
    val visibleLessons = calendar.lessons.filter { it.date == selectedDay }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp)) {
            WeekHeader(week, onMoveWeek)
            Spacer(Modifier.height(14.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(days) { day ->
                    DayTile(day, selected = day == selectedDay,
                        count = calendar.lessons.count { it.date == day }, onClick = { onSelectDay(day) })
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("${calendar.lessons.size} lezioni nell’anno" +
                if (weekend) " · scorri i giorni per il weekend" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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
private fun WeekHeader(week: LocalDate, onMoveWeek: (Long) -> Unit) {
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
        IconButton(onClick = { onMoveWeek(-1) }) { Icon(Icons.Outlined.ChevronLeft, "Settimana precedente") }
        IconButton(onClick = { onMoveWeek(1) }) { Icon(Icons.Outlined.ChevronRight, "Settimana successiva") }
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
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
