package ru.na.step4.obidy.data.profile

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import ru.na.step4.obidy.data.i18n.LocaleHelper

class ProfileStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _snapshot = MutableStateFlow(ProfileSnapshot())
    val snapshot: StateFlow<ProfileSnapshot> = _snapshot.asStateFlow()

    init {
        migrateIfNeeded()
        ensureLanguageDefault()
        _snapshot.value = read()
    }

    val current: ProfileSnapshot get() = _snapshot.value

    var name: String
        get() = current.name
        set(value) = update { copy(name = value.trim().take(40)) }

    var birthYear: String
        get() = current.birthYear
        set(value) = update { copy(birthYear = value.trim()) }

    var location: String
        get() = current.location
        set(value) = update { copy(location = value.trim()) }

    var aboutMe: String
        get() = current.aboutMe
        set(value) = update { copy(aboutMe = value.trim()) }

    var program: String
        get() = current.program
        set(value) = update { copy(program = value.trim()) }

    var problems: Set<String>
        get() = current.problems
        set(value) = update { copy(problems = value) }

    var personality: String
        get() = current.personality
        set(value) = update { copy(personality = value.trim()) }

    var personalityEnabled: Boolean
        get() = current.personalityEnabled
        set(value) = update { copy(personalityEnabled = value) }

    var personalityCollectEnabled: Boolean
        get() = current.personalityCollectEnabled
        set(value) = update { copy(personalityCollectEnabled = value) }

    var languageCode: String
        get() = current.languageCode
        set(value) = update { copy(languageCode = LocaleHelper.normalize(value)) }

    fun answer(id: String): String = when (id) {
        ProfileQuestionnaire.ID_NAME -> name
        ProfileQuestionnaire.ID_PROGRAM -> program.ifBlank { current.answers[id].orEmpty() }
        ProfileQuestionnaire.ID_BIRTH -> birthYear
        ProfileQuestionnaire.ID_LOCATION -> location
        ProfileQuestionnaire.ID_ABOUT -> aboutMe
        else -> current.answers[id].orEmpty()
    }

    fun putAnswer(id: String, value: String) {
        val trimmed = value.trim()
        when (id) {
            ProfileQuestionnaire.ID_NAME -> name = trimmed
            ProfileQuestionnaire.ID_PROGRAM -> {
                update {
                    copy(
                        program = trimmed,
                        answers = answers + (id to trimmed)
                    )
                }
            }
            ProfileQuestionnaire.ID_BIRTH -> birthYear = trimmed
            ProfileQuestionnaire.ID_LOCATION -> location = trimmed
            ProfileQuestionnaire.ID_ABOUT -> aboutMe = trimmed
            else -> update { copy(answers = answers + (id to trimmed)) }
        }
    }

    fun skipQuestion(id: String) {
        update { copy(skipped = skipped + id) }
    }

    fun skippedQuestions(): Set<String> = current.skipped

    fun answers(): Map<String, String> = current.answers

    fun nextUnanswered(): QuestionnaireQuestion? =
        ProfileQuestionnaire.nextUnanswered(current)

    fun questionnaireText(): String? = ProfileQuestionnaire.formatAnswers(current)

    fun personalityForAi(): String? =
        if (personalityEnabled) personality.ifBlank { null } else null

    fun hasEmptyRequired(): Boolean =
        name.isBlank() || birthYear.isBlank() || location.isBlank() ||
            program.isBlank() || aboutMe.isBlank()

    fun applyAll(
        name: String = this.name,
        birthYear: String = this.birthYear,
        location: String = this.location,
        aboutMe: String = this.aboutMe,
        program: String = this.program,
        problems: Set<String> = this.problems,
        personality: String = this.personality,
        answers: Map<String, String> = this.answers()
    ) {
        update {
            copy(
                name = name.trim().take(40),
                birthYear = birthYear.trim(),
                location = location.trim(),
                aboutMe = aboutMe.trim(),
                program = program.trim(),
                problems = problems,
                personality = personality.trim(),
                answers = answers
            )
        }
    }

    private fun update(block: ProfileSnapshot.() -> ProfileSnapshot) {
        val next = _snapshot.value.block()
        persist(next)
        _snapshot.value = next
    }

    private fun persist(snap: ProfileSnapshot) {
        val answers = JSONObject()
        snap.answers.forEach { (k, v) -> answers.put(k, v) }
        val skipped = JSONArray()
        snap.skipped.forEach { skipped.put(it) }
        prefs.edit()
            .putString(KEY_NAME, snap.name)
            .putString(KEY_BIRTH, snap.birthYear)
            .putString(KEY_LOCATION, snap.location)
            .putString(KEY_ABOUT, snap.aboutMe)
            .putString(KEY_PROGRAM, snap.program)
            .putStringSet(KEY_PROBLEMS, snap.problems)
            .putString(KEY_PERSONALITY, snap.personality)
            .putBoolean(KEY_PERSONALITY_ON, snap.personalityEnabled)
            .putBoolean(KEY_COLLECT, snap.personalityCollectEnabled)
            .putString(KEY_LANG, snap.languageCode)
            .putString(KEY_ANSWERS, answers.toString())
            .putString(KEY_SKIPPED, skipped.toString())
            .apply()
    }

    private fun read(): ProfileSnapshot {
        return ProfileSnapshot(
            name = prefs.getString(KEY_NAME, "").orEmpty(),
            birthYear = prefs.getString(KEY_BIRTH, "").orEmpty(),
            location = prefs.getString(KEY_LOCATION, "").orEmpty(),
            aboutMe = prefs.getString(KEY_ABOUT, "").orEmpty(),
            program = prefs.getString(KEY_PROGRAM, "").orEmpty(),
            problems = prefs.getStringSet(KEY_PROBLEMS, emptySet()) ?: emptySet(),
            personality = prefs.getString(KEY_PERSONALITY, "").orEmpty(),
            personalityEnabled = prefs.getBoolean(KEY_PERSONALITY_ON, true),
            personalityCollectEnabled = prefs.getBoolean(KEY_COLLECT, false),
            answers = decodeMap(prefs.getString(KEY_ANSWERS, "{}") ?: "{}"),
            skipped = decodeSet(prefs.getString(KEY_SKIPPED, "[]") ?: "[]"),
            languageCode = LocaleHelper.normalize(
                prefs.getString(KEY_LANG, LocaleHelper.deviceLanguage()) ?: LocaleHelper.deviceLanguage()
            )
        )
    }

    /** First install only: persist device language once, never overwrite later from OS changes. */
    private fun ensureLanguageDefault() {
        if (prefs.contains(KEY_LANG)) return
        val fromPsych = appContext.getSharedPreferences("psychologist", Context.MODE_PRIVATE)
            .getString("language_code", null)
            ?.takeIf { it.isNotBlank() }
        val initial = LocaleHelper.normalize(fromPsych ?: LocaleHelper.deviceLanguage())
        prefs.edit().putString(KEY_LANG, initial).apply()
    }

    private fun migrateIfNeeded() {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        val journal = appContext.getSharedPreferences("journal_local", Context.MODE_PRIVATE)
        val psych = appContext.getSharedPreferences("psychologist", Context.MODE_PRIVATE)
        val journalName = journal.getString("name", "").orEmpty().trim()
        val psychName = psych.getString("name", "").orEmpty().trim()
        val journalPortrait = journal.getString("personality", "").orEmpty().trim()
        val psychPortrait = psych.getString("my_personality", "").orEmpty().trim()
        val portrait = listOf(journalPortrait, psychPortrait)
            .filter { it.isNotBlank() }
            .maxByOrNull { it.length }
            .orEmpty()
        val answers = decodeMap(journal.getString("questionnaire", "{}") ?: "{}")
        val journalProgram = answers[ProfileQuestionnaire.ID_PROGRAM].orEmpty().trim()
        val psychProgram = psych.getString("recovery_program", "").orEmpty().trim()
        persist(
            ProfileSnapshot(
                name = journalName.ifBlank { psychName },
                birthYear = psych.getString("birth_year", "").orEmpty(),
                location = psych.getString("location", "").orEmpty(),
                aboutMe = psych.getString("about_me", "").orEmpty(),
                program = psychProgram.ifBlank { journalProgram },
                problems = journal.getStringSet("problems", emptySet()) ?: emptySet(),
                personality = portrait,
                personalityEnabled = journal.getBoolean("personality_on", true),
                personalityCollectEnabled = psych.getBoolean("my_personality_on", false),
                answers = answers,
                skipped = decodeSet(journal.getString("quest_skipped", "[]") ?: "[]")
            )
        )
        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    private fun decodeMap(raw: String): Map<String, String> {
        return try {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { o.optString(it) }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun decodeSet(raw: String): Set<String> {
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    companion object {
        private const val PREFS = "user_profile"
        private const val KEY_MIGRATED = "migrated_v1"
        private const val KEY_NAME = "name"
        private const val KEY_BIRTH = "birth_year"
        private const val KEY_LOCATION = "location"
        private const val KEY_ABOUT = "about_me"
        private const val KEY_PROGRAM = "program"
        private const val KEY_PROBLEMS = "problems"
        private const val KEY_PERSONALITY = "personality"
        private const val KEY_PERSONALITY_ON = "personality_on"
        private const val KEY_COLLECT = "personality_collect"
        private const val KEY_LANG = "language_code"
        private const val KEY_ANSWERS = "questionnaire"
        private const val KEY_SKIPPED = "quest_skipped"
    }
}
