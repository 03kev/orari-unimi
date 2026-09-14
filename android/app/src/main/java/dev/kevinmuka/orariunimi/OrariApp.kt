package dev.kevinmuka.orariunimi

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

data class CalendarData(val title: String, val lessons: List<Lesson>, val year: String, val source: SearchItem?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrariApp() {
    val context = LocalContext.current
    val store = remember(context) { LocalStore(context.applicationContext) }
    val api = remember { UnimiApi() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val rootFocus = remember { FocusRequester() }

    var tab by remember { mutableIntStateOf(0) }
    var settings by remember { mutableStateOf(false) }
    var calendar by remember { mutableStateOf<CalendarData?>(null) }
    var years by remember { mutableStateOf<List<AcademicYear>>(emptyList()) }
    var year by remember { mutableStateOf<AcademicYear?>(null) }
    var yearRetry by remember { mutableIntStateOf(0) }
    var loadingYears by remember { mutableStateOf(true) }
    var kind by remember { mutableStateOf(SearchKind.COURSE) }
    var query by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<Map<SearchKind, List<SearchItem>>>(emptyMap()) }
    var entriesRetry by remember { mutableIntStateOf(0) }
    var loadingEntries by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(store.saved()) }
    var weekend by remember { mutableStateOf(store.showWeekend) }
    var vim by remember { mutableStateOf(store.vimNavigation) }
    var week by remember { mutableStateOf(startOfWeek(LocalDate.now())) }
    var selectedDay by remember { mutableStateOf(LocalDate.now()) }
    var selectedSearchIndex by remember { mutableIntStateOf(0) }
    var selectedSavedIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(yearRetry) {
        loadingYears = true
        try {
            val fetched = withContext(Dispatchers.IO) { api.years() }
            if (fetched.isEmpty()) throw IllegalStateException("Nessun anno accademico disponibile.")
            years = fetched
            year = fetched.first()
            error = null
        } catch (cause: Exception) {
            error = cause.message ?: "Impossibile recuperare gli anni accademici."
        } finally {
            loadingYears = false
        }
    }

    LaunchedEffect(year?.code, kind, entriesRetry) {
        val currentYear = year ?: return@LaunchedEffect
        if (entries.containsKey(kind)) return@LaunchedEffect
        loadingEntries = true
        try {
            val fetched = withContext(Dispatchers.IO) { api.entries(kind, currentYear.code) }
            entries = entries + (kind to fetched)
            error = null
        } catch (cause: Exception) {
            error = cause.message ?: "Impossibile caricare l'elenco."
        } finally {
            loadingEntries = false
        }
    }

    val results = filterItems(entries[kind].orEmpty(), query)
    LaunchedEffect(query, kind) { selectedSearchIndex = 0 }

    fun toggleWeekend() {
        weekend = !weekend
        store.showWeekend = weekend
        if (!weekend && selectedDay.dayOfWeek.value > DayOfWeek.FRIDAY.value) selectedDay = week
    }

    fun toggleVim() {
        vim = !vim
        store.vimNavigation = vim
        scope.launch { snackbar.showSnackbar("Navigazione Vim ${if (vim) "attiva" else "disattivata"}") }
    }

    fun moveWeek(amount: Long) {
        week = week.plusWeeks(amount)
        selectedDay = preferredDay(calendar?.lessons.orEmpty(), week, weekend)
    }

    fun showCalendar(title: String, yearCode: String, source: SearchItem?, combined: List<SavedSubject>? = null) {
        scope.launch {
            busy = true
            error = null
            try {
                val lessons = withContext(Dispatchers.IO) {
                    if (combined != null) api.savedLessons(combined) else api.lessons(yearCode, requireNotNull(source))
                }
                val initialWeek = closestWeek(lessons)
                week = initialWeek
                selectedDay = preferredDay(lessons, initialWeek, weekend)
                calendar = CalendarData(title, lessons, yearCode, source)
            } catch (cause: Exception) {
                error = cause.message ?: "Impossibile recuperare le lezioni."
            } finally {
                busy = false
            }
        }
    }

    fun openItem(item: SearchItem) {
        val currentYear = year ?: return
        showCalendar(item.name, currentYear.code, item)
    }

    fun openSaved(subject: SavedSubject) = showCalendar(
        subject.name, subject.year, SearchItem(subject.code, subject.name, SearchKind.SUBJECT)
    )

    BackHandler(enabled = calendar != null || settings) {
        if (calendar != null) calendar = null else settings = false
        error = null
    }

    LaunchedEffect(Unit) { rootFocus.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when {
                        calendar != null -> Text(calendar!!.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        settings -> Text("Preferenze")
                        else -> Column {
                            Text("Orari UNIMI", style = MaterialTheme.typography.titleLarge)
                            Text(year?.name ?: "Anno accademico", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    if (calendar != null || settings) IconButton(onClick = {
                        if (calendar != null) calendar = null else settings = false
                        error = null
                    }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Indietro") }
                },
                actions = {
                    val shown = calendar
                    if (shown?.source?.kind == SearchKind.SUBJECT) {
                        val subject = SavedSubject(shown.year, shown.source.code, shown.source.name)
                        val isSaved = saved.any { it.year == subject.year && it.code == subject.code }
                        IconButton(onClick = {
                            if (isSaved) {
                                store.remove(subject)
                                saved = store.saved()
                                scope.launch { snackbar.showSnackbar("Rimosso dai tuoi orari") }
                            } else {
                                store.add(subject)
                                saved = store.saved()
                                scope.launch { snackbar.showSnackbar("Aggiunto ai tuoi orari") }
                            }
                        }) { Icon(if (isSaved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (isSaved) "Rimuovi dai miei orari" else "Salva nei miei orari") }
                    } else if (calendar == null && !settings) {
                        IconButton(onClick = { settings = true; error = null }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Preferenze")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (calendar == null && !settings) NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0; error = null },
                    icon = { Icon(Icons.Outlined.Search, contentDescription = null) }, label = { Text("Esplora") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1; error = null },
                    icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) }, label = { Text("I miei orari") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).focusRequester(rootFocus).focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val key = event.nativeKeyEvent.unicodeChar.toChar().lowercaseChar()
                    if (searchFocused && calendar == null && !settings) {
                        if (event.key == Key.Escape) { focusManager.clearFocus(); true } else false
                    } else if (calendar != null) {
                        when {
                            key == 'q' || event.key == Key.Escape -> { calendar = null; true }
                            key == 'w' -> { toggleWeekend(); true }
                            key == 'v' -> { toggleVim(); true }
                            vim && key == 'h' -> { moveWeek(-1); true }
                            vim && key == 'l' -> { moveWeek(1); true }
                            else -> false
                        }
                    } else if (!settings) {
                        when {
                            key == 'v' -> { toggleVim(); true }
                            !vim -> false
                            tab == 0 && key == 'j' && results.isNotEmpty() -> {
                                selectedSearchIndex = (selectedSearchIndex + 1) % results.size; true
                            }
                            tab == 0 && key == 'k' && results.isNotEmpty() -> {
                                selectedSearchIndex = (selectedSearchIndex - 1 + results.size) % results.size; true
                            }
                            tab == 0 && key == 'l' && results.isNotEmpty() -> {
                                openItem(results[selectedSearchIndex.coerceIn(results.indices)]); true
                            }
                            tab == 1 && key == 'j' && saved.isNotEmpty() -> {
                                selectedSavedIndex = (selectedSavedIndex + 1) % saved.size; true
                            }
                            tab == 1 && key == 'k' && saved.isNotEmpty() -> {
                                selectedSavedIndex = (selectedSavedIndex - 1 + saved.size) % saved.size; true
                            }
                            tab == 1 && key == 'l' && saved.isNotEmpty() -> {
                                openSaved(saved[selectedSavedIndex.coerceIn(saved.indices)]); true
                            }
                            tab == 1 && key == 'h' -> { tab = 0; true }
                            else -> false
                        }
                    } else false
                }
        ) {
            Column(Modifier.fillMaxSize()) {
                if (loadingEntries && tab == 0 && calendar == null && !settings || busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (error != null) ErrorBanner(error!!, onDismiss = { error = null },
                    onRetry = if (year == null) {{ yearRetry++ }} else if (calendar == null && tab == 0) {{
                        entries = entries - kind
                        entriesRetry++
                    }} else null)
                when {
                    settings -> SettingsScreen(weekend, vim, ::toggleWeekend, ::toggleVim)
                    calendar != null -> CalendarScreen(calendar!!, week, selectedDay, weekend,
                        onMoveWeek = ::moveWeek, onSelectDay = { selectedDay = it }, onToggleWeekend = ::toggleWeekend)
                    tab == 0 -> SearchScreen(
                        years = years, year = year, loadingYears = loadingYears,
                        kind = kind, query = query, results = results, loadingEntries = loadingEntries,
                        selectedIndex = selectedSearchIndex,
                        onYear = { selected -> year = selected; entries = emptyMap(); query = ""; error = null },
                        onKind = { kind = it; query = ""; error = null },
                        onQuery = { query = it }, onFocus = { searchFocused = it }, onSelect = ::openItem,
                        onRetryYears = { yearRetry++ }, onRetryEntries = {
                            entries = entries - kind
                            entriesRetry++
                        }
                    )
                    else -> SavedScreen(
                        saved = saved, selectedIndex = selectedSavedIndex,
                        onOpen = ::openSaved,
                        onCombined = { showCalendar("I miei orari", year?.code.orEmpty(), null, saved) },
                        onAdd = { tab = 0; kind = SearchKind.SUBJECT; query = "" },
                        onRemove = { store.remove(it); saved = store.saved(); selectedSavedIndex = 0 },
                        onClear = { store.clear(); saved = emptyList(); selectedSavedIndex = 0 }
                    )
                }
            }
            if (busy) CircularProgressIndicator(Modifier.align(androidx.compose.ui.Alignment.Center))
        }
    }
}

fun startOfWeek(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun closestWeek(lessons: List<Lesson>, today: LocalDate = LocalDate.now()): LocalDate {
    val current = startOfWeek(today)
    if (lessons.isEmpty() || lessons.any { !it.date.isBefore(current) && it.date.isBefore(current.plusWeeks(1)) }) return current
    val closest = lessons.minWith(compareBy<Lesson> { abs(ChronoUnit.DAYS.between(today, it.date)) }
        .thenByDescending { it.date })
    return startOfWeek(closest.date)
}

fun preferredDay(lessons: List<Lesson>, week: LocalDate, weekend: Boolean): LocalDate =
    lessons.firstOrNull { !it.date.isBefore(week) && it.date.isBefore(week.plusDays(if (weekend) 7 else 5)) }?.date
        ?: week
