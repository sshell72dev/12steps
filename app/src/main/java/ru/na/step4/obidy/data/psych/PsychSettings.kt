package ru.na.step4.obidy.data.psych

import android.content.Context
import android.content.SharedPreferences
import java.util.TimeZone
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import ru.na.step4.obidy.data.profile.ProfileQuestionnaire
import ru.na.step4.obidy.data.profile.ProfileStore

class PsychSettings(
    context: Context,
    val profile: ProfileStore = ProfileStore(context)
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) { prefs.edit().putBoolean(KEY_ONBOARDING, value).apply() }

    var name: String
        get() = profile.name
        set(value) { profile.name = value }

    var birthYear: String
        get() = profile.birthYear
        set(value) { profile.birthYear = value }

    var location: String
        get() = profile.location
        set(value) { profile.location = value }

    var recoveryProgram: String
        get() = profile.program
        set(value) { profile.program = value }

    var aboutMe: String
        get() = profile.aboutMe
        set(value) { profile.aboutMe = value }

    var utcOffsetMinutes: Int
        get() {
            if (!prefs.contains(KEY_OFFSET)) {
                val def = TimeZone.getDefault().rawOffset / 60_000
                prefs.edit().putInt(KEY_OFFSET, def).apply()
                return def
            }
            return prefs.getInt(KEY_OFFSET, 0)
        }
        set(value) { prefs.edit().putInt(KEY_OFFSET, value).apply() }

    var languageCode: String
        get() = profile.languageCode
        set(value) {
            profile.languageCode = value
            prefs.edit().putString(KEY_LANG, profile.languageCode).apply()
        }

    var proExpiryMillis: Long
        get() = prefs.getLong(KEY_PRO, 0L)
        set(value) { prefs.edit().putLong(KEY_PRO, value).apply() }

    val isPro: Boolean
        get() = proExpiryMillis > System.currentTimeMillis()

    var myPersonality: String
        get() = profile.personality
        set(value) { profile.personality = value }

    var personalityCollectEnabled: Boolean
        get() = profile.personalityCollectEnabled && isPro
        set(value) {
            profile.personalityCollectEnabled = value && isPro
        }

    var aiResponseVariant: String
        get() {
            val v = prefs.getString(KEY_VARIANT, "compact") ?: "compact"
            return if (v == "expanded" && !isPro) "compact" else v
        }
        set(value) { prefs.edit().putString(KEY_VARIANT, value).apply() }

    var aiResponseStyle: String
        get() {
            val v = prefs.getString(KEY_STYLE, "neutral") ?: "neutral"
            return if (v == "critical" && !isPro) "neutral" else v
        }
        set(value) { prefs.edit().putString(KEY_STYLE, value).apply() }

    var workQuestionDifficulty: String
        get() {
            val v = prefs.getString(KEY_DIFF, "simple") ?: "simple"
            return if (v == "hard" && !isPro) "simple" else v
        }
        set(value) { prefs.edit().putString(KEY_DIFF, value).apply() }

    var workQuestionLength: String
        get() {
            val v = prefs.getString(KEY_QLEN, "short") ?: "short"
            return if (v == "long" && !isPro) "short" else v
        }
        set(value) { prefs.edit().putString(KEY_QLEN, value).apply() }

    var topicsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TOPICS, true)
        set(value) { prefs.edit().putBoolean(KEY_TOPICS, value).apply() }

    var reminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_REM_ON, true)
        set(value) { prefs.edit().putBoolean(KEY_REM_ON, value).apply() }

    var reminderIntervalHours: Int
        get() {
            val stored = prefs.getInt(KEY_REM_HOURS, 0)
            if (stored > 0) return stored
            return if (isPro) 6 else 12
        }
        set(value) { prefs.edit().putInt(KEY_REM_HOURS, value.coerceIn(1, 72)).apply() }

    var quietStartHour: Int
        get() = prefs.getInt(KEY_QUIET_START, 23)
        set(value) { prefs.edit().putInt(KEY_QUIET_START, value).apply() }

    var quietEndHour: Int
        get() = prefs.getInt(KEY_QUIET_END, 8)
        set(value) { prefs.edit().putInt(KEY_QUIET_END, value).apply() }

    var nextReminderAt: Long
        get() = prefs.getLong(KEY_NEXT_REM, 0L)
        set(value) { prefs.edit().putLong(KEY_NEXT_REM, value).apply() }

    var reminderOutreachPending: Boolean
        get() = prefs.getBoolean(KEY_REM_PENDING, false)
        set(value) { prefs.edit().putBoolean(KEY_REM_PENDING, value).apply() }

    var lastReminderText: String
        get() = prefs.getString(KEY_LAST_REM, "").orEmpty()
        set(value) { prefs.edit().putString(KEY_LAST_REM, value).apply() }

    fun inboxMessages(): List<PsychInboxMessage> {
        val raw = prefs.getString(KEY_INBOX, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val text = obj.optString("t").trim()
                if (text.isBlank()) null
                else PsychInboxMessage(text, obj.optLong("at"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun appendInbox(text: String) {
        val body = text.trim()
        if (body.isBlank()) return
        val now = System.currentTimeMillis()
        val items = inboxMessages().toMutableList()
        val last = items.lastOrNull()
        if (last != null && last.text == body && now - last.createdAt < 8_000L) return
        items.add(PsychInboxMessage(body, now))
        while (items.size > 40) items.removeAt(0)
        val arr = JSONArray()
        items.forEach { msg ->
            arr.put(JSONObject().put("t", msg.text).put("at", msg.createdAt))
        }
        prefs.edit().putString(KEY_INBOX, arr.toString()).commit()
    }

    fun watchInbox(onChange: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_INBOX) onChange()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun unwatchInbox(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    var voiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE, true)
        set(value) { prefs.edit().putBoolean(KEY_VOICE, value).apply() }

    var dialogueExtraLimit: Int
        get() {
            val user = prefs.getInt(KEY_DIALOGUE_EXTRA, 0)
            if (user > 0) return user.coerceIn(1, LIMIT_MAX)
            return prefs.getInt(KEY_ADMIN_DIALOGUE_EXTRA, DEFAULT_DIALOGUE_EXTRA).coerceIn(1, LIMIT_MAX)
        }
        set(value) { prefs.edit().putInt(KEY_DIALOGUE_EXTRA, value.coerceIn(1, LIMIT_MAX)).apply() }

    var workQuestionLimit: Int
        get() {
            val user = prefs.getInt(KEY_WORK_Q, 0)
            if (user > 0) return user.coerceIn(1, LIMIT_MAX)
            return prefs.getInt(KEY_ADMIN_WORK_Q, DEFAULT_WORK_Q).coerceIn(1, LIMIT_MAX)
        }
        set(value) { prefs.edit().putInt(KEY_WORK_Q, value.coerceIn(1, LIMIT_MAX)).apply() }

    fun applyAdminQuestionLimits(dialogueExtra: Int, workQuestions: Int) {
        if (dialogueExtra in 1..LIMIT_MAX) {
            prefs.edit().putInt(KEY_ADMIN_DIALOGUE_EXTRA, dialogueExtra).apply()
        }
        if (workQuestions in 1..LIMIT_MAX) {
            prefs.edit().putInt(KEY_ADMIN_WORK_Q, workQuestions).apply()
        }
    }

    var liveIdleMinutes: Int
        get() = prefs.getInt(KEY_IDLE, 30).coerceIn(5, 180)
        set(value) { prefs.edit().putInt(KEY_IDLE, value).apply() }

    var lastQuestionAt: Long
        get() = prefs.getLong(KEY_LAST_Q, 0L)
        set(value) { prefs.edit().putLong(KEY_LAST_Q, value).apply() }

    var activeSessionUid: String
        get() = prefs.getString(KEY_ACTIVE_UID, "") ?: ""
        set(value) { prefs.edit().putString(KEY_ACTIVE_UID, value).apply() }

    var pendingReadMoreKey: String
        get() = prefs.getString(KEY_READ_MORE, "") ?: ""
        set(value) { prefs.edit().putString(KEY_READ_MORE, value).apply() }

    fun grantProDays(days: Int) {
        val now = System.currentTimeMillis()
        val base = proExpiryMillis.coerceAtLeast(now)
        proExpiryMillis = base + days * 24L * 60L * 60L * 1000L
    }

    fun expireProIfNeeded() {
        // Collect flag stays; getter still requires Premium for psych prompts.
    }

    var goalsProvider: () -> String = { "" }

    fun profileMap(): Map<String, Any?> {
        val snap = profile.current
        return mapOf(
            "name" to snap.name,
            "birth_year" to snap.birthYear,
            "location" to snap.location,
            "recovery_program" to snap.program.ifBlank {
                snap.answers[ProfileQuestionnaire.ID_PROGRAM].orEmpty()
            },
            "questionnaire_text" to (ProfileQuestionnaire.formatAnswers(snap) ?: ""),
            "about_me" to snap.aboutMe,
            "gender" to snap.answers[ProfileQuestionnaire.ID_GENDER].orEmpty(),
            "addiction_type" to snap.answers[ProfileQuestionnaire.ID_ADDICTION].orEmpty(),
            "last_use_date" to snap.answers[ProfileQuestionnaire.ID_LAST_USE].orEmpty(),
            "main_reason" to snap.answers[ProfileQuestionnaire.ID_REASON].orEmpty(),
            "motivation_level" to snap.answers[ProfileQuestionnaire.ID_MOTIVATION].orEmpty(),
            "problems" to (ru.na.step4.obidy.data.profile.ProfileProblems.labels(snap.problems) ?: ""),
            "utc_offset_minutes" to utcOffsetMinutes,
            "language_code" to languageCode,
            "pro_active" to isPro,
            "pro_expiry" to if (isPro) java.time.Instant.ofEpochMilli(proExpiryMillis).toString() else "",
            "goals" to goalsProvider(),
            "my_personality" to snap.personality,
            "my_personality_collect_enabled" to personalityCollectEnabled,
            "my_personality_use_enabled" to snap.personalityEnabled,
            "ai_response_variant" to aiResponseVariant,
            "ai_response_style" to aiResponseStyle,
            "work_question_difficulty" to workQuestionDifficulty,
            "work_question_length" to workQuestionLength,
            "dialogue_extra_limit" to dialogueExtraLimit,
            "work_question_limit" to workQuestionLimit
        )
    }

    fun hasEmptyProfileField(): Boolean = profile.hasEmptyRequired()

    fun dailyLimit(): Int = if (isPro) DAILY_LIMIT_PRO else DAILY_LIMIT_FREE

    fun newSessionUid(): String = UUID.randomUUID().toString()

    companion object {
        private const val PREFS = "psychologist"
        private const val KEY_ONBOARDING = "onboarding_done"
        private const val KEY_OFFSET = "utc_offset_minutes"
        private const val KEY_LANG = "language_code"
        private const val KEY_PRO = "pro_expiry"
        private const val KEY_VARIANT = "ai_variant"
        private const val KEY_STYLE = "ai_style"
        private const val KEY_DIFF = "work_diff"
        private const val KEY_QLEN = "work_len"
        private const val KEY_TOPICS = "topics_enabled"
        private const val KEY_REM_ON = "reminder_on"
        private const val KEY_REM_HOURS = "reminder_hours"
        private const val KEY_QUIET_START = "quiet_start"
        private const val KEY_QUIET_END = "quiet_end"
        private const val KEY_NEXT_REM = "next_reminder"
        private const val KEY_REM_PENDING = "reminder_pending"
        private const val KEY_LAST_REM = "last_reminder_text"
        private const val KEY_INBOX = "psych_inbox"
        private const val KEY_VOICE = "voice_enabled"
        private const val KEY_DIALOGUE_EXTRA = "dialogue_extra_q"
        private const val KEY_WORK_Q = "work_q_limit"
        private const val KEY_ADMIN_DIALOGUE_EXTRA = "admin_dialogue_extra"
        private const val KEY_ADMIN_WORK_Q = "admin_work_q"
        private const val KEY_IDLE = "idle_minutes"
        private const val KEY_LAST_Q = "last_question_at"
        private const val KEY_ACTIVE_UID = "active_session_uid"
        private const val KEY_READ_MORE = "pending_read_more"
        const val DEFAULT_DIALOGUE_EXTRA = 5
        const val DEFAULT_WORK_Q = 5
        const val LIMIT_MAX = 30
        const val DAILY_LIMIT_FREE = 20
        const val DAILY_LIMIT_PRO = 40
        const val LOCK_MS = 180_000L
        const val TEASER_TTL_MS = 24L * 60L * 60L * 1000L
        val PROGRAMS get() = ProfileQuestionnaire.programs
    }
}
