package app.orariunimi

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.io.File

class ScheduleWidgetLessonsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsService.RemoteViewsFactory =
        LessonFactory(applicationContext,
            intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))
}

private class LessonFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {
    private var lessons = emptyList<Lesson>()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            lessons = emptyList()
            return
        }
        val store = LocalStore(context)
        val saved = store.saved()
        val day = openingDay(ScheduleWidgetProvider.selectedDay(context, appWidgetId), store.showWeekend)
        lessons = if (saved.isEmpty()) emptyList() else runCatching {
            val snapshot = UnimiApi(
                cache = ResponseCache(File(context.cacheDir, "orari-responses"))
            ).cachedSavedLessons(saved)
            snapshot?.lessons?.let { lessonsForDay(it, day) }.orEmpty()
        }.getOrDefault(emptyList())
    }

    override fun onDestroy() {
        lessons = emptyList()
    }

    override fun getCount(): Int = lessons.size

    override fun getViewAt(position: Int): RemoteViews? = lessons.getOrNull(position)?.let { lesson ->
        RemoteViews(context.packageName, R.layout.widget_lesson_row).apply {
            setTextViewText(R.id.widget_lesson_time, "${lesson.start}\n${lesson.end}")
            setTextViewText(R.id.widget_lesson_subject,
                (if (lesson.cancelled) "ANNULLATA · " else "") +
                    lesson.subject.ifBlank { "Insegnamento" })
            setTextViewText(R.id.widget_lesson_room,
                listOf(lesson.room, lesson.teacher).filter { it.isNotBlank() }.joinToString(" · ")
                    .ifBlank { lesson.type.ifBlank { "Lezione" } })
            val accent = if (lesson.cancelled) context.getColor(R.color.widget_warning)
                else ScheduleWidgetProvider.widgetLessonColors[
                    (lesson.subjectCode.hashCode() and Int.MAX_VALUE) %
                        ScheduleWidgetProvider.widgetLessonColors.size
                ]
            setInt(R.id.widget_lesson_accent, "setBackgroundColor", accent)
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = lessons.getOrNull(position)?.let {
        listOf(it.date, it.start, it.end, it.subjectCode, it.room).hashCode().toLong()
    } ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
