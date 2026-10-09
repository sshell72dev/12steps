package ru.na.steps12.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VoiceSpeaker(
    context: Context,
    private val store: VoiceSettingsStore
) {
    private val app = context.applicationContext
    private var engine: TextToSpeech? = null
    private var pending: String? = null
    private var pendingChunks = 0
    private var speakGen = 0
    private var focusRequest: AudioFocusRequest? = null

    /**
     * Порции текущего текста. Номер порции — это позиция прослушивания: у TTS нет
     * своей шкалы, поэтому пауза и перемотка опираются на порции и оценку времени.
     */
    private var chunks: List<String> = emptyList()
    private var chunkIndex = 0
    private var chunkStartedAt = 0L
    private val ticker = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null

    private val audioAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val wakeLock: PowerManager.WakeLock by lazy {
        (app.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "steps12:tts")
            .apply { setReferenceCounted(false) }
    }

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    /** Прослушивание стоит на паузе: текст никуда не делся, продолжение — с того же места. */
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val speed: Float
        get() = store.snapshot.speed.coerceIn(0.5f, 1.8f)

    fun ensureReady() {
        if (engine != null) return
        engine = TextToSpeech(app) { status ->
            val ok = status == TextToSpeech.SUCCESS
            _ready.value = ok
            if (ok) {
                applyVoice()
                pending?.let { text ->
                    pending = null
                    speak(text)
                }
            }
        }.also { tts ->
            tts.setAudioAttributes(audioAttrs)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    val index = utteranceIndex(utteranceId)
                    if (index != null) {
                        chunkIndex = index
                        chunkStartedAt = System.currentTimeMillis()
                    }
                    markSpeaking(true)
                }

                override fun onDone(utteranceId: String?) {
                    onChunkFinished(utteranceId)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onChunkFinished(utteranceId)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    onChunkFinished(utteranceId)
                }
            })
        }
    }

    private fun utteranceIndex(utteranceId: String?): Int? =
        utteranceId?.removePrefix("voice-")?.substringAfter("-", "")?.toIntOrNull()

    private fun utteranceGen(utteranceId: String?): Int? =
        utteranceId?.removePrefix("voice-")?.substringBefore("-")?.toIntOrNull()

    private fun onChunkFinished(utteranceId: String?) {
        val gen = utteranceGen(utteranceId)
        val left = synchronized(this) {
            if (gen != null && gen != speakGen) return
            pendingChunks = (pendingChunks - 1).coerceAtLeast(0)
            pendingChunks
        }
        if (left == 0) {
            stopTicker()
            // Текст дочитан до конца: панель прослушивания закрывается, следующее
            // включение начнётся сначала.
            chunks = emptyList()
            chunkIndex = 0
            _paused.value = false
            _positionMs.value = _durationMs.value
            markSpeaking(false)
        }
    }

    var speakingListener: ((Boolean, String) -> Unit)? = null
    private var lastPreview: String = ""
    private var lastText: String = ""

    /** Совпадает ли текст с тем, что сейчас прослушивается: иначе кнопка начнёт его заново. */
    fun matches(text: String): Boolean = lastText.isNotBlank() && lastText == text.trim()

    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        lastText = clean
        lastPreview = clean.replace('\n', ' ').take(120)
        ensureReady()
        val spoken = stripForSpeech(clean)
        if (spoken.isBlank()) return
        chunks = speechChunks(spoken)
        chunkIndex = 0
        chunkStartedAt = System.currentTimeMillis()
        _durationMs.value = (chunks.sumOf { chunkSeconds(it, speed) } * 1000).toLong()
        _positionMs.value = 0L
        _paused.value = false
        if (!_ready.value) {
            pending = clean
            return
        }
        playFrom(0)
    }

    /** Пауза: запоминает место и замолкает, текст остаётся для продолжения. */
    fun pause() {
        if (!_speaking.value) return
        syncPosition()
        silenced()
        _paused.value = true
        markSpeaking(false)
    }

    /** Продолжение с места паузы — с начала порции, на которой остановились. */
    fun resume() {
        if (!_paused.value) return
        if (chunks.isEmpty()) {
            _paused.value = false
            return
        }
        ensureReady()
        if (!_ready.value) return
        _paused.value = false
        playFrom(chunkIndex)
    }

    fun pauseOrResume() {
        when {
            _speaking.value -> pause()
            _paused.value -> resume()
        }
    }

    /** Перемотка на [delta] минут от текущей позиции: −1 — минута назад, 1 — вперёд. */
    fun skipMinutes(delta: Int) {
        if (chunks.isEmpty()) return
        val target = _positionMs.value / 1000.0 + delta * 60.0
        seekToSeconds(target)
    }

    /** Начать с минуты [minute] (1 — первая минута). */
    fun seekToMinute(minute: Int) {
        if (chunks.isEmpty()) return
        seekToSeconds((minute.coerceAtLeast(1) - 1) * 60.0)
    }

    fun seekToSeconds(seconds: Double) {
        if (chunks.isEmpty()) return
        val index = chunkAtSeconds(chunks, seconds.coerceAtLeast(0.0), speed)
        chunkIndex = index
        chunkStartedAt = System.currentTimeMillis()
        _positionMs.value = (secondsBefore(chunks, index, speed) * 1000).toLong()
        ensureReady()
        if (!_ready.value) return
        _paused.value = false
        playFrom(index)
    }

    fun stop() {
        pending = null
        silenced()
        chunks = emptyList()
        chunkIndex = 0
        _positionMs.value = 0L
        _durationMs.value = 0L
        _paused.value = false
        markSpeaking(false)
    }

    fun toggle(text: String) {
        if (_speaking.value) stop() else speak(text)
    }

    fun release() {
        stop()
        tickerJob?.cancel()
        engine?.shutdown()
        engine = null
        _ready.value = false
    }

    fun deviceVoiceOptions(): List<VoiceOption> {
        val tts = engine ?: return emptyList()
        if (!_ready.value) return emptyList()
        tts.language = Locale.forLanguageTag(VoiceI18n.speechTag)
        return russianVoices(tts)
            .sortedWith(compareBy({ it.isNetworkConnectionRequired }, { it.name }))
            .map { voice ->
                VoiceOption(
                    id = voice.name,
                    provider = "android",
                    label = "Телефон · ${friendlyDeviceName(voice)}",
                    gender = VoiceCatalog.inferGender(voice.name)
                )
            }
    }

    /** Запуск озвучки с порции [index]: очередь TTS пересобирается с этого места. */
    private fun playFrom(index: Int) {
        val tts = engine ?: return
        if (chunks.isEmpty()) return
        applyVoice()
        val start = index.coerceIn(0, chunks.size - 1)
        chunkIndex = start
        chunkStartedAt = System.currentTimeMillis()
        _positionMs.value = (secondsBefore(chunks, start, speed) * 1000).toLong()
        val gen = synchronized(this) {
            speakGen += 1
            pendingChunks = chunks.size - start
            speakGen
        }
        markSpeaking(true)
        startTicker()
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }
        for (i in start until chunks.size) {
            val mode = if (i == start) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val code = tts.speak(chunks[i], mode, params, "voice-$gen-$i")
            if (code != TextToSpeech.SUCCESS) {
                synchronized(this) {
                    if (gen == speakGen) pendingChunks = 0
                }
                stopTicker()
                _paused.value = false
                markSpeaking(false)
                return
            }
        }
    }

    /** Глушит TTS и сбрасывает очередь, не трогая нарезанный текст. */
    private fun silenced() {
        synchronized(this) {
            speakGen += 1
            pendingChunks = 0
        }
        runCatching { engine?.stop() }
        stopTicker()
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = ticker.launch {
            while (true) {
                delay(500)
                syncPosition()
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    /** Оценка текущей позиции: пройденные порции плюс время на текущей. */
    private fun syncPosition() {
        if (chunks.isEmpty()) return
        val before = secondsBefore(chunks, chunkIndex, speed)
        val elapsed = if (_speaking.value) {
            (System.currentTimeMillis() - chunkStartedAt) / 1000.0
        } else {
            0.0
        }
        val current = chunks.getOrElse(chunkIndex) { "" }
        val inChunk = elapsed.coerceIn(0.0, chunkSeconds(current, speed))
        _positionMs.value = ((before + inChunk) * 1000).toLong()
    }

    private fun markSpeaking(on: Boolean) {
        val was = _speaking.value
        if (was == on) return
        _speaking.value = on
        if (on) holdPlayback() else releasePlayback()
        speakingListener?.invoke(on, lastPreview)
    }

    private fun holdPlayback() {
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttrs)
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = request
        runCatching { am.requestAudioFocus(request) }
        runCatching {
            if (!wakeLock.isHeld) wakeLock.acquire(30 * 60 * 1000L)
        }
    }

    private fun releasePlayback() {
        runCatching { if (wakeLock.isHeld) wakeLock.release() }
        val request = focusRequest ?: return
        focusRequest = null
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        runCatching { am.abandonAudioFocusRequest(request) }
    }

    private fun stripForSpeech(text: String): String =
        text.replace(Regex("#{1,6}\\s*"), "")
            .replace("**", "")
            .replace("*", "")
            .replace(Regex("[ \\t]+"), " ")
            .trim()

    private fun applyVoice() {
        val tts = engine ?: return
        if (!_ready.value) return
        val cfg = store.snapshot
        val option = cfg.option
        tts.setAudioAttributes(audioAttrs)
        tts.language = Locale.forLanguageTag(VoiceI18n.speechTag)
        tts.setSpeechRate(cfg.speed.coerceIn(0.5f, 1.8f))
        val chosen = pickVoice(tts, option)
        if (chosen != null) {
            tts.voice = chosen
        }
        tts.setPitch(pitchFor(tts, option, chosen))
    }

    private fun pickVoice(tts: TextToSpeech, option: VoiceOption): Voice? {
        val ru = russianVoices(tts)
        if (ru.isEmpty()) return null
        if (option.provider == "android") {
            ru.firstOrNull { it.name == option.id }?.let { return it }
        }
        val wantFemale = option.gender != "male"
        val scored = ru.map { voice ->
            voice to score(voice, wantFemale)
        }.sortedWith(
            compareByDescending<Pair<Voice, Int>> { it.second }
                .thenByDescending { it.first.quality }
                .thenBy { it.first.isNetworkConnectionRequired }
        )
        val best = scored.firstOrNull() ?: return null
        if (best.second <= 0 && ru.size == 1) return ru.first()
        return best.first
    }

    private fun score(voice: Voice, wantFemale: Boolean): Int {
        val gender = VoiceCatalog.inferGender(voice.name)
        var points = 0
        when {
            wantFemale && gender == "female" -> points += 40
            !wantFemale && gender == "male" -> points += 40
            gender == "unknown" -> points += 1
            else -> points -= 20
        }
        if (!voice.isNetworkConnectionRequired) points += 4
        if ("notInstalled" in voice.features) points -= 50
        return points
    }

    private fun pitchFor(tts: TextToSpeech, option: VoiceOption, chosen: Voice?): Float {
        if (option.provider == "android") return 1f
        val distinct = russianVoices(tts)
            .map { VoiceCatalog.inferGender(it.name) }
            .filter { it != "unknown" }
            .distinct()
            .size >= 2
        if (distinct && chosen != null && VoiceCatalog.inferGender(chosen.name) != "unknown") {
            return 1f
        }
        return if (option.gender == "male") 0.82f else 1.14f
    }

    private fun russianVoices(tts: TextToSpeech): List<Voice> {
        val all = tts.voices ?: return emptyList()
        val lang = Locale.forLanguageTag(VoiceI18n.speechTag).language.ifBlank { "ru" }
        val matched = all.filter { voice ->
            voice.locale.language.equals(lang, ignoreCase = true) &&
                "notInstalled" !in voice.features
        }
        if (matched.isNotEmpty()) return matched
        return all.filter { "notInstalled" !in it.features }
    }

    private fun friendlyDeviceName(voice: Voice): String {
        val gender = when (VoiceCatalog.inferGender(voice.name)) {
            "male" -> "муж."
            "female" -> "жен."
            else -> voice.locale.toLanguageTag()
        }
        val short = voice.name
            .substringAfterLast('-')
            .ifBlank { voice.name }
        val net = if (voice.isNetworkConnectionRequired) "сеть" else "офлайн"
        return "$short · $gender · $net"
    }
}
