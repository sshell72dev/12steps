package ru.na.step4.obidy.data.life

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ru.na.step4.obidy.MainActivity
import ru.na.step4.obidy.Step4App

/**
 * Звонок по событию календаря: точный будильник, сигнал и уведомление телефона.
 * Событие звонит один раз — в своё время, если в нём включён «Звонок».
 */
object LifeCallAlarms {
    const val EXTRA_ID = "life_call_id"
    const val ACTION_STOP = "ru.na.step4.obidy.life.CALL_STOP"

    private const val PREFS = "life_call_alarms"
    private const val KEY_IDS = "ids"
    private const val REQUEST_BASE = 0x4C2100
    private const val NOTIFY_BASE = 0x4C3100
    private const val CHANNEL = "life_calls"
    private const val RING_TIMEOUT_MS = 120_000L

    private var ringtone: Ringtone? = null
    private var wakeLock: PowerManager.WakeLock? = null

    /** Приводит будильники в соответствие событиям: ставит нужные, снимает лишние. */
    fun sync(context: Context, items: List<LifeItem>) {
        val app = context.applicationContext
        val wanted = items
            .filter { it.kind == LifeKind.EVENT && it.callOn && it.timeSet }
            .filter { it.status != LifeStatus.DONE && (it.dueAt ?: 0L) > System.currentTimeMillis() }
            .associateBy { it.id }
        loadIds(app).forEach { id ->
            if (!wanted.containsKey(id)) cancel(app, id)
        }
        wanted.values.forEach { schedule(app, it) }
    }

    /** Пересчёт будильников по сохранённому файлу — например, после перезагрузки телефона. */
    fun resyncFromFile(context: Context) {
        runCatching {
            val app = context.applicationContext
            val store = (app as? Step4App)?.lifeBoard ?: LifeBoardStore(app)
            sync(app, store.items.value)
        }
    }

    fun schedule(context: Context, item: LifeItem) {
        val due = item.dueAt ?: return
        if (!item.callOn || !item.timeSet || due <= System.currentTimeMillis()) {
            cancel(context, item.id)
            return
        }
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = pendingIntent(context, item.id, item.title, item.body)
        val show = PendingIntent.getActivity(
            context,
            requestCode(item.id) + 3,
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val placed = runCatching {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(due, show), pending)
        }.isSuccess
        if (!placed) {
            // Без разрешения на точные будильники ставим обычный — сработает примерно в это время.
            runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pending) }
        }
        remember(context, item.id)
    }

    fun cancel(context: Context, id: String) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (manager != null) {
            runCatching { manager.cancel(pendingIntent(context, id, "", "")) }
        }
        forget(context, id)
    }

    /** Показывает уведомление события и включает сигнал будильника. */
    fun notifyAndRing(context: Context, id: String, title: String, body: String) {
        forget(context, id)
        playRingtone(context, id)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, LifeBoardRu.callChannel, NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    description = LifeBoardRu.callChannelHint
                    enableVibration(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setSound(null, null)
                }
        )
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(
            context,
            requestCode(id),
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            ),
            flags
        )
        val stop = PendingIntent.getBroadcast(
            context,
            requestCode(id) + 1,
            Intent(context, LifeCallReceiver::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_ID, id)
            },
            flags
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body.ifBlank { LifeBoardRu.callBody })
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.ifBlank { LifeBoardRu.callBody }))
            .setContentIntent(open)
            .addAction(0, LifeBoardRu.stop, stop)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notifyId(id), notification) }
    }

    /** Тихий режим уведомления по событию — заготовка на будущее для повторных напоминаний. */
    fun notifyId(id: String): Int = NOTIFY_BASE + (id.hashCode() and 0x0FFF)

    fun stopRinging(context: Context) {
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    internal fun forget(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_IDS, loadIds(context) - id).apply()
    }

    private fun remember(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_IDS, loadIds(context) + id).apply()
    }

    private fun loadIds(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_IDS, emptySet())
            ?.toSet()
            .orEmpty()

    private fun requestCode(id: String): Int = REQUEST_BASE + (id.hashCode() and 0x0FFF)

    private fun pendingIntent(context: Context, id: String, title: String, body: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(id),
            Intent(context, LifeCallReceiver::class.java).apply {
                putExtra(EXTRA_ID, id)
                putExtra("life_call_title", title)
                putExtra("life_call_body", body)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun playRingtone(context: Context, id: String) {
        stopRingingOnly()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        runCatching {
            val sound = uri?.let { RingtoneManager.getRingtone(context, it) }
            sound?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) sound?.isLooping = true
            sound?.play()
            ringtone = sound
        }
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = runCatching {
            power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "steps12:lifeCall")?.apply {
                setReferenceCounted(false)
                acquire(RING_TIMEOUT_MS)
            }
        }.getOrNull()
        // Сигнал не должен звонить бесконечно: через две минуты он умолкает сам.
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val stop = PendingIntent.getBroadcast(
            context,
            requestCode(id) + 2,
            Intent(context, LifeCallReceiver::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_ID, id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            alarm?.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + RING_TIMEOUT_MS, stop)
        }
    }

    private fun stopRingingOnly() {
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }
}

/** Будильник события: проверяет, что событие всё ещё ждёт звонка, и звонит. */
class LifeCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val id = intent.getStringExtra(LifeCallAlarms.EXTRA_ID).orEmpty()
        if (intent.action == LifeCallAlarms.ACTION_STOP) {
            LifeCallAlarms.stopRinging(app)
            runCatching {
                NotificationManagerCompat.from(app).cancel(LifeCallAlarms.notifyId(id))
            }
            return
        }
        val item = (app as? Step4App)?.lifeBoard?.byId(id)
        // Событие могли перенести, отключить звонок или отметить сделанным.
        if (item == null || !item.callOn || !item.timeSet || item.status == LifeStatus.DONE) {
            LifeCallAlarms.forget(app, id)
            return
        }
        LifeCallAlarms.notifyAndRing(app, id, item.title, item.body)
    }
}

/** После перезагрузки телефона будильники ставятся заново по сохранённым событиям. */
class LifeCallBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            LifeCallAlarms.resyncFromFile(context)
        }
    }
}
