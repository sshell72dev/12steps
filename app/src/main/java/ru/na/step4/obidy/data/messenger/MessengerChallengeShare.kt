package ru.na.step4.obidy.data.messenger

import ru.na.step4.obidy.Ru
import ru.na.step4.obidy.data.analysis.AnalysisStreakStore
import ru.na.step4.obidy.data.journal.JournalStreakStore
import ru.na.step4.obidy.data.spiritual.SpiritualRatingStore
import ru.na.step4.obidy.data.spiritual.SpiritualRu

class MessengerChallengeShare(
    private val messenger: MessengerRepository,
    private val journalStreak: JournalStreakStore,
    private val analysisStreak: AnalysisStreakStore,
    private val spiritual: SpiritualRatingStore
) {
    suspend fun shareJournal(pointName: String) {
        runCatching {
            val name = pointName.trim()
            if (name.isBlank()) return
            val streak = journalStreak.label() ?: Ru.analysisStreak
            messenger.shareChallenge(
                MessengerChallengeKeys.STEPS,
                format(streak, MessengerRu.challengePoint, name)
            )
        }
    }

    suspend fun shareAnalysis(analysisTitle: String) {
        runCatching {
            val name = analysisTitle.trim()
            if (name.isBlank()) return
            val streak = analysisStreak.label() ?: Ru.analysisStreak
            messenger.shareChallenge(
                MessengerChallengeKeys.ANALYSIS,
                format(streak, MessengerRu.challengeAnalysisLabel, name)
            )
        }
    }

    private fun format(streakLabel: String, subjectLabel: String, subjectName: String): String {
        val snap = spiritual.snapshot.value
        val rating = "${SpiritualRu.abbr}: ${snap.totalScore} · ${SpiritualRu.day} ${snap.dayScore} · ${snap.dayLabel}"
        return "$streakLabel\n$subjectLabel: $subjectName\n$rating"
    }
}
