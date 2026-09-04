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

object AppAlerts {
    const val CHAT_ID = "local-alerts"
    const val EXTRA_OPEN = "open_alerts"
    const val KIND = "alerts"

    private const val CHANNEL = "app_alerts_high"
    private const val DEFAULT_NOTIFY_ID = 13013

    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun post(context: Context, title: String, body: String, notifyId: Int = DEFAULT_NOTIFY_ID) {
        val text = body.trim()
        if (text.isBlank()) return
        val app = context.applicationContext as? Step4App
        runCatching {
            runBlocking { app?.messengerRepository?.postAlertNow(text) }
        }
        notifyPhone(context.applicationContext, title.ifBlank { MessengerRu.alertsTitle }, text, notifyId)
    }

    private fun notifyPhone(context: Context, title: String, body: String, notifyId: Int) {
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
