package app.orariunimi

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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
        val current = selectedDay(context, appWidgetId)
        val selected = when (intent.action) {
            ACTION_PREVIOUS -> current.minusDays(1)
            ACTION_NEXT -> current.plusDays(1)
            else -> LocalDate.now()
        }
        preferences(context).edit().putString(dayKey(appWidgetId), selected.toString()).apply()
        updateAsync(context, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = preferences(context).edit()
        appWidgetIds.forEach { editor.remove(dayKey(it)) }
        editor.apply()
    }

    private fun updateAsync(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        Thread {
            try {
                appWidgetIds.forEach { appWidgetId ->
                    manager.updateAppWidget(appWidgetId, createViews(context, appWidgetId))
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun createViews(context: Context, appWidgetId: Int): RemoteViews {
        val saved = LocalStore(context).saved()
        val snapshot = if (saved.isEmpty()) null else runCatching {
            UnimiApi(cache = ResponseCache(File(context.cacheDir, "orari-responses"))).cachedSavedLessons(saved)
        }.getOrNull()
        val today = LocalDate.now()
        val selected = selectedDay(context, appWidgetId)
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
        views.setOnClickPendingIntent(R.id.widget_subject,
            dayPendingIntent(context, appWidgetId, ACTION_TODAY, 3))
        views.setTextViewText(R.id.widget_title, when (selected) {
            today -> "Lezioni di oggi"
            today.plusDays(1) -> "Lezioni di domani"
            else -> "Lezioni del giorno"
        })

        when {
            saved.isEmpty() -> {
                views.setTextViewText(R.id.widget_subject, "Nessun insegnamento salvato")
                views.setTextViewText(R.id.widget_time, "Tocca per aggiungerne uno")
                views.setViewVisibility(R.id.widget_details, View.GONE)
            }
            selectedLessons == null -> {
                views.setTextViewText(R.id.widget_subject, "Calendario da aggiornare")
                views.setTextViewText(R.id.widget_time, "Apri l’app per controllare gli orari")
                views.setViewVisibility(R.id.widget_details, View.GONE)
            }
            selectedLessons.isEmpty() -> {
                views.setTextViewText(R.id.widget_subject, selected.format(widgetDay)
                    .replaceFirstChar { it.titlecase(Locale.ITALIAN) })
                views.setTextViewText(R.id.widget_time, "Nessuna lezione")
                views.setTextViewText(R.id.widget_details, "Tocca la data per tornare a oggi")
                views.setViewVisibility(R.id.widget_details, View.VISIBLE)
            }
            else -> {
                val count = selectedLessons.size
                val day = selected.format(widgetDate).replaceFirstChar { it.titlecase(Locale.ITALIAN) }
                views.setTextViewText(R.id.widget_subject,
                    "$day · $count ${if (count == 1) "lezione" else "lezioni"}")
                views.setTextViewText(R.id.widget_time, selectedLessons.joinToString("\n") { lesson ->
                    val room = lesson.room.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                    val cancelled = if (lesson.cancelled) "ANNULLATA · " else ""
                    "${lesson.start}–${lesson.end}  $cancelled${lesson.subject.ifBlank { "Insegnamento" }}$room"
                })
                views.setTextViewTextSize(R.id.widget_time, TypedValue.COMPLEX_UNIT_SP,
                    if (count > 5) 10f else if (count > 3) 11f else 12f)
                views.setViewVisibility(R.id.widget_details, View.GONE)
            }
        }
        return views
    }

    companion object {
        private const val ACTION_PREVIOUS = "app.orariunimi.widget.PREVIOUS"
        private const val ACTION_NEXT = "app.orariunimi.widget.NEXT"
        private const val ACTION_TODAY = "app.orariunimi.widget.TODAY"
        private const val WIDGET_PREFERENCES = "schedule_widget"
        private val widgetDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)
        private val widgetDay = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)

        private fun dayKey(appWidgetId: Int) = "selected_day_$appWidgetId"

        private fun preferences(context: Context) =
            context.getSharedPreferences(WIDGET_PREFERENCES, Context.MODE_PRIVATE)

        private fun selectedDay(context: Context, appWidgetId: Int): LocalDate = runCatching {
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
