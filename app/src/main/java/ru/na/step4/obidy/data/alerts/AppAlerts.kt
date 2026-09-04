package ru.na.step4.obidy.data.alerts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.runBlocking
import ru.na.step4.obidy.MainActivity
import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.Step4App
import ru.na.step4.obidy.data.messenger.MessengerRu
import ru.na.step4.obidy.data.psych.PsychRu

object AppAlerts {
    const val CHAT_ID = "local-alerts"
    const val EXTRA_OPEN = "open_alerts"
    const val EXTRA_TARGET = "alert_target"
    const val KIND = "alerts"
    const val TARGET_ANALYSIS = "analysis"
    const val TARGET_PSYCH = "psych"
    const val TARGET_JOURNAL = "journal"

    private const val CHANNEL = "app_alerts_high"
    private const val DEFAULT_NOTIFY_ID = 13013
    private const val SENDER_PREFIX = "alert:"

    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun isKnownTarget(target: String): Boolean =
        target == TARGET_ANALYSIS || target == TARGET_PSYCH || target == TARGET_JOURNAL

    fun senderIdFor(target: String): String =
        if (isKnownTarget(target)) "$SENDER_PREFIX$target" else "system"

    fun senderNameFor(target: String): String = when (target) {
        TARGET_ANALYSIS -> Ru.sectionAnalysis
        TARGET_PSYCH -> PsychRu.psychologistName
        TARGET_JOURNAL -> Ru.sectionSteps
        else -> MessengerRu.alertsTitle
    }

    fun resolveTarget(senderId: String, body: String, senderName: String = ""): String {
        if (senderId.startsWith(SENDER_PREFIX)) {
            val key = senderId.removePrefix(SENDER_PREFIX)
            if (isKnownTarget(key)) return key
        }
        when (senderName) {
            Ru.sectionAnalysis -> return TARGET_ANALYSIS
            PsychRu.psychologistName, Ru.sectionPsych -> return TARGET_PSYCH
            Ru.sectionSteps -> return TARGET_JOURNAL
        }
        return inferTarget(body)
    }

    fun post(
        context: Context,
        title: String,
        body: String,
        notifyId: Int = DEFAULT_NOTIFY_ID,
        target: String = ""
    ) {
        val text = body.trim()
        if (text.isBlank()) return
        val app = context.applicationContext as? Step4App
        runCatching {
            runBlocking { app?.messengerRepository?.postAlertNow(text, target) }
        }
        notifyPhone(
            context.applicationContext,
            title.ifBlank { senderNameFor(target) },
            text,
            notifyId,
            target
        )
    }

    private fun inferTarget(body: String): String {
        data class Hit(val index: Int, val target: String)
        val hits = mutableListOf<Hit>()
        fun add(needles: List<String>, target: String) {
            val index = needles
                .map { body.indexOf(it, ignoreCase = true) }
                .filter { it >= 0 }
                .minOrNull() ?: return
            hits += Hit(index, target)
        }
        add(
            listOf(Ru.streakPartAnalysis, Ru.sectionAnalysis, "самоанализ"),
            TARGET_ANALYSIS
        )
        add(
            listOf(Ru.streakPartSteps, Ru.sectionSteps, "работы по шагам"),
            TARGET_JOURNAL
        )
        add(
            listOf(
                Ru.streakPartPsych,
                PsychRu.psychologistName,
                Ru.sectionPsych,
                "электронного психолога"
            ),
            TARGET_PSYCH
        )
        return hits.minByOrNull { it.index }?.target.orEmpty()
    }

    private fun notifyPhone(
        context: Context,
        title: String,
        body: String,
        notifyId: Int,
        target: String
    ) {
        if (!canPost(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL,
            MessengerRu.alertsTitle,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = MessengerRu.alertsHow
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
        val open = PendingIntent.getActivity(
            context,
            notifyId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN, true)
                if (isKnownTarget(target)) putExtra(EXTRA_TARGET, target)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        runCatching { manager.notify(notifyId, notification) }
    }
}

object AlertCopy {
    fun streakWarning(parts: List<String>): String {
        if (parts.isEmpty()) return ""
        val joined = parts.joinToString(", ")
        return if (parts.size == 1) {
            Ru.streakWarnOne.format(joined)
        } else {
            Ru.streakWarnMany.format(joined)
        }
    }
}
