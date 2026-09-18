package app.orariunimi

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit

object NotificationScheduler {
    private const val SCHEDULE_PERIODIC = "schedule-notifications-periodic"
    private const val SCHEDULE_NOW = "schedule-notifications-now"
    private const val UPDATES_PERIODIC = "update-notifications-periodic"
    private const val UPDATES_NOW = "update-notifications-now"
    private const val LESSON_REMINDER = "next-lesson-reminder"
    internal const val INTERNAL_ONLY = "internal-only"
    internal const val FORCE_REFRESH = "force-refresh"
    private const val REMINDER_KEY = "reminder-key"
    private const val REMINDER_ID = "reminder-id"
    private const val REMINDER_TITLE = "reminder-title"
    private const val REMINDER_MESSAGE = "reminder-message"
    private const val REMINDER_DATE = "reminder-date"
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun configure(context: Context, runNow: Boolean = false) {
        val appContext = context.applicationContext
        val work = WorkManager.getInstance(appContext)
        val preferences = LocalStore(appContext).notificationPreferences
        if (!preferences.enabled) {
            work.cancelUniqueWork(SCHEDULE_PERIODIC)
            work.cancelUniqueWork(SCHEDULE_NOW)
            work.cancelUniqueWork(UPDATES_PERIODIC)
            work.cancelUniqueWork(UPDATES_NOW)
            cancelLessonReminder(appContext)
            NotificationPublisher.cancelAll(appContext)
            return
        }

        NotificationPublisher.createChannels(appContext)
        if (preferences.importantChanges || preferences.lessonReminders) {
            val periodic = PeriodicWorkRequestBuilder<ScheduleNotificationWorker>(15, TimeUnit.MINUTES)
                .setConstraints(network).build()
            work.enqueueUniquePeriodicWork(SCHEDULE_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, periodic)
        } else {
            work.cancelUniqueWork(SCHEDULE_PERIODIC)
            work.cancelUniqueWork(SCHEDULE_NOW)
        }
        if (!preferences.lessonReminders) cancelLessonReminder(appContext)

        if (preferences.appUpdates) {
            val periodic = PeriodicWorkRequestBuilder<AppUpdateNotificationWorker>(15, TimeUnit.MINUTES)
                .setConstraints(network).build()
            work.enqueueUniquePeriodicWork(UPDATES_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, periodic)
        } else {
            work.cancelUniqueWork(UPDATES_PERIODIC)
            work.cancelUniqueWork(UPDATES_NOW)
        }

        if (runNow) enqueueNow(work, preferences)
    }

    fun refreshNow(context: Context): List<UUID> {
        val appContext = context.applicationContext
        val preferences = LocalStore(appContext).notificationPreferences
        if (!preferences.enabled) return emptyList()
        return enqueueNow(WorkManager.getInstance(appContext), preferences, forceRefresh = true)
    }

    fun scheduleLessonReminder(
        context: Context,
        lesson: Lesson,
        start: LocalDateTime,
        leadMinutes: Int,
        now: LocalDateTime = LocalDateTime.now()
    ) {
        val appContext = context.applicationContext
        val store = LocalStore(appContext)
        val key = "${NotificationEngine.lessonIdentity(lesson)}|${lesson.date}|${lesson.start}"
        if (store.lastLessonReminderKey == key) return
        val target = start.minusMinutes(leadMinutes.toLong())
        val targetMillis = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (store.scheduledLessonReminder() == (key to targetMillis)) return
        val message = "${lesson.subject} alle ${lesson.start}${if (lesson.room.isBlank()) "" else " · ${lesson.room}"}"
        val request = OneTimeWorkRequestBuilder<LessonReminderWorker>()
            .setInitialDelay(reminderDelayMillis(start, leadMinutes, now), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(
                REMINDER_KEY to key,
                REMINDER_ID to "reminder-${NotificationEngine.lessonFingerprint(lesson)}",
                REMINDER_TITLE to "Prossima lezione",
                REMINDER_MESSAGE to message,
                REMINDER_DATE to lesson.date.toString()
            ))
            .build()
        WorkManager.getInstance(appContext)
            .enqueueUniqueWork(LESSON_REMINDER, ExistingWorkPolicy.REPLACE, request)
        store.setScheduledLessonReminder(key, targetMillis)
    }

    fun cancelLessonReminder(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(LESSON_REMINDER)
        LocalStore(context.applicationContext).clearScheduledLessonReminder()
    }

    fun waitForRefresh(context: Context, ids: List<UUID>, timeoutMillis: Long = 30_000L): Boolean {
        if (ids.isEmpty()) return false
        val work = WorkManager.getInstance(context.applicationContext)
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (System.nanoTime() < deadline) {
            val states = runCatching {
                ids.mapNotNull { work.getWorkInfoById(it).get()?.state }
            }.getOrElse { return false }
            if (states.size == ids.size && states.all { it.isFinished }) {
                return states.all { it == androidx.work.WorkInfo.State.SUCCEEDED }
            }
            try {
                Thread.sleep(100)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return false
    }

    private fun enqueueNow(
        work: WorkManager,
        preferences: NotificationPreferences,
        forceRefresh: Boolean = false
    ): List<UUID> {
        val ids = mutableListOf<UUID>()
        if (preferences.importantChanges || preferences.lessonReminders) {
            val request = OneTimeWorkRequestBuilder<ScheduleNotificationWorker>()
                .setInputData(workDataOf(INTERNAL_ONLY to true, FORCE_REFRESH to forceRefresh))
                .setConstraints(network).build()
            work.enqueueUniqueWork(SCHEDULE_NOW, ExistingWorkPolicy.REPLACE, request)
            ids += request.id
        }
        if (preferences.appUpdates) {
            val request = OneTimeWorkRequestBuilder<AppUpdateNotificationWorker>()
                .setInputData(workDataOf(INTERNAL_ONLY to true))
                .setConstraints(network).build()
            work.enqueueUniqueWork(UPDATES_NOW, ExistingWorkPolicy.REPLACE, request)
            ids += request.id
        }
        return ids
    }
}

class ScheduleNotificationWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val allowSystemNotification = !inputData.getBoolean(NotificationScheduler.INTERNAL_ONLY, false)
        val forceRefresh = inputData.getBoolean(NotificationScheduler.FORCE_REFRESH, false)
        val store = LocalStore(applicationContext)
        val preferences = store.notificationPreferences
        if (!preferences.enabled || !preferences.importantChanges && !preferences.lessonReminders) return Result.success()
        val saved = store.saved()
        val baselineStore = NotificationBaselineStore(applicationContext)
        if (saved.isEmpty()) {
            baselineStore.clear()
            return Result.success()
        }

        val snapshot = try {
            val api = UnimiApi(cache = ResponseCache(File(applicationContext.cacheDir, "orari-responses")))
            if (forceRefresh) api.refreshSavedLessons(saved) else api.refreshSavedLessonsIfStale(saved)
        } catch (_: Exception) {
            return Result.retry()
        }

        val now = LocalDateTime.now()
        if (preferences.importantChanges) processChanges(
            store, baselineStore, snapshot.lessons, now.toLocalDate(), allowSystemNotification)
        if (preferences.lessonReminders) processReminder(
            snapshot.lessons, now, preferences.reminderMinutes)
        return Result.success()
    }

    private fun processChanges(
        store: LocalStore,
        baselineStore: NotificationBaselineStore,
        current: List<Lesson>,
        today: LocalDate,
        allowSystemNotification: Boolean
    ) {
        val future = current.filter { !it.date.isBefore(today) }
        val baseline = baselineStore.read()
        if (baseline == null) {
            baselineStore.write(NotificationBaseline(future))
            return
        }

        val oldFuture = baseline.lessons.filter { !it.date.isBefore(today) }
        if (NotificationEngine.shouldDeferEmptySnapshot(
                oldFuture, future, baseline.consecutiveEmptyResponses, today)) {
            baselineStore.write(baseline.copy(
                lessons = oldFuture,
                consecutiveEmptyResponses = baseline.consecutiveEmptyResponses + 1
            ))
            return
        }
        val priorMissing = if (future.isEmpty() && oldFuture.isNotEmpty()) {
            oldFuture.associate { NotificationEngine.lessonIdentity(it) to
                maxOf(1, baseline.missingCounts.getOrDefault(NotificationEngine.lessonIdentity(it), 0)) }
        } else baseline.missingCounts
        val evaluation = NotificationEngine.evaluate(oldFuture, future, priorMissing, today)
        baselineStore.write(NotificationBaseline(evaluation.lessons, evaluation.missingCounts, 0))

        val added = evaluation.drafts.map { draft ->
            AppNotificationEntry(draft.id, AppNotificationType.IMPORTANT_CHANGE, draft.title,
                draft.message, System.currentTimeMillis(), draft.targetDate)
        }.filter { store.addNotification(it) }
        when {
            added.size == 1 -> NotificationPublisher.post(
                applicationContext, added.single(), allowSystemNotification)
            added.size > 1 -> NotificationPublisher.postSummary(applicationContext, added.size,
                "${added.size} variazioni rilevate nei tuoi orari", allowSystemNotification)
        }
    }

    private fun processReminder(
        lessons: List<Lesson>,
        now: LocalDateTime,
        leadMinutes: Int
    ) {
        val next = lessons.asSequence().filter { !it.cancelled }.mapNotNull { lesson ->
            val start = runCatching {
                LocalDateTime.of(lesson.date, LocalTime.parse(lesson.start, DateTimeFormatter.ofPattern("H:mm")))
            }.getOrNull() ?: return@mapNotNull null
            if (start.isBefore(now)) null else lesson to start
        }.minByOrNull { it.second } ?: run {
            NotificationScheduler.cancelLessonReminder(applicationContext)
            return
        }
        val lesson = next.first
        NotificationScheduler.scheduleLessonReminder(applicationContext, lesson, next.second, leadMinutes, now)
    }
}

class LessonReminderWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val store = LocalStore(applicationContext)
        val key = inputData.getString("reminder-key") ?: return Result.failure()
        if (!store.notificationPreferences.let { it.enabled && it.lessonReminders }) {
            store.clearScheduledLessonReminder()
            return Result.success()
        }
        if (store.scheduledLessonReminder()?.first != key) return Result.success()
        if (store.lastLessonReminderKey == key) return Result.success()
        val entry = AppNotificationEntry(
            id = inputData.getString("reminder-id") ?: return Result.failure(),
            type = AppNotificationType.LESSON_REMINDER,
            title = inputData.getString("reminder-title") ?: "Prossima lezione",
            message = inputData.getString("reminder-message").orEmpty(),
            timestampMillis = System.currentTimeMillis(),
            targetDate = inputData.getString("reminder-date")?.let {
                runCatching { LocalDate.parse(it) }.getOrNull()
            }
        )
        store.lastLessonReminderKey = key
        store.clearScheduledLessonReminder()
        if (store.addNotification(entry)) NotificationPublisher.post(applicationContext, entry)
        return Result.success()
    }
}

internal fun reminderDelayMillis(
    start: LocalDateTime,
    leadMinutes: Int,
    now: LocalDateTime,
    zone: ZoneId = ZoneId.systemDefault()
): Long = Duration.between(
    now.atZone(zone).toInstant(),
    start.minusMinutes(leadMinutes.toLong()).atZone(zone).toInstant()
).toMillis().coerceAtLeast(0L)

class AppUpdateNotificationWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val allowSystemNotification = !inputData.getBoolean(NotificationScheduler.INTERNAL_ONLY, false)
        val store = LocalStore(applicationContext)
        val preferences = store.notificationPreferences
        if (!preferences.enabled || !preferences.appUpdates) return Result.success()
        val release = try { AppUpdater.latestRelease() } catch (_: Exception) { return Result.retry() }
        if (!AppUpdater.isNewer(release.version, BuildConfig.VERSION_NAME) ||
            store.lastUpdateNotificationVersion == release.version) return Result.success()
        val entry = AppNotificationEntry(
            id = "update-${release.version}", type = AppNotificationType.APP_UPDATE,
            title = "Aggiornamento disponibile", message = "È disponibile Orari UNIMI ${release.version}.",
            timestampMillis = System.currentTimeMillis()
        )
        store.lastUpdateNotificationVersion = release.version
        if (store.addNotification(entry)) {
            NotificationPublisher.post(applicationContext, entry, allowSystemNotification)
        }
        return Result.success()
    }
}

object NotificationPublisher {
    private const val TAG = "orari-unimi"
    private const val IMPORTANT_CHANNEL = "schedule_changes"
    private const val REMINDER_CHANNEL = "lesson_reminders"
    private const val UPDATE_CHANNEL = "app_updates"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(listOf(
            NotificationChannel(IMPORTANT_CHANNEL, "Variazioni degli orari", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Annullamenti e variazioni importanti delle lezioni" },
            silentChannel(REMINDER_CHANNEL, "Promemoria lezioni", "Promemoria silenziosi prima delle lezioni"),
            silentChannel(UPDATE_CHANNEL, "Aggiornamenti dell'app", "Avvisi silenziosi per nuove versioni")
        ))
    }

    fun post(context: Context, entry: AppNotificationEntry, allowSystemNotification: Boolean = true) {
        if (!NotificationDeliveryPolicy.shouldPostSystem(
                allowSystemNotification, AppVisibility.isForeground) || !canPost(context)) return
        createChannels(context)
        val notification = Notification.Builder(context, channel(entry.type))
            .setSmallIcon(R.drawable.ic_notification_calendar)
            .setContentTitle(entry.title)
            .setContentText(entry.message)
            .setStyle(Notification.BigTextStyle().bigText(entry.message))
            .setContentIntent(openCenterIntent(context))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_EVENT)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(TAG, entry.id.hashCode(), notification)
    }

    fun postSummary(
        context: Context,
        count: Int,
        message: String,
        allowSystemNotification: Boolean = true
    ) {
        if (!NotificationDeliveryPolicy.shouldPostSystem(
                allowSystemNotification, AppVisibility.isForeground) || !canPost(context)) return
        createChannels(context)
        val notification = Notification.Builder(context, IMPORTANT_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_calendar)
            .setContentTitle("Orari aggiornati")
            .setContentText(message)
            .setNumber(count)
            .setContentIntent(openCenterIntent(context))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_EVENT)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(TAG, 15_000, notification)
    }

    fun cancelAll(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancelAll()
    }

    private fun channel(type: AppNotificationType) = when (type) {
        AppNotificationType.IMPORTANT_CHANGE -> IMPORTANT_CHANNEL
        AppNotificationType.LESSON_REMINDER -> REMINDER_CHANNEL
        AppNotificationType.APP_UPDATE -> UPDATE_CHANNEL
    }

    private fun silentChannel(id: String, name: String, descriptionText: String) =
        NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW).apply {
            description = descriptionText
            setSound(null, null)
            enableVibration(false)
        }

    private fun canPost(context: Context) = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openCenterIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).putExtra(MainActivity.OPEN_NOTIFICATIONS, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

internal object NotificationDeliveryPolicy {
    fun shouldPostSystem(allowSystemNotification: Boolean, appInForeground: Boolean): Boolean =
        allowSystemNotification && !appInForeground
}

object AppVisibility {
    @Volatile
    var isForeground: Boolean = false
        private set

    fun enterForeground() {
        isForeground = true
    }

    fun enterBackground() {
        isForeground = false
    }
}
