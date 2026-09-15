package dev.kevinmuka.orariunimi

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

data class CalendarData(val title: String, val lessons: List<Lesson>, val year: String, val source: SearchItem?)
data class CourseDetailData(val year: String, val course: SearchItem)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrariApp() {
    val context = LocalContext.current
    val store = remember(context) { LocalStore(context.applicationContext) }
    val api = remember { UnimiApi() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }
    var settings by remember { mutableStateOf(false) }
    var calendar by remember { mutableStateOf<CalendarData?>(null) }
    var courseDetail by remember { mutableStateOf<CourseDetailData?>(null) }
    var years by remember { mutableStateOf<List<AcademicYear>>(emptyList()) }
    var year by remember { mutableStateOf<AcademicYear?>(null) }
    var yearRetry by remember { mutableIntStateOf(0) }
    var loadingYears by remember { mutableStateOf(true) }
    var kind by remember { mutableStateOf(SearchKind.COURSE) }
    var query by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<Map<SearchKind, SearchIndex>>(emptyMap()) }
    var entriesRetry by remember { mutableIntStateOf(0) }
    var loadingEntries by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(store.saved()) }
    var favorites by remember { mutableStateOf(store.favoriteCourses()) }
    var weekend by remember { mutableStateOf(store.showWeekend) }
    var week by remember { mutableStateOf(startOfWeek(LocalDate.now())) }
    var selectedDay by remember { mutableStateOf(LocalDate.now()) }

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
            val index = withContext(Dispatchers.Default) { SearchIndex(fetched) }
            entries = entries + (kind to index)
            error = null
        } catch (cause: Exception) {
            error = cause.message ?: "Impossibile caricare l'elenco."
        } finally {
            loadingEntries = false
        }
    }

    val searchIndex = entries[kind]
    val results by produceState<List<SearchItem>?>(null, query, searchIndex) {
        if (query.isNotBlank() && searchIndex != null) {
            delay(120)
            value = withContext(Dispatchers.Default) { searchIndex.search(query) }
        }
    }

    fun toggleWeekend() {
        weekend = !weekend
        store.showWeekend = weekend
        if (!weekend && selectedDay.dayOfWeek.value > DayOfWeek.FRIDAY.value) selectedDay = week
    }

    fun toggleSaved(subject: SavedSubject) {
        val isSaved = saved.any { it.year == subject.year && it.code == subject.code }
        if (isSaved) store.remove(subject) else store.add(subject)
        saved = store.saved()
        scope.launch { snackbar.showSnackbar(if (isSaved) "Rimosso dai tuoi orari" else "Aggiunto ai tuoi orari") }
    }

    fun toggleFavorite(course: FavoriteCourse) {
        val isFavorite = favorites.any { it.year == course.year && it.code == course.code }
        if (isFavorite) store.removeFavoriteCourse(course) else store.addFavoriteCourse(course)
        favorites = store.favoriteCourses()
        scope.launch { snackbar.showSnackbar(if (isFavorite) "Rimosso dai preferiti" else "Corso salvato nei preferiti") }
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
        if (item.kind != SearchKind.COURSE) {
            showCalendar(item.name, currentYear.code, item)
            return
        }
        scope.launch {
            busy = true
            error = null
            try {
                val course = withContext(Dispatchers.IO) { api.courseWithTeachings(currentYear.code, item) }
                courseDetail = CourseDetailData(currentYear.code, course)
            } catch (cause: Exception) {
                error = cause.message ?: "Impossibile aprire il corso."
            } finally {
                busy = false
            }
        }
    }

    fun openFavorite(favorite: FavoriteCourse) {
        scope.launch {
            busy = true
            error = null
            try {
                val cached = if (favorite.year == year?.code) entries[SearchKind.COURSE]?.items
                    ?.firstOrNull { it.code == favorite.code } else null
                val course = cached ?: withContext(Dispatchers.IO) {
                    api.entries(SearchKind.COURSE, favorite.year).firstOrNull { it.code == favorite.code }
                } ?: throw IllegalStateException("Il corso non è più disponibile per l'anno ${favorite.year}.")
                val complete = withContext(Dispatchers.IO) { api.courseWithTeachings(favorite.year, course) }
                courseDetail = CourseDetailData(favorite.year, complete)
            } catch (cause: Exception) {
                error = cause.message ?: "Impossibile aprire il corso preferito."
            } finally {
                busy = false
            }
        }
    }

    fun openSaved(subject: SavedSubject) = showCalendar(
        subject.name, subject.year, SearchItem(subject.code, subject.name, SearchKind.SUBJECT)
    )

    fun goBack() {
        when {
            calendar != null -> calendar = null
            courseDetail != null -> courseDetail = null
            else -> settings = false
        }
        error = null
    }

    BackHandler(enabled = calendar != null || courseDetail != null || settings) { goBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when {
                        calendar != null -> Text(calendar!!.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        courseDetail != null -> Text(courseDetail!!.course.name, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        settings -> Text("Preferenze")
                        else -> Column {
                            Text("Orari UNIMI", style = MaterialTheme.typography.titleLarge)
                            Text(year?.name ?: "Anno accademico", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    if (calendar != null || courseDetail != null || settings) IconButton(onClick = ::goBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    val shown = calendar
                    if (shown?.source?.kind == SearchKind.SUBJECT) {
                        val subject = SavedSubject(shown.year, shown.source.code, shown.source.name)
                        val isSaved = saved.any { it.year == subject.year && it.code == subject.code }
                        IconButton(onClick = { toggleSaved(subject) }) {
                            Icon(if (isSaved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (isSaved) "Rimuovi dai miei orari" else "Salva nei miei orari") }
                    } else if (shown?.source?.kind == SearchKind.COURSE ||
                        calendar == null && courseDetail != null) {
                        val detail = if (shown?.source?.kind == SearchKind.COURSE)
                            CourseDetailData(shown.year, shown.source) else courseDetail!!
                        val favorite = FavoriteCourse(detail.year, detail.course.code, detail.course.name,
                            detail.course.degreeType ?: DegreeType.OTHER)
                        val isFavorite = favorites.any { it.year == favorite.year && it.code == favorite.code }
                        IconButton(onClick = { toggleFavorite(favorite) }) {
                            Icon(if (isFavorite) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (isFavorite) "Rimuovi corso dai preferiti"
                                    else "Salva corso nei preferiti")
                        }
                    } else if (calendar == null && courseDetail == null && !settings) {
                        IconButton(onClick = { settings = true; error = null }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Preferenze")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (calendar == null && courseDetail == null && !settings) NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0; error = null },
                    icon = { Icon(Icons.Outlined.Search, contentDescription = null) }, label = { Text("Esplora") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1; error = null },
                    icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) }, label = { Text("I miei orari") }
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2; error = null },
                    icon = { Icon(Icons.Outlined.BookmarkBorder, contentDescription = null) }, label = { Text("Preferiti") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                if (loadingEntries && tab == 0 && calendar == null && courseDetail == null && !settings || busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (error != null) ErrorBanner(error!!, onDismiss = { error = null },
                    onRetry = if (year == null) {{ yearRetry++ }} else if (calendar == null && courseDetail == null && tab == 0) {{
                        entries = entries - kind
                        entriesRetry++
                    }} else null)
                when {
                    settings -> SettingsScreen(weekend, ::toggleWeekend)
                    calendar != null -> {
                        val shown = calendar!!
                        CalendarScreen(shown, week, selectedDay, weekend, saved,
                            onToggleSubject = { lesson ->
                                toggleSaved(SavedSubject(shown.year, lesson.subjectCode, lesson.subject))
                            },
                            onMoveWeek = ::moveWeek, onSelectDay = { day ->
                                week = startOfWeek(day)
                                selectedDay = day
                            })
                    }
                    courseDetail != null -> {
                        val detail = courseDetail!!
                        val favorite = FavoriteCourse(detail.year, detail.course.code, detail.course.name,
                            detail.course.degreeType ?: DegreeType.OTHER)
                        CourseDetailScreen(detail.course, detail.year,
                            favorite = favorites.any { it.year == favorite.year && it.code == favorite.code },
                            savedSubjects = saved,
                            onToggleFavorite = { toggleFavorite(favorite) },
                            onOpenCalendar = { showCalendar(detail.course.name, detail.year, detail.course) },
                            onOpenTeaching = { teaching ->
                                showCalendar(teaching.name, detail.year,
                                    SearchItem(teaching.code, teaching.name, SearchKind.SUBJECT))
                            },
                            onToggleTeaching = { teaching ->
                                toggleSaved(SavedSubject(detail.year, teaching.code, teaching.name))
                            })
                    }
                    tab == 0 -> SearchScreen(
                        years = years, year = year, loadingYears = loadingYears,
                        kind = kind, query = query, results = results, loadingEntries = loadingEntries,
                        favorites = favorites,
                        onYear = { selected -> year = selected; entries = emptyMap(); query = ""; error = null },
                        onKind = { kind = it; query = ""; error = null },
                        onQuery = { query = it }, onSelect = ::openItem,
                        onToggleCourseFavorite = { item ->
                            year?.let { selected -> toggleFavorite(FavoriteCourse(selected.code, item.code,
                                item.name, item.degreeType ?: DegreeType.OTHER)) }
                        },
                        onRetryYears = { yearRetry++ }, onRetryEntries = {
                            entries = entries - kind
                            entriesRetry++
                        }
                    )
                    tab == 1 -> SavedScreen(
                        saved = saved,
                        onOpen = ::openSaved,
                        onCombined = { showCalendar("I miei orari", year?.code.orEmpty(), null, saved) },
                        onAdd = { tab = 0; kind = SearchKind.SUBJECT; query = "" },
                        onRemove = { store.remove(it); saved = store.saved() },
                        onClear = { store.clear(); saved = emptyList() }
                    )
                    else -> FavoriteCoursesScreen(
                        favorites = favorites,
                        onOpen = ::openFavorite,
                        onRemove = ::toggleFavorite,
                        onExplore = { tab = 0; kind = SearchKind.COURSE; query = "" }
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
