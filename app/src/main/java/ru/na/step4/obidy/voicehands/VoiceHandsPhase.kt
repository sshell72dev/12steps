package ru.na.step4.obidy.voicehands

enum class VoiceHandsPhase {
    Off,
    Standby,
    Opening,
    Dictating,
    ThinkingQuestion,
    AwaitingReply,
    ThinkingResult,
    AskRead,
    Reading,
    AfterRead
}

data class VoiceHandsUi(
    val enabled: Boolean = false,
    val phase: VoiceHandsPhase = VoiceHandsPhase.Off,
    val listening: Boolean = false,
    val speaking: Boolean = false,
    val draft: String = "",
    val lastHeard: String = "",
    val status: String = "",
    val commandsHint: String = "",
    val showListenButton: Boolean = false,
    val error: String? = null
)
