package ru.na.step4.obidy.data.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Суточная выгрузка копии на сервер.
 *
 * Работает только когда почта привязана. Новая выгрузка заменяет предыдущую:
 * сервер хранит один актуальный архив на аккаунт.
 */
class BackupAutoWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!BackupServer.isLinked(context)) return Result.success()

        val archive = File(File(context.cacheDir, "backup_auto"), "auto.zip")
        val exported = BackupManager.exportToFile(context, archive, null)
        if (!exported.ok) {
            runCatching { archive.delete() }
            return Result.retry()
        }
        if (archive.length() > BackupServer.MAX_BYTES) {
            runCatching { archive.delete() }
            return Result.failure()
        }
        val answer = BackupServer.upload(context, archive)
        runCatching { archive.delete() }
        return if (answer.ok) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "backup-daily"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackupAutoWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
