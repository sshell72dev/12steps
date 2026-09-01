package ru.na.step4.obidy.data.analysis

data class Prayer(
    val title: String,
    val text: String
)

enum class QuestionButtons {
    AUTO,
    NONE,
    LIST
}

data class LinearQuestion(
    val id: String,
    val text: String,
    val prayer: Prayer? = null,
    val skipNextOnNo: Int = 0,
    val buttons: QuestionButtons = QuestionButtons.AUTO,
    val choices: List<Choice> = emptyList(),
    val allowText: Boolean = true,
    val skipNextByChoiceId: Map<String, Int> = emptyMap(),
    val endOnChoiceIds: Set<String> = emptySet(),
    val followUps: Map<String, List<LinearQuestion>> = emptyMap()
)

data class AnalysisBranch(
    val id: String,
    val title: String,
    val questions: List<String>
)

data class CleanDaySide(
    val label: String,
    val questions: List<String>
)

data class CleanDayItem(
    val title: String,
    val question: String,
    val ifYes: CleanDaySide,
    val ifNo: CleanDaySide
)

enum class AnalysisFlow {
    STEP10,
    MINI,
    LINEAR_PREVIEW,
    LINEAR_NOW,
    BRANCHED,
    CLEAN_DAY
}

data class CatalogEntry(
    val id: String,
    val title: String,
    val menuOrder: Int,
    val flow: AnalysisFlow,
    val questions: List<LinearQuestion> = emptyList(),
    val branches: List<AnalysisBranch> = emptyList(),
    val items: List<CleanDayItem> = emptyList(),
    val custom: Boolean = false
)

data class QaPair(
    val question: String,
    val answer: String
)

data class Choice(
    val id: String,
    val label: String
)

data class PreviewBlock(
    val heading: String?,
    val lines: List<String>
)

sealed class SessionScreen {
    data class Preview(
        val title: String,
        val blocks: List<PreviewBlock>,
        val primaryLabel: String,
        val showReroll: Boolean,
        val countOptions: List<Int> = emptyList(),
        val selectedCount: Int? = null,
        val canBegin: Boolean = true
    ) : SessionScreen()

    data class Question(
        val title: String,
        val prayer: Prayer?,
        val question: String,
        val choices: List<Choice>,
        val allowText: Boolean,
        val hideSend: Boolean,
        val progressIndex: Int,
        val progressTotal: Int
    ) : SessionScreen()

    data class Done(
        val title: String,
        val answers: List<QaPair>
    ) : SessionScreen()
}
