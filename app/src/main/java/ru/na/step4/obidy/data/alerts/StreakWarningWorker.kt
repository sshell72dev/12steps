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
        due.forEach { (part, mark) ->
            val target = targetOf(part)
            val body = AlertCopy.streakWarning(listOf(part))
            AppAlerts.post(
                applicationContext,
                Ru.analysisStreak,
                body,
                notifyId = notifyIdOf(target),
                target = target
            )
            mark()
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "streak_warning"
        private const val NOTIFY_ANALYSIS = 13014
        private const val NOTIFY_JOURNAL = 13015
        private const val NOTIFY_PSYCH = 13016

        private fun targetOf(part: String): String = when (part) {
            Ru.streakPartAnalysis -> AppAlerts.TARGET_ANALYSIS
            Ru.streakPartSteps -> AppAlerts.TARGET_JOURNAL
            else -> AppAlerts.TARGET_PSYCH
        }

        private fun notifyIdOf(target: String): Int = when (target) {
            AppAlerts.TARGET_ANALYSIS -> NOTIFY_ANALYSIS
            AppAlerts.TARGET_JOURNAL -> NOTIFY_JOURNAL
            else -> NOTIFY_PSYCH
        }

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
