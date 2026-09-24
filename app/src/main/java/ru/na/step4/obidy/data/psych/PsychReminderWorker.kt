package ru.na.step4.obidy.data.psych

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters

// Обращения психолога по таймеру отключены: они не попадают ни в чат «Оповещение»,
// ни в шторку и на заставку.
class PsychReminderWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result = Result.success()

    companion object {
        const val EXTRA_OPEN_PSYCH = "open_psych"
        private const val UNIQUE = "psych_reminder"

        fun schedule(context: Context, replace: Boolean = false) {
            cancel(context)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
        }

        fun canPost(context: Context): Boolean =
            NotificationManagerCompat.from(context).areNotificationsEnabled()

        fun notify(context: Context, text: String) {
        }
    }
}
