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
        val pendingResult = goAsync()
        Thread {
            try {
                val views = createViews(context)
                appWidgetIds.forEach { manager.updateAppWidget(it, views) }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun createViews(context: Context): RemoteViews {
        val saved = LocalStore(context).saved()
        val snapshot = if (saved.isEmpty()) null else runCatching {
            UnimiApi(cache = ResponseCache(File(context.cacheDir, "orari-responses"))).cachedSavedLessons(saved)
        }.getOrNull()
        val today = LocalDate.now()
        val todayLessons = snapshot?.lessons?.let { lessonsForDay(it, today) }
        val views = RemoteViews(context.packageName, R.layout.widget_next_lesson)
        val openApp = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.OPEN_SAVED, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(
            context, 1, openApp, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ))

        when {
            saved.isEmpty() -> {
                views.setTextViewText(R.id.widget_subject, "Nessun insegnamento salvato")
                views.setTextViewText(R.id.widget_time, "Tocca per aggiungerne uno")
                views.setViewVisibility(R.id.widget_details, View.GONE)
            }
            todayLessons == null -> {
                views.setTextViewText(R.id.widget_subject, "Calendario da aggiornare")
                views.setTextViewText(R.id.widget_time, "Apri l’app per controllare gli orari")
                views.setViewVisibility(R.id.widget_details, View.GONE)
            }
            todayLessons.isEmpty() -> {
                views.setTextViewText(R.id.widget_subject, today.format(widgetDay)
                    .replaceFirstChar { it.titlecase(Locale.ITALIAN) })
                views.setTextViewText(R.id.widget_time, "Nessuna lezione oggi")
                views.setTextViewText(R.id.widget_details, "Tocca per aprire I miei orari")
                views.setViewVisibility(R.id.widget_details, View.VISIBLE)
            }
            else -> {
                val count = todayLessons.size
                val day = today.format(widgetDate).replaceFirstChar { it.titlecase(Locale.ITALIAN) }
                views.setTextViewText(R.id.widget_subject,
                    "$day · $count ${if (count == 1) "lezione" else "lezioni"}")
                views.setTextViewText(R.id.widget_time, todayLessons.joinToString("\n") { lesson ->
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
        private val widgetDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)
        private val widgetDay = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)

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
