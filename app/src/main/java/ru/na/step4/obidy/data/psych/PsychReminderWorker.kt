package ru.na.step4.obidy.data.psych

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import ru.na.step4.obidy.MainActivity

class PsychReminderWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result {
        val settings = PsychSettings(applicationContext)
        if (!settings.reminderEnabled) return Result.success()
        val now = System.currentTimeMillis()
        if (PsychLogic.inQuietHours(
                now,
                settings.utcOffsetMinutes,
                settings.quietStartHour,
                settings.quietEndHour
            )
        ) {
            return Result.success()
        }
        val due = settings.nextReminderAt
        if (due == 0L) {
            settings.nextReminderAt = now + settings.reminderIntervalHours * 3_600_000L
            return Result.success()
        }
        if (now < due) return Result.success()
        settings.nextReminderAt = now + settings.reminderIntervalHours * 3_600_000L
        settings.reminderOutreachPending = true
        val text = settings.lastReminderText.ifBlank { PsychRu.reminderFallback }
        notify(applicationContext, text)
        return Result.success()
    }

    companion object {
        const val EXTRA_OPEN_PSYCH = "open_psych"
        private const val UNIQUE = "psych_reminder"
        private const val CHANNEL = "psych_reminders_high"
        private const val NOTIFY_ID = 12012

        fun schedule(context: Context, replace: Boolean = false) {
            val settings = PsychSettings(context)
            if (!settings.reminderEnabled) {
                cancel(context)
                return
            }
            if (settings.nextReminderAt == 0L) {
                settings.nextReminderAt =
                    System.currentTimeMillis() + settings.reminderIntervalHours * 3_600_000L
            }
            val request = PeriodicWorkRequestBuilder<PsychReminderWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                if (replace) ExistingPeriodicWorkPolicy.REPLACE else ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
        }

        fun canPost(context: Context): Boolean =
            NotificationManagerCompat.from(context).areNotificationsEnabled()

        fun notify(context: Context, text: String) {
            val body = text.ifBlank { PsychRu.reminderFallback }
            PsychSettings(context).appendInbox(body)
            if (!canPost(context)) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL,
                PsychRu.reminders,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = PsychRu.reminderHow
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
            val open = PendingIntent.getActivity(
                context,
                NOTIFY_ID,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(EXTRA_OPEN_PSYCH, true)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(PsychRu.psychologistName)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()
            runCatching { manager.notify(NOTIFY_ID, notification) }
        }
    }
}
