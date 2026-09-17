package app.orariunimi

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.ViewWeek
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
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class CalendarData(
    val title: String,
    val lessons: List<Lesson>,
    val year: String,
    val source: SearchItem?,
    val updatedAtMillis: Long,
    val offline: Boolean,
    val combinedSubjects: List<SavedSubject>? = null
)
data class CourseDetailData(val year: String, val course: SearchItem)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrariApp(initialTab: Int = 0, openSavedRequest: Int = 0) {
    val context = LocalContext.current
    val store = remember(context) { LocalStore(context.applicationContext) }
    val api = remember(context) {
        UnimiApi(cache = ResponseCache(File(context.cacheDir, "orari-responses")))
    }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(initialTab) }
    var settings by remember { mutableStateOf(false) }
    var agendaOpen by remember { mutableStateOf(false) }
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
    var calendarRefreshing by remember { mutableStateOf(false) }
    var calendarLoadId by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(store.saved()) }
    var savedSchedule by remember { mutableStateOf<ScheduleSnapshot?>(null) }
    var savedScheduleLoading by remember { mutableStateOf(false) }
    var favorites by remember { mutableStateOf(store.favoriteCourses()) }
    var weekend by remember { mutableStateOf(store.showWeekend) }
    var week by remember { mutableStateOf(startOfWeek(LocalDate.now())) }
    var selectedDay by remember { mutableStateOf(LocalDate.now()) }
    var updateState by remember { mutableStateOf<AppUpdateState>(AppUpdateState.Idle) }
    var pendingInstall by remember { mutableStateOf<File?>(null) }
    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val apk = pendingInstall
        if (apk != null && AppUpdater.canInstall(context)) {
            runCatching { AppUpdater.install(context, apk) }
                .onFailure { failure ->
                    scope.launch { snackbar.showSnackbar(failure.message ?: "Impossibile aprire l'installazione.") }
                }
            pendingInstall = null
        }
    }

    LaunchedEffect(openSavedRequest) {
        if (openSavedRequest > 0) {
            settings = false
            agendaOpen = false
            calendar = null
            courseDetail = null
            tab = 1
        }
    }

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

    LaunchedEffect(saved) {
        if (saved.isEmpty()) {
            savedSchedule = null
            savedScheduleLoading = false
            ScheduleWidgetProvider.updateAll(context)
            return@LaunchedEffect
        }
        savedScheduleLoading = true
        val cached = withContext(Dispatchers.IO) { runCatching { api.cachedSavedLessons(saved) }.getOrNull() }
        if (cached != null) {
            savedSchedule = cached
            ScheduleWidgetProvider.updateAll(context)
        }
        try {
            savedSchedule = withContext(Dispatchers.IO) { api.refreshSavedLessons(saved) }
            ScheduleWidgetProvider.updateAll(context)
        } catch (_: Exception) {
            // The personal calendar remains available from its recent cache.
        } finally {
            savedScheduleLoading = false
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
        ScheduleWidgetProvider.updateAll(context)
    }

    fun checkForUpdate() {
        scope.launch {
            updateState = AppUpdateState.Checking
            updateState = try {
                val release = withContext(Dispatchers.IO) { AppUpdater.latestRelease() }
                if (AppUpdater.isNewer(release.version, BuildConfig.VERSION_NAME)) {
                    AppUpdateState.Available(release)
                } else AppUpdateState.UpToDate
            } catch (_: Exception) {
                AppUpdateState.Error("Impossibile controllare gli aggiornamenti. Verifica la connessione e riprova.")
            }
        }
    }

    fun downloadUpdate(release: AppRelease) {
        scope.launch {
            updateState = AppUpdateState.Downloading(release, null)
            updateState = try {
                val apk = withContext(Dispatchers.IO) { AppUpdater.download(context, release) {} }
                AppUpdateState.Ready(release, apk)
            } catch (cause: Exception) {
                val message = cause.message.orEmpty()
                AppUpdateState.Error(if (message.contains("integrità")) message
                    else "Impossibile scaricare l'aggiornamento. Verifica la connessione e riprova.")
            }
        }
    }

    fun installUpdate(file: File) {
        if (AppUpdater.canInstall(context)) {
            runCatching { AppUpdater.install(context, file) }
                .onFailure { failure ->
                    scope.launch { snackbar.showSnackbar(failure.message ?: "Impossibile aprire l'installazione.") }
                }
        } else {
            pendingInstall = file
            runCatching { unknownSourcesLauncher.launch(AppUpdater.unknownSourcesIntent(context)) }
                .onFailure { failure ->
                    pendingInstall = null
                    scope.launch { snackbar.showSnackbar(failure.message ?: "Impossibile aprire il permesso di installazione.") }
                }
        }
    }

    fun openSettings() {
        settings = true
        error = null
        if (updateState is AppUpdateState.Idle) checkForUpdate()
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

    fun showCalendar(
        title: String, yearCode: String, source: SearchItem?,
        combined: List<SavedSubject>? = null, openDay: LocalDate? = null,
        openAgenda: Boolean = false
    ) {
        calendarLoadId++
        val loadId = calendarLoadId
        scope.launch {
            agendaOpen = false
            error = null
            val sameCalendar = calendar?.let {
                it.title == title && it.year == yearCode && it.source?.kind == source?.kind &&
                    it.source?.code == source?.code && it.combinedSubjects == combined
            } == true
            busy = !sameCalendar
            calendarRefreshing = true
            var selectionApplied = false

            fun show(snapshot: ScheduleSnapshot, offline: Boolean) {
                if (loadId != calendarLoadId) return
                val current = calendar
                val same = current?.let {
                    it.title == title && it.year == yearCode && it.source?.kind == source?.kind &&
                        it.source?.code == source?.code && it.combinedSubjects == combined
                } == true
                if (!same || openDay != null && !selectionApplied) {
                    val initialDay = openDay ?: openingDay(LocalDate.now(), weekend)
                    week = startOfWeek(initialDay)
                    selectedDay = initialDay
                    selectionApplied = true
                }
                calendar = CalendarData(title, snapshot.lessons, yearCode, source,
                    snapshot.updatedAtMillis, offline, combined)
                if (openAgenda) agendaOpen = true
            }

            val cached = withContext(Dispatchers.IO) {
                runCatching {
                    if (combined != null) api.cachedSavedLessons(combined)
                    else api.cachedLessons(yearCode, requireNotNull(source))
                }.getOrNull()
            }
            if (cached != null && loadId == calendarLoadId) {
                show(cached, offline = false)
                busy = false
            }

            try {
                val fresh = withContext(Dispatchers.IO) {
                    if (combined != null) api.refreshSavedLessons(combined)
                    else api.refreshLessons(yearCode, requireNotNull(source))
                }
                show(fresh, offline = false)
            } catch (cause: Exception) {
                if (loadId == calendarLoadId) {
                    if (cached != null) show(cached, offline = true)
                    else error = cause.message ?: "Impossibile recuperare le lezioni."
                }
            } finally {
                if (loadId == calendarLoadId) {
                    busy = false
                    calendarRefreshing = false
                }
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
        calendarLoadId++
        calendarRefreshing = false
        busy = false
        when {
            agendaOpen -> agendaOpen = false
            calendar != null -> calendar = null
            courseDetail != null -> courseDetail = null
            else -> settings = false
        }
        error = null
    }

    BackHandler(enabled = calendar != null || courseDetail != null || agendaOpen || settings) { goBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when {
                        agendaOpen -> Text(if (calendar != null) "Agenda · ${calendar!!.title}" else "Agenda personale",
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    if (calendar != null || courseDetail != null || agendaOpen || settings) IconButton(onClick = ::goBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    val shown = calendar
                    if (shown != null && !agendaOpen) IconButton(onClick = { agendaOpen = true; error = null }) {
                        Icon(Icons.Outlined.ViewWeek, contentDescription = "Apri vista agenda")
                    }
                    if (shown?.source?.kind == SearchKind.SUBJECT && !agendaOpen) {
                        val subject = SavedSubject(shown.year, shown.source.code, shown.source.name)
                        val isSaved = saved.any { it.year == subject.year && it.code == subject.code }
                        IconButton(onClick = { toggleSaved(subject) }) {
                            Icon(if (isSaved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (isSaved) "Rimuovi dai miei orari" else "Salva nei miei orari") }
                    } else if (!agendaOpen && (shown?.source?.kind == SearchKind.COURSE ||
                        calendar == null && courseDetail != null)) {
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
                    } else if (calendar == null && courseDetail == null && !agendaOpen && !settings) {
                        IconButton(onClick = ::openSettings) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Preferenze")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (calendar == null && courseDetail == null && !agendaOpen && !settings) NavigationBar {
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
                if (loadingEntries && tab == 0 && calendar == null && courseDetail == null && !agendaOpen && !settings || busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (error != null) ErrorBanner(error!!, onDismiss = { error = null },
                    onRetry = if (year == null) {{ yearRetry++ }} else if (calendar == null && courseDetail == null && tab == 0) {{
                        entries = entries - kind
                        entriesRetry++
                    }} else null)
                when {
                    settings -> SettingsScreen(
                        weekend = weekend,
                        onWeekend = ::toggleWeekend,
                        currentVersion = BuildConfig.VERSION_NAME,
                        updateState = updateState,
                        onCheckUpdate = ::checkForUpdate,
                        onDownloadUpdate = ::downloadUpdate,
                        onInstallUpdate = ::installUpdate
                    )
                    agendaOpen -> ScheduleAgendaScreen(
                        lessons = calendar?.lessons ?: savedSchedule?.lessons,
                        loading = if (calendar != null) calendarRefreshing else savedScheduleLoading,
                        weekend = weekend,
                        onOpenDay = { day ->
                            if (calendar != null) {
                                agendaOpen = false
                                week = startOfWeek(day)
                                selectedDay = day
                            } else showCalendar("I miei orari", year?.code.orEmpty(), null, saved, day)
                        }
                    )
                    calendar != null -> {
                        val shown = calendar!!
                        CalendarScreen(shown, week, selectedDay, weekend, saved,
                            refreshing = calendarRefreshing,
                            onToggleSubject = { lesson ->
                                toggleSaved(SavedSubject(shown.year, lesson.subjectCode, lesson.subject))
                            },
                            onRefresh = { showCalendar(shown.title, shown.year, shown.source,
                                shown.combinedSubjects) },
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
                            onOpenAgenda = { showCalendar(detail.course.name, detail.year, detail.course,
                                openAgenda = true) },
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
                        personalLessons = savedSchedule?.lessons,
                        loadingPersonal = savedScheduleLoading,
                        onOpen = ::openSaved,
                        onCombined = { showCalendar("I miei orari", year?.code.orEmpty(), null, saved) },
                        onOpenNext = { day ->
                            showCalendar("I miei orari", year?.code.orEmpty(), null, saved, day)
                        },
                        onAgenda = { agendaOpen = true; error = null },
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

fun openingDay(today: LocalDate, weekend: Boolean): LocalDate =
    if (weekend || today.dayOfWeek.value <= DayOfWeek.FRIDAY.value) today
    else startOfWeek(today).plusDays(4)

fun adjacentCalendarDay(day: LocalDate, direction: Long, weekend: Boolean): LocalDate {
    require(direction == -1L || direction == 1L)
    var adjacent = day.plusDays(direction)
    while (!weekend && adjacent.dayOfWeek.value > DayOfWeek.FRIDAY.value) {
        adjacent = adjacent.plusDays(direction)
    }
    return adjacent
}

fun preferredDay(lessons: List<Lesson>, week: LocalDate, weekend: Boolean): LocalDate =
    lessons.firstOrNull { !it.date.isBefore(week) && it.date.isBefore(week.plusDays(if (weekend) 7 else 5)) }?.date
        ?: week
