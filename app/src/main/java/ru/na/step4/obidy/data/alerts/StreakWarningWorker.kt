package ru.na.step4.obidy.data.alerts

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.Step4App

class StreakWarningWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result {
        val app = applicationContext as? Step4App ?: return Result.success()
        app.analysisStreak.refresh()
        app.journalStreak.refresh()
        app.psychStreak.refresh()
        val due = mutableListOf<Pair<String, () -> Unit>>()
        if (app.analysisStreak.shouldWarnNow()) {
            due += Ru.streakPartAnalysis to { app.analysisStreak.markWarnedNow() }
        }
        if (app.journalStreak.shouldWarnNow()) {
            due += Ru.streakPartSteps to { app.journalStreak.markWarnedNow() }
        }
        if (app.psychStreak.shouldWarnNow()) {
            due += Ru.streakPartPsych to { app.psychStreak.markWarnedNow() }
        }
        if (due.isEmpty()) return Result.success()
        val body = AlertCopy.streakWarning(due.map { it.first })
        AppAlerts.post(applicationContext, Ru.analysisStreak, body, NOTIFY_ID)
        due.forEach { it.second() }
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "streak_warning"
        private const val NOTIFY_ID = 13014

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<StreakWarningWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
