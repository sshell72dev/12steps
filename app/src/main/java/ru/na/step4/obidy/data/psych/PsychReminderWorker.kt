package ru.na.step4.obidy.data.psych

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
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
        if (PsychLogic.inQuietHours(now, settings.utcOffsetMinutes, settings.quietStartHour, settings.quietEndHour)) {
            return Result.success()
        }
        val due = settings.nextReminderAt
        if (due == 0L || now < due) return Result.success()
        settings.nextReminderAt = now + settings.reminderIntervalHours * 3_600_000L
        settings.reminderOutreachPending = true
        notify(applicationContext, PsychRu.reminderFallback)
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "psych_reminder"
        private const val CHANNEL = "psych_reminders"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PsychReminderWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun notify(context: Context, text: String) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL,
                PsychRu.reminders,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(PsychRu.record)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            manager.notify(12012, notification)
        }
    }
}
