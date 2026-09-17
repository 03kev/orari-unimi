package app.orariunimi

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Suppress("DEPRECATION") // Required for scrollable collection widgets on Android 8–11.
class ScheduleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAsync(context, manager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action !in setOf(ACTION_PREVIOUS, ACTION_NEXT, ACTION_TODAY)) return
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val weekend = LocalStore(context).showWeekend
        val current = openingDay(selectedDay(context, appWidgetId), weekend)
        val selected = when (intent.action) {
            ACTION_PREVIOUS -> adjacentCalendarDay(current, -1, weekend)
            ACTION_NEXT -> adjacentCalendarDay(current, 1, weekend)
            else -> openingDay(LocalDate.now(), weekend)
        }
        preferences(context).edit().putString(dayKey(appWidgetId), selected.toString()).apply()
        updateAsync(context, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId), partial = true)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = preferences(context).edit()
        appWidgetIds.forEach { editor.remove(dayKey(it)) }
        editor.apply()
    }

    private fun updateAsync(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        partial: Boolean = false
    ) {
        val pendingResult = goAsync()
        Thread {
            try {
                appWidgetIds.forEach { appWidgetId ->
                    val views = createViews(context, appWidgetId, bindAdapter = !partial)
                    if (partial) manager.partiallyUpdateAppWidget(appWidgetId, views)
                    else manager.updateAppWidget(appWidgetId, views)
                    manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_lessons)
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun createViews(context: Context, appWidgetId: Int, bindAdapter: Boolean): RemoteViews {
        val store = LocalStore(context)
        val saved = store.saved()
        val snapshot = if (saved.isEmpty()) null else runCatching {
            UnimiApi(cache = ResponseCache(File(context.cacheDir, "orari-responses"))).cachedSavedLessons(saved)
        }.getOrNull()
        val today = LocalDate.now()
        val storedDay = selectedDay(context, appWidgetId)
        val selected = openingDay(storedDay, store.showWeekend)
        if (selected != storedDay) {
            preferences(context).edit().putString(dayKey(appWidgetId), selected.toString()).apply()
        }
        val selectedLessons = snapshot?.lessons?.let { lessonsForDay(it, selected) }
        val views = RemoteViews(context.packageName, R.layout.widget_next_lesson)
        val openApp = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.OPEN_SAVED, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(
            context, appWidgetId, openApp, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ))
        views.setOnClickPendingIntent(R.id.widget_previous,
            dayPendingIntent(context, appWidgetId, ACTION_PREVIOUS, 1))
        views.setOnClickPendingIntent(R.id.widget_next,
            dayPendingIntent(context, appWidgetId, ACTION_NEXT, 2))
        views.setOnClickPendingIntent(R.id.widget_today_area,
            dayPendingIntent(context, appWidgetId, ACTION_TODAY, 3))
        if (bindAdapter) {
            views.setRemoteAdapter(R.id.widget_lessons,
                Intent(context, ScheduleWidgetLessonsService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = Uri.parse("orariunimi://widget/$appWidgetId")
                })
        }
        views.setTextViewText(R.id.widget_title, when (selected) {
            today -> "Agenda di oggi"
            today.plusDays(1) -> "Agenda di domani"
            else -> "Agenda del giorno"
        })

        when {
            saved.isEmpty() -> {
                views.setTextViewText(R.id.widget_subject, "Il tuo orario")
                showWidgetMessage(views, "Nessun insegnamento salvato")
            }
            selectedLessons == null -> {
                views.setTextViewText(R.id.widget_subject, "Calendario da aggiornare")
                showWidgetMessage(views, "Apri l’app per controllare gli orari")
            }
            selectedLessons.isEmpty() -> {
                views.setTextViewText(R.id.widget_subject, selected.format(widgetDay)
                    .replaceFirstChar { it.titlecase(Locale.ITALIAN) })
                showWidgetMessage(views, "Nessuna lezione")
            }
            else -> {
                val count = selectedLessons.size
                val day = selected.format(widgetDay).replaceFirstChar { it.titlecase(Locale.ITALIAN) }
                views.setTextViewText(R.id.widget_subject,
                    "$day · $count ${if (count == 1) "lezione" else "lezioni"}")
                views.setViewVisibility(R.id.widget_lessons, View.VISIBLE)
                views.setViewVisibility(R.id.widget_empty, View.GONE)
            }
        }
        return views
    }

    companion object {
        private const val ACTION_PREVIOUS = "app.orariunimi.widget.PREVIOUS"
        private const val ACTION_NEXT = "app.orariunimi.widget.NEXT"
        private const val ACTION_TODAY = "app.orariunimi.widget.TODAY"
        private const val WIDGET_PREFERENCES = "schedule_widget"
        private val widgetDay = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)
        internal val widgetLessonColors = intArrayOf(
            0xFF6F91F2.toInt(), 0xFF40B9AE.toInt(), 0xFFE08188.toInt(), 0xFFC49A3A.toInt(),
            0xFFAF8AE1.toInt(), 0xFF62A7D8.toInt(), 0xFFDC925E.toInt(), 0xFF7CAD70.toInt()
        )

        private fun showWidgetMessage(views: RemoteViews, message: String) {
            views.setViewVisibility(R.id.widget_lessons, View.GONE)
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            views.setTextViewText(R.id.widget_empty, message)
        }

        private fun dayKey(appWidgetId: Int) = "selected_day_$appWidgetId"

        private fun preferences(context: Context) =
            context.getSharedPreferences(WIDGET_PREFERENCES, Context.MODE_PRIVATE)

        internal fun selectedDay(context: Context, appWidgetId: Int): LocalDate = runCatching {
            LocalDate.parse(preferences(context).getString(dayKey(appWidgetId), null))
        }.getOrDefault(LocalDate.now())

        private fun dayPendingIntent(
            context: Context, appWidgetId: Int, action: String, actionId: Int
        ): PendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId * 10 + actionId,
            Intent(context, ScheduleWidgetProvider::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, ScheduleWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            context.sendBroadcast(Intent(context, ScheduleWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            })
        }
    }
}
