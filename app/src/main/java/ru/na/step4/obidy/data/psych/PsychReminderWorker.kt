package ru.na.step4.obidy.data.psych

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import ru.na.step4.obidy.data.alerts.AppAlerts

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
            AppAlerts.post(context, PsychRu.psychologistName, body, NOTIFY_ID)
        }
    }
}
