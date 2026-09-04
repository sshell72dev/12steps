package ru.na.step4.obidy.voicehands

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.na.step4.obidy.data.psych.PsychTopic
import ru.na.step4.obidy.ui.psych.PsychPage
import ru.na.step4.obidy.ui.psych.PsychUi
import ru.na.step4.obidy.ui.psych.PsychViewModel

/**
 * Narrow façade over the psychologist screen. The experiment talks only
 * through this gate so PsychViewModel can keep evolving on its own.
 */
class VoiceHandsPsych(private val vm: PsychViewModel) {
    val ui: StateFlow<PsychUi> get() = vm.ui

    fun same(other: PsychViewModel): Boolean = vm === other

    fun goRecord() = vm.goRecord()

    fun skipOnboarding() = vm.skipOnboarding()

    fun goHub() = vm.goHub()

    fun submitSituation(text: String) = vm.submitSituation(text, viaVoice = true)

    fun pickTopicNoHistory(situationId: Long) = vm.pickTopic(situationId, null, noHistory = true)

    fun confirmTopicForVoice(situationId: Long, topicId: Long?, createName: String?) =
        vm.confirmTopicForVoice(situationId, topicId, createName)

    suspend fun situationText(situationId: Long): String = vm.situationText(situationId)

    suspend fun topicCatalog(): List<PsychTopic> = vm.topicCatalog()

    suspend fun suggestTopic(situationId: Long): VoiceTopicChoice {
        val text = situationText(situationId)
        val topics = topicCatalog()
        return VoiceHandsTopicSuggest.suggest(text, topics)
    }

    fun answerDialogue(text: String) = vm.answerDialogue(text, viaVoice = true)

    fun analyze() = vm.analyze()

    fun recommend() = vm.recommend()

    fun startWork() = vm.startWork()

    fun willCompleteDialogueOnNextAnswer(): Boolean {
        val page = vm.ui.value.page as? PsychPage.Dialogue ?: return false
        return page.answers.size + 1 >= vm.settings.dialogueExtraLimit
    }
}

object VoiceHandsPsychGate {
    private val _bound = MutableStateFlow<VoiceHandsPsych?>(null)
    val bound: StateFlow<VoiceHandsPsych?> = _bound.asStateFlow()

    fun attach(vm: PsychViewModel) {
        _bound.value = VoiceHandsPsych(vm)
    }

    fun detach(vm: PsychViewModel) {
        if (_bound.value?.same(vm) == true) {
            _bound.value = null
        }
    }
}

internal val PsychUi.dialogueQuestion: String?
    get() = (page as? PsychPage.Dialogue)?.question?.trim()?.takeIf { it.isNotBlank() }

internal val PsychUi.topicPickId: Long?
    get() = (page as? PsychPage.TopicPick)?.situationId

internal val PsychUi.resultSpeakable: String?
    get() {
        val page = page as? PsychPage.Result ?: return null
        return page.speakable.ifBlank { page.text }.trim().takeIf { it.isNotBlank() }
    }

internal val PsychUi.resultKey: String?
    get() {
        val page = page as? PsychPage.Result ?: return null
        return "${page.kind}:${page.session.id}:${page.text.hashCode()}"
    }

internal val PsychUi.resultKind: String?
    get() = (page as? PsychPage.Result)?.kind

internal val PsychUi.isOnboarding: Boolean
    get() = page is PsychPage.Onboarding

internal val PsychUi.isRecord: Boolean
    get() = page is PsychPage.Record

internal val PsychUi.isPaywall: Boolean
    get() = page is PsychPage.Paywall

internal val PsychUi.isReview: Boolean
    get() = page is PsychPage.Review

internal val PsychUi.isIdle: Boolean
    get() = page is PsychPage.Idle
