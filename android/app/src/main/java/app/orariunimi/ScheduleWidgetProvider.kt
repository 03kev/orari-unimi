package app.orariunimi

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.io.File
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
        val next = snapshot?.lessons?.let(::nextLesson)
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
            next == null -> {
                views.setTextViewText(R.id.widget_subject, "Calendario da aggiornare")
                views.setTextViewText(R.id.widget_time, "Apri l’app per controllare gli orari")
                views.setViewVisibility(R.id.widget_details, View.GONE)
            }
            else -> {
                val day = next.date.format(widgetDate).replaceFirstChar { it.titlecase(Locale.ITALIAN) }
                views.setTextViewText(R.id.widget_subject, next.subject.ifBlank { "Insegnamento" })
                views.setTextViewText(R.id.widget_time, "$day · ${next.start}–${next.end}")
                val overlapping = lessonKey(next) in conflictingLessonKeys(snapshot.lessons)
                val details = listOfNotNull(
                    next.room.takeIf { it.isNotBlank() },
                    "Sovrapposizione".takeIf { overlapping }
                ).joinToString(" · ")
                views.setTextViewText(R.id.widget_details, details)
                views.setViewVisibility(R.id.widget_details, if (details.isBlank()) View.GONE else View.VISIBLE)
            }
        }
        return views
    }

    companion object {
        private val widgetDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)

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
