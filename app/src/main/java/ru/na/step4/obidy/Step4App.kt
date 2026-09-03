package ru.na.step4.obidy

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.na.step4.obidy.auth.AppLockStore
import ru.na.step4.obidy.data.AppDatabase
import ru.na.step4.obidy.data.ResentmentRepository
import ru.na.step4.obidy.data.activity.ActivityLog
import ru.na.step4.obidy.data.analysis.AnalysisProgressStore
import ru.na.step4.obidy.data.analysis.AnalysisRepository
import ru.na.step4.obidy.data.analysis.AnalysisSettings
import ru.na.step4.obidy.data.analysis.AnalysisStreakStore
import ru.na.step4.obidy.data.InventoryProgressStore
import ru.na.step4.obidy.data.InventoryAiCache
import ru.na.step4.obidy.data.journal.JournalAnalyzeCache
import ru.na.step4.obidy.data.journal.JournalPrefs
import ru.na.step4.obidy.data.journal.JournalStore
import ru.na.step4.obidy.data.journal.JournalStreakStore
import ru.na.step4.obidy.data.life.LifeBoardStore
import ru.na.step4.obidy.data.profile.ProfileStore
import ru.na.step4.obidy.data.psych.PsychReminderWorker
import ru.na.step4.obidy.data.psych.PsychRepository
import ru.na.step4.obidy.data.psych.PsychSettings
import ru.na.step4.obidy.data.spiritual.SpiritualRatingStore
import ru.na.step4.obidy.data.messenger.MessengerRepository
import ru.na.step4.obidy.data.messenger.MessengerChallengeShare
import ru.na.step4.obidy.data.support.SupportRepository
import ru.na.step4.obidy.voicehands.VoiceHandsSettings
import ru.na.steps12.voice.VoicePlugin

class Step4App : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var repository: ResentmentRepository
        private set

    lateinit var analysisRepository: AnalysisRepository
        private set

    lateinit var analysisSettings: AnalysisSettings
        private set

    lateinit var analysisProgress: AnalysisProgressStore
        private set

    lateinit var inventoryProgress: InventoryProgressStore
        private set

    lateinit var inventoryAiCache: InventoryAiCache
        private set

    lateinit var analysisStreak: AnalysisStreakStore
        private set

    lateinit var journalStreak: JournalStreakStore
        private set

    lateinit var journalStore: JournalStore
        private set

    lateinit var journalAnalyzeCache: JournalAnalyzeCache
        private set

    lateinit var profileStore: ProfileStore
        private set

    lateinit var journalPrefs: JournalPrefs
        private set

    lateinit var psychRepository: PsychRepository
        private set

    lateinit var psychSettings: PsychSettings
        private set

    lateinit var notesRepository: ru.na.step4.obidy.data.notes.NotesRepository
        private set

    lateinit var supportRepository: SupportRepository
        private set

    lateinit var lifeBoard: LifeBoardStore
        private set

    lateinit var spiritualRating: SpiritualRatingStore
        private set

    lateinit var appLockStore: AppLockStore
        private set

    lateinit var voicePlugin: VoicePlugin
        private set

    lateinit var i18nController: ru.na.step4.obidy.data.i18n.I18nController
        private set

    lateinit var messengerRepository: MessengerRepository
        private set

    lateinit var messengerChallenges: MessengerChallengeShare
        private set

    lateinit var voiceHandsSettings: VoiceHandsSettings
        private set

    lateinit var activityLog: ActivityLog
        private set

    override fun onCreate() {
        super.onCreate()
        appLockStore = AppLockStore(this)
        val db = AppDatabase.get(this)
        repository = ResentmentRepository(
            db.resentmentDao(),
            db.categoryDao(),
            db.situationDao()
        )
        analysisRepository = AnalysisRepository(db.analysisDao())
        analysisSettings = AnalysisSettings(this)
        analysisProgress = AnalysisProgressStore(this)
        inventoryProgress = InventoryProgressStore(this)
        inventoryAiCache = InventoryAiCache(this)
        analysisStreak = AnalysisStreakStore(this)
        journalStreak = JournalStreakStore(this)
        journalStore = JournalStore(this)
        journalAnalyzeCache = JournalAnalyzeCache(this)
        profileStore = ProfileStore(this)
        journalPrefs = JournalPrefs(this, profileStore)
        lifeBoard = LifeBoardStore(this)
        notesRepository = ru.na.step4.obidy.data.notes.NotesRepository(this, journalPrefs)
        spiritualRating = SpiritualRatingStore(this)
        supportRepository = SupportRepository(journalPrefs, spiritualRating, lifeBoard)
        psychRepository = PsychRepository(db.psychDao())
        psychSettings = PsychSettings(this, profileStore)
        psychSettings.goalsProvider = { lifeBoard.goalsPromptBlock().orEmpty() }
        psychSettings.languageCode = profileStore.languageCode
        psychSettings.expireProIfNeeded()
        voicePlugin = VoicePlugin(this)
        messengerRepository = MessengerRepository(this, profileStore)
        messengerChallenges = MessengerChallengeShare(
            messengerRepository,
            journalStreak,
            analysisStreak,
            spiritualRating
        )
        voiceHandsSettings = VoiceHandsSettings(this)
        activityLog = ActivityLog(db.activityDao(), appScope)
        voicePlugin.speaker.speakingListener = { on, preview ->
            activityLog.speakingChanged(on, preview)
        }
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : androidx.lifecycle.DefaultLifecycleObserver {
                override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                    activityLog.appBackground()
                }
            }
        )

        ru.na.step4.obidy.data.i18n.SourceBootstrap.registerAll()
        ru.na.step4.obidy.data.i18n.ContentI18n.registerStatic()
        ru.na.step4.obidy.data.i18n.ContentI18n.registerTree(this)
        ru.na.step4.obidy.data.i18n.ContentI18n.registerAnalysisCatalog(this)
        val i18n = ru.na.step4.obidy.data.i18n.I18nController(this, profileStore.languageCode)
        ru.na.step4.obidy.data.i18n.I18n.bind(i18n)
        ru.na.steps12.voice.VoiceI18n.resolver = { key, source ->
            ru.na.step4.obidy.data.i18n.I18n.t(key, source)
        }
        ru.na.steps12.voice.VoiceI18n.speechTag =
            ru.na.step4.obidy.data.i18n.LocaleHelper.speechTag(profileStore.languageCode)
        this.i18nController = i18n

        spiritualRating.refreshMissPenalties()
        if (voicePlugin.snapshot.publicKey.isBlank() && BuildConfig.VAPI_PUBLIC_KEY.isNotBlank()) {
            voicePlugin.store.snapshot = voicePlugin.snapshot.copy(
                publicKey = BuildConfig.VAPI_PUBLIC_KEY,
                assistantId = BuildConfig.VAPI_ASSISTANT_ID
            )
        }
        PsychReminderWorker.schedule(this)
        appScope.launch {
            repository.ensureDefaultCategories()
            notesRepository.sync()
            ru.na.step4.obidy.data.analysis.AnalysisCatalogSync.sync(
                analysisSettings,
                journalPrefs
            )
            voicePlugin.sync(BuildConfig.ANALYSIS_API_URL, BuildConfig.ANALYSIS_API_TOKEN)
            messengerRepository.refreshEnabled()
        }
    }
}
