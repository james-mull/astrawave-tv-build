package com.astrawave.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.astrawave.app.RebuildMainActivity
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object SportsReminderScheduler {
    private const val PREFIX = "astrawave-sports-reminder:"
    private const val LEAD_TIME_MS = 15L * 60L * 1000L
    private val sportsFormats = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
    )

    fun schedule(context: Context, profileId: String, reminder: SportsPreferenceStore.Reminder) {
        val workName = workName(profileId, reminder.id)
        if (!reminder.enabled) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(workName)
            return
        }
        val startMs = parseStartMs(reminder.startTime) ?: return
        val triggerAt = startMs - LEAD_TIME_MS
        if (startMs <= System.currentTimeMillis()) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(workName)
            return
        }
        val delay = (triggerAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<SportsReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    SportsReminderWorker.KEY_PROFILE_ID to profileId,
                    SportsReminderWorker.KEY_REMINDER_ID to reminder.id,
                    SportsReminderWorker.KEY_EVENT_ID to reminder.eventId,
                    SportsReminderWorker.KEY_TITLE to reminder.title,
                    SportsReminderWorker.KEY_START_TIME to reminder.startTime,
                ),
            )
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request)
    }

    fun rescheduleAll(context: Context, profileId: String, reminders: List<SportsPreferenceStore.Reminder>) {
        reminders.forEach { schedule(context, profileId, it) }
    }

    fun cancel(context: Context, profileId: String, reminderId: String) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(workName(profileId, reminderId))
    }

    private fun workName(profileId: String, reminderId: String): String =
        "$PREFIX${profileId.replace(':', '_')}:${reminderId.replace(':', '_')}"

    internal fun parseStartMs(value: String): Long? {
        runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()?.let { return it }
        runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
        runCatching { LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
        sportsFormats.forEach { format ->
            runCatching { LocalDateTime.parse(value, format).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
                .getOrNull()?.let { return it }
        }
        return null
    }
}

class SportsReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val profileId = inputData.getString(KEY_PROFILE_ID).orEmpty()
        val reminderId = inputData.getString(KEY_REMINDER_ID).orEmpty()
        val title = inputData.getString(KEY_TITLE).orEmpty()
        val startTime = inputData.getString(KEY_START_TIME).orEmpty()
        if (profileId.isBlank() || reminderId.isBlank() || title.isBlank()) return Result.failure()

        val saved = SportsPreferenceStore(applicationContext)
            .reminders(profileId)
            .firstOrNull { it.id == reminderId && it.enabled }
            ?: return Result.success()

        if (Build.VERSION.SDK_INT >= 33 && applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Sports reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Game Day alerts for sports events you asked AstraWave to remind you about."
                },
            )
        }

        val openIntent = Intent(applicationContext, RebuildMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("astrawave_open_destination", "sports")
            putExtra("astrawave_event_id", saved.eventId)
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            reminderId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val startLabel = SportsReminderScheduler.parseStartMs(startTime)?.let { millis ->
            java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(millis))
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            android.app.Notification.Builder(applicationContext, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(applicationContext)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Game starting soon")
            .setContentText(if (startLabel != null) "$title • $startLabel" else title)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setCategory(android.app.Notification.CATEGORY_EVENT)
            .build()

        runCatching { manager.notify(reminderId.hashCode(), notification) }
        return Result.success()
    }

    companion object {
        const val KEY_PROFILE_ID = "profileId"
        const val KEY_REMINDER_ID = "reminderId"
        const val KEY_EVENT_ID = "eventId"
        const val KEY_TITLE = "title"
        const val KEY_START_TIME = "startTime"
        private const val CHANNEL_ID = "astrawave_sports_reminders"
    }
}
